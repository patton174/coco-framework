#!/usr/bin/env python3

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import agent_review as review


BASE_SHA = "a" * 40
HEAD_SHA = "b" * 40


def config(**limit_overrides: int) -> dict:
    limits = {
        "diff_chars": 60000,
        "patch_chars": 48000,
        "intent_chars": 8000,
        "policy_chars": 20000,
        "code_context_chars": 20000,
        "per_file_chars": 12000,
        "full_file_chars": 16000,
        "assembled_context_chars": 96000,
        "max_findings_per_agent": 8,
        "max_questions_per_agent": 5,
        "max_context_gaps_per_agent": 10,
        "response_bytes": 1048576,
    }
    limits.update(limit_overrides)
    return {
        "schema_version": 1,
        "limits": limits,
        "context": {"always": ["AGENTS.md"], "path_rules": []},
        "specialists": [
            {"id": "architecture-api", "focus": "Architecture"},
            {"id": "correctness", "focus": "Correctness"},
            {"id": "security-isolation", "focus": "Security"},
            {"id": "tests-release", "focus": "Tests"},
            {"id": "robustness-blind", "focus": "Robustness", "blind_intent": True},
        ],
        "verifiers": [
            {"id": "evidence-verifier", "focus": "Evidence"},
            {"id": "policy-skeptic", "focus": "Policy"},
        ],
    }


def bound_context() -> dict:
    return review.bind_context(
        {
            "schema_version": 1,
            "binding": {
                "repository": "patton174/coco-framework",
                "pr_number": 60,
                "base_sha": BASE_SHA,
                "head_sha": HEAD_SHA,
                "protocol_sha256": "c" * 64,
                "context_sha256": "",
            },
            "trusted": {
                "policy": [{"source": "AGENTS.md", "content": "Policy"}],
                "module_map": [],
            },
            "untrusted": {
                "intent_json": "intent",
                "manifest": [{"filename": "src/Foo.java", "status": "modified"}],
                "diff": "+change",
                "code_contexts": [
                    {
                        "source": "src/Foo.java",
                        "kind": "head-file",
                        "content": "     1 class Foo {}",
                    }
                ],
            },
            "omissions": [],
        }
    )


def specialist_report(role: str, context: dict, severity: str = "P1") -> dict:
    return {
        "schema_version": 1,
        "role": role,
        "head_sha": context["binding"]["head_sha"],
        "context_sha256": context["binding"]["context_sha256"],
        "findings": [
            {
                "id": f"{role}:f1",
                "severity": severity,
                "category": "correctness",
                "file": "src/Foo.java",
                "start_line": 1,
                "end_line": 1,
                "title": "Wrong result",
                "claim": "The changed branch returns an incorrect result.",
                "trigger": "Call the method with an empty input collection.",
                "impact": "The public API returns the wrong value.",
                "evidence": "The changed branch returns false before evaluating the fallback.",
                "verification": "Add a focused empty-input unit test.",
                "confidence": 90,
            }
        ],
        "questions": [],
        "context_gaps": [],
    }


def verifier_report(
    role: str,
    context: dict,
    finding_id: str,
    action: str = "AGREE",
    confidence: int = 5,
) -> dict:
    del confidence
    return {
        "schema_version": 1,
        "role": role,
        "head_sha": context["binding"]["head_sha"],
        "context_sha256": context["binding"]["context_sha256"],
        "status": "COMPLETE",
        "evidence": "The verifier checked the bound candidate and supplied context.",
        "reviews": [
            {
                "finding_id": finding_id,
                "action": action,
                "reason": "The cited code and policy support this disposition.",
                "evidence": "The cited code and policy support this disposition.",
                "verification": "Inspect the cited branch and exercise the stated trigger.",
            }
        ],
        "context_gaps": [],
    }


class FakeContextClient:
    def __init__(self, head_files: dict[str, str]) -> None:
        self.head_files = head_files

    def file_text(
        self, repository: str, path: str, ref: str, max_bytes: int
    ) -> str | None:
        del repository, ref, max_bytes
        return self.head_files.get(path)


