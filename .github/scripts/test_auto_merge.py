#!/usr/bin/env python3

from __future__ import annotations

import copy
import json
import os
import subprocess
import unittest
import urllib.parse
from datetime import datetime, timezone
from pathlib import Path
from unittest import mock

import auto_merge as merge


HEAD_SHA = "b" * 40
OTHER_SHA = "c" * 40
BASE_SHA = "a" * 40
OTHER_BASE_SHA = "f" * 40
MERGE_SHA = "d" * 40
REPOSITORY = "patton174/coco-framework"
REPOSITORY_OWNER = "patton174"
FINDING_ID = "v1-" + "e" * 64
APP_LOGIN = "coco-agent[bot]"
APP_BOT_ID = 123456789
INCIDENT_ISSUE_NUMBER = 366
NOW = datetime(2026, 8, 17, 12, 0, 0, tzinfo=timezone.utc)


def pull_request(**overrides: object) -> dict:
    value = {
        "number": 17,
        "state": "open",
        "base": {"ref": "main", "sha": BASE_SHA},
        "head": {"sha": HEAD_SHA},
        "draft": False,
        "mergeable": True,
        "mergeable_state": "clean",
        "user": {"login": "author", "type": "User"},
    }
    value.update(overrides)
    return value


def marker(
    pull_request_number: int = 17,
    head_sha: str = HEAD_SHA,
    finding_id: str = FINDING_ID,
) -> str:
    return (
        '<!-- coco-agent-review: {"schema_version":1,"pull_request":'
        f'{pull_request_number},"head_sha":"{head_sha}",'
        f'"finding_id":"{finding_id}"}} -->'
    )


def incident_payload(**overrides: object) -> dict[str, object]:
    payload = {
        "schema_version": merge.INCIDENT_SCHEMA_VERSION,
        "repository": REPOSITORY,
        "base_sha": BASE_SHA,
        "pull_request": 17,
        "head_sha": HEAD_SHA,
        "missing_context": merge.INCIDENT_MISSING_CONTEXT,
        "issued_at": "2026-08-17T11:00:00Z",
        "expires_at": "2026-08-17T13:00:00Z",
    }
    payload.update(overrides)
    return payload


def incident_marker(**overrides: object) -> str:
    return (
        f"{merge.INCIDENT_MARKER_PREFIX}"
        f"{merge.canonical_json(incident_payload(**overrides))} -->"
    )


def incident_issue(**overrides: object) -> dict:
    value = {
        "number": INCIDENT_ISSUE_NUMBER,
        "state": "open",
        "body": incident_marker(),
        "author_association": "OWNER",
        "user": {"login": "patton174", "type": "User"},
    }
    value.update(overrides)
    return value


def app_actor(
    login: str = APP_LOGIN, bot_id: int = APP_BOT_ID, actor_type: str = "Bot"
) -> dict:
    return {"login": login, "id": bot_id, "type": actor_type}


def approval(
    login: str = "maintainer",
    *,
    review_id: int = 1,
    head_sha: str = HEAD_SHA,
    actor_type: str = "User",
) -> dict:
    return {
        "id": review_id,
        "state": "APPROVED",
        "commit_id": head_sha,
        "user": {"login": login, "type": actor_type},
    }


def incident_invocation(
    *,
    event_name: str = "workflow_dispatch",
    actor: str = REPOSITORY_OWNER,
    triggering_actor: str = REPOSITORY_OWNER,
    repository_owner: str = REPOSITORY_OWNER,
) -> merge.IncidentInvocation:
    return merge.IncidentInvocation(
        event_name, actor, triggering_actor, repository_owner
    )


def finding_issue(
    *,
    number: int = 99,
    pull_request_number: int = 17,
    head_sha: str = HEAD_SHA,
    labels: list[dict] | None = None,
    user: dict | None = None,
) -> dict:
    return {
        "number": number,
        "body": marker(pull_request_number, head_sha),
        "labels": [{"name": "agent-review"}] if labels is None else labels,
        "user": app_actor() if user is None else user,
    }


def status_signal(
    identifier: int,
    context: str,
    state: str = "success",
    *,
    creator_id: int = merge.GITHUB_ACTIONS_BOT_ID,
    creator_login: str = merge.GITHUB_ACTIONS_BOT_LOGIN,
    creator_type: str = "Bot",
) -> dict:
    return {
        "id": identifier,
        "context": context,
        "state": state,
        "creator": {
            "id": creator_id,
            "login": creator_login,
            "type": creator_type,
        },
    }


def success_status(identifier: int, context: str) -> dict:
    return status_signal(identifier, context)


def check_signal(
    identifier: int,
    name: str,
    conclusion: str = "success",
    *,
    app_id: int = merge.CI_CHECK_APP_ID,
) -> dict:
    return {
        "id": identifier,
        "name": name,
        "status": "completed",
        "conclusion": conclusion,
        "app": {"id": app_id},
    }


def success_check(identifier: int, name: str) -> dict:
    return check_signal(identifier, name)


def required_check_configuration(
    gates: tuple[str, ...] = merge.STANDARD_REQUIRED_GATES,
    *,
    strict: bool = True,
    contexts: list[object] | None = None,
    checks: list[object] | None = None,
) -> dict:
    return {
        "strict": strict,
        "contexts": list(gates) if contexts is None else contexts,
        "checks": (
            [{"context": gate, "app_id": merge.CI_CHECK_APP_ID} for gate in gates]
            if checks is None
            else checks
        ),
    }


def graphql_state(
    decision: str | None = "APPROVED", resolved: list[bool] | None = None
) -> dict:
    return {
        "repository": {
            "pullRequest": {
                "reviewDecision": decision,
                "reviewThreads": {
                    "nodes": [
                        {"isResolved": is_resolved} for is_resolved in (resolved or [])
                    ],
                    "pageInfo": {"hasNextPage": False, "endCursor": None},
                },
            }
        }
    }


class MergeOnlyClient:
    def __init__(self) -> None:
        self.sent: list[tuple[str, str, dict]] = []
        self.read_attempts = 0
        self.merge_response = {"merged": True, "sha": MERGE_SHA}

    def _reject_read(self) -> None:
        self.read_attempts += 1
        raise AssertionError("merge client must not perform reads")

    def get_json(self, path: str) -> object:
        del path
        self._reject_read()

    def paginate(self, path: str, **kwargs: object) -> list[object]:
        del path, kwargs
        self._reject_read()

    def graphql(self, query: str, variables: dict) -> dict:
        del query, variables
        self._reject_read()

    def send_json(self, method: str, path: str, payload: dict) -> dict:
        if method != "PUT" or path != f"repos/{REPOSITORY}/pulls/17/merge":
            raise AssertionError("merge client may only call the merge endpoint")
        self.sent.append((method, path, copy.deepcopy(payload)))
        return copy.deepcopy(self.merge_response)


class ProtectionOnlyClient:
    def __init__(self) -> None:
        self.configuration = required_check_configuration()
        self.configuration_pages: list[dict] | None = None
        self.error: Exception | None = None
        self.reads = 0
        self.branches_read: list[str] = []

    def required_status_checks(
        self, repository: str, branch: str = merge.BASE_BRANCH
    ) -> object:
        if repository != REPOSITORY:
            raise AssertionError("protection client received another repository")
        # Record which branch's contract was requested so tests can assert the
        # read follows the pull request's base rather than a fixed branch.
        self.branches_read.append(branch)
        self.reads += 1
        if self.error is not None:
            raise self.error
        if self.configuration_pages is not None:
            if not self.configuration_pages:
                raise AssertionError(
                    "unexpected extra required check configuration read"
                )
            payload = self.configuration_pages.pop(0)
        else:
            payload = self.configuration
        return copy.deepcopy(payload)


