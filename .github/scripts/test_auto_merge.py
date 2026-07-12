#!/usr/bin/env python3

from __future__ import annotations

import copy
import unittest
from pathlib import Path

import auto_merge as merge


HEAD_SHA = "b" * 40
OTHER_SHA = "c" * 40
MERGE_SHA = "d" * 40
REPOSITORY = "patton174/coco-framework"
FINDING_ID = "v1-" + "e" * 64
APP_LOGIN = "coco-agent[bot]"
APP_BOT_ID = 123456789


def pull_request(**overrides: object) -> dict:
    value = {
        "number": 17,
        "state": "open",
        "base": {"ref": "main"},
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


def app_actor(
    login: str = APP_LOGIN, bot_id: int = APP_BOT_ID, actor_type: str = "Bot"
) -> dict:
    return {"login": login, "id": bot_id, "type": actor_type}


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


class FakeClient:
    def __init__(self) -> None:
        self.pull_reads = [pull_request(), pull_request()]
        self.reviews = [
            {
                "id": 1,
                "state": "APPROVED",
                "commit_id": HEAD_SHA,
                "user": {"login": "maintainer", "type": "User"},
            }
        ]
        self.statuses = [
            success_status(10, "Agent jury gate"),
            success_status(11, "Agent issue gate"),
        ]
        self.check_runs = [success_check(20, "CI gate")]
        self.issues: list[dict] = []
        self.open_pulls: list[dict] = []
        self.permissions = {"maintainer": "write"}
        self.repository_settings = {
            "mergeCommitAllowed": True,
            "squashMergeAllowed": False,
            "rebaseMergeAllowed": False,
        }
        self.review_pages: list[list[dict]] | None = None
        self.status_pages: list[list[dict]] | None = None
        self.check_run_pages: list[list[dict]] | None = None
        self.issue_pages: list[list[dict]] | None = None
        self.repository_setting_pages: list[dict] | None = None
        self.thread_pages = [graphql_state(), graphql_state()]
        self.graphql_calls = 0
        self.repository_reads = 0
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
        if "/pulls?state=open&base=main" in path:
            return copy.deepcopy(self.open_pulls)
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

    def evaluate(
        self,
        client: FakeClient,
        candidate: merge.Candidate | None = None,
        *,
        dry_run: bool = False,
    ) -> merge.Decision:
        return merge.evaluate_candidate(
            client,
            client.merge_client,
            REPOSITORY,
            candidate or self.candidate(),
            APP_LOGIN,
            APP_BOT_ID,
            dry_run=dry_run,
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
            ("wrong base", {"base": {"ref": "release"}}),
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
            "vars.COCO_AGENT_APP_SLUG",
            "vars.COCO_AGENT_APP_LOGIN",
            "vars.COCO_AGENT_APP_BOT_ID",
            "GH_TOKEN: ${{ github.token }}",
            "AGENT_GH_TOKEN: ${{ steps.app-token.outputs.token }}",
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
        self.assertEqual(1, workflow.count("permission-contents: write"))
        self.assertEqual(
            ("CI gate", "Agent jury gate", "Agent issue gate"),
            merge.REQUIRED_GATES,
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
