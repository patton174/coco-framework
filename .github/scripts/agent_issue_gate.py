#!/usr/bin/env python3
"""Recompute the Agent issue gate from strictly bound governance issues."""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path
from typing import Any

from agent_review import (
    FINDING_ISSUE_LABEL,
    ISSUE_STATUS_CONTEXT,
    SHA_RE,
    GitHubClient,
    ReviewError,
    app_finding_issues,
    canonical_json,
    issue_label_names,
    parse_finding_issue_marker,
    publish_status,
    read_json,
    require_app_bot_id,
    require_app_bot_login,
    require_repository,
    require_resource_actor,
)


def strict_positive_int(value: Any, label: str) -> int:
    if type(value) is not int or value < 1:
        raise ReviewError(f"{label} is invalid.")
    return value


def dispatch_positive_int(value: Any, label: str) -> int:
    if type(value) is int:
        return strict_positive_int(value, label)
    if isinstance(value, str) and re.fullmatch(r"[1-9][0-9]*", value):
        return int(value)
    raise ReviewError(f"{label} is invalid.")


def event_repository(event: dict[str, Any]) -> str:
    repository = event.get("repository")
    if not isinstance(repository, dict):
        raise ReviewError("GitHub event repository is missing.")
    return require_repository(repository.get("full_name"))


def issue_author_login(issue: Any) -> str:
    if not isinstance(issue, dict) or not isinstance(issue.get("user"), dict):
        return ""
    return str(issue["user"].get("login") or "")


def resolve_event(event: dict[str, Any], expected_app_login: str) -> dict[str, Any]:
    repository = event_repository(event)
    expected_login = require_app_bot_login(expected_app_login)
    pull_request = event.get("pull_request")
    if isinstance(pull_request, dict):
        number = strict_positive_int(pull_request.get("number"), "Pull request number")
        head_sha = str((pull_request.get("head") or {}).get("sha") or "")
        if not SHA_RE.fullmatch(head_sha):
            raise ReviewError("Pull request event head SHA is invalid.")
        if (pull_request.get("base") or {}).get("ref") != "main":
            return {"ignored": True, "repository": repository}
        return {
            "ignored": False,
            "repository": repository,
            "pr_number": number,
            "expected_head_sha": head_sha,
        }

    inputs = event.get("inputs")
    if isinstance(inputs, dict) and ("pr_number" in inputs or "head_sha" in inputs):
        number = dispatch_positive_int(
            inputs.get("pr_number"), "Workflow dispatch pull request number"
        )
        head_sha = str(inputs.get("head_sha") or "")
        if not SHA_RE.fullmatch(head_sha):
            raise ReviewError("Workflow dispatch head SHA is invalid.")
        return {
            "ignored": False,
            "repository": repository,
            "pr_number": number,
            "expected_head_sha": head_sha,
        }

    issue = event.get("issue")
    if isinstance(issue, dict):
        if issue_author_login(issue) != expected_login:
            return {"ignored": True, "repository": repository}
        body = issue.get("body") if isinstance(issue.get("body"), str) else ""
        marker = None
        current_marker_error: ReviewError | None = None
        try:
            marker = parse_finding_issue_marker(body)
        except ReviewError as exc:
            current_marker_error = exc
        if marker is None:
            changes = event.get("changes")
            previous_body = (
                ((changes or {}).get("body") or {}).get("from")
                if isinstance(changes, dict)
                else None
            )
            previous_text = previous_body if isinstance(previous_body, str) else ""
            marker = parse_finding_issue_marker(previous_text)
        if marker is None and current_marker_error is not None:
            raise current_marker_error
        if marker is None:
            return {"ignored": True, "repository": repository}
        return {
            "ignored": False,
            "repository": repository,
            "pr_number": strict_positive_int(
                marker.get("pull_request"), "Finding issue pull request number"
            ),
            "expected_head_sha": "",
        }
    return {"ignored": True, "repository": repository}