class FakeClient:
    def __init__(self) -> None:
        self.pull_reads = [pull_request(), pull_request()]
        self.reviews = [approval()]
        self.statuses = [
            success_status(10, "Agent jury gate"),
            success_status(11, "Agent issue gate"),
        ]
        self.check_runs = [success_check(20, "CI gate")]
        self.issues: list[dict] = []
        self.open_pulls: list[dict] = []
        # Candidates per governed base. `open_pulls` stays the release-branch
        # fixture so existing expectations hold; other bases are set per test.
        self.open_pulls_by_base: dict[str, list[dict]] = {}
        self.scanned_bases: list[str] = []
        self.permissions = {"maintainer": "write"}
        self.repository_settings = {
            "mergeCommitAllowed": True,
            "squashMergeAllowed": False,
            "rebaseMergeAllowed": False,
        }
        self.incident_issue = incident_issue()
        self.incident_issue_pages: list[dict] | None = None
        self.incident_issue_error: Exception | None = None
        self.incident_issue_reads = 0
        self.review_pages: list[list[dict]] | None = None
        self.status_pages: list[list[dict]] | None = None
        self.check_run_pages: list[list[dict]] | None = None
        self.issue_pages: list[list[dict]] | None = None
        self.repository_setting_pages: list[dict] | None = None
        self.thread_pages = [graphql_state(), graphql_state()]
        self.graphql_calls = 0
        self.repository_reads = 0
        self.protection_client = ProtectionOnlyClient()
        self.merge_client = MergeOnlyClient()
        self.sent = self.merge_client.sent

    def get_json(self, path: str) -> object:
        if path == f"repos/{REPOSITORY}/pulls/17":
            if not self.pull_reads:
                raise AssertionError("unexpected extra pull request read")
            return copy.deepcopy(self.pull_reads.pop(0))
        permission_marker = f"repos/{REPOSITORY}/collaborators/"
        if path.startswith(permission_marker) and path.endswith("/permission"):
            login = path[len(permission_marker) : -len("/permission")]
            permission = self.permissions.get(login)
            if permission is None:
                raise merge.GitHubApiError("not found", 404)
            return {"permission": permission}
        if path == f"repos/{REPOSITORY}/issues/{INCIDENT_ISSUE_NUMBER}":
            self.incident_issue_reads += 1
            if self.incident_issue_error is not None:
                raise self.incident_issue_error
            if self.incident_issue_pages is not None:
                if not self.incident_issue_pages:
                    raise AssertionError("unexpected extra incident Issue read")
                payload = self.incident_issue_pages.pop(0)
            else:
                payload = self.incident_issue
            return copy.deepcopy(payload)
        raise AssertionError(f"unexpected get_json path: {path}")

    def paginate(
        self, path: str, *, limit: int = 1000, key: str | None = None
    ) -> list[object]:
        del limit
        if path.endswith("/reviews"):
            return self.next_page(self.review_pages, self.reviews, "reviews")
        if path.endswith(f"/commits/{HEAD_SHA}/statuses"):
            return self.next_page(self.status_pages, self.statuses, "statuses")
        if "/check-runs?filter=latest" in path:
            self.assert_check_key(key)
            return self.next_page(self.check_run_pages, self.check_runs, "check runs")
        if "/issues?state=open&labels=agent-review&sort=created&direction=asc" in path:
            return self.next_page(self.issue_pages, self.issues, "issues")
        base_marker = "/pulls?state=open&base="
        if base_marker in path:
            # URL-decode and stop at the next parameter so a future encoded base
            # cannot silently stop matching and hide drift in the multi-base scan.
            raw_base = path.split(base_marker, 1)[1].split("&", 1)[0]
            base = urllib.parse.unquote(raw_base)
            self.scanned_bases.append(base)
            if base == "main":
                return copy.deepcopy(self.open_pulls)
            return copy.deepcopy(self.open_pulls_by_base.get(base, []))
        raise AssertionError(f"unexpected paginate path: {path}")

    @staticmethod
    def next_page(
        pages: list[list[dict]] | None, fallback: list[dict], label: str
    ) -> list[object]:
        if pages is None:
            return copy.deepcopy(fallback)
        if not pages:
            raise AssertionError(f"unexpected extra {label} page")
        return copy.deepcopy(pages.pop(0))

    @staticmethod
    def assert_check_key(key: str | None) -> None:
        if key != "check_runs":
            raise AssertionError(f"unexpected check run key: {key}")

    def graphql(self, query: str, variables: dict) -> dict:
        if "reviewThreads" in query:
            self.assert_review_query(query, variables)
            if not self.thread_pages:
                raise AssertionError("unexpected extra GraphQL call")
            self.graphql_calls += 1
            return copy.deepcopy(self.thread_pages.pop(0))
        if "mergeCommitAllowed" in query:
            self.assert_repository_settings_query(query, variables)
            self.repository_reads += 1
            if self.repository_setting_pages is not None:
                if not self.repository_setting_pages:
                    raise AssertionError("unexpected extra repository settings read")
                settings = self.repository_setting_pages.pop(0)
            else:
                settings = self.repository_settings
            return {"repository": copy.deepcopy(settings)}
        raise AssertionError("unexpected GraphQL request")

    @staticmethod
    def assert_review_query(query: str, variables: dict) -> None:
        if (
            "reviewThreads" not in query
            or "reviewDecision" not in query
            or variables["number"] != 17
        ):
            raise AssertionError("unexpected GraphQL request")

    @staticmethod
    def assert_repository_settings_query(query: str, variables: dict) -> None:
        required_fields = {
            "mergeCommitAllowed",
            "squashMergeAllowed",
            "rebaseMergeAllowed",
        }
        owner, name = REPOSITORY.split("/", 1)
        if not all(field in query for field in required_fields) or variables != {
            "owner": owner,
            "name": name,
        }:
            raise AssertionError("unexpected repository settings GraphQL request")

    def send_json(self, method: str, path: str, payload: dict) -> dict:
        del method, path, payload
        raise AssertionError("read client must not perform writes")