class AgentReviewTests(unittest.TestCase):
    def test_repository_config_resolves_complete_jury(self) -> None:
        path = Path(__file__).resolve().parents[1] / "agent-review/config.json"
        value = review.load_config(path)
        self.assertEqual(
            {
                "architecture-api",
                "correctness",
                "security-isolation",
                "tests-release",
                "robustness-blind",
            },
            set(review.role_map(value, "specialists")),
        )
        self.assertEqual(
            {"evidence-verifier", "policy-skeptic"},
            set(review.role_map(value, "verifiers")),
        )
        repository_root = Path(__file__).resolve().parents[2]
        protocol = review.protocol_manifest(repository_root, value)
        self.assertRegex(protocol["protocol_sha256"], r"^[0-9a-f]{64}$")
        self.assertEqual(
            3, len([item for item in protocol["files"] if "prompts/" in item["path"]])
        )

    def test_role_config_rejects_duplicate_ids(self) -> None:
        value = config()
        value["specialists"].append({"id": "correctness", "focus": "duplicate"})
        with self.assertRaises(review.ReviewError):
            review.role_map(value, "specialists")

    def test_context_hash_detects_tampering(self) -> None:
        context = bound_context()
        review.validate_context(context)
        context["untrusted"]["diff"] = "+tampered"
        with self.assertRaises(review.ReviewError):
            review.validate_context(context)

    def test_dynamic_hunks_expands_to_java_method_boundary(self) -> None:
        content = "\n".join(
            [
                "class Example {",
                "  void first() {}",
                "  public String changed(String value) {",
                "    String a = value;",
                "    String b = a;",
                "    return b;",
                "  }",
                "}",
            ]
        )
        snippet = review.dynamic_hunks("@@ -5,1 +5,1 @@", content, before=1, after=1)
        self.assertIn("public String changed", snippet)
        self.assertIn("return b", snippet)

    def test_github_file_read_only_suppresses_not_found(self) -> None:
        client = review.GitHubClient("test")
        with patch.object(
            client, "get_json", side_effect=review.GitHubNotFoundError("missing")
        ):
            self.assertIsNone(
                client.file_text("owner/repo", "missing.txt", HEAD_SHA, 100)
            )
        with patch.object(
            client, "get_json", side_effect=review.ReviewError("rate limited")
        ):
            with self.assertRaises(review.ReviewError):
                client.file_text("owner/repo", "file.txt", HEAD_SHA, 100)

    def test_build_context_adds_related_test_and_module_pom(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Trusted policy", encoding="utf-8")
            (root / "pom.xml").write_text(
                "<project><artifactId>root</artifactId><modules><module>module</module></modules></project>",
                encoding="utf-8",
            )
            main = root / "module/src/main/java/io/example/Foo.java"
            test = root / "module/src/test/java/io/example/FooTest.java"
            main.parent.mkdir(parents=True)
            test.parent.mkdir(parents=True)
            main.write_text("class Foo { int value() { return 1; } }", encoding="utf-8")
            test.write_text("class FooTest {}", encoding="utf-8")
            (root / "module/pom.xml").write_text(
                "<project><artifactId>module</artifactId></project>", encoding="utf-8"
            )
            filename = "module/src/main/java/io/example/Foo.java"
            pr = {
                "number": 1,
                "title": "Change Foo",
                "body": "Intent",
                "base": {"sha": BASE_SHA},
                "head": {"sha": HEAD_SHA},
            }
            files = [
                {
                    "filename": filename,
                    "status": "modified",
                    "additions": 1,
                    "deletions": 1,
                    "changes": 2,
                    "patch": "@@ -1,1 +1,1 @@\n-old\n+new",
                }
            ]
            context = review.build_context(
                FakeContextClient(
                    {filename: "class Foo { int value() { return 2; } }"}
                ),
                "patton174/coco-framework",
                pr,
                files,
                [],
                "diff --git a/Foo.java b/Foo.java\n+new",
                root,
                config(),
            )
            review.validate_context(context)
            sources = {item["source"] for item in context["untrusted"]["code_contexts"]}
            self.assertIn(filename, sources)
            self.assertIn("module/src/test/java/io/example/FooTest.java", sources)
            self.assertIn("module/pom.xml", sources)

    def test_build_context_rejects_oversized_diff(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Policy", encoding="utf-8")
            with self.assertRaises(review.ReviewError):
                review.build_context(
                    FakeContextClient({}),
                    "patton174/coco-framework",
                    {
                        "number": 1,
                        "title": "x",
                        "body": "",
                        "base": {"sha": BASE_SHA},
                        "head": {"sha": HEAD_SHA},
                    },
                    [],
                    [],
                    "x" * 11,
                    root,
                    config(diff_chars=10),
                )

    def test_build_context_requires_protected_policy(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(review.ReviewError):
                review.build_context(
                    FakeContextClient({}),
                    "patton174/coco-framework",
                    {
                        "number": 1,
                        "title": "x",
                        "body": "",
                        "base": {"sha": BASE_SHA},
                        "head": {"sha": HEAD_SHA},
                    },
                    [],
                    [],
                    "diff",
                    Path(directory),
                    config(),
                )

    def test_build_context_clips_patch_and_records_omission(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Policy", encoding="utf-8")
            context = review.build_context(
                FakeContextClient({}),
                "patton174/coco-framework",
                {
                    "number": 1,
                    "title": "x",
                    "body": "",
                    "base": {"sha": BASE_SHA},
                    "head": {"sha": HEAD_SHA},
                },
                [],
                [],
                "x" * 40,
                root,
                config(diff_chars=50, patch_chars=32),
            )
            self.assertLessEqual(len(context["untrusted"]["diff"]), 32)
            self.assertTrue(
                any("PR diff: clipped" in item for item in context["omissions"])
            )

    def test_specialist_schema_rejects_unknown_file(self) -> None:
        context = bound_context()
        report = specialist_report("correctness", context)
        review.validate_specialist_report(report, "correctness", context, 8)
        report["findings"][0]["file"] = "src/Missing.java"
        with self.assertRaises(review.ReviewError):
            review.validate_specialist_report(report, "correctness", context, 8)

    def test_specialist_schema_rejects_extra_fields_and_output_overflow(self) -> None:
        context = bound_context()
        report = specialist_report("correctness", context)
        report["unexpected"] = True
        with self.assertRaises(review.ReviewError):
            review.validate_specialist_report(report, "correctness", context, 8)
        report.pop("unexpected")
        report["questions"] = ["Question"] * 6
        with self.assertRaises(review.ReviewError):
            review.validate_specialist_report(report, "correctness", context, 8, 5, 10)

    def test_consensus_requires_both_verifiers_to_agree(self) -> None:
        context = bound_context()
        specialist = specialist_report("correctness", context)
        finding_id = specialist["findings"][0]["id"]
        evidence = verifier_report("evidence-verifier", context, finding_id)
        policy = verifier_report("policy-skeptic", context, finding_id)
        consensus = review.compute_consensus([specialist], [evidence, policy])
        self.assertEqual(
            [finding_id], [item["finding"]["id"] for item in consensus["confirmed"]]
        )

        policy["reviews"][0]["action"] = "DISAGREE"
        consensus = review.compute_consensus([specialist], [evidence, policy])
        self.assertFalse(consensus["confirmed"])
        self.assertEqual(
            [finding_id], [item["finding"]["id"] for item in consensus["challenged"]]
        )

    def test_cross_review_prompt_schema_is_normalized(self) -> None:
        context = bound_context()
        finding_id = "correctness:f1"
        report = {
            "schema_version": 1,
            "role": "evidence-verifier",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "evidence": "The verifier checked the cited branch and trigger.",
            "verifications": [
                {
                    "finding_id": finding_id,
                    "status": "AGREE",
                    "reason": "The claim follows from the cited branch.",
                    "evidence": "Line 1 returns the value described by the finding.",
                    "verification": "Exercise the cited branch with the stated input.",
                }
            ],
            "context_gaps": [],
        }
        review.validate_cross_report(report, "evidence-verifier", context, {finding_id})
        self.assertEqual("AGREE", report["reviews"][0]["action"])
        self.assertEqual("COMPLETE", report["status"])
        self.assertNotIn("verifications", report)

    def test_cross_review_calls_model_when_there_are_no_high_findings(self) -> None:
        context = bound_context()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = root / "specialists"
            prompt_root = root / "prompts-root"
            reports.mkdir()
            (prompt_root / "prompts").mkdir(parents=True)
            (prompt_root / "prompts/cross-review.md").write_text(
                "Return strict JSON.", encoding="utf-8"
            )
            for role in review.role_map(config(), "specialists"):
                report = specialist_report(role, context)
                report["findings"] = []
                review.write_json(reports / f"{role}.json", report)
            config_path = root / "config.json"
            context_path = root / "context.json"
            output_path = root / "verifier.json"
            review.write_json(config_path, config())
            review.write_json(context_path, context)
            model_output = {
                "schema_version": 1,
                "role": "evidence-verifier",
                "head_sha": HEAD_SHA,
                "context_sha256": context["binding"]["context_sha256"],
                "evidence": "No P0/P1 candidates were present in the bound reports.",
                "verifications": [],
                "context_gaps": [],
            }
            with patch.object(review, "AnthropicClient") as client_class:
                client_class.return_value.complete.return_value = model_output
                result = review.command_cross(
                    SimpleNamespace(
                        role="evidence-verifier",
                        config=config_path,
                        prompt_root=prompt_root,
                        context=context_path,
                        reports=reports,
                        output=output_path,
                    )
                )
                client_class.return_value.complete.assert_called_once()
            self.assertEqual(0, result)
            self.assertEqual("NOT_NEEDED", review.read_json(output_path)["status"])

    def test_cross_review_schema_rejects_extra_fields(self) -> None:
        context = bound_context()
        finding_id = "correctness:f1"
        report = verifier_report("evidence-verifier", context, finding_id)
        report["reviews"][0]["confidence"] = 99
        with self.assertRaises(review.ReviewError):
            review.validate_cross_report(
                report, "evidence-verifier", context, {finding_id}
            )

    def test_unverified_vote_prevents_confirmation(self) -> None:
        context = bound_context()
        specialist = specialist_report("correctness", context)
        finding_id = specialist["findings"][0]["id"]
        evidence = verifier_report(
            "evidence-verifier", context, finding_id, action="UNVERIFIED"
        )
        policy = verifier_report("policy-skeptic", context, finding_id)
        consensus = review.compute_consensus([specialist], [evidence, policy])
        self.assertEqual(
            [finding_id], [item["finding"]["id"] for item in consensus["unverified"]]
        )

    def test_chair_cannot_invent_or_drop_blockers(self) -> None:
        context = bound_context()
        specialist = specialist_report("correctness", context)
        finding_id = specialist["findings"][0]["id"]
        evidence = verifier_report("evidence-verifier", context, finding_id)
        policy = verifier_report("policy-skeptic", context, finding_id)
        consensus = review.compute_consensus([specialist], [evidence, policy])
        chair = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "BLOCK",
            "confirmed_blocker_ids": [finding_id],
            "summary": "One independently verified blocker remains.",
            "follow_up_finding_ids": [],
            "questions": [],
        }
        review.validate_chair(chair, consensus, context)
        chair["confirmed_blocker_ids"] = ["chair:f1"]
        with self.assertRaises(review.ReviewError):
            review.validate_chair(chair, consensus, context)

    def test_chair_schema_rejects_extra_fields(self) -> None:
        context = bound_context()
        chair = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "PASS",
            "confirmed_blocker_ids": [],
            "summary": "No independently verified blockers remain.",
            "follow_up_finding_ids": [],
            "questions": [],
            "unexpected": True,
        }
        with self.assertRaises(review.ReviewError):
            review.validate_chair(
                chair,
                {"confirmed": [], "challenged": [], "unverified": []},
                context,
            )

    def test_final_artifact_is_recomputed_from_all_reports(self) -> None:
        context = bound_context()
        specialists = []
        for role in review.role_map(config(), "specialists"):
            report = specialist_report(role, context)
            report["findings"] = []
            specialists.append(report)
        verifiers = [
            {
                "schema_version": 1,
                "role": role,
                "head_sha": HEAD_SHA,
                "context_sha256": context["binding"]["context_sha256"],
                "status": "NOT_NEEDED",
                "evidence": "No P0/P1 candidates were present in the bound reports.",
                "reviews": [],
                "context_gaps": [],
            }
            for role in review.role_map(config(), "verifiers")
        ]
        consensus = review.compute_consensus(specialists, verifiers)
        chair = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "PASS",
            "confirmed_blocker_ids": [],
            "summary": "No independently verified blockers remain.",
            "follow_up_finding_ids": [],
            "questions": [],
        }
        final = {
            "schema_version": 1,
            "binding": context["binding"],
            "verdict": "PASS",
            "chair": chair,
            "consensus": consensus,
            "specialist_roles": sorted(review.role_map(config(), "specialists")),
            "verifier_roles": sorted(review.role_map(config(), "verifiers")),
        }
        markdown = review.validate_final_artifact(
            final, context, specialists, verifiers, config()
        )
        self.assertIn("Verdict: PASS", markdown)
        final["consensus"] = {
            "confirmed": [],
            "challenged": [],
            "unverified": [{"fake": True}],
        }
        with self.assertRaises(review.ReviewError):
            review.validate_final_artifact(
                final, context, specialists, verifiers, config()
            )

    def test_managed_comment_order_supports_legacy_migration(self) -> None:
        self.assertEqual(
            (0, 0), review.managed_comment_order(review.LEGACY_COMMENT_MARKER)
        )
        self.assertEqual(
            (123, 2),
            review.managed_comment_order(
                "<!-- agent-jury:v1 -->\n<!-- agent-jury-run:123:2 -->"
            ),
        )

    def test_rendered_comment_exposes_panel_and_dissent(self) -> None:
        context = bound_context()
        specialist = specialist_report("correctness", context)
        finding_id = specialist["findings"][0]["id"]
        evidence = verifier_report("evidence-verifier", context, finding_id)
        policy = verifier_report(
            "policy-skeptic", context, finding_id, action="DISAGREE"
        )
        consensus = review.compute_consensus([specialist], [evidence, policy])
        chair = {
            "verdict": "PASS",
            "summary": "No independently confirmed blockers remain.",
        }
        markdown = review.render_review(
            context, [specialist], [evidence, policy], consensus, chair
        )
        self.assertIn("Agent Review Jury", markdown)
        self.assertIn("`correctness`", markdown)
        self.assertIn("`policy-skeptic`: **DISAGREE**", markdown)
        self.assertIn(context["binding"]["context_sha256"], markdown)

    def test_classification_excludes_forks_and_bots(self) -> None:
        base = {
            "head": {"repo": {"full_name": "patton174/coco-framework"}},
            "user": {"login": "patton174", "type": "User"},
        }
        self.assertTrue(review.classify_pr(base, "patton174/coco-framework"))
        fork = json.loads(json.dumps(base))
        fork["head"]["repo"]["full_name"] = "someone/fork"
        self.assertFalse(review.classify_pr(fork, "patton174/coco-framework"))
        bot = json.loads(json.dumps(base))
        bot["user"] = {"login": "dependabot[bot]", "type": "Bot"}
        self.assertFalse(review.classify_pr(bot, "patton174/coco-framework"))

    def test_maintainer_approval_must_bind_current_head(self) -> None:
        class FakeClient:
            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                del path, limit
                return [
                    {
                        "state": "APPROVED",
                        "commit_id": BASE_SHA,
                        "user": {"login": "old", "type": "User"},
                    },
                    {
                        "state": "APPROVED",
                        "commit_id": HEAD_SHA,
                        "user": {"login": "maintainer", "type": "User"},
                    },
                ]

            def get_json(self, path: str) -> dict:
                return {
                    "permission": "write"
                    if path.endswith("/maintainer/permission")
                    else "read"
                }

        approved, approvers = review.current_maintainer_approval(
            FakeClient(), "patton174/coco-framework", 1, HEAD_SHA
        )
        self.assertTrue(approved)
        self.assertEqual(["maintainer"], approvers)

    def test_anthropic_client_accepts_strict_json_and_rejects_partial(self) -> None:
        class FakeResponse:
            def __init__(self, body: bytes) -> None:
                self.body = body

            def __enter__(self) -> "FakeResponse":
                return self

            def __exit__(self, *args: object) -> None:
                return None

            def read(self, limit: int) -> bytes:
                return self.body[:limit]

        payload = {
            "stop_reason": "end_turn",
            "content": [{"type": "text", "text": '{"ok":true}'}],
        }
        with patch.dict(
            "os.environ",
            {
                "ANTHROPIC_API_KEY": "test",
                "ANTHROPIC_BASE_URL": "https://example.invalid",
            },
            clear=False,
        ):
            client = review.AnthropicClient(config())
            with patch(
                "urllib.request.urlopen",
                return_value=FakeResponse(json.dumps(payload).encode()),
            ):
                self.assertEqual({"ok": True}, client.complete("system", "user", 100))
            payload["stop_reason"] = "max_tokens"
            with patch(
                "urllib.request.urlopen",
                return_value=FakeResponse(json.dumps(payload).encode()),
            ):
                with self.assertRaises(review.ReviewError):
                    client.complete("system", "user", 100)

    def test_anthropic_client_rejects_insecure_relay_url(self) -> None:
        with patch.dict(
            "os.environ",
            {"ANTHROPIC_API_KEY": "test", "ANTHROPIC_BASE_URL": "http://relay.invalid"},
            clear=False,
        ):
            with self.assertRaises(review.ReviewError):
                review.AnthropicClient(config())


if __name__ == "__main__":
    unittest.main(verbosity=2)