def append_github_output(path: Path, values: dict[str, str]) -> None:
    try:
        with path.open("a", encoding="utf-8") as output:
            for key, value in values.items():
                if "\n" in value or "\r" in value:
                    raise ReviewError("GitHub output value contains a newline.")
                output.write(f"{key}={value}\n")
    except OSError as exc:
        raise ReviewError("Unable to write Agent issue gate outputs.") from exc


def command_resolve(args: argparse.Namespace) -> int:
    event = read_json(args.event_path)
    if not isinstance(event, dict):
        raise ReviewError("GitHub event payload must be an object.")
    resolved = resolve_event(event, args.expected_app_login)
    values = {
        "ignored": str(bool(resolved["ignored"])).lower(),
        "pr-number": str(resolved.get("pr_number", "")),
        "expected-head-sha": str(resolved.get("expected_head_sha", "")),
    }
    append_github_output(args.github_output, values)
    print(canonical_json(resolved))
    return 0


def bind_current_pr(
    client: GitHubClient,
    repository: str,
    pr_number: int,
    expected_head_sha: str,
) -> dict[str, Any] | None:
    pr = client.get_json(f"repos/{repository}/pulls/{pr_number}")
    if not isinstance(pr, dict):
        raise ReviewError("GitHub returned an invalid pull request.")
    if pr.get("state") != "open":
        if expected_head_sha:
            raise ReviewError("Bound pull request is no longer open.")
        return None
    base = pr.get("base") or {}
    head = pr.get("head") or {}
    base_sha = str(base.get("sha") or "")
    head_sha = str(head.get("sha") or "")
    if base.get("ref") != "main":
        raise ReviewError("Agent issue gate accepts only pull requests targeting main.")
    if not SHA_RE.fullmatch(base_sha) or not SHA_RE.fullmatch(head_sha):
        raise ReviewError("GitHub returned invalid pull request commit SHAs.")
    if expected_head_sha and expected_head_sha != head_sha:
        raise ReviewError("Agent issue gate event head SHA is stale.")
    return {"pr": pr, "base_sha": base_sha, "head_sha": head_sha}


def require_same_pr_binding(
    client: GitHubClient,
    repository: str,
    pr_number: int,
    binding: dict[str, Any],
) -> None:
    current = client.get_json(f"repos/{repository}/pulls/{pr_number}")
    if (
        not isinstance(current, dict)
        or current.get("state") != "open"
        or (current.get("base") or {}).get("ref") != "main"
        or (current.get("base") or {}).get("sha") != binding["base_sha"]
        or (current.get("head") or {}).get("sha") != binding["head_sha"]
    ):
        raise ReviewError("Pull request changed before Agent issue gate publication.")


def current_event_issue(
    client: GitHubClient,
    repository: str,
    pr_number: int,
    event_path: Path | None,
    expected_login: str,
    expected_bot_id: int,
) -> dict[str, Any] | None:
    if event_path is None:
        return None
    event = read_json(event_path)
    if not isinstance(event, dict) or not isinstance(event.get("issue"), dict):
        return None
    if event_repository(event) != repository:
        raise ReviewError(
            "Issue event repository does not match the workflow repository."
        )
    event_issue = event["issue"]
    if issue_author_login(event_issue) != expected_login:
        return None
    require_resource_actor(
        event_issue, expected_login, expected_bot_id, "Agent review finding issue"
    )
    issue_number = strict_positive_int(event_issue.get("number"), "Issue number")
    action = str(event.get("action") or "")
    issue = (
        event_issue
        if action in {"deleted", "transferred"}
        else client.get_json(f"repos/{repository}/issues/{issue_number}")
    )
    if not isinstance(issue, dict) or issue.get("pull_request"):
        raise ReviewError("Agent issue gate event does not reference an issue.")
    require_resource_actor(
        issue, expected_login, expected_bot_id, "Agent review finding issue"
    )
    marker = parse_finding_issue_marker(issue.get("body"))
    if marker is None or marker["pull_request"] != pr_number:
        raise ReviewError("Issue event binding does not match the target pull request.")
    if FINDING_ISSUE_LABEL not in issue_label_names(issue):
        raise ReviewError("Bound Agent review issue is missing the required label.")
    return issue


