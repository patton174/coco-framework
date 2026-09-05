#!/usr/bin/env python3
"""Tests for the integration-branch admission gate."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import contributor_gate as gate

REPOSITORY = "patton174/coco-framework"
REPOSITORY_ID = 1288969213
HEAD_SHA = "a" * 40
DEPENDABOT_LOGIN = "dependabot[bot]"
DEPENDABOT_ID = 49699333
AGENT_LOGIN = "coco-framework-agent[bot]"
AGENT_ID = 302354622
ALLOWED_BOTS = f"{DEPENDABOT_LOGIN}={DEPENDABOT_ID},{AGENT_LOGIN}={AGENT_ID}"


def bots() -> dict[str, int]:
    return gate.allowed_bot_identities(ALLOWED_BOTS)


class FakeClient:
    """Records published statuses and answers collaborator permission lookups."""

    api_url = "https://api.github.test"

    def __init__(self, permissions: dict[str, str] | None = None) -> None:
        self.permissions = permissions or {}
        self.statuses: list[tuple[str, dict]] = []
        self.lookups: list[str] = []

    def get_json(self, path: str) -> object:
        self.lookups.append(path)
        if "/collaborators/" in path and path.endswith("/permission"):
            login = path.split("/collaborators/", 1)[1].rsplit("/permission", 1)[0]
            if login not in self.permissions:
                raise RuntimeError(f"404 for {login}")
            return {"permission": self.permissions[login]}
        raise AssertionError(f"unexpected get_json path: {path}")

    def send_json(self, method: str, path: str, payload: dict) -> dict:
        self.statuses.append((path, payload))
        # publish_status verifies the created resource echoes the request, so the
        # double has to honour that contract rather than return an empty object.
        return {
            **payload,
            "id": len(self.statuses),
            "url": f"{self.api_url}/{path}",
            "creator": {"login": "github-actions[bot]", "id": 41898282},
        }


def pull_request_event(
    branch: str = "dev-patton174",
    base_ref: str = "dev",
    author_login: str = "patton174",
    author_id: int = 1,
    head_repo_id: int = REPOSITORY_ID,
) -> dict:
    return {
        "pull_request": {
            "number": 500,
            "user": {"login": author_login, "id": author_id, "type": "User"},
            "base": {"ref": base_ref},
            "head": {
                "ref": branch,
                "sha": HEAD_SHA,
                "repo": {"id": head_repo_id, "full_name": REPOSITORY},
            },
        }
    }


class BotAllowListTest(unittest.TestCase):
    def test_parses_login_and_id_pairs_case_insensitively(self) -> None:
        parsed = bots()
        self.assertEqual(DEPENDABOT_ID, parsed[DEPENDABOT_LOGIN])
        self.assertEqual(AGENT_ID, parsed[AGENT_LOGIN])

    def test_empty_configuration_allows_no_bots(self) -> None:
        self.assertEqual({}, gate.allowed_bot_identities(""))
        self.assertEqual({}, gate.allowed_bot_identities("   "))

    def test_malformed_entries_fail_closed(self) -> None:
        for raw in ("dependabot[bot]", "dependabot[bot]=", "=1", "bot=0", "bot=abc"):
            with self.subTest(raw=raw):
                with self.assertRaises(gate.ReviewError):
                    gate.allowed_bot_identities(raw)


class BranchNamingTest(unittest.TestCase):
    def test_matching_contributor_branch_is_accepted(self) -> None:
        gate.evaluate_branch_naming("dev-patton174", "patton174", 1, bots())

    def test_author_login_match_is_case_insensitive(self) -> None:
        gate.evaluate_branch_naming("dev-Patton174", "patton174", 1, bots())

    def test_branch_naming_another_contributor_is_rejected(self) -> None:
        with self.assertRaisesRegex(gate.ReviewError, "must end with the author login"):
            gate.evaluate_branch_naming("dev-someoneelse", "patton174", 1, bots())

    def test_non_conforming_branch_is_rejected(self) -> None:
        for branch in ("bad-name", "feature/x", "dev", "main", "dev-"):
            with self.subTest(branch=branch):
                with self.assertRaisesRegex(gate.ReviewError, "must be named dev-"):
                    gate.evaluate_branch_naming(branch, "patton174", 1, bots())

    def test_dependabot_prefix_accepted_for_the_real_bot(self) -> None:
        gate.evaluate_branch_naming(
            "dependabot/pip/ruff-1.0", DEPENDABOT_LOGIN, DEPENDABOT_ID, bots()
        )

    def test_spoofed_exempt_prefix_is_rejected(self) -> None:
        # The whole point of tying exemption to identity: a human cannot borrow
        # Dependabot's naming exemption to bypass the dev-<login> rule.
        with self.assertRaisesRegex(gate.ReviewError, "reserved for"):
            gate.evaluate_branch_naming(
                "dependabot/pip/ruff-1.0", "patton174", 1, bots()
            )

    def test_exempt_prefix_with_wrong_bot_id_is_rejected(self) -> None:
        with self.assertRaisesRegex(gate.ReviewError, "does not match"):
            gate.evaluate_branch_naming(
                "dependabot/pip/ruff-1.0", DEPENDABOT_LOGIN, 999, bots()
            )

    def test_exempt_prefix_requires_the_bot_to_be_allow_listed(self) -> None:
        with self.assertRaisesRegex(gate.ReviewError, "not\n?.*allow-listed"):
            gate.evaluate_branch_naming(
                "dependabot/pip/ruff-1.0", DEPENDABOT_LOGIN, DEPENDABOT_ID, {}
            )

    def test_bare_prefix_without_a_suffix_is_not_exempt(self) -> None:
        self.assertIsNone(gate.exempt_prefix("dependabot/"))

    def test_agent_bot_may_use_its_own_codex_prefix(self) -> None:
        # The App login contains brackets, so dev-<login> is not a legal ref name;
        # the codex/ prefix is its exemption.
        gate.evaluate_branch_naming("codex/some-work", AGENT_LOGIN, AGENT_ID, bots())

    def test_human_cannot_use_the_agent_prefix(self) -> None:
        with self.assertRaisesRegex(gate.ReviewError, "reserved for"):
            gate.evaluate_branch_naming("codex/some-work", "patton174", 1, bots())

    def test_one_bot_cannot_use_another_bots_prefix(self) -> None:
        # Each prefix maps to exactly one owner, so cross-bot use is rejected.
        with self.assertRaisesRegex(gate.ReviewError, "reserved for"):
            gate.evaluate_branch_naming(
                "codex/some-work", DEPENDABOT_LOGIN, DEPENDABOT_ID, bots()
            )
        with self.assertRaisesRegex(gate.ReviewError, "reserved for"):
            gate.evaluate_branch_naming(
                "dependabot/pip/x", AGENT_LOGIN, AGENT_ID, bots()
            )

    def test_every_exempt_prefix_declares_exactly_one_owner(self) -> None:
        self.assertEqual(
            set(gate.EXEMPT_BRANCH_PREFIXES),
            set(gate.EXEMPT_BRANCH_PREFIX_OWNERS),
        )
        for prefix, owner in gate.EXEMPT_BRANCH_PREFIX_OWNERS.items():
            with self.subTest(prefix=prefix):
                self.assertTrue(prefix.endswith("/"))
                self.assertTrue(owner.endswith("[bot]"))


class AuthorizationTest(unittest.TestCase):
    def test_write_access_collaborator_is_authorized(self) -> None:
        for permission in ("write", "maintain", "admin"):
            with self.subTest(permission=permission):
                client = FakeClient({"patton174": permission})
                result = gate.evaluate_authorization(
                    client, REPOSITORY, "patton174", 1, bots()
                )
                self.assertIn("patton174", result)
                self.assertIn(permission, result)

    def test_read_only_collaborator_is_rejected(self) -> None:
        for permission in ("read", "triage", "none"):
            with self.subTest(permission=permission):
                client = FakeClient({"patton174": permission})
                with self.assertRaisesRegex(gate.ReviewError, "write"):
                    gate.evaluate_authorization(
                        client, REPOSITORY, "patton174", 1, bots()
                    )

    def test_non_collaborator_lookup_failure_fails_closed(self) -> None:
        client = FakeClient({})
        with self.assertRaisesRegex(gate.ReviewError, "Unable to resolve"):
            gate.evaluate_authorization(client, REPOSITORY, "stranger", 1, bots())

    def test_allow_listed_bot_skips_the_collaborator_lookup(self) -> None:
        client = FakeClient({})
        result = gate.evaluate_authorization(
            client, REPOSITORY, DEPENDABOT_LOGIN, DEPENDABOT_ID, bots()
        )
        self.assertIn(DEPENDABOT_LOGIN, result)
        self.assertEqual([], client.lookups)

    def test_bot_login_with_wrong_id_is_rejected(self) -> None:
        client = FakeClient({})
        with self.assertRaisesRegex(gate.ReviewError, "allow-listed user id"):
            gate.evaluate_authorization(
                client, REPOSITORY, DEPENDABOT_LOGIN, 999, bots()
            )


class PullRequestBindingTest(unittest.TestCase):
    def test_integration_branch_pull_request_applies(self) -> None:
        binding = gate.resolve_pull_request(pull_request_event(), REPOSITORY_ID)
        self.assertTrue(binding["applies"])
        self.assertEqual("dev-patton174", binding["branch"])
        self.assertEqual(HEAD_SHA, binding["head_sha"])

    def test_release_branch_pull_request_is_skipped(self) -> None:
        # main-targeting pull requests belong to the promotion gate.
        binding = gate.resolve_pull_request(
            pull_request_event(base_ref="main"), REPOSITORY_ID
        )
        self.assertFalse(binding["applies"])

    def test_fork_head_is_rejected(self) -> None:
        with self.assertRaisesRegex(gate.ReviewError, "not a fork"):
            gate.resolve_pull_request(
                pull_request_event(head_repo_id=REPOSITORY_ID + 1), REPOSITORY_ID
            )

    def test_invalid_head_sha_is_rejected(self) -> None:
        event = pull_request_event()
        event["pull_request"]["head"]["sha"] = "not-a-sha"
        with self.assertRaisesRegex(gate.ReviewError, "head SHA is invalid"):
            gate.resolve_pull_request(event, REPOSITORY_ID)

    def test_missing_pull_request_is_rejected(self) -> None:
        with self.assertRaisesRegex(gate.ReviewError, "requires a pull request"):
            gate.resolve_pull_request({}, REPOSITORY_ID)

    def test_invalid_author_identity_is_rejected(self) -> None:
        event = pull_request_event()
        event["pull_request"]["user"] = {"login": "", "id": 0}
        with self.assertRaisesRegex(gate.ReviewError, "author identity is invalid"):
            gate.author_identity(event["pull_request"])


class EndToEndTest(unittest.TestCase):
    """Drive main() so status publication and exit codes are covered."""

    def run_gate(
        self, event: dict, permissions: dict[str, str]
    ) -> tuple[int, FakeClient]:
        captured: dict[str, FakeClient] = {}
        original_client = gate.GitHubClient

        def fake_client(*_args: object, **_kwargs: object) -> FakeClient:
            client = FakeClient(permissions)
            captured["client"] = client
            return client

        gate.GitHubClient = fake_client  # type: ignore[assignment]
        try:
            with tempfile.TemporaryDirectory() as directory:
                event_path = Path(directory) / "event.json"
                event_path.write_text(json.dumps(event), encoding="utf-8")
                code = gate.main(
                    [
                        "--repository",
                        REPOSITORY,
                        "--repository-id",
                        str(REPOSITORY_ID),
                        "--event-path",
                        str(event_path),
                        "--run-url",
                        "https://github.example/runs/1",
                        "--allowed-bots",
                        ALLOWED_BOTS,
                    ]
                )
        finally:
            gate.GitHubClient = original_client  # type: ignore[assignment]
        return code, captured.get("client", FakeClient())

    def test_compliant_pull_request_publishes_success(self) -> None:
        code, client = self.run_gate(pull_request_event(), {"patton174": "admin"})
        self.assertEqual(0, code)
        self.assertEqual(1, len(client.statuses))
        path, payload = client.statuses[0]
        # Exact path, not just containment: the status must land on this head.
        self.assertEqual(f"repos/{REPOSITORY}/statuses/{HEAD_SHA}", path)
        self.assertEqual("success", payload["state"])
        self.assertEqual(gate.CONTRIBUTOR_STATUS_CONTEXT, payload["context"])

    def test_mismatched_branch_publishes_failure(self) -> None:
        code, client = self.run_gate(
            pull_request_event(branch="dev-someoneelse"), {"patton174": "admin"}
        )
        self.assertEqual(1, code)
        path, payload = client.statuses[0]
        self.assertEqual(f"repos/{REPOSITORY}/statuses/{HEAD_SHA}", path)
        self.assertEqual("failure", payload["state"])
        self.assertEqual(gate.CONTRIBUTOR_STATUS_CONTEXT, payload["context"])
        self.assertIn("author login", payload["description"])

    def test_non_collaborator_publishes_failure(self) -> None:
        code, client = self.run_gate(
            pull_request_event(branch="dev-stranger", author_login="stranger"), {}
        )
        self.assertEqual(1, code)
        _path, payload = client.statuses[0]
        self.assertEqual("failure", payload["state"])

    def test_release_branch_pull_request_publishes_nothing(self) -> None:
        code, client = self.run_gate(
            pull_request_event(base_ref="main"), {"patton174": "admin"}
        )
        self.assertEqual(0, code)
        self.assertEqual([], client.statuses)


if __name__ == "__main__":
    unittest.main()
