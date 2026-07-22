#!/usr/bin/env python3

from __future__ import annotations

import io
import json
import re
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import agent_issue_gate as issue_gate
import agent_review as review


BASE_SHA = "a" * 40
HEAD_SHA = "b" * 40
HEAD_REF = "dependabot/maven/example-1.0.1"
APP_BOT_ID = 424242
DEPENDABOT_BOT_ID = 49_699_333
REPOSITORY = "patton174/coco-framework"
REPOSITORY_ID = 123456789
DEFERRED_PR_NUMBER = 125
SOURCE_RUN_ID = 987654321
RELEASE_APP_ACTION_SHA = "bcd2ba49218906704ab6c1aa796996da409d3eb1"


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


def deferred_config() -> dict:
    value = config()
    value["deferred_bot_authors"] = [
        {"login": "dependabot[bot]", "id": DEPENDABOT_BOT_ID}
    ]
    return value


def deferred_pull_request() -> dict:
    return {
        "number": DEFERRED_PR_NUMBER,
        "state": "open",
        "changed_files": 1,
        "base": {
            "ref": "main",
            "sha": BASE_SHA,
            "repo": {"id": REPOSITORY_ID, "full_name": REPOSITORY},
        },
        "head": {
            "ref": HEAD_REF,
            "sha": HEAD_SHA,
            "repo": {"id": REPOSITORY_ID, "full_name": REPOSITORY},
        },
        "user": {
            "id": DEPENDABOT_BOT_ID,
            "login": "dependabot[bot]",
            "type": "Bot",
        },
    }


def deferred_workflow_run() -> dict:
    run_title = f"Agent Review Jury / PR #{DEFERRED_PR_NUMBER} / {HEAD_SHA}"
    return {
        "id": SOURCE_RUN_ID,
        "name": run_title,
        "path": review.DEFERRED_WORKFLOW_PATH,
        "event": review.DEFERRED_WORKFLOW_EVENT,
        "status": "completed",
        "conclusion": "success",
        "display_title": run_title,
        "repository": {"id": REPOSITORY_ID, "full_name": REPOSITORY},
        "head_repository": {"id": REPOSITORY_ID, "full_name": REPOSITORY},
        "head_sha": HEAD_SHA,
        "head_branch": HEAD_REF,
        "pull_requests": [{"number": DEFERRED_PR_NUMBER}],
    }


def trusted_metadata(run_id: int = 42, run_attempt: int = 1) -> dict:
    return {
        "schema_version": 1,
        "repository": REPOSITORY,
        "repository_id": REPOSITORY_ID,
        "pr_number": 1,
        "base_sha": BASE_SHA,
        "head_sha": HEAD_SHA,
        "review_route": review.PR_ROUTE_DIRECT,
        "trusted": True,
        "deferred": False,
        "ignored": False,
        "source_run_id": 0,
        "run_id": str(run_id),
        "run_attempt": str(run_attempt),
    }


def combined_ownership_status(run_id: int, run_attempt: int = 1) -> dict:
    return {
        "statuses": [
            {
                "context": review.OWNERSHIP_STATUS_CONTEXT,
                "description": review.run_ownership_description((run_id, run_attempt)),
            }
        ]
    }


class FakeDeferredClient:
    def __init__(
        self,
        *,
        run: dict | None = None,
        pull_request: dict | None = None,
        associated: list[dict] | None = None,
    ) -> None:
        self.run = json.loads(json.dumps(run or deferred_workflow_run()))
        if associated is not None:
            self.run["pull_requests"] = associated
        self.pull_request = pull_request or deferred_pull_request()
        self.get_paths: list[str] = []

    def get_json(self, path: str) -> dict:
        self.get_paths.append(path)
        if path == f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}":
            return self.run
        if path == f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}":
            return self.pull_request
        raise AssertionError(f"Unexpected GET path: {path}")

    def paginate(self, path: str, limit: int = 1000) -> list[dict]:
        raise AssertionError(f"Unexpected paginated path: {path} ({limit})")