def open_bound_issues(
    client: GitHubClient,
    repository: str,
    pr_number: int,
    expected_login: str,
    expected_bot_id: int,
) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    issues = app_finding_issues(
        client, repository, pr_number, expected_login, expected_bot_id
    )
    for finding_id, issue in issues.items():
        state = str(issue.get("state") or "")
        if state == "closed":
            continue
        if state != "open":
            raise ReviewError("Agent review finding issue state is invalid.")
        if FINDING_ISSUE_LABEL not in issue_label_names(issue):
            raise ReviewError("Open Agent review issue is missing the required label.")
        if finding_id in result:
            raise ReviewError("Duplicate open Agent review issues bind one finding ID.")
        result[finding_id] = issue
    return result


def command_recompute(args: argparse.Namespace) -> int:
    repository = require_repository(args.repository)
    pr_number = strict_positive_int(args.pr_number, "Pull request number")
    expected_head_sha = str(args.expected_head_sha or "")
    if expected_head_sha and not SHA_RE.fullmatch(expected_head_sha):
        raise ReviewError("Expected Agent issue gate head SHA is invalid.")
    client = GitHubClient(
        os.environ.get("GH_TOKEN", ""),
        os.environ.get("GITHUB_API_URL", "https://api.github.com"),
    )
    binding = bind_current_pr(client, repository, pr_number, expected_head_sha)
    if binding is None:
        print(canonical_json({"state": "ignored", "reason": "pull request closed"}))
        return 0

    try:
        expected_login = require_app_bot_login(args.expected_app_login)
        expected_bot_id = require_app_bot_id(args.expected_app_bot_id)
        current_event_issue(
            client,
            repository,
            pr_number,
            args.event_path,
            expected_login,
            expected_bot_id,
        )
        open_issues = open_bound_issues(
            client,
            repository,
            pr_number,
            expected_login,
            expected_bot_id,
        )
    except ReviewError:
        require_same_pr_binding(client, repository, pr_number, binding)
        publish_status(
            client,
            repository,
            binding["head_sha"],
            "failure",
            "Agent issue gate validation failed",
            args.run_url,
            ISSUE_STATUS_CONTEXT,
        )
        raise

    open_count = len(open_issues)
    state = "failure" if open_count else "success"
    description = (
        f"{open_count} open Agent review issue(s)"
        if open_count
        else "No open Agent review issues"
    )
    require_same_pr_binding(client, repository, pr_number, binding)
    publish_status(
        client,
        repository,
        binding["head_sha"],
        state,
        description,
        args.run_url,
        ISSUE_STATUS_CONTEXT,
    )
    print(
        canonical_json(
            {
                "state": state,
                "description": description,
                "head_sha": binding["head_sha"],
                "open_agent_review_issues": open_count,
            }
        )
    )
    return 0


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    commands = result.add_subparsers(dest="command", required=True)

    resolve = commands.add_parser("resolve")
    resolve.add_argument("--event-path", required=True, type=Path)
    resolve.add_argument("--github-output", required=True, type=Path)
    resolve.add_argument("--expected-app-login", required=True)
    resolve.set_defaults(handler=command_resolve)

    recompute = commands.add_parser("recompute")
    recompute.add_argument("--repository", required=True)
    recompute.add_argument("--pr-number", required=True, type=int)
    recompute.add_argument("--expected-head-sha", default="")
    recompute.add_argument("--expected-app-login", required=True)
    recompute.add_argument("--expected-app-bot-id", required=True)
    recompute.add_argument("--event-path", type=Path)
    recompute.add_argument("--run-url", required=True)
    recompute.set_defaults(handler=command_recompute)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        return int(args.handler(args))
    except ReviewError as exc:
        print(f"::error::{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
