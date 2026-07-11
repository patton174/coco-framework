#!/usr/bin/env python3

from __future__ import annotations

import json
import re
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import agent_issue_gate as issue_gate
import agent_review as review


BASE_SHA = "a" * 40
HEAD_SHA = "b" * 40
APP_BOT_ID = 424242


def config(**limit_overrides: int) -> dict:
    limits = {
        "diff_chars": 60000,
        "patch_chars": 60000,
        "intent_chars": 8000,
        "policy_chars": 20000,
        "code_context_chars": 20000,
        "per_file_chars": 12000,
        "full_file_chars": 16000,
        "max_context_files": 24,
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
        self.assertTrue(
            all(
                "P0 through P3" in verifier["lens"]
                for verifier in value["roles"]["verifiers"]
            )
        )
        self.assertEqual(
            {8192},
            {
                value["output_limits"][key]
                for key in ("specialist_tokens", "verifier_tokens", "chair_tokens")
            },
        )
        limits = review.normalized_limits(value)
        self.assertEqual(180_000, limits["diff_chars"])
        self.assertEqual(384_000, limits["assembled_context_chars"])
        self.assertEqual(48_000, limits["policy_chars"])
        self.assertEqual(24, limits["max_context_files"])
        repository_root = Path(__file__).resolve().parents[2]
        protocol = review.protocol_manifest(repository_root, value)
        self.assertRegex(protocol["protocol_sha256"], r"^[0-9a-f]{64}$")
        self.assertEqual(
            3, len([item for item in protocol["files"] if "prompts/" in item["path"]])
        )

        jury_spec = "docs/superpowers/specs/2026-07-10-multi-agent-review-jury.md"
        governance_spec = (
            "docs/superpowers/specs/2026-07-11-agent-governance-automation.md"
        )

        def mapped_specs(path: str) -> set[str]:
            return {
                spec_path
                for mapping in value["spec_path_mappings"]
                if any(
                    review.fnmatch.fnmatch(path, pattern)
                    for pattern in mapping["path_globs"]
                )
                for spec_path in mapping["spec_paths"]
            }

        for path in (
            ".github/scripts/agent_review.py",
            ".github/scripts/agent_issue_gate.py",
            ".github/workflows/agent-review.yml",
            ".github/workflows/agent-issue-gate.yml",
        ):
            with self.subTest(path=path):
                self.assertEqual(
                    {jury_spec, governance_spec} & mapped_specs(path),
                    {jury_spec, governance_spec},
                )
        for path in (
            ".github/scripts/auto_merge.py",
            ".github/workflows/auto-merge.yml",
            ".github/readme/fragments/en/overview.md",
            ".github/workflows/readme-maintenance.yml",
            ".github/workflow-governance.md",
            "README.md",
            "README_CN.md",
        ):
            with self.subTest(path=path):
                self.assertIn(governance_spec, mapped_specs(path))
        serialized_mappings = json.dumps(value["spec_path_mappings"])
        self.assertNotIn("update-readme-insights.yml", serialized_mappings)
        self.assertNotIn(".github/README.md", serialized_mappings)

    def test_config_and_context_require_strict_integer_schema_version(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            config_path = Path(directory) / "config.json"
            for invalid_version in (True, 1.0):
                with self.subTest(target="config", version=invalid_version):
                    value = config()
                    value["schema_version"] = invalid_version
                    review.write_json(config_path, value)
                    with self.assertRaises(review.ReviewError):
                        review.load_config(config_path)

                with self.subTest(target="context", version=invalid_version):
                    context = bound_context()
                    context["schema_version"] = invalid_version
                    review.bind_context(context)
                    with self.assertRaises(review.ReviewError):
                        review.validate_context(context)

    def test_normalized_limits_reads_output_tokens_with_legacy_priority(self) -> None:
        defaults = review.normalized_limits({})
        self.assertEqual(180_000, defaults["diff_chars"])
        self.assertEqual(180_000, defaults["patch_chars"])
        self.assertEqual(384_000, defaults["assembled_context_chars"])
        self.assertEqual(48_000, defaults["policy_chars"])
        self.assertEqual(60_000, defaults["code_context_chars"])
        self.assertEqual(4_000, defaults["per_file_chars"])
        self.assertEqual(12_000, defaults["full_file_chars"])
        token_keys = ("specialist_tokens", "verifier_tokens", "chair_tokens")
        self.assertEqual(
            {key: 8192 for key in token_keys},
            {key: review.normalized_limits({})[key] for key in token_keys},
        )

        value = {
            "output_limits": {
                "specialist_tokens": 4101,
                "verifier_tokens": 4102,
                "chair_tokens": 4103,
            }
        }
        self.assertEqual(
            (4101, 4102, 4103),
            tuple(review.normalized_limits(value)[key] for key in token_keys),
        )

        value["limits"] = {
            "specialist_tokens": 4201,
            "verifier_tokens": 4202,
            "chair_tokens": 4203,
        }
        self.assertEqual(
            (4201, 4202, 4203),
            tuple(review.normalized_limits(value)[key] for key in token_keys),
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
            self.assertEqual("github-raw-diff", context["untrusted"]["diff_source"])
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

    def test_collect_policy_requires_complete_specs_for_both_rename_paths(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Policy", encoding="utf-8")
            (root / "docs").mkdir()
            (root / "docs/old.md").write_text("Old specification", encoding="utf-8")
            (root / "docs/new.md").write_text("New specification", encoding="utf-8")
            value = config(policy_chars=100)
            value["context"]["path_rules"] = [
                {"patterns": ["old/**"], "files": ["docs/old.md"]},
                {"patterns": ["new/**"], "files": ["docs/new.md"]},
            ]
            omissions: list[str] = []
            sources = review.collect_policy(
                root,
                value,
                ["old/Foo.java", "new/Foo.java"],
                omissions,
            )

            self.assertEqual(
                {"AGENTS.md", "docs/old.md", "docs/new.md"},
                {item["source"] for item in sources},
            )
            self.assertEqual([], omissions)
            with self.assertRaisesRegex(review.ReviewError, "exceeds"):
                review.collect_policy(
                    root,
                    config(policy_chars=7)
                    | {
                        "context": {
                            "always": ["AGENTS.md"],
                            "path_rules": value["context"]["path_rules"],
                        }
                    },
                    ["old/Foo.java"],
                    [],
                )

    def test_build_context_rejects_patch_budget_below_hard_limit(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Policy", encoding="utf-8")
            with self.assertRaisesRegex(review.ReviewError, "complete"):
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
                    "x" * 40,
                    root,
                    config(diff_chars=50, patch_chars=32),
                )

    def test_build_context_assembles_bounded_round_robin_file_patches(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Policy", encoding="utf-8")
            files = [
                {
                    "filename": ".github/scripts/agent_review.py",
                    "status": "modified",
                    "additions": 1,
                    "deletions": 1,
                    "changes": 2,
                    "patch": "@@ -1 +1 @@\n-old-agent\n+new-agent",
                },
                {
                    "filename": ".github/scripts/test_agent_review.py",
                    "status": "modified",
                    "additions": 1,
                    "deletions": 1,
                    "changes": 2,
                    "patch": "@@ -1 +1 @@\n-old-test\n+new-test",
                },
                {
                    "filename": "coco-features/coco-web/src/main/java/Foo.java",
                    "status": "modified",
                    "additions": 1,
                    "deletions": 1,
                    "changes": 2,
                    "patch": "@@ -1 +1 @@\n-old-web\n+new-web",
                },
                {
                    "filename": "docs/architecture/module-layout.md",
                    "status": "modified",
                    "additions": 1,
                    "deletions": 1,
                    "changes": 2,
                    "patch": "@@ -1 +1 @@\n-old-doc\n+new-doc",
                },
            ]
            context = review.build_context(
                FakeContextClient({}),
                "patton174/coco-framework",
                {
                    "number": 1,
                    "title": "large layout",
                    "body": "",
                    "base": {"sha": BASE_SHA},
                    "head": {"sha": HEAD_SHA},
                },
                files,
                [],
                None,
                root,
                config(
                    diff_chars=10_000,
                    patch_chars=10_000,
                    code_context_chars=0,
                    max_context_files=0,
                    assembled_context_chars=20_000,
                ),
            )

            diff = context["untrusted"]["diff"]
            self.assertEqual(
                "github-files-api-patches", context["untrusted"]["diff_source"]
            )
            self.assertIn(".github/scripts/agent_review.py", diff)
            self.assertIn("coco-features/coco-web/src/main/java/Foo.java", diff)
            self.assertIn("docs/architecture/module-layout.md", diff)
            self.assertLess(
                diff.index("coco-features/coco-web/src/main/java/Foo.java"),
                diff.index(".github/scripts/test_agent_review.py"),
            )

    def test_build_context_rejects_missing_changed_file_patch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Policy", encoding="utf-8")
            with self.assertRaisesRegex(review.ReviewError, "omitted"):
                review.build_context(
                    FakeContextClient({}),
                    "patton174/coco-framework",
                    {
                        "number": 1,
                        "title": "large file",
                        "body": "",
                        "base": {"sha": BASE_SHA},
                        "head": {"sha": HEAD_SHA},
                    },
                    [
                        {
                            "filename": "src/Large.java",
                            "status": "modified",
                            "additions": 1000,
                            "deletions": 1000,
                            "changes": 2000,
                        }
                    ],
                    [],
                    None,
                    root,
                    config(),
                )

    def test_build_context_rejects_empty_or_truncated_changed_file_patch(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Policy", encoding="utf-8")
            base_file = {
                "filename": "src/Large.java",
                "status": "modified",
                "additions": 2,
                "deletions": 1,
                "changes": 3,
            }
            for patch_text in ("", "@@ -1 +1 @@\n-old\n+new"):
                with self.subTest(patch=patch_text):
                    with self.assertRaisesRegex(
                        review.ReviewError, "omitted|incomplete"
                    ):
                        review.build_context(
                            FakeContextClient({}),
                            "patton174/coco-framework",
                            {
                                "number": 1,
                                "title": "large file",
                                "body": "",
                                "base": {"sha": BASE_SHA},
                                "head": {"sha": HEAD_SHA},
                            },
                            [{**base_file, "patch": patch_text}],
                            [],
                            None,
                            root,
                            config(),
                        )

    def test_build_files_diff_reports_all_incomplete_patches(self) -> None:
        files = [
            {
                "filename": "src/Missing.java",
                "status": "modified",
                "additions": 1,
                "deletions": 0,
                "changes": 1,
            },
            {
                "filename": "src/Truncated.java",
                "status": "modified",
                "additions": 2,
                "deletions": 1,
                "changes": 3,
                "patch": "@@ -1 +1 @@\n-old\n+new",
            },
        ]
        with self.assertRaises(review.ReviewError) as caught:
            review.build_files_diff(files)

        message = str(caught.exception)
        self.assertIn("2 file(s)", message)
        self.assertIn("src/Missing.java", message)
        self.assertIn("src/Truncated.java", message)
        self.assertIn("partial review context is not emitted", message)

    def test_patch_change_counts_ignores_headers_and_rejects_truncated_hunks(
        self,
    ) -> None:
        complete = "\n".join(
            [
                "diff --git a/src/Foo.java b/src/Foo.java",
                "--- a/src/Foo.java",
                "+++ b/src/Foo.java",
                "@@ -1 +1 @@",
                "-old",
                "+new",
            ]
        )
        self.assertEqual((1, 1), review.patch_change_counts(complete))

        truncated = "@@ -1,3 +1,3 @@\n-old\n+new"
        with self.assertRaisesRegex(review.ReviewError, "hunk body is incomplete"):
            review.patch_change_counts(truncated)

    def test_removed_files_are_prioritized_before_modified_files(self) -> None:
        files = [
            {
                "filename": "coco-a/module/src/main/java/Modified.java",
                "status": "modified",
                "changes": 100,
            },
            {
                "filename": "coco-a/module/src/main/java/Removed.java",
                "status": "removed",
                "changes": 1,
            },
        ]

        ordered = review.prioritized_files(files)
        self.assertEqual("removed", ordered[0]["status"])

    def test_code_context_budget_stops_additional_remote_file_reads(self) -> None:
        class CountingClient:
            def __init__(self) -> None:
                self.paths: list[str] = []

            def file_text(
                self, repository: str, path: str, ref: str, max_bytes: int
            ) -> str:
                del repository, ref, max_bytes
                self.paths.append(path)
                if len(self.paths) > 1:
                    raise AssertionError(
                        "file_text called after context budget was full"
                    )
                return "class Foo {}"

        files = [
            {
                "filename": "coco-a/module/src/main/java/Foo.java",
                "status": "modified",
                "patch": "@@ -1 +1 @@\n-old\n+new",
            },
            {
                "filename": "coco-b/module/src/main/java/Bar.java",
                "status": "modified",
                "patch": "@@ -1 +1 @@\n-old\n+new",
            },
        ]
        client = CountingClient()
        omissions: list[str] = []
        contexts = review.build_code_contexts(
            client,
            "patton174/coco-framework",
            HEAD_SHA,
            Path.cwd(),
            files,
            config(
                code_context_chars=1,
                per_file_chars=1,
                full_file_chars=100,
            ),
            omissions,
        )

        self.assertEqual(["coco-a/module/src/main/java/Foo.java"], client.paths)
        self.assertEqual(1, len(contexts))
        self.assertTrue(any("character budget" in item for item in omissions))

    def test_code_context_records_binary_and_unsupported_file_omissions(self) -> None:
        files = [
            {
                "filename": "docs/architecture.png",
                "status": "modified",
                "patch": "",
            },
            {
                "filename": "src/main/java/Foo.java",
                "status": "modified",
                "patch": "@@ -1 +1 @@\n-old\n+new",
            },
        ]
        omissions: list[str] = []
        contexts = review.build_code_contexts(
            FakeContextClient({"src/main/java/Foo.java": "class Foo {}"}),
            "patton174/coco-framework",
            HEAD_SHA,
            Path.cwd(),
            files,
            config(),
            omissions,
        )

        self.assertTrue(
            any(
                item == "binary or unsupported changed file: docs/architecture.png"
                for item in omissions
            )
        )
        self.assertTrue(
            any(item["source"] == "src/main/java/Foo.java" for item in contexts)
        )

    def test_prepare_uses_bounded_files_api_without_raw_diff_request(self) -> None:
        pull_request = {
            "number": 1,
            "state": "open",
            "title": "large layout",
            "body": "",
            "changed_files": 501,
            "base": {"sha": BASE_SHA, "ref": "main"},
            "head": {
                "sha": HEAD_SHA,
                "repo": {"full_name": "patton174/coco-framework"},
            },
            "user": {"login": "patton174", "type": "User"},
        }

        class FakeClient:
            def __init__(self) -> None:
                self.paginated: list[tuple[str, int]] = []

            def get_json(self, path: str) -> dict:
                if path == "repos/patton174/coco-framework/pulls/1":
                    return pull_request
                raise AssertionError(f"Unexpected GET path: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                self.paginated.append((path, limit))
                if path.endswith("/files"):
                    return [
                        {
                            "filename": f"module-{index}/Foo.java",
                            "status": "modified",
                            "additions": 1,
                            "deletions": 1,
                            "changes": 2,
                            "patch": "@@ -1 +1 @@\n-old\n+new",
                        }
                        for index in range(501)
                    ]
                if path.endswith("/commits"):
                    return []
                raise AssertionError(f"Unexpected paginated path: {path}")

        client = FakeClient()
        context = bound_context()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch.object(review, "load_config", return_value=config()),
                patch.object(review, "build_context", return_value=context) as builder,
                patch("builtins.print"),
                patch.dict("os.environ", {"GH_TOKEN": "token"}),
            ):
                result = review.command_prepare(
                    SimpleNamespace(
                        repository="patton174/coco-framework",
                        pr_number=1,
                        event_name="pull_request_target",
                        expected_head_sha=HEAD_SHA,
                        base_root=root,
                        config=root / "config.json",
                        context_output=root / "context.json",
                        metadata_output=root / "metadata.json",
                    )
                )

        self.assertEqual(0, result)
        self.assertIn(
            (
                "repos/patton174/coco-framework/pulls/1/files",
                review.MAX_PULL_REQUEST_FILES,
            ),
            client.paginated,
        )
        self.assertIsNone(builder.call_args.args[5])

    def test_pull_request_diff_uses_raw_media_only_within_github_limit(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.calls = 0

            def get_raw(self, path: str, accept: str, max_bytes: int) -> bytes:
                self.calls += 1
                self.assert_request(path, accept, max_bytes)
                return b"diff --git a/Foo.java b/Foo.java\n+new"

            @staticmethod
            def assert_request(path: str, accept: str, max_bytes: int) -> None:
                if path != "repos/patton174/coco-framework/pulls/1":
                    raise AssertionError(f"Unexpected raw path: {path}")
                if accept != "application/vnd.github.v3.diff":
                    raise AssertionError(f"Unexpected raw media type: {accept}")
                if max_bytes != 1024 * 1024:
                    raise AssertionError(f"Unexpected raw byte limit: {max_bytes}")

        self.assertEqual(300, review.MAX_RAW_DIFF_FILES)
        client = FakeClient()
        self.assertEqual(
            "diff --git a/Foo.java b/Foo.java\n+new",
            review.pull_request_diff(
                client,
                "patton174/coco-framework",
                1,
                review.MAX_RAW_DIFF_FILES,
            ),
        )
        self.assertIsNone(
            review.pull_request_diff(
                client,
                "patton174/coco-framework",
                1,
                review.MAX_RAW_DIFF_FILES + 1,
            )
        )
        self.assertEqual(1, client.calls)

    def test_changed_file_count_rejects_github_overflow_before_pagination(self) -> None:
        self.assertEqual(
            review.MAX_PULL_REQUEST_FILES,
            review.changed_file_count({"changed_files": review.MAX_PULL_REQUEST_FILES}),
        )
        with self.assertRaisesRegex(review.ReviewError, "split"):
            review.changed_file_count(
                {"changed_files": review.MAX_PULL_REQUEST_FILES + 1}
            )

    def test_pull_file_validation_rejects_short_duplicate_and_unsafe_results(
        self,
    ) -> None:
        valid = {
            "filename": "src/Foo.java",
            "status": "modified",
            "additions": 1,
            "deletions": 1,
            "changes": 2,
            "patch": "@@ -1 +1 @@\n-old\n+new",
        }
        review.validate_pull_files([valid], 1)
        with self.assertRaisesRegex(review.ReviewError, "changed_files"):
            review.validate_pull_files([valid], 2)
        with self.assertRaisesRegex(review.ReviewError, "duplicate"):
            review.validate_pull_files([valid, dict(valid)], 2)
        unsafe = {**valid, "filename": "../Foo.java"}
        with self.assertRaisesRegex(review.ReviewError, "unsafe"):
            review.validate_pull_files([unsafe], 1)

    def test_pull_file_validation_requires_safe_rename_source(self) -> None:
        renamed = {
            "filename": "src/New.java",
            "previous_filename": "src/Old.java",
            "status": "renamed",
            "additions": 0,
            "deletions": 0,
            "changes": 0,
        }
        review.validate_pull_files([renamed], 1)
        for previous in (None, "", "../Old.java", "src\\Old.java", "src/New.java"):
            with self.subTest(previous=previous):
                candidate = dict(renamed)
                if previous is None:
                    candidate.pop("previous_filename")
                else:
                    candidate["previous_filename"] = previous
                with self.assertRaisesRegex(review.ReviewError, "previous|identical"):
                    review.validate_pull_files([candidate], 1)

        copied = {**renamed, "status": "copied"}
        review.validate_pull_files([copied], 1)
        unexpected_previous = {**renamed, "status": "modified"}
        with self.assertRaisesRegex(review.ReviewError, "status=modified"):
            review.validate_pull_files([unexpected_previous], 1)

    def test_pull_file_validation_rejects_inconsistent_change_totals(self) -> None:
        invalid = {
            "filename": "src/Foo.java",
            "status": "modified",
            "additions": 1,
            "deletions": 1,
            "changes": 3,
            "patch": "@@ -1 +1 @@\n-old\n+new",
        }
        with self.assertRaisesRegex(review.ReviewError, "inconsistent"):
            review.validate_pull_files([invalid], 1)

    def test_specialist_schema_rejects_unknown_file(self) -> None:
        context = bound_context()
        report = specialist_report("correctness", context)
        review.validate_specialist_report(report, "correctness", context, 8)
        report["findings"][0]["file"] = "src/Missing.java"
        with self.assertRaises(review.ReportShapeError):
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

    def test_specialist_numeric_fields_require_strict_integers(self) -> None:
        context = bound_context()
        for field in ("start_line", "end_line", "confidence"):
            for invalid_value in (True, 1.0):
                with self.subTest(field=field, value=invalid_value):
                    report = specialist_report("correctness", context)
                    report["findings"][0][field] = invalid_value
                    with self.assertRaises(review.ReportShapeError):
                        review.validate_specialist_report(
                            report, "correctness", context, 8
                        )

    def test_specialist_confidence_is_optional(self) -> None:
        context = bound_context()
        report = specialist_report("correctness", context)
        del report["findings"][0]["confidence"]
        review.validate_specialist_report(report, "correctness", context, 8)

    def test_markdown_text_neutralizes_active_content_and_mentions(self) -> None:
        rendered = review.markdown_text(
            "@team\n# heading [link](https://example.test) "
            "![image](https://example.test/image.png) *bold* `code` <tag> "
            "www.example.test GH-123 deadbeef"
        )
        self.assertNotIn("\n", rendered)
        self.assertNotIn("@team", rendered)
        self.assertIn("&#64;team", rendered)
        self.assertIn("&#35; heading", rendered)
        self.assertIn(r"\[link\]\(", rendered)
        self.assertIn("https:\u200b//example.test", rendered)
        self.assertNotIn("www.example.test", rendered)
        self.assertNotIn("GH-123", rendered)
        self.assertNotIn("deadbeef", rendered)
        self.assertIn("&lt;tag&gt;", rendered)
        for source, escaped in (
            ("- item", r"\- item"),
            ("+ item", r"\+ item"),
            ("1. item", r"1\. item"),
            ("---", r"\---"),
        ):
            with self.subTest(source=source):
                self.assertEqual(escaped, review.markdown_text(source))
        self.assertLessEqual(
            review.utf8_size(review.markdown_text("\u6d4b" * 20, 12)), 12
        )
        title = review.issue_title(
            {
                "finding": {
                    "severity": "P3",
                    "title": (
                        "@team #123 https://example.test www.example.test "
                        "GH-123 deadbeef\nfollow-up"
                    ),
                }
            }
        )
        self.assertNotIn("@team", title)
        self.assertNotIn("#123", title)
        self.assertNotIn("https://", title)
        self.assertNotIn("www.example.test", title)
        self.assertNotIn("GH-123", title)
        self.assertNotIn("deadbeef", title)
        self.assertNotIn("\n", title)
        self.assertLessEqual(review.utf8_size(title), 240)

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

    def test_cross_review_calls_model_when_there_are_no_findings(self) -> None:
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
                "evidence": "No P0-P3 candidates were present in the bound reports.",
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

    def test_cross_review_checks_low_severity_findings(self) -> None:
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
            finding_id = "correctness:f1"
            for role in review.role_map(config(), "specialists"):
                report = specialist_report(role, context, severity="P3")
                if role != "correctness":
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
                "evidence": "The verifier checked the P3 claim and its cited trigger.",
                "verifications": [
                    {
                        "finding_id": finding_id,
                        "status": "AGREE",
                        "reason": "The cited code supports the low-severity claim.",
                        "evidence": "The changed branch matches the reported behavior.",
                        "verification": "Exercise the cited P3 trigger in a focused test.",
                    }
                ],
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
            self.assertEqual(0, result)
            output = review.read_json(output_path)
            self.assertEqual("COMPLETE", output["status"])
            self.assertEqual(finding_id, output["reviews"][0]["finding_id"])

    def test_cross_review_schema_rejects_extra_fields(self) -> None:
        context = bound_context()
        finding_id = "correctness:f1"
        report = verifier_report("evidence-verifier", context, finding_id)
        report["reviews"][0]["confidence"] = 99
        with self.assertRaises(review.ReportShapeError):
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

    def test_low_severity_followup_requires_both_verifiers_to_agree(self) -> None:
        context = bound_context()
        specialist = specialist_report("correctness", context, severity="P3")
        finding_id = specialist["findings"][0]["id"]
        evidence = verifier_report("evidence-verifier", context, finding_id)
        policy = verifier_report("policy-skeptic", context, finding_id)
        consensus = review.compute_consensus([specialist], [evidence, policy])
        self.assertEqual(
            {finding_id}, review.confirmed_finding_ids(consensus, {"P2", "P3"})
        )
        self.assertFalse(review.confirmed_finding_ids(consensus, {"P0", "P1"}))
        chair = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "PASS",
            "confirmed_blocker_ids": [],
            "summary": "No independently verified blockers remain.",
            "follow_up_finding_ids": [finding_id],
            "questions": [],
        }
        review.validate_chair(chair, consensus, context, {finding_id})

        for action in ("DISAGREE", "UNVERIFIED"):
            with self.subTest(action=action):
                policy = verifier_report(
                    "policy-skeptic", context, finding_id, action=action
                )
                consensus = review.compute_consensus([specialist], [evidence, policy])
                self.assertFalse(review.confirmed_finding_ids(consensus, {"P2", "P3"}))
                with self.assertRaises(review.ReportShapeError):
                    review.validate_chair(chair, consensus, context, set())

    def test_consensus_covers_every_severity_and_verifier_disposition(self) -> None:
        context = bound_context()
        expected_bucket = {
            "AGREE": "confirmed",
            "DISAGREE": "challenged",
            "UNVERIFIED": "unverified",
        }
        for severity in ("P0", "P1", "P2", "P3"):
            for action, bucket in expected_bucket.items():
                with self.subTest(severity=severity, action=action):
                    specialist = specialist_report(
                        "correctness", context, severity=severity
                    )
                    finding_id = specialist["findings"][0]["id"]
                    evidence = verifier_report("evidence-verifier", context, finding_id)
                    policy = verifier_report(
                        "policy-skeptic", context, finding_id, action=action
                    )
                    consensus = review.compute_consensus(
                        [specialist], [evidence, policy]
                    )
                    self.assertEqual(
                        [finding_id],
                        [item["finding"]["id"] for item in consensus[bucket]],
                    )
                    blocker_ids = review.confirmed_finding_ids(consensus, {"P0", "P1"})
                    followup_ids = review.confirmed_finding_ids(consensus, {"P2", "P3"})
                    self.assertEqual(
                        {finding_id}
                        if action == "AGREE" and severity in {"P0", "P1"}
                        else set(),
                        blocker_ids,
                    )
                    self.assertEqual(
                        {finding_id}
                        if action == "AGREE" and severity in {"P2", "P3"}
                        else set(),
                        followup_ids,
                    )

    def test_no_defect_p3_regression_cannot_create_an_unconfirmed_issue(self) -> None:
        context = bound_context()
        specialist = specialist_report("correctness", context, severity="P3")
        finding = specialist["findings"][0]
        finding["claim"] = "No defect is present in the reviewed change."
        finding["trigger"] = "No triggering input exists."
        finding["impact"] = "No user-visible impact exists."
        evidence = verifier_report("evidence-verifier", context, finding["id"])
        policy = verifier_report(
            "policy-skeptic", context, finding["id"], action="DISAGREE"
        )
        consensus = review.compute_consensus([specialist], [evidence, policy])
        chair = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "PASS",
            "confirmed_blocker_ids": [],
            "summary": "No independently verified blockers remain.",
            "follow_up_finding_ids": [finding["id"]],
            "questions": [],
        }
        with self.assertRaises(review.ReportShapeError):
            review.validate_chair(chair, consensus, context, set())
        with self.assertRaisesRegex(
            review.ReviewError, "without dual-verifier confirmation"
        ):
            review.actionable_findings(
                {"consensus": consensus, "chair": chair}, [specialist]
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
        with self.assertRaises(review.ReportShapeError):
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
                "evidence": "No P0-P3 candidates were present in the bound reports.",
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

    def test_final_artifact_recomputes_confirmed_low_severity_followup(self) -> None:
        context = bound_context()
        specialists = []
        finding_id = "correctness:f1"
        for role in review.role_map(config(), "specialists"):
            report = specialist_report(role, context, severity="P2")
            if role != "correctness":
                report["findings"] = []
            specialists.append(report)
        verifiers = [
            verifier_report(role, context, finding_id)
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
            "follow_up_finding_ids": [finding_id],
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
        self.assertIn("verified and selected", markdown)

        tampered = json.loads(json.dumps(final))
        tampered["consensus"]["challenged"] = tampered["consensus"]["confirmed"]
        tampered["consensus"]["confirmed"] = []
        with self.assertRaises(review.ReviewError):
            review.validate_final_artifact(
                tampered, context, specialists, verifiers, config()
            )

    def test_final_artifact_contract_errors_remain_non_shape_errors(self) -> None:
        context = bound_context()

        def artifact(schema_version: object = 1) -> dict:
            return {
                "schema_version": schema_version,
                "binding": context["binding"],
                "verdict": "PASS",
                "chair": {},
                "consensus": {},
                "specialist_roles": [],
                "verifier_roles": [],
            }

        missing_field = artifact()
        missing_field.pop("verdict")
        extra_field = artifact()
        extra_field["unexpected"] = True
        cases = [
            ("missing-field", missing_field),
            ("extra-field", extra_field),
            ("boolean-version", artifact(True)),
            ("float-version", artifact(1.0)),
        ]
        for name, value in cases:
            with self.subTest(case=name):
                with self.assertRaises(review.ReviewError) as raised:
                    review.validate_final_artifact(value, context, [], [], config())
                self.assertNotIsInstance(raised.exception, review.ReportShapeError)

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

    def test_finding_issue_marker_is_strict_and_binds_first_head(self) -> None:
        finding_id = "v1-" + "d" * 64
        marker = review.finding_issue_marker(60, HEAD_SHA, finding_id)
        self.assertEqual(
            '<!-- coco-agent-review: {"schema_version":1,"pull_request":60,'
            f'"head_sha":"{HEAD_SHA}","finding_id":"{finding_id}"}} -->',
            marker,
        )
        self.assertEqual(
            {
                "schema_version": 1,
                "pull_request": 60,
                "head_sha": HEAD_SHA,
                "finding_id": finding_id,
            },
            review.parse_finding_issue_marker(marker + "\nDetails"),
        )
        reordered = (
            '<!-- coco-agent-review: {"pull_request":60,"schema_version":1,'
            f'"head_sha":"{HEAD_SHA}","finding_id":"{finding_id}"}} -->'
        )
        with self.assertRaisesRegex(review.ReviewError, "canonical"):
            review.parse_finding_issue_marker(reordered)
        with self.assertRaisesRegex(review.ReviewError, "first body line"):
            review.parse_finding_issue_marker("Details\n" + marker)
        with self.assertRaisesRegex(review.ReviewError, "exactly one"):
            review.parse_finding_issue_marker(marker + "\n" + marker)

    def test_issue_event_resolver_uses_previous_marker_after_body_edit(self) -> None:
        app_login = "coco-agent[bot]"
        finding_id = "v1-" + "a" * 64
        marker = review.finding_issue_marker(60, HEAD_SHA, finding_id)
        base_event = {
            "repository": {"full_name": "patton174/coco-framework"},
            "issue": {
                "number": 12,
                "body": "Marker removed",
                "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
            },
            "changes": {"body": {"from": marker + "\nOld body"}},
        }
        resolved = issue_gate.resolve_event(base_event, app_login)
        self.assertFalse(resolved["ignored"])
        self.assertEqual(60, resolved["pr_number"])

        quoted = json.loads(json.dumps(base_event))
        quoted["issue"]["body"] = "Documentation example\n" + marker
        quoted.pop("changes")
        with self.assertRaisesRegex(review.ReviewError, "first body line"):
            issue_gate.resolve_event(quoted, app_login)

        for unrelated in (
            {"repository": {"full_name": "patton174/coco-framework"}},
            {
                "repository": {"full_name": "patton174/coco-framework"},
                "issue": {"number": 12, "body": "Ordinary issue without a marker"},
            },
        ):
            with self.subTest(unrelated=unrelated):
                self.assertTrue(
                    issue_gate.resolve_event(unrelated, app_login)["ignored"]
                )

        spoof_bodies = (
            "<!-- coco-agent-review: similar but invalid -->",
            marker + "\nValid-looking spoof",
            "Documentation example\n" + marker,
        )
        for body in spoof_bodies:
            with self.subTest(spoof_body=body):
                spoof_event = {
                    "repository": {"full_name": "patton174/coco-framework"},
                    "issue": {
                        "number": 99,
                        "body": body,
                        "user": {"id": 7, "login": "mallory", "type": "User"},
                    },
                }
                self.assertTrue(
                    issue_gate.resolve_event(spoof_event, app_login)["ignored"]
                )

        terminal_issue = {
            "number": 12,
            "body": marker + "\nDeleted or transferred finding",
            "labels": [{"name": review.FINDING_ISSUE_LABEL}],
            "user": {
                "id": APP_BOT_ID,
                "login": app_login,
                "type": "Bot",
            },
        }

        class NoIssueReadClient:
            @staticmethod
            def get_json(path: str) -> dict:
                raise AssertionError(f"Terminal issue must not be re-read: {path}")

        with tempfile.TemporaryDirectory() as temp_dir:
            event_path = Path(temp_dir) / "event.json"
            for action in ("deleted", "transferred"):
                with self.subTest(action=action):
                    event_path.write_text(
                        json.dumps(
                            {
                                "action": action,
                                "repository": {"full_name": "patton174/coco-framework"},
                                "issue": terminal_issue,
                            }
                        ),
                        encoding="utf-8",
                    )
                    checked = issue_gate.current_event_issue(
                        NoIssueReadClient(),
                        "patton174/coco-framework",
                        60,
                        event_path,
                        app_login,
                        APP_BOT_ID,
                    )
                    self.assertEqual(12, checked["number"])

            event_path.write_text(
                json.dumps(
                    {
                        "action": "edited",
                        "repository": {"full_name": "patton174/coco-framework"},
                        "issue": {
                            "number": 99,
                            "body": "Documentation example\n" + marker,
                            "user": {"id": 7, "login": "mallory", "type": "User"},
                        },
                    }
                ),
                encoding="utf-8",
            )
            self.assertIsNone(
                issue_gate.current_event_issue(
                    NoIssueReadClient(),
                    "patton174/coco-framework",
                    60,
                    event_path,
                    app_login,
                    APP_BOT_ID,
                )
            )

        dispatched = issue_gate.resolve_event(
            {
                "repository": {"full_name": "patton174/coco-framework"},
                "inputs": {"pr_number": "60", "head_sha": HEAD_SHA},
            },
            app_login,
        )
        self.assertEqual(60, dispatched["pr_number"])
        self.assertEqual(HEAD_SHA, dispatched["expected_head_sha"])

    def test_actionable_findings_are_confirmed_or_chair_selected_and_stable(
        self,
    ) -> None:
        context = bound_context()
        blocker_report = specialist_report("correctness", context)
        followup_report = specialist_report("architecture-api", context, severity="P2")
        omitted_report = specialist_report("tests-release", context, severity="P3")
        blocker = blocker_report["findings"][0]
        followup = followup_report["findings"][0]
        final = {
            "consensus": {
                "confirmed": [{"finding": blocker}, {"finding": followup}],
                "challenged": [],
                "unverified": [{"finding": omitted_report["findings"][0]}],
            },
            "chair": {"follow_up_finding_ids": [followup["id"]]},
        }
        actionable = review.actionable_findings(
            final, [blocker_report, followup_report, omitted_report]
        )
        self.assertEqual(
            {blocker["id"], followup["id"]},
            {item["source_id"] for item in actionable},
        )
        self.assertEqual(
            {"confirmed-blocker", "follow-up"},
            {item["kind"] for item in actionable},
        )

        for bucket in ("challenged", "unverified"):
            with self.subTest(bucket=bucket):
                unconfirmed = json.loads(json.dumps(final))
                unconfirmed["consensus"]["confirmed"] = [{"finding": blocker}]
                unconfirmed["consensus"]["challenged"] = []
                unconfirmed["consensus"]["unverified"] = []
                unconfirmed["consensus"][bucket] = [{"finding": followup}]
                with self.assertRaisesRegex(
                    review.ReviewError, "without dual-verifier confirmation"
                ):
                    review.actionable_findings(
                        unconfirmed, [blocker_report, followup_report, omitted_report]
                    )

        normalized = json.loads(json.dumps(blocker))
        normalized["severity"] = "P2"
        normalized["claim"] = "  THE changed branch   returns an incorrect result.  "
        self.assertEqual(
            review.stable_finding_id(blocker), review.stable_finding_id(normalized)
        )

        moved = json.loads(json.dumps(blocker))
        moved["start_line"] = 40
        moved["end_line"] = 42
        self.assertNotEqual(
            review.stable_finding_id(blocker), review.stable_finding_id(moved)
        )
        changed_claim = json.loads(json.dumps(blocker))
        changed_claim["claim"] = "A materially different defect claim."
        self.assertNotEqual(
            review.stable_finding_id(blocker), review.stable_finding_id(changed_claim)
        )

    def test_managed_comment_is_owned_and_updated_by_exact_app_identity(self) -> None:
        app_login = "coco-agent[bot]"
        old_actions_comment = {
            "id": 1,
            "body": (review.COMMENT_MARKER + "\n<!-- agent-jury-run:200:1 -->"),
            "user": {"id": 1, "login": "github-actions[bot]", "type": "Bot"},
        }
        app_comment = {
            "id": 2,
            "body": (review.COMMENT_MARKER + "\n<!-- agent-jury-run:100:1 -->"),
            "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
        }

        class FakeClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                del path, limit
                return [old_actions_comment, app_comment]

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {
                    "id": 2,
                    "body": payload["body"],
                    "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
                }

        client = FakeClient()
        body = review.COMMENT_MARKER + "\n<!-- agent-jury-run:101:1 -->\nResult"
        review.upsert_comment(
            client,
            "patton174/coco-framework",
            60,
            body,
            (101, 1),
            app_login,
            APP_BOT_ID,
        )
        self.assertEqual(
            "repos/patton174/coco-framework/issues/comments/2", client.sent[0][1]
        )

    def test_finding_issue_sync_updates_reopens_creates_and_closes(self) -> None:
        app_login = "coco-agent[bot]"
        context = bound_context()
        current_finding = specialist_report("correctness", context, severity="P2")[
            "findings"
        ][0]
        new_finding = specialist_report("architecture-api", context, severity="P1")[
            "findings"
        ][0]
        current = {
            "stable_id": review.stable_finding_id(current_finding),
            "source_id": current_finding["id"],
            "kind": "follow-up",
            "finding": current_finding,
        }
        new = {
            "stable_id": review.stable_finding_id(new_finding),
            "source_id": new_finding["id"],
            "kind": "confirmed-blocker",
            "finding": new_finding,
        }
        disappeared_id = "v1-" + "e" * 64

        def issue(number: int, marker: str, state: str) -> dict:
            return {
                "number": number,
                "title": "old",
                "body": marker + "\nOld body",
                "state": state,
                "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                "html_url": f"https://github.example/issues/{number}",
                "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
            }

        class FakeClient:
            def __init__(self) -> None:
                self.issues = {
                    10: issue(
                        10,
                        review.finding_issue_marker(60, BASE_SHA, disappeared_id),
                        "open",
                    ),
                    11: issue(
                        11,
                        review.finding_issue_marker(60, BASE_SHA, current["stable_id"]),
                        "closed",
                    ),
                    98: {
                        **issue(
                            98,
                            review.finding_issue_marker(
                                60, BASE_SHA, current["stable_id"]
                            ),
                            "open",
                        ),
                        "user": {"id": 7, "login": "mallory", "type": "User"},
                    },
                    99: {
                        **issue(
                            99,
                            review.finding_issue_marker(60, BASE_SHA, disappeared_id),
                            "open",
                        ),
                        "body": (
                            "Documentation example\n"
                            + review.finding_issue_marker(60, BASE_SHA, disappeared_id)
                        ),
                        "user": {"id": 7, "login": "mallory", "type": "User"},
                    },
                }
                self.comments: list[tuple[int, str]] = []
                self.next_issue = 12
                self.scan_snapshots: list[set[int]] = []

            def get_json(self, path: str) -> dict:
                if path.endswith("/labels/agent-review"):
                    return {"name": review.FINDING_ISSUE_LABEL}
                raise AssertionError(f"Unexpected GET path: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                self.assertEqualLimit(limit)
                if (
                    path
                    != "repos/patton174/coco-framework/issues?state=all&labels=agent-review&sort=created&direction=asc"
                    or "creator=" in path
                ):
                    raise AssertionError(f"Unexpected paginated path: {path}")
                filtered = [
                    value
                    for value in self.issues.values()
                    if review.FINDING_ISSUE_LABEL
                    in {label["name"] for label in value.get("labels", [])}
                ]
                if len(self.scan_snapshots) == 1:
                    filtered = [
                        value for value in filtered if int(value["number"]) != 12
                    ]
                self.scan_snapshots.append({int(value["number"]) for value in filtered})
                return filtered

            @staticmethod
            def assertEqualLimit(limit: int) -> None:
                if limit != 5000:
                    raise AssertionError(f"Unexpected issue limit: {limit}")

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                if method == "POST" and path.endswith("/issues"):
                    number = self.next_issue
                    self.next_issue += 1
                    value = {
                        "number": number,
                        "state": "open",
                        "html_url": f"https://github.example/issues/{number}",
                        "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
                        **payload,
                    }
                    value["labels"] = [{"name": name} for name in payload["labels"]]
                    self.issues[number] = value
                    return value
                if method == "POST" and path.endswith("/comments"):
                    number = int(path.split("/")[-2])
                    self.comments.append((number, payload["body"]))
                    return {
                        "body": payload["body"],
                        "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
                    }
                if method == "PATCH" and "/issues/" in path:
                    number = int(path.rsplit("/", 1)[-1])
                    value = self.issues[number]
                    value.update(payload)
                    if "labels" in payload:
                        value["labels"] = [{"name": name} for name in payload["labels"]]
                    return value
                raise AssertionError(f"Unexpected write: {method} {path}")

        client = FakeClient()
        with patch.object(review.time, "sleep") as sleep:
            synchronized = review.synchronize_finding_issues(
                client,
                "patton174/coco-framework",
                60,
                HEAD_SHA,
                [current, new],
                app_login,
                APP_BOT_ID,
                "https://github.example/runs/1",
                "https://github.example",
                lambda: {},
            )
        sleep.assert_called_once_with(
            review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS[0]
        )
        self.assertEqual(2, len(synchronized))
        self.assertEqual("closed", client.issues[10]["state"])
        self.assertEqual(1, len(client.comments))
        self.assertEqual("open", client.issues[11]["state"])
        marker = review.parse_finding_issue_marker(client.issues[11]["body"])
        self.assertEqual(BASE_SHA, marker["head_sha"])
        self.assertEqual(3, len(client.scan_snapshots))
        self.assertNotIn(12, client.scan_snapshots[0])
        self.assertNotIn(12, client.scan_snapshots[1])
        self.assertIn(12, client.scan_snapshots[2])
        self.assertEqual("open", client.issues[98]["state"])
        self.assertEqual("open", client.issues[99]["state"])
        created = client.issues[12]
        created_marker = review.parse_finding_issue_marker(created["body"])
        self.assertEqual(HEAD_SHA, created_marker["head_sha"])

    def test_finding_issue_convergence_retry_exhaustion_fails_closed(self) -> None:
        finding_id = "v1-" + "9" * 64

        class FakeClient:
            def __init__(self) -> None:
                self.scans = 0

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if (
                    limit != 5000
                    or "issues?state=all&labels=agent-review" not in path
                    or "creator=" in path
                ):
                    raise AssertionError(f"Unexpected paginated path: {path}")
                self.scans += 1
                return []

        client = FakeClient()
        pr_checks: list[int] = []

        def require_current_pr() -> dict:
            pr_checks.append(len(pr_checks))
            return {}

        with patch.object(review.time, "sleep") as sleep:
            with self.assertRaisesRegex(review.ReviewError, "did not converge"):
                review.wait_for_finding_issue_convergence(
                    client,
                    "patton174/coco-framework",
                    60,
                    "coco-agent[bot]",
                    APP_BOT_ID,
                    {finding_id},
                    require_current_pr,
                )
        self.assertEqual(
            list(review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS),
            [value.args[0] for value in sleep.call_args_list],
        )
        attempts = len(review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS) + 1
        self.assertEqual(attempts, client.scans)
        self.assertEqual(attempts * 2, len(pr_checks))

    def test_agent_issue_gate_fails_with_open_issue_and_passes_with_zero(self) -> None:
        app_login = "coco-agent[bot]"
        finding_id = "v1-" + "f" * 64
        marker = review.finding_issue_marker(60, BASE_SHA, finding_id)
        spoof_bodies = (
            "<!-- coco-agent-review: similar but invalid -->",
            marker + "\nValid-looking spoof",
            "Documentation example\n" + marker,
        )

        class FakeClient:
            def __init__(self, include_issue: bool) -> None:
                self.include_issue = include_issue
                self.sent: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> dict:
                if path.endswith("/pulls/60"):
                    return {
                        "state": "open",
                        "head": {"sha": HEAD_SHA},
                        "base": {"sha": BASE_SHA, "ref": "main"},
                    }
                raise AssertionError(f"Unexpected GET path: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if limit != 5000 or (
                    "issues?state=all&labels=agent-review" not in path
                    or "creator=" in path
                ):
                    raise AssertionError(f"Unexpected paginated path: {path}")
                issues = [
                    {
                        "number": 30 + index,
                        "body": body,
                        "state": "open",
                        "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                        "user": {"id": 7, "login": "mallory", "type": "User"},
                    }
                    for index, body in enumerate(spoof_bodies)
                ]
                if self.include_issue:
                    issues.append(
                        {
                            "number": 20,
                            "body": marker + "\nBody",
                            "state": "open",
                            "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                            "user": {
                                "id": APP_BOT_ID,
                                "login": app_login,
                                "type": "Bot",
                            },
                        }
                    )
                return issues

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {}

        for include_issue, expected_state in ((True, "failure"), (False, "success")):
            with self.subTest(include_issue=include_issue):
                client = FakeClient(include_issue)
                with patch.object(issue_gate, "GitHubClient", return_value=client):
                    with patch("builtins.print"):
                        result = issue_gate.command_recompute(
                            SimpleNamespace(
                                repository="patton174/coco-framework",
                                pr_number=60,
                                expected_head_sha=HEAD_SHA,
                                expected_app_login=app_login,
                                expected_app_bot_id=str(APP_BOT_ID),
                                event_path=None,
                                run_url="https://github.example/runs/2",
                            )
                        )
                self.assertEqual(0, result)
                status = client.sent[-1]
                self.assertEqual(
                    f"repos/patton174/coco-framework/statuses/{HEAD_SHA}", status[1]
                )
                self.assertEqual(expected_state, status[2]["state"])
                self.assertEqual(review.ISSUE_STATUS_CONTEXT, status[2]["context"])

    def test_agent_issue_gate_rejects_expected_creator_identity_drift(self) -> None:
        finding_id = "v1-" + "1" * 64
        marker = review.finding_issue_marker(60, BASE_SHA, finding_id)

        class FakeClient:
            def __init__(self, user: dict) -> None:
                self.user = user
                self.sent: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> dict:
                if path.endswith("/pulls/60"):
                    return {
                        "state": "open",
                        "head": {"sha": HEAD_SHA},
                        "base": {"sha": BASE_SHA, "ref": "main"},
                    }
                raise AssertionError(f"Unexpected GET path: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if limit != 5000 or (
                    "issues?state=all&labels=agent-review" not in path
                    or "creator=" in path
                ):
                    raise AssertionError(f"Unexpected paginated path: {path}")
                return [
                    {
                        "number": 20,
                        "body": "Documentation example\n" + marker,
                        "state": "open",
                        "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                        "user": self.user,
                    }
                ]

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {}

        drifted_users = (
            {"id": APP_BOT_ID + 1, "login": "coco-agent[bot]", "type": "Bot"},
            {"id": APP_BOT_ID, "login": "coco-agent[bot]", "type": "User"},
        )
        for user in drifted_users:
            with self.subTest(user=user):
                client = FakeClient(user)
                with patch.object(issue_gate, "GitHubClient", return_value=client):
                    with self.assertRaises(review.ReviewError):
                        issue_gate.command_recompute(
                            SimpleNamespace(
                                repository="patton174/coco-framework",
                                pr_number=60,
                                expected_head_sha=HEAD_SHA,
                                expected_app_login="coco-agent[bot]",
                                expected_app_bot_id=str(APP_BOT_ID),
                                event_path=None,
                                run_url="https://github.example/runs/3",
                            )
                        )
                self.assertEqual("failure", client.sent[-1][2]["state"])
                self.assertEqual(
                    review.ISSUE_STATUS_CONTEXT, client.sent[-1][2]["context"]
                )

    def test_governance_files_follow_naming_convention(self) -> None:
        github_root = Path(__file__).resolve().parents[1]
        workflow_root = github_root / "workflows"
        workflow_name = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*\.yml")
        python_name = re.compile(r"(?:test_)?[a-z][a-z0-9]*(?:_[a-z0-9]+)*\.py")
        node_name = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*\.mjs")

        for path in workflow_root.glob("*.yml"):
            self.assertIsNotNone(workflow_name.fullmatch(path.name), path.name)
            workflow = path.read_text(encoding="utf-8")
            if re.search(r"^  workflow_call:\s*$", workflow, re.MULTILINE):
                self.assertTrue(path.name.startswith("reusable-"), path.name)
        for path in (github_root / "scripts").glob("*.py"):
            self.assertIsNotNone(python_name.fullmatch(path.name), path.name)
        for path in (github_root / "readme" / "scripts").glob("*.mjs"):
            self.assertIsNotNone(node_name.fullmatch(path.name), path.name)

        self.assertTrue((workflow_root / "reusable-tests.yml").exists())
        self.assertTrue((workflow_root / "reusable-static-analysis.yml").exists())
        self.assertTrue((workflow_root / "reusable-codeql.yml").exists())
        static_analysis = (workflow_root / "reusable-static-analysis.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("shellcheck --version", static_analysis)
        self.assertIn("-shellcheck=shellcheck", static_analysis)
        self.assertFalse((github_root / "README.md").exists())
        self.assertTrue((github_root / "workflow-governance.md").exists())
        for legacy_name in (
            "_test.yml",
            "_static-analysis.yml",
            "claude-review.yml",
            "update-readme-insights.yml",
        ):
            self.assertFalse((workflow_root / legacy_name).exists(), legacy_name)

    def test_agent_issue_gate_workflow_has_no_secret_path_and_shared_lock(self) -> None:
        workflow_root = Path(__file__).resolve().parents[1] / "workflows"
        review_workflow = (workflow_root / "agent-review.yml").read_text(
            encoding="utf-8"
        )
        self.assertFalse((workflow_root / "claude-review.yml").exists())
        self.assertTrue(review_workflow.startswith("name: Agent Review Jury\n"))
        trusted = review_workflow.split("\n  trusted-publisher:\n", 1)[1].split(
            "\n  no-secret-publisher:\n", 1
        )[0]
        no_secret = review_workflow.split("\n  no-secret-publisher:\n", 1)[1]
        self.assertIn("environment: coco-agent", trusted)
        self.assertIn(
            "actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1",
            trusted,
        )
        self.assertIn("client-id: ${{ vars.COCO_AGENT_APP_CLIENT_ID }}", trusted)
        self.assertIn("secrets.COCO_AGENT_APP_PRIVATE_KEY", trusted)
        self.assertIn("permission-issues: write", trusted)
        self.assertIn("permission-pull-requests: write", trusted)
        self.assertIn(
            "TOKEN_APP_SLUG: ${{ steps.agent-app-token.outputs.app-slug }}", trusted
        )
        self.assertIn("EXPECTED_APP_SLUG: ${{ vars.COCO_AGENT_APP_SLUG }}", trusted)
        self.assertIn("EXPECTED_APP_LOGIN: ${{ vars.COCO_AGENT_APP_LOGIN }}", trusted)
        self.assertIn("EXPECTED_APP_BOT_ID: ${{ vars.COCO_AGENT_APP_BOT_ID }}", trusted)
        self.assertIn('"${TOKEN_APP_SLUG}" != "${EXPECTED_APP_SLUG}"', trusted)
        self.assertIn("${TOKEN_APP_SLUG}[bot]", trusted)
        self.assertIn('"${actual_login}" != "${EXPECTED_APP_LOGIN}"', trusted)
        self.assertIn('"${bot_id}" != "${EXPECTED_APP_BOT_ID}"', trusted)
        self.assertIn(
            "AGENT_GH_TOKEN: ${{ steps.agent-app-token.outputs.token }}", trusted
        )
        self.assertIn(
            "COCO_AGENT_APP_LOGIN: ${{ steps.app-identity.outputs.login }}", trusted
        )
        self.assertIn(
            "COCO_AGENT_APP_BOT_ID: ${{ steps.app-identity.outputs.bot-id }}", trusted
        )
        self.assertIn("GH_TOKEN: ${{ github.token }}", trusted)
        self.assertNotIn("permission-statuses", trusted)
        self.assertNotIn("environment:", no_secret)
        self.assertNotIn("COCO_AGENT_APP", no_secret)
        self.assertNotIn("AGENT_GH_TOKEN", no_secret)
        self.assertNotIn("create-github-app-token", no_secret)
        self.assertNotIn("private-key", no_secret)
        self.assertNotIn("ANTHROPIC", no_secret)
        self.assertIn("GH_TOKEN: ${{ github.token }}", no_secret)
        self.assertNotIn("repository_dispatch", review_workflow)
        self.assertNotIn("client_payload", review_workflow)
        self.assertNotIn("workflow_dispatch", review_workflow)

        gate_workflow = (workflow_root / "agent-issue-gate.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            "types: [opened, synchronize, reopened, ready_for_review]", gate_workflow
        )
        self.assertIn(
            "types: [opened, closed, reopened, labeled, unlabeled, edited, deleted, transferred]",
            gate_workflow,
        )
        self.assertIn("workflow_dispatch:", gate_workflow)
        self.assertIn("'refs/heads/main'", gate_workflow)
        self.assertIn("vars.COCO_AGENT_APP_LOGIN", gate_workflow)
        self.assertIn("vars.COCO_AGENT_APP_BOT_ID", gate_workflow)
        self.assertIn('--expected-app-login "${COCO_AGENT_APP_LOGIN}"', gate_workflow)
        self.assertIn("GH_TOKEN: ${{ github.token }}", gate_workflow)
        self.assertIn("agent-review-publisher-", gate_workflow)
        self.assertNotIn("ANTHROPIC", gate_workflow)
        self.assertNotIn("COCO_AGENT_APP_PRIVATE_KEY", gate_workflow)

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

    def test_rendered_comment_keeps_unconfirmed_low_findings_visible(self) -> None:
        context = bound_context()
        specialist = specialist_report("correctness", context, severity="P3")
        finding_id = specialist["findings"][0]["id"]
        evidence = verifier_report("evidence-verifier", context, finding_id)
        chair = {
            "verdict": "PASS",
            "summary": "No independently confirmed blockers remain.",
            "follow_up_finding_ids": [],
        }
        for action, state in (("DISAGREE", "challenged"), ("UNVERIFIED", "unverified")):
            with self.subTest(action=action):
                policy = verifier_report(
                    "policy-skeptic", context, finding_id, action=action
                )
                consensus = review.compute_consensus([specialist], [evidence, policy])
                markdown = review.render_review(
                    context, [specialist], [evidence, policy], consensus, chair
                )
                self.assertIn(f"`{finding_id}`; {state}", markdown)
                self.assertIn(f"`policy-skeptic`: **{action}**", markdown)

    def test_rendered_comment_compacts_before_github_size_limit(self) -> None:
        context = bound_context()
        specialists = []
        finding_ids = []
        for role in review.role_map(config(), "specialists"):
            report = specialist_report(role, context)
            template = report["findings"][0]
            report["findings"] = []
            for index in range(10):
                finding = json.loads(json.dumps(template))
                finding["id"] = f"{role}:f{index}"
                finding["title"] = "@review " + ("title" * 100)
                finding["claim"] = "claim" * 500
                finding["trigger"] = "trigger" * 300
                finding["impact"] = "impact" * 500
                report["findings"].append(finding)
                finding_ids.append(finding["id"])
            specialists.append(report)
        verifiers = []
        for role in review.role_map(config(), "verifiers"):
            report = verifier_report(role, context, finding_ids[0])
            template = report["reviews"][0]
            report["reviews"] = []
            for finding_id in finding_ids:
                entry = json.loads(json.dumps(template))
                entry["finding_id"] = finding_id
                report["reviews"].append(entry)
            verifiers.append(report)
        consensus = review.compute_consensus(specialists, verifiers)
        chair = {
            "verdict": "BLOCK",
            "summary": "@review " + ("summary" * 500),
            "follow_up_finding_ids": [],
            "questions": [],
        }
        markdown = review.render_review(
            context, specialists, verifiers, consensus, chair
        )
        self.assertIn("Compact view", markdown)
        self.assertLessEqual(review.utf8_size(markdown), review.MAX_REVIEW_BODY_BYTES)
        self.assertNotIn("@review", markdown)
        for finding_id in finding_ids:
            self.assertIn(f"`{finding_id}`", markdown)
        synchronized = []
        issue_number = 1
        for report in specialists:
            for finding in report["findings"]:
                synchronized.append(
                    {
                        "issue": {
                            "number": issue_number,
                            "html_url": (
                                "https://github.com/patton174/coco-framework/issues/"
                                f"{issue_number}"
                            ),
                        },
                        "actionable": {
                            "finding": finding,
                            "stable_id": f"v1-{issue_number:064x}",
                        },
                    }
                )
                issue_number += 1
        complete_comment = review.append_finding_issue_summary(
            markdown,
            synchronized,
            "patton174/coco-framework",
            "https://github.com",
        )
        complete_comment += (
            "\n<sub>Updated 2026-07-11T00:00:00+00:00 - "
            "[workflow run](https://github.com/patton174/coco-framework/actions/runs/1)</sub>\n"
        )
        self.assertLessEqual(
            review.utf8_size(complete_comment),
            review.MAX_GITHUB_COMMENT_BODY_BYTES,
        )
        for report in specialists:
            for finding in report["findings"]:
                finding["severity"] = "P3"
        for report in verifiers:
            for entry in report["reviews"]:
                entry["evidence"] = "evidence" * 100
                if report["role"] == "policy-skeptic":
                    entry["action"] = "DISAGREE"
        challenged_consensus = review.compute_consensus(specialists, verifiers)
        challenged_markdown = review.render_review(
            context,
            specialists,
            verifiers,
            challenged_consensus,
            {
                "verdict": "PASS",
                "summary": "No independently confirmed blockers remain.",
                "follow_up_finding_ids": [],
                "questions": [],
            },
        )
        self.assertIn("Compact view", challenged_markdown)
        self.assertIn("challenged", challenged_markdown)
        self.assertLessEqual(
            review.utf8_size(challenged_markdown),
            review.MAX_REVIEW_BODY_BYTES,
        )
        with self.assertRaises(review.ReviewError):
            review.require_comment_size(
                "x" * (review.MAX_GITHUB_COMMENT_BODY_BYTES + 1),
                review.MAX_GITHUB_COMMENT_BODY_BYTES,
                "test comment",
            )

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

    def test_no_secret_publish_writes_only_bound_status(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> dict:
                if path == "repos/patton174/coco-framework/pulls/1":
                    return {
                        "state": "open",
                        "head": {"sha": HEAD_SHA},
                        "base": {"sha": BASE_SHA, "ref": "main"},
                    }
                if path.endswith("/collaborators/maintainer/permission"):
                    return {"permission": "write"}
                raise AssertionError(f"Unexpected GET path: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                self.assert_review_path(path, limit)
                return [
                    {
                        "state": "APPROVED",
                        "commit_id": HEAD_SHA,
                        "user": {"login": "maintainer", "type": "User"},
                    }
                ]

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {}

            @staticmethod
            def assert_review_path(path: str, limit: int) -> None:
                if path != "repos/patton174/coco-framework/pulls/1/reviews":
                    raise AssertionError(f"Unexpected paginated path: {path}")
                if limit != 500:
                    raise AssertionError(f"Unexpected review limit: {limit}")

        metadata = {
            "repository": "patton174/coco-framework",
            "pr_number": 1,
            "base_sha": BASE_SHA,
            "head_sha": HEAD_SHA,
            "trusted": False,
            "ignored": False,
        }
        with tempfile.TemporaryDirectory() as directory:
            metadata_path = Path(directory) / "metadata.json"
            review.write_json(metadata_path, metadata)
            client = FakeClient()
            with patch.object(review, "GitHubClient", return_value=client):
                with patch("builtins.print") as output:
                    result = review.command_publish(
                        SimpleNamespace(
                            metadata=metadata_path,
                            run_url="https://github.example/runs/1",
                        )
                    )

        self.assertEqual(0, result)
        output.assert_called_once()
        self.assertEqual(1, len(client.sent))
        method, path, payload = client.sent[0]
        self.assertEqual("POST", method)
        self.assertEqual(f"repos/patton174/coco-framework/statuses/{HEAD_SHA}", path)
        self.assertEqual("success", payload["state"])
        self.assertNotIn("comments", path)

    def test_no_secret_publish_without_approval_remains_pending(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> dict:
                if path == "repos/patton174/coco-framework/pulls/1":
                    return {
                        "state": "open",
                        "head": {"sha": HEAD_SHA},
                        "base": {"sha": BASE_SHA, "ref": "main"},
                    }
                raise AssertionError(f"Unexpected GET path: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                self.assert_review_path(path, limit)
                return []

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {}

            @staticmethod
            def assert_review_path(path: str, limit: int) -> None:
                if path != "repos/patton174/coco-framework/pulls/1/reviews":
                    raise AssertionError(f"Unexpected paginated path: {path}")
                if limit != 500:
                    raise AssertionError(f"Unexpected review limit: {limit}")

        metadata = {
            "repository": "patton174/coco-framework",
            "pr_number": 1,
            "base_sha": BASE_SHA,
            "head_sha": HEAD_SHA,
            "trusted": False,
            "ignored": False,
        }
        with tempfile.TemporaryDirectory() as directory:
            metadata_path = Path(directory) / "metadata.json"
            review.write_json(metadata_path, metadata)
            client = FakeClient()
            with patch.object(review, "GitHubClient", return_value=client):
                with patch("builtins.print"):
                    result = review.command_publish(
                        SimpleNamespace(
                            metadata=metadata_path,
                            run_url="https://github.example/runs/1",
                        )
                    )

        self.assertEqual(0, result)
        self.assertEqual(1, len(client.sent))
        self.assertEqual("pending", client.sent[0][2]["state"])

    def test_no_secret_publish_rejects_pr_drift_before_status(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.pull_reads = 0
                self.sent: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> dict:
                if path == "repos/patton174/coco-framework/pulls/1":
                    self.pull_reads += 1
                    head_sha = HEAD_SHA if self.pull_reads == 1 else "c" * 40
                    return {
                        "state": "open",
                        "head": {"sha": head_sha},
                        "base": {"sha": BASE_SHA, "ref": "main"},
                    }
                if path.endswith("/collaborators/maintainer/permission"):
                    return {"permission": "write"}
                raise AssertionError(f"Unexpected GET path: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                del path, limit
                return [
                    {
                        "state": "APPROVED",
                        "commit_id": HEAD_SHA,
                        "user": {"login": "maintainer", "type": "User"},
                    }
                ]

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {}

        metadata = {
            "repository": "patton174/coco-framework",
            "pr_number": 1,
            "base_sha": BASE_SHA,
            "head_sha": HEAD_SHA,
            "trusted": False,
            "ignored": False,
        }
        with tempfile.TemporaryDirectory() as directory:
            metadata_path = Path(directory) / "metadata.json"
            review.write_json(metadata_path, metadata)
            client = FakeClient()
            with patch.object(review, "GitHubClient", return_value=client):
                with self.assertRaisesRegex(review.ReviewError, "changed"):
                    review.command_publish(
                        SimpleNamespace(
                            metadata=metadata_path,
                            run_url="https://github.example/runs/1",
                        )
                    )

        self.assertEqual(2, client.pull_reads)
        self.assertEqual([], client.sent)

    def test_publisher_jobs_are_serialized_across_event_groups(self) -> None:
        workflow = (
            Path(__file__).resolve().parents[1] / "workflows/agent-review.yml"
        ).read_text(encoding="utf-8")
        trusted = workflow.split("\n  trusted-publisher:\n", 1)[1].split(
            "\n  no-secret-publisher:\n", 1
        )[0]
        no_secret = workflow.split("\n  no-secret-publisher:\n", 1)[1]
        for publisher in (trusted, no_secret):
            self.assertIn("agent-review-publisher-", publisher)
            self.assertIn("cancel-in-progress: false", publisher)

    def test_anthropic_client_classifies_retryable_model_output_failures(self) -> None:
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

            cases = [
                (
                    "max_tokens",
                    {
                        "stop_reason": "max_tokens",
                        "content": [{"type": "text", "text": '{"ok":'}],
                    },
                    "max_tokens",
                ),
                (
                    "empty content",
                    {
                        "stop_reason": "end_turn",
                        "content": [],
                    },
                    "no text",
                ),
                (
                    "empty text",
                    {
                        "stop_reason": "end_turn",
                        "content": [{"type": "text", "text": "   "}],
                    },
                    "no text",
                ),
                (
                    "invalid output JSON",
                    {
                        "stop_reason": "end_turn",
                        "content": [{"type": "text", "text": "not-json"}],
                    },
                    "strict JSON",
                ),
            ]
            for name, response_payload, message in cases:
                with self.subTest(name=name):
                    with patch(
                        "urllib.request.urlopen",
                        return_value=FakeResponse(
                            json.dumps(response_payload).encode()
                        ),
                    ):
                        with self.assertRaisesRegex(
                            review.RetryableModelOutputError, message
                        ):
                            client.complete("system", "user", 100)

    def test_anthropic_client_refusal_precedes_max_tokens(self) -> None:
        class FakeResponse:
            def __enter__(self) -> "FakeResponse":
                return self

            def __exit__(self, *args: object) -> None:
                return None

            def read(self, limit: int) -> bytes:
                payload = {
                    "stop_reason": "max_tokens",
                    "content": [{"type": "refusal", "text": "No"}],
                }
                return json.dumps(payload).encode()[:limit]

        with patch.dict(
            "os.environ",
            {
                "ANTHROPIC_API_KEY": "test",
                "ANTHROPIC_BASE_URL": "https://example.invalid",
            },
            clear=False,
        ):
            client = review.AnthropicClient(config())
            with patch("urllib.request.urlopen", return_value=FakeResponse()):
                with self.assertRaisesRegex(review.ReviewError, "refused") as raised:
                    client.complete("system", "user", 100)
        self.assertNotIsInstance(raised.exception, review.RetryableModelOutputError)

    def test_anthropic_client_keeps_other_response_errors_non_retryable(self) -> None:
        class FakeResponse:
            def __init__(self, body: bytes) -> None:
                self.body = body

            def __enter__(self) -> "FakeResponse":
                return self

            def __exit__(self, *args: object) -> None:
                return None

            def read(self, limit: int) -> bytes:
                return self.body[:limit]

        cases = [
            (
                "other stop reason",
                json.dumps(
                    {
                        "stop_reason": "stop_sequence",
                        "content": [{"type": "text", "text": '{"ok":true}'}],
                    }
                ).encode(),
                "did not complete",
            ),
            (
                "invalid API JSON",
                b"not-an-envelope",
                "API returned invalid JSON",
            ),
            (
                "invalid envelope",
                json.dumps({"stop_reason": "end_turn", "content": {}}).encode(),
                "invalid response envelope",
            ),
            (
                "non-object output",
                json.dumps(
                    {
                        "stop_reason": "end_turn",
                        "content": [{"type": "text", "text": "[]"}],
                    }
                ).encode(),
                "JSON object",
            ),
        ]
        with patch.dict(
            "os.environ",
            {
                "ANTHROPIC_API_KEY": "test",
                "ANTHROPIC_BASE_URL": "https://example.invalid",
            },
            clear=False,
        ):
            client = review.AnthropicClient(config())
            for name, body, message in cases:
                with self.subTest(name=name):
                    with patch(
                        "urllib.request.urlopen", return_value=FakeResponse(body)
                    ):
                        with self.assertRaisesRegex(
                            review.ReviewError, message
                        ) as raised:
                            client.complete("system", "user", 100)
                    self.assertNotIsInstance(
                        raised.exception, review.RetryableModelOutputError
                    )

    def test_anthropic_client_rejects_malformed_content_blocks_before_stop_reason(
        self,
    ) -> None:
        class FakeResponse:
            def __init__(self, body: bytes) -> None:
                self.body = body

            def __enter__(self) -> "FakeResponse":
                return self

            def __exit__(self, *args: object) -> None:
                return None

            def read(self, limit: int) -> bytes:
                return self.body[:limit]

        cases = [
            ("non-dict block", ["not-a-block"], "end_turn"),
            ("missing type", [{"text": "{}"}], "end_turn"),
            ("unknown type", [{"type": "tool_use", "text": "{}"}], "end_turn"),
            ("non-string text", [{"type": "text", "text": 1}], "end_turn"),
            ("malformed before max_tokens", [None], "max_tokens"),
        ]
        with patch.dict(
            "os.environ",
            {
                "ANTHROPIC_API_KEY": "test",
                "ANTHROPIC_BASE_URL": "https://example.invalid",
            },
            clear=False,
        ):
            client = review.AnthropicClient(config())
            for name, content, stop_reason in cases:
                with self.subTest(name=name):
                    payload = {"stop_reason": stop_reason, "content": content}
                    with patch(
                        "urllib.request.urlopen",
                        return_value=FakeResponse(json.dumps(payload).encode()),
                    ):
                        with self.assertRaisesRegex(
                            review.ReviewError, "invalid response envelope"
                        ) as raised:
                            client.complete("system", "user", 100)
                    self.assertNotIsInstance(
                        raised.exception, review.RetryableModelOutputError
                    )

    def test_anthropic_client_keeps_http_and_transport_errors_non_retryable(
        self,
    ) -> None:
        cases = [
            (
                "authentication",
                review.urllib.error.HTTPError(
                    "https://example.invalid/v1/messages",
                    401,
                    "Unauthorized",
                    None,
                    None,
                ),
                "HTTP 401",
            ),
            (
                "transport",
                review.urllib.error.URLError("connection failed"),
                "transport failed",
            ),
        ]
        with patch.dict(
            "os.environ",
            {
                "ANTHROPIC_API_KEY": "test",
                "ANTHROPIC_BASE_URL": "https://example.invalid",
            },
            clear=False,
        ):
            client = review.AnthropicClient(config())
            for name, error, message in cases:
                with self.subTest(name=name):
                    try:
                        with patch("urllib.request.urlopen", side_effect=error):
                            with self.assertRaisesRegex(
                                review.ReviewError, message
                            ) as raised:
                                client.complete("system", "user", 100)
                        self.assertNotIsInstance(
                            raised.exception, review.RetryableModelOutputError
                        )
                    finally:
                        if isinstance(error, review.urllib.error.HTTPError):
                            error.close()

    def test_anthropic_client_rejects_insecure_relay_url(self) -> None:
        with patch.dict(
            "os.environ",
            {"ANTHROPIC_API_KEY": "test", "ANTHROPIC_BASE_URL": "http://relay.invalid"},
            clear=False,
        ):
            with self.assertRaises(review.ReviewError):
                review.AnthropicClient(config())

    def test_retryable_output_failure_retries_once_with_same_arguments(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.responses = [
                    review.RetryableModelOutputError("retryable"),
                    {"required": True},
                ]
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                response = self.responses.pop(0)
                if isinstance(response, Exception):
                    raise response
                return response

        client = FakeClient()

        def validate(value: dict) -> None:
            review.require_report_fields(value, {"required"}, "Test report")

        system = "protected system"
        user = '{"task":"review"}'
        max_tokens = 100
        expected_call = (system, user, max_tokens)
        with patch("builtins.print") as warning:
            result = review.complete_with_shape_repair(
                client, system, user, max_tokens, validate
            )

        self.assertEqual({"required": True}, result)
        self.assertEqual([expected_call, expected_call], client.calls)
        warning.assert_called_once()

    def test_retryable_output_failure_stops_after_bounded_completions(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.calls = 0

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                del system, user, max_tokens
                self.calls += 1
                raise review.RetryableModelOutputError(f"retryable {self.calls}")

        client = FakeClient()
        with patch("builtins.print"):
            with self.assertRaisesRegex(
                review.RetryableModelOutputError,
                f"retryable {review.MODEL_COMPLETION_MAX_ATTEMPTS}",
            ):
                review.complete_with_shape_repair(
                    client,
                    "protected system",
                    '{"task":"review"}',
                    100,
                    lambda value: value,
                )
        self.assertEqual(review.MODEL_COMPLETION_MAX_ATTEMPTS, client.calls)

    def test_refusal_error_does_not_retry_completion(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.calls = 0

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                del system, user, max_tokens
                self.calls += 1
                raise review.ReviewError("Anthropic refused the review.")

        client = FakeClient()
        with self.assertRaisesRegex(review.ReviewError, "refused"):
            review.complete_with_shape_repair(
                client,
                "protected system",
                '{"task":"review"}',
                100,
                lambda value: value,
            )
        self.assertEqual(1, client.calls)

    def test_fresh_retry_shape_error_receives_final_protocol_correction(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                if len(self.calls) == 1:
                    raise review.RetryableModelOutputError("retryable")
                if len(self.calls) == 2:
                    return {"wrong": True}
                return {"required": True}

        client = FakeClient()

        def validate(value: dict) -> None:
            review.require_report_fields(value, {"required"}, "Test report")

        with patch("builtins.print") as warning:
            result = review.complete_with_shape_repair(
                client,
                "protected system",
                '{"task":"review"}',
                100,
                validate,
            )
        self.assertEqual({"required": True}, result)
        self.assertEqual(3, len(client.calls))
        self.assertIn("Protected protocol correction", client.calls[2][0])
        self.assertEqual(2, warning.call_count)

    def test_shape_repair_retries_once_with_bound_original_task(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.responses = [{"wrong": True}, {"required": True}]
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return self.responses.pop(0)

        client = FakeClient()

        def validate(value: dict) -> None:
            review.require_report_fields(value, {"required"}, "Test report")

        with patch("builtins.print") as warning:
            result = review.complete_with_shape_repair(
                client, "protected system", '{"task":"review"}', 100, validate
            )

        self.assertEqual({"required": True}, result)
        warning.assert_called_once()
        self.assertEqual(2, len(client.calls))
        self.assertIn("Protected protocol correction", client.calls[1][0])
        repair_payload = json.loads(client.calls[1][1])
        self.assertEqual({"task": "review"}, repair_payload["original_task"])
        self.assertEqual({"wrong": True}, repair_payload["previous_response"])
        self.assertIn("missing=['required']", repair_payload["validator_message"])

    def test_bound_report_contract_type_errors_receive_correction(self) -> None:
        class FakeClient:
            def __init__(self, responses: list[dict]) -> None:
                self.responses = responses
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return self.responses.pop(0)

        context = bound_context()
        specialist_invalid = specialist_report("correctness", context)
        specialist_invalid["context_gaps"] = "not-an-array"
        specialist_valid = specialist_report("correctness", context)

        finding_id = specialist_valid["findings"][0]["id"]
        cross_invalid = verifier_report("evidence-verifier", context, finding_id)
        cross_invalid["reviews"] = "not-an-array"
        cross_valid = verifier_report("evidence-verifier", context, finding_id)

        chair_valid = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "PASS",
            "summary": "No independently confirmed blockers.",
            "confirmed_blocker_ids": [],
            "follow_up_finding_ids": [],
            "questions": [],
        }
        chair_invalid = dict(chair_valid)
        chair_invalid["questions"] = "not-an-array"
        consensus = {"confirmed": [], "challenged": [], "unverified": []}

        cases = [
            (
                "specialist",
                specialist_invalid,
                specialist_valid,
                lambda value: review.validate_specialist_report(
                    value, "correctness", context, 8
                ),
            ),
            (
                "cross-review",
                cross_invalid,
                cross_valid,
                lambda value: review.validate_cross_report(
                    value, "evidence-verifier", context, {finding_id}
                ),
            ),
            (
                "chair",
                chair_invalid,
                chair_valid,
                lambda value: review.validate_chair(value, consensus, context),
            ),
        ]
        for name, invalid, valid, validate in cases:
            with self.subTest(role=name):
                with self.assertRaises(review.ReportShapeError):
                    validate(invalid)
                client = FakeClient([invalid, valid])
                with patch("builtins.print") as warning:
                    result = review.complete_with_shape_repair(
                        client,
                        "protected system",
                        '{"task":"review"}',
                        100,
                        validate,
                    )
                self.assertEqual(valid, result)
                self.assertEqual(2, len(client.calls))
                warning.assert_called_once()
                self.assertIn("Protected protocol correction", client.calls[1][0])

    def test_shape_repair_fails_closed_after_bounded_shape_errors(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.calls = 0

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                del system, user, max_tokens
                self.calls += 1
                return {"wrong": self.calls}

        client = FakeClient()

        def validate(value: dict) -> None:
            review.require_report_fields(value, {"required"}, "Test report")

        with patch("builtins.print"):
            with self.assertRaises(review.ReportShapeError):
                review.complete_with_shape_repair(
                    client, "protected system", '{"task":"review"}', 100, validate
                )
        self.assertEqual(review.MODEL_COMPLETION_MAX_ATTEMPTS, client.calls)

    def test_report_identity_schema_version_errors_do_not_retry(self) -> None:
        class FakeClient:
            def __init__(self, response: dict) -> None:
                self.response = response
                self.calls = 0

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                del system, user, max_tokens
                self.calls += 1
                return self.response

        context = bound_context()
        for invalid_version in (True, 1.0):
            with self.subTest(version=invalid_version):
                report = specialist_report("correctness", context)
                report["schema_version"] = invalid_version
                client = FakeClient(report)
                with self.assertRaises(review.ReviewError) as raised:
                    review.complete_with_shape_repair(
                        client,
                        "protected system",
                        '{"task":"review"}',
                        100,
                        lambda value: review.validate_specialist_report(
                            value, "correctness", context, 8
                        ),
                    )
                self.assertEqual(1, client.calls)
                self.assertNotIsInstance(raised.exception, review.ReportShapeError)

    def test_shape_repair_does_not_retry_mixed_shape_and_binding_errors(self) -> None:
        class FakeClient:
            def __init__(self, response: dict) -> None:
                self.response = response
                self.calls = 0

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                del system, user, max_tokens
                self.calls += 1
                return self.response

        context = bound_context()
        specialist = specialist_report("correctness", context)
        finding_id = specialist["findings"][0]["id"]
        verifier = verifier_report("evidence-verifier", context, finding_id)
        chair = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "PASS",
            "summary": "No independently confirmed blockers.",
            "confirmed_blocker_ids": [],
            "follow_up_finding_ids": [],
            "questions": [],
        }
        consensus = {"confirmed": [], "challenged": [], "unverified": []}
        cases = [
            (
                specialist,
                lambda value: review.validate_specialist_report(
                    value, "correctness", context, 8
                ),
            ),
            (
                verifier,
                lambda value: review.validate_cross_report(
                    value, "evidence-verifier", context, {finding_id}
                ),
            ),
            (
                chair,
                lambda value: review.validate_chair(value, consensus, context),
            ),
        ]

        for response, validate in cases:
            with self.subTest(role=response["role"]):
                response["head_sha"] = "c" * 40
                response["unexpected"] = True
                client = FakeClient(response)
                with self.assertRaisesRegex(
                    review.ReviewError, "binding mismatch"
                ) as raised:
                    review.complete_with_shape_repair(
                        client,
                        "protected system",
                        '{"task":"review"}',
                        100,
                        validate,
                    )
                self.assertEqual(1, client.calls)
                self.assertNotIsInstance(raised.exception, review.ReportShapeError)


if __name__ == "__main__":
    unittest.main(verbosity=2)
