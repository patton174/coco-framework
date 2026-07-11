#!/usr/bin/env python3
"""Fail-closed automatic merge checks executed from the protected base branch."""

from __future__ import annotations

import argparse
import dataclasses
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

from agent_review import ReviewError as AgentReviewError
from agent_review import parse_finding_issue_marker
from agent_review import require_resource_actor


BASE_BRANCH = "main"
AGENT_ISSUE_LABEL = "agent-review"
REQUIRED_GATES = ("CI gate", "Agent jury gate", "Agent issue gate")
CI_CHECK_APP_ID = 15368
GITHUB_ACTIONS_BOT_LOGIN = "github-actions[bot]"
GITHUB_ACTIONS_BOT_ID = 41898282
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
REPOSITORY_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
REVIEWER_PERMISSIONS = {"write", "maintain", "admin"}
MAX_EVENT_BYTES = 2 * 1024 * 1024
MAX_OPEN_PULL_REQUESTS = 200


class AutoMergeError(RuntimeError):
    """An unexpected condition that must stop automatic merging."""


class ContractError(AutoMergeError):
    """A repository automation payload violated its strict contract."""


class GitHubApiError(AutoMergeError):
    """A GitHub API operation failed."""

    def __init__(self, message: str, status: int | None = None) -> None:
        super().__init__(message)
        self.status = status


@dataclasses.dataclass(frozen=True)
class Candidate:
    number: int
    expected_head_sha: str | None
    source: str


@dataclasses.dataclass(frozen=True)
class PullRequestSnapshot:
    number: int
    state: str
    base_ref: str
    head_sha: str
    draft: bool
    mergeable: bool | None
    mergeable_state: str
    author_login: str


@dataclasses.dataclass(frozen=True)
class RepositoryMergeSettings:
    allow_merge_commit: bool
    allow_squash_merge: bool
    allow_rebase_merge: bool

    def as_dict(self) -> dict[str, bool]:
        return {
            "allow_merge_commit": self.allow_merge_commit,
            "allow_rebase_merge": self.allow_rebase_merge,
            "allow_squash_merge": self.allow_squash_merge,
        }


@dataclasses.dataclass(frozen=True)
class ReviewState:
    decision: str
    unresolved_threads: int


@dataclasses.dataclass(frozen=True)
class Eligibility:
    snapshot: PullRequestSnapshot
    reasons: tuple[str, ...]
    approvers: tuple[str, ...] = ()
    gates: dict[str, str] = dataclasses.field(default_factory=dict)
    review_decision: str = "UNKNOWN"
    unresolved_review_threads: int = 0
    open_agent_issues: tuple[int, ...] = ()
    merge_settings: RepositoryMergeSettings | None = None


@dataclasses.dataclass(frozen=True)
class Decision:
    state: str
    pull_request: int
    head_sha: str
    source: str
    reasons: tuple[str, ...] = ()
    approvers: tuple[str, ...] = ()
    gates: dict[str, str] = dataclasses.field(default_factory=dict)
    review_decision: str = "UNKNOWN"
    unresolved_review_threads: int = 0
    open_agent_issues: tuple[int, ...] = ()
    merge_settings: dict[str, bool] = dataclasses.field(default_factory=dict)
    merge_sha: str = ""

    def as_dict(self) -> dict[str, Any]:
        return {
            "approvers": list(self.approvers),
            "gates": self.gates,
            "head_sha": self.head_sha,
            "merge_sha": self.merge_sha,
            "merge_settings": self.merge_settings,
            "open_agent_issues": list(self.open_agent_issues),
            "pull_request": self.pull_request,
            "reasons": list(self.reasons),
            "review_decision": self.review_decision,
            "source": self.source,
            "state": self.state,
            "unresolved_review_threads": self.unresolved_review_threads,
        }


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def require_mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ContractError(f"{label} must be a JSON object.")
    return value


def require_list(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list):
        raise ContractError(f"{label} must be a JSON array.")
    return value


def positive_integer(value: Any, label: str) -> int:
    if type(value) is not int or value <= 0:
        raise ContractError(f"{label} must be a positive integer.")
    return value


def valid_sha(value: Any, label: str) -> str:
    if not isinstance(value, str) or not SHA_RE.fullmatch(value):
        raise ContractError(f"{label} must be a lowercase 40-character commit SHA.")
    return value