class AutoMergeTests(unittest.TestCase):
    def candidate(self, head_sha: str | None = HEAD_SHA) -> merge.Candidate:
        return merge.Candidate(17, head_sha, "test")

    def incident_client(self) -> FakeClient:
        client = FakeClient()
        client.pull_reads = [
            pull_request(user=app_actor()),
            pull_request(user=app_actor()),
        ]
        client.reviews = [approval(REPOSITORY_OWNER)]
        client.permissions = {REPOSITORY_OWNER: "admin"}
        client.protection_client.configuration = required_check_configuration(
            merge.INCIDENT_REQUIRED_GATES
        )
        client.statuses = [success_status(11, "Agent issue gate")]
        return client

    def evaluate(
        self,
        client: FakeClient,
        candidate: merge.Candidate | None = None,
        *,
        dry_run: bool = False,
        incident_issue_number: int | None = None,
        now_values: list[datetime] | None = None,
        invocation_values: list[merge.IncidentInvocation] | None = None,
    ) -> merge.Decision:
        clock_values = iter(now_values or [NOW, NOW])
        invocations = iter(
            invocation_values
            if invocation_values is not None
            else [incident_invocation(), incident_invocation()]
        )
        return merge.evaluate_candidate(
            client,
            client.protection_client,
            client.merge_client,
            REPOSITORY,
            candidate or self.candidate(),
            APP_LOGIN,
            APP_BOT_ID,
            dry_run=dry_run,
            incident_issue_number=incident_issue_number,
            now_provider=lambda: next(clock_values),
            incident_invocation_provider=lambda: next(invocations),
        )

    def test_agent_issue_marker_requires_exact_canonical_json(self) -> None:
        self.assertEqual(
            {
                "schema_version": 1,
                "pull_request": 17,
                "head_sha": HEAD_SHA,
                "finding_id": FINDING_ID,
            },
            merge.parse_agent_issue_marker(f"{marker()}\nafter"),
        )
        invalid_markers = [
            marker().replace('"schema_version":1', '"schema_version": 1'),
            marker().replace('"schema_version":1', '"schema_version":true'),
            marker().replace(
                f'"finding_id":"{FINDING_ID}"',
                f'"finding_id":"{FINDING_ID}","extra":1',
            ),
            " " + marker(),
            marker() + "\n" + marker(finding_id="second"),
        ]
        for value in invalid_markers:
            with self.subTest(value=value):
                with self.assertRaises(merge.ContractError):
                    merge.parse_agent_issue_marker(value)

    def test_issue_event_binds_only_pull_request_number(self) -> None:
        candidates = merge.event_candidates(
            "issues",
            {
                "action": "closed",
                "issue": {
                    "labels": [{"name": "agent-review"}],
                    "body": marker(),
                },
            },
        )
        self.assertEqual([merge.Candidate(17, None, "issues:closed")], candidates)

    def test_unlabeled_issue_event_uses_protected_marker(self) -> None:
        candidates = merge.event_candidates(
            "issues",
            {
                "action": "unlabeled",
                "label": {"name": "agent-review"},
                "issue": {"labels": [], "body": marker()},
            },
        )
        self.assertEqual([merge.Candidate(17, None, "issues:unlabeled")], candidates)

    def test_review_events_cannot_enter_the_secret_bearing_workflow(self) -> None:
        for event_name in ("pull_request_review", "pull_request_review_thread"):
            with self.subTest(event_name=event_name):
                with self.assertRaisesRegex(
                    merge.ContractError, "Unsupported auto-merge event"
                ):
                    merge.event_candidates(event_name, {})

    def test_workflow_run_without_associated_prs_falls_back_to_open_main_prs(
        self,
    ) -> None:
        client = FakeClient()
        client.open_pulls = [{"number": 17, "head": {"sha": HEAD_SHA}}]
        candidates = merge.resolve_candidates(
            client,
            REPOSITORY,
            "workflow_run",
            {
                "workflow_run": {
                    "status": "completed",
                    "conclusion": "success",
                    "name": "Agent Issue Gate",
                    "pull_requests": [],
                }
            },
        )
        self.assertEqual(
            [merge.Candidate(17, HEAD_SHA, "workflow_run:open-pr-scan")],
            candidates,
        )

    def test_schedule_scans_open_main_pull_requests(self) -> None:
        client = FakeClient()
        client.open_pulls = [{"number": 17, "head": {"sha": HEAD_SHA}}]
        candidates = merge.resolve_candidates(
            client, REPOSITORY, "schedule", {"schedule": "*/10 * * * *"}
        )
        self.assertEqual(
            [merge.Candidate(17, HEAD_SHA, "schedule:open-pr-scan")], candidates
        )

    def test_protection_is_read_from_the_pull_request_base(self) -> None:
        # The contract must come from the branch the pull request targets. Reading a
        # fixed branch would judge an integration-branch pull request against gates
        # it never publishes.
        for base in sorted(merge.ACCEPTED_PR_BASES):
            with self.subTest(base=base):
                client = FakeClient()
                client.pull_reads = [
                    pull_request(base={"ref": base, "sha": BASE_SHA}),
                    pull_request(base={"ref": base, "sha": BASE_SHA}),
                ]
                merge.evaluate_eligibility(
                    client,
                    client.protection_client,
                    REPOSITORY,
                    merge.Candidate(17, HEAD_SHA, "test"),
                    HEAD_SHA,
                    APP_LOGIN,
                    APP_BOT_ID,
                    None,
                    NOW,
                    incident_invocation,
                )
                self.assertEqual([base], client.protection_client.branches_read)

    def test_protection_read_rejects_an_ungoverned_branch(self) -> None:
        real = merge.BranchProtectionClient.__new__(merge.BranchProtectionClient)
        with self.assertRaisesRegex(merge.ContractError, "governed base branch"):
            merge.BranchProtectionClient.required_status_checks(
                real, REPOSITORY, "master"
            )

    def test_protection_read_url_encodes_the_governed_branch(self) -> None:
        # The branch is interpolated into a GitHub API path, so it must be
        # percent-encoded. Both governed bases are ASCII, but pinning the
        # encoding keeps the path safe if the governed set ever grows.
        captured: list[str] = []

        class CapturingInner:
            def get_json(self, path: str) -> object:
                captured.append(path)
                return {"strict": True, "checks": []}

        real = merge.BranchProtectionClient.__new__(merge.BranchProtectionClient)
        real._client = CapturingInner()
        for branch in sorted(merge.ACCEPTED_PR_BASES):
            merge.BranchProtectionClient.required_status_checks(
                real, REPOSITORY, branch
            )
        self.assertEqual(
            [
                f"repos/{REPOSITORY}/branches/"
                f"{urllib.parse.quote(branch, safe='')}"
                "/protection/required_status_checks"
                for branch in sorted(merge.ACCEPTED_PR_BASES)
            ],
            captured,
        )

    def test_scan_discovers_integration_branch_candidates(self) -> None:
        # GitHub's pulls endpoint filters one base at a time, so a dev-targeting
        # pull request is only found if every governed base is queried.
        client = FakeClient()
        client.open_pulls = []
        client.open_pulls_by_base = {"dev": [{"number": 17, "head": {"sha": HEAD_SHA}}]}
        candidates = merge.resolve_candidates(
            client, REPOSITORY, "schedule", {"schedule": "*/10 * * * *"}
        )
        self.assertEqual(
            [merge.Candidate(17, HEAD_SHA, "schedule:open-pr-scan")], candidates
        )
        self.assertEqual(sorted(merge.ACCEPTED_PR_BASES), sorted(client.scanned_bases))

    def test_eligible_pull_request_uses_merge_commit_and_exact_head(self) -> None:
        client = FakeClient()
        decision = self.evaluate(client)
        self.assertEqual("merged", decision.state)
        self.assertEqual(MERGE_SHA, decision.merge_sha)
        self.assertEqual(
            [
                (
                    "PUT",
                    f"repos/{REPOSITORY}/pulls/17/merge",
                    {"sha": HEAD_SHA, "merge_method": "merge"},
                )
            ],
            client.sent,
        )
        self.assertEqual([], client.pull_reads)
        self.assertEqual(2, client.graphql_calls)
        self.assertEqual(2, client.repository_reads)
        self.assertEqual(0, client.merge_client.read_attempts)

    def test_unstable_state_still_requires_and_can_pass_the_explicit_contract(
        self,
    ) -> None:
        client = FakeClient()
        client.pull_reads = [
            pull_request(mergeable_state="unstable"),
            pull_request(mergeable_state="unstable"),
        ]

        decision = self.evaluate(client)

        self.assertEqual("merged", decision.state)
        self.assertEqual(MERGE_SHA, decision.merge_sha)
        self.assertEqual(
            [
                (
                    "PUT",
                    f"repos/{REPOSITORY}/pulls/17/merge",
                    {"sha": HEAD_SHA, "merge_method": "merge"},
                )
            ],
            client.sent,
        )

    def test_dry_run_performs_full_check_without_merge(self) -> None:
        client = FakeClient()
        decision = self.evaluate(client, dry_run=True)
        self.assertEqual("dry-run", decision.state)
        self.assertEqual([], client.sent)
        self.assertEqual([], client.pull_reads)
        self.assertEqual(2, client.graphql_calls)
        self.assertEqual(2, client.repository_reads)

    def test_static_pull_request_conditions_block_before_other_api_calls(self) -> None:
        cases = [
            ("closed", {"state": "closed"}),
            ("wrong base", {"base": {"ref": "release", "sha": BASE_SHA}}),
            ("draft", {"draft": True}),
            ("not mergeable", {"mergeable": False}),
            ("unknown mergeable", {"mergeable": None}),
            ("dirty", {"mergeable_state": "dirty"}),
            ("behind", {"mergeable_state": "behind"}),
            ("blocked", {"mergeable_state": "blocked"}),
            ("unknown state", {"mergeable_state": "unknown"}),
        ]
        for label, overrides in cases:
            with self.subTest(label=label):
                client = FakeClient()
                client.pull_reads = [pull_request(**overrides)]
                decision = self.evaluate(client)
                self.assertEqual("blocked", decision.state)
                self.assertEqual([], client.sent)

    def test_stale_event_head_blocks(self) -> None:
        client = FakeClient()
        client.pull_reads = [pull_request()]
        decision = self.evaluate(client, self.candidate(OTHER_SHA))
        self.assertEqual("blocked", decision.state)
        self.assertIn("expected bound head", " ".join(decision.reasons))
        self.assertEqual([], client.sent)

    def test_standard_protection_contract_allows_all_three_gates(self) -> None:
        client = FakeClient()
        decision = self.evaluate(client, dry_run=True, invocation_values=[])
        self.assertEqual("dry-run", decision.state)
        self.assertEqual(
            tuple(sorted(merge.STANDARD_REQUIRED_GATES)),
            tuple(sorted(decision.gates)),
        )
        self.assertEqual(2, client.protection_client.reads)
        self.assertEqual(0, client.incident_issue_reads)

    def test_controlled_incident_contract_requires_exact_issue_authorization(
        self,
    ) -> None:
        client = self.incident_client()
        with self.assertRaisesRegex(merge.ContractError, "requires an incident Issue"):
            self.evaluate(client, dry_run=True)

        for dry_run, expected_state in ((True, "dry-run"), (False, "merged")):
            with self.subTest(dry_run=dry_run):
                client = self.incident_client()
                client.protection_client.configuration_pages = [
                    required_check_configuration(merge.INCIDENT_REQUIRED_GATES),
                    required_check_configuration(merge.INCIDENT_REQUIRED_GATES),
                ]
                decision = self.evaluate(
                    client,
                    dry_run=dry_run,
                    incident_issue_number=INCIDENT_ISSUE_NUMBER,
                )
                self.assertEqual(expected_state, decision.state)
                self.assertEqual({"CI gate", "Agent issue gate"}, set(decision.gates))
                self.assertEqual((REPOSITORY_OWNER,), decision.approvers)
                self.assertEqual(2, client.incident_issue_reads)
                self.assertEqual(2, client.protection_client.reads)
                self.assertEqual([], client.protection_client.configuration_pages)
                self.assertEqual(0 if dry_run else 1, len(client.sent))

    def test_incident_consumes_exactly_two_protection_snapshots(self) -> None:
        three_pages = self.incident_client()
        three_pages.protection_client.configuration_pages = [
            required_check_configuration(merge.INCIDENT_REQUIRED_GATES),
            required_check_configuration(merge.INCIDENT_REQUIRED_GATES),
            required_check_configuration(merge.INCIDENT_REQUIRED_GATES),
        ]
        decision = self.evaluate(
            three_pages,
            dry_run=True,
            incident_issue_number=INCIDENT_ISSUE_NUMBER,
        )
        self.assertEqual("dry-run", decision.state)
        self.assertEqual(2, three_pages.protection_client.reads)
        self.assertEqual(1, len(three_pages.protection_client.configuration_pages))

        one_page = self.incident_client()
        one_page.protection_client.configuration_pages = [
            required_check_configuration(merge.INCIDENT_REQUIRED_GATES)
        ]
        with self.assertRaisesRegex(
            AssertionError, "unexpected extra required check configuration read"
        ):
            self.evaluate(
                one_page,
                dry_run=True,
                incident_issue_number=INCIDENT_ISSUE_NUMBER,
            )
        self.assertEqual(2, one_page.protection_client.reads)
        self.assertEqual([], one_page.sent)

    def test_incident_owner_issue_marker_cannot_replace_owner_dispatch(self) -> None:
        cases = {
            "collaborator actor": incident_invocation(actor="maintainer"),
            "collaborator rerun": incident_invocation(triggering_actor="maintainer"),
            "non-dispatch event": incident_invocation(event_name="schedule"),
        }
        for label, invocation in cases.items():
            with self.subTest(label=label):
                client = self.incident_client()
                client.pull_reads = [pull_request(user=app_actor())]
                with self.assertRaises(merge.ContractError):
                    self.evaluate(
                        client,
                        incident_issue_number=INCIDENT_ISSUE_NUMBER,
                        invocation_values=[invocation],
                    )
                self.assertEqual([], client.sent)

    def test_incident_invocation_uses_github_runtime_environment(self) -> None:
        with mock.patch.dict(
            merge.os.environ,
            {
                "GITHUB_EVENT_NAME": "workflow_dispatch",
                "GITHUB_ACTOR": REPOSITORY_OWNER,
                "GITHUB_TRIGGERING_ACTOR": REPOSITORY_OWNER,
                "GITHUB_REPOSITORY_OWNER": REPOSITORY_OWNER,
            },
            clear=True,
        ):
            self.assertEqual(
                incident_invocation(), merge.incident_invocation_from_environment()
            )
        with mock.patch.dict(merge.os.environ, {}, clear=True):
            with self.assertRaisesRegex(merge.ContractError, "GitHub-provided"):
                merge.incident_invocation_from_environment()

    def test_incident_requires_exact_configured_app_pull_request_author(self) -> None:
        invalid_authors = {
            "user": {"login": REPOSITORY_OWNER, "id": 1, "type": "User"},
            "dependabot": app_actor("dependabot[bot]", 49699333),
            "other app": app_actor("other-app[bot]", APP_BOT_ID + 1),
        }
        for label, author in invalid_authors.items():
            with self.subTest(label=label):
                client = self.incident_client()
                client.pull_reads = [pull_request(user=author)]
                with self.assertRaises(merge.ContractError):
                    self.evaluate(
                        client,
                        incident_issue_number=INCIDENT_ISSUE_NUMBER,
                    )
                self.assertEqual([], client.sent)

    def test_incident_requires_owner_approval_on_exact_current_head(self) -> None:
        cases = {
            "missing": [],
            "maintainer only": [approval()],
            "owner stale head": [approval(REPOSITORY_OWNER, head_sha=OTHER_SHA)],
        }
        for label, reviews in cases.items():
            with self.subTest(label=label):
                client = self.incident_client()
                client.reviews = reviews
                client.permissions["maintainer"] = "write"
                decision = self.evaluate(
                    client,
                    incident_issue_number=INCIDENT_ISSUE_NUMBER,
                )
                self.assertEqual("blocked", decision.state)
                self.assertIn("repository owner's approval", " ".join(decision.reasons))
                self.assertEqual([], client.sent)

    def test_standard_contract_rejects_unused_incident_parameter(self) -> None:
        client = FakeClient()
        client.pull_reads = [pull_request()]
        with self.assertRaisesRegex(merge.ContractError, "rejects an incident Issue"):
            self.evaluate(client, incident_issue_number=INCIDENT_ISSUE_NUMBER)
        self.assertEqual(0, client.incident_issue_reads)

    def test_incident_issue_contract_rejects_invalid_authorizations(self) -> None:
        invalid_issues = {
            "closed": incident_issue(state="closed"),
            "pull request": incident_issue(
                pull_request={"url": "https://example.invalid"}
            ),
            "wrong author": incident_issue(
                author_association="MEMBER",
                user={"login": "maintainer", "type": "User"},
            ),
            "missing marker": incident_issue(body="incident details only"),
            "duplicate marker": incident_issue(
                body=f"{incident_marker()}\n{incident_marker()}"
            ),
            "malformed JSON": incident_issue(
                body=f'{merge.INCIDENT_MARKER_PREFIX}{{"schema_version":}} -->'
            ),
            "non-canonical marker": incident_issue(
                body=(
                    f"{merge.INCIDENT_MARKER_PREFIX}"
                    f"{json.dumps(incident_payload())} -->"
                )
            ),
            "extra marker field": incident_issue(
                body=incident_marker(unexpected="value")
            ),
            "wrong repository": incident_issue(
                body=incident_marker(repository="patton174/another-repository")
            ),
            "wrong base": incident_issue(body=incident_marker(base_sha=OTHER_BASE_SHA)),
            "wrong PR": incident_issue(body=incident_marker(pull_request=18)),
            "wrong head": incident_issue(body=incident_marker(head_sha=OTHER_SHA)),
            "wrong missing context": incident_issue(
                body=incident_marker(missing_context="CI gate")
            ),
            "expired": incident_issue(
                body=incident_marker(
                    issued_at="2026-08-17T09:00:00Z",
                    expires_at="2026-08-17T11:59:59Z",
                )
            ),
            "future": incident_issue(
                body=incident_marker(
                    issued_at="2026-08-17T12:00:01Z",
                    expires_at="2026-08-17T13:00:00Z",
                )
            ),
            "issued equals expires": incident_issue(
                body=incident_marker(
                    issued_at="2026-08-17T12:30:00Z",
                    expires_at="2026-08-17T12:30:00Z",
                )
            ),
            "overlong": incident_issue(
                body=incident_marker(
                    issued_at="2026-08-16T12:00:00Z",
                    expires_at="2026-08-17T13:00:00Z",
                )
            ),
        }
        for label, issue in invalid_issues.items():
            with self.subTest(label=label):
                client = self.incident_client()
                client.pull_reads = [pull_request(user=app_actor())]
                client.incident_issue = issue
                with self.assertRaises(merge.ContractError):
                    self.evaluate(
                        client,
                        incident_issue_number=INCIDENT_ISSUE_NUMBER,
                    )
                self.assertEqual([], client.sent)

    def test_incident_marker_accepts_rfc3339_utc_equivalents(self) -> None:
        cases = {
            "Z": ("2026-08-17T11:00:00Z", 0),
            "one-digit fractional Z": ("2026-08-17T11:00:00.1Z", 100000),
            "numeric UTC offset": ("2026-08-17T11:00:00+00:00", 0),
            "six-digit fractional UTC offset": (
                "2026-08-17T11:00:00.123456+00:00",
                123456,
            ),
            "seven-digit lossless fractional Z": (
                "2026-08-17T11:00:00.1234560Z",
                123456,
            ),
            "nine-digit lossless fractional UTC offset": (
                "2026-08-17T11:00:00.123456000+00:00",
                123456,
            ),
        }
        for label, (timestamp, expected_microsecond) in cases.items():
            with self.subTest(label=label):
                parsed = merge.parse_utc_timestamp(timestamp, "incident timestamp")
                self.assertEqual(timezone.utc, parsed.tzinfo)
                self.assertEqual(expected_microsecond, parsed.microsecond)

    def test_incident_marker_rejects_lossy_sub_microsecond_precision(self) -> None:
        for timestamp in (
            "2026-08-17T11:00:00.1234567Z",
            "2026-08-17T11:00:00.123456001+00:00",
        ):
            with self.subTest(timestamp=timestamp):
                with self.assertRaisesRegex(merge.ContractError, "without loss"):
                    merge.parse_utc_timestamp(timestamp, "incident timestamp")

        lossless_window = self.incident_client()
        lossless_window.incident_issue = incident_issue(
            body=incident_marker(
                issued_at="2026-08-17T11:00:00.000000000Z",
                expires_at="2026-08-17T13:00:00.000000000+00:00",
            )
        )
        decision = self.evaluate(
            lossless_window,
            dry_run=True,
            incident_issue_number=INCIDENT_ISSUE_NUMBER,
        )
        self.assertEqual("dry-run", decision.state)

        unsafe_windows = {
            "issued_at would be moved earlier by truncation": {
                "issued_at": "2026-08-17T12:00:00.0000001Z"
            },
            "expires_at carries unrepresentable precision": {
                "expires_at": "2026-08-17T12:00:00.0000001Z"
            },
        }
        for label, marker_overrides in unsafe_windows.items():
            with self.subTest(label=label):
                client = self.incident_client()
                client.pull_reads = [pull_request(user=app_actor())]
                client.incident_issue = incident_issue(
                    body=incident_marker(**marker_overrides)
                )
                with self.assertRaisesRegex(merge.ContractError, "without loss"):
                    self.evaluate(
                        client,
                        incident_issue_number=INCIDENT_ISSUE_NUMBER,
                    )
                self.assertEqual([], client.sent)

    def test_incident_marker_rejects_non_utc_naive_and_malformed_timestamps(
        self,
    ) -> None:
        invalid_timestamps = (
            "2026-08-17T11:00:00",
            "2026-08-17T11:00:00-00:00",
            "2026-08-17T11:00:00+08:00",
            "2026-08-17 11:00:00Z",
            "2026-08-17T11:00:00.\uff11\uff12\uff13Z",
            "not-a-timestamp",
        )
        for timestamp in invalid_timestamps:
            with self.subTest(timestamp=timestamp):
                with self.assertRaisesRegex(merge.ContractError, "RFC 3339 UTC"):
                    merge.parse_utc_timestamp(timestamp, "incident timestamp")

    def test_incident_issue_api_failure_fails_closed(self) -> None:
        client = self.incident_client()
        client.pull_reads = [pull_request(user=app_actor())]
        client.incident_issue_error = merge.GitHubApiError("incident unavailable", 503)
        with self.assertRaisesRegex(merge.GitHubApiError, "incident unavailable"):
            self.evaluate(client, incident_issue_number=INCIDENT_ISSUE_NUMBER)
        self.assertEqual([], client.sent)

    def test_invalid_branch_protection_contract_fails_closed(self) -> None:
        invalid_configurations = {
            "missing CI": required_check_configuration(
                ("Agent jury gate", "Agent issue gate")
            ),
            "missing issue": required_check_configuration(
                ("CI gate", "Agent jury gate")
            ),
            "unknown context": required_check_configuration(
                ("CI gate", "Agent jury gate", "Agent issue gate", "Other gate")
            ),
            "duplicate legacy context": required_check_configuration(
                contexts=["CI gate", "CI gate", "Agent jury gate", "Agent issue gate"]
            ),
            "duplicate app-bound context": required_check_configuration(
                checks=[
                    {"context": "CI gate", "app_id": merge.CI_CHECK_APP_ID},
                    {"context": "CI gate", "app_id": merge.CI_CHECK_APP_ID},
                    {"context": "Agent jury gate", "app_id": merge.CI_CHECK_APP_ID},
                    {"context": "Agent issue gate", "app_id": merge.CI_CHECK_APP_ID},
                ]
            ),
            "legacy mismatch": required_check_configuration(
                contexts=["CI gate", "Agent jury gate"],
            ),
            "wrong app": required_check_configuration(
                checks=[
                    {"context": "CI gate", "app_id": merge.CI_CHECK_APP_ID + 1},
                    {"context": "Agent jury gate", "app_id": merge.CI_CHECK_APP_ID},
                    {"context": "Agent issue gate", "app_id": merge.CI_CHECK_APP_ID},
                ]
            ),
            "missing app": required_check_configuration(
                checks=[
                    {"context": "CI gate"},
                    {"context": "Agent jury gate", "app_id": merge.CI_CHECK_APP_ID},
                    {"context": "Agent issue gate", "app_id": merge.CI_CHECK_APP_ID},
                ]
            ),
            "strict false": required_check_configuration(strict=False),
            "malformed checks": {"strict": True, "contexts": [], "checks": {}},
            "malformed contexts": {"strict": True, "contexts": {}, "checks": []},
        }
        for label, configuration in invalid_configurations.items():
            with self.subTest(label=label):
                client = FakeClient()
                client.pull_reads = [pull_request()]
                client.protection_client.configuration = configuration
                with self.assertRaises(merge.ContractError):
                    self.evaluate(client)
                self.assertEqual([], client.sent)

    def test_nonstrict_integration_base_protection_fails_closed(self) -> None:
        # A dev-targeting candidate whose base protection is nonstrict must fail
        # loud, not silently skip: the per-base read raises ContractError, and no
        # merge is attempted. Mirrors the release-branch guard for the dev base.
        client = FakeClient()
        client.pull_reads = [pull_request(base={"ref": "dev", "sha": BASE_SHA})]
        client.protection_client.configuration = required_check_configuration(
            strict=False
        )
        with self.assertRaises(merge.ContractError):
            self.evaluate(client)
        self.assertEqual([], client.sent)
        self.assertIn("dev", client.protection_client.branches_read)

    def test_branch_protection_api_failure_fails_closed(self) -> None:
        for status in (401, 403, 404, 503):
            with self.subTest(status=status):
                client = FakeClient()
                client.pull_reads = [pull_request()]
                client.protection_client.error = merge.GitHubApiError(
                    "protection unavailable", status
                )
                with self.assertRaisesRegex(
                    merge.GitHubApiError, "protection unavailable"
                ):
                    self.evaluate(client)
                self.assertEqual([], client.sent)

    def test_branch_protection_client_exposes_only_exact_required_check_read(
        self,
    ) -> None:
        configuration = required_check_configuration()
        with mock.patch.object(merge, "GitHubClient") as client_type:
            raw_client = client_type.return_value
            raw_client.get_json.return_value = configuration
            client = merge.BranchProtectionClient(
                "protection-token", "https://api.example.test"
            )

            self.assertEqual(configuration, client.required_status_checks(REPOSITORY))
            client_type.assert_called_once_with(
                "protection-token", "https://api.example.test"
            )
            raw_client.get_json.assert_called_once_with(
                f"repos/{REPOSITORY}/branches/main/protection/required_status_checks"
            )
            public_methods = {
                name
                for name in dir(client)
                if not name.startswith("_") and callable(getattr(client, name))
            }
            self.assertEqual({"required_status_checks"}, public_methods)

    def test_every_required_gate_must_have_a_successful_latest_signal(self) -> None:
        cases = {
            "missing CI": (
                [
                    success_status(10, "Agent jury gate"),
                    success_status(11, "Agent issue gate"),
                ],
                [],
            ),
            "jury pending": (
                [
                    status_signal(12, "Agent jury gate", "pending"),
                    success_status(11, "Agent issue gate"),
                ],
                [success_check(20, "CI gate")],
            ),
            "issue check failed": (
                [success_status(10, "Agent jury gate")],
                [
                    success_check(20, "CI gate"),
                    {
                        "id": 21,
                        "name": "Agent issue gate",
                        "status": "completed",
                        "conclusion": "failure",
                        "app": {"id": merge.CI_CHECK_APP_ID},
                    },
                ],
            ),
        }
        for label, (statuses, checks) in cases.items():
            with self.subTest(label=label):
                client = FakeClient()
                client.pull_reads = [pull_request()]
                client.statuses = statuses
                client.check_runs = checks
                decision = self.evaluate(client)
                self.assertEqual("blocked", decision.state)
                self.assertEqual([], client.sent)

    def test_latest_status_for_each_context_wins(self) -> None:
        client = FakeClient()
        client.statuses = [
            status_signal(1, "Agent jury gate", "failure"),
            success_status(10, "Agent jury gate"),
            success_status(11, "Agent issue gate"),
        ]
        decision = self.evaluate(client, dry_run=True)
        self.assertEqual("dry-run", decision.state)

    def test_same_name_gate_signals_from_untrusted_providers_block(self) -> None:
        clients: list[FakeClient] = []

        ci_spoof = FakeClient()
        ci_spoof.pull_reads = [pull_request()]
        ci_spoof.check_runs.append(
            check_signal(99, "CI gate", app_id=merge.CI_CHECK_APP_ID + 1)
        )
        clients.append(ci_spoof)

        jury_spoof = FakeClient()
        jury_spoof.pull_reads = [pull_request()]
        jury_spoof.statuses.append(
            status_signal(
                99,
                "Agent jury gate",
                creator_id=1,
                creator_login="spoof[bot]",
            )
        )
        clients.append(jury_spoof)

        issue_check_spoof = FakeClient()
        issue_check_spoof.pull_reads = [pull_request()]
        issue_check_spoof.check_runs.append(success_check(99, "Agent issue gate"))
        clients.append(issue_check_spoof)

        for client in clients:
            with self.subTest(index=clients.index(client)):
                decision = self.evaluate(client)
                self.assertEqual("blocked", decision.state)
                self.assertIn("untrusted provider", " ".join(decision.reasons))
                self.assertEqual([], client.sent)

    def test_graphql_review_decision_blocks_stale_rest_approval(self) -> None:
        client = FakeClient()
        client.pull_reads = [pull_request()]
        client.thread_pages = [graphql_state("REVIEW_REQUIRED")]
        decision = self.evaluate(client)
        self.assertEqual("blocked", decision.state)
        self.assertEqual("REVIEW_REQUIRED", decision.review_decision)
        self.assertIn("reviewDecision", " ".join(decision.reasons))
        self.assertEqual([], client.sent)

    def test_repository_must_be_merge_commit_only(self) -> None:
        cases = (
            ("mergeCommitAllowed", False),
            ("squashMergeAllowed", True),
            ("rebaseMergeAllowed", True),
        )
        for setting, value in cases:
            with self.subTest(setting=setting):
                client = FakeClient()
                client.pull_reads = [pull_request()]
                client.repository_settings[setting] = value
                decision = self.evaluate(client)
                self.assertEqual("blocked", decision.state)
                self.assertEqual([], client.sent)

    def test_repository_merge_settings_use_graphql_public_fields(self) -> None:
        client = FakeClient()
        settings = merge.repository_merge_settings(client, REPOSITORY)
        self.assertEqual(
            merge.RepositoryMergeSettings(True, False, False),
            settings,
        )
        self.assertEqual(1, client.repository_reads)
        self.assertEqual(0, client.graphql_calls)

    def test_repository_merge_settings_fail_closed_on_missing_graphql_field(
        self,
    ) -> None:
        client = FakeClient()
        del client.repository_settings["mergeCommitAllowed"]
        with self.assertRaisesRegex(merge.ContractError, "mergeCommitAllowed"):
            merge.repository_merge_settings(client, REPOSITORY)

    def test_unresolved_review_thread_blocks(self) -> None:
        client = FakeClient()
        client.pull_reads = [pull_request()]
        client.thread_pages = [graphql_state(resolved=[False])]
        decision = self.evaluate(client)
        self.assertEqual("blocked", decision.state)
        self.assertEqual(1, decision.unresolved_review_threads)
        self.assertEqual([], client.sent)

    def test_any_open_issue_bound_to_the_pull_request_blocks(self) -> None:
        client = FakeClient()
        client.pull_reads = [pull_request()]
        client.issues = [finding_issue(head_sha=OTHER_SHA)]
        blocked = self.evaluate(client)
        self.assertEqual("blocked", blocked.state)
        self.assertEqual((99,), blocked.open_agent_issues)

        client = FakeClient()
        client.issues = [finding_issue(pull_request_number=18)]
        allowed = self.evaluate(client, dry_run=True)
        self.assertEqual("dry-run", allowed.state)

    def test_malformed_labeled_issue_fails_closed(self) -> None:
        client = FakeClient()
        client.pull_reads = [pull_request()]
        client.issues = [
            {
                "number": 99,
                "body": marker().replace('"schema_version":1', '"schema_version": 1'),
                "labels": [{"name": "agent-review"}],
                "user": app_actor(),
            }
        ]
        with self.assertRaises(merge.ContractError):
            self.evaluate(client)
        self.assertEqual([], client.sent)

    def test_bound_issue_without_required_label_fails_closed(self) -> None:
        client = FakeClient()
        client.pull_reads = [pull_request()]
        client.issues = [finding_issue(labels=[])]
        with self.assertRaisesRegex(merge.ContractError, "missing label"):
            self.evaluate(client)
        self.assertEqual([], client.sent)

    def test_ordinary_issue_spoof_is_ignored_but_expected_login_drift_fails_closed(
        self,
    ) -> None:
        ordinary_spoof = FakeClient()
        ordinary_spoof.issues = [finding_issue(user=app_actor(login="spoof[bot]"))]
        allowed = self.evaluate(ordinary_spoof, dry_run=True)
        self.assertEqual("dry-run", allowed.state)
        self.assertEqual((), allowed.open_agent_issues)

        actors = {
            "id": app_actor(bot_id=APP_BOT_ID + 1),
            "type": app_actor(actor_type="User"),
        }
        for label, actor in actors.items():
            with self.subTest(label=label):
                client = FakeClient()
                client.pull_reads = [pull_request()]
                client.issues = [finding_issue(user=actor)]
                with self.assertRaisesRegex(
                    merge.ContractError,
                    "identity mismatch|not authored by a GitHub App bot",
                ):
                    self.evaluate(client)
                self.assertEqual([], client.sent)

    def test_approval_must_be_latest_human_current_head_and_write_capable(self) -> None:
        cases = {
            "stale head": [
                {
                    "id": 1,
                    "state": "APPROVED",
                    "commit_id": OTHER_SHA,
                    "user": {"login": "maintainer", "type": "User"},
                }
            ],
            "dismissed latest": [
                {
                    "id": 1,
                    "state": "APPROVED",
                    "commit_id": HEAD_SHA,
                    "user": {"login": "maintainer", "type": "User"},
                },
                {
                    "id": 2,
                    "state": "DISMISSED",
                    "commit_id": HEAD_SHA,
                    "user": {"login": "maintainer", "type": "User"},
                },
            ],
            "bot": [
                {
                    "id": 1,
                    "state": "APPROVED",
                    "commit_id": HEAD_SHA,
                    "user": {"login": "service[bot]", "type": "Bot"},
                }
            ],
            "author": [
                {
                    "id": 1,
                    "state": "APPROVED",
                    "commit_id": HEAD_SHA,
                    "user": {"login": "author", "type": "User"},
                }
            ],
        }
        for label, reviews in cases.items():
            with self.subTest(label=label):
                client = FakeClient()
                client.pull_reads = [pull_request()]
                client.reviews = reviews
                decision = self.evaluate(client)
                self.assertEqual("blocked", decision.state)
                self.assertEqual([], client.sent)

        client = FakeClient()
        client.pull_reads = [pull_request()]
        client.permissions["maintainer"] = "read"
        decision = self.evaluate(client)
        self.assertEqual("blocked", decision.state)

    def test_second_pull_request_read_detects_head_drift(self) -> None:
        client = FakeClient()
        client.pull_reads = [
            pull_request(),
            pull_request(head={"sha": OTHER_SHA}),
        ]
        decision = self.evaluate(client)
        self.assertEqual("blocked", decision.state)
        self.assertIn("expected bound head", " ".join(decision.reasons))
        self.assertEqual([], client.sent)

    def test_second_full_check_blocks_gate_issue_and_thread_changes(self) -> None:
        gate_change = FakeClient()
        gate_change.status_pages = [
            copy.deepcopy(gate_change.statuses),
            [
                status_signal(20, "Agent jury gate", "pending"),
                success_status(21, "Agent issue gate"),
            ],
        ]

        issue_change = FakeClient()
        issue_change.issue_pages = [
            [],
            [finding_issue(head_sha=OTHER_SHA)],
        ]

        thread_change = FakeClient()
        thread_change.thread_pages = [
            graphql_state("APPROVED"),
            graphql_state("APPROVED", resolved=[False]),
        ]

        cases = {
            "gate": gate_change,
            "issue": issue_change,
            "thread": thread_change,
        }
        for label, client in cases.items():
            with self.subTest(label=label):
                decision = self.evaluate(client)
                self.assertEqual("blocked", decision.state)
                self.assertEqual([], client.sent)
                self.assertEqual(2, client.graphql_calls)
                self.assertEqual(2, client.repository_reads)

    def test_second_protection_read_rejects_gate_or_app_binding_changes(self) -> None:
        changed_gates = FakeClient()
        changed_gates.protection_client.configuration_pages = [
            required_check_configuration(),
            required_check_configuration(merge.INCIDENT_REQUIRED_GATES),
        ]

        changed_gates.status_pages = [
            copy.deepcopy(changed_gates.statuses),
            [success_status(11, "Agent issue gate")],
        ]
        with self.assertRaisesRegex(merge.ContractError, "requires an incident Issue"):
            self.evaluate(changed_gates)
        self.assertEqual([], changed_gates.sent)

        changed_binding = FakeClient()
        changed_binding.protection_client.configuration_pages = [
            required_check_configuration(),
            required_check_configuration(
                checks=[
                    {"context": "CI gate", "app_id": merge.CI_CHECK_APP_ID},
                    {"context": "Agent jury gate", "app_id": merge.CI_CHECK_APP_ID},
                    {
                        "context": "Agent issue gate",
                        "app_id": merge.CI_CHECK_APP_ID + 1,
                    },
                ]
            ),
        ]
        with self.assertRaisesRegex(merge.ContractError, "GitHub Actions App"):
            self.evaluate(changed_binding)
        self.assertEqual([], changed_binding.sent)

    def test_protection_failure_precedes_simultaneous_incident_drift(self) -> None:
        client = self.incident_client()
        client.protection_client.configuration_pages = [
            required_check_configuration(merge.INCIDENT_REQUIRED_GATES),
            required_check_configuration(
                merge.INCIDENT_REQUIRED_GATES,
                checks=[
                    {"context": "CI gate", "app_id": merge.CI_CHECK_APP_ID},
                    {
                        "context": "Agent issue gate",
                        "app_id": merge.CI_CHECK_APP_ID + 1,
                    },
                ],
            ),
        ]
        client.incident_issue_pages = [
            incident_issue(),
            incident_issue(body=incident_marker(issued_at="2026-08-17T11:01:00Z")),
        ]

        with self.assertRaisesRegex(
            merge.ContractError,
            "required checks must be bound to the GitHub Actions App",
        ):
            self.evaluate(client, incident_issue_number=INCIDENT_ISSUE_NUMBER)
        self.assertEqual(2, client.protection_client.reads)
        self.assertEqual(1, client.incident_issue_reads)
        self.assertEqual(1, len(client.incident_issue_pages))
        self.assertEqual([], client.sent)

    def test_second_incident_read_rejects_binding_changes(self) -> None:
        client = self.incident_client()
        client.incident_issue_pages = [
            incident_issue(),
            incident_issue(
                body=incident_marker(
                    issued_at="2026-08-17T11:01:00Z",
                    expires_at="2026-08-17T13:00:00Z",
                )
            ),
        ]
        decision = self.evaluate(
            client,
            incident_issue_number=INCIDENT_ISSUE_NUMBER,
        )
        self.assertEqual("blocked", decision.state)
        self.assertIn("incident authorization changed", " ".join(decision.reasons))
        self.assertEqual(2, client.incident_issue_reads)
        self.assertEqual([], client.incident_issue_pages)
        self.assertEqual([], client.sent)

    def test_second_incident_read_rejects_canonical_marker_changes(self) -> None:
        client = self.incident_client()
        client.incident_issue_pages = [
            incident_issue(),
            incident_issue(
                body=incident_marker(issued_at="2026-08-17T11:00:00.000000000Z")
            ),
        ]
        decision = self.evaluate(
            client,
            incident_issue_number=INCIDENT_ISSUE_NUMBER,
        )
        self.assertEqual("blocked", decision.state)
        self.assertIn("incident authorization changed", " ".join(decision.reasons))
        self.assertEqual(2, client.incident_issue_reads)
        self.assertEqual([], client.incident_issue_pages)
        self.assertEqual([], client.sent)

    def test_second_incident_read_rejects_issue_body_changes(self) -> None:
        client = self.incident_client()
        client.incident_issue_pages = [
            incident_issue(body=f"initial details\n{incident_marker()}"),
            incident_issue(body=f"edited details\n{incident_marker()}"),
        ]
        decision = self.evaluate(
            client,
            incident_issue_number=INCIDENT_ISSUE_NUMBER,
        )
        self.assertEqual("blocked", decision.state)
        self.assertIn("incident authorization changed", " ".join(decision.reasons))
        self.assertEqual(2, client.incident_issue_reads)
        self.assertEqual([], client.incident_issue_pages)
        self.assertEqual([], client.sent)

    def test_second_incident_read_rejects_issue_state_changes(self) -> None:
        client = self.incident_client()
        client.incident_issue_pages = [incident_issue(), incident_issue(state="closed")]
        with self.assertRaisesRegex(merge.ContractError, "must remain open"):
            self.evaluate(client, incident_issue_number=INCIDENT_ISSUE_NUMBER)
        self.assertEqual(2, client.incident_issue_reads)
        self.assertEqual([], client.incident_issue_pages)
        self.assertEqual([], client.sent)

    def test_second_incident_eligibility_rejects_identity_and_head_drift(
        self,
    ) -> None:
        actor_drift = self.incident_client()
        with self.assertRaisesRegex(merge.ContractError, "repository owner"):
            self.evaluate(
                actor_drift,
                incident_issue_number=INCIDENT_ISSUE_NUMBER,
                invocation_values=[
                    incident_invocation(),
                    incident_invocation(actor="maintainer"),
                ],
            )

        author_drift = self.incident_client()
        author_drift.pull_reads = [
            pull_request(user=app_actor()),
            pull_request(user=app_actor("other-app[bot]", APP_BOT_ID + 1)),
        ]
        with self.assertRaisesRegex(merge.ContractError, "identity mismatch"):
            self.evaluate(
                author_drift,
                incident_issue_number=INCIDENT_ISSUE_NUMBER,
            )

        approval_drift = self.incident_client()
        approval_drift.review_pages = [
            [approval(REPOSITORY_OWNER, review_id=1)],
            [approval(REPOSITORY_OWNER, review_id=2)],
        ]
        approval_decision = self.evaluate(
            approval_drift,
            incident_issue_number=INCIDENT_ISSUE_NUMBER,
        )
        self.assertEqual("blocked", approval_decision.state)
        self.assertIn(
            "incident authorization changed", " ".join(approval_decision.reasons)
        )

        head_drift = self.incident_client()
        head_drift.pull_reads = [
            pull_request(user=app_actor()),
            pull_request(head={"sha": OTHER_SHA}, user=app_actor()),
        ]
        head_decision = self.evaluate(
            head_drift,
            incident_issue_number=INCIDENT_ISSUE_NUMBER,
        )
        self.assertEqual("blocked", head_decision.state)
        self.assertIn("expected bound head", " ".join(head_decision.reasons))

        for client in (actor_drift, author_drift, approval_drift, head_drift):
            self.assertEqual([], client.sent)

    def test_incident_path_preserves_all_other_merge_guards(self) -> None:
        missing_approval = self.incident_client()
        missing_approval.reviews = []

        unresolved_thread = self.incident_client()
        unresolved_thread.thread_pages = [graphql_state(resolved=[False])]

        open_finding = self.incident_client()
        open_finding.issues = [finding_issue()]

        for label, client in {
            "approval": missing_approval,
            "thread": unresolved_thread,
            "issue": open_finding,
        }.items():
            with self.subTest(label=label):
                decision = self.evaluate(
                    client,
                    incident_issue_number=INCIDENT_ISSUE_NUMBER,
                )
                self.assertEqual("blocked", decision.state)
                self.assertEqual([], client.sent)

    def test_merge_refusal_is_a_fail_closed_error(self) -> None:
        client = FakeClient()
        client.merge_client.merge_response = {
            "merged": False,
            "message": "branch protection",
        }
        with self.assertRaisesRegex(merge.AutoMergeError, "refused"):
            self.evaluate(client)

    def test_workflow_uses_trusted_main_and_required_triggers(self) -> None:
        workflow = (
            Path(__file__).resolve().parents[1] / "workflows/auto-merge.yml"
        ).read_text(encoding="utf-8")
        for value in (
            "workflow_run:",
            "schedule:",
            "cron: '*/10 * * * *'",
            "issues:",
            "workflow_dispatch:",
            "incident_issue:",
            "Optional exact incident authorization Issue number",
            "CI",
            "Agent Review Jury",
            "Agent Issue Gate",
            "types: [opened, closed, reopened, deleted, transferred, edited, labeled, unlabeled]",
            "ref: refs/heads/main",
            "environment: coco-agent",
            "actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1 # v3.2.0",
            "client-id: ${{ vars.COCO_AGENT_APP_CLIENT_ID }}",
            "secrets.COCO_AGENT_APP_PRIVATE_KEY",
            "permission-contents: write",
            "permission-administration: read",
            "vars.COCO_AGENT_APP_SLUG",
            "vars.COCO_AGENT_APP_LOGIN",
            "vars.COCO_AGENT_APP_BOT_ID",
            "GH_TOKEN: ${{ github.token }}",
            "AGENT_GH_TOKEN: ${{ steps.merge-token.outputs.token }}",
            "PROTECTION_GH_TOKEN: ${{ steps.protection-token.outputs.token }}",
            "INCIDENT_ISSUE_NUMBER: ${{ github.event_name == 'workflow_dispatch' && inputs.incident_issue || '' }}",
            "\"${GITHUB_EVENT_NAME}\" != 'workflow_dispatch'",
            '"${GITHUB_ACTOR}" != "${GITHUB_REPOSITORY_OWNER}"',
            '"${GITHUB_TRIGGERING_ACTOR}" != "${GITHUB_REPOSITORY_OWNER}"',
            "Incident merge requires a repository-owner workflow_dispatch.",
            '--incident-issue-number "${INCIDENT_ISSUE_NUMBER}"',
            '--expected-app-login "${EXPECTED_APP_LOGIN}"',
            '--expected-app-bot-id "${EXPECTED_APP_BOT_ID}"',
            "checks: read",
            "contents: read",
            "issues: read",
            "pull-requests: read",
            "statuses: read",
            "contains(github.event.issue.labels.*.name, 'agent-review')",
            "github.event.label.name == 'agent-review'",
            "group: auto-merge-${{ github.repository_id }}",
            "cancel-in-progress: false",
        ):
            self.assertIn(value, workflow)
        self.assertNotIn("pull_request_review:", workflow)
        self.assertNotIn("pull_request_review_thread:", workflow)
        self.assertNotIn("pull_request_target:", workflow)
        self.assertNotIn("github.event.pull_request.head.sha", workflow)
        self.assertNotIn("app-id:", workflow)
        self.assertNotIn("permission-checks:", workflow)
        self.assertNotIn("permission-issues:", workflow)
        self.assertNotIn("permission-metadata:", workflow)
        self.assertNotIn("permission-pull-requests:", workflow)
        self.assertNotIn("permission-statuses:", workflow)
        self.assertNotIn("PROTECTION_GH_TOKEN: ${{ github.token }}", workflow)
        self.assertNotIn(
            "PROTECTION_GH_TOKEN: ${{ steps.merge-token.outputs.token }}", workflow
        )
        merge_token_step = workflow.split(
            "- name: Create merge-only GitHub App token", 1
        )[1].split("- name:", 1)[0]
        protection_token_step = workflow.split(
            "- name: Create branch-protection read token", 1
        )[1].split("- name:", 1)[0]
        workflow_permissions = workflow.split("permissions:", 1)[1].split(
            "concurrency:", 1
        )[0]
        self.assertNotIn("administration:", workflow_permissions)
        self.assertIn("id: merge-token", merge_token_step)
        self.assertIn("permission-contents: write", merge_token_step)
        self.assertNotIn("permission-administration:", merge_token_step)
        self.assertIn("id: protection-token", protection_token_step)
        self.assertIn("permission-administration: read", protection_token_step)
        self.assertNotIn("permission-contents:", protection_token_step)
        self.assertEqual(
            2,
            workflow.count(
                "actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1 # v3.2.0"
            ),
        )
        self.assertEqual(1, workflow.count("permission-contents: write"))
        self.assertEqual(1, workflow.count("permission-administration: read"))
        self.assertEqual(
            ("CI gate", "Agent jury gate", "Agent issue gate"),
            merge.STANDARD_REQUIRED_GATES,
        )
        self.assertEqual(("CI gate", "Agent issue gate"), merge.INCIDENT_REQUIRED_GATES)

        specification = (
            Path(__file__).resolve().parents[2]
            / "coco-support/coco-document/superpowers/specs/2026-07-11-agent-governance-automation.md"
        ).read_text(encoding="utf-8")
        self.assertIn("| Administration | Read |", specification)
        self.assertIn("禁止授予 `Administration: write`", specification)
        self.assertNotIn("不授予 Administration 权限", specification)

    def test_incident_shell_guard_allows_only_owner_workflow_dispatch(self) -> None:
        workflow = (
            Path(__file__).resolve().parents[1] / "workflows/auto-merge.yml"
        ).read_text(encoding="utf-8")
        start = workflow.index('          if [[ -n "${INCIDENT_ISSUE_NUMBER}" ]]; then')
        end = workflow.index("          args=(", start)
        guard = "set -euo pipefail\n" + workflow[start:end]
        allowed_environment = {
            "INCIDENT_ISSUE_NUMBER": str(INCIDENT_ISSUE_NUMBER),
            "GITHUB_EVENT_NAME": "workflow_dispatch",
            "GITHUB_ACTOR": REPOSITORY_OWNER,
            "GITHUB_TRIGGERING_ACTOR": REPOSITORY_OWNER,
            "GITHUB_REPOSITORY_OWNER": REPOSITORY_OWNER,
        }

        def execute(environment: dict[str, str]) -> subprocess.CompletedProcess[str]:
            return subprocess.run(
                ["bash", "-c", guard],
                check=False,
                capture_output=True,
                env={**os.environ, **environment},
                text=True,
            )

        allowed = execute(allowed_environment)
        self.assertEqual(0, allowed.returncode, allowed.stdout + allowed.stderr)
        self.assertNotIn("Incident merge requires", allowed.stdout)

        rejected_environments = {
            "non-dispatch event": {"GITHUB_EVENT_NAME": "schedule"},
            "non-owner dispatch actor": {"GITHUB_ACTOR": "maintainer"},
            "non-owner re-run actor": {"GITHUB_TRIGGERING_ACTOR": "maintainer"},
        }
        for label, overrides in rejected_environments.items():
            with self.subTest(label=label):
                rejected = execute({**allowed_environment, **overrides})
                self.assertEqual(1, rejected.returncode)
                self.assertIn(
                    "::error::Incident merge requires a repository-owner workflow_dispatch.",
                    rejected.stdout,
                )


if __name__ == "__main__":
    unittest.main(verbosity=2)