class AgentReviewTests(unittest.TestCase):
    def test_repository_config_resolves_complete_jury_and_policy_routes(self) -> None:
        """Check routing and tracked integration inputs, not module behavior."""
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
        self.assertEqual(8192, value["output_limits"]["specialist_tokens"])
        self.assertEqual(12288, value["output_limits"]["verifier_tokens"])
        self.assertEqual(8192, value["output_limits"]["chair_tokens"])
        self.assertEqual(
            (("dependabot[bot]", DEPENDABOT_BOT_ID),),
            review.configured_deferred_bot_authors(value),
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

        jury_spec = "coco-support/coco-document/superpowers/specs/2026-07-10-multi-agent-review-jury.md"
        governance_spec = "coco-support/coco-document/superpowers/specs/2026-07-11-agent-governance-automation.md"
        module_layout_spec = "coco-support/coco-document/architecture/module-layout.md"
        api_i18n_spec = "coco-support/coco-document/superpowers/specs/2026-07-04-coco-api-core-i18n-design.md"
        common_i18n_spec = "coco-support/coco-document/superpowers/specs/2026-07-04-coco-common-i18n-design.md"
        web_response_spec = "coco-support/coco-document/superpowers/specs/2026-07-05-coco-web-response-wrap-design.md"
        jdbc_replay_spec = "coco-support/coco-document/superpowers/specs/2026-07-10-coco-jdbc-replay-store.md"
        framework_boundary_spec = "coco-support/coco-document/superpowers/specs/2026-07-08-coco-web-server-framework-boundary.md"
        audit_logging_spec = "coco-support/coco-document/superpowers/specs/2026-07-10-coco-default-audit-logging.md"
        audit_independence_spec = "coco-support/coco-document/superpowers/specs/2026-07-10-coco-audit-feature-independence.md"
        logging_overflow_spec = "coco-support/coco-document/superpowers/specs/2026-07-10-coco-async-log-overflow-observability.md"
        codegen_spec = "coco-support/coco-document/superpowers/specs/2026-07-10-coco-default-crud-codegen.md"

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
            ".github/workflows/agent-open-pr.yml",
            ".github/workflows/release.yml",
            ".github/workflows/pr-labeler.yml",
            ".github/labeler.yml",
            ".github/dependabot.yml",
            ".github/CODEOWNERS",
            ".github/ISSUE_TEMPLATE/bug-report.yml",
            ".github/PULL_REQUEST_TEMPLATE.md",
            ".github/readme/fragments/en/overview.md",
            ".github/workflows/readme-maintenance.yml",
            ".github/workflow-governance.md",
            "AGENTS.md",
            "CODE_OF_CONDUCT.md",
            "CONTRIBUTING.md",
            "GOVERNANCE.md",
            "LICENSE",
            "README.md",
            "README_CN.md",
            "SECURITY.md",
            "SUPPORT.md",
            "coco-support/coco-document/release.md",
            governance_spec,
        ):
            with self.subTest(path=path):
                self.assertIn(governance_spec, mapped_specs(path))
        module_layout_policy_candidates = (
            "coco-parent/pom.xml",
            "coco-build/coco-parent/pom.xml",
            "coco-api/pom.xml",
            "coco-foundation/coco-api/pom.xml",
            "coco-config/pom.xml",
            "coco-spring/coco-config/pom.xml",
            "coco-build/coco-compatibility/coco-config/pom.xml",
            "coco-features/coco-feature-runtime/pom.xml",
            "coco-build/coco-compatibility/coco-feature-runtime/pom.xml",
            "coco-test/pom.xml",
            "coco-support/coco-test/pom.xml",
            "coco-support/coco-test-support/pom.xml",
            "coco-build/coco-compatibility/coco-test/pom.xml",
        )
        for path in module_layout_policy_candidates:
            with self.subTest(path=path):
                self.assertIn(module_layout_spec, mapped_specs(path))
        test_support_policy_candidates = (
            "coco-support/coco-test/pom.xml",
            "coco-support/coco-test-support/pom.xml",
            "coco-build/coco-compatibility/coco-test/pom.xml",
        )
        for path in test_support_policy_candidates:
            with self.subTest(test_support_policy_candidate=path):
                self.assertEqual({module_layout_spec}, mapped_specs(path))
        i18n_specs = {module_layout_spec, api_i18n_spec, common_i18n_spec}
        web_specs = {
            module_layout_spec,
            web_response_spec,
            jdbc_replay_spec,
            framework_boundary_spec,
        }
        audit_specs = {
            module_layout_spec,
            audit_logging_spec,
            audit_independence_spec,
        }
        module_policy_routes = {
            ("coco-api", "coco-foundation/coco-api"): i18n_specs,
            (
                "coco-common/coco-common-i18n",
                "coco-foundation/coco-i18n",
            ): i18n_specs,
            (
                "coco-features/coco-feature-registry",
                "coco-foundation/coco-feature-model",
            ): i18n_specs,
            (
                "coco-config",
                "coco-spring/coco-config",
                "coco-build/coco-compatibility/coco-config",
            ): i18n_specs,
            (
                "coco-spring-boot-autoconfigure",
                "coco-spring/coco-spring-boot-autoconfigure",
            ): i18n_specs,
            (
                "coco-common/coco-common-logging",
                "coco-foundation/coco-logging",
            ): {module_layout_spec, logging_overflow_spec},
            (
                "coco-features/coco-feature-web",
                "coco-features/coco-web",
            ): web_specs,
            (
                "coco-features/coco-feature-audit",
                "coco-features/coco-audit",
            ): audit_specs,
            ("coco-features/coco-feature-codegen",): {
                module_layout_spec,
                codegen_spec,
            },
            ("coco-maven-plugin", "coco-build/coco-maven-plugin"): {
                module_layout_spec,
                codegen_spec,
            },
        }
        # Candidate paths intentionally include future physical locations. This
        # loop asserts only config routing; Maven integration owns materialized
        # modules, compilation, and compatibility-consumer execution.
        for candidate_paths, expected_specs in module_policy_routes.items():
            for candidate_root in candidate_paths:
                for relative_path in (
                    "pom.xml",
                    "README.md",
                    "src/main/java/Example.java",
                    "src/main/resources/example.properties",
                ):
                    with self.subTest(
                        policy_candidate=candidate_root,
                        relative_path=relative_path,
                    ):
                        self.assertEqual(
                            expected_specs,
                            mapped_specs(f"{candidate_root}/{relative_path}"),
                        )
        self.assertEqual(
            {governance_spec},
            mapped_specs("coco-support/coco-document/release.md"),
        )
        self.assertEqual({governance_spec}, mapped_specs(governance_spec))
        self.assertEqual({module_layout_spec}, mapped_specs(module_layout_spec))
        support_directories = {
            path.name
            for path in (repository_root / "coco-support").iterdir()
            if path.is_dir()
            and not path.name.startswith(".")
            and any(
                child.name != "target" and not child.name.startswith(".")
                for child in path.iterdir()
            )
        }
        test_support_source_directories = {"coco-test", "coco-test-support"}
        active_test_support_directories = (
            support_directories & test_support_source_directories
        )
        self.assertEqual(
            1,
            len(active_test_support_directories),
            "Exactly one test-support source directory must be active.",
        )
        support_directory_layout_policy = {
            "coco-document": False,
            "coco-tools": True,
            **{directory: True for directory in active_test_support_directories},
        }
        self.assertEqual(
            set(support_directory_layout_policy),
            support_directories,
            "Every coco-support directory must explicitly declare whether it needs module-layout policy.",
        )
        for directory, expects_module_layout in support_directory_layout_policy.items():
            with self.subTest(support_directory=directory):
                self.assertEqual(
                    expects_module_layout,
                    module_layout_spec
                    in mapped_specs(f"coco-support/{directory}/__mapping_probe__"),
                )
        serialized_mappings = json.dumps(value["spec_path_mappings"])
        self.assertNotIn("coco-support/**", serialized_mappings)
        self.assertNotIn("update-readme-insights.yml", serialized_mappings)
        self.assertNotIn(".github/README.md", serialized_mappings)

        # The routing fixtures above can name planned paths. Keep physical
        # compatibility evidence in the canonical integration inputs instead.
        required_paths = {
            "coco-spring/coco-config/pom.xml",
            "coco-features/coco-feature-runtime/pom.xml",
            "coco-samples/coco-sample-basic/pom.xml",
            "coco-samples/coco-sample-full/pom.xml",
            ".github/scripts/verify_sample_feature_coordinates.py",
        }
        try:
            tracked = subprocess.run(
                [
                    "git",
                    "-C",
                    str(repository_root),
                    "ls-files",
                    "--error-unmatch",
                    *sorted(required_paths),
                ],
                check=False,
                capture_output=True,
                text=True,
            )
        except OSError as exc:
            self.fail(f"Unable to verify canonical integration inputs: {exc}")
        self.assertEqual(0, tracked.returncode, tracked.stderr)
        self.assertTrue(required_paths.issubset(set(tracked.stdout.splitlines())))

        workflow = (repository_root / ".github/workflows/reusable-tests.yml").read_text(
            encoding="utf-8"
        )
        for command in (
            "run: mvn -B -ntp install",
            "run: mvn -B -ntp -f coco-samples/coco-sample-basic/pom.xml verify",
            "run: mvn -B -ntp -f coco-samples/coco-sample-full/pom.xml verify",
            "python .github/scripts/verify_sample_feature_coordinates.py",
        ):
            with self.subTest(command=command):
                self.assertIn(command, workflow)

    def test_agent_open_pr_workflow_uses_protected_app_identity(self) -> None:
        workflow = (
            Path(__file__).resolve().parents[1] / "workflows/agent-open-pr.yml"
        ).read_text(encoding="utf-8")
        for value in (
            "workflow_dispatch:",
            "head_sha:",
            "github.ref == 'refs/heads/main'",
            "environment: coco-agent",
            "actions/create-github-app-token@bcd2ba49218906704ab6c1aa796996da409d3eb1 # v3.2.0",
            "client-id: ${{ vars.COCO_AGENT_APP_CLIENT_ID }}",
            "secrets.COCO_AGENT_APP_PRIVATE_KEY",
            "permission-contents: read",
            "permission-pull-requests: write",
            "vars.COCO_AGENT_APP_SLUG",
            "vars.COCO_AGENT_APP_LOGIN",
            "vars.COCO_AGENT_APP_BOT_ID",
            "GITHUB_REPOSITORY_ID",
            '"${GITHUB_SHA}" != "${main_sha}"',
            "^codex/[A-Za-z0-9][A-Za-z0-9._/-]*$",
            "'$value | @uri'",
            '"repos/${GITHUB_REPOSITORY}/compare/${main_sha}...${HEAD_SHA}"',
            '"${branch_sha}" != "${HEAD_SHA}"',
            "GH_TOKEN: ${{ steps.app-token.outputs.token }}",
            "READ_TOKEN: ${{ github.token }}",
            "Multiple pull requests match the requested branch.",
            "Pull request identity or branch binding is invalid.",
            'gh api --method POST "repos/${GITHUB_REPOSITORY}/pulls"',
            'gh api "repos/${GITHUB_REPOSITORY}/pulls/${pr_number}"',
        ):
            self.assertIn(value, workflow)
        self.assertNotIn("actions/checkout", workflow)
        self.assertNotIn("inputs.body", workflow)
        self.assertNotIn("git clone", workflow)
        self.assertNotIn("git fetch", workflow)
        self.assertNotIn("git checkout", workflow)
        self.assertNotIn("gh pr checkout", workflow)
        self.assertNotIn("eval ", workflow)
        self.assertNotIn("bash -c", workflow)
        self.assertNotIn("sh -c", workflow)
        self.assertNotIn(".github/scripts/", workflow)
        self.assertNotIn("\n  pull_request:", workflow)
        self.assertNotIn("pull_request_target:", workflow)
        self.assertNotIn("\n  push:", workflow)
        self.assertNotIn("\n  workflow_run:", workflow)
        self.assertNotIn("permission-contents: write", workflow)
        self.assertNotIn("permission-issues:", workflow)
        self.assertNotIn("permission-checks:", workflow)
        self.assertNotIn("permission-statuses:", workflow)

    def test_policy_routing_covers_migration_batches_within_budget(self) -> None:
        """Exercise collect_policy() only; this does not compile or run modules."""
        repository_root = Path(__file__).resolve().parents[2]
        config_path = repository_root / ".github/agent-review/config.json"
        value = review.load_config(config_path)
        module_layout_spec = "coco-support/coco-document/architecture/module-layout.md"
        base_policy = {
            "AGENTS.md",
            ".github/agent-review/policy.md",
            module_layout_spec,
        }
        i18n_specs = {
            "coco-support/coco-document/superpowers/specs/2026-07-04-coco-api-core-i18n-design.md",
            "coco-support/coco-document/superpowers/specs/2026-07-04-coco-common-i18n-design.md",
        }
        web_specs = {
            "coco-support/coco-document/superpowers/specs/2026-07-05-coco-web-response-wrap-design.md",
            "coco-support/coco-document/superpowers/specs/2026-07-10-coco-jdbc-replay-store.md",
            "coco-support/coco-document/superpowers/specs/2026-07-08-coco-web-server-framework-boundary.md",
        }
        audit_specs = {
            "coco-support/coco-document/superpowers/specs/2026-07-10-coco-default-audit-logging.md",
            "coco-support/coco-document/superpowers/specs/2026-07-10-coco-audit-feature-independence.md",
        }
        codegen_spec = "coco-support/coco-document/superpowers/specs/2026-07-10-coco-default-crud-codegen.md"
        batches = {
            "build": [
                "pom.xml",
                "coco-parent/pom.xml",
                "coco-build/coco-parent/pom.xml",
                "coco-maven-plugin/src/main/java/Example.java",
                "coco-build/coco-maven-plugin/src/main/java/Example.java",
            ],
            "foundation": [
                "pom.xml",
                "coco-api/pom.xml",
                "coco-foundation/coco-api/pom.xml",
                "coco-common/coco-common-i18n/pom.xml",
                "coco-foundation/coco-common/coco-common-i18n/pom.xml",
            ],
            "spring": [
                "pom.xml",
                "coco-config/pom.xml",
                "coco-spring/coco-config/pom.xml",
                "coco-spring-boot-autoconfigure/pom.xml",
                "coco-spring/coco-spring-boot-autoconfigure/pom.xml",
                "coco-spring-boot-starter/pom.xml",
                "coco-spring/coco-spring-boot-starter/pom.xml",
            ],
            "support": [
                "pom.xml",
                "coco-test/pom.xml",
                "coco-support/coco-test/pom.xml",
                "coco-support/coco-test-support/pom.xml",
                "coco-build/coco-compatibility/coco-test/pom.xml",
            ],
        }

        for name, changed_paths in batches.items():
            with self.subTest(batch=name):
                omissions: list[str] = []
                sources = review.collect_policy(
                    repository_root,
                    value,
                    changed_paths,
                    omissions,
                )
                self.assertEqual([], omissions)
                self.assertIn(
                    "coco-support/coco-document/architecture/module-layout.md",
                    {source["source"] for source in sources},
                )
                if name == "support":
                    self.assertLess(
                        sum(len(source["content"]) for source in sources),
                        review.normalized_limits(value)["policy_chars"],
                    )

        # These are deterministic routing inputs, including planned relocation
        # paths. They are not evidence that those paths exist or compile.
        spring_cutover_policy_batches = {
            "starter-and-core-features": [
                "coco-config/pom.xml",
                "coco-spring/coco-config/pom.xml",
                "coco-spring/coco-config/src/test/java/io/github/coco/config/CocoConfigFacadeFqcnCompileContract.java",
                "coco-build/coco-compatibility/coco-config/pom.xml",
                "coco-build/coco-compatibility/coco-config/src/test/java/io/github/coco/config/CocoConfigFacadeFqcnCompileContract.java",
                "coco-features/coco-feature-runtime/pom.xml",
                "coco-features/coco-feature-runtime/src/test/java/io/github/coco/feature/runtime/CocoFeatureRuntimeFacadeFqcnCompileContract.java",
                "coco-build/coco-compatibility/coco-feature-runtime/pom.xml",
                "coco-build/coco-compatibility/coco-feature-runtime/src/test/java/io/github/coco/feature/runtime/CocoFeatureRuntimeFacadeFqcnCompileContract.java",
                "coco-spring/coco-spring-boot-starter/pom.xml",
                "coco-spring/coco-spring-boot-starter/src/test/java/io/github/coco/spring/boot/CocoSpringDependencyCutoverTest.java",
                "coco-features/coco-feature-data-permission/pom.xml",
                "coco-features/coco-feature-mybatis-plus/pom.xml",
                "coco-features/coco-feature-openapi/pom.xml",
                "coco-features/coco-feature-security/pom.xml",
                "coco-features/coco-feature-tenant/pom.xml",
            ],
            "web": ["coco-features/coco-feature-web/pom.xml"],
            "audit": ["coco-features/coco-feature-audit/pom.xml"],
            "codegen": ["coco-features/coco-feature-codegen/pom.xml"],
        }
        module_entries = review.module_map(repository_root)
        modules_by_artifact = {
            entry["artifact_id"]: entry
            for entry in module_entries
            if entry["artifact_id"]
        }
        starter = modules_by_artifact["coco-spring-boot-starter"]
        concrete_feature_artifacts = {
            artifact_id
            for artifact_id in starter["coco_dependencies"]
            if artifact_id in modules_by_artifact
            and modules_by_artifact[artifact_id]["path"].startswith("coco-features/")
            and artifact_id != "coco-feature-runtime"
        }
        expected_consumer_poms = {
            starter["path"],
            *(
                modules_by_artifact[artifact_id]["path"]
                for artifact_id in concrete_feature_artifacts
            ),
        }
        scheduled_consumer_poms = [
            path
            for paths in spring_cutover_policy_batches.values()
            for path in paths
            if path in expected_consumer_poms
        ]
        self.assertEqual(expected_consumer_poms, set(scheduled_consumer_poms))
        self.assertEqual(len(expected_consumer_poms), len(scheduled_consumer_poms))
        expected_policy_sources = {
            "starter-and-core-features": base_policy | i18n_specs,
            "web": base_policy | web_specs,
            "audit": base_policy | audit_specs,
            "codegen": base_policy | {codegen_spec},
        }
        for name, changed_paths in spring_cutover_policy_batches.items():
            with self.subTest(spring_cutover_policy_batch=name):
                omissions = []
                sources = review.collect_policy(
                    repository_root,
                    value,
                    changed_paths,
                    omissions,
                )
                self.assertEqual([], omissions)
                self.assertEqual(
                    expected_policy_sources[name],
                    {source["source"] for source in sources},
                )
                self.assertLessEqual(
                    sum(len(source["content"]) for source in sources),
                    review.normalized_limits(value)["policy_chars"],
                )

    def test_repository_governance_policy_does_not_pull_module_layout(self) -> None:
        repository_root = Path(__file__).resolve().parents[2]
        config_path = repository_root / ".github/agent-review/config.json"
        value = review.load_config(config_path)
        jury_spec = "coco-support/coco-document/superpowers/specs/2026-07-10-multi-agent-review-jury.md"
        governance_spec = "coco-support/coco-document/superpowers/specs/2026-07-11-agent-governance-automation.md"
        omissions: list[str] = []

        sources = review.collect_policy(
            repository_root,
            value,
            [
                ".github/agent-review/config.json",
                ".github/labeler.yml",
                ".github/scripts/test_agent_review.py",
                ".github/scripts/test_verify_sample_feature_coordinates.py",
                ".github/scripts/verify_sample_feature_coordinates.py",
                ".github/workflows/reusable-static-analysis.yml",
                ".github/workflows/reusable-tests.yml",
            ],
            omissions,
        )

        self.assertEqual([], omissions)
        self.assertEqual(
            {
                "AGENTS.md",
                ".github/agent-review/policy.md",
                jury_spec,
                governance_spec,
            },
            {source["source"] for source in sources},
        )
        self.assertLess(
            sum(len(source["content"]) for source in sources),
            review.normalized_limits(value)["policy_chars"],
        )

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

    def test_deferred_bot_config_requires_exact_login_and_numeric_id(self) -> None:
        invalid_values = (
            {"login": "dependabot[bot]", "id": DEPENDABOT_BOT_ID},
            [{"login": "dependabot[bot]"}],
            [{"login": "dependabot", "id": DEPENDABOT_BOT_ID}],
            [{"login": "dependabot[bot]", "id": str(DEPENDABOT_BOT_ID)}],
            [{"login": "dependabot[bot]", "id": True}],
            [
                {"login": "dependabot[bot]", "id": DEPENDABOT_BOT_ID},
                {"login": "dependabot[bot]", "id": DEPENDABOT_BOT_ID},
            ],
            [
                {"login": "dependabot[bot]", "id": DEPENDABOT_BOT_ID},
                {"login": "dependabot[bot]", "id": DEPENDABOT_BOT_ID + 1},
            ],
            [
                {"login": "dependabot[bot]", "id": DEPENDABOT_BOT_ID},
                {"login": "dependabot-preview[bot]", "id": DEPENDABOT_BOT_ID},
            ],
        )
        with tempfile.TemporaryDirectory() as directory:
            config_path = Path(directory) / "config.json"
            for deferred_bot_authors in invalid_values:
                with self.subTest(deferred_bot_authors=deferred_bot_authors):
                    value = config()
                    value["deferred_bot_authors"] = deferred_bot_authors
                    review.write_json(config_path, value)
                    with self.assertRaises(review.ReviewError):
                        review.load_config(config_path)

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

    def test_github_transport_retries_only_transient_url_errors(self) -> None:
        transient = review.urllib.error.URLError(TimeoutError("timeout"))
        permanent = (
            review.urllib.error.URLError("invalid URL"),
            review.urllib.error.URLError(
                OSError(review.errno.EMFILE, "too many open files")
            ),
            review.urllib.error.URLError(
                review.ssl.SSLCertVerificationError("certificate verify failed")
            ),
        )
        self.assertTrue(review.retryable_url_error(transient))
        self.assertTrue(
            review.retryable_github_lookup_error(transient, retry_not_found=False)
        )
        for error in permanent:
            with self.subTest(reason=repr(error.reason)):
                self.assertFalse(review.retryable_url_error(error))
                self.assertFalse(
                    review.retryable_github_lookup_error(error, retry_not_found=False)
                )

        client = review.GitHubClient("test")
        with patch("urllib.request.urlopen", side_effect=permanent[0]):
            with self.assertRaises(review.ReviewError) as raised:
                client.get_json("repos/owner/repo")
        self.assertNotIsInstance(raised.exception, review.GitHubTransientError)
        with patch(
            "urllib.request.urlopen",
            side_effect=review.urllib.error.URLError(TimeoutError("timeout")),
        ):
            with self.assertRaises(review.GitHubTransientError):
                client.get_json("repos/owner/repo")

    def test_github_client_classifies_retryable_lookup_failures(self) -> None:
        client = review.GitHubClient("test", "https://api.example.invalid")

        for status in (408, 429, 500, 502, 503):
            with self.subTest(status=status):
                error = review.urllib.error.HTTPError(
                    "https://api.example.invalid/repos/owner/repo",
                    status,
                    "temporary",
                    None,
                    io.BytesIO(b'{"message":"temporary"}'),
                )
                try:
                    with patch.object(
                        review.urllib.request, "urlopen", side_effect=error
                    ):
                        with self.assertRaises(review.GitHubTransientError):
                            client.get_json("repos/owner/repo")
                finally:
                    error.close()

        with patch.object(
            review.urllib.request,
            "urlopen",
            side_effect=review.urllib.error.URLError(
                ConnectionResetError(review.errno.ECONNRESET, "connection reset")
            ),
        ):
            with self.assertRaises(review.GitHubTransientError):
                client.get_json("repos/owner/repo")

        limited = review.urllib.error.HTTPError(
            "https://api.example.invalid/repos/owner/repo",
            403,
            "rate limited",
            {"Retry-After": "1"},
            io.BytesIO(b'{"message":"rate limited"}'),
        )
        try:
            with patch.object(review.urllib.request, "urlopen", side_effect=limited):
                with self.assertRaises(review.GitHubTransientError):
                    client.get_json("repos/owner/repo")
        finally:
            limited.close()

        for status in (401, 403):
            with self.subTest(status=status):
                error = review.urllib.error.HTTPError(
                    "https://api.example.invalid/repos/owner/repo",
                    status,
                    "denied",
                    None,
                    io.BytesIO(b'{"message":"denied"}'),
                )
                try:
                    with patch.object(
                        review.urllib.request, "urlopen", side_effect=error
                    ):
                        with self.assertRaises(review.ReviewError) as raised:
                            client.get_json("repos/owner/repo")
                    self.assertNotIsInstance(
                        raised.exception, review.GitHubTransientError
                    )
                finally:
                    error.close()

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
                    "filename": "coco-support/coco-document/architecture/module-layout.md",
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
            self.assertIn(
                "coco-support/coco-document/architecture/module-layout.md", diff
            )
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
            "base": {
                "sha": BASE_SHA,
                "ref": "main",
                "repo": {"id": REPOSITORY_ID, "full_name": REPOSITORY},
            },
            "head": {
                "sha": HEAD_SHA,
                "repo": {"full_name": "patton174/coco-framework"},
            },
            "user": {"id": 42, "login": "patton174", "type": "User"},
        }

        class FakeClient:
            def __init__(self) -> None:
                self.paginated: list[tuple[str, int]] = []
                self.pull_reads = 0

            def get_json(self, path: str) -> dict:
                if path == "repos/patton174/coco-framework/pulls/1":
                    self.pull_reads += 1
                    if self.pull_reads == 2:
                        raise review.GitHubTransientError("HTTP 502")
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
                patch.object(review.time, "sleep") as sleeper,
                patch("builtins.print"),
                patch.dict("os.environ", {"GH_TOKEN": "token"}),
            ):
                result = review.command_prepare(
                    SimpleNamespace(
                        repository="patton174/coco-framework",
                        pr_number=1,
                        event_name="pull_request_target",
                        expected_head_sha="",
                        base_root=root,
                        config=root / "config.json",
                        context_output=root / "context.json",
                        metadata_output=root / "metadata.json",
                    )
                )

        self.assertEqual(0, result)
        self.assertEqual(3, client.pull_reads)
        sleeper.assert_called_once()
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

    def test_labeler_does_not_predeclare_missing_compatibility_modules(
        self,
    ) -> None:
        repository_root = Path(__file__).resolve().parents[2]
        labeler = (repository_root / ".github/labeler.yml").read_text(encoding="utf-8")

        compatibility_glob_roots = [
            value.removesuffix("/**")
            for value in re.findall(r'"([^"]+)"', labeler)
            if value.startswith("coco-build/coco-compatibility/")
            and value.endswith("/**")
        ]
        for relative_root in compatibility_glob_roots:
            with self.subTest(compatibility_glob=relative_root):
                self.assertTrue(
                    (repository_root / relative_root).is_dir(),
                    f"Labeler compatibility glob has no module directory: {relative_root}",
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

    @staticmethod
    def _release_workflow() -> str:
        return (
            Path(__file__).resolve().parents[1] / "workflows/release.yml"
        ).read_text(encoding="utf-8")

    @staticmethod
    def _release_tag_contract_sections(workflow: str) -> tuple[str, str, str, str, str]:
        tag = workflow.split("\n  tag:\n", 1)[1]
        preflight = tag.split(
            "\n      - name: Revalidate release target before privileged token mint\n",
            1,
        )[1].split("\n      - name: Create Release App installation token\n", 1)[0]
        token = tag.split("\n      - name: Create Release App installation token\n", 1)[
            1
        ].split("\n      - name: Bind Release App identity and repository\n", 1)[0]
        identity = tag.split(
            "\n      - name: Bind Release App identity and repository\n", 1
        )[1].split("\n      - name: Tag the successful release\n", 1)[0]
        write = tag.split("\n      - name: Tag the successful release\n", 1)[1]
        return tag, preflight, token, identity, write

    def _assert_release_app_contract(self, workflow: str) -> None:
        _, preflight, token, identity, write = self._release_tag_contract_sections(
            workflow
        )
        action_match = re.search(
            r"uses: actions/create-github-app-token@([0-9a-f]{40}) # v3[.]2[.]0",
            token,
        )
        self.assertIsNotNone(action_match)
        assert action_match is not None
        self.assertEqual(RELEASE_APP_ACTION_SHA, action_match.group(1))
        self.assertIn('EXPECTED_APP_ID}" != "4279686"', preflight)
        self.assertIn('EXPECTED_INSTALLATION_ID}" != "146080543"', preflight)
        self.assertIn("secrets.COCO_RELEASE_APP_PRIVATE_KEY", token)
        self.assertIn("permission-contents: write", token)
        self.assertIn("skip-token-revoke: false", token)
        self.assertIn('derived_login="${ACTUAL_APP_SLUG}[bot]"', identity)
        self.assertIn('"${actual_bot_id}" != "${EXPECTED_APP_BOT_ID}"', identity)
        self.assertIn('echo "authorized=true" >> "${GITHUB_OUTPUT}"', identity)
        self.assertIn("steps.release-app-identity.outputs.authorized == 'true'", write)
        normalized_write = " ".join(re.sub(r"\\\s+", " ", write).split())
        for endpoint in ("git/tags", "git/refs"):
            self.assertIn(
                'GH_TOKEN="${APP_TOKEN}" gh api --method POST '
                f'"repos/${{GITHUB_REPOSITORY}}/{endpoint}"',
                normalized_write,
            )

    def test_release_workflow_is_latest_main_only_and_least_privilege(self) -> None:
        workflow = self._release_workflow()
        workflow_header = workflow.split("\njobs:\n", 1)[0]
        publish, tag = workflow.split("\n  publish:\n", 1)[1].split("\n  tag:\n", 1)

        self.assertIn("  workflow_dispatch:\n", workflow)
        self.assertNotRegex(workflow, r"(?m)^  push:\s*$")
        self.assertIn("permissions:\n  contents: read\n", workflow)
        self.assertIn(
            "concurrency:\n"
            "  group: release-${{ github.repository_id }}\n"
            "  cancel-in-progress: false\n",
            workflow_header,
        )
        self.assertIn('"${GITHUB_REF}" != "refs/heads/main"', workflow)
        self.assertIn("git/ref/heads/main", workflow)
        self.assertIn('"${GITHUB_SHA}" != "${latest_main_sha}"', workflow)
        self.assertIn("needs: guard", workflow)
        self.assertIn("needs:\n      - test\n      - central-capacity\n", publish)
        self.assertNotIn("contents: write", publish)
        self.assertIn("environment: coco-spring", publish)
        self.assertIn('central_wait_until="PUBLISHED"', publish)
        self.assertNotIn('central_wait_until="VALIDATED"', publish)
        self.assertIn("persist-credentials: false", publish)
        self.assertIn("needs:\n      - test\n      - publish\n", tag)
        self.assertIn("environment: coco-spring", tag)
        self.assertIn("permissions:\n      contents: read\n", tag)
        self.assertNotRegex(tag, r"(?m)^\s+contents:\s+write\s*$")
        self.assertIn("GitHub exposes no tag-only App permission", tag)
        self.assertNotIn("git push origin", workflow)

    def test_release_workflow_pins_release_app_before_token_mint(self) -> None:
        workflow = self._release_workflow()
        tag, preflight, token, identity, _ = self._release_tag_contract_sections(
            workflow
        )
        self._assert_release_app_contract(workflow)

        step_names = (
            "Revalidate release target before privileged token mint",
            "Create Release App installation token",
            "Bind Release App identity and repository",
            "Tag the successful release",
        )
        positions = [tag.index(f"      - name: {name}") for name in step_names]
        self.assertEqual(sorted(positions), positions)
        for variable in (
            "COCO_RELEASE_APP_CLIENT_ID",
            "COCO_RELEASE_APP_ID",
            "COCO_RELEASE_APP_SLUG",
            "COCO_RELEASE_APP_LOGIN",
            "COCO_RELEASE_APP_BOT_ID",
            "COCO_RELEASE_APP_INSTALLATION_ID",
        ):
            self.assertIn(variable, preflight)
        self.assertIn("client-id: ${{ vars.COCO_RELEASE_APP_CLIENT_ID }}", token)
        self.assertIn(
            "ACTUAL_INSTALLATION_ID: "
            "${{ steps.release-app-token.outputs.installation-id }}",
            identity,
        )
        normalized_identity = " ".join(re.sub(r"\\\s+", " ", identity).split())
        self.assertIn(
            'GH_TOKEN="${APP_TOKEN}" gh api "users/${derived_login}"',
            normalized_identity,
        )
        self.assertIn(
            'GH_TOKEN="${APP_TOKEN}" gh api "repos/${GITHUB_REPOSITORY}"',
            normalized_identity,
        )

    def test_release_workflow_rebinds_before_explicit_app_tag_writes(self) -> None:
        workflow = self._release_workflow()
        _, _, _, _, write = self._release_tag_contract_sections(workflow)
        self._assert_release_app_contract(workflow)

        self.assertIn("require_current_main()", write)
        self.assertIn('GH_TOKEN="${READ_TOKEN}" gh api', write)
        self.assertIn("steps.release-app-identity.outputs.authorized == 'true'", write)
        self.assertIn('ref="refs/tags/${RELEASE_TAG}"', write)
        self.assertNotIn("COCO_RELEASE_APP_PRIVATE_KEY", write)
        for line in write.splitlines():
            if "gh api --method POST" in line:
                self.assertIn('GH_TOKEN="${APP_TOKEN}"', line)

    def test_release_workflow_tag_lookup_fails_closed_on_api_errors(self) -> None:
        workflow = self._release_workflow()
        _, preflight, _, _, write = self._release_tag_contract_sections(workflow)
        for step in (preflight, write):
            self.assertIn("git/matching-refs/tags/${RELEASE_TAG}", step)
            self.assertIn("jq --arg ref", step)
            self.assertIn('! "${tag_ref_count}" =~ ^[0-9]+$', step)
            self.assertNotIn("> /dev/null 2>&1", step)

    def test_release_workflow_contract_rejects_unsafe_mutations(self) -> None:
        workflow = self._release_workflow()
        mutations = {
            "unpinned action": workflow.replace(
                f"@{RELEASE_APP_ACTION_SHA}", "@v3.2.0", 1
            ),
            "missing identity gate": workflow.replace(
                "steps.release-app-identity.outputs.authorized == 'true'",
                "steps.release-target.outputs.validated == 'true'",
                1,
            ),
            "implicit write token": workflow.replace(
                'GH_TOKEN="${APP_TOKEN}" gh api --method POST',
                "gh api --method POST",
                1,
            ),
            "drifted installation pin": workflow.replace(
                'EXPECTED_INSTALLATION_ID}" != "146080543"',
                'EXPECTED_INSTALLATION_ID}" != "999"',
                1,
            ),
        }
        for name, mutated in mutations.items():
            with self.subTest(name=name):
                self.assertNotEqual(workflow, mutated)
                with self.assertRaises((AssertionError, IndexError)):
                    self._assert_release_app_contract(mutated)

    def test_agent_issue_gate_workflow_has_no_secret_path_and_shared_lock(self) -> None:
        workflow_root = Path(__file__).resolve().parents[1] / "workflows"
        direct_workflow = (workflow_root / "agent-review.yml").read_text(
            encoding="utf-8"
        )
        review_workflow = (workflow_root / "reusable-agent-review-jury.yml").read_text(
            encoding="utf-8"
        )
        deferred_workflow = (workflow_root / "agent-review-deferred.yml").read_text(
            encoding="utf-8"
        )
        self.assertFalse((workflow_root / "claude-review.yml").exists())
        self.assertTrue(direct_workflow.startswith("name: Agent Review Jury\n"))
        self.assertIn(
            'run-name: "Agent Review Jury / PR #${{ github.event.pull_request.number }} / ${{ github.event.pull_request.head.sha }}"',
            direct_workflow,
        )
        self.assertIn('"${review_script}" route', direct_workflow)
        self.assertEqual(
            2,
            direct_workflow.count(
                "uses: ./.github/workflows/reusable-agent-review-jury.yml"
            ),
        )
        direct_secret = direct_workflow.split("\n  direct-secret-review:\n", 1)[
            1
        ].split("\n  no-secret-review:\n", 1)[0]
        direct_no_secret = direct_workflow.split("\n  no-secret-review:\n", 1)[1]
        self.assertIn("secrets: inherit", direct_secret)
        self.assertNotIn("secrets: inherit", direct_no_secret)
        self.assertNotIn("${{ secrets.", direct_workflow)
        self.assertNotIn("COCO_AGENT_APP_PRIVATE_KEY", direct_workflow)
        self.assertNotIn("ANTHROPIC", direct_workflow)

        self.assertTrue(
            review_workflow.startswith("name: Reusable Agent Review Jury\n")
        )
        self.assertIn("  workflow_call:\n", review_workflow)
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

        self.assertIn("name: Deferred Agent Review Jury", deferred_workflow)
        self.assertIn(
            'run-name: "Deferred Agent Review Jury / source run #${{ github.event.workflow_run.id }}"',
            deferred_workflow,
        )
        self.assertIn("  workflow_run:\n", deferred_workflow)
        self.assertIn("workflows: [Agent Review Jury]", deferred_workflow)
        self.assertIn(
            "github.event.workflow_run.head_repository.id == fromJSON(github.repository_id)",
            deferred_workflow,
        )
        self.assertIn(
            "github.event.workflow_run.head_repository.full_name == github.repository",
            deferred_workflow,
        )
        self.assertIn("agent_review.py bind-deferred", deferred_workflow)
        self.assertIn(
            "COCO_AGENT_APP_BOT_ID: ${{ vars.COCO_AGENT_APP_BOT_ID }}",
            deferred_workflow,
        )
        self.assertIn(
            "COCO_AGENT_APP_LOGIN: ${{ vars.COCO_AGENT_APP_LOGIN }}",
            deferred_workflow,
        )
        self.assertIn("allow_deferred: true", deferred_workflow)
        self.assertIn(
            "source_run_id: ${{ github.event.workflow_run.id }}", deferred_workflow
        )
        self.assertIn("ref: ${{ github.sha }}", deferred_workflow)
        self.assertIn("secrets: inherit", deferred_workflow)
        self.assertNotIn("github.event.workflow_run.head_sha", deferred_workflow)
        self.assertNotIn("actions/download-artifact", deferred_workflow)
        self.assertNotIn("actions/cache", deferred_workflow)
        self.assertNotIn("refs/pull/", deferred_workflow)
        self.assertNotIn("/merge", deferred_workflow)

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

    def test_reusable_jury_keeps_all_model_jobs_and_least_privilege(self) -> None:
        workflow_root = Path(__file__).resolve().parents[1] / "workflows"
        core = (workflow_root / "reusable-agent-review-jury.yml").read_text(
            encoding="utf-8"
        )
        direct = (workflow_root / "agent-review.yml").read_text(encoding="utf-8")
        deferred = (workflow_root / "agent-review-deferred.yml").read_text(
            encoding="utf-8"
        )

        for job in (
            "  specialists:\n",
            "  verifiers:\n",
            "  chair:\n",
            "  trusted-publisher:\n",
            "  no-secret-publisher:\n",
        ):
            self.assertEqual(1, core.count(job), job)
        self.assertIn(
            "matrix:\n        include: ${{ fromJSON(needs.prepare.outputs.specialist-matrix) }}",
            core,
        )
        self.assertIn(
            "matrix:\n        include: ${{ fromJSON(needs.prepare.outputs.verifier-matrix) }}",
            core,
        )
        self.assertGreaterEqual(
            core.count("needs.prepare.outputs.trusted == 'true'"), 4
        )
        self.assertGreaterEqual(
            core.count("needs.prepare.outputs.ignored == 'false'"), 4
        )

        specialists = core.split("\n  specialists:\n", 1)[1].split(
            "\n  verifiers:\n", 1
        )[0]
        verifiers = core.split("\n  verifiers:\n", 1)[1].split("\n  chair:\n", 1)[0]
        chair = core.split("\n  chair:\n", 1)[1].split("\n  trusted-publisher:\n", 1)[0]
        for model_job in (specialists, verifiers, chair):
            self.assertNotIn("statuses: write", model_job)
        self.assertEqual(3, core.count("statuses: write"))

        reusable_call = "uses: ./.github/workflows/reusable-agent-review-jury.yml"
        self.assertEqual(2, direct.count(reusable_call))
        self.assertEqual(1, deferred.count(reusable_call))
        self.assertIn("allow_deferred: true", deferred)
        self.assertIn("event_name: workflow_run", deferred)
        self.assertNotIn("\n  specialists:\n", direct)
        self.assertNotIn("\n  specialists:\n", deferred)

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

    def test_classification_has_direct_deferred_and_no_secret_routes(self) -> None:
        base = {
            "head": {"repo": {"full_name": "patton174/coco-framework"}},
            "user": {"id": 42, "login": "patton174", "type": "User"},
        }
        self.assertEqual(
            review.PR_ROUTE_DIRECT,
            review.classify_pr_route(base, "patton174/coco-framework"),
        )
        fork = json.loads(json.dumps(base))
        fork["head"]["repo"]["full_name"] = "someone/fork"
        self.assertEqual(
            review.PR_ROUTE_NO_SECRET,
            review.classify_pr_route(fork, "patton174/coco-framework"),
        )
        for actor in (
            {"id": 42, "login": "patton174", "type": "Organization"},
            {"id": 42, "login": "patton174[bot]", "type": "User"},
            {"id": 42, "login": "patton174"},
            {"login": "patton174", "type": "User"},
            {"id": 42, "login": "", "type": "User"},
            {"id": -1, "login": "patton174", "type": "User"},
            {"id": 0, "login": "patton174", "type": "User"},
        ):
            with self.subTest(actor=actor):
                unknown = json.loads(json.dumps(base))
                unknown["user"] = actor
                self.assertEqual(
                    review.PR_ROUTE_NO_SECRET,
                    review.classify_pr_route(unknown, "patton174/coco-framework"),
                )
        deferred_bot_authors = (("dependabot[bot]", DEPENDABOT_BOT_ID),)
        dependabot = json.loads(json.dumps(base))
        dependabot["user"] = {
            "id": DEPENDABOT_BOT_ID,
            "login": "dependabot[bot]",
            "type": "Bot",
        }
        self.assertEqual(
            review.PR_ROUTE_NO_SECRET,
            review.classify_pr_route(dependabot, "patton174/coco-framework"),
        )
        self.assertEqual(
            review.PR_ROUTE_DEFERRED,
            review.classify_pr_route(
                dependabot,
                "patton174/coco-framework",
                deferred_bot_authors=deferred_bot_authors,
            ),
        )

        for actor in (
            {
                "id": DEPENDABOT_BOT_ID,
                "login": "dependabot-preview[bot]",
                "type": "Bot",
            },
            {
                "id": DEPENDABOT_BOT_ID + 1,
                "login": "dependabot[bot]",
                "type": "Bot",
            },
            {"id": 1, "login": "github-actions[bot]", "type": "Bot"},
        ):
            bot = json.loads(json.dumps(base))
            bot["user"] = actor
            self.assertEqual(
                review.PR_ROUTE_NO_SECRET,
                review.classify_pr_route(
                    bot,
                    "patton174/coco-framework",
                    deferred_bot_authors=deferred_bot_authors,
                ),
            )

        external_dependabot = json.loads(json.dumps(dependabot))
        external_dependabot["head"]["repo"]["full_name"] = "someone/fork"
        self.assertEqual(
            review.PR_ROUTE_NO_SECRET,
            review.classify_pr_route(
                external_dependabot,
                "patton174/coco-framework",
                deferred_bot_authors=deferred_bot_authors,
            ),
        )

        app = json.loads(json.dumps(base))
        app["user"] = {
            "id": APP_BOT_ID,
            "login": "coco-agent[bot]",
            "type": "Bot",
        }
        self.assertEqual(
            review.PR_ROUTE_NO_SECRET,
            review.classify_pr_route(app, "patton174/coco-framework"),
        )
        self.assertEqual(
            review.PR_ROUTE_DIRECT,
            review.classify_pr_route(
                app,
                "patton174/coco-framework",
                "coco-agent[bot]",
                APP_BOT_ID,
            ),
        )
        self.assertEqual(
            review.PR_ROUTE_DIRECT,
            review.classify_pr_route(
                app,
                "patton174/coco-framework",
                "coco-agent[bot]",
                APP_BOT_ID,
                deferred_bot_authors=(("coco-agent[bot]", APP_BOT_ID),),
            ),
        )
        self.assertEqual(
            review.PR_ROUTE_NO_SECRET,
            review.classify_pr_route(
                app,
                "patton174/coco-framework",
                "coco-agent[bot]",
                APP_BOT_ID + 1,
            ),
        )
        self.assertTrue(review.classify_pr(base, "patton174/coco-framework"))
        self.assertFalse(review.classify_pr(fork, "patton174/coco-framework"))
        self.assertFalse(review.classify_pr(dependabot, "patton174/coco-framework"))
        self.assertTrue(
            review.classify_pr(
                app,
                "patton174/coco-framework",
                "coco-agent[bot]",
                APP_BOT_ID,
            )
        )

    def test_resolve_pr_retries_and_writes_exact_protected_binding(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.attempts = 0

            def get_json(self, path: str) -> dict:
                if path != f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}":
                    raise AssertionError(f"Unexpected GET path: {path}")
                self.attempts += 1
                if self.attempts == 1:
                    raise review.GitHubTransientError("HTTP 502")
                return deferred_pull_request()

        client = FakeClient()
        with tempfile.TemporaryDirectory() as directory:
            output_path = Path(directory) / "binding.json"
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch.object(review.time, "sleep") as sleeper,
                patch("builtins.print"),
                patch.dict("os.environ", {"GH_TOKEN": "token"}, clear=True),
            ):
                result = review.command_resolve_pr(
                    SimpleNamespace(
                        repository=REPOSITORY,
                        repository_id=REPOSITORY_ID,
                        pr_number=DEFERRED_PR_NUMBER,
                        expected_base_sha=BASE_SHA,
                        expected_head_sha=HEAD_SHA,
                        output=output_path,
                    )
                )
            binding = review.read_json(output_path)

        self.assertEqual(0, result)
        self.assertEqual(2, client.attempts)
        sleeper.assert_called_once()
        self.assertEqual(DEFERRED_PR_NUMBER, binding["pr_number"])
        self.assertEqual(BASE_SHA, binding["base_sha"])
        self.assertEqual(HEAD_SHA, binding["head_sha"])
        self.assertEqual(REPOSITORY_ID, binding["repository_id"])

    def test_route_command_emits_structured_classification_context(self) -> None:
        pull_request = deferred_pull_request()

        class FakeClient:
            def __init__(self) -> None:
                self.attempts = 0

            def get_json(self, path: str) -> dict:
                if path == f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}":
                    self.attempts += 1
                    if self.attempts == 1:
                        raise review.urllib.error.URLError(
                            ConnectionResetError(
                                review.errno.ECONNRESET, "connection reset"
                            )
                        )
                    return pull_request
                raise AssertionError(f"Unexpected GET path: {path}")

        client = FakeClient()
        with tempfile.TemporaryDirectory() as directory:
            output_path = Path(directory) / "route.json"
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch.object(review, "load_config", return_value=deferred_config()),
                patch.object(review.time, "sleep") as sleeper,
                patch("builtins.print"),
                patch.dict("os.environ", {"GH_TOKEN": "token"}, clear=True),
            ):
                result = review.command_route(
                    SimpleNamespace(
                        repository=REPOSITORY,
                        repository_id=REPOSITORY_ID,
                        pr_number=DEFERRED_PR_NUMBER,
                        event_name="pull_request_target",
                        expected_head_sha=HEAD_SHA,
                        config=Path(directory) / "config.json",
                        output=output_path,
                    )
                )
            decision = review.read_json(output_path)

        self.assertEqual(0, result)
        self.assertEqual(2, client.attempts)
        sleeper.assert_called_once()
        self.assertEqual(review.PR_ROUTE_DEFERRED, decision["review_route"])
        self.assertEqual("same-repository-deferred-bot", decision["route_reason"])
        self.assertEqual("dependabot[bot]", decision["author_login"])
        self.assertEqual("Bot", decision["author_type"])
        self.assertEqual(DEPENDABOT_BOT_ID, decision["author_id"])
        self.assertEqual(REPOSITORY, decision["head_repository"])

    def test_classify_pr_compatibility_shim_matches_direct_route(self) -> None:
        cases = (
            (
                {
                    "head": {"repo": {"full_name": REPOSITORY}},
                    "user": {"id": 42, "login": "maintainer", "type": "User"},
                },
                "",
                0,
            ),
            (
                {
                    "head": {"repo": {"full_name": "someone/fork"}},
                    "user": {"id": 42, "login": "maintainer", "type": "User"},
                },
                "",
                0,
            ),
            (deferred_pull_request(), "", 0),
            (
                {
                    "head": {"repo": {"full_name": REPOSITORY}},
                    "user": {
                        "id": APP_BOT_ID,
                        "login": "coco-agent[bot]",
                        "type": "Bot",
                    },
                },
                "coco-agent[bot]",
                APP_BOT_ID,
            ),
        )

        for pull_request, trusted_app_login, trusted_app_bot_id in cases:
            with self.subTest(user=pull_request["user"]):
                self.assertEqual(
                    review.classify_pr_route(
                        pull_request,
                        REPOSITORY,
                        trusted_app_login,
                        trusted_app_bot_id,
                        deferred_bot_authors=(),
                    )
                    == review.PR_ROUTE_DIRECT,
                    review.classify_pr(
                        pull_request,
                        REPOSITORY,
                        trusted_app_login,
                        trusted_app_bot_id,
                    ),
                )

    def test_prepare_cli_preserves_legacy_optional_binding_arguments(self) -> None:
        args = review.parser().parse_args(
            [
                "prepare",
                "--repository",
                REPOSITORY,
                "--pr-number",
                "1",
                "--event-name",
                "pull_request_target",
                "--base-root",
                ".",
                "--config",
                "config.json",
                "--context-output",
                "context.json",
                "--metadata-output",
                "metadata.json",
            ]
        )

        self.assertEqual(0, args.repository_id)
        self.assertEqual("", args.expected_head_sha)
        self.assertFalse(args.allow_deferred)
        self.assertEqual(0, args.source_run_id)

    def test_prepare_rejects_incompatible_modes_without_api_calls(self) -> None:
        cases = (
            (
                "deferred review event",
                "pull_request_review",
                True,
                SOURCE_RUN_ID,
                "Deferred Agent review mode requires a workflow_run binding.",
            ),
            (
                "deferred run without source",
                "workflow_run",
                True,
                0,
                "Deferred Agent review mode requires a workflow_run binding.",
            ),
            (
                "direct workflow run",
                "workflow_run",
                False,
                0,
                "Direct Agent review event is invalid.",
            ),
            (
                "unknown direct event",
                "push",
                False,
                0,
                "Direct Agent review event is invalid.",
            ),
            (
                "direct event with source run",
                "pull_request_target",
                False,
                SOURCE_RUN_ID,
                "workflow_run review requires explicit deferred mode.",
            ),
        )

        for name, event_name, allow_deferred, source_run_id, message in cases:
            with self.subTest(name=name):
                with (
                    patch.object(review, "load_config") as config_loader,
                    patch.object(review, "GitHubClient") as client_constructor,
                ):
                    with self.assertRaisesRegex(review.ReviewError, message):
                        review.command_prepare(
                            SimpleNamespace(
                                repository=REPOSITORY,
                                repository_id=REPOSITORY_ID,
                                pr_number=DEFERRED_PR_NUMBER,
                                event_name=event_name,
                                expected_head_sha=HEAD_SHA,
                                allow_deferred=allow_deferred,
                                source_run_id=source_run_id,
                                base_root=Path("."),
                                config=Path("config.json"),
                                context_output=Path("context.json"),
                                metadata_output=Path("metadata.json"),
                            )
                        )
                config_loader.assert_not_called()
                client_constructor.assert_not_called()

    def test_deferred_workflow_binding_revalidates_exact_run_and_pull_request(
        self,
    ) -> None:
        client = FakeDeferredClient()
        binding = review.deferred_review_binding(
            client,
            REPOSITORY,
            REPOSITORY_ID,
            SOURCE_RUN_ID,
            deferred_config(),
            DEFERRED_PR_NUMBER,
            HEAD_SHA,
        )

        self.assertTrue(binding["eligible"])
        self.assertEqual(review.PR_ROUTE_DEFERRED, binding["review_route"])
        self.assertEqual(DEFERRED_PR_NUMBER, binding["pr_number"])
        self.assertEqual(HEAD_SHA, binding["head_sha"])
        self.assertEqual("dependabot[bot]", binding["author_login"])
        self.assertEqual("Bot", binding["author_type"])
        self.assertEqual(DEPENDABOT_BOT_ID, binding["author_id"])
        self.assertEqual(
            [
                f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}",
                f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}",
            ],
            client.get_paths,
        )

    def test_deferred_binding_retries_each_transient_lookup(self) -> None:
        class FlakyDeferredClient(FakeDeferredClient):
            def __init__(self) -> None:
                super().__init__()
                self.run_attempts = 0
                self.pull_attempts = 0
                self.errors: list[review.urllib.error.HTTPError] = []

            def http_error(self, path: str, status: int, reason: str):
                error = review.urllib.error.HTTPError(
                    path,
                    status,
                    reason,
                    None,
                    io.BytesIO(b'{"message":"temporary"}'),
                )
                self.errors.append(error)
                return error

            def get_json(self, path: str) -> dict:
                self.get_paths.append(path)
                if path == f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}":
                    self.run_attempts += 1
                    if self.run_attempts == 1:
                        raise review.urllib.error.URLError(
                            ConnectionResetError(
                                review.errno.ECONNRESET, "connection reset"
                            )
                        )
                    if self.run_attempts == 2:
                        raise self.http_error(
                            "https://api.example.invalid/actions/runs/1",
                            502,
                            "temporary",
                        )
                    return self.run
                if path == f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}":
                    self.pull_attempts += 1
                    if self.pull_attempts == 1:
                        raise self.http_error(
                            "https://api.example.invalid/pulls/1",
                            404,
                            "not found",
                        )
                    return self.pull_request
                raise AssertionError(f"Unexpected GET path: {path}")

        client = FlakyDeferredClient()
        try:
            with (
                patch.object(review.time, "sleep") as sleeper,
                patch("builtins.print"),
            ):
                binding = review.deferred_review_binding(
                    client,
                    REPOSITORY,
                    REPOSITORY_ID,
                    SOURCE_RUN_ID,
                    deferred_config(),
                    DEFERRED_PR_NUMBER,
                    HEAD_SHA,
                )
        finally:
            for error in client.errors:
                error.close()

        self.assertTrue(binding["eligible"])
        self.assertEqual(3, client.run_attempts)
        self.assertEqual(2, client.pull_attempts)
        self.assertEqual(3, sleeper.call_count)

    def test_deferred_binding_fails_closed_after_bounded_retries(self) -> None:
        class AlwaysTransientClient:
            def __init__(self) -> None:
                self.attempts = 0

            def get_json(self, path: str) -> dict:
                del path
                self.attempts += 1
                raise review.GitHubTransientError("HTTP 502")

        client = AlwaysTransientClient()
        with (
            patch.object(review.time, "sleep") as sleeper,
            patch("builtins.print"),
        ):
            with self.assertRaisesRegex(review.ReviewError, "failed after 4 attempts"):
                review.deferred_review_candidate(
                    client,
                    REPOSITORY,
                    REPOSITORY_ID,
                    SOURCE_RUN_ID,
                    deferred_config(),
                )

        self.assertEqual(4, client.attempts)
        self.assertEqual(3, sleeper.call_count)

    def test_deferred_binding_does_not_retry_invalid_payloads(self) -> None:
        run = deferred_workflow_run()
        run["name"] = "Other Workflow"
        client = FakeDeferredClient(run=run)

        with patch.object(review.time, "sleep") as sleeper:
            with self.assertRaisesRegex(review.ReviewError, "binding is invalid"):
                review.deferred_review_candidate(
                    client,
                    REPOSITORY,
                    REPOSITORY_ID,
                    SOURCE_RUN_ID,
                    deferred_config(),
                )

        self.assertEqual(
            [f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}"], client.get_paths
        )
        sleeper.assert_not_called()

    def test_deferred_workflow_binding_rejects_forged_or_stale_inputs(self) -> None:
        cases: list[tuple[str, dict, dict, list[dict]]] = []

        def add_case(
            name: str,
            *,
            run_change: tuple[str, object] | None = None,
            pr_path: tuple[str, str, object] | None = None,
            associated: list[dict] | None = None,
        ) -> None:
            run = json.loads(json.dumps(deferred_workflow_run()))
            pull_request = json.loads(json.dumps(deferred_pull_request()))
            if run_change is not None:
                run[run_change[0]] = run_change[1]
            if pr_path is not None:
                parent, key, value = pr_path
                pull_request[parent][key] = value
            cases.append(
                (
                    name,
                    run,
                    pull_request,
                    associated
                    if associated is not None
                    else [{"number": DEFERRED_PR_NUMBER}],
                )
            )

        add_case("wrong run id", run_change=("id", SOURCE_RUN_ID + 1))
        add_case("wrong workflow", run_change=("name", "Other Workflow"))
        add_case(
            "wrong workflow path",
            run_change=("path", ".github/workflows/reusable-agent-review-jury.yml"),
        )
        add_case("wrong event", run_change=("event", "pull_request_review"))
        add_case("failed run", run_change=("conclusion", "failure"))
        add_case(
            "wrong run repository id",
            run_change=(
                "repository",
                {"id": REPOSITORY_ID + 1, "full_name": REPOSITORY},
            ),
        )
        add_case(
            "wrong run repository name",
            run_change=(
                "repository",
                {"id": REPOSITORY_ID, "full_name": "someone/coco-framework"},
            ),
        )
        add_case(
            "wrong source head repository id",
            run_change=(
                "head_repository",
                {"id": REPOSITORY_ID + 1, "full_name": REPOSITORY},
            ),
        )
        add_case(
            "wrong source head repository name",
            run_change=(
                "head_repository",
                {"id": REPOSITORY_ID, "full_name": "someone/coco-framework"},
            ),
        )
        add_case(
            "stale title head",
            run_change=(
                "display_title",
                f"Agent Review Jury / PR #{DEFERRED_PR_NUMBER} / {'c' * 40}",
            ),
        )
        add_case("run head SHA drift", run_change=("head_sha", "c" * 40))
        add_case(
            "run head branch drift",
            run_change=("head_branch", "dependabot/maven/example-1.0.2"),
        )
        add_case("missing association", associated=[])
        add_case(
            "multiple associations",
            associated=[
                {"number": DEFERRED_PR_NUMBER},
                {"number": DEFERRED_PR_NUMBER + 1},
            ],
        )
        add_case(
            "wrong association",
            associated=[{"number": DEFERRED_PR_NUMBER + 1}],
        )
        add_case("stale current head", pr_path=("head", "sha", "c" * 40))
        add_case("wrong base", pr_path=("base", "ref", "release"))
        add_case(
            "wrong pull request head repository id",
            pr_path=(
                "head",
                "repo",
                {"id": REPOSITORY_ID + 1, "full_name": REPOSITORY},
            ),
        )
        add_case(
            "wrong pull request head repository name",
            pr_path=(
                "head",
                "repo",
                {"id": REPOSITORY_ID, "full_name": "someone/coco-framework"},
            ),
        )

        for field, value in (
            ("login", "dependabot-preview[bot]"),
            ("id", DEPENDABOT_BOT_ID + 1),
            ("type", "User"),
        ):
            run = json.loads(json.dumps(deferred_workflow_run()))
            pull_request = json.loads(json.dumps(deferred_pull_request()))
            pull_request["user"][field] = value
            cases.append(
                (
                    f"wrong author {field}",
                    run,
                    pull_request,
                    [{"number": DEFERRED_PR_NUMBER}],
                )
            )

        for name, run, pull_request, associated in cases:
            with self.subTest(name=name):
                client = FakeDeferredClient(
                    run=run,
                    pull_request=pull_request,
                    associated=associated,
                )
                with self.assertRaises(review.ReviewError):
                    review.deferred_review_binding(
                        client,
                        REPOSITORY,
                        REPOSITORY_ID,
                        SOURCE_RUN_ID,
                        deferred_config(),
                        DEFERRED_PR_NUMBER,
                        HEAD_SHA,
                    )

    def test_deferred_candidate_skips_non_pinned_authors(self) -> None:
        for user, expected_route in (
            (
                {"id": 12, "login": "maintainer", "type": "User"},
                review.PR_ROUTE_DIRECT,
            ),
            (
                {"id": 13, "login": "renovate[bot]", "type": "Bot"},
                review.PR_ROUTE_NO_SECRET,
            ),
        ):
            with self.subTest(user=user):
                pull_request = deferred_pull_request()
                pull_request["user"] = user
                candidate = review.deferred_review_candidate(
                    FakeDeferredClient(pull_request=pull_request),
                    REPOSITORY,
                    REPOSITORY_ID,
                    SOURCE_RUN_ID,
                    deferred_config(),
                )
                self.assertFalse(candidate["eligible"])
                self.assertEqual(expected_route, candidate["review_route"])

        fork = deferred_pull_request()
        fork["head"]["repo"] = {"id": 7, "full_name": "someone/coco-framework"}
        with self.assertRaises(review.ReviewError):
            review.deferred_review_candidate(
                FakeDeferredClient(pull_request=fork),
                REPOSITORY,
                REPOSITORY_ID,
                SOURCE_RUN_ID,
                deferred_config(),
            )

    def test_deferred_candidate_prioritizes_trusted_app_identity(self) -> None:
        configured = deferred_config()
        configured["deferred_bot_authors"].append(
            {"login": "coco-agent[bot]", "id": APP_BOT_ID}
        )
        app_pull_request = deferred_pull_request()
        app_pull_request["user"] = {
            "id": APP_BOT_ID,
            "login": "coco-agent[bot]",
            "type": "Bot",
        }

        with patch.dict(
            "os.environ",
            {
                "COCO_AGENT_APP_LOGIN": "coco-agent[bot]",
                "COCO_AGENT_APP_BOT_ID": str(APP_BOT_ID),
            },
            clear=True,
        ):
            app_candidate = review.deferred_review_candidate(
                FakeDeferredClient(pull_request=app_pull_request),
                REPOSITORY,
                REPOSITORY_ID,
                SOURCE_RUN_ID,
                configured,
            )
            dependabot_candidate = review.deferred_review_candidate(
                FakeDeferredClient(),
                REPOSITORY,
                REPOSITORY_ID,
                SOURCE_RUN_ID,
                configured,
            )

        self.assertFalse(app_candidate["eligible"])
        self.assertEqual(review.PR_ROUTE_DIRECT, app_candidate["review_route"])
        self.assertTrue(dependabot_candidate["eligible"])
        self.assertEqual(review.PR_ROUTE_DEFERRED, dependabot_candidate["review_route"])

    def test_bind_deferred_emits_ineligible_result_for_clean_skip(self) -> None:
        pull_request = deferred_pull_request()
        pull_request["user"] = {
            "id": 42,
            "login": "patton174",
            "type": "User",
        }
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "binding.json"
            with (
                patch.object(
                    review,
                    "GitHubClient",
                    return_value=FakeDeferredClient(pull_request=pull_request),
                ),
                patch.object(review, "load_config", return_value=deferred_config()),
                patch("builtins.print"),
            ):
                result = review.command_bind_deferred(
                    SimpleNamespace(
                        repository=REPOSITORY,
                        repository_id=REPOSITORY_ID,
                        run_id=SOURCE_RUN_ID,
                        config=Path(directory) / "config.json",
                        output=output,
                    )
                )

            binding = review.read_json(output)

        self.assertEqual(0, result)
        self.assertFalse(binding["eligible"])
        self.assertEqual(review.PR_ROUTE_DIRECT, binding["review_route"])

    def test_prepare_enables_full_jury_only_for_bound_deferred_run(self) -> None:
        class FakeClient(FakeDeferredClient):
            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if path.endswith("/files"):
                    return [
                        {
                            "filename": "pom.xml",
                            "status": "modified",
                            "additions": 1,
                            "deletions": 1,
                            "changes": 2,
                            "patch": "@@ -1 +1 @@\n-old\n+new",
                        }
                    ]
                if path.endswith("/commits"):
                    return []
                return super().paginate(path, limit)

        context = bound_context()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            context_output = root / "context.json"
            metadata_output = root / "metadata.json"
            with (
                patch.object(review, "GitHubClient", return_value=FakeClient()),
                patch.object(review, "load_config", return_value=deferred_config()),
                patch.object(review, "pull_request_diff", return_value="+change"),
                patch.object(review, "build_context", return_value=context) as builder,
                patch.object(
                    review,
                    "current_maintainer_approval",
                    return_value=(False, []),
                ) as approval,
                patch("builtins.print"),
                patch.dict("os.environ", {"GH_TOKEN": "token"}, clear=True),
            ):
                result = review.command_prepare(
                    SimpleNamespace(
                        repository=REPOSITORY,
                        repository_id=REPOSITORY_ID,
                        pr_number=DEFERRED_PR_NUMBER,
                        event_name="workflow_run",
                        expected_head_sha=HEAD_SHA,
                        allow_deferred=True,
                        source_run_id=SOURCE_RUN_ID,
                        base_root=root,
                        config=root / "config.json",
                        context_output=context_output,
                        metadata_output=metadata_output,
                    )
                )
            metadata = review.read_json(metadata_output)

        self.assertEqual(0, result)
        self.assertEqual(review.PR_ROUTE_DEFERRED, metadata["review_route"])
        self.assertTrue(metadata["trusted"])
        self.assertTrue(metadata["deferred"])
        self.assertFalse(metadata["ignored"])
        self.assertEqual(SOURCE_RUN_ID, metadata["source_run_id"])
        self.assertEqual(REPOSITORY_ID, metadata["repository_id"])
        builder.assert_called_once()
        approval.assert_not_called()

    def test_prepare_defers_dependabot_without_context_or_approval(self) -> None:
        configured = config()
        configured["deferred_bot_authors"] = [
            {"login": "dependabot[bot]", "id": DEPENDABOT_BOT_ID}
        ]
        pull_request = {
            "number": 1,
            "state": "open",
            "title": "build(deps): update dependency",
            "body": "",
            "changed_files": 1,
            "base": {
                "ref": "main",
                "sha": BASE_SHA,
                "repo": {"id": REPOSITORY_ID, "full_name": REPOSITORY},
            },
            "head": {
                "sha": HEAD_SHA,
                "repo": {"full_name": "patton174/coco-framework"},
            },
            "user": {
                "id": DEPENDABOT_BOT_ID,
                "login": "dependabot[bot]",
                "type": "Bot",
            },
        }

        class FakeClient:
            def __init__(self) -> None:
                self.attempts = 0

            def get_json(self, path: str) -> dict:
                if path == "repos/patton174/coco-framework/pulls/1":
                    self.attempts += 1
                    if self.attempts == 1:
                        raise review.GitHubTransientError("HTTP 502")
                    return pull_request
                raise AssertionError(f"Unexpected GET path: {path}")

            @staticmethod
            def paginate(path: str, limit: int = 1000) -> list[dict]:
                del limit
                if path.endswith("/files"):
                    return [
                        {
                            "filename": "pom.xml",
                            "status": "modified",
                            "additions": 1,
                            "deletions": 1,
                            "changes": 2,
                            "patch": "@@ -1 +1 @@\n-old\n+new",
                        }
                    ]
                if path.endswith("/commits"):
                    return []
                raise AssertionError(f"Unexpected paginated path: {path}")

        context = bound_context()
        client = FakeClient()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            context_output = root / "context.json"
            metadata_output = root / "metadata.json"
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch.object(review, "load_config", return_value=configured),
                patch.object(review, "pull_request_diff", return_value="+change"),
                patch.object(review, "build_context", return_value=context) as builder,
                patch.object(
                    review,
                    "current_maintainer_approval",
                    return_value=(False, []),
                ) as approval,
                patch.object(review.time, "sleep") as sleeper,
                patch("builtins.print"),
                patch.dict("os.environ", {"GH_TOKEN": "token"}, clear=True),
            ):
                result = review.command_prepare(
                    SimpleNamespace(
                        repository="patton174/coco-framework",
                        repository_id=REPOSITORY_ID,
                        pr_number=1,
                        event_name="pull_request_target",
                        expected_head_sha=HEAD_SHA,
                        base_root=root,
                        config=root / "config.json",
                        context_output=context_output,
                        metadata_output=metadata_output,
                    )
                )
            metadata = review.read_json(metadata_output)

        self.assertEqual(0, result)
        self.assertEqual(2, client.attempts)
        sleeper.assert_called_once()
        self.assertEqual(review.PR_ROUTE_DEFERRED, metadata["review_route"])
        self.assertFalse(metadata["trusted"])
        self.assertTrue(metadata["deferred"])
        self.assertTrue(metadata["ignored"])
        self.assertFalse(metadata["maintainer_approved"])
        builder.assert_not_called()
        approval.assert_not_called()

    def test_dependabot_review_event_cannot_overwrite_deferred_gate(self) -> None:
        pull_request = deferred_pull_request()

        class FakeClient:
            @staticmethod
            def get_json(path: str) -> dict:
                if path == f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}":
                    return pull_request
                raise AssertionError(f"Unexpected GET path: {path}")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata_output = root / "metadata.json"
            with (
                patch.object(review, "GitHubClient", return_value=FakeClient()),
                patch.object(review, "load_config", return_value=deferred_config()),
                patch.object(
                    review,
                    "current_maintainer_approval",
                    side_effect=AssertionError("approval must not be read"),
                ),
                patch("builtins.print"),
                patch.dict("os.environ", {"GH_TOKEN": "token"}, clear=True),
            ):
                review.command_prepare(
                    SimpleNamespace(
                        repository=REPOSITORY,
                        repository_id=REPOSITORY_ID,
                        pr_number=DEFERRED_PR_NUMBER,
                        event_name="pull_request_review",
                        expected_head_sha=HEAD_SHA,
                        allow_deferred=False,
                        source_run_id=0,
                        base_root=root,
                        config=root / "config.json",
                        context_output=root / "context.json",
                        metadata_output=metadata_output,
                    )
                )
            metadata = review.read_json(metadata_output)
            self.assertTrue(metadata["deferred"])
            self.assertTrue(metadata["ignored"])

            with (
                patch.object(
                    review,
                    "GitHubClient",
                    side_effect=AssertionError("ignored events must not publish"),
                ),
                patch("builtins.print"),
            ):
                self.assertEqual(
                    0,
                    review.command_mark_pending(
                        SimpleNamespace(
                            metadata=metadata_output,
                            run_url="https://github.example/runs/review",
                        )
                    ),
                )
                self.assertEqual(
                    0,
                    review.command_mark_failed(
                        SimpleNamespace(
                            metadata=metadata_output,
                            run_url="https://github.example/runs/review",
                        )
                    ),
                )
                self.assertEqual(
                    0,
                    review.command_publish(
                        SimpleNamespace(
                            metadata=metadata_output,
                            run_url="https://github.example/runs/review",
                        )
                    ),
                )

    def test_deferred_no_secret_route_is_ignored_and_cannot_publish_success(
        self,
    ) -> None:
        workflow_root = Path(__file__).resolve().parents[1] / "workflows"
        router = (workflow_root / "agent-review.yml").read_text(encoding="utf-8")
        reusable = (workflow_root / "reusable-agent-review-jury.yml").read_text(
            encoding="utf-8"
        )
        no_secret_call = router.split("\n  no-secret-review:\n", 1)[1]
        self.assertIn(
            "needs.route.outputs.review-route == 'deferred-pinned-bot'",
            no_secret_call,
        )
        self.assertNotIn("secrets: inherit", no_secret_call)
        self.assertIn("deferred: ${{ steps.metadata.outputs.deferred }}", reusable)
        self.assertIn(
            "output.write(f\"deferred={str(bool(metadata.get('deferred'))).lower()}\\n\")",
            reusable,
        )
        no_secret_publisher = reusable.split("\n  no-secret-publisher:\n", 1)[1]
        self.assertIn("needs.prepare.outputs.deferred == 'false'", no_secret_publisher)
        self.assertIn("needs.prepare.outputs.ignored == 'false'", no_secret_publisher)

        for event_name in ("pull_request_target", "pull_request_review"):
            with self.subTest(event_name=event_name):
                route_state = review.prepare_direct_route_state(
                    event_name, 0, review.PR_ROUTE_DEFERRED
                )
                self.assertTrue(route_state["deferred"])
                self.assertTrue(route_state["ignored"])
                with tempfile.TemporaryDirectory() as directory:
                    metadata_path = Path(directory) / "metadata.json"
                    review.write_json(
                        metadata_path,
                        {
                            "repository": REPOSITORY,
                            "pr_number": DEFERRED_PR_NUMBER,
                            "base_sha": BASE_SHA,
                            "head_sha": HEAD_SHA,
                            "review_route": review.PR_ROUTE_DEFERRED,
                            **route_state,
                        },
                    )
                    with (
                        patch.object(
                            review,
                            "GitHubClient",
                            side_effect=AssertionError(
                                "ignored metadata must not publish"
                            ),
                        ),
                        patch("builtins.print") as output,
                    ):
                        result = review.command_publish(
                            SimpleNamespace(
                                metadata=metadata_path,
                                run_url="https://github.example/runs/review",
                            )
                        )

                self.assertEqual(0, result)
                output.assert_called_once()
                publication = json.loads(output.call_args.args[0])
                self.assertEqual({"state": "ignored"}, publication)
                self.assertNotEqual("success", publication["state"])

    def test_mark_pending_records_the_current_run_owner(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {}

        metadata = {
            "repository": REPOSITORY,
            "head_sha": HEAD_SHA,
            "ignored": False,
            "run_id": "42",
            "run_attempt": "3",
        }
        with tempfile.TemporaryDirectory() as directory:
            metadata_path = Path(directory) / "metadata.json"
            review.write_json(metadata_path, metadata)
            client = FakeClient()
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch.dict("os.environ", {"GH_TOKEN": "token"}, clear=True),
            ):
                result = review.command_mark_pending(
                    SimpleNamespace(
                        metadata=metadata_path,
                        run_url="https://github.example/runs/42",
                    )
                )

        self.assertEqual(0, result)
        self.assertEqual(2, len(client.sent))
        method, path, payload = client.sent[0]
        self.assertEqual("POST", method)
        self.assertEqual(f"repos/{REPOSITORY}/statuses/{HEAD_SHA}", path)
        self.assertEqual("pending", payload["state"])
        self.assertEqual(review.OWNERSHIP_STATUS_CONTEXT, payload["context"])
        self.assertEqual("Agent jury run 42:3 in progress", payload["description"])
        self.assertEqual(review.STATUS_CONTEXT, client.sent[1][2]["context"])

    def test_owned_mark_failed_does_not_overwrite_a_newer_run(self) -> None:
        class FakeClient:
            @staticmethod
            def get_json(path: str) -> object:
                if path == f"repos/{REPOSITORY}/commits/{HEAD_SHA}/status":
                    return combined_ownership_status(43)
                raise AssertionError(f"Unexpected GET path: {path}")

            @staticmethod
            def send_json(method: str, path: str, payload: dict) -> dict:
                del method, path, payload
                raise AssertionError("A stale run must not publish a failure status")

        metadata = {
            "repository": REPOSITORY,
            "head_sha": HEAD_SHA,
            "ignored": False,
            "run_id": "42",
            "run_attempt": "1",
        }
        with tempfile.TemporaryDirectory() as directory:
            metadata_path = Path(directory) / "metadata.json"
            review.write_json(metadata_path, metadata)
            with (
                patch.object(review, "GitHubClient", return_value=FakeClient()),
                patch("builtins.print") as output,
            ):
                result = review.command_mark_failed(
                    SimpleNamespace(
                        metadata=metadata_path,
                        run_url="https://github.example/runs/42",
                        require_run_ownership=True,
                    )
                )

        self.assertEqual(0, result)
        self.assertEqual({"state": "stale"}, json.loads(output.call_args.args[0]))

    def test_publisher_admission_accepts_exact_current_trusted_run(self) -> None:
        class FakeClient:
            @staticmethod
            def get_json(path: str) -> object:
                if path == f"repos/{REPOSITORY}/pulls/1":
                    return {
                        "state": "open",
                        "head": {"sha": HEAD_SHA},
                        "base": {"sha": BASE_SHA, "ref": "main"},
                    }
                if path == f"repos/{REPOSITORY}/commits/{HEAD_SHA}/status":
                    return combined_ownership_status(42, 2)
                raise AssertionError(f"Unexpected GET path: {path}")

        metadata = {
            "repository": REPOSITORY,
            "pr_number": 1,
            "base_sha": BASE_SHA,
            "head_sha": HEAD_SHA,
            "trusted": True,
            "ignored": False,
            "run_id": "42",
            "run_attempt": "2",
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata_path = root / "metadata.json"
            output_path = root / "admission.json"
            review.write_json(metadata_path, metadata)
            with (
                patch.object(review, "GitHubClient", return_value=FakeClient()),
                patch("builtins.print"),
                patch.dict("os.environ", {"GH_TOKEN": "token"}, clear=True),
            ):
                result = review.command_admit_publisher(
                    SimpleNamespace(metadata=metadata_path, output=output_path)
                )
            admission = review.read_json(output_path)

        self.assertEqual(0, result)
        self.assertTrue(admission["admitted"])
        self.assertEqual("current-run-admitted", admission["reason"])

    def test_publisher_admission_rejects_stale_head_and_newer_run(self) -> None:
        metadata = {
            "repository": REPOSITORY,
            "pr_number": 1,
            "base_sha": BASE_SHA,
            "head_sha": HEAD_SHA,
            "trusted": True,
            "ignored": False,
            "run_id": "42",
            "run_attempt": "1",
        }

        for case, current_head, statuses, expected_reason in (
            (
                "head-changed",
                "c" * 40,
                None,
                "pull-request-binding-changed",
            ),
            (
                "newer-run",
                HEAD_SHA,
                combined_ownership_status(43),
                "newer-run-owns-publication",
            ),
        ):
            with self.subTest(case=case):

                class FakeClient:
                    @staticmethod
                    def get_json(path: str) -> object:
                        if path == f"repos/{REPOSITORY}/pulls/1":
                            return {
                                "state": "open",
                                "head": {"sha": current_head},
                                "base": {"sha": BASE_SHA, "ref": "main"},
                            }
                        if (
                            path == f"repos/{REPOSITORY}/commits/{HEAD_SHA}/status"
                            and statuses is not None
                        ):
                            return statuses
                        raise AssertionError(f"Unexpected GET path: {path}")

                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    metadata_path = root / "metadata.json"
                    output_path = root / "admission.json"
                    review.write_json(metadata_path, metadata)
                    with (
                        patch.object(review, "GitHubClient", return_value=FakeClient()),
                        patch("builtins.print"),
                    ):
                        result = review.command_admit_publisher(
                            SimpleNamespace(metadata=metadata_path, output=output_path)
                        )
                    admission = review.read_json(output_path)

                self.assertEqual(0, result)
                self.assertFalse(admission["admitted"])
                self.assertEqual(expected_reason, admission["reason"])

    def test_publisher_admission_retries_transient_api_failures(self) -> None:
        class RecoveringClient:
            def __init__(self) -> None:
                self.pull_attempts = 0

            def get_json(self, path: str) -> object:
                if path == f"repos/{REPOSITORY}/pulls/1":
                    self.pull_attempts += 1
                    if self.pull_attempts == 1:
                        raise review.GitHubTransientError("temporary")
                    return {
                        "state": "open",
                        "head": {"sha": HEAD_SHA},
                        "base": {"sha": BASE_SHA, "ref": "main"},
                    }
                if path == f"repos/{REPOSITORY}/commits/{HEAD_SHA}/status":
                    return combined_ownership_status(42)
                raise AssertionError(f"Unexpected GET path: {path}")

        metadata = {
            "repository": REPOSITORY,
            "pr_number": 1,
            "base_sha": BASE_SHA,
            "head_sha": HEAD_SHA,
            "trusted": True,
            "ignored": False,
            "run_id": "42",
            "run_attempt": "1",
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata_path = root / "metadata.json"
            output_path = root / "admission.json"
            review.write_json(metadata_path, metadata)
            client = RecoveringClient()
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch.object(review.time, "sleep") as sleeper,
                patch("builtins.print"),
            ):
                result = review.command_admit_publisher(
                    SimpleNamespace(metadata=metadata_path, output=output_path)
                )
            admission = review.read_json(output_path)

        self.assertEqual(0, result)
        self.assertEqual(2, client.pull_attempts)
        sleeper.assert_called_once()
        self.assertTrue(admission["admitted"])

    def test_publisher_admission_fails_closed_after_retry_exhaustion(self) -> None:
        class FailingClient:
            def __init__(self) -> None:
                self.attempts = 0

            def get_json(self, path: str) -> object:
                if path != f"repos/{REPOSITORY}/pulls/1":
                    raise AssertionError(f"Unexpected GET path: {path}")
                self.attempts += 1
                raise review.GitHubTransientError("temporary")

        metadata = {
            "repository": REPOSITORY,
            "pr_number": 1,
            "base_sha": BASE_SHA,
            "head_sha": HEAD_SHA,
            "trusted": True,
            "ignored": False,
            "run_id": "42",
            "run_attempt": "1",
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata_path = root / "metadata.json"
            output_path = root / "admission.json"
            review.write_json(metadata_path, metadata)
            client = FailingClient()
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch.object(review.time, "sleep") as sleeper,
                patch("builtins.print"),
            ):
                with self.assertRaisesRegex(review.ReviewError, "failed after"):
                    review.command_admit_publisher(
                        SimpleNamespace(metadata=metadata_path, output=output_path)
                    )

        self.assertEqual(4, client.attempts)
        self.assertEqual(3, sleeper.call_count)
        self.assertFalse(output_path.exists())

    def test_deferred_publish_revalidates_source_run_before_app_publication(
        self,
    ) -> None:
        class FakeStatusClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []

            @staticmethod
            def get_json(path: str) -> dict:
                if path == f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}":
                    return deferred_pull_request()
                if path == f"repos/{REPOSITORY}/commits/{HEAD_SHA}/status":
                    return combined_ownership_status(22)
                raise AssertionError(f"Unexpected GET path: {path}")

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {}

        metadata = {
            "schema_version": 1,
            "repository": REPOSITORY,
            "repository_id": REPOSITORY_ID,
            "pr_number": DEFERRED_PR_NUMBER,
            "base_sha": BASE_SHA,
            "head_sha": HEAD_SHA,
            "review_route": review.PR_ROUTE_DEFERRED,
            "trusted": True,
            "deferred": True,
            "ignored": False,
            "source_run_id": SOURCE_RUN_ID,
            "run_id": "22",
            "run_attempt": "1",
        }
        config_path = Path(__file__).resolve().parents[1] / "agent-review/config.json"
        with tempfile.TemporaryDirectory() as directory:
            metadata_path = Path(directory) / "metadata.json"
            review.write_json(metadata_path, metadata)
            client = FakeStatusClient()
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch.object(
                    review,
                    "deferred_review_binding",
                    side_effect=review.ReviewError("source run changed"),
                ) as binding,
                patch.dict("os.environ", {"GH_TOKEN": "token"}, clear=True),
            ):
                with self.assertRaisesRegex(review.ReviewError, "source run changed"):
                    review.command_publish(
                        SimpleNamespace(
                            metadata=metadata_path,
                            config=config_path,
                            run_url="https://github.example/runs/22",
                        )
                    )

        binding.assert_called_once()
        status_path = f"repos/{REPOSITORY}/statuses/{HEAD_SHA}"
        self.assertEqual(
            [("POST", status_path), ("POST", status_path)],
            [(method, path) for method, path, _payload in client.sent],
        )
        self.assertEqual(
            ["failure", "failure"],
            [payload["state"] for _method, _path, payload in client.sent],
        )
        self.assertEqual(
            ["Agent issue gate", "Agent jury gate"],
            [payload["context"] for _method, _path, payload in client.sent],
        )

    def test_deferred_publish_emits_binding_failures_once_when_rechecked(
        self,
    ) -> None:
        class FakeStatusClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []

            @staticmethod
            def get_json(path: str) -> dict:
                if path == f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}":
                    return deferred_pull_request()
                if path == f"repos/{REPOSITORY}/commits/{HEAD_SHA}/status":
                    return combined_ownership_status(22)
                raise AssertionError(f"Unexpected GET path: {path}")

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {}

        metadata = {
            "schema_version": 1,
            "repository": REPOSITORY,
            "repository_id": REPOSITORY_ID,
            "pr_number": DEFERRED_PR_NUMBER,
            "base_sha": BASE_SHA,
            "head_sha": HEAD_SHA,
            "review_route": review.PR_ROUTE_DEFERRED,
            "trusted": True,
            "deferred": True,
            "ignored": False,
            "source_run_id": SOURCE_RUN_ID,
            "run_id": "22",
            "run_attempt": "1",
        }
        config_path = Path(__file__).resolve().parents[1] / "agent-review/config.json"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata_path = root / "metadata.json"
            review.write_json(metadata_path, metadata)
            client = FakeStatusClient()
            binding_error = review.ReviewError("source run changed")
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch.object(
                    review,
                    "deferred_review_binding",
                    side_effect=[
                        {"base_sha": BASE_SHA},
                        binding_error,
                        binding_error,
                    ],
                ) as binding,
                patch.dict(
                    "os.environ",
                    {
                        "GH_TOKEN": "token",
                        "AGENT_GH_TOKEN": "agent-token",
                        "COCO_AGENT_APP_LOGIN": "coco-agent[bot]",
                        "COCO_AGENT_APP_BOT_ID": str(APP_BOT_ID),
                    },
                    clear=True,
                ),
            ):
                with self.assertRaisesRegex(review.ReviewError, "source run changed"):
                    review.command_publish(
                        SimpleNamespace(
                            metadata=metadata_path,
                            config=config_path,
                            context=root / "missing-context.json",
                            specialists=root / "missing-specialists",
                            verifiers=root / "missing-verifiers",
                            final_json=root / "missing-final.json",
                            final_markdown=root / "missing-final.md",
                            run_url="https://github.example/runs/22",
                        )
                    )

        self.assertEqual(3, binding.call_count)
        self.assertEqual(
            ["Agent issue gate", "Agent jury gate"],
            [payload["context"] for _method, _path, payload in client.sent],
        )
        self.assertEqual(
            ["failure", "failure"],
            [payload["state"] for _method, _path, payload in client.sent],
        )

    def test_agent_review_workflow_concurrency_is_scoped_to_pr_and_event_group(
        self,
    ) -> None:
        workflow = (
            Path(__file__).resolve().parents[1] / "workflows/agent-review.yml"
        ).read_text(encoding="utf-8")
        workflow_header = workflow.split("\njobs:\n", 1)[0]

        for value in (
            "\nconcurrency:\n",
            "agent-review-router-${{ github.repository_id }}",
            "${{ github.event.pull_request.number }}",
            "github.event_name == 'pull_request_review' && 'approval' || 'head'",
            "cancel-in-progress: true",
        ):
            self.assertIn(value, workflow_header)

    def test_reusable_jury_top_level_concurrency_separates_route_groups(
        self,
    ) -> None:
        workflow = (
            Path(__file__).resolve().parents[1]
            / "workflows/reusable-agent-review-jury.yml"
        ).read_text(encoding="utf-8")
        workflow_header = workflow.split("\njobs:\n", 1)[0]

        for value in (
            "\nconcurrency:\n",
            "inputs.allow_deferred && format('deferred-{0}', inputs.expected_head_sha) ||",
            "inputs.event_name == 'pull_request_review' && 'approval' || 'head'",
            "cancel-in-progress: ${{ ! inputs.allow_deferred }}",
        ):
            self.assertIn(value, workflow_header)
        self.assertNotIn("cancel-in-progress: true", workflow_header)

    def test_agent_review_workflows_bootstrap_legacy_protected_base(self) -> None:
        workflow_root = Path(__file__).resolve().parents[1] / "workflows"
        router = (workflow_root / "agent-review.yml").read_text(encoding="utf-8")
        reusable = (workflow_root / "reusable-agent-review-jury.yml").read_text(
            encoding="utf-8"
        )

        route_step = router.split("\n      - name: Classify bound pull request\n", 1)[
            1
        ].split("\n  direct-secret-review:\n", 1)[0]
        for value in (
            'if python3 "${review_script}" route --help >/dev/null 2>&1; then',
            "route_mode='legacy-prepare'",
            'python3 "${review_script}" prepare \\',
            "EXPECTED_BASE_SHA: ${{ github.event.pull_request.base.sha }}",
            '--context-output "${RUNNER_TEMP}/agent-review-route-context.json"',
            '--metadata-output "${output}"',
            'route_mode == "legacy-prepare"',
            'event_name == "pull_request_review"',
            'payload.get("trusted") is True',
            'payload.get("ignored") is True',
            'payload.get("repository") == repository',
            'type(payload.get("pr_number")) is int',
            'payload.get("pr_number") == expected_pr_number',
            'payload.get("head_sha") == expected_head_sha',
            'payload.get("base_sha") == expected_base_sha',
            'review_route = "compat-skip"',
        ):
            self.assertIn(value, route_step)
        legacy_route_call = route_step.rsplit(
            'python3 "${review_script}" prepare \\', 1
        )[1]
        self.assertNotIn("--repository-id", legacy_route_call)
        decision_script = textwrap.dedent(
            route_step.split("<<'PY'\n", 1)[1].split("\n          PY", 1)[0]
        )
        legacy_decision = decision_script.split(
            'elif route_mode == "legacy-prepare":\n', 1
        )[1].split("\nelse:\n", 1)[0]
        self.assertNotIn('"direct-secret"', legacy_decision)
        self.assertNotIn('"no-secret"', legacy_decision)

        no_secret = router.split("\n  no-secret-review:\n", 1)[1]
        self.assertIn(
            "if: needs.route.outputs.review-route == 'no-secret' || needs.route.outputs.review-route == 'deferred-pinned-bot'",
            no_secret,
        )
        self.assertNotIn("!= 'direct-secret'", no_secret)
        self.assertNotIn("compat-skip", no_secret)

        context_step = reusable.split(
            "\n      - name: Build canonical review context\n", 1
        )[1].split("\n      - name: Export metadata\n", 1)[0]
        for value in (
            'prepare_help="$(python3 "${review_script}" prepare --help)"',
            "if ! grep -q -- '--repository-id' <<< \"${prepare_help}\"; then",
            "Agent review requires the current protected-base prepare protocol.",
            "exit 1",
        ):
            self.assertIn(value, context_step)
        self.assertEqual(1, context_step.count('python3 "${review_script}" prepare \\'))
        self.assertLess(
            context_step.index("if ! grep -q -- '--repository-id'"),
            context_step.index('python3 "${review_script}" prepare \\'),
        )
        modern_call = context_step.split('python3 "${review_script}" prepare \\', 1)[1]
        for value in (
            '--repository-id "${REPOSITORY_ID}"',
            '--source-run-id "${SOURCE_RUN_ID}"',
            '"${deferred_args[@]}"',
        ):
            self.assertIn(value, modern_call)

    def test_router_emits_structured_route_log_for_each_modern_route(self) -> None:
        router = (
            Path(__file__).resolve().parents[1] / "workflows/agent-review.yml"
        ).read_text(encoding="utf-8")
        route_step = router.split("\n      - name: Classify bound pull request\n", 1)[
            1
        ].split("\n  direct-secret-review:\n", 1)[0]
        decision_script = textwrap.dedent(
            route_step.split("<<'PY'\n", 1)[1].split("\n          PY", 1)[0]
        )
        cases = (
            (
                review.PR_ROUTE_DIRECT,
                "same-repository-human",
                "maintainer",
                "User",
                42,
                REPOSITORY,
            ),
            (
                review.PR_ROUTE_DEFERRED,
                "same-repository-deferred-bot",
                "dependabot[bot]",
                "Bot",
                DEPENDABOT_BOT_ID,
                REPOSITORY,
            ),
            (
                review.PR_ROUTE_NO_SECRET,
                "head-repository-mismatch",
                "contributor",
                "User",
                84,
                "someone/coco-framework",
            ),
        )

        for route, reason, login, author_type, author_id, head_repo in cases:
            with self.subTest(route=route):
                payload = {
                    "review_route": route,
                    "route_reason": reason,
                    "author_login": login,
                    "author_type": author_type,
                    "author_id": author_id,
                    "head_repository": head_repo,
                }
                with tempfile.TemporaryDirectory() as directory:
                    root = Path(directory)
                    payload_path = root / "payload.json"
                    output_path = root / "github-output.txt"
                    review.write_json(payload_path, payload)
                    result = subprocess.run(
                        [
                            sys.executable,
                            "-",
                            str(payload_path),
                            str(output_path),
                            "route",
                            "pull_request_target",
                            REPOSITORY,
                            str(DEFERRED_PR_NUMBER),
                            HEAD_SHA,
                            BASE_SHA,
                        ],
                        input=decision_script,
                        text=True,
                        capture_output=True,
                        check=False,
                    )
                    output_text = output_path.read_text(encoding="utf-8")

                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(f"review-route={route}\n", output_text)
                prefix = "agent-review-route "
                self.assertTrue(result.stdout.startswith(prefix), result.stdout)
                route_log = json.loads(result.stdout.removeprefix(prefix))
                self.assertEqual("agent-review-route", route_log["event"])
                self.assertEqual("route", route_log["route_mode"])
                self.assertEqual(route, route_log["review_route"])
                self.assertEqual(reason, route_log["route_reason"])
                self.assertEqual(login, route_log["author_login"])
                self.assertEqual(author_type, route_log["author_type"])
                self.assertEqual(author_id, route_log["author_id"])
                self.assertEqual(head_repo, route_log["head_repository"])

    def test_legacy_router_compat_skip_requires_exact_metadata_binding(self) -> None:
        router = (
            Path(__file__).resolve().parents[1] / "workflows/agent-review.yml"
        ).read_text(encoding="utf-8")
        route_step = router.split("\n      - name: Classify bound pull request\n", 1)[
            1
        ].split("\n  direct-secret-review:\n", 1)[0]
        decision_script = textwrap.dedent(
            route_step.split("<<'PY'\n", 1)[1].split("\n          PY", 1)[0]
        )

        exact_payload = {
            "trusted": True,
            "ignored": True,
            "repository": REPOSITORY,
            "pr_number": DEFERRED_PR_NUMBER,
            "head_sha": HEAD_SHA,
            "base_sha": BASE_SHA,
        }

        def execute(
            payload: dict,
            *,
            event_name: str = "pull_request_review",
            repository: str = REPOSITORY,
            pr_number: str = str(DEFERRED_PR_NUMBER),
            head_sha: str = HEAD_SHA,
            base_sha: str = BASE_SHA,
        ) -> tuple[subprocess.CompletedProcess[str], str]:
            with tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                payload_path = root / "payload.json"
                output_path = root / "github-output.txt"
                review.write_json(payload_path, payload)
                result = subprocess.run(
                    [
                        sys.executable,
                        "-",
                        str(payload_path),
                        str(output_path),
                        "legacy-prepare",
                        event_name,
                        repository,
                        pr_number,
                        head_sha,
                        base_sha,
                    ],
                    input=decision_script,
                    text=True,
                    capture_output=True,
                    check=False,
                )
                output = (
                    output_path.read_text(encoding="utf-8")
                    if output_path.exists()
                    else ""
                )
                return result, output

        result, output = execute(exact_payload)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("review-route=compat-skip\n", output)

        failure_cases = (
            ("untrusted", {"trusted": False}, {}),
            ("not ignored", {"ignored": False}, {}),
            ("repository mismatch", {"repository": "someone/fork"}, {}),
            ("PR mismatch", {"pr_number": DEFERRED_PR_NUMBER + 1}, {}),
            ("head mismatch", {"head_sha": "c" * 40}, {}),
            ("base mismatch", {"base_sha": "d" * 40}, {}),
            ("event mismatch", {}, {"event_name": "pull_request_target"}),
            ("non-numeric PR", {}, {"pr_number": "not-a-number"}),
        )
        for name, payload_changes, argument_changes in failure_cases:
            with self.subTest(name=name):
                result, output = execute(
                    {**exact_payload, **payload_changes}, **argument_changes
                )
                self.assertNotEqual(0, result.returncode)
                self.assertIn(
                    "Legacy protected-base metadata is not an exact trusted approval skip.",
                    result.stderr,
                )
                self.assertEqual("", output)

    def test_agent_review_workflow_binds_the_trusted_app_author(self) -> None:
        workflow = (
            Path(__file__).resolve().parents[1] / "workflows/agent-review.yml"
        ).read_text(encoding="utf-8")

        for value in (
            "COCO_AGENT_APP_BOT_ID: ${{ vars.COCO_AGENT_APP_BOT_ID }}",
            "COCO_AGENT_APP_LOGIN: ${{ vars.COCO_AGENT_APP_LOGIN }}",
        ):
            self.assertIn(value, workflow)
        self.assertNotIn("--trusted-app-login", workflow)
        self.assertNotIn("--trusted-app-bot-id", workflow)

    def test_prepare_reads_the_trusted_app_identity_from_environment(self) -> None:
        configured = config()
        configured["deferred_bot_authors"] = [
            {"login": "dependabot[bot]", "id": DEPENDABOT_BOT_ID}
        ]
        pull_request = {
            "state": "open",
            "base": {
                "ref": "main",
                "sha": BASE_SHA,
                "repo": {"id": REPOSITORY_ID, "full_name": REPOSITORY},
            },
            "head": {
                "sha": HEAD_SHA,
                "repo": {"full_name": "patton174/coco-framework"},
            },
            "user": {
                "id": APP_BOT_ID,
                "login": "coco-agent[bot]",
                "type": "Bot",
            },
        }

        class FakeClient:
            @staticmethod
            def get_json(path: str) -> dict:
                if path == "repos/patton174/coco-framework/pulls/1":
                    return pull_request
                raise AssertionError(f"Unexpected GET path: {path}")

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with (
                patch.object(review, "GitHubClient", return_value=FakeClient()),
                patch.object(review, "load_config", return_value=configured),
                patch.object(
                    review,
                    "classify_pr_route",
                    return_value=review.PR_ROUTE_NO_SECRET,
                ) as classifier,
                patch.object(
                    review,
                    "current_maintainer_approval",
                    return_value=(False, []),
                ),
                patch("builtins.print"),
                patch.dict(
                    "os.environ",
                    {
                        "GH_TOKEN": "token",
                        "COCO_AGENT_APP_LOGIN": "coco-agent[bot]",
                        "COCO_AGENT_APP_BOT_ID": str(APP_BOT_ID),
                    },
                    clear=True,
                ),
            ):
                result = review.command_prepare(
                    SimpleNamespace(
                        repository="patton174/coco-framework",
                        repository_id=REPOSITORY_ID,
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
        classifier.assert_called_once_with(
            pull_request,
            "patton174/coco-framework",
            "coco-agent[bot]",
            APP_BOT_ID,
            (("dependabot[bot]", DEPENDABOT_BOT_ID),),
        )

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
                self.send_pull_reads: list[int] = []
                self.pull_reads = 0

            def get_json(self, path: str) -> dict:
                if path == "repos/patton174/coco-framework/pulls/1":
                    self.pull_reads += 1
                    if self.pull_reads == 1:
                        raise review.GitHubTransientError("HTTP 502")
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
                self.send_pull_reads.append(self.pull_reads)
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
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch.object(review.time, "sleep") as sleeper,
                patch("builtins.print") as output,
            ):
                result = review.command_publish(
                    SimpleNamespace(
                        metadata=metadata_path,
                        run_url="https://github.example/runs/1",
                    )
                )

        self.assertEqual(0, result)
        self.assertEqual(3, client.pull_reads)
        sleeper.assert_called_once()
        publication = json.loads(output.call_args_list[-1].args[0])
        self.assertEqual("success", publication["state"])
        self.assertEqual(1, len(client.sent))
        self.assertEqual([3], client.send_pull_reads)
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
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch("builtins.print"),
            ):
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

    def test_stale_same_head_run_cannot_overwrite_newer_gate_statuses(self) -> None:
        class FakeStatusClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []

            @staticmethod
            def get_json(path: str) -> dict:
                if path == "repos/patton174/coco-framework/pulls/1":
                    return {
                        "state": "open",
                        "head": {"sha": HEAD_SHA},
                        "base": {"sha": BASE_SHA, "ref": "main"},
                    }
                if path == (
                    f"repos/patton174/coco-framework/commits/{HEAD_SHA}/status"
                ):
                    return combined_ownership_status(20)
                raise AssertionError(f"Unexpected GET path: {path}")

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {}

        class FakeAgentClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []

            @staticmethod
            def paginate(path: str, limit: int = 1000) -> list[dict]:
                if path != "repos/patton174/coco-framework/issues/1/comments":
                    raise AssertionError(f"Unexpected paginated path: {path}")
                if limit != 500:
                    raise AssertionError(f"Unexpected comment limit: {limit}")
                return [
                    {
                        "id": 99,
                        "body": (
                            f"{review.COMMENT_MARKER}\n<!-- agent-jury-run:20:1 -->\n"
                        ),
                        "user": {
                            "login": "coco-agent[bot]",
                            "id": APP_BOT_ID,
                            "type": "Bot",
                        },
                    }
                ]

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {}

        metadata = {
            "schema_version": 1,
            "repository": "patton174/coco-framework",
            "pr_number": 1,
            "base_sha": BASE_SHA,
            "head_sha": HEAD_SHA,
            "review_route": review.PR_ROUTE_DIRECT,
            "trusted": True,
            "deferred": False,
            "ignored": False,
            "source_run_id": 0,
            "run_id": "10",
            "run_attempt": "1",
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata_path = root / "metadata.json"
            review.write_json(metadata_path, metadata)
            status_client = FakeStatusClient()
            agent_client = FakeAgentClient()
            with (
                patch.object(
                    review,
                    "GitHubClient",
                    side_effect=[status_client, agent_client],
                ),
                patch("builtins.print") as output,
                patch.dict(
                    "os.environ",
                    {
                        "GH_TOKEN": "token",
                        "AGENT_GH_TOKEN": "agent-token",
                        "COCO_AGENT_APP_LOGIN": "coco-agent[bot]",
                        "COCO_AGENT_APP_BOT_ID": str(APP_BOT_ID),
                    },
                    clear=True,
                ),
            ):
                result = review.command_publish(
                    SimpleNamespace(
                        metadata=metadata_path,
                        config=root / "missing-config.json",
                        context=root / "missing-context.json",
                        specialists=root / "missing-specialists",
                        verifiers=root / "missing-verifiers",
                        final_json=root / "missing-final.json",
                        final_markdown=root / "missing-final.md",
                        run_url="https://github.example/runs/10",
                    )
                )

        self.assertEqual(0, result)
        self.assertEqual([], status_client.sent)
        self.assertEqual([], agent_client.sent)
        publication = json.loads(output.call_args.args[0])
        self.assertEqual("stale", publication["state"])
        self.assertEqual(10, publication["run_id"])

    def test_run_becoming_stale_before_comment_has_zero_side_effects(self) -> None:
        class FakeStatusClient:
            def __init__(self) -> None:
                self.ownership_reads = 0
                self.sent: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> object:
                if path == f"repos/{REPOSITORY}/pulls/1":
                    return {
                        "state": "open",
                        "head": {"sha": HEAD_SHA},
                        "base": {"sha": BASE_SHA, "ref": "main"},
                    }
                if path == f"repos/{REPOSITORY}/commits/{HEAD_SHA}/status":
                    self.ownership_reads += 1
                    owner = 10 if self.ownership_reads < 3 else 20
                    return combined_ownership_status(owner)
                raise AssertionError(f"Unexpected GET path: {path}")

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {}

        class FakeAgentClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {}

        metadata = {
            "schema_version": 1,
            "repository": REPOSITORY,
            "pr_number": 1,
            "base_sha": BASE_SHA,
            "head_sha": HEAD_SHA,
            "review_route": review.PR_ROUTE_DIRECT,
            "trusted": True,
            "deferred": False,
            "ignored": False,
            "source_run_id": 0,
            "run_id": "10",
            "run_attempt": "1",
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata_path = root / "metadata.json"
            review.write_json(metadata_path, metadata)
            status_client = FakeStatusClient()
            agent_client = FakeAgentClient()
            with (
                patch.object(
                    review,
                    "GitHubClient",
                    side_effect=[status_client, agent_client],
                ),
                patch.object(review, "managed_comment", return_value=None),
                patch.object(review, "app_finding_issues", return_value={}),
                patch("builtins.print") as output,
                patch.dict(
                    "os.environ",
                    {
                        "GH_TOKEN": "token",
                        "AGENT_GH_TOKEN": "agent-token",
                        "COCO_AGENT_APP_LOGIN": "coco-agent[bot]",
                        "COCO_AGENT_APP_BOT_ID": str(APP_BOT_ID),
                    },
                    clear=True,
                ),
            ):
                result = review.command_publish(
                    SimpleNamespace(
                        metadata=metadata_path,
                        config=root / "missing-config.json",
                        context=root / "missing-context.json",
                        specialists=root / "missing-specialists",
                        verifiers=root / "missing-verifiers",
                        final_json=root / "missing-final.json",
                        final_markdown=root / "missing-final.md",
                        run_url="https://github.example/runs/10",
                    )
                )

        self.assertEqual(0, result)
        self.assertEqual(3, status_client.ownership_reads)
        self.assertEqual([], status_client.sent)
        self.assertEqual([], agent_client.sent)
        publication = json.loads(output.call_args.args[0])
        self.assertEqual("stale", publication["state"])

    def test_reusable_binding_uses_protected_retrying_resolver(self) -> None:
        workflow = (
            Path(__file__).resolve().parents[1]
            / "workflows/reusable-agent-review-jury.yml"
        ).read_text(encoding="utf-8")
        binding = workflow.split(
            "\n      - name: Checkout protected binding helper\n", 1
        )[1].split("\n      - name: Checkout trusted base\n", 1)[0]

        for value in (
            "ref: ${{ inputs.expected_base_sha }}",
            "path: .agent-review-bootstrap",
            ".agent-review-bootstrap/.github/scripts/agent_review.py resolve-pr",
            '--repository-id "${REPOSITORY_ID}"',
            '--expected-base-sha "${EXPECTED_BASE_SHA}"',
            '--expected-head-sha "${EXPECTED_HEAD_SHA}"',
        ):
            self.assertIn(value, binding)
        self.assertNotIn('gh api "repos/${REPOSITORY}/pulls/', binding)

    def test_reusable_binding_output_parser_enforces_exact_pr_and_head(self) -> None:
        workflow = (
            Path(__file__).resolve().parents[1]
            / "workflows/reusable-agent-review-jury.yml"
        ).read_text(encoding="utf-8")
        binding_step = workflow.split(
            "\n      - name: Resolve pull request binding\n", 1
        )[1].split("\n      - name: Checkout trusted base\n", 1)[0]
        parser_script = textwrap.dedent(
            binding_step.split("<<'PY'\n", 1)[1].split("\n          PY", 1)[0]
        )
        payload = {
            "pr_number": DEFERRED_PR_NUMBER,
            "base_sha": BASE_SHA,
            "head_sha": HEAD_SHA,
        }

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            payload_path = root / "binding.json"
            output_path = root / "github-output.txt"
            review.write_json(payload_path, payload)
            valid = subprocess.run(
                [
                    sys.executable,
                    "-",
                    str(payload_path),
                    str(output_path),
                    str(DEFERRED_PR_NUMBER),
                    BASE_SHA,
                    HEAD_SHA,
                ],
                input=parser_script,
                text=True,
                capture_output=True,
                check=False,
            )
            output = output_path.read_text(encoding="utf-8")
            stale = subprocess.run(
                [
                    sys.executable,
                    "-",
                    str(payload_path),
                    str(root / "stale-output.txt"),
                    str(DEFERRED_PR_NUMBER),
                    BASE_SHA,
                    "c" * 40,
                ],
                input=parser_script,
                text=True,
                capture_output=True,
                check=False,
            )

        self.assertEqual(0, valid.returncode, valid.stderr)
        self.assertEqual(
            (
                f"pr-number={DEFERRED_PR_NUMBER}\n"
                f"base-sha={BASE_SHA}\n"
                f"head-sha={HEAD_SHA}\n"
            ),
            output,
        )
        self.assertNotEqual(0, stale.returncode)
        self.assertIn("stale head SHA", stale.stderr)

    def test_publisher_admission_is_protected_and_has_no_repository_secrets(
        self,
    ) -> None:
        workflow = (
            Path(__file__).resolve().parents[1]
            / "workflows/reusable-agent-review-jury.yml"
        ).read_text(encoding="utf-8")
        admission = workflow.split("\n  publisher-admission:\n", 1)[1].split(
            "\n  trusted-publisher:\n", 1
        )[0]
        trusted = workflow.split("\n  trusted-publisher:\n", 1)[1].split(
            "\n  no-secret-publisher:\n", 1
        )[0]
        no_secret = workflow.split("\n  no-secret-publisher:\n", 1)[1]

        for value in (
            "needs: [prepare, specialists, verifiers, chair]",
            "always() &&",
            "ref: ${{ needs.prepare.outputs.base-sha }}",
            ".agent-review-admission/.github/scripts/agent_review.py admit-publisher",
            "--require-run-ownership",
            "statuses: read",
        ):
            self.assertIn(value, admission)
        for forbidden in (
            "${{ secrets.",
            "ANTHROPIC_API_KEY",
            "COCO_AGENT_APP_PRIVATE_KEY",
            "environment: coco-agent",
        ):
            self.assertNotIn(forbidden, admission)
        for publisher in (trusted, no_secret):
            self.assertIn("publisher-admission", publisher)
            self.assertIn(
                "needs.publisher-admission.outputs.admitted == 'true'", publisher
            )

    def test_publisher_jobs_are_serialized_across_event_groups(self) -> None:
        workflow = (
            Path(__file__).resolve().parents[1]
            / "workflows/reusable-agent-review-jury.yml"
        ).read_text(encoding="utf-8")
        trusted = workflow.split("\n  trusted-publisher:\n", 1)[1].split(
            "\n  no-secret-publisher:\n", 1
        )[0]
        no_secret = workflow.split("\n  no-secret-publisher:\n", 1)[1]
        for publisher in (trusted, no_secret):
            self.assertIn("agent-review-publisher-", publisher)
            self.assertIn("cancel-in-progress: false", publisher)
            concurrency = publisher.split("\n    concurrency:\n", 1)[1].split(
                "\n    permissions:\n", 1
            )[0]
            self.assertIn(
                "agent-review-publisher-${{ inputs.repository_id }}-${{ inputs.pr_number }}",
                concurrency,
            )
            self.assertNotIn("needs.prepare.outputs.head-sha", concurrency)

    def test_route_decision_explains_direct_deferred_and_no_secret(self) -> None:
        human = deferred_pull_request()
        human["user"] = {"id": 1, "login": "patton174", "type": "User"}
        fork = json.loads(json.dumps(human))
        fork["head"]["repo"]["full_name"] = "someone/fork"
        cases = (
            (human, (), review.PR_ROUTE_DIRECT, "same-repository-human"),
            (fork, (), review.PR_ROUTE_NO_SECRET, "head-repository-mismatch"),
            (
                deferred_pull_request(),
                (("dependabot[bot]", DEPENDABOT_BOT_ID),),
                review.PR_ROUTE_DEFERRED,
                "same-repository-deferred-bot",
            ),
        )
        for pull_request, deferred_authors, route, reason in cases:
            with self.subTest(route=route):
                decision = review.classify_pr_route_decision(
                    pull_request,
                    REPOSITORY,
                    deferred_bot_authors=deferred_authors,
                )
                self.assertEqual(route, decision["review_route"])
                self.assertEqual(reason, decision["route_reason"])
                self.assertEqual(
                    pull_request["user"]["login"], decision["author_login"]
                )

        trusted_app = deferred_pull_request()
        trusted_app["user"] = {
            "id": str(APP_BOT_ID),
            "login": "coco-agent[bot]",
            "type": "Bot",
        }
        self.assertEqual(
            review.PR_ROUTE_DIRECT,
            review.classify_pr_route_decision(
                trusted_app, REPOSITORY, "coco-agent[bot]", APP_BOT_ID
            )["review_route"],
        )
        dependabot = deferred_pull_request()
        dependabot["user"]["id"] = str(DEPENDABOT_BOT_ID)
        self.assertEqual(
            review.PR_ROUTE_DEFERRED,
            review.classify_pr_route_decision(
                dependabot,
                REPOSITORY,
                deferred_bot_authors=(("dependabot[bot]", DEPENDABOT_BOT_ID),),
            )["review_route"],
        )
        for invalid in (True, 0, -1, "0", "01", "+1", " 1", 1.0, None):
            self.assertIsNone(review.normalize_actor_id(invalid))

    def test_prepare_rejects_incompatible_event_modes_before_api_calls(self) -> None:
        cases = (
            (True, "pull_request_review", SOURCE_RUN_ID, "workflow_run binding"),
            (False, "workflow_run", 0, "Direct Agent review event"),
            (False, "pull_request_target", SOURCE_RUN_ID, "explicit deferred mode"),
        )
        for allow_deferred, event_name, source_run_id, message in cases:
            with self.subTest(event_name=event_name, allow_deferred=allow_deferred):
                with patch.object(
                    review,
                    "GitHubClient",
                    side_effect=AssertionError("invalid modes must not call GitHub"),
                ):
                    with self.assertRaisesRegex(review.ReviewError, message):
                        review.command_prepare(
                            SimpleNamespace(
                                repository=REPOSITORY,
                                repository_id=REPOSITORY_ID,
                                pr_number=1,
                                event_name=event_name,
                                expected_head_sha=HEAD_SHA,
                                allow_deferred=allow_deferred,
                                source_run_id=source_run_id,
                                base_root=Path("."),
                                config=Path("config.json"),
                                context_output=Path("context.json"),
                                metadata_output=Path("metadata.json"),
                            )
                        )

    def test_deferred_binding_retries_transient_run_and_pull_lookups(self) -> None:
        class RecoveringClient:
            def __init__(self) -> None:
                self.attempts = {"run": 0, "pull": 0}

            def get_json(self, path: str) -> dict:
                if path == f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}":
                    key, value = "run", deferred_workflow_run()
                elif path == f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}":
                    key, value = "pull", deferred_pull_request()
                else:
                    raise AssertionError(f"Unexpected GET path: {path}")
                self.attempts[key] += 1
                if self.attempts[key] == 1:
                    raise review.GitHubTransientError("temporary")
                return value

        client = RecoveringClient()
        with (
            patch.object(review.time, "sleep") as sleeper,
            patch("builtins.print"),
            patch.dict("os.environ", {}, clear=True),
        ):
            binding = review.deferred_review_candidate(
                client,
                REPOSITORY,
                REPOSITORY_ID,
                SOURCE_RUN_ID,
                deferred_config(),
            )

        self.assertTrue(binding["eligible"])
        self.assertEqual({"run": 2, "pull": 2}, client.attempts)
        self.assertEqual(2, sleeper.call_count)

    def test_deferred_binding_fails_closed_after_retry_exhaustion(self) -> None:
        class FailingClient:
            def __init__(self) -> None:
                self.attempts = 0

            def get_json(self, path: str) -> dict:
                self.attempts += 1
                raise review.GitHubTransientError(f"temporary: {path}")

        client = FailingClient()
        with (
            patch.object(review.time, "sleep") as sleeper,
            patch("builtins.print"),
        ):
            with self.assertRaisesRegex(review.ReviewError, "failed after"):
                review.deferred_review_candidate(
                    client,
                    REPOSITORY,
                    REPOSITORY_ID,
                    SOURCE_RUN_ID,
                    deferred_config(),
                )
        self.assertEqual(4, client.attempts)
        self.assertEqual(3, sleeper.call_count)

    def test_dependabot_review_event_is_ignored_without_gate_writes(self) -> None:
        route = review.prepare_direct_route_state(
            "pull_request_review", 0, review.PR_ROUTE_DEFERRED
        )
        self.assertEqual(
            {"trusted": False, "deferred": True, "ignored": True, "source_run_id": 0},
            route,
        )
        with tempfile.TemporaryDirectory() as directory:
            metadata_path = Path(directory) / "metadata.json"
            review.write_json(
                metadata_path,
                {
                    "repository": REPOSITORY,
                    "pr_number": DEFERRED_PR_NUMBER,
                    "base_sha": BASE_SHA,
                    "head_sha": HEAD_SHA,
                    "review_route": review.PR_ROUTE_DEFERRED,
                    **route,
                },
            )
            args = SimpleNamespace(
                metadata=metadata_path,
                run_url="https://github.example/runs/1",
            )
            with (
                patch.object(
                    review,
                    "GitHubClient",
                    side_effect=AssertionError("ignored events must not call GitHub"),
                ),
                patch("builtins.print"),
            ):
                self.assertEqual(0, review.command_mark_pending(args))
                self.assertEqual(0, review.command_mark_failed(args))
                self.assertEqual(0, review.command_publish(args))

    def test_workflows_preserve_deferred_and_publisher_security_boundaries(
        self,
    ) -> None:
        root = Path(__file__).resolve().parents[1] / "workflows"
        router = (root / "agent-review.yml").read_text(encoding="utf-8")
        deferred = (root / "agent-review-deferred.yml").read_text(encoding="utf-8")
        reusable = (root / "reusable-agent-review-jury.yml").read_text(encoding="utf-8")
        no_secret_call = router.split("\n  no-secret-review:\n", 1)[1]
        self.assertIn("agent-review-route", router)
        self.assertIn('"route_reason"', router)
        self.assertNotIn("secrets: inherit", no_secret_call)
        self.assertIn("allow_deferred: true", deferred)
        self.assertIn("source_run_id: ${{ github.event.workflow_run.id }}", deferred)
        self.assertIn("base_sha: ${{ steps.binding.outputs.base_sha }}", deferred)
        self.assertIn("expected_base_sha: ${{ needs.bind.outputs.base_sha }}", deferred)
        self.assertEqual(
            2,
            router.count(
                "expected_base_sha: ${{ github.event.pull_request.base.sha }}"
            ),
        )
        header = reusable.split("\njobs:\n", 1)[0]
        self.assertIn("expected_base_sha:", header)
        self.assertIn("format('deferred-{0}', inputs.expected_head_sha)", header)
        self.assertIn("cancel-in-progress: ${{ ! inputs.allow_deferred }}", header)
        self.assertIn("ref: ${{ inputs.expected_base_sha }}", reusable)
        self.assertIn("--expected-base-sha", reusable)
        self.assertIn("base_sha != expected_base_sha", reusable)
        no_secret_publisher = reusable.split("\n  no-secret-publisher:\n", 1)[1]
        self.assertIn("needs.prepare.outputs.deferred == 'false'", no_secret_publisher)

        admission = reusable.split("\n  publisher-admission:\n", 1)[1].split(
            "\n  trusted-publisher:\n", 1
        )[0]
        self.assertIn("ref: ${{ needs.prepare.outputs.base-sha }}", admission)
        self.assertIn("agent_review.py admit-publisher", admission)
        self.assertIn("statuses: read", admission)
        self.assertNotIn("${{ secrets.", admission)
        for publisher in (
            reusable.split("\n  trusted-publisher:\n", 1)[1].split(
                "\n  no-secret-publisher:\n", 1
            )[0],
            no_secret_publisher,
        ):
            concurrency = publisher.split("\n    concurrency:\n", 1)[1].split(
                "\n    permissions:\n", 1
            )[0]
            self.assertIn(
                "agent-review-publisher-${{ inputs.repository_id }}-${{ inputs.pr_number }}",
                concurrency,
            )
            self.assertNotIn("needs.prepare.outputs.head-sha", concurrency)

    def test_mark_pending_and_admission_bind_the_current_run(self) -> None:
        class PendingClient:
            def __init__(self) -> None:
                self.sent: list[dict] = []

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                del method, path
                self.sent.append(payload)
                return {}

        metadata = trusted_metadata(run_id=42, run_attempt=2)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata_path = root / "metadata.json"
            review.write_json(metadata_path, metadata)
            pending = PendingClient()
            with patch.object(review, "GitHubClient", return_value=pending):
                review.command_mark_pending(
                    SimpleNamespace(metadata=metadata_path, run_url="run")
                )
            self.assertEqual(
                "Agent jury run 42:2 in progress", pending.sent[0]["description"]
            )
            self.assertEqual(
                [review.OWNERSHIP_STATUS_CONTEXT, review.STATUS_CONTEXT],
                [status["context"] for status in pending.sent],
            )

            for owner, admitted in ((42, True), (43, False)):

                class AdmissionClient:
                    @staticmethod
                    def get_json(path: str) -> object:
                        if path == f"repos/{REPOSITORY}/pulls/1":
                            return {
                                "state": "open",
                                "head": {"sha": HEAD_SHA},
                                "base": {"sha": BASE_SHA, "ref": "main"},
                            }
                        return {
                            "statuses": [
                                {
                                    "context": review.OWNERSHIP_STATUS_CONTEXT,
                                    "description": review.run_ownership_description(
                                        (owner, 1 if owner == 43 else 2)
                                    ),
                                }
                            ]
                        }

                output_path = root / f"admission-{owner}.json"
                with (
                    patch.object(
                        review, "GitHubClient", return_value=AdmissionClient()
                    ),
                    patch("builtins.print"),
                ):
                    review.command_admit_publisher(
                        SimpleNamespace(metadata=metadata_path, output=output_path)
                    )
                self.assertEqual(admitted, review.read_json(output_path)["admitted"])

    def test_publisher_admission_retries_and_exhausts_api_failures(self) -> None:
        class AdmissionClient:
            def __init__(self, recover: bool) -> None:
                self.recover = recover
                self.attempts = 0

            def get_json(self, path: str) -> object:
                if path == f"repos/{REPOSITORY}/pulls/1":
                    self.attempts += 1
                    if not self.recover or self.attempts == 1:
                        raise review.GitHubTransientError("temporary")
                    return {
                        "state": "open",
                        "head": {"sha": HEAD_SHA},
                        "base": {"sha": BASE_SHA, "ref": "main"},
                    }
                return {
                    "statuses": [
                        {
                            "context": review.OWNERSHIP_STATUS_CONTEXT,
                            "description": review.run_ownership_description((42, 1)),
                        }
                    ]
                }

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata_path = root / "metadata.json"
            review.write_json(metadata_path, trusted_metadata())
            recovering = AdmissionClient(True)
            with (
                patch.object(review, "GitHubClient", return_value=recovering),
                patch.object(review.time, "sleep") as sleeper,
                patch("builtins.print"),
            ):
                review.command_admit_publisher(
                    SimpleNamespace(
                        metadata=metadata_path, output=root / "admitted.json"
                    )
                )
            self.assertEqual(2, recovering.attempts)
            sleeper.assert_called_once()

            failing = AdmissionClient(False)
            with (
                patch.object(review, "GitHubClient", return_value=failing),
                patch.object(review.time, "sleep") as sleeper,
                patch("builtins.print"),
            ):
                with self.assertRaisesRegex(review.ReviewError, "failed after"):
                    review.command_admit_publisher(
                        SimpleNamespace(
                            metadata=metadata_path, output=root / "failed.json"
                        )
                    )
            self.assertEqual(4, failing.attempts)
            self.assertEqual(3, sleeper.call_count)

    def test_stale_trusted_run_cannot_publish_any_side_effect(self) -> None:
        class StatusClient:
            def __init__(self) -> None:
                self.sent: list[dict] = []

            @staticmethod
            def get_json(path: str) -> object:
                if path == f"repos/{REPOSITORY}/pulls/1":
                    return {
                        "state": "open",
                        "head": {"sha": HEAD_SHA},
                        "base": {"sha": BASE_SHA, "ref": "main"},
                    }
                return {
                    "statuses": [
                        {
                            "context": review.OWNERSHIP_STATUS_CONTEXT,
                            "description": review.run_ownership_description((43, 1)),
                        }
                    ]
                }

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                del method, path
                self.sent.append(payload)
                return {}

        with tempfile.TemporaryDirectory() as directory:
            metadata_path = Path(directory) / "metadata.json"
            review.write_json(metadata_path, trusted_metadata())
            client = StatusClient()
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch("builtins.print") as output,
            ):
                result = review.command_publish(
                    SimpleNamespace(metadata=metadata_path, run_url="run")
                )
        self.assertEqual(0, result)
        self.assertEqual([], client.sent)
        self.assertEqual("stale", json.loads(output.call_args.args[0])["state"])

    def test_resolve_pr_retries_and_writes_exact_binding(self) -> None:
        pull_request = deferred_pull_request()
        pull_request["number"] = 1

        class Client:
            def __init__(self) -> None:
                self.attempts = 0

            def get_json(self, path: str) -> dict:
                self.attempts += 1
                if self.attempts == 1:
                    raise review.GitHubTransientError("temporary")
                if path != f"repos/{REPOSITORY}/pulls/1":
                    raise AssertionError(f"Unexpected GET path: {path}")
                return pull_request

        with tempfile.TemporaryDirectory() as directory:
            output_path = Path(directory) / "binding.json"
            client = Client()
            with (
                patch.object(review, "GitHubClient", return_value=client),
                patch.object(review.time, "sleep") as sleeper,
                patch("builtins.print"),
            ):
                review.command_resolve_pr(
                    SimpleNamespace(
                        repository=REPOSITORY,
                        repository_id=REPOSITORY_ID,
                        pr_number=1,
                        expected_base_sha=BASE_SHA,
                        expected_head_sha=HEAD_SHA,
                        output=output_path,
                    )
                )
            binding = review.read_json(output_path)
        self.assertEqual(2, client.attempts)
        sleeper.assert_called_once()
        self.assertEqual(HEAD_SHA, binding["head_sha"])
        self.assertEqual(BASE_SHA, binding["base_sha"])
        with self.assertRaisesRegex(review.ReviewError, "binding is invalid"):
            review.resolve_current_pull_request(
                SimpleNamespace(get_json=lambda _path: pull_request),
                REPOSITORY,
                REPOSITORY_ID,
                1,
                HEAD_SHA,
                "test-base-binding",
                "c" * 40,
            )

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

    def test_max_tokens_continuation_reconstructs_json_on_third_attempt(self) -> None:
        class FragmentClient(review.AnthropicClient):
            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []
                self.responses = [
                    review.ModelTextResponse('{"required":', "max_tokens"),
                    review.ModelTextResponse("true", "max_tokens"),
                    review.ModelTextResponse("}", "end_turn"),
                ]

            def complete_fragment(
                self, system: str, user: str, max_tokens: int
            ) -> review.ModelTextResponse:
                self.calls.append((system, user, max_tokens))
                return self.responses.pop(0)

        client = FragmentClient()
        with patch("builtins.print") as warning:
            result = review.complete_with_shape_repair(
                client,
                "protected system",
                '{"task":"review"}',
                100,
                lambda value: review.require_report_fields(value, {"required"}, "Test"),
            )

        self.assertEqual({"required": True}, result)
        self.assertEqual(3, len(client.calls))
        self.assertIn("Protected truncation continuation", client.calls[1][0])
        self.assertIn("Protected truncation continuation", client.calls[2][0])
        self.assertEqual(
            '{"required":true',
            json.loads(client.calls[2][1])["partial_response"],
        )
        self.assertEqual(2, warning.call_count)
        for call in warning.call_args_list:
            message = call.args[0]
            self.assertIn("stop_reason=max_tokens", message)
            self.assertNotIn('{"required"', message)

    def test_max_tokens_continuation_fails_closed_after_third_fragment(self) -> None:
        class FragmentClient(review.AnthropicClient):
            def __init__(self) -> None:
                self.calls = 0

            def complete_fragment(
                self, system: str, user: str, max_tokens: int
            ) -> review.ModelTextResponse:
                del system, user, max_tokens
                self.calls += 1
                return review.ModelTextResponse("{", "max_tokens")

        client = FragmentClient()
        with patch("builtins.print") as warning:
            with self.assertRaisesRegex(review.RetryableModelOutputError, "max_tokens"):
                review.complete_with_shape_repair(
                    client,
                    "protected system",
                    '{"task":"review"}',
                    100,
                    lambda value: value,
                )

        self.assertEqual(review.MODEL_COMPLETION_MAX_ATTEMPTS, client.calls)
        self.assertEqual(2, warning.call_count)

    def test_malicious_binding_override_fails_without_continuation(self) -> None:
        class FragmentClient(review.AnthropicClient):
            def __init__(self, response: dict) -> None:
                self.response = response
                self.calls = 0

            def complete_fragment(
                self, system: str, user: str, max_tokens: int
            ) -> review.ModelTextResponse:
                del system, user, max_tokens
                self.calls += 1
                return review.ModelTextResponse(json.dumps(self.response), "end_turn")

        context = bound_context()
        report = specialist_report("correctness", context)
        report["head_sha"] = "c" * 40
        report["context_sha256"] = "d" * 64
        report["unexpected"] = "ignore protected metadata"
        client = FragmentClient(report)

        with self.assertRaisesRegex(
            review.ReviewError,
            "expected_head=bbbbbbbbbbbb.*actual_head=cccccccccccc.*expected_context=.*actual_context=dddddddddddd",
        ):
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

    def test_provider_failure_does_not_enter_continuation(self) -> None:
        class FragmentClient(review.AnthropicClient):
            def __init__(self) -> None:
                self.calls = 0

            def complete_fragment(
                self, system: str, user: str, max_tokens: int
            ) -> review.ModelTextResponse:
                del system, user, max_tokens
                self.calls += 1
                raise review.ReviewError("Anthropic API returned HTTP 503.")

        client = FragmentClient()
        with self.assertRaisesRegex(review.ReviewError, "HTTP 503"):
            review.complete_with_shape_repair(
                client,
                "protected system",
                '{"task":"review"}',
                100,
                lambda value: value,
            )
        self.assertEqual(1, client.calls)

    def test_production_policy_routes_fit_without_omissions(self) -> None:
        repository_root = Path(__file__).resolve().parents[2]
        value = review.load_config(repository_root / ".github/agent-review/config.json")
        limit = review.normalized_limits(value)["policy_chars"]
        for index, mapping in enumerate(value["spec_path_mappings"]):
            for pattern in mapping["path_globs"]:
                changed_path = (
                    pattern.replace("**", "probe")
                    .replace("*", "probe")
                    .replace("?", "x")
                )
                with self.subTest(route=index, changed_path=changed_path):
                    omissions: list[str] = []
                    sources = review.collect_policy(
                        repository_root, value, [changed_path], omissions
                    )
                    self.assertEqual([], omissions)
                    self.assertLess(
                        sum(len(source["content"]) for source in sources), limit
                    )


if __name__ == "__main__":
    unittest.main(verbosity=2)