class GitHubClient:
    """Small bounded GitHub REST and GraphQL client."""

    def __init__(
        self,
        token: str,
        api_url: str = "https://api.github.com",
        graphql_url: str = "https://api.github.com/graphql",
    ) -> None:
        if not token:
            raise AutoMergeError("GH_TOKEN is required.")
        self.token = token
        self.api_url = api_url.rstrip("/")
        self.graphql_url = graphql_url

    def request(
        self,
        method: str,
        path: str,
        *,
        payload: dict[str, Any] | None = None,
        max_bytes: int = 4 * 1024 * 1024,
    ) -> bytes:
        url = path if path.startswith("http") else f"{self.api_url}/{path.lstrip('/')}"
        data = None if payload is None else canonical_json(payload).encode("utf-8")
        request = urllib.request.Request(
            url,
            method=method,
            data=data,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "Content-Type": "application/json",
                "User-Agent": "coco-auto-merge",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                body = response.read(max_bytes + 1)
                if len(body) > max_bytes:
                    raise GitHubApiError("GitHub API response exceeded the size limit.")
                return body
        except urllib.error.HTTPError as exc:
            detail = ""
            try:
                error_body = exc.read(4097)
                if len(error_body) <= 4096:
                    error_payload = json.loads(error_body)
                    if isinstance(error_payload, dict) and isinstance(
                        error_payload.get("message"), str
                    ):
                        detail = " " + error_payload["message"].replace("\n", " ")[:300]
            except (OSError, UnicodeDecodeError, json.JSONDecodeError):
                pass
            raise GitHubApiError(
                f"GitHub API returned HTTP {exc.code} for {method} {path}.{detail}",
                exc.code,
            ) from exc
        except urllib.error.URLError as exc:
            raise GitHubApiError(
                f"GitHub API request failed for {method} {path}."
            ) from exc

    def json_request(
        self, method: str, path: str, payload: dict[str, Any] | None = None
    ) -> Any:
        body = self.request(method, path, payload=payload)
        if not body:
            return None
        try:
            return json.loads(body)
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise GitHubApiError("GitHub API returned invalid JSON.") from exc

    def get_json(self, path: str) -> Any:
        return self.json_request("GET", path)

    def send_json(self, method: str, path: str, payload: dict[str, Any]) -> Any:
        return self.json_request(method, path, payload)

    def paginate(
        self,
        path: str,
        *,
        limit: int = 1000,
        key: str | None = None,
    ) -> list[Any]:
        separator = "&" if "?" in path else "?"
        values: list[Any] = []
        page = 1
        while True:
            payload = self.get_json(f"{path}{separator}per_page=100&page={page}")
            if key is None:
                batch = require_list(payload, f"GitHub page for {path}")
            else:
                batch = require_list(
                    require_mapping(payload, f"GitHub page for {path}").get(key),
                    f"GitHub page field {key}",
                )
            values.extend(batch)
            if len(values) > limit:
                raise GitHubApiError(
                    f"GitHub pagination for {path} exceeded the {limit}-item limit."
                )
            if len(batch) < 100:
                return values
            page += 1

    def graphql(self, query: str, variables: dict[str, Any]) -> dict[str, Any]:
        payload = self.send_json(
            "POST", self.graphql_url, {"query": query, "variables": variables}
        )
        root = require_mapping(payload, "GitHub GraphQL response")
        if root.get("errors"):
            raise GitHubApiError("GitHub GraphQL returned errors.")
        return require_mapping(root.get("data"), "GitHub GraphQL data")


def parse_agent_issue_marker(body: Any) -> dict[str, Any] | None:
    """Reuse the review publisher's canonical marker contract."""

    try:
        return parse_finding_issue_marker(body)
    except AgentReviewError as exc:
        raise ContractError(str(exc)) from exc


def read_event(path: Path | None) -> dict[str, Any]:
    if path is None:
        return {}
    try:
        if path.stat().st_size > MAX_EVENT_BYTES:
            raise AutoMergeError("GitHub event payload exceeded the size limit.")
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise AutoMergeError(
            f"Unable to read GitHub event payload from {path}."
        ) from exc
    return require_mapping(payload, "GitHub event payload")


def event_candidates(
    event_name: str,
    event: dict[str, Any],
    explicit_pr_number: int | None = None,
    explicit_head_sha: str | None = None,
) -> list[Candidate]:
    if explicit_pr_number is not None:
        return [
            Candidate(
                positive_integer(explicit_pr_number, "pull request number"),
                (
                    valid_sha(explicit_head_sha, "expected head SHA")
                    if explicit_head_sha
                    else None
                ),
                "manual",
            )
        ]

    if event_name == "issues":
        issue = require_mapping(event.get("issue"), "issue event")
        labels = require_list(issue.get("labels", []), "issue labels")
        label_names = {
            str(require_mapping(label, "issue label").get("name", ""))
            for label in labels
        }
        marker = parse_agent_issue_marker(issue.get("body"))
        if marker is None:
            event_label = event.get("label")
            event_label_name = (
                event_label.get("name") if isinstance(event_label, dict) else None
            )
            if (
                AGENT_ISSUE_LABEL in label_names
                or event_label_name == AGENT_ISSUE_LABEL
            ):
                raise ContractError(
                    "Labeled Agent Issue is missing its binding marker."
                )
            return []
        return [
            Candidate(
                int(marker["pull_request"]),
                None,
                f"issues:{event.get('action', 'unknown')}",
            )
        ]

    if event_name == "workflow_dispatch":
        inputs = require_mapping(event.get("inputs", {}), "workflow dispatch inputs")
        raw_number = inputs.get("pull_request")
        if raw_number in (None, ""):
            raise ContractError("workflow_dispatch requires pull_request input.")
        try:
            number = int(str(raw_number))
        except ValueError as exc:
            raise ContractError(
                "workflow_dispatch pull_request must be numeric."
            ) from exc
        raw_head = inputs.get("head_sha")
        return [
            Candidate(
                positive_integer(number, "workflow dispatch pull request"),
                valid_sha(raw_head, "workflow dispatch head SHA") if raw_head else None,
                "workflow_dispatch",
            )
        ]

    if event_name == "workflow_run":
        workflow_run = require_mapping(event.get("workflow_run"), "workflow_run event")
        if (
            workflow_run.get("status") != "completed"
            or workflow_run.get("conclusion") != "success"
        ):
            return []
        pull_requests = require_list(
            workflow_run.get("pull_requests", []), "workflow_run pull_requests"
        )
        candidates: list[Candidate] = []
        for value in pull_requests:
            pull_request = require_mapping(value, "workflow_run pull request")
            head_value = pull_request.get("head")
            expected_head: str | None = None
            if isinstance(head_value, dict) and head_value.get("sha"):
                expected_head = valid_sha(
                    head_value.get("sha"), "workflow_run pull request head SHA"
                )
            candidates.append(
                Candidate(
                    positive_integer(
                        pull_request.get("number"), "workflow_run pull request number"
                    ),
                    expected_head,
                    f"workflow_run:{workflow_run.get('name', 'unknown')}",
                )
            )
        return deduplicate_candidates(candidates)

    if event_name == "schedule":
        return []

    raise ContractError(f"Unsupported auto-merge event: {event_name!r}.")


def deduplicate_candidates(candidates: list[Candidate]) -> list[Candidate]:
    unique: dict[int, Candidate] = {}
    for candidate in candidates:
        previous = unique.get(candidate.number)
        if previous is not None:
            if (
                previous.expected_head_sha
                and candidate.expected_head_sha
                and previous.expected_head_sha != candidate.expected_head_sha
            ):
                raise ContractError(
                    f"Event supplied conflicting heads for PR #{candidate.number}."
                )
            if previous.expected_head_sha is None and candidate.expected_head_sha:
                unique[candidate.number] = candidate
            continue
        unique[candidate.number] = candidate
    return [unique[number] for number in sorted(unique)]


def resolve_candidates(
    client: GitHubClient,
    repository: str,
    event_name: str,
    event: dict[str, Any],
    explicit_pr_number: int | None = None,
    explicit_head_sha: str | None = None,
) -> list[Candidate]:
    candidates = event_candidates(
        event_name,
        event,
        explicit_pr_number=explicit_pr_number,
        explicit_head_sha=explicit_head_sha,
    )
    if event_name not in {"workflow_run", "schedule"} or candidates:
        return candidates

    if event_name == "workflow_run":
        workflow_run = require_mapping(event.get("workflow_run"), "workflow_run event")
        if (
            workflow_run.get("status") != "completed"
            or workflow_run.get("conclusion") != "success"
        ):
            return []

    pulls = client.paginate(
        f"repos/{repository}/pulls?state=open&base={BASE_BRANCH}",
        limit=MAX_OPEN_PULL_REQUESTS,
    )
    fallback: list[Candidate] = []
    for value in pulls:
        pull_request = require_mapping(value, "open pull request")
        head = require_mapping(pull_request.get("head"), "open pull request head")
        fallback.append(
            Candidate(
                positive_integer(
                    pull_request.get("number"), "open pull request number"
                ),
                valid_sha(head.get("sha"), "open pull request head SHA"),
                f"{event_name}:open-pr-scan",
            )
        )
    return deduplicate_candidates(fallback)


def pull_request_snapshot(value: Any, expected_number: int) -> PullRequestSnapshot:
    pull_request = require_mapping(value, f"pull request #{expected_number}")
    number = positive_integer(pull_request.get("number"), "pull request number")
    if number != expected_number:
        raise ContractError(
            f"GitHub returned PR #{number} while PR #{expected_number} was requested."
        )
    state = pull_request.get("state")
    if not isinstance(state, str):
        raise ContractError("Pull request state is missing.")
    base = require_mapping(pull_request.get("base"), "pull request base")
    base_ref = base.get("ref")
    if not isinstance(base_ref, str):
        raise ContractError("Pull request base ref is missing.")
    head = require_mapping(pull_request.get("head"), "pull request head")
    head_sha = valid_sha(head.get("sha"), "pull request head SHA")
    draft = pull_request.get("draft")
    if type(draft) is not bool:
        raise ContractError("Pull request draft flag is missing.")
    mergeable = pull_request.get("mergeable")
    if mergeable is not None and type(mergeable) is not bool:
        raise ContractError("Pull request mergeable flag is invalid.")
    mergeable_state = pull_request.get("mergeable_state")
    if not isinstance(mergeable_state, str):
        raise ContractError("Pull request mergeable_state is missing.")
    author = require_mapping(pull_request.get("user"), "pull request author")
    author_login = author.get("login")
    if not isinstance(author_login, str) or not author_login:
        raise ContractError("Pull request author login is missing.")
    return PullRequestSnapshot(
        number=number,
        state=state,
        base_ref=base_ref,
        head_sha=head_sha,
        draft=draft,
        mergeable=mergeable,
        mergeable_state=mergeable_state,
        author_login=author_login,
    )


def snapshot_reasons(
    snapshot: PullRequestSnapshot, expected_head_sha: str | None
) -> list[str]:
    reasons: list[str] = []
    if snapshot.state != "open":
        reasons.append("pull request is not open")
    if snapshot.base_ref != BASE_BRANCH:
        reasons.append(f"pull request base is not {BASE_BRANCH}")
    if snapshot.draft:
        reasons.append("pull request is a draft")
    if expected_head_sha and snapshot.head_sha != expected_head_sha:
        reasons.append("pull request head does not match the expected bound head")
    if snapshot.mergeable is not True:
        reasons.append("pull request is not currently mergeable")
    if snapshot.mergeable_state != "clean":
        reasons.append(
            f"pull request mergeable_state is {snapshot.mergeable_state!r}, not 'clean'"
        )
    return reasons


def current_approvers(
    client: GitHubClient,
    repository: str,
    pull_request: int,
    head_sha: str,
    author_login: str,
) -> tuple[str, ...]:
    reviews = client.paginate(
        f"repos/{repository}/pulls/{pull_request}/reviews", limit=500
    )
    latest: dict[str, tuple[int, dict[str, Any]]] = {}
    for value in reviews:
        review = require_mapping(value, "pull request review")
        user = require_mapping(review.get("user"), "review user")
        login = user.get("login")
        if not isinstance(login, str) or not login:
            raise ContractError("Review user login is missing.")
        if (
            user.get("type") == "Bot"
            or login.endswith("[bot]")
            or login == author_login
        ):
            continue
        review_id = positive_integer(review.get("id"), "review id")
        previous = latest.get(login)
        if previous is None or review_id > previous[0]:
            latest[login] = (review_id, review)

    approvers: list[str] = []
    for login, (_, review) in sorted(latest.items()):
        if review.get("state") != "APPROVED" or review.get("commit_id") != head_sha:
            continue
        encoded_login = urllib.parse.quote(login, safe="")
        try:
            permission_payload = client.get_json(
                f"repos/{repository}/collaborators/{encoded_login}/permission"
            )
        except GitHubApiError as exc:
            if exc.status == 404:
                continue
            raise
        permission = require_mapping(
            permission_payload, f"collaborator permission for {login}"
        ).get("permission")
        if permission in REVIEWER_PERMISSIONS:
            approvers.append(login)
    return tuple(approvers)


def _latest_by_id(values: list[dict[str, Any]], label: str) -> dict[str, Any]:
    latest: dict[str, Any] | None = None
    latest_id = -1
    for value in values:
        identifier = positive_integer(value.get("id"), f"{label} id")
        if identifier > latest_id:
            latest = value
            latest_id = identifier
    if latest is None:
        raise ContractError(f"Unable to select latest {label}.")
    return latest


def _check_run_outcome(check_run: dict[str, Any], gate: str) -> tuple[str, bool]:
    status = check_run.get("status")
    conclusion = check_run.get("conclusion")
    if not isinstance(status, str):
        raise ContractError(f"{gate} check run status is missing.")
    if conclusion is not None and not isinstance(conclusion, str):
        raise ContractError(f"{gate} check run conclusion is invalid.")
    return (
        f"check:{status}/{conclusion or 'none'}@app:{CI_CHECK_APP_ID}",
        status == "completed" and conclusion == "success",
    )


def _status_outcome(status: dict[str, Any], gate: str) -> tuple[str, bool]:
    state = status.get("state")
    if not isinstance(state, str):
        raise ContractError(f"{gate} status state is missing.")
    return (
        f"status:{state}@{GITHUB_ACTIONS_BOT_LOGIN}:{GITHUB_ACTIONS_BOT_ID}",
        state == "success",
    )


def required_gate_states(
    client: GitHubClient, repository: str, head_sha: str
) -> tuple[dict[str, str], tuple[str, ...]]:
    statuses = client.paginate(
        f"repos/{repository}/commits/{head_sha}/statuses", limit=1000
    )
    check_runs = client.paginate(
        f"repos/{repository}/commits/{head_sha}/check-runs?filter=latest",
        limit=1000,
        key="check_runs",
    )
    status_matches: dict[str, list[dict[str, Any]]] = {
        gate: [] for gate in REQUIRED_GATES
    }
    check_matches: dict[str, list[dict[str, Any]]] = {
        gate: [] for gate in REQUIRED_GATES
    }
    for value in statuses:
        status = require_mapping(value, "commit status")
        context = status.get("context")
        if context in status_matches:
            status_matches[str(context)].append(status)
    for value in check_runs:
        check_run = require_mapping(value, "check run")
        name = check_run.get("name")
        if name in check_matches:
            check_matches[str(name)].append(check_run)

    states: dict[str, str] = {}
    reasons: list[str] = []

    ci_valid: list[dict[str, Any]] = []
    ci_untrusted = len(status_matches["CI gate"])
    for check_run in check_matches["CI gate"]:
        app = check_run.get("app")
        if isinstance(app, dict) and app.get("id") == CI_CHECK_APP_ID:
            ci_valid.append(check_run)
        else:
            ci_untrusted += 1
    if ci_untrusted:
        reasons.append(
            "required gate 'CI gate' has same-name signal(s) from an untrusted provider"
        )
    if not ci_valid:
        states["CI gate"] = "missing-trusted-provider"
        reasons.append("required gate 'CI gate' is missing its trusted check run")
    else:
        ci_signal, ci_success = _check_run_outcome(
            _latest_by_id(ci_valid, "CI gate trusted check run"), "CI gate"
        )
        states["CI gate"] = ci_signal
        if not ci_success:
            reasons.append("required gate 'CI gate' is not successful")

    for gate in ("Agent jury gate", "Agent issue gate"):
        trusted_statuses: list[dict[str, Any]] = []
        untrusted = len(check_matches[gate])
        for status in status_matches[gate]:
            creator = status.get("creator")
            if (
                isinstance(creator, dict)
                and creator.get("login") == GITHUB_ACTIONS_BOT_LOGIN
                and creator.get("id") == GITHUB_ACTIONS_BOT_ID
                and creator.get("type") == "Bot"
            ):
                trusted_statuses.append(status)
            else:
                untrusted += 1
        if untrusted:
            reasons.append(
                f"required gate {gate!r} has same-name signal(s) from an untrusted provider"
            )
        if not trusted_statuses:
            states[gate] = "missing-trusted-provider"
            reasons.append(f"required gate {gate!r} is missing its trusted status")
            continue
        signal, success = _status_outcome(
            _latest_by_id(trusted_statuses, f"{gate} trusted status"), gate
        )
        states[gate] = signal
        if not success:
            reasons.append(f"required gate {gate!r} is not successful")
    return states, tuple(reasons)


REVIEW_STATE_QUERY = """
query($owner: String!, $name: String!, $number: Int!, $cursor: String) {
  repository(owner: $owner, name: $name) {
    pullRequest(number: $number) {
      reviewDecision
      reviewThreads(first: 100, after: $cursor) {
        nodes { isResolved }
        pageInfo { hasNextPage endCursor }
      }
    }
  }
}
"""


def pull_request_review_state(
    client: GitHubClient, repository: str, pull_request: int
) -> ReviewState:
    owner, name = repository.split("/", 1)
    cursor: str | None = None
    seen_cursors: set[str] = set()
    unresolved = 0
    total = 0
    review_decision: str | None = None
    while True:
        data = client.graphql(
            REVIEW_STATE_QUERY,
            {"owner": owner, "name": name, "number": pull_request, "cursor": cursor},
        )
        repository_node = require_mapping(data.get("repository"), "GraphQL repository")
        pull_request_node = require_mapping(
            repository_node.get("pullRequest"), "GraphQL pull request"
        )
        page_decision = pull_request_node.get("reviewDecision")
        if page_decision is not None and not isinstance(page_decision, str):
            raise ContractError("GraphQL reviewDecision is invalid.")
        normalized_decision = page_decision or "NONE"
        if review_decision is None:
            review_decision = normalized_decision
        elif review_decision != normalized_decision:
            raise ContractError("GraphQL reviewDecision changed during pagination.")
        connection = require_mapping(
            pull_request_node.get("reviewThreads"), "GraphQL reviewThreads"
        )
        nodes = require_list(connection.get("nodes"), "GraphQL review thread nodes")
        for value in nodes:
            thread = require_mapping(value, "GraphQL review thread")
            is_resolved = thread.get("isResolved")
            if type(is_resolved) is not bool:
                raise ContractError("GraphQL review thread resolution is invalid.")
            total += 1
            if total > 1000:
                raise AutoMergeError("Review thread count exceeded the safety limit.")
            if not is_resolved:
                unresolved += 1
        page_info = require_mapping(
            connection.get("pageInfo"), "GraphQL review thread pageInfo"
        )
        has_next_page = page_info.get("hasNextPage")
        if type(has_next_page) is not bool:
            raise ContractError("GraphQL review thread pagination is invalid.")
        if not has_next_page:
            return ReviewState(review_decision or "NONE", unresolved)
        end_cursor = page_info.get("endCursor")
        if (
            not isinstance(end_cursor, str)
            or not end_cursor
            or end_cursor in seen_cursors
        ):
            raise ContractError("GraphQL review thread cursor is invalid.")
        seen_cursors.add(end_cursor)
        cursor = end_cursor


def open_bound_agent_issues(
    client: GitHubClient,
    repository: str,
    pull_request: int,
    expected_app_login: str,
    expected_app_bot_id: int,
) -> tuple[int, ...]:
    encoded_creator = urllib.parse.quote(expected_app_login, safe="")
    issues = client.paginate(
        f"repos/{repository}/issues?state=open&creator={encoded_creator}"
        "&sort=created&direction=asc",
        limit=5000,
    )
    bound: list[int] = []
    finding_ids: set[str] = set()
    for value in issues:
        issue = require_mapping(value, "open Agent Issue")
        if "pull_request" in issue:
            continue
        actor = issue.get("user")
        actor_login = actor.get("login") if isinstance(actor, dict) else None
        if actor_login != expected_app_login:
            continue
        try:
            require_resource_actor(
                issue,
                expected_app_login,
                expected_app_bot_id,
                "Open bound Agent review finding issue",
            )
        except AgentReviewError as exc:
            raise ContractError(str(exc)) from exc
        number = positive_integer(issue.get("number"), "Agent Issue number")
        marker = parse_agent_issue_marker(issue.get("body"))
        if marker is None:
            continue
        if marker["pull_request"] != pull_request:
            continue
        labels = require_list(issue.get("labels", []), f"Agent Issue #{number} labels")
        label_names = {
            str(require_mapping(label, "Agent Issue label").get("name", ""))
            for label in labels
        }
        if AGENT_ISSUE_LABEL not in label_names:
            raise ContractError(
                f"Open bound Agent Issue #{number} is missing label {AGENT_ISSUE_LABEL!r}."
            )
        finding_id = str(marker["finding_id"])
        if finding_id in finding_ids:
            raise ContractError(
                f"Duplicate open Agent Issues bind finding_id {finding_id!r}."
            )
        finding_ids.add(finding_id)
        bound.append(number)
    return tuple(sorted(bound))


def repository_merge_settings(
    client: GitHubClient, repository: str
) -> RepositoryMergeSettings:
    payload = require_mapping(
        client.get_json(f"repos/{repository}"), "repository merge settings"
    )
    values: dict[str, bool] = {}
    for name in ("allow_merge_commit", "allow_squash_merge", "allow_rebase_merge"):
        value = payload.get(name)
        if type(value) is not bool:
            raise ContractError(f"Repository setting {name} is missing or invalid.")
        values[name] = value
    return RepositoryMergeSettings(**values)


def merge_setting_reasons(settings: RepositoryMergeSettings) -> tuple[str, ...]:
    reasons: list[str] = []
    if not settings.allow_merge_commit:
        reasons.append("repository does not allow merge commits")
    if settings.allow_squash_merge:
        reasons.append("repository still allows squash merging")
    if settings.allow_rebase_merge:
        reasons.append("repository still allows rebase merging")
    return tuple(reasons)


def evaluate_eligibility(
    client: GitHubClient,
    repository: str,
    candidate: Candidate,
    expected_head_sha: str | None,
    expected_app_login: str,
    expected_app_bot_id: int,
) -> Eligibility:
    snapshot = pull_request_snapshot(
        client.get_json(f"repos/{repository}/pulls/{candidate.number}"),
        candidate.number,
    )
    snapshot_failures = snapshot_reasons(snapshot, expected_head_sha)
    if snapshot_failures:
        return Eligibility(snapshot=snapshot, reasons=tuple(snapshot_failures))

    review_state = pull_request_review_state(client, repository, candidate.number)
    approvers = current_approvers(
        client,
        repository,
        candidate.number,
        snapshot.head_sha,
        snapshot.author_login,
    )
    gates, gate_reasons = required_gate_states(client, repository, snapshot.head_sha)
    open_issues = open_bound_agent_issues(
        client,
        repository,
        candidate.number,
        expected_app_login,
        expected_app_bot_id,
    )
    merge_settings = repository_merge_settings(client, repository)

    reasons = list(gate_reasons)
    if review_state.decision != "APPROVED":
        reasons.append(
            f"GraphQL reviewDecision is {review_state.decision!r}, not 'APPROVED'"
        )
    if not approvers:
        reasons.append(
            "no current valid non-bot maintainer approval exists for the current head"
        )
    if review_state.unresolved_threads:
        reasons.append(
            f"{review_state.unresolved_threads} review thread(s) remain unresolved"
        )
    if open_issues:
        reasons.append("open bound Agent Issue(s) remain")
    reasons.extend(merge_setting_reasons(merge_settings))
    return Eligibility(
        snapshot=snapshot,
        reasons=tuple(reasons),
        approvers=approvers,
        gates=gates,
        review_decision=review_state.decision,
        unresolved_review_threads=review_state.unresolved_threads,
        open_agent_issues=open_issues,
        merge_settings=merge_settings,
    )


def decision_from_eligibility(
    state: str, candidate: Candidate, eligibility: Eligibility
) -> Decision:
    return Decision(
        state=state,
        pull_request=candidate.number,
        head_sha=eligibility.snapshot.head_sha,
        source=candidate.source,
        reasons=eligibility.reasons,
        approvers=eligibility.approvers,
        gates=eligibility.gates,
        review_decision=eligibility.review_decision,
        unresolved_review_threads=eligibility.unresolved_review_threads,
        open_agent_issues=eligibility.open_agent_issues,
        merge_settings=(
            eligibility.merge_settings.as_dict() if eligibility.merge_settings else {}
        ),
    )


def evaluate_candidate(
    read_client: GitHubClient,
    merge_client: GitHubClient,
    repository: str,
    candidate: Candidate,
    expected_app_login: str,
    expected_app_bot_id: int,
    *,
    dry_run: bool,
) -> Decision:
    first = evaluate_eligibility(
        read_client,
        repository,
        candidate,
        candidate.expected_head_sha,
        expected_app_login,
        expected_app_bot_id,
    )
    if first.reasons:
        return decision_from_eligibility("blocked", candidate, first)

    second = evaluate_eligibility(
        read_client,
        repository,
        candidate,
        first.snapshot.head_sha,
        expected_app_login,
        expected_app_bot_id,
    )
    if second.reasons:
        return decision_from_eligibility("blocked", candidate, second)

    if dry_run:
        return decision_from_eligibility("dry-run", candidate, second)

    response = require_mapping(
        merge_client.send_json(
            "PUT",
            f"repos/{repository}/pulls/{candidate.number}/merge",
            {"sha": second.snapshot.head_sha, "merge_method": "merge"},
        ),
        "GitHub merge response",
    )
    if response.get("merged") is not True:
        message = response.get("message")
        detail = f": {message}" if isinstance(message, str) and message else ""
        raise AutoMergeError(f"GitHub refused to merge PR #{candidate.number}{detail}.")
    merge_sha = response.get("sha")
    if not isinstance(merge_sha, str) or not SHA_RE.fullmatch(merge_sha):
        raise ContractError("GitHub merge response did not contain a valid merge SHA.")
    return Decision(
        state="merged",
        pull_request=candidate.number,
        head_sha=second.snapshot.head_sha,
        source=candidate.source,
        approvers=second.approvers,
        gates=second.gates,
        review_decision=second.review_decision,
        unresolved_review_threads=second.unresolved_review_threads,
        open_agent_issues=second.open_agent_issues,
        merge_settings=(
            second.merge_settings.as_dict() if second.merge_settings else {}
        ),
        merge_sha=merge_sha,
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", default=os.environ.get("GITHUB_REPOSITORY", ""))
    parser.add_argument("--event-name", default=os.environ.get("GITHUB_EVENT_NAME", ""))
    parser.add_argument(
        "--event-path",
        type=Path,
        default=(
            Path(os.environ["GITHUB_EVENT_PATH"])
            if os.environ.get("GITHUB_EVENT_PATH")
            else None
        ),
    )
    parser.add_argument("--pr-number", type=int)
    parser.add_argument("--expected-head-sha")
    parser.add_argument("--expected-app-login", required=True)
    parser.add_argument("--expected-app-bot-id", required=True, type=int)
    parser.add_argument("--dry-run", action="store_true")
    return parser


def command_run(args: argparse.Namespace) -> int:
    repository = str(args.repository)
    if not REPOSITORY_RE.fullmatch(repository):
        raise ContractError("repository must use the owner/name form.")
    event_name = str(args.event_name)
    if not event_name and args.pr_number is not None:
        event_name = "workflow_dispatch"
    if not event_name:
        raise ContractError("GitHub event name is required.")
    event = read_event(args.event_path)
    read_client = GitHubClient(
        os.environ.get("GH_TOKEN", ""),
        os.environ.get("GITHUB_API_URL", "https://api.github.com"),
        os.environ.get("GITHUB_GRAPHQL_URL", "https://api.github.com/graphql"),
    )
    merge_client = GitHubClient(
        os.environ.get("AGENT_GH_TOKEN", ""),
        os.environ.get("GITHUB_API_URL", "https://api.github.com"),
        os.environ.get("GITHUB_GRAPHQL_URL", "https://api.github.com/graphql"),
    )
    expected_app_login = str(args.expected_app_login)
    if not expected_app_login.endswith("[bot]"):
        raise ContractError("expected app login must be a GitHub App bot login.")
    expected_app_bot_id = positive_integer(
        args.expected_app_bot_id, "expected app bot id"
    )
    candidates = resolve_candidates(
        read_client,
        repository,
        event_name,
        event,
        explicit_pr_number=args.pr_number,
        explicit_head_sha=args.expected_head_sha,
    )
    if not candidates:
        print(canonical_json({"state": "no-candidates"}))
        return 0
    for candidate in candidates:
        decision = evaluate_candidate(
            read_client,
            merge_client,
            repository,
            candidate,
            expected_app_login,
            expected_app_bot_id,
            dry_run=bool(args.dry_run),
        )
        print(canonical_json(decision.as_dict()))
    return 0


def main() -> int:
    try:
        return command_run(build_parser().parse_args())
    except AutoMergeError as exc:
        print(
            canonical_json({"error": str(exc), "state": "failed-closed"}),
            file=sys.stderr,
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
