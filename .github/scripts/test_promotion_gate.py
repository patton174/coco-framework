#!/usr/bin/env python3
"""Tests for the release promotion gate."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import promotion_gate as gate

REPOSITORY = "patton174/coco-framework"
REPOSITORY_ID = 1288969213
OWNER = "patton174"
HEAD_SHA = "a" * 40
FINDING_MARKER = (
    '<!-- coco-agent-review: {"schema_version":1,"pull_request":17,'
    f'"head_sha":"{HEAD_SHA}","finding_id":"v1-{"e" * 64}"}} -->'
)


def green_statuses() -> list[dict]:
    """Statuses a dev head may or may not carry; the gate must not depend on them."""

    return [
        {"context": name, "state": "success"}
        for name in ("CI gate", "Agent jury gate", "Agent issue gate")
    ]


class FakeClient:
    api_url = "https://api.github.test"

    def __init__(
        self,
        *,
        behind_by: int = 0,
        statuses: list[dict] | None = None,
        check_runs: list[dict] | None = None,
        issues: list[dict] | None = None,
    ) -> None:
        self.behind_by = behind_by
        self.statuses_payload = green_statuses() if statuses is None else statuses
        self.check_runs = check_runs or []
        self.issues = issues or []
        self.published: list[tuple[str, dict]] = []

    def get_json(self, path: str) -> object:
        if "/compare/" in path:
            return {"status": "ahead", "ahead_by": 3, "behind_by": self.behind_by}
        raise AssertionError(f"unexpected get_json path: {path}")

    def paginate(self, path: str, **_kwargs: object) -> list[object]:
        if path.endswith("/statuses"):
            return list(self.statuses_payload)
        if "/check-runs?filter=latest" in path:
            return list(self.check_runs)
        if "/issues?state=open" in path:
            return list(self.issues)
        raise AssertionError(f"unexpected paginate path: {path}")

    def send_json(self, method: str, path: str, payload: dict) -> dict:
        self.published.append((path, payload))
        return {
            **payload,
            "id": len(self.published),
            "url": f"{self.api_url}/{path}",
            "creator": {"login": "github-actions[bot]", "id": 41898282},
        }


def promotion_event(
    head_ref: str = "dev",
    base_ref: str = "main",
    author: str = OWNER,
    head_repo_id: int = REPOSITORY_ID,
) -> dict:
    return {
        "pull_request": {
            "number": 600,
            "user": {"login": author, "id": 1, "type": "User"},
            "base": {"ref": base_ref},
            "head": {
                "ref": head_ref,
                "sha": HEAD_SHA,
                "repo": {"id": head_repo_id, "full_name": REPOSITORY},
            },
        }
    }


class PromotionSourceTest(unittest.TestCase):
    def test_integration_branch_is_the_only_allowed_source(self) -> None:
        gate.require_promotion_source("dev")

    def test_any_other_head_is_rejected(self) -> None:
        for head in ("main", "dev-patton174", "hotfix", "feature/x", ""):
            with self.subTest(head=head):
                with self.assertRaisesRegex(gate.ReviewError, "may be promoted"):
                    gate.require_promotion_source(head)


class OwnerAuthorTest(unittest.TestCase):
    def test_owner_may_promote(self) -> None:
        author = gate.require_owner_author(promotion_event()["pull_request"], OWNER)
        self.assertEqual(OWNER, author)

    def test_owner_match_is_case_insensitive(self) -> None:
        pull_request = promotion_event(author="Patton174")["pull_request"]
        self.assertEqual("Patton174", gate.require_owner_author(pull_request, OWNER))

    def test_non_owner_is_rejected_even_with_write_access(self) -> None:
        pull_request = promotion_event(author="maintainer")["pull_request"]
        with self.assertRaisesRegex(gate.ReviewError, "repository owner"):
            gate.require_owner_author(pull_request, OWNER)

    def test_missing_author_is_rejected(self) -> None:
        pull_request = promotion_event()["pull_request"]
        pull_request["user"] = {"login": ""}
        with self.assertRaisesRegex(gate.ReviewError, "author identity is invalid"):
            gate.require_owner_author(pull_request, OWNER)


class ReleaseCommitRetentionTest(unittest.TestCase):
    def test_up_to_date_integration_branch_passes(self) -> None:
        client = FakeClient(behind_by=0)
        gate.require_no_release_commits_dropped(client, REPOSITORY, HEAD_SHA)

    def test_missing_release_commits_are_rejected(self) -> None:
        # Non-zero behind_by means the release branch has work the integration
        # branch lacks; promoting would silently drop it.
        client = FakeClient(behind_by=2)
        with self.assertRaisesRegex(gate.ReviewError, "missing 2 commit"):
            gate.require_no_release_commits_dropped(client, REPOSITORY, HEAD_SHA)

    def test_invalid_comparison_fails_closed(self) -> None:
        client = FakeClient()
        client.get_json = lambda path: {"behind_by": "many"}  # type: ignore[assignment]
        with self.assertRaisesRegex(gate.ReviewError, "behind_by is invalid"):
            gate.require_no_release_commits_dropped(client, REPOSITORY, HEAD_SHA)


class NoInheritedGateRequirementTest(unittest.TestCase):
    """Pin that the gate does not demand per-change verdicts on the dev head.

    `agent-review.yml` runs on `pull_request_target`, so the jury and issue gate
    publish against the contributor pull request head. The merge commit that lands
    on `dev` never carries those contexts, so requiring them here was never
    satisfiable and made promotion permanently impossible.
    """

    def test_promotion_succeeds_with_no_statuses_on_the_dev_head(self) -> None:
        client = FakeClient(statuses=[], check_runs=[])
        gate.require_promotion_source("dev")
        gate.require_owner_author(promotion_event()["pull_request"], OWNER)
        gate.require_no_release_commits_dropped(client, REPOSITORY, HEAD_SHA)
        gate.require_no_open_findings(client, REPOSITORY)

    def test_the_inherited_gate_check_is_gone(self) -> None:
        self.assertFalse(hasattr(gate, "require_inherited_content_gates"))
        self.assertFalse(hasattr(gate, "INHERITED_CONTENT_GATES"))


class OpenFindingTest(unittest.TestCase):
    def test_no_findings_passes(self) -> None:
        gate.require_no_open_findings(FakeClient(issues=[]), REPOSITORY)

    def test_bound_open_finding_blocks_promotion(self) -> None:
        client = FakeClient(issues=[{"number": 481, "body": FINDING_MARKER}])
        with self.assertRaisesRegex(gate.ReviewError, "#481"):
            gate.require_no_open_findings(client, REPOSITORY)

    def test_unmarked_issue_is_ignored(self) -> None:
        client = FakeClient(issues=[{"number": 999, "body": "ordinary issue"}])
        gate.require_no_open_findings(client, REPOSITORY)


class BindingTest(unittest.TestCase):
    def test_release_targeting_pull_request_applies(self) -> None:
        binding = gate.resolve_pull_request(promotion_event(), REPOSITORY_ID)
        self.assertTrue(binding["applies"])
        self.assertEqual("dev", binding["head_ref"])

    def test_integration_targeting_pull_request_is_skipped(self) -> None:
        # dev-targeting pull requests belong to the contributor gate.
        binding = gate.resolve_pull_request(
            promotion_event(base_ref="dev", head_ref="dev-patton174"), REPOSITORY_ID
        )
        self.assertFalse(binding["applies"])

    def test_fork_head_is_rejected(self) -> None:
        with self.assertRaisesRegex(gate.ReviewError, "must originate"):
            gate.resolve_pull_request(
                promotion_event(head_repo_id=REPOSITORY_ID + 1), REPOSITORY_ID
            )


class EndToEndTest(unittest.TestCase):
    def run_gate(self, event: dict, client: FakeClient) -> int:
        original = gate.GitHubClient
        gate.GitHubClient = lambda *_a, **_k: client  # type: ignore[assignment]
        try:
            with tempfile.TemporaryDirectory() as directory:
                path = Path(directory) / "event.json"
                path.write_text(json.dumps(event), encoding="utf-8")
                return gate.main(
                    [
                        "--repository",
                        REPOSITORY,
                        "--repository-id",
                        str(REPOSITORY_ID),
                        "--repository-owner",
                        OWNER,
                        "--event-path",
                        str(path),
                        "--run-url",
                        "https://github.example/runs/1",
                    ]
                )
        finally:
            gate.GitHubClient = original  # type: ignore[assignment]

    def test_valid_promotion_publishes_success(self) -> None:
        client = FakeClient()
        self.assertEqual(0, self.run_gate(promotion_event(), client))
        self.assertEqual(1, len(client.published))
        path, payload = client.published[0]
        # The status must land on the promotion head, not any other commit.
        self.assertEqual(f"repos/{REPOSITORY}/statuses/{HEAD_SHA}", path)
        self.assertEqual("success", payload["state"])
        self.assertEqual(gate.PROMOTION_STATUS_CONTEXT, payload["context"])

    def test_non_dev_head_publishes_failure(self) -> None:
        client = FakeClient()
        self.assertEqual(1, self.run_gate(promotion_event(head_ref="hotfix"), client))
        path, payload = client.published[0]
        self.assertEqual(f"repos/{REPOSITORY}/statuses/{HEAD_SHA}", path)
        self.assertEqual("failure", payload["state"])
        self.assertEqual(gate.PROMOTION_STATUS_CONTEXT, payload["context"])
        self.assertIn("promoted", payload["description"])

    def test_non_owner_publishes_failure(self) -> None:
        client = FakeClient()
        self.assertEqual(1, self.run_gate(promotion_event(author="maintainer"), client))
        _path, payload = client.published[0]
        self.assertEqual("failure", payload["state"])

    def test_integration_pull_request_publishes_nothing(self) -> None:
        client = FakeClient()
        code = self.run_gate(
            promotion_event(base_ref="dev", head_ref="dev-patton174"), client
        )
        self.assertEqual(0, code)
        self.assertEqual([], client.published)


if __name__ == "__main__":
    unittest.main()
