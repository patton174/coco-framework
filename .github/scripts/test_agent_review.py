#!/usr/bin/env python3

from __future__ import annotations

import io
import json
import re
import subprocess
import sys
import tempfile
import textwrap
import traceback
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
DEFERRED_WORKFLOW_ID = 1234567
RELEASE_APP_ACTION_SHA = "bcd2ba49218906704ab6c1aa796996da409d3eb1"
MODEL_CONFIG_SHA256 = review.sha256_text(
    review.canonical_json(
        {
            "protocol": "openai-responses",
            "base_url": "https://models.example.invalid/v1",
            "model": "review-model",
            "thinking": "auto",
        }
    )
)
NON_MODEL_JOB_FORBIDDEN_ENV = ("COCO_AGENT_MODEL_API_KEY",)
APP_LOGIN = "coco-agent[bot]"
GITHUB_RESPONSE_READ_LIMIT = 4 * 1024 * 1024 + 1


def commit_status_response(
    path: str, payload: dict, api_url: str = "https://api.github.com"
) -> dict:
    return {
        "id": 1,
        "url": f"{api_url.rstrip('/')}/{path.lstrip('/')}",
        "context": payload["context"],
        "state": payload["state"],
        "description": payload["description"],
        "target_url": payload["target_url"],
        "creator": {},
    }


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


def model_env(
    protocol: str, base_url: str = "https://models.example.invalid"
) -> dict[str, str]:
    return {
        "COCO_AGENT_MODEL_PROTOCOL": protocol,
        "COCO_AGENT_MODEL_BASE_URL": base_url,
        "COCO_AGENT_MODEL": "review-model",
        "COCO_AGENT_MODEL_THINKING": "auto",
        "COCO_AGENT_MODEL_API_KEY": "test-api-key",
    }


def model_configuration_env() -> dict[str, str]:
    environment = model_env("openai-responses")
    del environment["COCO_AGENT_MODEL_API_KEY"]
    return environment


class FakeModelResponse:
    def __init__(self, body: bytes) -> None:
        self.body = body
        self.read_limit = 0

    def __enter__(self) -> "FakeModelResponse":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def read(self, limit: int) -> bytes:
        self.read_limit = limit
        return self.body[:limit]


class FakeGitHubHeaders:
    def __init__(
        self,
        content_lengths: list[str] | None = None,
        transfer_encodings: list[str] | None = None,
    ) -> None:
        self.content_lengths = content_lengths or []
        self.transfer_encodings = transfer_encodings or []

    def get_all(self, name: str) -> list[str] | None:
        values = {
            "content-length": self.content_lengths,
            "transfer-encoding": self.transfer_encodings,
        }.get(name.lower(), [])
        return values or None

    def items(self) -> list[tuple[str, str]]:
        return [
            *[("Content-Length", value) for value in self.content_lengths],
            *[("Transfer-Encoding", value) for value in self.transfer_encodings],
        ]


class FakeGitHubResponse:
    def __init__(
        self,
        body: bytes = b"",
        *,
        error: BaseException | None = None,
        headers: object | None = None,
        content_length: int | None = None,
    ) -> None:
        self.body = body
        self.error = error
        self.headers = (
            headers
            if headers is not None
            else (
                {"Content-Length": str(content_length)}
                if content_length is not None
                else {}
            )
        )
        self.reads = 0

    def __enter__(self) -> "FakeGitHubResponse":
        return self

    def __exit__(self, *args: object) -> None:
        return None

    def read(self, limit: int) -> bytes:
        self.reads += 1
        if limit != GITHUB_RESPONSE_READ_LIMIT:
            raise AssertionError(f"Unexpected read limit: {limit}")
        if self.error is not None:
            raise self.error
        return self.body


class FailingReadBody:
    def __init__(self, error: BaseException) -> None:
        self.error = error

    def read(self, limit: int) -> bytes:
        if limit != 4097:
            raise AssertionError(f"Unexpected error read limit: {limit}")
        raise self.error

    def close(self) -> None:
        return None


def transient_transport_errors() -> tuple[BaseException, ...]:
    return (
        TimeoutError("response timeout"),
        ConnectionResetError(review.errno.ECONNRESET, "connection reset"),
        ConnectionAbortedError(review.errno.ECONNABORTED, "connection aborted"),
        review.http.client.RemoteDisconnected("remote disconnected"),
        review.http.client.IncompleteRead(b"partial", 10),
        review.ssl.SSLEOFError("TLS EOF"),
        review.ssl.SSLZeroReturnError("TLS connection closed"),
    )


def permanent_transport_errors() -> tuple[BaseException, ...]:
    return (
        OSError(review.errno.EMFILE, "too many open files"),
        review.ssl.SSLCertVerificationError("certificate rejected"),
        review.ssl.SSLError("TLS protocol error"),
    )


def managed_comment(body: str) -> dict:
    return {
        "id": 7,
        "body": body,
        "user": {"id": APP_BOT_ID, "login": APP_LOGIN, "type": "Bot"},
    }


def app_actor(
    bot_id: int = APP_BOT_ID, login: str = APP_LOGIN, actor_type: str = "Bot"
) -> dict:
    return {"id": bot_id, "login": login, "type": actor_type}


def test_operation_marker(
    default_group_id: str, default_action: str, **changes: object
) -> str:
    values = {
        "repository": REPOSITORY,
        "repository_id": REPOSITORY_ID,
        "expected_login": APP_LOGIN,
        "expected_bot_id": APP_BOT_ID,
        "run_order": (42, 1),
        "pr_number": 60,
        "head_sha": HEAD_SHA,
        "group_id": default_group_id,
        "action": default_action,
    }
    return review.operation_marker(**{**values, **changes})


def finding_issue_resource(
    number: int,
    marker: str,
    operation: str,
    user: dict | None = None,
) -> dict:
    return {
        "number": number,
        "title": "title",
        "body": f"{marker}\n{operation}\nBody\n",
        "state": "open",
        "labels": [{"name": review.FINDING_ISSUE_LABEL}],
        "user": user or app_actor(),
    }


class FindingIssueScanClient:
    def __init__(self, issues: list[dict]) -> None:
        self.issues = issues
        self.scans = 0

    def paginate(self, path: str, limit: int = 1000) -> list[dict]:
        self.scans += 1
        return self.issues


def old_managed_comment() -> dict:
    return managed_comment(
        review.COMMENT_MARKER + "\n<!-- agent-jury-run:41:1 -->\nOld\n"
    )


def test_managed_comment_body() -> str:
    return review.COMMENT_MARKER + "\n<!-- agent-jury-run:42:1 -->\nResult\n"


def upsert_test_comment(
    prior: dict | None, require_current_pr: object
) -> dict[str, object]:
    return review.upsert_comment(
        review.GitHubClient("token"),
        REPOSITORY,
        REPOSITORY_ID,
        60,
        HEAD_SHA,
        test_managed_comment_body(),
        (42, 1),
        APP_LOGIN,
        APP_BOT_ID,
        require_current_pr,
        prior,
    )


def synchronize_test_findings(client: object, findings: list[dict]) -> list[dict]:
    return review.synchronize_finding_issues(
        client,
        REPOSITORY,
        REPOSITORY_ID,
        60,
        HEAD_SHA,
        findings,
        (42, 1),
        APP_LOGIN,
        APP_BOT_ID,
        "https://github.example/runs/42",
        "https://github.example",
        lambda: {},
    )


def github_request_method(request: object, timeout: int) -> str:
    if timeout != 60 or not isinstance(request, review.urllib.request.Request):
        raise AssertionError("Unexpected GitHub request")
    return request.get_method()


def anthropic_envelope(
    text: str = '{"ok":true}', stop_reason: str = "end_turn"
) -> dict:
    return {
        "stop_reason": stop_reason,
        "content": [{"type": "text", "text": text}],
    }


def openai_envelope(
    text: str = '{"ok":true}',
    status: str = "completed",
    incomplete_reason: str | None = None,
) -> dict:
    value = {
        "object": "response",
        "status": status,
        "output": [
            {"type": "reasoning", "summary": []},
            {
                "type": "message",
                "role": "assistant",
                "status": "completed" if status == "completed" else "incomplete",
                "content": [{"type": "output_text", "text": text}],
            },
        ],
    }
    if incomplete_reason is not None:
        value["incomplete_details"] = {"reason": incomplete_reason}
    return value


def openai_chat_envelope(
    text: str = '{"ok":true}', finish_reason: str = "stop"
) -> dict:
    return {
        "id": "chatcmpl-test",
        "object": "chat.completion",
        "choices": [
            {
                "index": 0,
                "message": {"role": "assistant", "content": text},
                "finish_reason": finish_reason,
            }
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
                "model_config_sha256": MODEL_CONFIG_SHA256,
                "context_sha256": "",
            },
            "trusted": {
                "policy": [
                    {
                        "source": "AGENTS.md",
                        "trust_domain": "protected-policy",
                        "line_count": 1,
                        "available_line_ranges": [[1, 1]],
                        "content": "Policy",
                    }
                ],
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
                        "trust_domain": "head-code",
                        "line_count": 1,
                        "available_line_ranges": [[1, 1]],
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
    evidence_refs: list[dict] | None = None,
) -> dict:
    del confidence
    checks = {
        "claim": "SUPPORTED",
        "severity": "SUPPORTED",
        "anchor": "SUPPORTED",
        "trigger": "SUPPORTED",
        "impact": "SUPPORTED",
        "change_scope": "IN_SCOPE",
    }
    if action == "DISAGREE":
        checks["claim"] = "CONTRADICTED"
    elif action == "UNVERIFIED":
        checks["claim"] = "UNVERIFIED"
    default_evidence_refs = [
        {
            "trust_domain": "head-code",
            "path": "src/Foo.java",
            "start_line": 1,
            "end_line": 1,
            "checks": ["anchor", "claim", "impact", "trigger"],
        },
        {
            "trust_domain": "protected-policy",
            "path": "AGENTS.md",
            "start_line": 1,
            "end_line": 1,
            "checks": ["change_scope", "severity"],
        },
    ]
    references = default_evidence_refs if evidence_refs is None else evidence_refs
    evidence = "; ".join(
        f"{item.get('trust_domain')}:{item.get('path')}#L{item.get('start_line')}-L{item.get('end_line')}"
        for item in references
    )
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
                **checks,
                "evidence_refs": references,
                "reason": "The cited code and policy support this disposition.",
                "evidence": evidence,
                "verification": "Inspect the cited branch and exercise the stated trigger.",
            }
        ],
        "context_gaps": [],
    }


def raw_verifier_report(
    role: str,
    context: dict,
    finding_id: str,
    action: str = "AGREE",
    evidence_refs: list[dict] | None = None,
) -> dict:
    normalized = verifier_report(role, context, finding_id, action=action)
    normalized_review = normalized["reviews"][0]
    if evidence_refs is None:
        source_ids = {
            (item["trust_domain"], item["path"]): item["source_id"]
            for item in review.context_evidence_catalog(context)
        }
        evidence_refs = [
            {
                "source_id": source_ids[(item["trust_domain"], item["path"])],
                "start_line": item["start_line"],
                "end_line": item["end_line"],
                "checks": item["checks"],
            }
            for item in normalized_review["evidence_refs"]
        ]
    verification = {
        key: value
        for key, value in normalized_review.items()
        if key not in {"action", "evidence", "evidence_refs"}
    }
    verification["evidence_refs"] = evidence_refs
    return {
        "schema_version": normalized["schema_version"],
        "role": normalized["role"],
        "head_sha": normalized["head_sha"],
        "context_sha256": normalized["context_sha256"],
        "evidence": normalized["evidence"],
        "verifications": [verification],
        "context_gaps": normalized["context_gaps"],
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


def deferred_source_association() -> dict:
    pull_request = deferred_pull_request()
    return {
        "number": pull_request["number"],
        "base": {
            **pull_request["base"],
            "repo": {
                "id": REPOSITORY_ID,
                "name": "coco-framework",
                "url": "https://api.github.com/repos/patton174/coco-framework",
            },
        },
        "head": {
            **pull_request["head"],
            "repo": {
                "id": REPOSITORY_ID,
                "name": "coco-framework",
                "url": "https://api.github.com/repos/patton174/coco-framework",
            },
        },
    }


def deferred_workflow() -> dict:
    return {
        "id": DEFERRED_WORKFLOW_ID,
        "name": review.DEFERRED_WORKFLOW_NAME,
        "path": review.DEFERRED_WORKFLOW_PATH,
        "state": "active",
    }


def deferred_workflow_run() -> dict:
    run_title = (
        f"Agent Review Jury / PR #{DEFERRED_PR_NUMBER} / "
        f"head {HEAD_SHA} / base {BASE_SHA}"
    )
    return {
        "id": SOURCE_RUN_ID,
        "workflow_id": DEFERRED_WORKFLOW_ID,
        "name": run_title,
        "path": review.DEFERRED_WORKFLOW_PATH,
        "event": "pull_request_target",
        "status": "completed",
        "conclusion": "success",
        "display_title": run_title,
        "repository": {"id": REPOSITORY_ID, "full_name": REPOSITORY},
        "head_repository": {"id": REPOSITORY_ID, "full_name": REPOSITORY},
        "head_sha": HEAD_SHA,
        "head_branch": HEAD_REF,
        "pull_requests": [deferred_source_association()],
    }


def deferred_source_jobs() -> dict:
    jobs = [
        {
            "name": review.DEFERRED_ROUTE_JOB_NAME,
            "status": "completed",
            "conclusion": "success",
        },
        {
            "name": review.DEFERRED_MARKER_JOB_NAME,
            "status": "completed",
            "conclusion": "success",
        },
        {
            "name": "Run no-secret maintainer gate",
            "status": "completed",
            "conclusion": "skipped",
        },
    ]
    return {"total_count": len(jobs), "jobs": jobs}


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
        "model_config_sha256": MODEL_CONFIG_SHA256,
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
        workflow: dict | None = None,
        pull_request: dict | None = None,
        associated: list[dict] | None = None,
        jobs: dict | None = None,
    ) -> None:
        self.run = json.loads(json.dumps(run or deferred_workflow_run()))
        self.workflow = json.loads(json.dumps(workflow or deferred_workflow()))
        if associated is not None:
            self.run["pull_requests"] = associated
        self.pull_request = pull_request or deferred_pull_request()
        self.jobs = jobs or deferred_source_jobs()
        self.get_paths: list[str] = []

    def get_json(self, path: str) -> dict:
        self.get_paths.append(path)
        if (
            path
            == f"repos/{REPOSITORY}/actions/workflows/{review.DEFERRED_WORKFLOW_FILE}"
        ):
            return self.workflow
        if path == f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}":
            return self.run
        if path == f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}":
            return self.pull_request
        if path == (
            f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}/jobs"
            "?filter=latest&per_page=100"
        ):
            return self.jobs
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
        self.assertEqual(64_000, limits["policy_chars"])
        self.assertEqual(24, limits["max_context_files"])
        explicit_section_budget = sum(
            limits[key]
            for key in (
                "diff_chars",
                "policy_chars",
                "intent_chars",
                "code_context_chars",
            )
        )
        self.assertEqual(312_000, explicit_section_budget)
        self.assertEqual(
            72_000, limits["assembled_context_chars"] - explicit_section_budget
        )
        self.assertEqual(limits["diff_chars"], limits["patch_chars"])
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
            ("coco-features/coco-feature-registry",): i18n_specs,
            ("coco-foundation/coco-feature-model",): {module_layout_spec},
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
            "coco-feature-archive-smoke": True,
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
            "coco-build/coco-maven-plugin/pom.xml",
            "coco-build/coco-maven-plugin/src/test/java/io/github/coco/maven/CocoPackagePruneMojoTest.java",
            "coco-support/coco-feature-archive-smoke/pom.xml",
            "coco-support/coco-feature-archive-smoke/src/test/java/io/github/coco/fixture/archive/FeatureArchiveSmokeIT.java",
            ".github/scripts/verify_boot_archive_feature_coordinates.py",
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
        self.assertIn("run: mvn -B -ntp install", workflow)
        self.assertNotIn("coco-samples/", workflow)
        self.assertFalse((repository_root / "coco-samples").exists())
        tracked_samples = subprocess.run(
            ["git", "-C", str(repository_root), "ls-files", "coco-samples"],
            check=True,
            capture_output=True,
            text=True,
        )
        self.assertEqual([], tracked_samples.stdout.splitlines())

    def test_security_specialist_has_compact_prompt_and_bounded_output_budget(
        self,
    ) -> None:
        root = Path(__file__).resolve().parents[1]
        value = review.load_config(root / "agent-review/config.json")
        security = review.role_map(value, "specialists")["security-isolation"]
        prompt = (root / "agent-review/prompts/specialist.md").read_text(
            encoding="utf-8"
        )

        self.assertEqual(6144, security["max_tokens"])
        self.assertIn("Compact Output Requirement", prompt)
        self.assertIn("no more than 160 characters", prompt)
        self.assertIn("Never repeat the diff", prompt)
        self.assertIn("do not omit a security finding", prompt)

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

        canonical_feature_model_paths = [
            "coco-foundation/coco-feature-model/src/main/java/"
            "io/github/coco/feature/model/StandardCocoFeatures.java",
        ]
        omissions = []
        sources = review.collect_policy(
            repository_root,
            value,
            canonical_feature_model_paths,
            omissions,
        )
        source_paths = {source["source"] for source in sources}
        self.assertEqual([], omissions)
        self.assertEqual(base_policy, source_paths)
        self.assertFalse(i18n_specs & source_paths)
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
                "coco-features/coco-mybatis-plus/pom.xml",
                "coco-features/coco-feature-openapi/pom.xml",
                "coco-features/coco-rate-limit/pom.xml",
                "coco-features/coco-idempotency/pom.xml",
                "coco-features/coco-feature-security/pom.xml",
                "coco-features/coco-feature-tenant/pom.xml",
                "coco-features/coco-lock/pom.xml",
                "coco-features/coco-scheduling/pom.xml",
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
                ".github/scripts/test_verify_boot_archive_feature_coordinates.py",
                ".github/scripts/verify_boot_archive_feature_coordinates.py",
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

    def test_deferred_binding_policies_define_exact_trust_contract(self) -> None:
        repository_root = Path(__file__).resolve().parents[2]
        policy_paths = (
            ".github/agent-review/policy.md",
            ".github/workflow-governance.md",
            "coco-support/coco-document/superpowers/specs/"
            "2026-07-10-multi-agent-review-jury.md",
            "coco-support/coco-document/superpowers/specs/"
            "2026-07-11-agent-governance-automation.md",
        )
        expected_contract = {
            "canonical": ["ID", "name", "path", "state"],
            "source": ["workflow_id", "path", "event", "repository"],
            "association": ["structured pull_requests", "current PR re-fetch"],
            "jobs": {"route": "success", "marker": "success", "others": "skipped"},
            "untrusted": ["run-name", "name", "display_title"],
        }
        contract_pattern = re.compile(
            r"<!-- coco-agent-deferred-binding-contract:v1 "
            r"(?P<contract>\{[^\n]+\}) -->"
        )
        contradictory_claims = (
            re.compile(
                r"\b(?:trust|trusts|trusted|rely|relies|relying)\b"
                r"(?:(?!\b(?:not|never|untrusted)\b).){0,120}"
                r"(?:`run-name`|`display_title`|evaluated `name`)",
                re.IGNORECASE,
            ),
            re.compile(
                r"(?:`run-name`|`display_title`|evaluated `name`)"
                r"(?:(?!\b(?:not|never|untrusted)\b).){0,120}"
                r"\b(?:trusted|authoritative)\b",
                re.IGNORECASE,
            ),
            re.compile(
                r"\b(?:workflow identity|PR[- ]binding)\b"
                r"(?:(?!\b(?:not|never|untrusted)\b).){0,120}"
                r"\b(?:from|using|uses|derived from|binds?)\b"
                r".{0,80}(?:`run-name`|`display_title`|evaluated `name`)",
                re.IGNORECASE,
            ),
            re.compile(
                r"(?:`run-name`|`display_title`|evaluated `name`)"
                r".{0,80}\b(?:defines|provides|determines|establishes)\b"
                r".{0,80}\b(?:workflow identity|PR[- ]binding)\b",
                re.IGNORECASE,
            ),
        )

        def assert_contract(policy: str) -> None:
            matches = list(contract_pattern.finditer(policy))
            self.assertEqual(1, len(matches), "deferred binding contract count")
            self.assertEqual(
                expected_contract,
                json.loads(matches[0].group("contract")),
            )
            normalized_policy = " ".join(policy.split())
            for contradictory_claim in contradictory_claims:
                self.assertIsNone(
                    contradictory_claim.search(normalized_policy),
                    "evaluated workflow titles must remain untrusted",
                )

        for relative_path in policy_paths:
            with self.subTest(policy=relative_path):
                policy = (repository_root / relative_path).read_text(encoding="utf-8")
                assert_contract(policy)
                with self.assertRaisesRegex(
                    AssertionError,
                    "evaluated workflow titles must remain untrusted",
                ):
                    assert_contract(
                        policy + "\nWorkflow identity is derived from `run-name`.\n"
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
        self.assertEqual(52_000, defaults["policy_chars"])
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

    def test_github_client_status_and_header_failures_use_narrow_transport_errors(
        self,
    ) -> None:
        client = review.GitHubClient("token")
        for error in transient_transport_errors():
            with self.subTest(error=type(error).__name__):
                with patch.object(review.urllib.request, "urlopen", side_effect=error):
                    with self.assertRaises(review.GitHubTransientError):
                        client.get_json(f"repos/{REPOSITORY}")

        for error in permanent_transport_errors():
            with self.subTest(error=type(error).__name__):
                with patch.object(review.urllib.request, "urlopen", side_effect=error):
                    with self.assertRaises(type(error)) as raised:
                        client.get_json(f"repos/{REPOSITORY}")
                self.assertNotIsInstance(raised.exception, review.GitHubTransientError)

    def test_truncated_retryable_http_error_reconciles_without_write_replay(
        self,
    ) -> None:
        error_factories = (
            lambda: review.http.client.IncompleteRead(b"partial", 10),
            lambda: review.http.client.RemoteDisconnected("remote disconnected"),
        )
        for prior, method in ((None, "POST"), (old_managed_comment(), "PATCH")):
            for error_factory in error_factories:
                body_error = error_factory()
                with self.subTest(method=method, error=type(body_error).__name__):
                    persisted: dict | None = None
                    recovery_reads = 0
                    reads = 0
                    writes = 0

                    def urlopen(request: object, timeout: int) -> FakeGitHubResponse:
                        nonlocal persisted, reads, recovery_reads, writes
                        request_method = github_request_method(request, timeout)
                        if request_method == "GET":
                            reads += 1
                            if persisted is None:
                                return FakeGitHubResponse(b"[]")
                            recovery_reads += 1
                            if recovery_reads == 1:
                                raise review.http.client.RemoteDisconnected(
                                    "recovery connection closed"
                                )
                            return FakeGitHubResponse(
                                review.canonical_json([persisted]).encode("utf-8")
                            )
                        if request_method != method:
                            raise AssertionError(f"Unexpected write: {request_method}")
                        writes += 1
                        payload = json.loads(bytes(request.data or b"{}"))
                        persisted = managed_comment(payload["body"])
                        raise review.urllib.error.HTTPError(
                            request.full_url,
                            503,
                            "temporary",
                            {"Transfer-Encoding": "chunked"},
                            FailingReadBody(body_error),
                        )

                    checks: list[int] = []
                    with (
                        patch.object(review.urllib.request, "urlopen", urlopen),
                        patch.object(review.time, "sleep") as sleep,
                    ):
                        result = upsert_test_comment(
                            prior, lambda: checks.append(1) or {}
                        )
                    self.assertEqual(7, result["id"])
                    self.assertEqual(1, writes)
                    self.assertEqual(2, recovery_reads)
                    self.assertEqual(3 if prior is None else 2, reads)
                    self.assertEqual(5, len(checks))
                    sleep.assert_called_once_with(
                        review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS[0]
                    )

    def test_truncated_nonretryable_http_error_has_no_fabricated_detail(
        self,
    ) -> None:
        client = review.GitHubClient("token")
        path = f"repos/{REPOSITORY}/issues"
        truncated = review.urllib.error.HTTPError(
            path,
            422,
            "unprocessable",
            {"Transfer-Encoding": "chunked"},
            FailingReadBody(review.http.client.IncompleteRead(b"partial", 10)),
        )
        with patch.object(review.urllib.request, "urlopen", side_effect=truncated):
            with self.assertRaises(review.ReviewError) as raised:
                client.send_json("POST", path, {})
        self.assertNotIsInstance(raised.exception, review.GitHubTransientError)
        self.assertEqual(
            f"GitHub API returned HTTP 422 for POST {path}.", str(raised.exception)
        )
        self.assertNotIn("partial", str(raised.exception))

        permanent = OSError(review.errno.EMFILE, "too many open files")
        error = review.urllib.error.HTTPError(
            path,
            422,
            "unprocessable",
            None,
            FailingReadBody(permanent),
        )
        with patch.object(review.urllib.request, "urlopen", side_effect=error):
            with self.assertRaises(OSError) as permanent_raised:
                client.send_json("POST", path, {})
        self.assertEqual(review.errno.EMFILE, permanent_raised.exception.errno)

    def test_github_client_read_failures_reconcile_post_and_patch_without_replay(
        self,
    ) -> None:
        for prior, method in ((None, "POST"), (old_managed_comment(), "PATCH")):
            for response_error in transient_transport_errors():
                with self.subTest(method=method, error=type(response_error).__name__):
                    persisted: dict | None = None
                    writes = 0
                    reads = 0

                    def urlopen(request: object, timeout: int) -> FakeGitHubResponse:
                        nonlocal persisted, reads, writes
                        request_method = github_request_method(request, timeout)
                        if request_method == "GET":
                            reads += 1
                            values = [] if persisted is None else [persisted]
                            return FakeGitHubResponse(
                                review.canonical_json(values).encode()
                            )
                        if request_method != method:
                            raise AssertionError(f"Unexpected write: {request_method}")
                        writes += 1
                        payload = json.loads(bytes(request.data or b"{}"))
                        persisted = managed_comment(payload["body"])
                        return FakeGitHubResponse(error=response_error)

                    checks: list[int] = []
                    with patch.object(review.urllib.request, "urlopen", urlopen):
                        result = upsert_test_comment(
                            prior, lambda: checks.append(1) or {}
                        )
                    self.assertEqual(7, result["id"])
                    self.assertEqual(1, writes)
                    self.assertEqual(1 if prior is not None else 2, reads)
                    self.assertEqual(3, len(checks))

    def test_github_client_read_failures_use_bounded_recovery_gets(self) -> None:
        previous = old_managed_comment()
        for recovery_error in transient_transport_errors():
            with self.subTest(error=type(recovery_error).__name__):
                persisted: dict | None = None
                reads = 0
                writes = 0

                def urlopen(request: object, timeout: int) -> FakeGitHubResponse:
                    nonlocal persisted, reads, writes
                    method = github_request_method(request, timeout)
                    if method == "PATCH":
                        writes += 1
                        payload = json.loads(bytes(request.data or b"{}"))
                        persisted = managed_comment(payload["body"])
                        return FakeGitHubResponse(error=TimeoutError("write timeout"))
                    if method != "GET" or persisted is None:
                        raise AssertionError(f"Unexpected request: {request.full_url}")
                    reads += 1
                    if reads == 1:
                        return FakeGitHubResponse(error=recovery_error)
                    return FakeGitHubResponse(
                        review.canonical_json([persisted]).encode("utf-8")
                    )

                checks: list[int] = []
                with (
                    patch.object(review.urllib.request, "urlopen", urlopen),
                    patch.object(review.time, "sleep") as sleep,
                ):
                    result = upsert_test_comment(
                        previous, lambda: checks.append(1) or {}
                    )
                self.assertEqual(7, result["id"])
                self.assertEqual(1, writes)
                self.assertEqual(2, reads)
                self.assertEqual(5, len(checks))
                sleep.assert_called_once_with(
                    review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS[0]
                )

    def test_github_client_read_failure_does_not_generalize_oserror(self) -> None:
        client = review.GitHubClient("token")
        for error in permanent_transport_errors():
            with self.subTest(error=type(error).__name__):
                response = FakeGitHubResponse(error=error)
                with patch.object(
                    review.urllib.request, "urlopen", return_value=response
                ):
                    with self.assertRaises(type(error)) as raised:
                        client.send_json("POST", f"repos/{REPOSITORY}/issues", {})
                self.assertNotIsInstance(raised.exception, review.GitHubTransientError)

    def test_short_content_length_write_reconciles_without_replay(self) -> None:
        for prior, method in ((None, "POST"), (old_managed_comment(), "PATCH")):
            with self.subTest(method=method):
                persisted: dict | None = None
                reads = 0
                writes = 0

                def urlopen(request: object, timeout: int) -> FakeGitHubResponse:
                    nonlocal persisted, reads, writes
                    request_method = github_request_method(request, timeout)
                    if request_method == "GET":
                        reads += 1
                        body = review.canonical_json(
                            [] if persisted is None else [persisted]
                        ).encode("utf-8")
                        return FakeGitHubResponse(body, content_length=len(body))
                    if request_method != method:
                        raise AssertionError(f"Unexpected write: {request_method}")
                    writes += 1
                    payload = json.loads(bytes(request.data or b"{}"))
                    persisted = managed_comment(payload["body"])
                    response_body = b'{"incomplete":true}'
                    return FakeGitHubResponse(
                        response_body, content_length=len(response_body) + 1
                    )

                checks: list[int] = []
                with patch.object(review.urllib.request, "urlopen", urlopen):
                    result = upsert_test_comment(prior, lambda: checks.append(1) or {})
                self.assertEqual(7, result["id"])
                self.assertEqual(1, writes)
                self.assertEqual(1 if prior is not None else 2, reads)
                self.assertEqual(3, len(checks))

    def test_short_content_length_recovery_get_uses_bounded_retry(self) -> None:
        previous = old_managed_comment()
        persisted: dict | None = None
        reads = 0
        writes = 0

        def urlopen(request: object, timeout: int) -> FakeGitHubResponse:
            nonlocal persisted, reads, writes
            method = github_request_method(request, timeout)
            if method == "PATCH":
                writes += 1
                payload = json.loads(bytes(request.data or b"{}"))
                persisted = managed_comment(payload["body"])
                response_body = b'{"incomplete":true}'
                return FakeGitHubResponse(
                    response_body, content_length=len(response_body) + 1
                )
            if method != "GET" or persisted is None:
                raise AssertionError(f"Unexpected request: {request.full_url}")
            reads += 1
            body = review.canonical_json([persisted]).encode("utf-8")
            return FakeGitHubResponse(
                body, content_length=len(body) + (1 if reads == 1 else 0)
            )

        checks: list[int] = []
        with (
            patch.object(review.urllib.request, "urlopen", urlopen),
            patch.object(review.time, "sleep") as sleep,
        ):
            result = upsert_test_comment(previous, lambda: checks.append(1) or {})
        self.assertEqual(7, result["id"])
        self.assertEqual(1, writes)
        self.assertEqual(2, reads)
        self.assertEqual(5, len(checks))
        sleep.assert_called_once_with(
            review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS[0]
        )

    def test_content_length_contract_is_strict_without_chunked_inference(self) -> None:
        client = review.GitHubClient("token")
        invalid_headers = (
            FakeGitHubHeaders(["invalid"]),
            FakeGitHubHeaders(["-1"]),
            FakeGitHubHeaders(["1", "2"]),
            FakeGitHubHeaders(["1,2"]),
            FakeGitHubHeaders(["2"], ["chunked"]),
            FakeGitHubHeaders(transfer_encodings=[""]),
            FakeGitHubHeaders(transfer_encodings=["gzip"]),
            FakeGitHubHeaders(transfer_encodings=["chunked", "chunked"]),
            FakeGitHubHeaders(transfer_encodings=["chunked, chunked"]),
            FakeGitHubHeaders(transfer_encodings=["chunked, gzip"]),
            FakeGitHubHeaders(transfer_encodings=["gzip, chunked"]),
        )
        for headers in invalid_headers:
            with self.subTest(headers=headers.items()):
                response = FakeGitHubResponse(b"{}", headers=headers)
                with patch.object(
                    review.urllib.request, "urlopen", return_value=response
                ):
                    with self.assertRaises(review.ReviewError) as raised:
                        client.send_json("POST", f"repos/{REPOSITORY}/issues", {})
                self.assertNotIsInstance(raised.exception, review.GitHubTransientError)
                self.assertEqual(0, response.reads)

        oversized = FakeGitHubResponse(
            headers=FakeGitHubHeaders([str(4 * 1024 * 1024 + 1)])
        )
        with patch.object(review.urllib.request, "urlopen", return_value=oversized):
            with self.assertRaisesRegex(review.ReviewError, "bounded size"):
                client.send_json("POST", f"repos/{REPOSITORY}/issues", {})
        self.assertEqual(0, oversized.reads)

        accepted_headers = (
            FakeGitHubHeaders(),
            FakeGitHubHeaders(transfer_encodings=["chunked"]),
            FakeGitHubHeaders(transfer_encodings=[" Chunked "]),
            FakeGitHubHeaders(["2", "2"]),
            FakeGitHubHeaders(["2, 2"]),
        )
        for headers in accepted_headers:
            with self.subTest(headers=headers.items()):
                response = FakeGitHubResponse(b"{}", headers=headers)
                with patch.object(
                    review.urllib.request, "urlopen", return_value=response
                ):
                    self.assertEqual(
                        {},
                        client.send_json("POST", f"repos/{REPOSITORY}/issues", {}),
                    )
                self.assertEqual(1, response.reads)

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
                MODEL_CONFIG_SHA256,
            )
            review.validate_context(context)
            self.assertEqual("github-raw-diff", context["untrusted"]["diff_source"])
            sources = {item["source"] for item in context["untrusted"]["code_contexts"]}
            self.assertIn(filename, sources)
            self.assertIn("module/src/test/java/io/example/FooTest.java", sources)
            self.assertIn("module/pom.xml", sources)

    def test_build_context_adds_starter_pom_for_feature_pom_change(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Trusted policy", encoding="utf-8")
            (root / "pom.xml").write_text("<project />", encoding="utf-8")
            starter = root / "coco-spring/coco-spring-boot-starter/pom.xml"
            starter.parent.mkdir(parents=True)
            starter.write_text(
                "<project>\n  <artifactId>starter</artifactId>\n</project>",
                encoding="utf-8",
            )
            filename = "coco-features/coco-feature-web/pom.xml"
            context = review.build_context(
                FakeContextClient(
                    {filename: "<project><artifactId>web</artifactId></project>"}
                ),
                REPOSITORY,
                {
                    "number": 1,
                    "title": "Feature pom",
                    "body": "",
                    "base": {"sha": BASE_SHA},
                    "head": {"sha": HEAD_SHA},
                },
                [
                    {
                        "filename": filename,
                        "status": "modified",
                        "patch": "@@ -1 +1 @@\n-old\n+new",
                    }
                ],
                [],
                "diff --git a/pom.xml b/pom.xml\n+new",
                root,
                config(),
                MODEL_CONFIG_SHA256,
            )

            contexts = context["untrusted"]["code_contexts"]
            starter_context = next(
                item
                for item in contexts
                if item["source"] == "coco-spring/coco-spring-boot-starter/pom.xml"
            )
            self.assertEqual("related-starter-pom", starter_context["kind"])
            self.assertEqual(3, starter_context["line_count"])
            self.assertIn("     3 </project>", starter_context["content"])

    def test_build_context_skips_starter_pom_for_non_feature_change(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Trusted policy", encoding="utf-8")
            (root / "pom.xml").write_text("<project />", encoding="utf-8")
            starter = root / "coco-spring/coco-spring-boot-starter/pom.xml"
            starter.parent.mkdir(parents=True)
            starter.write_text(
                "<project>\n  <artifactId>starter</artifactId>\n</project>",
                encoding="utf-8",
            )
            pom_filename = "coco-spring/coco-config/pom.xml"
            file_filename = "docs/guide.md"
            context = review.build_context(
                FakeContextClient(
                    {
                        pom_filename: "<project><artifactId>config</artifactId></project>",
                        file_filename: "# Guide",
                    }
                ),
                REPOSITORY,
                {
                    "number": 1,
                    "title": "Other files",
                    "body": "",
                    "base": {"sha": BASE_SHA},
                    "head": {"sha": HEAD_SHA},
                },
                [
                    {
                        "filename": pom_filename,
                        "status": "modified",
                        "patch": "@@ -1 +1 @@\n-old\n+new",
                    },
                    {
                        "filename": file_filename,
                        "status": "modified",
                        "patch": "@@ -1 +1 @@\n-old\n+new",
                    },
                ],
                [],
                "diff --git a/files b/files\n+new",
                root,
                config(),
                MODEL_CONFIG_SHA256,
            )

            self.assertNotIn(
                "coco-spring/coco-spring-boot-starter/pom.xml",
                {item["source"] for item in context["untrusted"]["code_contexts"]},
            )

    def test_build_context_rejects_feature_pom_without_starter_context(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Trusted policy", encoding="utf-8")
            (root / "pom.xml").write_text("<project />", encoding="utf-8")
            filename = "coco-features/coco-feature-web/pom.xml"

            with self.assertRaisesRegex(
                review.ReviewError,
                "Required starter composition context is missing at trusted base",
            ):
                review.build_context(
                    FakeContextClient({filename: "<project />"}),
                    REPOSITORY,
                    {
                        "number": 1,
                        "title": "Feature pom",
                        "body": "",
                        "base": {"sha": BASE_SHA},
                        "head": {"sha": HEAD_SHA},
                    },
                    [
                        {
                            "filename": filename,
                            "status": "modified",
                            "patch": "@@ -1 +1 @@\n-old\n+new",
                        }
                    ],
                    [],
                    "diff --git a/pom.xml b/pom.xml\n+new",
                    root,
                    config(),
                    MODEL_CONFIG_SHA256,
                )

    def test_build_context_adds_complete_starter_context_over_per_file_limit(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Trusted policy", encoding="utf-8")
            (root / "pom.xml").write_text("<project />", encoding="utf-8")
            starter = root / "coco-spring/coco-spring-boot-starter/pom.xml"
            starter.parent.mkdir(parents=True)
            starter_lines = ["<project>"]
            starter_lines.extend(
                f"  <dependency>starter-{index:04d}</dependency>"
                for index in range(180)
            )
            starter_lines.append("</project>")
            starter_content = "\n".join(starter_lines)
            starter.write_text(starter_content, encoding="utf-8")
            filename = "coco-features/coco-feature-web/pom.xml"
            starter_context = review.numbered_text(starter_content)
            self.assertGreater(len(starter_context), 4_000)
            self.assertLess(len(starter_context), 12_000)

            context = review.build_context(
                FakeContextClient({filename: "<project />"}),
                REPOSITORY,
                {
                    "number": 1,
                    "title": "Feature pom",
                    "body": "",
                    "base": {"sha": BASE_SHA},
                    "head": {"sha": HEAD_SHA},
                },
                [
                    {
                        "filename": filename,
                        "status": "modified",
                        "patch": "@@ -1 +1 @@\n-old\n+new",
                    }
                ],
                [],
                "diff --git a/pom.xml b/pom.xml\n+new",
                root,
                config(per_file_chars=4_000, full_file_chars=12_000),
                MODEL_CONFIG_SHA256,
            )

            starter_entry = next(
                item
                for item in context["untrusted"]["code_contexts"]
                if item["source"] == "coco-spring/coco-spring-boot-starter/pom.xml"
            )
            self.assertEqual(starter_context, starter_entry["content"])
            self.assertIn(
                f"{len(starter_lines):6d} </project>", starter_entry["content"]
            )

    def test_build_context_rejects_starter_context_over_full_file_limit(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Trusted policy", encoding="utf-8")
            (root / "pom.xml").write_text("<project />", encoding="utf-8")
            starter = root / "coco-spring/coco-spring-boot-starter/pom.xml"
            starter.parent.mkdir(parents=True)
            starter.write_text(
                "<project>\n  <artifactId>starter</artifactId>\n</project>",
                encoding="utf-8",
            )
            filename = "coco-features/coco-feature-web/pom.xml"

            with self.assertRaisesRegex(
                review.ReviewError,
                "Required starter composition context exceeds the full-file context limit",
            ):
                review.build_context(
                    FakeContextClient({filename: "<project />"}),
                    REPOSITORY,
                    {
                        "number": 1,
                        "title": "Feature pom",
                        "body": "",
                        "base": {"sha": BASE_SHA},
                        "head": {"sha": HEAD_SHA},
                    },
                    [
                        {
                            "filename": filename,
                            "status": "modified",
                            "patch": "@@ -1 +1 @@\n-old\n+new",
                        }
                    ],
                    [],
                    "diff --git a/pom.xml b/pom.xml\n+new",
                    root,
                    config(full_file_chars=1),
                    MODEL_CONFIG_SHA256,
                )

    def test_build_context_rejects_starter_context_over_total_budget(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Trusted policy", encoding="utf-8")
            (root / "pom.xml").write_text("<project />", encoding="utf-8")
            starter = root / "coco-spring/coco-spring-boot-starter/pom.xml"
            starter.parent.mkdir(parents=True)
            starter.write_text(
                "<project>\n  <artifactId>starter</artifactId>\n</project>",
                encoding="utf-8",
            )
            filename = "coco-features/coco-feature-web/pom.xml"

            with self.assertRaisesRegex(
                review.ReviewError,
                "Required starter composition context exceeds the remaining code context budget",
            ):
                review.build_context(
                    FakeContextClient({filename: "<project />"}),
                    REPOSITORY,
                    {
                        "number": 1,
                        "title": "Feature pom",
                        "body": "",
                        "base": {"sha": BASE_SHA},
                        "head": {"sha": HEAD_SHA},
                    },
                    [
                        {
                            "filename": filename,
                            "status": "modified",
                            "patch": "@@ -1 +1 @@\n-old\n+new",
                        }
                    ],
                    [],
                    "diff --git a/pom.xml b/pom.xml\n+new",
                    root,
                    config(code_context_chars=1),
                    MODEL_CONFIG_SHA256,
                )

    def test_build_context_deduplicates_starter_pom_for_unordered_feature_changes(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Trusted policy", encoding="utf-8")
            (root / "pom.xml").write_text("<project />", encoding="utf-8")
            starter = root / "coco-spring/coco-spring-boot-starter/pom.xml"
            starter.parent.mkdir(parents=True)
            starter.write_text(
                "<project>\n  <artifactId>starter</artifactId>\n</project>",
                encoding="utf-8",
            )
            first = "coco-features/coco-feature-web/pom.xml"
            second = "coco-features/coco-feature-audit/pom.xml"
            entries = [
                {
                    "filename": first,
                    "status": "modified",
                    "patch": "@@ -1 +1 @@\n-old\n+new",
                },
                {
                    "filename": second,
                    "status": "modified",
                    "patch": "@@ -1 +1 @@\n-old\n+new",
                },
                {
                    "filename": first,
                    "status": "modified",
                    "patch": "@@ -1 +1 @@\n-old\n+new",
                },
            ]
            client = FakeContextClient({first: "<project />", second: "<project />"})
            pr = {
                "number": 1,
                "title": "Feature poms",
                "body": "",
                "base": {"sha": BASE_SHA},
                "head": {"sha": HEAD_SHA},
            }
            forward = review.build_context(
                client,
                REPOSITORY,
                pr,
                entries,
                [],
                "diff",
                root,
                config(),
                MODEL_CONFIG_SHA256,
            )
            reverse = review.build_context(
                client,
                REPOSITORY,
                pr,
                list(reversed(entries)),
                [],
                "diff",
                root,
                config(),
                MODEL_CONFIG_SHA256,
            )

            starter_source = "coco-spring/coco-spring-boot-starter/pom.xml"
            forward_sources = [
                item["source"] for item in forward["untrusted"]["code_contexts"]
            ]
            reverse_sources = [
                item["source"] for item in reverse["untrusted"]["code_contexts"]
            ]
            self.assertEqual(forward_sources, reverse_sources)
            self.assertEqual(1, forward_sources.count(starter_source))

    def test_specialist_schema_rejects_starter_pom_without_injected_context(
        self,
    ) -> None:
        context = bound_context()
        report = specialist_report("correctness", context)
        report["findings"][0]["file"] = "coco-spring/coco-spring-boot-starter/pom.xml"

        with self.assertRaisesRegex(review.ReportShapeError, "absent from its context"):
            review.validate_specialist_report(report, "correctness", context, 8)

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
                    MODEL_CONFIG_SHA256,
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
                    MODEL_CONFIG_SHA256,
                )

    def test_collect_policy_keeps_complete_specs_when_they_fit(
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
            omissions = []
            sources = review.collect_policy(
                root,
                config(policy_chars=7)
                | {
                    "context": {
                        "always": ["AGENTS.md"],
                        "path_rules": value["context"]["path_rules"],
                    }
                },
                ["old/Foo.java"],
                omissions,
            )
            self.assertEqual({"AGENTS.md"}, {item["source"] for item in sources})
            self.assertEqual(
                ["trusted policy omitted by budget: docs/old.md"], omissions
            )

    def test_collect_policy_budget_does_not_drop_messaging_or_storage_specs(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("Policy", encoding="utf-8")
            (root / "docs").mkdir()
            (root / "docs/history.md").write_text("history" * 3, encoding="utf-8")
            (root / "docs/messaging.md").write_text("message", encoding="utf-8")
            (root / "docs/storage.md").write_text("store", encoding="utf-8")
            value = config(policy_chars=20)
            value["context"]["path_rules"] = [
                {"patterns": ["coco-features/**"], "files": ["docs/history.md"]},
                {
                    "patterns": ["coco-features/coco-messaging/**"],
                    "files": ["docs/messaging.md"],
                },
                {
                    "patterns": ["coco-features/coco-storage/**"],
                    "files": ["docs/storage.md"],
                },
            ]

            omissions: list[str] = []
            sources = review.collect_policy(
                root,
                value,
                [
                    "coco-features/coco-messaging/pom.xml",
                    "coco-features/coco-storage/pom.xml",
                ],
                omissions,
            )

        self.assertEqual(
            {"AGENTS.md", "docs/messaging.md", "docs/storage.md"},
            {item["source"] for item in sources},
        )
        self.assertEqual(
            ["trusted policy omitted by budget: docs/history.md"], omissions
        )

    def test_collect_policy_fails_closed_for_oversized_protected_policies(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            value = config(policy_chars=10)
            value["protected_policy_paths"] = ["AGENTS.md", "policy.md"]
            value["context"] = {
                "always": ["AGENTS.md", "policy.md"],
                "path_rules": [],
            }

            for oversized in ("AGENTS.md", "policy.md"):
                with self.subTest(oversized=oversized):
                    (root / "AGENTS.md").write_text(
                        "ok" if oversized != "AGENTS.md" else "x" * 11,
                        encoding="utf-8",
                    )
                    (root / "policy.md").write_text(
                        "ok" if oversized != "policy.md" else "x" * 11,
                        encoding="utf-8",
                    )
                    with self.assertRaisesRegex(
                        review.ReviewError, "exceeds the context budget"
                    ):
                        review.collect_policy(root, value, [], [])

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
                    MODEL_CONFIG_SHA256,
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
                MODEL_CONFIG_SHA256,
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
                    MODEL_CONFIG_SHA256,
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
                            MODEL_CONFIG_SHA256,
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
                patch.object(
                    review,
                    "classify_pr_route",
                    return_value=review.PR_ROUTE_DIRECT,
                ),
                patch.object(review, "build_context", return_value=context) as builder,
                patch.object(review.time, "sleep") as sleeper,
                patch("builtins.print"),
                patch.dict(
                    "os.environ",
                    {"GH_TOKEN": "token", **model_env("openai-responses")},
                ),
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
        report = raw_verifier_report("evidence-verifier", context, finding_id)
        review.validate_raw_cross_report(
            report, "evidence-verifier", context, {finding_id}
        )
        self.assertEqual("AGREE", report["reviews"][0]["action"])
        self.assertEqual("COMPLETE", report["status"])
        self.assertNotIn("verifications", report)
        self.assertEqual(
            [
                {
                    "trust_domain": "head-code",
                    "path": "src/Foo.java",
                    "start_line": 1,
                    "end_line": 1,
                    "checks": ["anchor", "claim", "impact", "trigger"],
                },
                {
                    "trust_domain": "protected-policy",
                    "path": "AGENTS.md",
                    "start_line": 1,
                    "end_line": 1,
                    "checks": ["change_scope", "severity"],
                },
            ],
            report["reviews"][0]["evidence_refs"],
        )
        self.assertNotIn("source_id", review.canonical_json(report))

    def test_raw_change_scope_evidence_uses_protected_policy_source(self) -> None:
        context = bound_context()
        report = raw_verifier_report("evidence-verifier", context, "correctness:f1")
        catalog = {
            item["source_id"]: item for item in review.context_evidence_catalog(context)
        }
        policy_source_id = next(
            source_id
            for source_id, item in catalog.items()
            if item["trust_domain"] in review.POLICY_EVIDENCE_DOMAINS
        )
        code_source_id = next(
            source_id
            for source_id, item in catalog.items()
            if item["trust_domain"] == "head-code"
        )
        refs = report["verifications"][0]["evidence_refs"]
        refs[0]["checks"] = ["anchor", "claim", "impact", "trigger"]
        refs[1]["source_id"] = policy_source_id
        refs[1]["checks"] = ["change_scope", "severity"]
        self.assertNotEqual(policy_source_id, code_source_id)
        review.validate_raw_cross_report(
            report, "evidence-verifier", context, {"correctness:f1"}
        )
        self.assertEqual(
            "protected-policy",
            report["reviews"][0]["evidence_refs"][1]["trust_domain"],
        )

    def test_persisted_normalized_cross_report_revalidates_without_format_change(
        self,
    ) -> None:
        context = bound_context()
        report = verifier_report("evidence-verifier", context, "correctness:f1")
        expected = json.loads(json.dumps(report))

        review.validate_cross_report(
            report, "evidence-verifier", context, {"correctness:f1"}
        )

        self.assertEqual(expected, report)

    def test_raw_evidence_reference_rejects_domain_and_path_fields(self) -> None:
        context = bound_context()
        report = raw_verifier_report("evidence-verifier", context, "correctness:f1")
        reference = report["verifications"][0]["evidence_refs"][0]
        reference.pop("source_id")
        reference.update({"trust_domain": "head-code", "path": "src/Foo.java"})

        with self.assertRaisesRegex(review.ReportShapeError, "schema fields mismatch"):
            review.validate_raw_cross_report(
                report, "evidence-verifier", context, {"correctness:f1"}
            )

    def test_normalized_cross_validator_rejects_raw_envelope(self) -> None:
        context = bound_context()
        report = raw_verifier_report("evidence-verifier", context, "correctness:f1")

        with self.assertRaisesRegex(review.ReportShapeError, "schema fields mismatch"):
            review.validate_cross_report(
                report, "evidence-verifier", context, {"correctness:f1"}
            )

    def test_raw_cross_validator_checks_binding_before_envelope_shape(self) -> None:
        context = bound_context()
        report = verifier_report("evidence-verifier", context, "correctness:f1")
        report["head_sha"] = "c" * 40

        with self.assertRaisesRegex(review.ReviewError, "binding mismatch") as raised:
            review.validate_raw_cross_report(
                report, "evidence-verifier", context, {"correctness:f1"}
            )

        self.assertNotIsInstance(raised.exception, review.ReportShapeError)

    def test_cross_review_contract_text_limits_verifiers_to_blockers(self) -> None:
        root = Path(__file__).resolve().parents[2]
        prompt = (root / ".github/agent-review/prompts/cross-review.md").read_text(
            encoding="utf-8"
        )
        spec = (
            root / "coco-support/coco-document/superpowers/specs/"
            "2026-07-10-multi-agent-review-jury.md"
        ).read_text(encoding="utf-8")

        self.assertIn("every supplied P0/P1 blocker candidate", prompt)
        self.assertIn("P2/P3 candidates are not supplied to verifier calls.", prompt)
        self.assertIn(
            "A\ncontinuity call is required whenever the supplied `current_groups` array is\nnon-empty",
            prompt,
        )
        self.assertIn(
            "always return the complete schema-v2 `relationships` report", prompt
        )
        self.assertIn(
            "ordinary cross-review\ncoordinator does not call you when there are no P0/P1 candidates",
            prompt,
        )
        self.assertIn(
            "That ordinary rule does not apply to a\ncontinuity call with any supplied current group",
            prompt,
        )
        self.assertIn("canonical evidence source catalog", prompt)
        self.assertIn("copy only its `source_id`", prompt)
        self.assertIn("Never output `trust_domain` or `path`", prompt)
        self.assertIn("只覆盖全部 P0/P1 finding", spec)
        self.assertIn("两个 verifier 均为零模型调用", spec)
        self.assertIn("P2/P3 不进入 verifier", spec)
        self.assertIn("由 chair 选中的 P2/P3 评论/Issue follow-up", spec)
        self.assertIn("P0/P1 的两个 verifier 显式状态", spec)
        self.assertIn("P2/P3 的\nspecialist/chair 非阻断状态", spec)
        self.assertIn("context_evidence_sources(context)", spec)
        self.assertIn("raw仅收ID/行区间/checks的`verifications`", spec)
        self.assertIn("发布/下游仅收normalized `reviews/status`", spec)
        for obsolete in (
            "P0/P1/P2/P3 severity",
            "必须覆盖全部 P0/P1/P2/P3 finding",
            "不能省略该席位的模型调用",
            "所有 P0/P1/P2/P3 finding 都需要双重独立验证",
            "P2/P3 双 `AGREE` actionable",
            "选择非双 `AGREE` P2/P3",
            "非阻断 findings、双 `AGREE` actionable follow-up",
            "全部 finding 的 disposition、两个 verifier 的显式状态",
        ):
            with self.subTest(obsolete=obsolete):
                self.assertNotIn(obsolete, prompt + spec)

        compact_view = spec.split("确定性切换到 compact\n视图，", 1)[1].split(
            "\n追加 actionable", 1
        )[0]
        self.assertNotRegex(
            compact_view,
            r"(?:全部|所有) finding(?:(?!P0/P1|P2/P3).){0,160}两个 verifier",
        )
        self.assertNotRegex(
            spec,
            r"P2/P3(?:(?!chair|非阻断).){0,160}(?:双|两个|2 个)\s*`?AGREE`?",
        )

    def test_cross_review_continuity_role_contract_requires_exact_role_identity(
        self,
    ) -> None:
        root = Path(__file__).resolve().parents[2]
        prompt = (root / ".github/agent-review/prompts/cross-review.md").read_text(
            encoding="utf-8"
        )
        normalized_prompt = " ".join(prompt.split())

        self.assertIn('"role": "<exact-protected-task-role-id>"', prompt)
        self.assertIn(
            '"schema_version": 2',
            normalized_prompt,
        )
        self.assertIn(
            '"relationships": [',
            normalized_prompt,
        )
        self.assertIn(
            "The coordinator binds the normalized report identity to the protected task role",
            normalized_prompt,
        )
        self.assertIn(
            "For ordinary reports, copy `role` verbatim from the protected task metadata.",
            normalized_prompt,
        )
        self.assertNotIn('"role": "evidence-verifier|policy-skeptic"', prompt)
        self.assertIn("`action` is the relationship type", normalized_prompt)
        self.assertIn(
            "`candidate_sha256`, `previous_group_id`, `previous_issue_number`, and `previous_anchor` fields must all be JSON `null`",
            normalized_prompt,
        )
        self.assertIn('"candidate_sha256": null', prompt)
        self.assertIn('"previous_anchor": null', prompt)

    def test_cross_review_writes_exact_not_needed_without_model_when_no_findings(
        self,
    ) -> None:
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
            with (
                patch.object(review, "AgentModelClient") as client_class,
                patch.dict("os.environ", model_env("openai-responses"), clear=True),
            ):
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
                client_class.return_value.complete.assert_not_called()
            self.assertEqual(0, result)
            output = review.read_json(output_path)
            self.assertEqual("NOT_NEEDED", output["status"])
            self.assertEqual(HEAD_SHA, output["head_sha"])
            self.assertEqual(
                context["binding"]["context_sha256"], output["context_sha256"]
            )

    def test_cross_review_skips_model_for_p2_p3_only_findings(self) -> None:
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
                report = specialist_report(role, context, severity="P3")
                if role != "correctness":
                    report["findings"] = []
                review.write_json(reports / f"{role}.json", report)
            config_path = root / "config.json"
            context_path = root / "context.json"
            output_path = root / "verifier.json"
            review.write_json(config_path, config())
            review.write_json(context_path, context)
            with (
                patch.object(review, "AgentModelClient") as client_class,
                patch.dict("os.environ", model_env("openai-responses"), clear=True),
            ):
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
                client_class.return_value.complete.assert_not_called()
            self.assertEqual(0, result)
            output = review.read_json(output_path)
            self.assertEqual("NOT_NEEDED", output["status"])
            self.assertEqual([], output["reviews"])

    def test_cross_review_sends_only_p0_p1_claims_for_mixed_findings(self) -> None:
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
            p1 = specialist_report("correctness", context, severity="P1")
            p2 = specialist_report("architecture-api", context, severity="P2")
            review.write_json(reports / "correctness.json", p1)
            review.write_json(reports / "architecture-api.json", p2)
            for role in ("security-isolation", "tests-release", "robustness-blind"):
                report = specialist_report(role, context)
                report["findings"] = []
                review.write_json(reports / f"{role}.json", report)
            config_path = root / "config.json"
            context_path = root / "context.json"
            output_path = root / "verifier.json"
            review.write_json(config_path, config())
            review.write_json(context_path, context)
            model_output = raw_verifier_report(
                "evidence-verifier", context, "correctness:f1"
            )
            with (
                patch.object(review, "AgentModelClient") as client_class,
                patch.dict("os.environ", model_env("openai-responses"), clear=True),
            ):
                client_class.return_value.complete.return_value = model_output
                self.assertEqual(
                    0,
                    review.command_cross(
                        SimpleNamespace(
                            role="evidence-verifier",
                            config=config_path,
                            prompt_root=prompt_root,
                            context=context_path,
                            reports=reports,
                            output=output_path,
                        )
                    ),
                )
                user = json.loads(client_class.return_value.complete.call_args.args[1])
            self.assertEqual(
                ["correctness:f1"], [item["id"] for item in user["claims"]]
            )
            self.assertEqual(
                "correctness:f1",
                review.read_json(output_path)["reviews"][0]["finding_id"],
            )

    def test_command_cross_repairs_normalized_envelope_then_accepts_raw(self) -> None:
        context = bound_context()
        normalized = verifier_report("evidence-verifier", context, "correctness:f1")
        raw = raw_verifier_report("evidence-verifier", context, "correctness:f1")
        expected = verifier_report("evidence-verifier", context, "correctness:f1")
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
                if role != "correctness":
                    report["findings"] = []
                review.write_json(reports / f"{role}.json", report)
            config_path = root / "config.json"
            context_path = root / "context.json"
            output_path = root / "verifier.json"
            review.write_json(config_path, config())
            review.write_json(context_path, context)
            with (
                patch.object(review, "AgentModelClient") as client_class,
                patch.dict("os.environ", model_env("openai-responses"), clear=True),
                patch("builtins.print"),
            ):
                client_class.return_value.complete.side_effect = [normalized, raw]
                self.assertEqual(
                    0,
                    review.command_cross(
                        SimpleNamespace(
                            role="evidence-verifier",
                            config=config_path,
                            prompt_root=prompt_root,
                            context=context_path,
                            reports=reports,
                            output=output_path,
                        )
                    ),
                )
                calls = client_class.return_value.complete.call_args_list
                output = review.read_json(output_path)

        self.assertEqual(2, len(calls))
        correction = json.loads(calls[1].args[1])
        self.assertEqual(
            {"original_task", "previous_response_sha256", "validator_message"},
            set(correction),
        )
        self.assertEqual(
            review.sha256_text(review.canonical_json(normalized)),
            correction["previous_response_sha256"],
        )
        self.assertNotIn("previous_response", correction)
        self.assertIn("schema fields mismatch", correction["validator_message"])
        self.assertEqual(expected, output)

    def test_command_cross_repairs_evidence_verifier_change_scope_domain(self) -> None:
        context = bound_context()
        invalid = raw_verifier_report("evidence-verifier", context, "correctness:f1")
        invalid_refs = invalid["verifications"][0]["evidence_refs"]
        invalid_refs[0]["checks"].append("change_scope")
        invalid_refs[1]["checks"].remove("change_scope")
        valid = raw_verifier_report("evidence-verifier", context, "correctness:f1")
        expected = verifier_report("evidence-verifier", context, "correctness:f1")
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
                if role != "correctness":
                    report["findings"] = []
                review.write_json(reports / f"{role}.json", report)
            config_path = root / "config.json"
            context_path = root / "context.json"
            output_path = root / "verifier.json"
            review.write_json(config_path, config())
            review.write_json(context_path, context)
            with (
                patch.object(review, "AgentModelClient") as client_class,
                patch.dict("os.environ", model_env("openai-responses"), clear=True),
                patch("builtins.print"),
            ):
                client_class.return_value.complete.side_effect = [invalid, valid]
                self.assertEqual(
                    0,
                    review.command_cross(
                        SimpleNamespace(
                            role="evidence-verifier",
                            config=config_path,
                            prompt_root=prompt_root,
                            context=context_path,
                            reports=reports,
                            output=output_path,
                        )
                    ),
                )
                calls = client_class.return_value.complete.call_args_list
                output = review.read_json(output_path)

        self.assertEqual(2, len(calls))
        self.assertIn("verifications[].evidence_refs[].checks", calls[0].args[0])
        correction_system = calls[1].args[0]
        self.assertIn("evidence-verifier", correction_system)
        self.assertIn("verifications[].evidence_refs[].checks", correction_system)
        self.assertIn("protected-policy", correction_system)
        self.assertIn("base-spec", correction_system)
        self.assertIn("head-code", correction_system)
        self.assertIn("base-code", correction_system)
        self.assertIn("PR diff", correction_system)
        correction = json.loads(calls[1].args[1])
        self.assertEqual(
            "Cross-review evidence-verifier change_scope evidence must be protected policy or a base specification.",
            correction["validator_message"],
        )
        self.assertEqual(expected, output)

    def test_command_cross_rejects_three_normalized_envelopes(self) -> None:
        context = bound_context()
        normalized = verifier_report("evidence-verifier", context, "correctness:f1")
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
                if role != "correctness":
                    report["findings"] = []
                review.write_json(reports / f"{role}.json", report)
            config_path = root / "config.json"
            context_path = root / "context.json"
            output_path = root / "verifier.json"
            review.write_json(config_path, config())
            review.write_json(context_path, context)
            with (
                patch.object(review, "AgentModelClient") as client_class,
                patch.dict("os.environ", model_env("openai-responses"), clear=True),
                patch("builtins.print"),
            ):
                client_class.return_value.complete.return_value = normalized
                with self.assertRaisesRegex(
                    review.ReportShapeError, "schema fields mismatch"
                ):
                    review.command_cross(
                        SimpleNamespace(
                            role="evidence-verifier",
                            config=config_path,
                            prompt_root=prompt_root,
                            context=context_path,
                            reports=reports,
                            output=output_path,
                        )
                    )
                calls = client_class.return_value.complete.call_args_list

        self.assertEqual(review.MODEL_COMPLETION_MAX_ATTEMPTS, len(calls))
        for call in calls[1:]:
            correction = json.loads(call.args[1])
            self.assertNotIn("previous_response", correction)
            self.assertIn("previous_response_sha256", correction)
            self.assertIn("schema fields mismatch", correction["validator_message"])

    def test_cross_review_repairs_unknown_source_id_and_normalizes_output(
        self,
    ) -> None:
        context = bound_context()
        invalid = raw_verifier_report("evidence-verifier", context, "correctness:f1")
        invalid["verifications"][0]["evidence_refs"][0]["source_id"] = "S999"
        valid = raw_verifier_report("evidence-verifier", context, "correctness:f1")
        expected = verifier_report("evidence-verifier", context, "correctness:f1")
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
                if role != "correctness":
                    report["findings"] = []
                review.write_json(reports / f"{role}.json", report)
            config_path = root / "config.json"
            context_path = root / "context.json"
            output_path = root / "verifier.json"
            review.write_json(config_path, config())
            review.write_json(context_path, context)
            with (
                patch.object(review, "AgentModelClient") as client_class,
                patch.dict("os.environ", model_env("openai-responses"), clear=True),
            ):
                client_class.return_value.complete.side_effect = [invalid, valid]
                self.assertEqual(
                    0,
                    review.command_cross(
                        SimpleNamespace(
                            role="evidence-verifier",
                            config=config_path,
                            prompt_root=prompt_root,
                            context=context_path,
                            reports=reports,
                            output=output_path,
                        )
                    ),
                )
                calls = client_class.return_value.complete.call_args_list
                output = review.read_json(output_path)

        self.assertEqual(2, len(calls))
        catalog = review.canonical_json(review.context_evidence_catalog(context))
        self.assertIn("Protected canonical evidence source catalog", calls[0].args[0])
        self.assertIn(catalog, calls[0].args[0])
        self.assertIn(catalog, calls[1].args[0])
        self.assertNotIn("class Foo", calls[0].args[0])
        correction = json.loads(calls[1].args[1])
        self.assertEqual(
            {"original_task", "previous_response_sha256", "validator_message"},
            set(correction),
        )
        self.assertNotIn("previous_response", correction)
        self.assertNotIn("S999", calls[1].args[1])
        self.assertEqual(expected, output)
        self.assertEqual(
            "head-code", output["reviews"][0]["evidence_refs"][0]["trust_domain"]
        )
        self.assertEqual(
            "src/Foo.java", output["reviews"][0]["evidence_refs"][0]["path"]
        )
        self.assertNotIn("source_id", review.canonical_json(output))

    def test_nonblocking_findings_remain_publishable_without_verifier_votes(
        self,
    ) -> None:
        context = bound_context()
        p1_report = specialist_report("correctness", context, severity="P1")
        p2_report = specialist_report("architecture-api", context, severity="P2")
        p3_report = specialist_report("tests-release", context, severity="P3")
        p1 = p1_report["findings"][0]
        p2 = p2_report["findings"][0]
        p3 = p3_report["findings"][0]
        verifiers = [
            verifier_report("evidence-verifier", context, p1["id"]),
            verifier_report("policy-skeptic", context, p1["id"]),
        ]
        consensus = review.compute_consensus(
            [p1_report, p2_report, p3_report], verifiers
        )
        self.assertEqual(
            {p2["id"], p3["id"]},
            {
                item["finding"]["id"]
                for item in consensus["unverified"]
                if item["finding"]["severity"] in {"P2", "P3"}
            },
        )
        chair = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "BLOCK",
            "summary": "The deterministic consensus confirms the blocking finding.",
            "confirmed_blocker_ids": [p1["id"]],
            "actionable_groups": [
                {"primary_finding_id": p1["id"], "duplicate_finding_ids": []},
                {"primary_finding_id": p2["id"], "duplicate_finding_ids": []},
                {"primary_finding_id": p3["id"], "duplicate_finding_ids": []},
            ],
            "questions": [],
        }
        review.validate_chair(chair, consensus, context)
        final = {"consensus": consensus, "chair": chair}
        actionable = review.actionable_findings(
            final, [p1_report, p2_report, p3_report]
        )
        self.assertEqual(
            {p1["id"], p2["id"], p3["id"]},
            {item["source_id"] for item in actionable},
        )
        markdown = review.render_review(
            context, [p1_report, p2_report, p3_report], verifiers, consensus, chair
        )
        self.assertIn(p2["id"], markdown)
        self.assertIn(p3["id"], markdown)

    def test_evidence_reference_checks_are_losslessly_canonicalized(self) -> None:
        context = bound_context()
        report = verifier_report("evidence-verifier", context, "correctness:f1")
        report["reviews"][0]["evidence_refs"][0]["checks"] = [
            "trigger",
            "claim",
            "anchor",
            "claim",
            "impact",
        ]
        review.validate_cross_report(
            report, "evidence-verifier", context, {"correctness:f1"}
        )
        self.assertEqual(
            ["anchor", "claim", "impact", "trigger"],
            report["reviews"][0]["evidence_refs"][0]["checks"],
        )

    def test_unknown_evidence_check_is_a_correctable_report_shape_error(self) -> None:
        context = bound_context()
        report = verifier_report("evidence-verifier", context, "correctness:f1")
        report["reviews"][0]["evidence_refs"][0]["checks"] = ["unknown"]
        with self.assertRaisesRegex(review.ReportShapeError, "unsupported field"):
            review.validate_cross_report(
                report, "evidence-verifier", context, {"correctness:f1"}
            )

    def test_cross_review_schema_rejects_extra_fields(self) -> None:
        context = bound_context()
        finding_id = "correctness:f1"
        report = verifier_report("evidence-verifier", context, finding_id)
        report["reviews"][0]["confidence"] = 99
        with self.assertRaises(review.ReportShapeError):
            review.validate_cross_report(
                report, "evidence-verifier", context, {finding_id}
            )

    def test_cross_review_rejects_action_that_contradicts_structured_checks(
        self,
    ) -> None:
        context = bound_context()
        report = verifier_report("evidence-verifier", context, "correctness:f1")
        report["reviews"][0]["action"] = "DISAGREE"
        with self.assertRaisesRegex(
            review.ReportShapeError, "contradicts its structured"
        ):
            review.validate_cross_report(
                report, "evidence-verifier", context, {"correctness:f1"}
            )

    def test_cross_review_rejects_head_code_as_policy_evidence(self) -> None:
        context = bound_context()
        report = verifier_report("policy-skeptic", context, "correctness:f1")
        report["reviews"][0]["evidence_refs"][1].update(
            {"trust_domain": "head-code", "path": "src/Foo.java"}
        )
        report["reviews"][0]["evidence"] = (
            "head-code:src/Foo.java#L1-L1; head-code:AGENTS.md#L1-L1"
        )
        with self.assertRaisesRegex(review.ReviewError, "protected policy"):
            review.validate_cross_report(
                report, "policy-skeptic", context, {"correctness:f1"}
            )

    def test_evidence_verifier_rejects_head_code_for_policy_checks(self) -> None:
        context = bound_context()
        report = verifier_report("evidence-verifier", context, "correctness:f1")
        report["reviews"][0]["evidence_refs"][0]["checks"] = ["severity"]
        report["reviews"][0]["evidence_refs"][1]["checks"] = ["change_scope"]
        report["reviews"][0]["evidence"] = (
            "head-code:src/Foo.java#L1-L1; protected-policy:AGENTS.md#L1-L1"
        )
        with self.assertRaisesRegex(review.ReviewError, "protected policy"):
            review.validate_cross_report(
                report, "evidence-verifier", context, {"correctness:f1"}
            )

    def test_raw_source_id_authorization_error_does_not_enter_shape_repair(
        self,
    ) -> None:
        context = bound_context()
        report = raw_verifier_report("evidence-verifier", context, "correctness:f1")
        report["verifications"][0]["evidence_refs"][0]["checks"] = ["severity"]
        report["verifications"][0]["evidence_refs"][1]["checks"] = ["change_scope"]

        class FakeClient:
            def __init__(self) -> None:
                self.calls = 0

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                del system, user, max_tokens
                self.calls += 1
                return report

        client = FakeClient()
        with self.assertRaisesRegex(review.ReviewError, "protected policy") as raised:
            review.complete_with_shape_repair(
                client,
                "protected cross-review system",
                '{"task":"cross-review"}',
                100,
                lambda value: review.validate_raw_cross_report(
                    value, "evidence-verifier", context, {"correctness:f1"}
                ),
                cross_review_fresh_retry=True,
            )

        self.assertNotIsInstance(raised.exception, review.ReportShapeError)
        self.assertEqual(1, client.calls)

    def test_evidence_verifier_rejects_head_code_as_change_scope_policy_evidence(
        self,
    ) -> None:
        context = bound_context()
        report = verifier_report("evidence-verifier", context, "correctness:f1")
        report["reviews"][0]["evidence_refs"][0]["checks"] = ["change_scope"]
        report["reviews"][0]["evidence_refs"][1]["checks"] = ["severity"]
        report["reviews"][0]["evidence"] = (
            "head-code:src/Foo.java#L1-L1; protected-policy:AGENTS.md#L1-L1"
        )
        with self.assertRaisesRegex(review.ReviewError, "protected policy"):
            review.validate_cross_report(
                report, "evidence-verifier", context, {"correctness:f1"}
            )

    def test_canonical_policy_and_head_revision_of_same_path_validate_evidence(
        self,
    ) -> None:
        source = "policy.py"

        class FakeClient:
            @staticmethod
            def file_text(
                repository: str,
                path: str,
                ref: str,
                max_bytes: int,
            ) -> str:
                if (repository, path, ref, max_bytes) != (
                    REPOSITORY,
                    source,
                    HEAD_SHA,
                    256000,
                ):
                    raise AssertionError("Unexpected head-code lookup.")
                return "head policy\n"

        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / source).write_text("base policy\n", encoding="utf-8")
            configured = config()
            configured["context"] = {"always": [source], "path_rules": []}
            configured["protected_policy_paths"] = [source]
            files = [
                {
                    "filename": source,
                    "status": "modified",
                    "additions": 1,
                    "deletions": 1,
                    "changes": 2,
                    "patch": "@@ -1 +1 @@\n-base policy\n+head policy",
                }
            ]
            policy = review.collect_policy(root, configured, [source], [])
            code_contexts = review.build_code_contexts(
                FakeClient(),
                REPOSITORY,
                HEAD_SHA,
                root,
                files,
                configured,
                [],
            )

        context = bound_context()
        context["trusted"]["policy"] = policy
        context["untrusted"]["code_contexts"] = code_contexts
        context["binding"]["context_sha256"] = ""
        context = review.bind_context(context)
        evidence_refs = [
            {
                "trust_domain": "head-code",
                "path": source,
                "start_line": 1,
                "end_line": 1,
                "checks": ["anchor", "claim", "impact", "trigger"],
            },
            {
                "trust_domain": "protected-policy",
                "path": source,
                "start_line": 1,
                "end_line": 1,
                "checks": ["change_scope", "severity"],
            },
        ]
        report = verifier_report(
            "evidence-verifier",
            context,
            "correctness:f1",
            evidence_refs=evidence_refs,
        )

        sources = review.context_evidence_sources(context)
        self.assertEqual({1}, sources[("protected-policy", source)])
        self.assertEqual({1}, sources[("head-code", source)])
        review.validate_cross_report(
            report, "evidence-verifier", context, {"correctness:f1"}
        )

    def test_canonical_evidence_catalog_uses_only_verified_sources_without_content(
        self,
    ) -> None:
        context = bound_context()
        verified_sources = {
            ("protected-policy", "z-policy.md"): {1, 2, 5},
            ("head-code", "a-code.java"): {3, 4, 8},
        }
        with patch.object(
            review, "context_evidence_sources", return_value=verified_sources
        ) as sources:
            catalog = review.context_evidence_catalog(context)

        sources.assert_called_once_with(context)
        self.assertEqual(
            [
                {
                    "source_id": "S001",
                    "trust_domain": "head-code",
                    "path": "a-code.java",
                    "available_line_ranges": [[3, 4], [8, 8]],
                },
                {
                    "source_id": "S002",
                    "trust_domain": "protected-policy",
                    "path": "z-policy.md",
                    "available_line_ranges": [[1, 2], [5, 5]],
                },
            ],
            catalog,
        )
        self.assertNotIn("Policy", review.canonical_json(catalog))
        self.assertNotIn("class Foo", review.canonical_json(catalog))

    def test_context_evidence_sources_reject_same_domain_duplicate_or_misrouting(
        self,
    ) -> None:
        for name, mutate in (
            (
                "duplicate-protected-policy",
                lambda context: context["trusted"]["policy"].append(
                    json.loads(json.dumps(context["trusted"]["policy"][0]))
                ),
            ),
            (
                "head-code-in-protected-policy",
                lambda context: context["trusted"]["policy"][0].update(
                    trust_domain="head-code"
                ),
            ),
        ):
            with self.subTest(name=name):
                context = bound_context()
                mutate(context)
                with self.assertRaises(review.ReportShapeError):
                    review.context_evidence_sources(context)

    def test_context_evidence_ranges_allow_gaps_but_reject_gap_references(self) -> None:
        context = bound_context()
        source = context["untrusted"]["code_contexts"][0]
        source.update(
            {
                "line_count": 10,
                "available_line_ranges": [[1, 3], [5, 5], [7, 10]],
                "content": "\n".join(
                    f"{line:6d} line {line}" for line in (1, 2, 3, 5, 7, 8, 9, 10)
                ),
            }
        )
        context["binding"]["context_sha256"] = ""
        context = review.bind_context(context)
        self.assertEqual(
            {1, 2, 3, 5, 7, 8, 9, 10},
            review.context_evidence_sources(context)[("head-code", "src/Foo.java")],
        )
        valid_reference = [
            {
                "trust_domain": "head-code",
                "path": "src/Foo.java",
                "start_line": 5,
                "end_line": 5,
                "checks": ["claim"],
            }
        ]
        report = verifier_report(
            "evidence-verifier",
            context,
            "correctness:f1",
            action="UNVERIFIED",
            evidence_refs=valid_reference,
        )
        review.validate_cross_report(
            report, "evidence-verifier", context, {"correctness:f1"}
        )
        self.assertEqual("UNVERIFIED", report["reviews"][0]["action"])
        gap_reference = json.loads(json.dumps(valid_reference))
        gap_reference[0]["start_line"] = 4
        gap_reference[0]["end_line"] = 4
        report = verifier_report(
            "evidence-verifier",
            context,
            "correctness:f1",
            action="UNVERIFIED",
            evidence_refs=gap_reference,
        )
        with self.assertRaisesRegex(review.ReportShapeError, "canonical line coverage"):
            review.validate_cross_report(
                report, "evidence-verifier", context, {"correctness:f1"}
            )

    def test_partial_evidence_derives_unverified_and_malformed_refs_fail_closed(
        self,
    ) -> None:
        context = bound_context()
        base = verifier_report("evidence-verifier", context, "correctness:f1")
        partial = json.loads(json.dumps(base["reviews"][0]["evidence_refs"]))
        partial[1]["checks"] = ["change_scope"]
        report = verifier_report(
            "evidence-verifier",
            context,
            "correctness:f1",
            action="UNVERIFIED",
            evidence_refs=partial,
        )
        review.validate_cross_report(
            report, "evidence-verifier", context, {"correctness:f1"}
        )
        self.assertEqual("UNVERIFIED", report["reviews"][0]["action"])
        for name, mutate in (
            ("unknown-domain", lambda refs: refs[0].update(trust_domain="unknown")),
            ("invalid-range", lambda refs: refs[0].update(start_line=2, end_line=1)),
            ("outside-range", lambda refs: refs[0].update(start_line=2, end_line=2)),
            ("duplicate", lambda refs: refs.append(json.loads(json.dumps(refs[0])))),
        ):
            with self.subTest(name=name):
                malformed = json.loads(json.dumps(base["reviews"][0]["evidence_refs"]))
                mutate(malformed)
                report = verifier_report(
                    "evidence-verifier",
                    context,
                    "correctness:f1",
                    evidence_refs=malformed,
                )
                with self.assertRaises(review.ReportShapeError):
                    review.validate_cross_report(
                        report, "evidence-verifier", context, {"correctness:f1"}
                    )

    def test_evidence_ref_errors_are_precise_and_do_not_echo_untrusted_values(
        self,
    ) -> None:
        context = bound_context()
        base = verifier_report("evidence-verifier", context, "correctness:f1")
        untrusted_path = "untrusted-path-must-not-appear-in-validator-message"
        cases = (
            (
                "checks",
                lambda refs: refs[0].update(checks=["claim", "untrusted-check"]),
                "checks contain an unsupported field",
            ),
            (
                "line-types",
                lambda refs: refs[0].update(start_line=True),
                "line range must use integer start_line and end_line",
            ),
            (
                "canonical-source",
                lambda refs: refs[0].update(path=untrusted_path),
                "trust_domain and path must name a supplied canonical source",
            ),
            (
                "canonical-range",
                lambda refs: refs[0].update(start_line=999, end_line=999),
                "line range must stay within supplied canonical line coverage",
            ),
        )
        for name, mutate, message in cases:
            with self.subTest(name=name):
                references = json.loads(json.dumps(base["reviews"][0]["evidence_refs"]))
                mutate(references)
                report = verifier_report(
                    "evidence-verifier",
                    context,
                    "correctness:f1",
                    evidence_refs=references,
                )
                with self.assertRaisesRegex(review.ReportShapeError, message) as raised:
                    review.validate_cross_report(
                        report, "evidence-verifier", context, {"correctness:f1"}
                    )
                self.assertNotIn(untrusted_path, str(raised.exception))

    def test_chair_group_member_ids_rejects_malformed_groups(self) -> None:
        cases = (
            {},
            {"actionable_groups": "not-an-array"},
            {"actionable_groups": [None]},
            {"actionable_groups": [{}]},
            {"actionable_groups": [{"duplicate_finding_ids": []}]},
            {
                "actionable_groups": [
                    {"primary_finding_id": "invalid", "duplicate_finding_ids": []}
                ]
            },
            {"actionable_groups": [{"primary_finding_id": "correctness:f1"}]},
            {
                "actionable_groups": [
                    {
                        "primary_finding_id": "correctness:f1",
                        "duplicate_finding_ids": "architecture-api:f1",
                    }
                ]
            },
            {
                "actionable_groups": [
                    {
                        "primary_finding_id": "correctness:f1",
                        "duplicate_finding_ids": ["invalid"],
                    }
                ]
            },
            {
                "actionable_groups": [
                    {
                        "primary_finding_id": "correctness:f1",
                        "duplicate_finding_ids": ["correctness:f1"],
                    }
                ]
            },
            {
                "actionable_groups": [
                    {
                        "primary_finding_id": "correctness:f1",
                        "duplicate_finding_ids": [
                            "correctness:f3",
                            "architecture-api:f1",
                        ],
                    }
                ]
            },
            {
                "actionable_groups": [
                    {
                        "primary_finding_id": "correctness:f1",
                        "duplicate_finding_ids": [
                            "architecture-api:f1",
                            "architecture-api:f1",
                        ],
                    }
                ]
            },
        )
        for chair in cases:
            with self.subTest(chair=chair):
                with self.assertRaises(review.ReportShapeError):
                    review.chair_group_member_ids(chair)

    def test_chair_groups_only_deterministic_duplicates_and_all_blockers(self) -> None:
        context = bound_context()
        first = specialist_report("correctness", context)["findings"][0]
        second = json.loads(json.dumps(first))
        second["id"] = "architecture-api:f1"
        second["title"] = "Equivalent result defect"
        second["start_line"] = 9
        second["end_line"] = 10
        consensus = {
            "confirmed": [{"finding": first}, {"finding": second}],
            "challenged": [],
            "unverified": [],
        }
        chair = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "BLOCK",
            "summary": "Two source reports identify one confirmed blocker.",
            "confirmed_blocker_ids": sorted([first["id"], second["id"]]),
            "actionable_groups": [
                {
                    "primary_finding_id": first["id"],
                    "duplicate_finding_ids": [second["id"]],
                }
            ],
            "questions": [],
        }
        review.validate_chair(chair, consensus, context)
        second["impact"] = "A distinct impact is observed by a separate caller."
        with self.assertRaisesRegex(review.ReportShapeError, "deterministic duplicate"):
            review.validate_chair(chair, consensus, context)

    def test_chair_correction_merges_semantic_identity_duplicated_across_groups(
        self,
    ) -> None:
        context = bound_context()
        first = specialist_report("correctness", context)["findings"][0]
        second = json.loads(json.dumps(first))
        second["id"] = "architecture-api:f1"
        second["title"] = "Equivalent result defect"
        second["start_line"] = 9
        second["end_line"] = 10
        consensus = {
            "confirmed": [{"finding": first}, {"finding": second}],
            "challenged": [],
            "unverified": [],
        }
        base = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "BLOCK",
            "summary": "Two source reports identify one confirmed blocker.",
            "confirmed_blocker_ids": sorted([first["id"], second["id"]]),
            "questions": [],
        }
        duplicated = {
            **base,
            "actionable_groups": [
                {
                    "primary_finding_id": first["id"],
                    "duplicate_finding_ids": [],
                },
                {
                    "primary_finding_id": second["id"],
                    "duplicate_finding_ids": [],
                },
            ],
        }
        corrected = {
            **base,
            "actionable_groups": [
                {
                    "primary_finding_id": first["id"],
                    "duplicate_finding_ids": [second["id"]],
                }
            ],
        }

        class FakeClient:
            def __init__(self) -> None:
                self.responses = [duplicated, corrected]
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return self.responses.pop(0)

        client = FakeClient()
        result = review.complete_with_shape_repair(
            client,
            "Each semantic identity must appear in exactly one actionable group.",
            '{"task":"chair"}',
            100,
            lambda value: review.validate_chair(value, consensus, context),
        )

        self.assertEqual(corrected, result)
        self.assertEqual(2, len(client.calls))
        correction = json.loads(client.calls[1][1])
        self.assertEqual(duplicated, correction["previous_response"])

    def test_actionable_issue_group_limit_has_zero_issue_side_effects(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.reads = 0
                self.writes = 0

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                del path, limit
                self.reads += 1
                return []

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                del method, path, payload
                self.writes += 1
                return {}

        client = FakeClient()
        findings = [{"stable_id": f"v2-{index:064x}"} for index in range(9)]
        with self.assertRaisesRegex(review.ReviewError, "protected limit is 8"):
            review.synchronize_finding_issues(
                client,
                REPOSITORY,
                REPOSITORY_ID,
                60,
                HEAD_SHA,
                findings,
                (1, 1),
                "coco-agent[bot]",
                APP_BOT_ID,
                "https://github.example/runs/1",
                "https://github.example",
                lambda: {},
                max_groups=8,
            )
        self.assertEqual(0, client.reads)
        self.assertEqual(0, client.writes)

    def test_publish_preflights_direct_and_deferred_groups_before_binding_writes(
        self,
    ) -> None:
        class FakeStatusClient:
            def __init__(self) -> None:
                self.reads: list[str] = []
                self.writes: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> dict:
                self.reads.append(path)
                if path == f"repos/{REPOSITORY}/commits/{HEAD_SHA}/status":
                    return combined_ownership_status(42)
                raise AssertionError(
                    f"Actionable group preflight must precede binding read: {path}"
                )

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.writes.append((method, path, payload))
                raise AssertionError(f"Unexpected repository write: {method} {path}")

        class FakeAgentClient:
            def __init__(self) -> None:
                self.writes: list[tuple[str, str, dict]] = []

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.writes.append((method, path, payload))
                raise AssertionError(f"Unexpected repository write: {method} {path}")

        selected_findings = [{"stable_id": f"v2-{index:064x}"} for index in range(2)]
        routes = (
            ("direct", trusted_metadata()),
            (
                "deferred-binding-drift",
                {
                    **trusted_metadata(),
                    "pr_number": DEFERRED_PR_NUMBER,
                    "review_route": review.PR_ROUTE_DEFERRED,
                    "deferred": True,
                    "source_run_id": SOURCE_RUN_ID,
                },
            ),
        )
        for name, route_metadata in routes:
            with self.subTest(route=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                metadata = {
                    **route_metadata,
                    "context_sha256": "c" * 64,
                    "protocol_sha256": "d" * 64,
                }
                metadata_path = root / "metadata.json"
                config_path = root / "config.json"
                context_path = root / "context.json"
                specialists_path = root / "specialists"
                verifiers_path = root / "verifiers"
                continuity_path = root / "continuity"
                final_json_path = root / "final.json"
                final_markdown_path = root / "final.md"
                review.write_json(metadata_path, metadata)
                review.write_json(config_path, {})
                review.write_json(
                    context_path,
                    {
                        "binding": {
                            "head_sha": HEAD_SHA,
                            "base_sha": BASE_SHA,
                            "context_sha256": metadata["context_sha256"],
                            "protocol_sha256": metadata["protocol_sha256"],
                            "model_config_sha256": MODEL_CONFIG_SHA256,
                        }
                    },
                )
                specialists_path.mkdir()
                verifiers_path.mkdir()
                continuity_path.mkdir()
                review.write_json(final_json_path, {"verdict": "PASS"})
                final_markdown_path.write_text("validated report\n", encoding="utf-8")
                status_client = FakeStatusClient()
                agent_client = FakeAgentClient()
                with (
                    patch.object(
                        review,
                        "GitHubClient",
                        side_effect=[status_client, agent_client],
                    ),
                    patch.object(
                        review,
                        "load_config",
                        return_value={"max_actionable_issue_groups": 1},
                    ),
                    patch.object(review, "validate_context"),
                    patch.object(review, "load_reports", return_value=[]),
                    patch.object(review, "continuity_adoptions", return_value={}),
                    patch.object(review, "continuity_groups", return_value=[]),
                    patch.object(
                        review,
                        "validate_final_artifact",
                        return_value="validated report\n",
                    ),
                    patch.object(
                        review, "actionable_findings", return_value=selected_findings
                    ),
                    patch.object(review, "revalidate_model_configuration_if_available"),
                    patch.object(
                        review,
                        "deferred_review_binding",
                        side_effect=review.ReviewError(
                            "Deferred Agent review binding changed before publication."
                        ),
                    ) as deferred_binding,
                    patch.object(
                        review,
                        "managed_comment",
                        side_effect=AssertionError(
                            "group limit must be checked before comment reads"
                        ),
                    ),
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
                    with self.assertRaisesRegex(
                        review.ReviewError, "protected limit is 1"
                    ):
                        review.command_publish(
                            SimpleNamespace(
                                metadata=metadata_path,
                                config=config_path,
                                context=context_path,
                                specialists=specialists_path,
                                verifiers=verifiers_path,
                                final_json=final_json_path,
                                final_markdown=final_markdown_path,
                                continuity=continuity_path,
                                run_url="https://github.example/runs/42",
                            )
                        )

                deferred_binding.assert_not_called()
                self.assertEqual(
                    [f"repos/{REPOSITORY}/commits/{HEAD_SHA}/status"],
                    status_client.reads,
                )
                self.assertEqual([], status_client.writes)
                self.assertEqual([], agent_client.writes)

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
            "actionable_groups": [
                {"primary_finding_id": finding_id, "duplicate_finding_ids": []}
            ],
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
            "actionable_groups": [
                {"primary_finding_id": finding["id"], "duplicate_finding_ids": []}
            ],
            "questions": [],
        }
        with self.assertRaises(review.ReportShapeError):
            review.validate_chair(chair, consensus, context, set())
        with self.assertRaisesRegex(review.ReviewError, "ineligible finding"):
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
            "actionable_groups": [
                {"primary_finding_id": finding_id, "duplicate_finding_ids": []}
            ],
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
            "actionable_groups": [],
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
            "actionable_groups": [],
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
            {
                "schema_version": 1,
                "role": role,
                "head_sha": HEAD_SHA,
                "context_sha256": context["binding"]["context_sha256"],
                "status": "NOT_NEEDED",
                "evidence": "No P0/P1 blocker candidates were present in the bound reports.",
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
            "actionable_groups": [
                {"primary_finding_id": finding_id, "duplicate_finding_ids": []}
            ],
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
        self.assertIn("selected for Issue", markdown)

        tampered = json.loads(json.dumps(final))
        tampered["consensus"]["confirmed"] = tampered["consensus"]["unverified"]
        tampered["consensus"]["unverified"] = []
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
            "chair": {
                "actionable_groups": [
                    {"primary_finding_id": blocker["id"], "duplicate_finding_ids": []},
                    {"primary_finding_id": followup["id"], "duplicate_finding_ids": []},
                ]
            },
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
                with self.assertRaisesRegex(review.ReviewError, "ineligible finding"):
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
        self.assertEqual(
            review.semantic_finding_identity(blocker),
            review.semantic_finding_identity(moved),
        )
        changed_claim = json.loads(json.dumps(blocker))
        changed_claim["claim"] = "A materially different defect claim."
        self.assertNotEqual(
            review.stable_finding_id(blocker), review.stable_finding_id(changed_claim)
        )

    def test_actionable_groups_preserve_confirmed_duplicates_and_reject_mixed_kinds(
        self,
    ) -> None:
        context = bound_context()
        primary_report = specialist_report("correctness", context, severity="P1")
        duplicate_report = specialist_report("architecture-api", context, severity="P1")
        primary = primary_report["findings"][0]
        duplicate = duplicate_report["findings"][0]
        final = {
            "consensus": {
                "confirmed": [{"finding": primary}, {"finding": duplicate}],
                "challenged": [],
                "unverified": [],
            },
            "chair": {
                "actionable_groups": [
                    {
                        "primary_finding_id": primary["id"],
                        "duplicate_finding_ids": [duplicate["id"]],
                    }
                ]
            },
        }
        actionable = review.actionable_findings(
            final, [primary_report, duplicate_report]
        )
        self.assertEqual(1, len(actionable))
        self.assertEqual("confirmed-blocker", actionable[0]["kind"])
        self.assertEqual(
            {primary["id"], duplicate["id"]}, set(actionable[0]["source_ids"])
        )

        followup = json.loads(json.dumps(duplicate))
        followup["id"] = "architecture-api:f2"
        followup["severity"] = "P2"
        mixed = {
            "consensus": {
                "confirmed": [{"finding": primary}, {"finding": followup}],
                "challenged": [],
                "unverified": [],
            },
            "chair": {
                "actionable_groups": [
                    {
                        "primary_finding_id": followup["id"],
                        "duplicate_finding_ids": [primary["id"]],
                    }
                ]
            },
        }
        duplicate_report["findings"].append(followup)
        with self.assertRaisesRegex(review.ReviewError, "mixes finding kinds"):
            review.actionable_findings(mixed, [primary_report, duplicate_report])

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
            REPOSITORY_ID,
            60,
            HEAD_SHA,
            body,
            (101, 1),
            app_login,
            APP_BOT_ID,
            lambda: {},
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
            "stable_id": review.stable_actionable_group_id([current_finding]),
            "source_id": current_finding["id"],
            "duplicate_source_ids": [],
            "kind": "follow-up",
            "finding": current_finding,
        }
        new = {
            "stable_id": review.stable_actionable_group_id([new_finding]),
            "source_id": new_finding["id"],
            "duplicate_source_ids": [],
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
                        "id": len(self.comments),
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
                REPOSITORY_ID,
                60,
                HEAD_SHA,
                [current, new],
                (1, 1),
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

    def test_v1_bound_issue_is_retained_without_automatic_migration(
        self,
    ) -> None:
        app_login = "coco-agent[bot]"
        context = bound_context()
        finding = specialist_report("correctness", context, severity="P1")["findings"][
            0
        ]
        legacy_id = review.stable_finding_id(finding)
        group_id = review.stable_actionable_group_id([finding])
        actionable = {
            "stable_id": group_id,
            "legacy_finding_ids": [legacy_id],
            "source_id": finding["id"],
            "duplicate_source_ids": [],
            "kind": "confirmed-blocker",
            "finding": finding,
        }

        class FakeClient:
            def __init__(self) -> None:
                self.issue = {
                    "number": 11,
                    "title": "old finding",
                    "body": review.finding_issue_marker(60, BASE_SHA, legacy_id)
                    + "\nOld body",
                    "state": "open",
                    "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                    "html_url": "https://github.example/issues/11",
                    "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
                }
                self.writes: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> dict:
                if path.endswith("/labels/agent-review"):
                    return {"name": review.FINDING_ISSUE_LABEL}
                raise AssertionError(f"Unexpected GET path: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if limit != 5000 or "issues?state=all&labels=agent-review" not in path:
                    raise AssertionError(f"Unexpected pagination: {path}")
                return [self.issue]

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.writes.append((method, path, payload))
                if method != "PATCH" or not path.endswith("/issues/11"):
                    raise AssertionError(f"Unexpected write: {method} {path}")
                self.issue.update(payload)
                self.issue["labels"] = [{"name": value} for value in payload["labels"]]
                return self.issue

        client = FakeClient()
        synchronized = review.synchronize_finding_issues(
            client,
            REPOSITORY,
            REPOSITORY_ID,
            60,
            HEAD_SHA,
            [actionable],
            (1, 1),
            app_login,
            APP_BOT_ID,
            "https://github.example/runs/1",
            "https://github.example",
            lambda: {},
            continuity_context={
                "binding": context["binding"],
                "trusted": {"continuity_candidates": []},
            },
            continuity_adopted={},
            continuity_proof_sha256="c" * 64,
        )
        self.assertEqual(1, len(synchronized))
        self.assertTrue(synchronized[0]["retained"])
        self.assertEqual([], client.writes)
        self.assertEqual("open", client.issue["state"])
        marker = review.parse_finding_issue_marker(client.issue["body"])
        self.assertEqual(legacy_id, marker["finding_id"])
        self.assertEqual(BASE_SHA, marker["head_sha"])

    def test_issue_sync_rejects_v1_v2_candidate_ambiguity_before_writes(self) -> None:
        app_login = "coco-agent[bot]"
        context = bound_context()
        finding = specialist_report("correctness", context, severity="P1")["findings"][
            0
        ]
        legacy_id = review.stable_finding_id(finding)
        group_id = review.stable_actionable_group_id([finding])
        actionable = {
            "stable_id": group_id,
            "legacy_finding_ids": [legacy_id],
            "source_id": finding["id"],
            "duplicate_source_ids": [],
            "kind": "confirmed-blocker",
            "finding": finding,
        }

        def issue(number: int, finding_id: str) -> dict:
            return {
                "number": number,
                "title": "old finding",
                "body": review.finding_issue_marker(60, BASE_SHA, finding_id),
                "state": "open",
                "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                "html_url": f"https://github.example/issues/{number}",
                "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
            }

        class FakeClient:
            def __init__(self) -> None:
                self.label_reads = 0
                self.writes: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> dict:
                self.label_reads += 1
                raise AssertionError(f"Unexpected label lookup: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if limit != 5000 or "issues?state=all&labels=agent-review" not in path:
                    raise AssertionError(f"Unexpected pagination: {path}")
                return [issue(11, group_id), issue(12, legacy_id)]

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.writes.append((method, path, payload))
                raise AssertionError(f"Unexpected Issue write: {method} {path}")

        client = FakeClient()
        with self.assertRaisesRegex(
            review.ReviewError, "Multiple managed Issues match"
        ):
            review.synchronize_finding_issues(
                client,
                REPOSITORY,
                REPOSITORY_ID,
                60,
                HEAD_SHA,
                [actionable],
                (1, 1),
                app_login,
                APP_BOT_ID,
                "https://github.example/runs/1",
                "https://github.example",
                lambda: {},
            )
        self.assertEqual(0, client.label_reads)
        self.assertEqual([], client.writes)

    def test_issue_sync_rejects_two_v2_groups_claiming_one_legacy_issue_before_writes(
        self,
    ) -> None:
        app_login = "coco-agent[bot]"
        context = bound_context()
        first = specialist_report("correctness", context, severity="P1")["findings"][0]
        second = json.loads(json.dumps(first))
        second["id"] = "architecture-api:f1"
        second["claim"] = "A separate defect has a distinct trigger."
        second["trigger"] = "Call the method after a configuration reload."
        legacy_id = review.stable_finding_id(first)
        actionables = [
            {
                "stable_id": review.stable_actionable_group_id([first]),
                "legacy_finding_ids": [legacy_id],
                "source_id": first["id"],
                "duplicate_source_ids": [],
                "kind": "confirmed-blocker",
                "finding": first,
            },
            {
                "stable_id": review.stable_actionable_group_id([second]),
                "legacy_finding_ids": [legacy_id],
                "source_id": second["id"],
                "duplicate_source_ids": [],
                "kind": "confirmed-blocker",
                "finding": second,
            },
        ]

        class FakeClient:
            def __init__(self) -> None:
                self.label_reads = 0
                self.writes: list[tuple[str, str, dict]] = []
                self.issue = {
                    "number": 11,
                    "body": review.finding_issue_marker(60, BASE_SHA, legacy_id),
                    "state": "open",
                    "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                    "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
                }

            def get_json(self, path: str) -> dict:
                self.label_reads += 1
                raise AssertionError(f"Unexpected label lookup: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if limit != 5000 or "issues?state=all&labels=agent-review" not in path:
                    raise AssertionError(f"Unexpected pagination: {path}")
                return [self.issue]

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.writes.append((method, path, payload))
                raise AssertionError(f"Unexpected Issue write: {method} {path}")

        client = FakeClient()
        with self.assertRaisesRegex(review.ReviewError, "multiple actionable groups"):
            review.synchronize_finding_issues(
                client,
                REPOSITORY,
                REPOSITORY_ID,
                60,
                HEAD_SHA,
                actionables,
                (1, 1),
                app_login,
                APP_BOT_ID,
                "https://github.example/runs/1",
                "https://github.example",
                lambda: {},
            )
        self.assertEqual(0, client.label_reads)
        self.assertEqual([], client.writes)

    def test_issue_lookup_ignores_cross_pr_open_and_closed_id_alias_matches(
        self,
    ) -> None:
        app_login = "coco-agent[bot]"
        context = bound_context()
        finding = specialist_report("correctness", context, severity="P1")["findings"][
            0
        ]
        legacy_id = review.stable_finding_id(finding)
        group_id = review.stable_actionable_group_id([finding])

        class FakeClient:
            @staticmethod
            def paginate(path: str, limit: int = 1000) -> list[dict]:
                if limit != 5000 or "issues?state=all&labels=agent-review" not in path:
                    raise AssertionError(f"Unexpected pagination: {path}")
                return [
                    {
                        "number": 11,
                        "body": review.finding_issue_marker(61, BASE_SHA, legacy_id),
                        "state": "open",
                        "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                        "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
                    },
                    {
                        "number": 12,
                        "body": review.finding_issue_marker(62, BASE_SHA, group_id),
                        "state": "closed",
                        "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                        "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
                    },
                ]

        self.assertEqual(
            {},
            review.app_finding_issues(
                FakeClient(), REPOSITORY, 60, app_login, APP_BOT_ID
            ),
        )

    def test_v1_aliases_are_retained_without_automatic_migration(self) -> None:
        app_login = "coco-agent[bot]"
        context = bound_context()
        first = specialist_report("correctness", context, severity="P1")["findings"][0]
        second = json.loads(json.dumps(first))
        second["id"] = "architecture-api:f1"
        second["claim"] = "A separately confirmed defect has a different trigger."
        second["trigger"] = "Call the method with a non-empty input collection."
        first_legacy_id = review.stable_finding_id(first)
        second_legacy_id = review.stable_finding_id(second)
        first_group_id = review.stable_actionable_group_id([first])
        second_group_id = review.stable_actionable_group_id([second])
        actionables = [
            {
                "stable_id": first_group_id,
                "legacy_finding_ids": [first_legacy_id],
                "source_id": first["id"],
                "duplicate_source_ids": [],
                "kind": "confirmed-blocker",
                "finding": first,
            },
            {
                "stable_id": second_group_id,
                "legacy_finding_ids": [second_legacy_id],
                "source_id": second["id"],
                "duplicate_source_ids": [],
                "kind": "confirmed-blocker",
                "finding": second,
            },
        ]
        first_head = "a" * 40
        second_head = "b" * 40

        def issue(number: int, first_head_sha: str, finding_id: str) -> dict:
            return {
                "number": number,
                "title": "old finding",
                "body": review.finding_issue_marker(60, first_head_sha, finding_id),
                "state": "open",
                "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                "html_url": f"https://github.example/issues/{number}",
                "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
            }

        class FakeClient:
            def __init__(self) -> None:
                self.issues = {
                    11: issue(11, first_head, first_legacy_id),
                    12: issue(12, second_head, second_legacy_id),
                }
                self.writes: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> dict:
                if path.endswith("/labels/agent-review"):
                    return {"name": review.FINDING_ISSUE_LABEL}
                raise AssertionError(f"Unexpected GET path: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if limit != 5000 or "issues?state=all&labels=agent-review" not in path:
                    raise AssertionError(f"Unexpected pagination: {path}")
                return list(self.issues.values())

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.writes.append((method, path, payload))
                if method != "PATCH" or "/issues/" not in path:
                    raise AssertionError(f"Unexpected Issue write: {method} {path}")
                value = self.issues[int(path.rsplit("/", 1)[-1])]
                value.update(payload)
                value["labels"] = [{"name": name} for name in payload["labels"]]
                return value

        client = FakeClient()
        synchronized = review.synchronize_finding_issues(
            client,
            REPOSITORY,
            REPOSITORY_ID,
            60,
            HEAD_SHA,
            actionables,
            (1, 1),
            app_login,
            APP_BOT_ID,
            "https://github.example/runs/1",
            "https://github.example",
            lambda: {},
            continuity_context={
                "binding": context["binding"],
                "trusted": {"continuity_candidates": []},
            },
            continuity_adopted={},
            continuity_proof_sha256="c" * 64,
        )
        self.assertEqual(2, len(synchronized))
        self.assertTrue(all(value["retained"] for value in synchronized))
        self.assertEqual([], client.writes)
        first_marker = review.parse_finding_issue_marker(client.issues[11]["body"])
        second_marker = review.parse_finding_issue_marker(client.issues[12]["body"])
        self.assertEqual(first_legacy_id, first_marker["finding_id"])
        self.assertEqual(first_head, first_marker["head_sha"])
        self.assertEqual(second_legacy_id, second_marker["finding_id"])
        self.assertEqual(second_head, second_marker["head_sha"])

    def test_operation_marker_is_canonical_and_rejects_confusable_values(self) -> None:
        marker = review.operation_marker(
            REPOSITORY,
            REPOSITORY_ID,
            "coco-agent[bot]",
            APP_BOT_ID,
            (42, 3),
            60,
            HEAD_SHA,
            "v2-" + "a" * 64,
            "finding-issue-create",
        )
        self.assertEqual(
            {
                "action": "finding-issue-create",
                "app_bot_id": APP_BOT_ID,
                "app_login": "coco-agent[bot]",
                "group_id": "v2-" + "a" * 64,
                "head_sha": HEAD_SHA,
                "pull_request": 60,
                "repository": REPOSITORY,
                "repository_id": REPOSITORY_ID,
                "run_attempt": 3,
                "run_id": 42,
                "schema_version": 1,
            },
            review.parse_operation_marker(marker + "\n"),
        )
        payload = json.loads(marker[len(review.OPERATION_MARKER_PREFIX) : -4])
        malformed = []
        for field, value in (
            ("repository_id", str(REPOSITORY_ID)),
            ("run_id", 0),
            ("action", "anything"),
            ("group_id", "summary"),
            ("head_sha", HEAD_SHA.upper()),
        ):
            changed = {**payload, field: value}
            malformed.append(
                review.OPERATION_MARKER_PREFIX
                + json.dumps(changed, separators=(",", ":"))
                + " -->"
            )
        malformed.extend(
            [
                marker + "\n" + marker,
                marker.replace('"action"', '"extra":1,"action"', 1),
                "text " + marker,
                marker.replace(
                    review.OPERATION_MARKER_PREFIX, review.OPERATION_MARKER_NAMESPACE
                ),
            ]
        )
        for value in malformed:
            with self.subTest(value=value[:80]):
                with self.assertRaises(review.ReviewError):
                    review.parse_operation_marker(value)

    def test_github_write_success_without_usable_json_is_uncertain(self) -> None:
        client = review.GitHubClient("token")
        for body in (b"", b"null", b"not-json", b"\xff"):
            with self.subTest(body=body):
                with patch.object(client, "request", return_value=(body, {})):
                    with self.assertRaises(review.GitHubUncertainWriteResponse):
                        client.send_json("POST", "repos/example/issues", {})
        with self.assertRaises(review.GitHubUncertainWriteResponse):
            review.verify_finding_issue(
                {},
                "coco-agent[bot]",
                APP_BOT_ID,
                review.finding_issue_marker(60, BASE_SHA, "v2-" + "a" * 64),
                "<!-- operation -->",
                "title",
                "body",
                {review.FINDING_ISSUE_LABEL},
                "open",
            )

    def test_write_actor_incomplete_is_uncertain_but_mismatch_is_conflict(self) -> None:
        finding_id = "v2-" + "a" * 64
        finding_marker = review.finding_issue_marker(60, BASE_SHA, finding_id)
        operation = test_operation_marker(finding_id, "finding-issue-update")
        body = f"{finding_marker}\n{operation}\nBody\n"
        issue = {
            **finding_issue_resource(8, finding_marker, operation),
            "title": "Finding",
        }
        comment = managed_comment("Old body")

        def verify_issue(candidate: dict) -> dict:
            return review.verify_finding_issue(
                candidate,
                APP_LOGIN,
                APP_BOT_ID,
                finding_marker,
                operation,
                issue["title"],
                body,
                {review.FINDING_ISSUE_LABEL},
                "open",
                expected_number=8,
            )

        for verifier, resource in (
            (verify_issue, issue),
            (
                lambda candidate: review.verify_finding_issue_snapshot(
                    candidate, issue, APP_LOGIN, APP_BOT_ID
                ),
                issue,
            ),
            (
                lambda candidate: review.verify_managed_comment_snapshot(
                    candidate, comment, APP_LOGIN, APP_BOT_ID
                ),
                comment,
            ),
        ):
            for user in (
                {},
                {"id": APP_BOT_ID},
                {"login": APP_LOGIN, "type": "Bot"},
            ):
                with self.subTest(verifier=verifier, incomplete=user):
                    with self.assertRaises(review.GitHubUncertainWriteResponse):
                        verifier({**resource, "user": user})

            with self.subTest(verifier=verifier, mismatch=True):
                with self.assertRaises(review.ReviewError) as raised:
                    verifier(
                        {
                            **resource,
                            "user": app_actor(APP_BOT_ID + 1, "other-app[bot]"),
                        }
                    )
                self.assertNotIsInstance(
                    raised.exception, review.GitHubUncertainWriteResponse
                )

    def test_uncertain_write_recovery_bounds_transient_lookup_failures(self) -> None:
        checks: list[int] = []
        lookups = 0

        def lookup() -> review.RecoveryProbe:
            nonlocal lookups
            lookups += 1
            if lookups < 3:
                raise review.GitHubTransientError("temporary recovery read failure")
            return review.recovery_exact({"id": 7})

        with patch.object(review.time, "sleep") as sleep:
            value = review.uncertain_write_recovery(
                "managed-comment-update",
                f"repos/{REPOSITORY}/issues/comments/7",
                lambda: checks.append(1) or {},
                lookup,
            )
        self.assertEqual({"id": 7}, value)
        self.assertEqual(3, lookups)
        self.assertEqual(6, len(checks))
        self.assertEqual(
            list(review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS[:2]),
            [call.args[0] for call in sleep.call_args_list],
        )

        checks.clear()
        attempts = len(review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS) + 1
        with patch.object(review.time, "sleep") as sleep:
            with self.assertRaisesRegex(review.ReviewError, "bounded reads"):
                review.uncertain_write_recovery(
                    "managed-comment-update",
                    f"repos/{REPOSITORY}/issues/comments/7",
                    lambda: checks.append(1) or {},
                    lambda: (_ for _ in ()).throw(
                        review.GitHubTransientError("temporary recovery read failure")
                    ),
                )
        self.assertEqual(attempts * 2, len(checks))
        self.assertEqual(
            list(review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS),
            [call.args[0] for call in sleep.call_args_list],
        )

    def test_non_managed_label_write_does_not_enter_operation_recovery(self) -> None:
        class FakeClient:
            def __init__(self) -> None:
                self.sent = 0
                self.reads = 0

            def get_json(self, path: str) -> object:
                self.reads += 1
                raise review.GitHubNotFoundError(path)

            def send_json(self, method: str, path: str, payload: dict) -> object:
                self.sent += 1
                return None

            def paginate(self, path: str, limit: int = 1000) -> list[object]:
                raise AssertionError("Label writes must not attempt operation recovery")

        client = FakeClient()
        with self.assertRaises(review.ReviewError):
            review.ensure_finding_issue_label(client, REPOSITORY)
        self.assertEqual(1, client.sent)
        self.assertEqual(1, client.reads)

    def test_publish_status_requires_exact_endpoint_resource_without_retry(
        self,
    ) -> None:
        api_url = "https://github.example/api/v3"
        description = "d" * 160
        target_url = "https://github.example/runs/42"
        expected = {
            "id": 17,
            "url": f"{api_url}/repos/{REPOSITORY}/statuses/{HEAD_SHA}",
            "context": review.STATUS_CONTEXT,
            "state": "success",
            "description": description[:140],
            "target_url": target_url,
            "creator": {},
            "node_id": "status-node",
        }
        invalid = (
            None,
            {},
            [],
            True,
            1,
            {"id": 17},
            {**expected, "id": 0},
            {**expected, "id": True},
            {**expected, "url": f"{api_url}/repos/{REPOSITORY}/statuses/{BASE_SHA}"},
            {
                **expected,
                "url": f"https://api.github.com/repos/{REPOSITORY}/statuses/{HEAD_SHA}",
            },
            {**expected, "context": [review.STATUS_CONTEXT]},
            {**expected, "state": True},
            {**expected, "context": review.ISSUE_STATUS_CONTEXT},
            {**expected, "state": "failure"},
            {**expected, "description": description},
            {**expected, "target_url": target_url + "/other"},
            {**expected, "creator": "coco-agent[bot]"},
        )

        for response in (*invalid, expected):
            with self.subTest(response=response):

                class FakeClient:
                    def __init__(self) -> None:
                        self.sent = 0
                        self.api_url = api_url

                    def send_json(
                        self, method: str, path: str, payload: dict
                    ) -> object:
                        self.sent += 1
                        return response

                    def paginate(self, path: str, limit: int = 1000) -> list:
                        raise AssertionError(
                            "Commit status writes must not enter operation recovery"
                        )

                client = FakeClient()
                if response is expected:
                    review.publish_status(
                        client,
                        REPOSITORY,
                        HEAD_SHA,
                        "success",
                        description,
                        target_url,
                    )
                else:
                    with self.assertRaises(review.ReviewError):
                        review.publish_status(
                            client,
                            REPOSITORY,
                            HEAD_SHA,
                            "success",
                            description,
                            target_url,
                        )
                self.assertEqual(1, client.sent)

    def test_managed_comment_final_utf8_budget_is_checked_before_write(self) -> None:
        app_login = "coco-agent[bot]"
        run_order = (42, 1)
        run_marker = "<!-- agent-jury-run:42:1 -->"
        base_body = f"{review.COMMENT_MARKER}\n{run_marker}\n\u754c"
        marker = review.operation_marker(
            REPOSITORY,
            REPOSITORY_ID,
            app_login,
            APP_BOT_ID,
            run_order,
            60,
            HEAD_SHA,
            review.MANAGED_COMMENT_GROUP_ID,
            "managed-comment-create",
        )
        base_published = review.insert_operation_marker(base_body, marker, 2)
        remaining = review.MAX_GITHUB_COMMENT_BODY_BYTES - review.utf8_size(
            base_published
        )
        boundary_body = base_body + "x" * remaining
        overflow_body = boundary_body + "\u754c"
        self.assertEqual(
            review.MAX_GITHUB_COMMENT_BODY_BYTES,
            review.utf8_size(review.insert_operation_marker(boundary_body, marker, 2)),
        )
        self.assertLessEqual(
            review.utf8_size(overflow_body),
            review.MAX_GITHUB_COMMENT_BODY_BYTES,
        )
        self.assertGreater(
            review.utf8_size(review.insert_operation_marker(overflow_body, marker, 2)),
            review.MAX_GITHUB_COMMENT_BODY_BYTES,
        )

        class FakeClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                return []

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return {
                    "id": 7,
                    "body": payload["body"],
                    "user": {
                        "id": APP_BOT_ID,
                        "login": app_login,
                        "type": "Bot",
                    },
                }

        boundary_client = FakeClient()
        review.upsert_comment(
            boundary_client,
            REPOSITORY,
            REPOSITORY_ID,
            60,
            HEAD_SHA,
            boundary_body,
            run_order,
            app_login,
            APP_BOT_ID,
            lambda: {},
        )
        self.assertEqual(1, len(boundary_client.sent))

        overflow_client = FakeClient()
        with self.assertRaisesRegex(review.ReviewError, "comment budget"):
            review.upsert_comment(
                overflow_client,
                REPOSITORY,
                REPOSITORY_ID,
                60,
                HEAD_SHA,
                overflow_body,
                run_order,
                app_login,
                APP_BOT_ID,
                lambda: {},
            )
        self.assertEqual([], overflow_client.sent)

    def test_legacy_finding_close_body_budget_is_checked_before_any_write(
        self,
    ) -> None:
        app_login = "coco-agent[bot]"
        finding_id = "v1-" + "7" * 64
        marker = review.finding_issue_marker(60, BASE_SHA, finding_id)
        prefix = marker + "\n\u754c"
        body = prefix + "x" * (
            review.MAX_GITHUB_COMMENT_BODY_BYTES - review.utf8_size(prefix)
        )
        close_operation = review.operation_marker(
            REPOSITORY,
            REPOSITORY_ID,
            app_login,
            APP_BOT_ID,
            (42, 1),
            60,
            HEAD_SHA,
            finding_id,
            "finding-issue-close",
        )
        self.assertEqual(review.MAX_GITHUB_COMMENT_BODY_BYTES, review.utf8_size(body))
        self.assertGreater(
            review.utf8_size(review.insert_operation_marker(body, close_operation, 1)),
            review.MAX_GITHUB_COMMENT_BODY_BYTES,
        )

        class FakeClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []
                self.issue = {
                    "number": 9,
                    "title": "Legacy finding",
                    "body": body,
                    "state": "open",
                    "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                    "user": {
                        "id": APP_BOT_ID,
                        "login": app_login,
                        "type": "Bot",
                    },
                }

            def get_json(self, path: str) -> dict:
                if path.endswith("/labels/agent-review"):
                    return {"name": review.FINDING_ISSUE_LABEL}
                raise AssertionError(f"Unexpected GET: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if limit != 5000:
                    raise AssertionError(f"Unexpected paginated read: {path}")
                return [self.issue]

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                raise AssertionError("Close-body overflow must fail before any write")

        client = FakeClient()
        with self.assertRaisesRegex(review.ReviewError, "comment budget"):
            synchronize_test_findings(client, [])
        self.assertEqual([], client.sent)

    def test_uncertain_managed_comment_create_and_update_read_without_rewrite(
        self,
    ) -> None:
        app_login = "coco-agent[bot]"

        for previous, response_mode, action in (
            (None, "empty", "managed-comment-create"),
            (
                {
                    "id": 7,
                    "body": review.COMMENT_MARKER
                    + "\n<!-- agent-jury-run:41:1 -->\nOld\n",
                    "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
                },
                "empty",
                "managed-comment-update",
            ),
            (None, "transient", "managed-comment-create"),
            (
                {
                    "id": 7,
                    "body": review.COMMENT_MARKER
                    + "\n<!-- agent-jury-run:41:1 -->\nOld\n",
                    "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
                },
                "transient",
                "managed-comment-update",
            ),
            (None, "incomplete-actor", "managed-comment-create"),
            (
                {
                    "id": 7,
                    "body": review.COMMENT_MARKER
                    + "\n<!-- agent-jury-run:41:1 -->\nOld\n",
                    "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
                },
                "incomplete-actor",
                "managed-comment-update",
            ),
        ):
            with self.subTest(action=action, response=response_mode):

                class FakeClient:
                    def __init__(self) -> None:
                        self.comments: list[dict] = []
                        self.paginations = 0
                        self.sent: list[tuple[str, str, dict]] = []

                    def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                        self.paginations += 1
                        self.assert_path(path, limit)
                        if previous is None:
                            if self.paginations < 3:
                                return []
                        elif self.paginations == 1:
                            return [previous]
                        return list(self.comments)

                    @staticmethod
                    def assert_path(path: str, limit: int) -> None:
                        if (
                            path != f"repos/{REPOSITORY}/issues/60/comments"
                            or limit != 500
                        ):
                            raise AssertionError(f"Unexpected comments read: {path}")

                    def send_json(
                        self, method: str, path: str, payload: dict
                    ) -> object:
                        self.sent.append((method, path, payload))
                        self.comments = [
                            {
                                "id": 7,
                                "body": payload["body"],
                                "user": {
                                    "id": APP_BOT_ID,
                                    "login": app_login,
                                    "type": "Bot",
                                },
                            }
                        ]
                        if response_mode == "transient":
                            raise review.GitHubTransientError("write outcome unknown")
                        if response_mode == "incomplete-actor":
                            return {**self.comments[0], "user": {}}
                        return None

                client = FakeClient()
                checks: list[int] = []
                body = (
                    review.COMMENT_MARKER + "\n<!-- agent-jury-run:42:1 -->\nResult\n"
                )
                with patch.object(review.time, "sleep"):
                    result = review.upsert_comment(
                        client,
                        REPOSITORY,
                        REPOSITORY_ID,
                        60,
                        HEAD_SHA,
                        body,
                        (42, 1),
                        app_login,
                        APP_BOT_ID,
                        lambda: checks.append(1) or {},
                        previous,
                    )
                self.assertEqual(1, len(client.sent))
                self.assertEqual("PATCH" if previous else "POST", client.sent[0][0])
                self.assertEqual(2 if previous else 3, client.paginations)
                self.assertGreaterEqual(len(checks), 4)
                operation = review.parse_operation_marker(result["body"])
                self.assertEqual(action, operation["action"])
                self.assertEqual(
                    (42, 1), (operation["run_id"], operation["run_attempt"])
                )

    def test_comment_recovery_ignores_untrusted_operation_markers(self) -> None:
        app_login = "coco-agent[bot]"
        body = review.COMMENT_MARKER + "\n<!-- agent-jury-run:42:1 -->\nResult\n"
        wrong_user = {
            "id": APP_BOT_ID + 1,
            "login": "other-app[bot]",
            "type": "Bot",
        }
        trusted_user = {"id": APP_BOT_ID, "login": app_login, "type": "Bot"}

        for marker_case in ("malformed", "matching"):
            with self.subTest(marker=marker_case):

                class FakeClient:
                    def __init__(self) -> None:
                        self.reads = 0
                        self.sent: list[tuple[str, str, dict]] = []

                    def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                        if (
                            path != f"repos/{REPOSITORY}/issues/60/comments"
                            or limit != 500
                        ):
                            raise AssertionError(f"Unexpected comments read: {path}")
                        self.reads += 1
                        if self.reads == 1:
                            return []
                        published = self.sent[0][2]["body"]
                        untrusted_body = (
                            review.OPERATION_MARKER_PREFIX + "not-json -->"
                            if marker_case == "malformed"
                            else published
                        )
                        return [
                            {"id": 6, "body": untrusted_body, "user": wrong_user},
                            {"id": 7, "body": published, "user": trusted_user},
                        ]

                    def send_json(
                        self, method: str, path: str, payload: dict
                    ) -> object:
                        self.sent.append((method, path, payload))
                        raise review.GitHubTransientError("write outcome unknown")

                client = FakeClient()
                result = review.upsert_comment(
                    client,
                    REPOSITORY,
                    REPOSITORY_ID,
                    60,
                    HEAD_SHA,
                    body,
                    (42, 1),
                    app_login,
                    APP_BOT_ID,
                    lambda: {},
                )
                self.assertEqual(7, result["id"])
                self.assertEqual(1, len(client.sent))

    def test_comment_recovery_trusted_malformed_marker_fails_closed(self) -> None:
        app_login = "coco-agent[bot]"

        class FakeClient:
            def __init__(self) -> None:
                self.reads = 0

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if path != f"repos/{REPOSITORY}/issues/60/comments" or limit != 500:
                    raise AssertionError(f"Unexpected comments read: {path}")
                self.reads += 1
                if self.reads == 1:
                    return []
                return [
                    {
                        "id": 7,
                        "body": review.OPERATION_MARKER_PREFIX + "not-json -->",
                        "user": {
                            "id": APP_BOT_ID,
                            "login": app_login,
                            "type": "Bot",
                        },
                    }
                ]

            def send_json(self, method: str, path: str, payload: dict) -> object:
                if (
                    method != "POST"
                    or path != f"repos/{REPOSITORY}/issues/60/comments"
                    or not isinstance(payload.get("body"), str)
                ):
                    raise AssertionError(f"Unexpected comment write: {method} {path}")
                raise review.GitHubTransientError("write outcome unknown")

        with self.assertRaisesRegex(review.ReviewError, "marker JSON"):
            review.upsert_comment(
                FakeClient(),
                REPOSITORY,
                REPOSITORY_ID,
                60,
                HEAD_SHA,
                review.COMMENT_MARKER + "\n<!-- agent-jury-run:42:1 -->\nResult\n",
                (42, 1),
                app_login,
                APP_BOT_ID,
                lambda: {},
            )

    def test_uncertain_issue_closure_comment_and_close_recover_without_rewrite(
        self,
    ) -> None:
        finding_id = "v2-" + "c" * 64
        app_login = "coco-agent[bot]"

        class FakeClient:
            def __init__(self) -> None:
                self.issue = {
                    "number": 9,
                    "title": "Old finding",
                    "body": review.finding_issue_marker(60, BASE_SHA, finding_id)
                    + "\nOld body\n",
                    "state": "open",
                    "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                    "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
                }
                self.pending_issue: dict | None = None
                self.issue_reads = 0
                self.comments: list[dict] = []
                self.sent: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> dict:
                if path.endswith("/labels/agent-review"):
                    return {"name": review.FINDING_ISSUE_LABEL}
                if path.endswith("/issues/9"):
                    self.issue_reads += 1
                    if self.pending_issue is not None:
                        value = self.pending_issue
                        self.pending_issue = None
                        return value
                    return self.issue
                raise AssertionError(f"Unexpected GET: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if path.endswith("/comments"):
                    if limit != 500:
                        raise AssertionError("Closure comment limit changed")
                    return list(self.comments)
                if limit != 5000:
                    raise AssertionError("Issue list limit changed")
                return [self.issue]

            def send_json(self, method: str, path: str, payload: dict) -> object:
                self.sent.append((method, path, payload))
                if method == "POST":
                    self.comments = [
                        {
                            "id": 3,
                            "body": payload["body"],
                            "user": {
                                "id": APP_BOT_ID,
                                "login": app_login,
                                "type": "Bot",
                            },
                        }
                    ]
                    raise review.GitHubTransientError("write outcome unknown")
                self.pending_issue = json.loads(json.dumps(self.issue))
                self.issue.update(payload)
                self.issue["labels"] = [{"name": name} for name in payload["labels"]]
                raise review.GitHubTransientError("write outcome unknown")

        client = FakeClient()
        with patch.object(review.time, "sleep"):
            synchronize_test_findings(client, [])
        self.assertEqual(["POST", "PATCH"], [value[0] for value in client.sent])
        self.assertEqual(2, client.issue_reads)
        self.assertEqual("closed", client.issue["state"])
        self.assertEqual(
            "finding-issue-closure-comment",
            review.parse_operation_marker(client.comments[0]["body"])["action"],
        )
        self.assertEqual(
            "finding-issue-close",
            review.parse_operation_marker(client.issue["body"])["action"],
        )

    def test_uncertain_finding_issue_create_and_update_recover_once(self) -> None:
        app_login = "coco-agent[bot]"
        context = bound_context()
        finding = specialist_report("correctness", context, severity="P1")["findings"][
            0
        ]
        group_id = review.stable_actionable_group_id([finding])
        actionable = {
            "stable_id": group_id,
            "legacy_finding_ids": [],
            "source_id": finding["id"],
            "duplicate_source_ids": [],
            "kind": "confirmed-blocker",
            "finding": finding,
        }

        for previous, response_mode, expected_action in (
            (None, "empty", "finding-issue-create"),
            (
                {
                    "number": 8,
                    "title": "Old finding",
                    "body": review.finding_issue_marker(60, BASE_SHA, group_id)
                    + "\nOld body\n",
                    "state": "open",
                    "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                    "user": {
                        "id": APP_BOT_ID,
                        "login": app_login,
                        "type": "Bot",
                    },
                },
                "empty",
                "finding-issue-update",
            ),
            (None, "transient", "finding-issue-create"),
            (
                {
                    "number": 8,
                    "title": "Old finding",
                    "body": review.finding_issue_marker(60, BASE_SHA, group_id)
                    + "\nOld body\n",
                    "state": "open",
                    "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                    "user": {
                        "id": APP_BOT_ID,
                        "login": app_login,
                        "type": "Bot",
                    },
                },
                "transient",
                "finding-issue-update",
            ),
            (None, "incomplete-actor", "finding-issue-create"),
            (
                {
                    "number": 8,
                    "title": "Old finding",
                    "body": review.finding_issue_marker(60, BASE_SHA, group_id)
                    + "\nOld body\n",
                    "state": "open",
                    "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                    "user": {
                        "id": APP_BOT_ID,
                        "login": app_login,
                        "type": "Bot",
                    },
                },
                "incomplete-actor",
                "finding-issue-update",
            ),
        ):
            with self.subTest(action=expected_action, response=response_mode):

                class FakeClient:
                    def __init__(self) -> None:
                        self.issue = (
                            json.loads(json.dumps(previous)) if previous else None
                        )
                        self.pending_issue: dict | None = None
                        self.issue_reads = 0
                        self.issue_scans = 0
                        self.sent: list[tuple[str, str, dict]] = []

                    def get_json(self, path: str) -> dict:
                        if path.endswith("/labels/agent-review"):
                            return {"name": review.FINDING_ISSUE_LABEL}
                        if path.endswith("/issues/8") and self.issue is not None:
                            self.issue_reads += 1
                            if self.pending_issue is not None:
                                value = self.pending_issue
                                self.pending_issue = None
                                return value
                            return self.issue
                        raise AssertionError(f"Unexpected GET: {path}")

                    def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                        if limit != 5000:
                            raise AssertionError(f"Unexpected page limit: {limit}")
                        self.issue_scans += 1
                        if previous is None and self.issue_scans == 2:
                            return []
                        return [self.issue] if self.issue is not None else []

                    def send_json(
                        self, method: str, path: str, payload: dict
                    ) -> object:
                        self.sent.append((method, path, payload))
                        if self.issue is None:
                            self.issue = {
                                "number": 8,
                                "state": "open",
                                "user": {
                                    "id": APP_BOT_ID,
                                    "login": app_login,
                                    "type": "Bot",
                                },
                            }
                        else:
                            self.pending_issue = json.loads(json.dumps(self.issue))
                        self.issue.update(payload)
                        self.issue["labels"] = [
                            {"name": name} for name in payload["labels"]
                        ]
                        if response_mode == "transient":
                            raise review.GitHubTransientError("write outcome unknown")
                        if response_mode == "incomplete-actor":
                            return {**self.issue, "user": {}}
                        return None

                client = FakeClient()
                with patch.object(review.time, "sleep"):
                    synchronized = synchronize_test_findings(client, [actionable])
                self.assertEqual(1, len(client.sent))
                self.assertEqual(
                    "POST" if previous is None else "PATCH", client.sent[0][0]
                )
                self.assertEqual(1, len(synchronized))
                self.assertEqual(0 if previous is None else 2, client.issue_reads)
                self.assertEqual(
                    expected_action,
                    review.parse_operation_marker(client.issue["body"])["action"],
                )

    def test_finding_issue_update_and_close_bind_target_numbers(self) -> None:
        app_login = "coco-agent[bot]"
        context = bound_context()
        finding = specialist_report("correctness", context, severity="P1")["findings"][
            0
        ]
        group_id = review.stable_actionable_group_id([finding])
        actionable = {
            "stable_id": group_id,
            "legacy_finding_ids": [],
            "source_id": finding["id"],
            "duplicate_source_ids": [],
            "kind": "confirmed-blocker",
            "finding": finding,
        }
        previous = {
            "number": 8,
            "title": "Old finding",
            "body": review.finding_issue_marker(60, BASE_SHA, group_id)
            + "\nOld body\n",
            "state": "open",
            "labels": [{"name": review.FINDING_ISSUE_LABEL}],
            "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
        }

        for name, uncertain, returned_number in (
            ("normal-wrong-number", False, 9),
            ("normal-string-number", False, "8"),
            ("normal-boolean-number", False, True),
            ("recovery-wrong-number", True, 9),
        ):
            with self.subTest(operation="update", case=name):

                class UpdateClient:
                    def __init__(self) -> None:
                        self.sent: list[tuple[str, str, dict]] = []
                        self.issue_reads = 0
                        self.candidate: dict | None = None

                    def get_json(self, path: str) -> dict:
                        if path.endswith("/labels/agent-review"):
                            return {"name": review.FINDING_ISSUE_LABEL}
                        if path.endswith("/issues/8") and self.candidate is not None:
                            self.issue_reads += 1
                            return self.candidate
                        raise AssertionError(f"Unexpected GET: {path}")

                    def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                        return [json.loads(json.dumps(previous))]

                    def send_json(
                        self, method: str, path: str, payload: dict
                    ) -> object:
                        self.sent.append((method, path, payload))
                        self.candidate = {
                            **json.loads(json.dumps(previous)),
                            **payload,
                            "number": returned_number,
                            "labels": [{"name": value} for value in payload["labels"]],
                        }
                        return None if uncertain else self.candidate

                client = UpdateClient()
                with self.assertRaisesRegex(review.ReviewError, "number"):
                    synchronize_test_findings(client, [actionable])
                self.assertEqual(["PATCH"], [value[0] for value in client.sent])
                self.assertEqual(1 if uncertain else 0, client.issue_reads)

        closing_issue = {
            **previous,
            "number": 9,
            "body": review.finding_issue_marker_v2(
                REPOSITORY,
                REPOSITORY_ID,
                60,
                BASE_SHA,
                HEAD_SHA,
                group_id,
                review.continuity_anchor(finding),
                "a" * 64,
                "b" * 64,
                "c" * 64,
            )
            + "\nOld body\n",
        }
        for name, uncertain in (("normal", False), ("recovery", True)):
            with self.subTest(operation="close", case=name):

                class CloseClient:
                    def __init__(self) -> None:
                        self.sent: list[tuple[str, str, dict]] = []
                        self.issue_reads = 0
                        self.candidate: dict | None = None

                    def get_json(self, path: str) -> dict:
                        if path.endswith("/labels/agent-review"):
                            return {"name": review.FINDING_ISSUE_LABEL}
                        if path.endswith("/issues/9") and self.candidate is not None:
                            self.issue_reads += 1
                            return self.candidate
                        raise AssertionError(f"Unexpected GET: {path}")

                    def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                        if path.endswith("/comments"):
                            raise AssertionError(
                                "Normal closure comment must not recover"
                            )
                        return [json.loads(json.dumps(closing_issue))]

                    def send_json(
                        self, method: str, path: str, payload: dict
                    ) -> object:
                        self.sent.append((method, path, payload))
                        if method == "POST":
                            return {
                                "id": 3,
                                "body": payload["body"],
                                "user": {
                                    "id": APP_BOT_ID,
                                    "login": app_login,
                                    "type": "Bot",
                                },
                            }
                        self.candidate = {
                            **json.loads(json.dumps(closing_issue)),
                            **payload,
                            "number": 10,
                            "labels": [{"name": value} for value in payload["labels"]],
                        }
                        return None if uncertain else self.candidate

                client = CloseClient()
                with self.assertRaisesRegex(review.ReviewError, "number"):
                    synchronize_test_findings(client, [])
                self.assertEqual(["POST", "PATCH"], [value[0] for value in client.sent])
                self.assertEqual(1 if uncertain else 0, client.issue_reads)

    def test_managed_comment_update_binds_target_comment_id(self) -> None:
        app_login = "coco-agent[bot]"
        previous = {
            "id": 7,
            "body": review.COMMENT_MARKER + "\n<!-- agent-jury-run:41:1 -->\nOld\n",
            "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
        }
        body = review.COMMENT_MARKER + "\n<!-- agent-jury-run:42:1 -->\nResult\n"

        for name, uncertain, returned_id in (
            ("normal-wrong-id", False, 8),
            ("normal-string-id", False, "7"),
            ("normal-boolean-id", False, True),
            ("recovery-wrong-id", True, 8),
        ):
            with self.subTest(case=name):

                class FakeClient:
                    def __init__(self) -> None:
                        self.sent: list[tuple[str, str, dict]] = []
                        self.reads = 0
                        self.candidate: dict | None = None

                    def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                        self.reads += 1
                        return [self.candidate] if self.candidate is not None else []

                    def send_json(
                        self, method: str, path: str, payload: dict
                    ) -> object:
                        self.sent.append((method, path, payload))
                        self.candidate = {
                            "id": returned_id,
                            "body": payload["body"],
                            "user": {
                                "id": APP_BOT_ID,
                                "login": app_login,
                                "type": "Bot",
                            },
                        }
                        return None if uncertain else self.candidate

                client = FakeClient()
                with self.assertRaisesRegex(review.ReviewError, "ID"):
                    review.upsert_comment(
                        client,
                        REPOSITORY,
                        REPOSITORY_ID,
                        60,
                        HEAD_SHA,
                        body,
                        (42, 1),
                        app_login,
                        APP_BOT_ID,
                        lambda: {},
                        previous,
                    )
                self.assertEqual(["PATCH"], [value[0] for value in client.sent])
                self.assertEqual(1 if uncertain else 0, client.reads)

    def test_finding_issue_update_and_close_pending_exhaustion_do_not_rewrite(
        self,
    ) -> None:
        app_login = "coco-agent[bot]"
        context = bound_context()
        finding = specialist_report("correctness", context, severity="P1")["findings"][
            0
        ]
        group_id = review.stable_actionable_group_id([finding])
        actionable = {
            "stable_id": group_id,
            "legacy_finding_ids": [],
            "source_id": finding["id"],
            "duplicate_source_ids": [],
            "kind": "confirmed-blocker",
            "finding": finding,
        }
        previous = {
            "number": 8,
            "title": "Old finding",
            "body": review.finding_issue_marker(60, BASE_SHA, group_id)
            + "\nOld body\n",
            "state": "open",
            "labels": [{"name": review.FINDING_ISSUE_LABEL}],
            "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
        }
        attempts = len(review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS) + 1

        class UpdateClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []
                self.issue_reads = 0

            def get_json(self, path: str) -> dict:
                if path.endswith("/labels/agent-review"):
                    return {"name": review.FINDING_ISSUE_LABEL}
                if path.endswith("/issues/8"):
                    self.issue_reads += 1
                    return json.loads(json.dumps(previous))
                raise AssertionError(f"Unexpected GET: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                return [json.loads(json.dumps(previous))]

            def send_json(self, method: str, path: str, payload: dict) -> None:
                self.sent.append((method, path, payload))
                return None

        update = UpdateClient()
        with patch.object(review.time, "sleep"):
            with self.assertRaisesRegex(review.ReviewError, "bounded reads"):
                synchronize_test_findings(update, [actionable])
        self.assertEqual(["PATCH"], [value[0] for value in update.sent])
        self.assertEqual(attempts, update.issue_reads)

        closing_issue = {**previous, "number": 9}

        class CloseClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []
                self.issue_reads = 0

            def get_json(self, path: str) -> dict:
                if path.endswith("/labels/agent-review"):
                    return {"name": review.FINDING_ISSUE_LABEL}
                if path.endswith("/issues/9"):
                    self.issue_reads += 1
                    return json.loads(json.dumps(closing_issue))
                raise AssertionError(f"Unexpected GET: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if path.endswith("/comments"):
                    raise AssertionError("Normal closure comment must not recover")
                return [json.loads(json.dumps(closing_issue))]

            def send_json(self, method: str, path: str, payload: dict) -> object:
                self.sent.append((method, path, payload))
                if method == "POST":
                    return {
                        "id": 3,
                        "body": payload["body"],
                        "user": {
                            "id": APP_BOT_ID,
                            "login": app_login,
                            "type": "Bot",
                        },
                    }
                return None

        close = CloseClient()
        with patch.object(review.time, "sleep"):
            with self.assertRaisesRegex(review.ReviewError, "bounded reads"):
                synchronize_test_findings(close, [])
        self.assertEqual(["POST", "PATCH"], [value[0] for value in close.sent])
        self.assertEqual(attempts, close.issue_reads)

    def test_uncertain_finding_issue_recovery_rejects_conflict_and_pr_drift(
        self,
    ) -> None:
        group_id = "v2-" + "d" * 64
        finding_marker = review.finding_issue_marker(60, BASE_SHA, group_id)
        expected_operation = review.operation_marker(
            REPOSITORY,
            REPOSITORY_ID,
            "coco-agent[bot]",
            APP_BOT_ID,
            (42, 1),
            60,
            HEAD_SHA,
            group_id,
            "finding-issue-create",
        )

        class FakeClient:
            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                return [
                    {
                        "number": 8,
                        "body": finding_marker
                        + "\n"
                        + review.operation_marker(
                            REPOSITORY,
                            REPOSITORY_ID,
                            "coco-agent[bot]",
                            APP_BOT_ID,
                            (42, 2),
                            60,
                            HEAD_SHA,
                            group_id,
                            "finding-issue-create",
                        )
                        + "\nBody\n",
                        "state": "open",
                        "title": "title",
                        "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                        "user": {
                            "id": APP_BOT_ID,
                            "login": "coco-agent[bot]",
                            "type": "Bot",
                        },
                    }
                ]

        with self.assertRaisesRegex(review.ReviewError, "Conflicting finding Issue"):
            review.recover_finding_issue_create(
                FakeClient(),
                REPOSITORY,
                "coco-agent[bot]",
                APP_BOT_ID,
                finding_marker,
                expected_operation,
                lambda value: value,
                lambda: {},
            )
        reads = 0

        def drift() -> dict:
            nonlocal reads
            reads += 1
            raise review.ReviewError("Pull request changed before publication.")

        with self.assertRaisesRegex(review.ReviewError, "Pull request changed"):
            review.uncertain_write_recovery(
                "finding-issue-create",
                "repos/example/issues",
                drift,
                review.recovery_pending,
            )
        self.assertEqual(1, reads)

    def test_finding_issue_recovery_scans_actor_before_markers(self) -> None:
        group_id = "v2-" + "7" * 64
        finding_marker = review.finding_issue_marker(60, BASE_SHA, group_id)
        expected_operation = test_operation_marker(group_id, "finding-issue-create")
        malformed_operation = review.OPERATION_MARKER_PREFIX + '{"bad":true} -->'
        wrong_actor = app_actor(APP_BOT_ID + 1, "other-app[bot]")
        untrusted_cases = {
            "malformed": [
                finding_issue_resource(
                    7,
                    review.FINDING_ISSUE_MARKER_PREFIX + '{"bad":true} -->',
                    "",
                    wrong_actor,
                ),
                finding_issue_resource(
                    8, finding_marker, malformed_operation, wrong_actor
                ),
            ],
            "matching": [
                finding_issue_resource(
                    8, finding_marker, expected_operation, wrong_actor
                )
            ],
            "duplicate": [
                finding_issue_resource(
                    number, finding_marker, expected_operation, wrong_actor
                )
                for number in (8, 9)
            ],
        }
        for name, issues in untrusted_cases.items():
            with self.subTest(case=name):
                probe = review.finding_issue_recovery_candidate(
                    FindingIssueScanClient(issues),
                    REPOSITORY,
                    APP_LOGIN,
                    APP_BOT_ID,
                    finding_marker,
                    expected_operation,
                    lambda value: value,
                )
                self.assertIs(review.RecoveryState.PENDING, probe.state)

        with self.assertRaises(review.ReviewError):
            review.finding_issue_recovery_candidate(
                FindingIssueScanClient(
                    [finding_issue_resource(8, finding_marker, malformed_operation)]
                ),
                REPOSITORY,
                APP_LOGIN,
                APP_BOT_ID,
                finding_marker,
                expected_operation,
                lambda value: value,
            )

    def test_finding_issue_recovery_rejects_zero_duplicate_and_binding_conflicts(
        self,
    ) -> None:
        group_id = "v2-" + "e" * 64
        finding_marker = review.finding_issue_marker(60, BASE_SHA, group_id)
        expected_operation = test_operation_marker(group_id, "finding-issue-create")
        expected_body = f"{finding_marker}\n{expected_operation}\nBody\n"

        def issue(number: int, operation: str = expected_operation) -> dict:
            return finding_issue_resource(number, finding_marker, operation)

        def verify(value: object) -> dict:
            return review.verify_finding_issue(
                value,
                APP_LOGIN,
                APP_BOT_ID,
                finding_marker,
                expected_operation,
                "title",
                expected_body,
                {review.FINDING_ISSUE_LABEL},
                "open",
            )

        missing = FindingIssueScanClient([])
        with patch.object(review.time, "sleep"):
            with self.assertRaisesRegex(review.ReviewError, "bounded reads"):
                review.recover_finding_issue_create(
                    missing,
                    REPOSITORY,
                    APP_LOGIN,
                    APP_BOT_ID,
                    finding_marker,
                    expected_operation,
                    verify,
                    lambda: {},
                )
        self.assertEqual(
            len(review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS) + 1,
            missing.scans,
        )

        duplicate = FindingIssueScanClient([issue(8), issue(9)])
        with self.assertRaisesRegex(review.ReviewError, "Multiple finding Issues"):
            review.recover_finding_issue_create(
                duplicate,
                REPOSITORY,
                APP_LOGIN,
                APP_BOT_ID,
                finding_marker,
                expected_operation,
                verify,
                lambda: {},
            )
        self.assertEqual(1, duplicate.scans)

        mismatches = {
            "repository": {"repository": "other/repository"},
            "repository_id": {"repository_id": REPOSITORY_ID + 1},
            "app_login": {"expected_login": "other-app[bot]"},
            "app_bot_id": {"expected_bot_id": APP_BOT_ID + 1},
            "pull_request": {"pr_number": 61},
            "head_sha": {"head_sha": "c" * 40},
            "run_id": {"run_order": (43, 1)},
            "run_attempt": {"run_order": (42, 2)},
            "group_id": {"group_id": "v2-" + "f" * 64},
            "action": {"action": "finding-issue-update"},
        }
        for name, changed in mismatches.items():
            with self.subTest(binding=name):
                candidate_operation = test_operation_marker(
                    group_id, "finding-issue-create", **changed
                )
                client = FindingIssueScanClient([issue(8, candidate_operation)])
                with self.assertRaisesRegex(review.ReviewError, "Conflicting"):
                    review.recover_finding_issue_create(
                        client,
                        REPOSITORY,
                        APP_LOGIN,
                        APP_BOT_ID,
                        finding_marker,
                        expected_operation,
                        verify,
                        lambda: {},
                    )
                self.assertEqual(1, client.scans)

        wrong_actors = (
            {"id": APP_BOT_ID, "login": "other-app[bot]", "type": "Bot"},
            app_actor(APP_BOT_ID + 1),
            app_actor(actor_type="User"),
        )
        for user in wrong_actors:
            with self.subTest(user=user):
                client = FindingIssueScanClient(
                    [
                        finding_issue_resource(
                            8, finding_marker, expected_operation, user
                        )
                    ]
                )
                with patch.object(review.time, "sleep"):
                    with self.assertRaisesRegex(review.ReviewError, "bounded reads"):
                        review.recover_finding_issue_create(
                            client,
                            REPOSITORY,
                            APP_LOGIN,
                            APP_BOT_ID,
                            finding_marker,
                            expected_operation,
                            verify,
                            lambda: {},
                        )
                self.assertEqual(
                    len(review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS) + 1,
                    client.scans,
                )

    def test_uncertain_closure_comment_rejects_duplicate_exact_markers(self) -> None:
        app_login = "coco-agent[bot]"
        finding_id = "v2-" + "9" * 64

        class FakeClient:
            def __init__(self) -> None:
                self.issue = {
                    "number": 9,
                    "title": "Old finding",
                    "body": review.finding_issue_marker(60, BASE_SHA, finding_id)
                    + "\nOld body\n",
                    "state": "open",
                    "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                    "user": {
                        "id": APP_BOT_ID,
                        "login": app_login,
                        "type": "Bot",
                    },
                }
                self.comments: list[dict] = []
                self.sent: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> dict:
                if path.endswith("/labels/agent-review"):
                    return {"name": review.FINDING_ISSUE_LABEL}
                raise AssertionError(f"Unexpected GET: {path}")

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if path.endswith("/comments"):
                    return self.comments
                return [self.issue]

            def send_json(self, method: str, path: str, payload: dict) -> object:
                self.sent.append((method, path, payload))
                self.comments = [
                    {
                        "id": number,
                        "body": payload["body"],
                        "user": {
                            "id": APP_BOT_ID,
                            "login": app_login,
                            "type": "Bot",
                        },
                    }
                    for number in (3, 4)
                ]
                return None

        client = FakeClient()
        with self.assertRaisesRegex(review.ReviewError, "Multiple closure comments"):
            synchronize_test_findings(client, [])
        self.assertEqual(["POST"], [method for method, _path, _body in client.sent])

    def test_uncertain_closure_comment_zero_and_binding_mismatches_exhaust(
        self,
    ) -> None:
        app_login = "coco-agent[bot]"
        finding_id = "v2-" + "8" * 64
        operation_values = {
            "repository": REPOSITORY,
            "repository_id": REPOSITORY_ID,
            "expected_login": app_login,
            "expected_bot_id": APP_BOT_ID,
            "run_order": (42, 1),
            "pr_number": 60,
            "head_sha": HEAD_SHA,
            "group_id": finding_id,
            "action": "finding-issue-closure-comment",
        }
        mismatches = {
            "repository": {"repository": "other/repository"},
            "repository_id": {"repository_id": REPOSITORY_ID + 1},
            "app_login": {"expected_login": "other-app[bot]"},
            "app_bot_id": {"expected_bot_id": APP_BOT_ID + 1},
            "pull_request": {"pr_number": 61},
            "head_sha": {"head_sha": "c" * 40},
            "run_id": {"run_order": (43, 1)},
            "run_attempt": {"run_order": (42, 2)},
            "group_id": {"group_id": "v2-" + "7" * 64},
            "action": {"action": "finding-issue-close"},
        }
        attempts = len(review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS) + 1

        for name, changed in (("zero", None), *mismatches.items()):
            with self.subTest(binding=name):
                candidate_operation = (
                    None
                    if changed is None
                    else review.operation_marker(**{**operation_values, **changed})
                )

                class FakeClient:
                    def __init__(self) -> None:
                        self.sent: list[tuple[str, str, dict]] = []
                        self.comment_reads = 0
                        self.comments: list[dict] = []
                        self.issue = {
                            "number": 9,
                            "title": "Old finding",
                            "body": review.finding_issue_marker(
                                60, BASE_SHA, finding_id
                            )
                            + "\nOld body\n",
                            "state": "open",
                            "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                            "user": {
                                "id": APP_BOT_ID,
                                "login": app_login,
                                "type": "Bot",
                            },
                        }

                    def get_json(self, path: str) -> dict:
                        if path.endswith("/labels/agent-review"):
                            return {"name": review.FINDING_ISSUE_LABEL}
                        raise AssertionError(f"Unexpected GET: {path}")

                    def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                        if path.endswith("/comments"):
                            self.comment_reads += 1
                            return self.comments
                        return [self.issue]

                    def send_json(self, method: str, path: str, payload: dict) -> None:
                        self.sent.append((method, path, payload))
                        if candidate_operation is not None:
                            lines = payload["body"].splitlines()
                            marker_index = next(
                                index
                                for index, line in enumerate(lines)
                                if line.startswith(review.OPERATION_MARKER_PREFIX)
                            )
                            lines[marker_index] = candidate_operation
                            self.comments = [
                                {
                                    "id": 3,
                                    "body": "\n".join(lines) + "\n",
                                    "user": {
                                        "id": APP_BOT_ID,
                                        "login": app_login,
                                        "type": "Bot",
                                    },
                                }
                            ]
                        return None

                client = FakeClient()
                with patch.object(review.time, "sleep"):
                    with self.assertRaisesRegex(review.ReviewError, "bounded reads"):
                        synchronize_test_findings(client, [])
                self.assertEqual(["POST"], [value[0] for value in client.sent])
                self.assertEqual(attempts, client.comment_reads)

    def test_uncertain_managed_comment_rejects_duplicate_and_hard_conflicts(
        self,
    ) -> None:
        app_login = "coco-agent[bot]"
        body = review.COMMENT_MARKER + "\n<!-- agent-jury-run:42:1 -->\nResult\n"

        class DuplicateClient:
            def __init__(self) -> None:
                self.comments: list[dict] = []
                self.sent: list[tuple[str, str, dict]] = []
                self.reads = 0

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                self.reads += 1
                return [] if self.reads == 1 else self.comments

            def send_json(self, method: str, path: str, payload: dict) -> object:
                self.sent.append((method, path, payload))
                self.comments = [
                    {
                        "id": number,
                        "body": payload["body"],
                        "user": {
                            "id": APP_BOT_ID,
                            "login": app_login,
                            "type": "Bot",
                        },
                    }
                    for number in (7, 8)
                ]
                return None

        duplicate = DuplicateClient()
        with self.assertRaisesRegex(review.ReviewError, "Multiple"):
            review.upsert_comment(
                duplicate,
                REPOSITORY,
                REPOSITORY_ID,
                60,
                HEAD_SHA,
                body,
                (42, 1),
                app_login,
                APP_BOT_ID,
                lambda: {},
            )
        self.assertEqual(1, len(duplicate.sent))

        previous = {
            "id": 7,
            "body": review.COMMENT_MARKER + "\n<!-- agent-jury-run:41:1 -->\nOld\n",
            "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
        }

        class ConflictClient:
            def __init__(self, candidate: dict | None) -> None:
                self.candidate = candidate
                self.sent: list[tuple[str, str, dict]] = []

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                if self.candidate is None:
                    self.candidate = {
                        "id": 7,
                        "body": self.sent[0][2]["body"],
                        "user": {
                            "id": APP_BOT_ID + 1,
                            "login": app_login,
                            "type": "Bot",
                        },
                    }
                return [self.candidate]

            def send_json(self, method: str, path: str, payload: dict) -> object:
                self.sent.append((method, path, payload))
                return None

        newer = {
            "id": 7,
            "body": review.COMMENT_MARKER + "\n<!-- agent-jury-run:43:1 -->\nNewer\n",
            "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
        }
        for name, candidate in (("newer-run", newer), ("wrong-actor", None)):
            with self.subTest(case=name):
                client = ConflictClient(candidate)
                with self.assertRaises(review.ReviewError):
                    review.upsert_comment(
                        client,
                        REPOSITORY,
                        REPOSITORY_ID,
                        60,
                        HEAD_SHA,
                        body,
                        (42, 1),
                        app_login,
                        APP_BOT_ID,
                        lambda: {},
                        previous,
                    )
                self.assertEqual(1, len(client.sent))

    def test_uncertain_managed_comment_zero_and_binding_mismatches_are_rejected(
        self,
    ) -> None:
        app_login = "coco-agent[bot]"
        body = review.COMMENT_MARKER + "\n<!-- agent-jury-run:42:1 -->\nResult\n"
        previous = {
            "id": 7,
            "body": review.COMMENT_MARKER + "\n<!-- agent-jury-run:41:1 -->\nOld\n",
            "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
        }
        attempts = len(review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS) + 1

        class MissingClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []
                self.reads = 0

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                self.reads += 1
                return []

            def send_json(self, method: str, path: str, payload: dict) -> None:
                self.sent.append((method, path, payload))
                return None

        missing = MissingClient()
        with patch.object(review.time, "sleep"):
            with self.assertRaisesRegex(review.ReviewError, "bounded reads"):
                review.upsert_comment(
                    missing,
                    REPOSITORY,
                    REPOSITORY_ID,
                    60,
                    HEAD_SHA,
                    body,
                    (42, 1),
                    app_login,
                    APP_BOT_ID,
                    lambda: {},
                )
        self.assertEqual(["POST"], [value[0] for value in missing.sent])
        self.assertEqual(attempts + 1, missing.reads)

        mismatches = {
            "repository": {"repository": "other/repository"},
            "repository_id": {"repository_id": REPOSITORY_ID + 1},
            "app_login": {"expected_login": "other-app[bot]"},
            "app_bot_id": {"expected_bot_id": APP_BOT_ID + 1},
            "pull_request": {"pr_number": 61},
            "head_sha": {"head_sha": "c" * 40},
            "run_id": {"run_order": (43, 1)},
            "run_attempt": {"run_order": (42, 2)},
            "group_id": {"group_id": "v2-" + "6" * 64},
        }
        for prior, action in (
            (None, "managed-comment-create"),
            (previous, "managed-comment-update"),
        ):
            operation_values = {
                "repository": REPOSITORY,
                "repository_id": REPOSITORY_ID,
                "expected_login": app_login,
                "expected_bot_id": APP_BOT_ID,
                "run_order": (42, 1),
                "pr_number": 60,
                "head_sha": HEAD_SHA,
                "group_id": review.MANAGED_COMMENT_GROUP_ID,
                "action": action,
            }
            action_mismatch = (
                "managed-comment-update"
                if action == "managed-comment-create"
                else "managed-comment-create"
            )
            cases = {**mismatches, "action": {"action": action_mismatch}}
            for name, changed in cases.items():
                with self.subTest(action=action, binding=name):
                    candidate_operation = review.operation_marker(
                        **{**operation_values, **changed}
                    )

                    class FakeClient:
                        def __init__(self) -> None:
                            self.sent: list[tuple[str, str, dict]] = []
                            self.reads = 0
                            self.comments: list[dict] = []

                        def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                            self.reads += 1
                            return self.comments

                        def send_json(
                            self, method: str, path: str, payload: dict
                        ) -> None:
                            self.sent.append((method, path, payload))
                            lines = payload["body"].splitlines()
                            marker_index = next(
                                index
                                for index, line in enumerate(lines)
                                if line.startswith(review.OPERATION_MARKER_PREFIX)
                            )
                            lines[marker_index] = candidate_operation
                            self.comments = [
                                {
                                    "id": 7,
                                    "body": "\n".join(lines) + "\n",
                                    "user": {
                                        "id": APP_BOT_ID,
                                        "login": app_login,
                                        "type": "Bot",
                                    },
                                }
                            ]
                            return None

                    client = FakeClient()
                    with self.assertRaises(review.ReviewError):
                        review.upsert_comment(
                            client,
                            REPOSITORY,
                            REPOSITORY_ID,
                            60,
                            HEAD_SHA,
                            body,
                            (42, 1),
                            app_login,
                            APP_BOT_ID,
                            lambda: {},
                            prior,
                        )
                    self.assertEqual(
                        ["PATCH" if prior else "POST"],
                        [value[0] for value in client.sent],
                    )
                    self.assertEqual(1 if prior else 2, client.reads)

    def test_uncertain_managed_comment_old_snapshot_exhausts_without_rewrite(
        self,
    ) -> None:
        app_login = "coco-agent[bot]"
        previous = {
            "id": 7,
            "body": review.COMMENT_MARKER + "\n<!-- agent-jury-run:41:1 -->\nOld\n",
            "user": {"id": APP_BOT_ID, "login": app_login, "type": "Bot"},
        }

        class FakeClient:
            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []
                self.reads = 0

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                self.reads += 1
                return [previous]

            def send_json(self, method: str, path: str, payload: dict) -> object:
                self.sent.append((method, path, payload))
                return None

        client = FakeClient()
        with patch.object(review.time, "sleep"):
            with self.assertRaisesRegex(review.ReviewError, "bounded reads"):
                review.upsert_comment(
                    client,
                    REPOSITORY,
                    REPOSITORY_ID,
                    60,
                    HEAD_SHA,
                    review.COMMENT_MARKER + "\n<!-- agent-jury-run:42:1 -->\nResult\n",
                    (42, 1),
                    app_login,
                    APP_BOT_ID,
                    lambda: {},
                    previous,
                )
        self.assertEqual(1, len(client.sent))
        self.assertEqual(
            len(review.FINDING_ISSUE_CONVERGENCE_BACKOFF_SECONDS) + 1,
            client.reads,
        )

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
            api_url = "https://api.github.com"

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
                return commit_status_response(path, payload)

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
            api_url = "https://api.github.com"

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
                return commit_status_response(path, payload)

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
        self.assertIn("needs: test", publish)
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
            'run-name: "Agent Review Jury / PR #${{ github.event.pull_request.number }} / head ${{ github.event.pull_request.head.sha }} / base ${{ github.event.pull_request.base.sha }}"',
            direct_workflow,
        )
        self.assertIn('"${review_script}" route', direct_workflow)
        self.assertEqual(
            1,
            direct_workflow.count(
                "uses: ./.github/workflows/reusable-agent-review-jury.yml"
            ),
        )
        marker = direct_workflow.split("\n  deferred-marker:\n", 1)[1].split(
            "\n  no-secret-review:\n", 1
        )[0]
        direct_no_secret = direct_workflow.split("\n  no-secret-review:\n", 1)[1]
        self.assertIn("name: Emit protected no-secret marker", marker)
        self.assertIn("needs.route.outputs.review-route == 'deferred-secret'", marker)
        self.assertIn("agent-review-deferred-marker", marker)
        self.assertIn("permissions: {}", marker)
        self.assertNotIn("actions/checkout", marker)
        self.assertNotIn("environment:", marker)
        self.assertNotIn("secrets: inherit", direct_no_secret)
        self.assertNotIn("direct-secret-review", direct_workflow)
        self.assertNotIn("direct-secret", direct_workflow)
        self.assertNotIn("${{ secrets.", direct_workflow)
        self.assertNotIn("COCO_AGENT_APP_PRIVATE_KEY", direct_workflow)
        self.assertNotIn("ANTHROPIC", direct_workflow)

        self.assertTrue(
            review_workflow.startswith("name: Reusable Agent Review Jury\n")
        )
        self.assertIn("  workflow_call:\n", review_workflow)
        prepare = review_workflow.split("\n  prepare:\n", 1)[1].split(
            "\n  specialists:\n", 1
        )[0]
        admission = review_workflow.split("\n  publisher-admission:\n", 1)[1].split(
            "\n  trusted-publisher:\n", 1
        )[0]
        trusted = review_workflow.split("\n  trusted-publisher:\n", 1)[1].split(
            "\n  no-secret-publisher:\n", 1
        )[0]
        no_secret = review_workflow.split("\n  no-secret-publisher:\n", 1)[1]
        specialists = review_workflow.split("\n  specialists:\n", 1)[1].split(
            "\n  verifiers:\n", 1
        )[0]
        verifiers = review_workflow.split("\n  verifiers:\n", 1)[1].split(
            "\n  chair:\n", 1
        )[0]
        chair = review_workflow.split("\n  chair:\n", 1)[1].split(
            "\n  publisher-admission:\n", 1
        )[0]
        model_environment = "    environment: coco-agent-model\n"
        self.assertEqual(3, review_workflow.count(model_environment))
        for model_job in (specialists, verifiers, chair):
            self.assertEqual(1, model_job.count(model_environment))
        model_variables = (
            "COCO_AGENT_MODEL_PROTOCOL",
            "COCO_AGENT_MODEL_BASE_URL",
            "COCO_AGENT_MODEL_THINKING",
            "COCO_AGENT_MODEL",
        )
        for workflow_name, workflow in (
            ("reusable", review_workflow),
            ("deferred", deferred_workflow),
        ):
            workflow_prepare = workflow.split("\n  prepare:\n", 1)[1].split(
                "\n  specialists:\n", 1
            )[0]
            workflow_specialists = workflow.split("\n  specialists:\n", 1)[1].split(
                "\n  verifiers:\n", 1
            )[0]
            workflow_verifiers = workflow.split("\n  verifiers:\n", 1)[1].split(
                "\n  chair:\n", 1
            )[0]
            chair_end = (
                "\n  continuity-verifiers:\n"
                if workflow_name == "deferred"
                else "\n  publisher-admission:\n"
            )
            workflow_chair = workflow.split("\n  chair:\n", 1)[1].split(chair_end, 1)[0]
            workflow_continuity = (
                workflow.split("\n  continuity-verifiers:\n", 1)[1].split(
                    "\n  publisher-admission:\n", 1
                )[0]
                if workflow_name == "deferred"
                else ""
            )
            workflow_admission = workflow.split("\n  publisher-admission:\n", 1)[
                1
            ].split("\n  trusted-publisher:\n", 1)[0]
            model_sections = [
                ("prepare", workflow_prepare),
                ("specialists", workflow_specialists),
                ("verifiers", workflow_verifiers),
                ("chair", workflow_chair),
                ("publisher admission", workflow_admission),
            ]
            if workflow_continuity:
                model_sections.append(("continuity", workflow_continuity))
            for section_name, section in model_sections:
                for variable in model_variables:
                    self.assertEqual(
                        1,
                        section.count(f"{variable}: ${{{{ vars.{variable} }}}}"),
                        f"{workflow_name} {section_name}: {variable}",
                    )
            api_key_sections = [
                ("specialists", workflow_specialists),
                ("verifiers", workflow_verifiers),
                ("chair", workflow_chair),
            ]
            if workflow_continuity:
                api_key_sections.append(("continuity", workflow_continuity))
            for section_name, section in api_key_sections:
                self.assertEqual(
                    1,
                    section.count(
                        "COCO_AGENT_MODEL_API_KEY: "
                        "${{ secrets.COCO_AGENT_MODEL_API_KEY }}"
                    ),
                    f"{workflow_name} {section_name}: API key",
                )
            for section_name, section in (
                ("prepare", workflow_prepare),
                ("publisher admission", workflow_admission),
            ):
                self.assertNotIn(
                    "COCO_AGENT_MODEL_API_KEY",
                    section,
                    f"{workflow_name} {section_name}: API key",
                )
        for name, section in (
            ("source no-secret call", direct_no_secret),
            ("prepare", prepare),
            ("publisher admission", admission),
            ("trusted publisher", trusted),
            ("no-secret publisher", no_secret),
        ):
            for forbidden in NON_MODEL_JOB_FORBIDDEN_ENV:
                self.assertNotIn(forbidden, section, f"{name}: {forbidden}")
            self.assertNotIn(model_environment, section, name)
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
        self.assertNotIn("continuity-verifiers", review_workflow)
        self.assertIn("--continuity-candidates", deferred_workflow)
        self.assertIn("continuity-verifiers", deferred_workflow)
        self.assertIn(
            '--continuity "${RUNNER_TEMP}/agent-review-continuity"', deferred_workflow
        )
        self.assertIn(
            'run-name: "Deferred Agent Review Jury / source run #${{ github.event.workflow_run.id }}"',
            deferred_workflow,
        )
        self.assertIn("  workflow_run:\n", deferred_workflow)
        self.assertIn("workflows: [Agent Review Jury]", deferred_workflow)
        self.assertIn("github.ref == 'refs/heads/main'", deferred_workflow)
        self.assertIn(
            "github.event.workflow_run.event == 'pull_request_target'",
            deferred_workflow,
        )
        self.assertIn(
            "github.event.workflow_run.event == 'pull_request_review'",
            deferred_workflow,
        )
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
        self.assertIn("ALLOW_DEFERRED: ${{ true }}", deferred_workflow)
        self.assertIn("deferred_args+=(--allow-deferred)", deferred_workflow)
        self.assertIn('--source-run-id "${SOURCE_RUN_ID}"', deferred_workflow)
        self.assertIn("ref: ${{ github.sha }}", deferred_workflow)
        self.assertNotIn("secrets: inherit", deferred_workflow)
        self.assertNotIn("github.event.workflow_run.head_sha", deferred_workflow)
        self.assertIn("actions/download-artifact", deferred_workflow)
        self.assertIn(
            "name: agent-review-input-${{ github.run_id }}", deferred_workflow
        )
        self.assertNotIn("actions/cache", deferred_workflow)
        self.assertNotIn("refs/pull/", deferred_workflow)
        self.assertNotIn("/merge", deferred_workflow)
        self.assertNotIn(model_environment, direct_workflow)
        self.assertEqual(5, deferred_workflow.count(model_environment))
        self.assertIn("environment: coco-agent", deferred_workflow)
        deferred_pre_model = deferred_workflow.split("\n  prepare:\n", 1)[0]
        deferred_admission = deferred_workflow.split("\n  publisher-admission:\n", 1)[
            1
        ].split("\n  trusted-publisher:\n", 1)[0]
        self.assertNotIn("${{ secrets.", deferred_pre_model)
        self.assertNotIn("${{ secrets.", deferred_admission)

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
        self.assertNotIn(model_environment, gate_workflow)
        for forbidden in NON_MODEL_JOB_FORBIDDEN_ENV:
            self.assertNotIn(forbidden, gate_workflow)

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
            self.assertIn("inputs.allow_deferred", model_job)
            self.assertNotIn("statuses: write", model_job)
        trusted = core.split("\n  trusted-publisher:\n", 1)[1].split(
            "\n  no-secret-publisher:\n", 1
        )[0]
        self.assertIn("inputs.allow_deferred", trusted)
        self.assertIn("environment: coco-agent", trusted)
        self.assertEqual(3, core.count("statuses: write"))

        reusable_call = "uses: ./.github/workflows/reusable-agent-review-jury.yml"
        self.assertEqual(1, direct.count(reusable_call))
        self.assertEqual(0, deferred.count(reusable_call))
        for job in (
            "  prepare:\n",
            "  specialists:\n",
            "  verifiers:\n",
            "  chair:\n",
            "  publisher-admission:\n",
            "  trusted-publisher:\n",
        ):
            self.assertEqual(1, deferred.count(job), job)
        self.assertIn("EVENT_NAME: workflow_run", deferred)
        self.assertIn("ALLOW_DEFERRED: ${{ true }}", deferred)
        self.assertIn("deferred_args+=(--allow-deferred)", deferred)
        self.assertIn("needs.specialists.result == 'success'", deferred)
        self.assertIn("needs.verifiers.result == 'success'", deferred)
        self.assertIn("needs.chair.result == 'success'", deferred)
        self.assertIn("environment: coco-agent-model", deferred)
        self.assertIn("environment: coco-agent", deferred)
        self.assertNotIn("allow_deferred: true", direct)
        self.assertNotIn("environment:", direct)
        self.assertNotIn("\n  specialists:\n", direct)
        self.assertIn("\n  specialists:\n", deferred)

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
            "actionable_groups": [],
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
            "actionable_groups": [],
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
            "actionable_groups": [],
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
                "actionable_groups": [],
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

    def test_classification_routes_all_trusted_sources_through_deferred_marker(
        self,
    ) -> None:
        base = {
            "head": {"repo": {"full_name": "patton174/coco-framework"}},
            "user": {"id": 42, "login": "patton174", "type": "User"},
        }
        self.assertEqual(
            review.PR_ROUTE_DEFERRED,
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
            review.PR_ROUTE_DEFERRED,
            review.classify_pr_route(
                app,
                "patton174/coco-framework",
                "coco-agent[bot]",
                APP_BOT_ID,
            ),
        )
        self.assertEqual(
            review.PR_ROUTE_DEFERRED,
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

    def test_classify_pr_compatibility_shim_matches_trusted_deferred_route(
        self,
    ) -> None:
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
                    == review.PR_ROUTE_DEFERRED,
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
        source_association = client.run["pull_requests"][0]
        self.assertEqual(
            {
                "id": REPOSITORY_ID,
                "name": "coco-framework",
                "url": "https://api.github.com/repos/patton174/coco-framework",
            },
            source_association["base"]["repo"],
        )
        self.assertNotIn("full_name", source_association["head"]["repo"])
        self.assertEqual(
            [
                f"repos/{REPOSITORY}/actions/workflows/{review.DEFERRED_WORKFLOW_FILE}",
                f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}",
                f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}",
                (
                    f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}/jobs"
                    "?filter=latest&per_page=100"
                ),
            ],
            client.get_paths,
        )
        self.assertNotIn(review.DEFERRED_WORKFLOW_PATH, client.get_paths[0])

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
                if path == (
                    f"repos/{REPOSITORY}/actions/workflows/"
                    f"{review.DEFERRED_WORKFLOW_FILE}"
                ):
                    return self.workflow
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
                if path == (
                    f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}/jobs"
                    "?filter=latest&per_page=100"
                ):
                    return self.jobs
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

    def test_deferred_binding_does_not_retry_invalid_workflow_identity(self) -> None:
        workflow = deferred_workflow()
        workflow["name"] = "Other Workflow"
        client = FakeDeferredClient(workflow=workflow)

        with patch.object(review.time, "sleep") as sleeper:
            with self.assertRaisesRegex(
                review.ReviewError, "source workflow identity is invalid"
            ):
                review.deferred_review_candidate(
                    client,
                    REPOSITORY,
                    REPOSITORY_ID,
                    SOURCE_RUN_ID,
                    deferred_config(),
                )

        self.assertEqual(
            [f"repos/{REPOSITORY}/actions/workflows/{review.DEFERRED_WORKFLOW_FILE}"],
            client.get_paths,
        )
        sleeper.assert_not_called()

    def test_deferred_binding_ignores_evaluated_run_titles(self) -> None:
        run = deferred_workflow_run()
        run["name"] = "PR-controlled display title"
        run["display_title"] = "Another untrusted display title"

        binding = review.deferred_review_binding(
            FakeDeferredClient(run=run),
            REPOSITORY,
            REPOSITORY_ID,
            SOURCE_RUN_ID,
            deferred_config(),
            DEFERRED_PR_NUMBER,
            HEAD_SHA,
        )

        self.assertTrue(binding["eligible"])

    def test_deferred_binding_requires_canonical_workflow_identity(self) -> None:
        cases: list[tuple[str, dict, dict]] = []

        for name, run_change, workflow_change in (
            (
                "source workflow ID",
                ("workflow_id", DEFERRED_WORKFLOW_ID + 1),
                None,
            ),
            (
                "canonical workflow ID",
                None,
                ("id", DEFERRED_WORKFLOW_ID + 1),
            ),
            (
                "canonical workflow name",
                None,
                ("name", "Other Workflow"),
            ),
            (
                "canonical workflow path",
                None,
                ("path", ".github/workflows/other.yml"),
            ),
            (
                "canonical workflow state",
                None,
                ("state", "inactive"),
            ),
        ):
            run = deferred_workflow_run()
            workflow = deferred_workflow()
            if run_change is not None:
                run[run_change[0]] = run_change[1]
            if workflow_change is not None:
                workflow[workflow_change[0]] = workflow_change[1]
            cases.append((name, run, workflow))

        for name, run, workflow in cases:
            with self.subTest(name=name):
                with self.assertRaises(review.ReviewError):
                    review.deferred_review_binding(
                        FakeDeferredClient(run=run, workflow=workflow),
                        REPOSITORY,
                        REPOSITORY_ID,
                        SOURCE_RUN_ID,
                        deferred_config(),
                        DEFERRED_PR_NUMBER,
                        HEAD_SHA,
                    )

    def test_deferred_binding_requires_exact_successful_marker_jobs(self) -> None:
        valid = deferred_source_jobs()["jobs"]
        cases = {
            "missing marker": [valid[0], valid[2]],
            "failed marker": [
                valid[0],
                {
                    **valid[1],
                    "conclusion": "failure",
                },
                valid[2],
            ],
            "duplicate marker": [valid[0], valid[1], valid[1], valid[2]],
            "unexpected successful job": [
                valid[0],
                valid[1],
                {
                    "name": "Run direct secret-backed jury",
                    "status": "completed",
                    "conclusion": "success",
                },
            ],
            "incomplete marker": [
                valid[0],
                {
                    **valid[1],
                    "status": "in_progress",
                    "conclusion": None,
                },
                valid[2],
            ],
        }
        for name, jobs in cases.items():
            with self.subTest(name=name):
                client = FakeDeferredClient(
                    jobs={"total_count": len(jobs), "jobs": jobs}
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

        invalid_count = deferred_source_jobs()
        invalid_count["total_count"] += 1
        with self.assertRaisesRegex(review.ReviewError, "source jobs are invalid"):
            review.deferred_review_binding(
                FakeDeferredClient(jobs=invalid_count),
                REPOSITORY,
                REPOSITORY_ID,
                SOURCE_RUN_ID,
                deferred_config(),
                DEFERRED_PR_NUMBER,
                HEAD_SHA,
            )

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
                    else [deferred_source_association()],
                )
            )

        add_case("wrong run id", run_change=("id", SOURCE_RUN_ID + 1))
        add_case(
            "wrong workflow ID", run_change=("workflow_id", DEFERRED_WORKFLOW_ID + 1)
        )
        add_case(
            "wrong workflow path",
            run_change=("path", ".github/workflows/reusable-agent-review-jury.yml"),
        )
        add_case("wrong event", run_change=("event", "push"))
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
        stale_source_base = deferred_source_association()
        stale_source_base["base"]["sha"] = "c" * 40
        add_case("stale source base", associated=[stale_source_base])
        stale_source_head = deferred_source_association()
        stale_source_head["head"]["sha"] = "c" * 40
        add_case("stale source head", associated=[stale_source_head])
        add_case("stale current head", pr_path=("head", "sha", "c" * 40))
        add_case("stale current base", pr_path=("base", "sha", "c" * 40))
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
                    [deferred_source_association()],
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

    def test_deferred_binding_rejects_source_association_base_repository_id_drift(
        self,
    ) -> None:
        associated = deferred_source_association()
        associated["base"]["repo"]["id"] = REPOSITORY_ID + 1

        with self.assertRaises(review.ReviewError):
            review.deferred_review_binding(
                FakeDeferredClient(associated=[associated]),
                REPOSITORY,
                REPOSITORY_ID,
                SOURCE_RUN_ID,
                deferred_config(),
                DEFERRED_PR_NUMBER,
                HEAD_SHA,
            )

    def test_deferred_binding_rejects_source_association_head_repository_id_drift(
        self,
    ) -> None:
        associated = deferred_source_association()
        associated["head"]["repo"]["id"] = REPOSITORY_ID + 1

        with self.assertRaises(review.ReviewError):
            review.deferred_review_binding(
                FakeDeferredClient(associated=[associated]),
                REPOSITORY,
                REPOSITORY_ID,
                SOURCE_RUN_ID,
                deferred_config(),
                DEFERRED_PR_NUMBER,
                HEAD_SHA,
            )

    def test_deferred_candidate_accepts_humans_and_skips_unpinned_bots(self) -> None:
        for user, expected_route, expected_eligible in (
            (
                {"id": 12, "login": "maintainer", "type": "User"},
                review.PR_ROUTE_DEFERRED,
                True,
            ),
            (
                {"id": 13, "login": "renovate[bot]", "type": "Bot"},
                review.PR_ROUTE_NO_SECRET,
                False,
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
                self.assertEqual(expected_eligible, candidate["eligible"])
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

        self.assertTrue(app_candidate["eligible"])
        self.assertEqual(review.PR_ROUTE_DEFERRED, app_candidate["review_route"])
        self.assertEqual("same-repository-trusted-app", app_candidate["route_reason"])
        self.assertTrue(dependabot_candidate["eligible"])
        self.assertEqual(review.PR_ROUTE_DEFERRED, dependabot_candidate["review_route"])

    def test_bind_deferred_emits_ineligible_result_for_clean_skip(self) -> None:
        pull_request = deferred_pull_request()
        pull_request["user"] = {
            "id": 42,
            "login": "renovate[bot]",
            "type": "Bot",
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
        self.assertEqual(review.PR_ROUTE_NO_SECRET, binding["review_route"])

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
                patch.dict(
                    "os.environ",
                    {"GH_TOKEN": "token", **model_env("openai-responses")},
                    clear=True,
                ),
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

    def test_deferred_marker_route_is_ignored_and_cannot_publish_success(
        self,
    ) -> None:
        workflow_root = Path(__file__).resolve().parents[1] / "workflows"
        router = (workflow_root / "agent-review.yml").read_text(encoding="utf-8")
        reusable = (workflow_root / "reusable-agent-review-jury.yml").read_text(
            encoding="utf-8"
        )
        marker = router.split("\n  deferred-marker:\n", 1)[1].split(
            "\n  no-secret-review:\n", 1
        )[0]
        no_secret_call = router.split("\n  no-secret-review:\n", 1)[1]
        self.assertIn("needs.route.outputs.review-route == 'deferred-secret'", marker)
        self.assertNotIn("deferred-secret", no_secret_call)
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
            api_url = "https://api.github.com"

            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return commit_status_response(path, payload)

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

        metadata = trusted_metadata(run_attempt=2)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            metadata_path = root / "metadata.json"
            output_path = root / "admission.json"
            review.write_json(metadata_path, metadata)
            with (
                patch.object(review, "GitHubClient", return_value=FakeClient()),
                patch("builtins.print"),
                patch.dict(
                    "os.environ",
                    {"GH_TOKEN": "token", **model_configuration_env()},
                    clear=True,
                ),
            ):
                result = review.command_admit_publisher(
                    SimpleNamespace(metadata=metadata_path, output=output_path)
                )
            admission = review.read_json(output_path)

        self.assertEqual(0, result)
        self.assertTrue(admission["admitted"])
        self.assertEqual("current-run-admitted", admission["reason"])

    def test_publisher_admission_requires_complete_model_configuration(self) -> None:
        complete = model_configuration_env()
        cases = [("all-missing", {})]
        for missing in complete:
            cases.append(
                (
                    f"missing-{missing}",
                    {
                        name: value
                        for name, value in complete.items()
                        if name != missing
                    },
                )
            )

        for name, environment in cases:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                metadata_path = root / "metadata.json"
                output_path = root / "admission.json"
                review.write_json(metadata_path, trusted_metadata())
                with (
                    patch.dict("os.environ", environment, clear=True),
                    patch.object(
                        review,
                        "GitHubClient",
                        side_effect=AssertionError(
                            "Invalid model configuration must fail before GitHub access."
                        ),
                    ),
                ):
                    with self.assertRaises(review.ReviewError):
                        review.command_admit_publisher(
                            SimpleNamespace(
                                metadata=metadata_path,
                                output=output_path,
                            )
                        )
                self.assertFalse(output_path.exists())

    def test_publisher_admission_requires_exact_metadata_model_digest(self) -> None:
        for name, digest in (("missing", None), ("mismatch", "f" * 64)):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                metadata_path = root / "metadata.json"
                output_path = root / "admission.json"
                metadata = trusted_metadata()
                if digest is None:
                    del metadata["model_config_sha256"]
                else:
                    metadata["model_config_sha256"] = digest
                review.write_json(metadata_path, metadata)
                with (
                    patch.dict("os.environ", model_configuration_env(), clear=True),
                    patch.object(
                        review,
                        "GitHubClient",
                        side_effect=AssertionError(
                            "Invalid model digest must fail before GitHub access."
                        ),
                    ),
                ):
                    with self.assertRaisesRegex(
                        review.ReviewError,
                        "model configuration binding changed",
                    ):
                        review.command_admit_publisher(
                            SimpleNamespace(
                                metadata=metadata_path,
                                output=output_path,
                            )
                        )
                self.assertFalse(output_path.exists())

    def test_publisher_admission_rejects_stale_head_and_newer_run(self) -> None:
        metadata = trusted_metadata()

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
                        patch.dict("os.environ", model_configuration_env(), clear=True),
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

        metadata = trusted_metadata()
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
                patch.dict("os.environ", model_configuration_env(), clear=True),
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

        metadata = trusted_metadata()
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
                patch.dict("os.environ", model_configuration_env(), clear=True),
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
            api_url = "https://api.github.com"

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
                return commit_status_response(path, payload)

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
            api_url = "https://api.github.com"

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
                return commit_status_response(path, payload)

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
        ].split("\n  deferred-marker:\n", 1)[0]
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
            "if: needs.route.outputs.review-route == 'no-secret'",
            no_secret,
        )
        self.assertNotIn("deferred-secret", no_secret)
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
        ].split("\n  deferred-marker:\n", 1)[0]
        decision_script = textwrap.dedent(
            route_step.split("<<'PY'\n", 1)[1].split("\n          PY", 1)[0]
        )
        cases = (
            (
                review.PR_ROUTE_DEFERRED,
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
        ].split("\n  deferred-marker:\n", 1)[0]
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
            api_url = "https://api.github.com"

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
                return commit_status_response(path, payload)

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
            api_url = "https://api.github.com"

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
                return commit_status_response(path, payload)

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
            "repository_id": REPOSITORY_ID,
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
            "repository_id": REPOSITORY_ID,
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
        for variable in (
            "COCO_AGENT_MODEL_PROTOCOL",
            "COCO_AGENT_MODEL_BASE_URL",
            "COCO_AGENT_MODEL_THINKING",
            "COCO_AGENT_MODEL",
        ):
            self.assertIn(f"{variable}: ${{{{ vars.{variable} }}}}", admission)
        for forbidden in (
            "${{ secrets.",
            "ANTHROPIC_API_KEY",
            "COCO_AGENT_MODEL_API_KEY",
            "environment: coco-agent-model",
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

    def test_route_decision_explains_deferred_and_no_secret(self) -> None:
        human = deferred_pull_request()
        human["user"] = {"id": 1, "login": "patton174", "type": "User"}
        fork = json.loads(json.dumps(human))
        fork["head"]["repo"]["full_name"] = "someone/fork"
        cases = (
            (human, (), review.PR_ROUTE_DEFERRED, "same-repository-human"),
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
            review.PR_ROUTE_DEFERRED,
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
                self.attempts = {"workflow": 0, "run": 0, "pull": 0, "jobs": 0}

            def get_json(self, path: str) -> dict:
                if path == (
                    f"repos/{REPOSITORY}/actions/workflows/"
                    f"{review.DEFERRED_WORKFLOW_FILE}"
                ):
                    key, value = "workflow", deferred_workflow()
                elif path == f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}":
                    key, value = "run", deferred_workflow_run()
                elif path == f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}":
                    key, value = "pull", deferred_pull_request()
                elif path == (
                    f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}/jobs"
                    "?filter=latest&per_page=100"
                ):
                    key, value = "jobs", deferred_source_jobs()
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
        self.assertEqual(
            {"workflow": 2, "run": 2, "pull": 2, "jobs": 2}, client.attempts
        )
        self.assertEqual(4, sleeper.call_count)

    def test_deferred_binding_accepts_only_protected_source_events(self) -> None:
        self.assertEqual(
            frozenset({"pull_request_target", "pull_request_review"}),
            review.DEFERRED_WORKFLOW_EVENTS,
        )

        class SourceRunClient:
            def __init__(self, event: str) -> None:
                self.run = deferred_workflow_run()
                self.run["event"] = event

            def get_json(self, path: str) -> dict:
                if path == (
                    f"repos/{REPOSITORY}/actions/workflows/"
                    f"{review.DEFERRED_WORKFLOW_FILE}"
                ):
                    return deferred_workflow()
                if path == f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}":
                    return self.run
                if path == f"repos/{REPOSITORY}/pulls/{DEFERRED_PR_NUMBER}":
                    return deferred_pull_request()
                if path == (
                    f"repos/{REPOSITORY}/actions/runs/{SOURCE_RUN_ID}/jobs"
                    "?filter=latest&per_page=100"
                ):
                    return deferred_source_jobs()
                raise AssertionError(f"Unexpected GET path: {path}")

        for event in review.DEFERRED_WORKFLOW_EVENTS:
            with self.subTest(event=event):
                binding = review.deferred_review_candidate(
                    SourceRunClient(event),
                    REPOSITORY,
                    REPOSITORY_ID,
                    SOURCE_RUN_ID,
                    deferred_config(),
                )
                self.assertTrue(binding["eligible"])

        with self.assertRaisesRegex(
            review.ReviewError, "workflow run binding is invalid"
        ):
            review.deferred_review_candidate(
                SourceRunClient("push"),
                REPOSITORY,
                REPOSITORY_ID,
                SOURCE_RUN_ID,
                deferred_config(),
            )

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
        self.assertIn("ALLOW_DEFERRED: ${{ true }}", deferred)
        self.assertIn("deferred_args+=(--allow-deferred)", deferred)
        self.assertIn('--source-run-id "${SOURCE_RUN_ID}"', deferred)
        self.assertIn("base_sha: ${{ steps.binding.outputs.base_sha }}", deferred)
        self.assertIn("EXPECTED_BASE_SHA: ${{ needs.bind.outputs.base_sha }}", deferred)
        self.assertEqual(
            1,
            router.count(
                "expected_base_sha: ${{ github.event.pull_request.base.sha }}"
            ),
        )
        self.assertIn(
            "/ head ${{ github.event.pull_request.head.sha }} / base ", router
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
            api_url = "https://api.github.com"

            def __init__(self) -> None:
                self.sent: list[dict] = []

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                del method
                self.sent.append(payload)
                return commit_status_response(path, payload)

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
                    patch.dict("os.environ", model_configuration_env(), clear=True),
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
                patch.dict("os.environ", model_configuration_env(), clear=True),
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
                patch.dict("os.environ", model_configuration_env(), clear=True),
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
        with patch.dict("os.environ", model_env("anthropic-messages"), clear=True):
            client = review.AgentModelClient(config())
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

        with patch.dict("os.environ", model_env("anthropic-messages"), clear=True):
            client = review.AgentModelClient(config())
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
        with patch.dict("os.environ", model_env("anthropic-messages"), clear=True):
            client = review.AgentModelClient(config())
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
        with patch.dict("os.environ", model_env("anthropic-messages"), clear=True):
            client = review.AgentModelClient(config())
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
        with patch.dict("os.environ", model_env("anthropic-messages"), clear=True):
            client = review.AgentModelClient(config())
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
            model_env("anthropic-messages", "http://relay.invalid"),
            clear=True,
        ):
            with self.assertRaises(review.ReviewError):
                review.AgentModelClient(config())

    def test_model_endpoint_normalization_for_both_protocols(self) -> None:
        cases = [
            (
                "anthropic-messages",
                "https://models.example.invalid",
                "https://models.example.invalid/v1/messages",
            ),
            (
                "anthropic-messages",
                "https://models.example.invalid/v1/",
                "https://models.example.invalid/v1/messages",
            ),
            (
                "openai-responses",
                "https://models.example.invalid",
                "https://models.example.invalid/v1/responses",
            ),
            (
                "openai-responses",
                "https://models.example.invalid/proxy/v1/",
                "https://models.example.invalid/proxy/v1/responses",
            ),
            (
                "openai-chat-completions",
                "https://models.example.invalid/proxy/v1/",
                "https://models.example.invalid/proxy/v1/chat/completions",
            ),
        ]
        for protocol, base_url, expected in cases:
            with self.subTest(protocol=protocol, base_url=base_url):
                self.assertEqual(
                    expected, review.model_api_endpoint(protocol, base_url)
                )

    def test_model_configuration_digest_is_canonical_and_excludes_api_key(
        self,
    ) -> None:
        origin = model_env("openai-responses")
        versioned = model_env("openai-responses", "https://models.example.invalid/v1/")
        versioned["COCO_AGENT_MODEL_API_KEY"] = "rotated-api-key"
        with patch.dict("os.environ", origin, clear=True):
            material = review.model_configuration()
            original_digest = review.model_configuration_sha256()
        with patch.dict("os.environ", versioned, clear=True):
            rotated_digest = review.model_configuration_sha256()

        self.assertEqual(
            {
                "protocol": "openai-responses",
                "base_url": "https://models.example.invalid/v1",
                "model": "review-model",
                "thinking": "auto",
            },
            material,
        )
        self.assertEqual(original_digest, rotated_digest)
        serialized = review.canonical_json(material)
        self.assertNotIn("test-api-key", serialized)
        self.assertNotIn("rotated-api-key", serialized)

    def test_model_configuration_binding_rejects_protocol_base_and_model_drift(
        self,
    ) -> None:
        baseline = model_env("openai-responses")
        with patch.dict("os.environ", baseline, clear=True):
            digest = review.model_configuration_sha256()
        context = bound_context()
        context["binding"]["model_config_sha256"] = digest
        context = review.bind_context(context)

        changes = [
            {"COCO_AGENT_MODEL_PROTOCOL": "anthropic-messages"},
            {
                "COCO_AGENT_MODEL_BASE_URL": (
                    "https://different-models.example.invalid/v1"
                )
            },
            {"COCO_AGENT_MODEL": "different-review-model"},
            {"COCO_AGENT_MODEL_THINKING": "disabled"},
        ]
        for change in changes:
            environment = {**baseline, **change}
            with self.subTest(change=change):
                with patch.dict("os.environ", environment, clear=True):
                    with self.assertRaisesRegex(
                        review.ReviewError, "configuration binding changed"
                    ):
                        review.require_model_configuration_binding(context["binding"])

        rotated_key = {
            **baseline,
            "COCO_AGENT_MODEL_API_KEY": "rotated-api-key",
        }
        with patch.dict("os.environ", rotated_key, clear=True):
            review.require_model_configuration_binding(context["binding"])
        self.assertNotIn("test-api-key", review.canonical_json(context))
        self.assertNotIn("rotated-api-key", review.canonical_json(context))

    def test_model_client_rejects_invalid_protocol_urls_and_limits(self) -> None:
        invalid_endpoints = [
            ("unsupported", "https://models.example.invalid", "PROTOCOL"),
            ("anthropic-messages", "", "BASE_URL"),
            ("anthropic-messages", "http://models.example.invalid", "HTTPS"),
            (
                "openai-responses",
                "https://user:secret@models.example.invalid",
                "without credentials",
            ),
            (
                "openai-responses",
                "https://models.example.invalid/v1?key=secret",
                "query data",
            ),
            (
                "openai-responses",
                "https://models.example.invalid/v1#fragment",
                "fragments",
            ),
            (
                "openai-responses",
                "https://models.example.invalid/../v1",
                "invalid path",
            ),
            (
                "openai-responses",
                "https://models.example.invalid/proxy",
                "exact v1",
            ),
            (
                "openai-responses",
                "https://models.example.invalid/v1/responses",
                "exact v1",
            ),
        ]
        for protocol, base_url, message in invalid_endpoints:
            with self.subTest(protocol=protocol, base_url=base_url):
                with self.assertRaisesRegex(review.ReviewError, message):
                    review.model_api_endpoint(protocol, base_url)

        with patch.dict("os.environ", model_env("openai-responses"), clear=True):
            with self.assertRaisesRegex(review.ReviewError, "response_bytes"):
                review.AgentModelClient(
                    config(response_bytes=review.MAX_MODEL_RESPONSE_BYTES + 1)
                )
            with self.assertRaisesRegex(review.ReviewError, "timeout"):
                review.AgentModelClient(
                    config(
                        request_timeout_seconds=(
                            review.MAX_MODEL_REQUEST_TIMEOUT_SECONDS + 1
                        )
                    )
                )

    def test_model_client_requires_provider_neutral_configuration(self) -> None:
        required = (
            "COCO_AGENT_MODEL_PROTOCOL",
            "COCO_AGENT_MODEL_BASE_URL",
            "COCO_AGENT_MODEL_THINKING",
            "COCO_AGENT_MODEL",
            "COCO_AGENT_MODEL_API_KEY",
        )
        for missing in required:
            environment = model_env("openai-responses")
            del environment[missing]
            with self.subTest(missing=missing):
                with patch.dict("os.environ", environment, clear=True):
                    with self.assertRaisesRegex(review.ReviewError, missing):
                        review.AgentModelClient(config())

        invalid_values = [
            ("COCO_AGENT_MODEL", "model\nname"),
            ("COCO_AGENT_MODEL_THINKING", "unsupported"),
            ("COCO_AGENT_MODEL_THINKING", "ENABLED"),
            ("COCO_AGENT_MODEL_THINKING", " disabled "),
            ("COCO_AGENT_MODEL_API_KEY", " key"),
            ("COCO_AGENT_MODEL_API_KEY", "key "),
            ("COCO_AGENT_MODEL_API_KEY", "key\nvalue"),
        ]
        for name, invalid in invalid_values:
            environment = model_env("openai-responses")
            environment[name] = invalid
            with self.subTest(name=name, invalid=repr(invalid)):
                with (
                    patch.dict("os.environ", environment, clear=True),
                    patch(
                        "urllib.request.urlopen",
                        side_effect=AssertionError(
                            "invalid model configuration must fail before network"
                        ),
                    ),
                ):
                    with self.assertRaisesRegex(review.ReviewError, name):
                        review.AgentModelClient(config())

    def test_model_client_builds_protocol_specific_requests(self) -> None:
        cases = [
            (
                "anthropic-messages",
                anthropic_envelope(),
                "/v1/messages",
                "max_tokens",
                "x-api-key",
            ),
            (
                "openai-responses",
                openai_envelope(),
                "/v1/responses",
                "max_output_tokens",
                "authorization",
            ),
            (
                "openai-chat-completions",
                openai_chat_envelope(),
                "/v1/chat/completions",
                "max_tokens",
                "authorization",
            ),
        ]
        for protocol, envelope, suffix, token_field, auth_header in cases:
            with self.subTest(protocol=protocol):
                captured: dict[str, object] = {}
                response = FakeModelResponse(json.dumps(envelope).encode())

                def urlopen(request: object, timeout: int) -> FakeModelResponse:
                    captured["request"] = request
                    captured["timeout"] = timeout
                    return response

                with (
                    patch.dict("os.environ", model_env(protocol), clear=True),
                    patch("urllib.request.urlopen", side_effect=urlopen),
                ):
                    client = review.AgentModelClient(config())
                    self.assertEqual(
                        {"ok": True}, client.complete("system", "user", 100)
                    )
                    self.assertEqual(
                        protocol == "anthropic-messages",
                        client.supports_fragment_continuation,
                    )

                request = captured["request"]
                self.assertTrue(request.full_url.endswith(suffix))
                payload = json.loads(request.data)
                self.assertEqual("review-model", payload["model"])
                self.assertEqual(100, payload[token_field])
                self.assertNotIn("test-api-key", request.data.decode())
                headers = {
                    name.lower(): value for name, value in request.header_items()
                }
                self.assertIsNotNone(headers.get(auth_header))
                self.assertEqual(180, captured["timeout"])
                self.assertEqual(1048577, response.read_limit)
                if protocol == "openai-chat-completions":
                    self.assertEqual(
                        [
                            {"role": "system", "content": "system"},
                            {"role": "user", "content": "user"},
                        ],
                        payload["messages"],
                    )
                    self.assertEqual(
                        {"type": "json_object"}, payload["response_format"]
                    )
                    self.assertNotIn("chat_template_kwargs", payload)
                    self.assertIs(payload["stream"], False)
                if protocol == "openai-responses":
                    self.assertIs(payload["store"], False)
                    self.assertIs(payload["stream"], False)
                    self.assertEqual("disabled", payload["truncation"])
                    self.assertNotIn("temperature", payload)
                    self.assertNotIn("tools", payload)

    def test_openai_client_handles_refusal_and_incomplete_responses(self) -> None:
        incomplete = openai_envelope('{"ok":', "incomplete", "max_output_tokens")
        empty_output = {
            "object": "response",
            "status": "incomplete",
            "output": [],
            "incomplete_details": {"reason": "max_output_tokens"},
        }
        refusal = openai_envelope("", "incomplete", "max_output_tokens")
        refusal["output"][1]["content"] = [
            {"type": "unexpected"},
            {"type": "refusal", "refusal": "Cannot review"},
        ]
        refusal["output"].append({"type": "tool_call"})
        content_filtered = openai_envelope("", "incomplete", "content_filter")
        cases = [
            (incomplete, review.RetryableModelOutputError, "max_tokens"),
            (empty_output, review.RetryableModelOutputError, "max_tokens"),
            (refusal, review.ReviewError, "refused"),
            (content_filtered, review.ReviewError, "content_filter"),
        ]
        with patch.dict("os.environ", model_env("openai-responses"), clear=True):
            client = review.AgentModelClient(config())
            for envelope, error_type, message in cases:
                with self.subTest(message=message):
                    response = FakeModelResponse(json.dumps(envelope).encode())
                    with patch("urllib.request.urlopen", return_value=response):
                        with self.assertRaisesRegex(error_type, message) as raised:
                            client.complete("system", "user", 100)
                    if message != "max_tokens":
                        self.assertNotIsInstance(
                            raised.exception, review.RetryableModelOutputError
                        )

    def test_openai_chat_client_rejects_refusal_and_retries_length(self) -> None:
        cases = [
            (
                openai_chat_envelope("", "content_filter"),
                review.ReviewError,
                "content_filter",
            ),
            (
                openai_chat_envelope("", "stop"),
                review.RetryableModelOutputError,
                "no text",
            ),
            (
                openai_chat_envelope('{"ok":', "length"),
                review.RetryableModelOutputError,
                "max_tokens",
            ),
        ]
        with patch.dict("os.environ", model_env("openai-chat-completions"), clear=True):
            client = review.AgentModelClient(config())
            for envelope, error_type, message in cases:
                with self.subTest(message=message):
                    with patch(
                        "urllib.request.urlopen",
                        return_value=FakeModelResponse(json.dumps(envelope).encode()),
                    ):
                        with self.assertRaisesRegex(error_type, message):
                            client.complete("system", "user", 100)

    def test_openai_chat_client_rejects_refusal_tool_calls_and_malformed_envelopes(
        self,
    ) -> None:
        refusal = openai_chat_envelope()
        refusal["choices"][0]["message"] = {
            "role": "assistant",
            "content": None,
            "refusal": "not permitted",
        }
        tool_call = openai_chat_envelope()
        tool_call["choices"][0]["finish_reason"] = "tool_calls"
        tool_call["choices"][0]["message"]["tool_calls"] = []
        malformed = {"object": "chat.completion", "choices": []}
        cases = [
            (refusal, review.ReviewError, "refused"),
            (tool_call, review.ReviewError, "invalid response envelope"),
            (malformed, review.ReviewError, "invalid response envelope"),
        ]
        with patch.dict("os.environ", model_env("openai-chat-completions"), clear=True):
            client = review.AgentModelClient(config())
            for envelope, error_type, message in cases:
                with self.subTest(message=message):
                    with patch(
                        "urllib.request.urlopen",
                        return_value=FakeModelResponse(json.dumps(envelope).encode()),
                    ):
                        with self.assertRaisesRegex(error_type, message):
                            client.complete("system", "user", 100)

    def test_openai_client_accepts_missing_message_status(self) -> None:
        completed = openai_envelope()
        del completed["output"][1]["status"]
        incomplete = openai_envelope('{"ok":', "incomplete", "max_output_tokens")
        del incomplete["output"][1]["status"]
        with patch.dict("os.environ", model_env("openai-responses"), clear=True):
            client = review.AgentModelClient(config())
            with patch(
                "urllib.request.urlopen",
                return_value=FakeModelResponse(json.dumps(completed).encode()),
            ):
                self.assertEqual({"ok": True}, client.complete("system", "user", 100))
            with patch(
                "urllib.request.urlopen",
                return_value=FakeModelResponse(json.dumps(incomplete).encode()),
            ):
                with self.assertRaisesRegex(
                    review.RetryableModelOutputError, "max_tokens"
                ):
                    client.complete("system", "user", 100)

    def test_openai_client_reports_only_safe_response_shape(self) -> None:
        envelope = openai_envelope()
        envelope["secret_value"] = "must-not-appear"
        envelope["output"][1]["role"] = "tool"
        envelope["output"][1]["content"][0]["type"] = "unexpected_block"
        envelope["output"][1]["content"][0]["text"] = "sensitive response text"
        with patch.dict("os.environ", model_env("openai-responses"), clear=True):
            client = review.AgentModelClient(config())
            with patch(
                "urllib.request.urlopen",
                return_value=FakeModelResponse(json.dumps(envelope).encode()),
            ):
                with self.assertRaisesRegex(
                    review.ReviewError,
                    r"shape=.*items=message=1.*roles=other=1.*content=other=1",
                ) as raised:
                    client.complete("system", "user", 100)
        message = str(raised.exception)
        self.assertNotIn("must-not-appear", message)
        self.assertNotIn("sensitive response text", message)

    def test_openai_client_rejects_malformed_response_envelopes(self) -> None:
        completed_with_error = openai_envelope()
        completed_with_error["error"] = {"message": "provider error"}
        completed_with_incomplete_details = openai_envelope()
        completed_with_incomplete_details["incomplete_details"] = {
            "reason": "max_output_tokens"
        }
        completed_with_incomplete_message = openai_envelope()
        completed_with_incomplete_message["output"][1]["status"] = "incomplete"
        incomplete_with_completed_message = openai_envelope(
            '{"ok":', "incomplete", "max_output_tokens"
        )
        incomplete_with_completed_message["output"][1]["status"] = "completed"
        malformed = [
            {},
            {"object": "list", "status": "completed", "output": []},
            {"object": "response", "status": "completed", "output": {}},
            completed_with_error,
            completed_with_incomplete_details,
            completed_with_incomplete_message,
            incomplete_with_completed_message,
            {
                "object": "response",
                "status": "completed",
                "output": [{"type": "message", "content": []}],
            },
            {
                "object": "response",
                "status": "completed",
                "output": [{"type": "tool_call"}],
            },
            {
                "object": "response",
                "status": "completed",
                "output": [
                    {
                        "type": "message",
                        "role": "assistant",
                        "status": "completed",
                        "content": [{"type": "output_text", "text": "{}"}],
                    },
                    {
                        "type": "message",
                        "role": "assistant",
                        "status": "completed",
                        "content": [{"type": "output_text", "text": "{}"}],
                    },
                ],
            },
            {
                "object": "response",
                "status": "completed",
                "output": [
                    {
                        "type": "message",
                        "role": "assistant",
                        "status": "completed",
                        "content": [{"type": "output_text", "text": 1}],
                    }
                ],
            },
            {
                "object": "response",
                "status": "incomplete",
                "output": [],
            },
        ]
        with patch.dict("os.environ", model_env("openai-responses"), clear=True):
            client = review.AgentModelClient(config())
            for envelope in malformed:
                with self.subTest(envelope=envelope):
                    response = FakeModelResponse(json.dumps(envelope).encode())
                    with patch("urllib.request.urlopen", return_value=response):
                        with self.assertRaisesRegex(
                            review.ReviewError, "invalid response envelope"
                        ) as raised:
                            client.complete("system", "user", 100)
                    self.assertNotIsInstance(
                        raised.exception, review.RetryableModelOutputError
                    )

    def test_openai_chat_client_controls_provider_thinking_mode(self) -> None:
        for thinking, enabled in (("enabled", True), ("disabled", False)):
            with self.subTest(thinking=thinking):
                captured: dict[str, object] = {}

                def urlopen(request: object, timeout: int) -> FakeModelResponse:
                    captured["request"] = request
                    captured["timeout"] = timeout
                    return FakeModelResponse(
                        json.dumps(openai_chat_envelope()).encode()
                    )

                environment = model_env("openai-chat-completions")
                environment["COCO_AGENT_MODEL_THINKING"] = thinking
                with (
                    patch.dict("os.environ", environment, clear=True),
                    patch("urllib.request.urlopen", side_effect=urlopen),
                ):
                    self.assertEqual(
                        {"ok": True},
                        review.AgentModelClient(config()).complete(
                            "system", "user", 100
                        ),
                    )
                payload = json.loads(captured["request"].data)
                self.assertEqual(
                    {"enable_thinking": enabled},
                    payload["chat_template_kwargs"],
                )

    def _assert_openai_message_less_max_output_retries(
        self, output: list[dict]
    ) -> None:
        incomplete = {
            "object": "response",
            "status": "incomplete",
            "output": output,
            "incomplete_details": {"reason": "max_output_tokens"},
        }
        responses = [
            FakeModelResponse(json.dumps(incomplete).encode()),
            FakeModelResponse(
                json.dumps(openai_envelope('{"required":true}')).encode()
            ),
        ]
        requests: list[bytes | None] = []

        def urlopen(request: object, timeout: int) -> FakeModelResponse:
            del timeout
            requests.append(request.data)
            return responses.pop(0)

        with (
            patch.dict("os.environ", model_env("openai-responses"), clear=True),
            patch("urllib.request.urlopen", side_effect=urlopen),
        ):
            client = review.AgentModelClient(config())
            with patch("builtins.print") as warning:
                result = review.complete_with_shape_repair(
                    client,
                    "protected system",
                    '{"task":"review"}',
                    100,
                    lambda value: review.require_report_fields(
                        value, {"required"}, "Test"
                    ),
                )

        self.assertEqual({"required": True}, result)
        self.assertEqual(2, len(requests))
        self.assertEqual(requests[0], requests[1])
        warning.assert_called_once()

    def test_openai_reasoning_only_max_output_uses_fresh_retry(self) -> None:
        self._assert_openai_message_less_max_output_retries(
            [{"type": "reasoning", "summary": []}]
        )

    def test_openai_empty_output_max_output_uses_fresh_retry(self) -> None:
        self._assert_openai_message_less_max_output_retries([])

    def test_model_client_bounds_response_bytes_and_timeout(self) -> None:
        response = FakeModelResponse(b"123456789")
        captured: dict[str, int] = {}

        def urlopen(_request: object, timeout: int) -> FakeModelResponse:
            captured["timeout"] = timeout
            return response

        with (
            patch.dict("os.environ", model_env("openai-responses"), clear=True),
            patch("urllib.request.urlopen", side_effect=urlopen),
        ):
            client = review.AgentModelClient(
                config(response_bytes=8, request_timeout_seconds=7)
            )
            with self.assertRaisesRegex(review.ReviewError, "bounded size"):
                client.complete("system", "user", 100)
        self.assertEqual(9, response.read_limit)
        self.assertEqual(7, captured["timeout"])

    def test_model_api_key_does_not_leak_from_provider_failures(self) -> None:
        api_key = "coco-model-api-key-sentinel-for-redaction"
        environment = model_env("openai-responses")
        environment["COCO_AGENT_MODEL_API_KEY"] = api_key
        cases = [
            (
                "http",
                review.urllib.error.HTTPError(
                    "https://models.example.invalid/v1/responses",
                    401,
                    api_key,
                    None,
                    None,
                ),
                config(),
            ),
            ("url", review.urllib.error.URLError(api_key), config()),
            ("timeout", TimeoutError(api_key), config()),
            (
                "oversize",
                FakeModelResponse(api_key.encode()),
                config(response_bytes=8),
            ),
            (
                "invalid-json",
                FakeModelResponse(api_key.encode()),
                config(),
            ),
        ]

        for name, provider_result, client_config in cases:
            stdout = io.StringIO()
            stderr = io.StringIO()
            with self.subTest(name=name):
                try:
                    patcher = (
                        patch("urllib.request.urlopen", side_effect=provider_result)
                        if isinstance(provider_result, BaseException)
                        else patch(
                            "urllib.request.urlopen", return_value=provider_result
                        )
                    )
                    with (
                        patch.dict("os.environ", environment, clear=True),
                        patcher,
                        patch("sys.stdout", new=stdout),
                        patch("sys.stderr", new=stderr),
                    ):
                        client = review.AgentModelClient(client_config)
                        with self.assertRaises(review.ReviewError) as raised:
                            client.complete("system", "user", 100)
                    surfaces = (
                        str(raised.exception),
                        repr(raised.exception),
                        "".join(traceback.format_exception(raised.exception)),
                        stdout.getvalue(),
                        stderr.getvalue(),
                    )
                    self.assertFalse(
                        any(api_key in surface for surface in surfaces),
                        f"{name} exposed the model API key.",
                    )
                finally:
                    if isinstance(provider_result, review.urllib.error.HTTPError):
                        provider_result.close()

    def test_model_api_key_does_not_leak_from_retry_warning_or_traceback(
        self,
    ) -> None:
        api_key = "coco-model-api-key-sentinel-for-retry-redaction"
        environment = model_env("openai-responses")
        environment["COCO_AGENT_MODEL_API_KEY"] = api_key
        incomplete = openai_envelope(
            api_key,
            "incomplete",
            "max_output_tokens",
        )
        responses = [
            FakeModelResponse(json.dumps(incomplete).encode())
            for _ in range(review.MODEL_COMPLETION_MAX_ATTEMPTS)
        ]
        stdout = io.StringIO()
        stderr = io.StringIO()

        with (
            patch.dict("os.environ", environment, clear=True),
            patch("urllib.request.urlopen", side_effect=responses),
            patch("sys.stdout", new=stdout),
            patch("sys.stderr", new=stderr),
        ):
            client = review.AgentModelClient(config())
            with self.assertRaises(review.RetryableModelOutputError) as raised:
                review.complete_with_shape_repair(
                    client,
                    "protected system",
                    '{"task":"review"}',
                    100,
                    lambda value: value,
                )

        surfaces = (
            str(raised.exception),
            repr(raised.exception),
            "".join(traceback.format_exception(raised.exception)),
            stdout.getvalue(),
            stderr.getvalue(),
        )
        self.assertFalse(
            any(api_key in surface for surface in surfaces),
            "Retry diagnostics exposed the model API key.",
        )

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

    def test_openai_incomplete_response_retries_without_fragment_continuation(
        self,
    ) -> None:
        class FakeOpenAIClient:
            supports_fragment_continuation = False

            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                if len(self.calls) == 1:
                    raise review.RetryableModelOutputError(
                        "incomplete",
                        stop_reason="max_tokens",
                        partial_text='{"required":',
                    )
                return {"required": True}

        client = FakeOpenAIClient()
        expected = ("protected system", '{"task":"review"}', 100)
        with patch("builtins.print"):
            result = review.complete_with_shape_repair(
                client,
                expected[0],
                expected[1],
                expected[2],
                lambda value: review.require_report_fields(value, {"required"}, "Test"),
            )
        self.assertEqual({"required": True}, result)
        self.assertEqual([expected, expected], client.calls)

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

    def test_cross_review_shape_repair_uses_digest_without_previous_response(
        self,
    ) -> None:
        context = bound_context()
        finding_id = "correctness:f1"
        invalid = raw_verifier_report("evidence-verifier", context, finding_id)
        invalid["evidence"] = "untrusted-previous-response-" * 1800
        del invalid["verifications"][0]["reason"]
        valid = raw_verifier_report("evidence-verifier", context, finding_id)
        expected = verifier_report("evidence-verifier", context, finding_id)

        class FakeClient:
            def __init__(self) -> None:
                self.responses = [invalid, valid]
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return self.responses.pop(0)

        original_task = '{"task":"cross-review"}'
        client = FakeClient()
        with patch("builtins.print"):
            result = review.complete_with_shape_repair(
                client,
                "protected cross-review system",
                original_task,
                100,
                lambda value: review.validate_raw_cross_report(
                    value, "evidence-verifier", context, {finding_id}
                ),
                cross_review_fresh_retry=True,
            )

        self.assertEqual(expected, result)
        self.assertEqual(2, len(client.calls))
        self.assertIn(
            "Protected cross-review fresh protocol correction", client.calls[1][0]
        )
        correction = json.loads(client.calls[1][1])
        self.assertEqual(
            {"original_task", "previous_response_sha256", "validator_message"},
            set(correction),
        )
        self.assertEqual({"task": "cross-review"}, correction["original_task"])
        self.assertEqual(
            review.sha256_text(review.canonical_json(invalid)),
            correction["previous_response_sha256"],
        )
        self.assertIn("missing", correction["validator_message"])
        self.assertNotIn("previous_response", correction)
        self.assertNotIn("untrusted-previous-response", client.calls[1][1])

    def test_cross_review_out_of_range_first_response_is_fixed_by_bounded_correction(
        self,
    ) -> None:
        context = bound_context()
        finding_id = "correctness:f1"
        invalid = raw_verifier_report("evidence-verifier", context, finding_id)
        invalid["verifications"][0]["evidence_refs"][0].update(
            start_line=999, end_line=999
        )
        valid = raw_verifier_report("evidence-verifier", context, finding_id)

        class FakeClient:
            def __init__(self) -> None:
                self.responses = [invalid, valid]
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return self.responses.pop(0)

        client = FakeClient()
        with patch("builtins.print"):
            result = review.complete_with_shape_repair(
                client,
                "protected cross-review system",
                '{"task":"cross-review"}',
                100,
                lambda value: review.validate_raw_cross_report(
                    value, "evidence-verifier", context, {finding_id}
                ),
                cross_review_fresh_retry=True,
            )

        self.assertEqual(valid, result)
        self.assertEqual(2, len(client.calls))
        self.assertIn("continuous interval", client.calls[1][0])
        self.assertIn("start_line equal to end_line", client.calls[1][0])
        correction = json.loads(client.calls[1][1])
        self.assertIn("canonical line coverage", correction["validator_message"])
        self.assertNotIn("previous_response", correction)

    def test_cross_review_out_of_range_correction_still_fails_closed(self) -> None:
        context = bound_context()
        finding_id = "correctness:f1"
        invalid = raw_verifier_report("evidence-verifier", context, finding_id)
        invalid["verifications"][0]["evidence_refs"][0].update(
            start_line=999, end_line=999
        )

        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return invalid

        client = FakeClient()
        with patch("builtins.print"):
            with self.assertRaisesRegex(
                review.ReportShapeError, "canonical line coverage"
            ):
                review.complete_with_shape_repair(
                    client,
                    "protected cross-review system",
                    '{"task":"cross-review"}',
                    100,
                    lambda value: review.validate_raw_cross_report(
                        value, "evidence-verifier", context, {finding_id}
                    ),
                    cross_review_fresh_retry=True,
                )

        self.assertEqual(review.MODEL_COMPLETION_MAX_ATTEMPTS, len(client.calls))

    def test_cross_review_contradicted_check_error_names_missing_evidence(self) -> None:
        checks = {
            "claim": "CONTRADICTED",
            "severity": "SUPPORTED",
            "anchor": "SUPPORTED",
            "trigger": "SUPPORTED",
            "impact": "CONTRADICTED",
            "change_scope": "IN_SCOPE",
        }
        evidence_refs = [
            {
                "trust_domain": "head-code",
                "path": "src/Foo.java",
                "start_line": 1,
                "end_line": 1,
                "checks": ["anchor", "trigger"],
            }
        ]

        with self.assertRaisesRegex(
            review.ReportShapeError, r"missing=\['claim', 'impact'\]"
        ):
            review.derive_verifier_action(checks, evidence_refs)

    def test_cross_review_contradicted_check_is_repaired_by_bounded_retry(
        self,
    ) -> None:
        context = bound_context()
        finding_id = "correctness:f1"
        invalid = raw_verifier_report("evidence-verifier", context, finding_id)
        invalid["verifications"][0]["claim"] = "CONTRADICTED"
        invalid["verifications"][0]["evidence_refs"][0]["checks"].remove("claim")
        valid = raw_verifier_report("evidence-verifier", context, finding_id)

        class FakeClient:
            def __init__(self) -> None:
                self.responses = [invalid, valid]
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return self.responses.pop(0)

        client = FakeClient()
        with patch("builtins.print"):
            result = review.complete_with_shape_repair(
                client,
                "protected cross-review system",
                '{"task":"cross-review"}',
                100,
                lambda value: review.validate_raw_cross_report(
                    value, "evidence-verifier", context, {finding_id}
                ),
                cross_review_fresh_retry=True,
            )

        self.assertEqual(valid, result)
        self.assertEqual(2, len(client.calls))
        correction = json.loads(client.calls[1][1])
        self.assertIn("missing", correction["validator_message"])
        self.assertIn("claim", correction["validator_message"])
        self.assertIn("exact check", client.calls[1][0])

    def test_cross_review_contradicted_check_retry_remains_fail_closed(self) -> None:
        context = bound_context()
        finding_id = "correctness:f1"
        invalid = raw_verifier_report("evidence-verifier", context, finding_id)
        invalid["verifications"][0]["claim"] = "CONTRADICTED"
        invalid["verifications"][0]["evidence_refs"][0]["checks"].remove("claim")

        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return invalid

        client = FakeClient()
        with patch("builtins.print"):
            with self.assertRaisesRegex(review.ReportShapeError, "missing"):
                review.complete_with_shape_repair(
                    client,
                    "protected cross-review system",
                    '{"task":"cross-review"}',
                    100,
                    lambda value: review.validate_raw_cross_report(
                        value, "evidence-verifier", context, {finding_id}
                    ),
                    cross_review_fresh_retry=True,
                )

        self.assertEqual(review.MODEL_COMPLETION_MAX_ATTEMPTS, len(client.calls))

    def test_cross_review_empty_and_non_json_retries_are_bounded_and_json_only(
        self,
    ) -> None:
        for label in ("empty", "non-json"):
            with self.subTest(response=label):

                class FakeClient:
                    def __init__(self) -> None:
                        self.calls: list[tuple[str, str, int]] = []

                    def complete(self, system: str, user: str, max_tokens: int) -> dict:
                        self.calls.append((system, user, max_tokens))
                        raise review.RetryableModelOutputError(
                            label,
                            stop_reason="end_turn",
                            response_chars=0 if label == "empty" else 12,
                            accumulated_chars=0 if label == "empty" else 12,
                        )

                client = FakeClient()
                with patch("builtins.print"):
                    with self.assertRaises(review.RetryableModelOutputError):
                        review.complete_with_shape_repair(
                            client,
                            "protected cross-review system",
                            '{"task":"cross-review"}',
                            100,
                            lambda value: value,
                            cross_review_fresh_retry=True,
                        )

                self.assertEqual(
                    review.MODEL_COMPLETION_MAX_ATTEMPTS, len(client.calls)
                )
                for system, user, _ in client.calls[1:]:
                    self.assertIn("one complete replacement JSON object", system)
                    self.assertIn("no\nMarkdown", system)
                    self.assertIn("at most one evidence reference", system)
                    self.assertIn("start_line equal to end_line", system)
                    self.assertEqual('{"task":"cross-review"}', user)

    def test_cross_review_max_tokens_retry_discards_large_partial_response(
        self,
    ) -> None:
        partial = "x" * 45030

        class FakeClient:
            supports_fragment_continuation = False

            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                if len(self.calls) == 1:
                    raise review.RetryableModelOutputError(
                        "truncated",
                        stop_reason="max_tokens",
                        response_chars=len(partial),
                        accumulated_chars=len(partial),
                        partial_text=partial,
                    )
                return {"required": True}

        client = FakeClient()
        original_task = '{"task":"cross-review"}'
        with patch("builtins.print"):
            result = review.complete_with_shape_repair(
                client,
                "protected cross-review system",
                original_task,
                100,
                lambda value: review.require_report_fields(value, {"required"}, "Test"),
                cross_review_fresh_retry=True,
            )

        self.assertEqual({"required": True}, result)
        self.assertEqual(2, len(client.calls))
        self.assertIn(
            "Protected cross-review fresh completion correction", client.calls[1][0]
        )
        self.assertEqual(original_task, client.calls[1][1])
        self.assertNotIn("partial_response", client.calls[1][1])
        self.assertNotIn(partial, client.calls[1][1])

    def test_cross_review_unknown_source_id_fails_closed_after_three_reports(
        self,
    ) -> None:
        context = bound_context()
        finding_id = "correctness:f1"
        invalid = raw_verifier_report("evidence-verifier", context, finding_id)
        invalid["verifications"][0]["evidence_refs"][0]["source_id"] = "S999"
        catalog = review.canonical_json(review.context_evidence_catalog(context))

        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return invalid

        client = FakeClient()
        with patch("builtins.print"):
            with self.assertRaisesRegex(review.ReportShapeError, "source_id"):
                review.complete_with_shape_repair(
                    client,
                    "protected cross-review system\n" + catalog,
                    '{"task":"cross-review"}',
                    100,
                    lambda value: review.validate_raw_cross_report(
                        value, "evidence-verifier", context, {finding_id}
                    ),
                    cross_review_fresh_retry=True,
                )

        self.assertEqual(review.MODEL_COMPLETION_MAX_ATTEMPTS, len(client.calls))
        for system, user, _ in client.calls[1:]:
            self.assertIn(catalog, system)
            correction = json.loads(user)
            self.assertNotIn("previous_response", correction)
            self.assertIn("previous_response_sha256", correction)
            self.assertNotIn("S999", user)

    def test_specialist_and_chair_keep_fragment_continuation_by_default(self) -> None:
        class FragmentClient(review.AgentModelClient):
            supports_fragment_continuation = True

            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []
                self.responses = [
                    review.ModelTextResponse('{"required":', "max_tokens"),
                    review.ModelTextResponse("true}", "end_turn"),
                ]

            def complete_fragment(
                self, system: str, user: str, max_tokens: int
            ) -> review.ModelTextResponse:
                self.calls.append((system, user, max_tokens))
                return self.responses.pop(0)

        for role in ("specialist", "chair"):
            with self.subTest(role=role):
                client = FragmentClient()
                result = review.complete_with_shape_repair(
                    client,
                    f"protected {role} system",
                    '{"task":"review"}',
                    100,
                    lambda value: review.require_report_fields(
                        value, {"required"}, "Test"
                    ),
                )
                self.assertEqual({"required": True}, result)
                self.assertIn("Protected truncation continuation", client.calls[1][0])
                self.assertEqual(
                    '{"required":', json.loads(client.calls[1][1])["partial_response"]
                )

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
        cross_invalid = raw_verifier_report("evidence-verifier", context, finding_id)
        cross_invalid["verifications"] = "not-an-array"
        cross_valid = raw_verifier_report("evidence-verifier", context, finding_id)

        chair_valid = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "PASS",
            "summary": "No independently confirmed blockers.",
            "confirmed_blocker_ids": [],
            "actionable_groups": [],
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
                lambda value: review.validate_raw_cross_report(
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

    def test_chair_question_budget_correction_converges(self) -> None:
        context = bound_context()
        finding_id = "correctness:f1"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            specialists = root / "specialists"
            verifiers = root / "verifiers"
            prompt_root = root / "prompts-root"
            config_path = root / "config.json"
            context_path = root / "context.json"
            output_json = root / "chair.json"
            output_markdown = root / "chair.md"
            specialists.mkdir()
            verifiers.mkdir()
            (prompt_root / "prompts").mkdir(parents=True)
            (prompt_root / "prompts/chair.md").write_text(
                "Return strict JSON.", encoding="utf-8"
            )

            with patch.dict(
                "os.environ", model_env("openai-chat-completions"), clear=True
            ):
                context["binding"]["model_config_sha256"] = (
                    review.model_configuration_sha256()
                )
                review.bind_context(context)

            chair_config = config()
            chair_config["roles"] = {
                "chair": {"id": "chair", "lens": "Bounded test synthesis."}
            }
            for role in review.role_map(chair_config, "specialists"):
                report = specialist_report(role, context)
                if role != "correctness":
                    report["findings"] = []
                review.write_json(specialists / f"{role}.json", report)
            for role in review.role_map(chair_config, "verifiers"):
                review.write_json(
                    verifiers / f"{role}.json",
                    verifier_report(role, context, finding_id),
                )
            review.write_json(config_path, chair_config)
            review.write_json(context_path, context)

            valid = {
                "schema_version": 1,
                "role": "chair",
                "head_sha": HEAD_SHA,
                "context_sha256": context["binding"]["context_sha256"],
                "verdict": "BLOCK",
                "summary": "The deterministic consensus confirms the cited blocker.",
                "confirmed_blocker_ids": [finding_id],
                "actionable_groups": [
                    {
                        "primary_finding_id": finding_id,
                        "duplicate_finding_ids": [],
                    }
                ],
                "questions": [
                    "Which focused regression test proves the stated trigger?"
                ],
            }
            too_many_questions = dict(valid)
            too_many_questions["questions"] = [
                f"Clarification question {index}" for index in range(6)
            ]

            with (
                patch.object(review, "AgentModelClient") as client_class,
                patch.dict(
                    "os.environ", model_env("openai-chat-completions"), clear=True
                ),
            ):
                client_class.return_value.complete.side_effect = [
                    too_many_questions,
                    valid,
                ]
                result = review.command_chair(
                    SimpleNamespace(
                        config=config_path,
                        prompt_root=prompt_root,
                        context=context_path,
                        specialists=specialists,
                        verifiers=verifiers,
                        output_json=output_json,
                        output_markdown=output_markdown,
                    )
                )

            calls = client_class.return_value.complete.call_args_list
            self.assertEqual(0, result)
            self.assertEqual(2, len(calls))
            self.assertIn("at most 5 non-empty strings", calls[0][0][0])
            self.assertIn("at most 5 non-empty strings", calls[1][0][0])
            self.assertIn("Apply every protected numeric output", calls[1][0][0])
            correction = json.loads(calls[1][0][1])
            self.assertEqual(too_many_questions, correction["previous_response"])
            self.assertEqual(valid, review.read_json(output_json)["chair"])

    def test_chair_group_contract_correction_replaces_blocker_followup_group(
        self,
    ) -> None:
        context = bound_context()
        blocker_id = "correctness:f1"
        followup_id = "architecture-api:f1"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            specialists = root / "specialists"
            verifiers = root / "verifiers"
            config_path = root / "config.json"
            context_path = root / "context.json"
            output_json = root / "chair.json"
            output_markdown = root / "chair.md"
            specialists.mkdir()
            verifiers.mkdir()

            with patch.dict(
                "os.environ", model_env("openai-chat-completions"), clear=True
            ):
                context["binding"]["model_config_sha256"] = (
                    review.model_configuration_sha256()
                )
                review.bind_context(context)

            chair_config = config()
            chair_config["roles"] = {
                "chair": {"id": "chair", "lens": "Bounded test synthesis."}
            }
            for role in review.role_map(chair_config, "specialists"):
                report = specialist_report(role, context)
                if role == "architecture-api":
                    report["findings"][0]["severity"] = "P2"
                elif role != "correctness":
                    report["findings"] = []
                review.write_json(specialists / f"{role}.json", report)
            for role in review.role_map(chair_config, "verifiers"):
                report = verifier_report(role, context, blocker_id)
                review.write_json(verifiers / f"{role}.json", report)
            review.write_json(config_path, chair_config)
            review.write_json(context_path, context)

            valid = {
                "schema_version": 1,
                "role": "chair",
                "head_sha": HEAD_SHA,
                "context_sha256": context["binding"]["context_sha256"],
                "verdict": "BLOCK",
                "summary": "The deterministic consensus confirms the cited blocker.",
                "confirmed_blocker_ids": [blocker_id],
                "actionable_groups": [
                    {
                        "primary_finding_id": blocker_id,
                        "duplicate_finding_ids": [],
                    }
                ],
                "questions": [],
            }
            blocking_as_followup = dict(valid)
            blocking_as_followup["actionable_groups"] = [
                {
                    "primary_finding_id": followup_id,
                    "duplicate_finding_ids": [blocker_id],
                }
            ]

            with (
                patch.object(review, "AgentModelClient") as client_class,
                patch.dict(
                    "os.environ", model_env("openai-chat-completions"), clear=True
                ),
            ):
                client_class.return_value.complete.side_effect = [
                    blocking_as_followup,
                    valid,
                ]
                result = review.command_chair(
                    SimpleNamespace(
                        config=config_path,
                        prompt_root=Path(__file__).resolve().parents[1]
                        / "agent-review",
                        context=context_path,
                        specialists=specialists,
                        verifiers=verifiers,
                        output_json=output_json,
                        output_markdown=output_markdown,
                    )
                )

            calls = client_class.return_value.complete.call_args_list
            self.assertEqual(0, result)
            self.assertEqual(2, len(calls))
            for phrase in (
                "may cite only canonical source finding IDs",
                "can never be selected as follow-up work",
                "one kind, one severity, and one deterministic semantic identity",
                "Never combine IDs with different kinds or severities",
                "one group per finding with an empty",
            ):
                self.assertIn(phrase, calls[0][0][0])
                self.assertIn(phrase, calls[1][0][0])
            self.assertIn(
                "Reapply every role-specific protected source-ID", calls[1][0][0]
            )
            correction = json.loads(calls[1][0][1])
            self.assertEqual(blocking_as_followup, correction["previous_response"])
            self.assertEqual(valid, review.read_json(output_json)["chair"])

    def test_chair_group_contract_fails_closed_after_three_invalid_outputs(
        self,
    ) -> None:
        context = bound_context()
        blocker = specialist_report("correctness", context)["findings"][0]
        followup = json.loads(json.dumps(blocker))
        followup["id"] = "architecture-api:f1"
        followup["severity"] = "P2"
        consensus = {
            "confirmed": [{"finding": blocker}, {"finding": followup}],
            "challenged": [],
            "unverified": [],
        }
        invalid = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "BLOCK",
            "summary": "The deterministic consensus confirms the cited blocker.",
            "confirmed_blocker_ids": [blocker["id"]],
            "actionable_groups": [
                {
                    "primary_finding_id": followup["id"],
                    "duplicate_finding_ids": [blocker["id"]],
                }
            ],
            "questions": [],
        }

        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return invalid

        client = FakeClient()
        protected_system = (
            "`actionable_groups` may cite only canonical source finding IDs. "
            "Confirmed P0/P1 IDs can never be selected as follow-up work."
        )
        with patch("builtins.print"):
            with self.assertRaisesRegex(review.ReportShapeError, "mixes finding kinds"):
                review.complete_with_shape_repair(
                    client,
                    protected_system,
                    '{"task":"chair"}',
                    100,
                    lambda value: review.validate_chair(
                        value,
                        consensus,
                        context,
                        {followup["id"]},
                    ),
                )

        self.assertEqual(review.MODEL_COMPLETION_MAX_ATTEMPTS, len(client.calls))
        self.assertTrue(
            all(
                "Confirmed P0/P1 IDs can never be selected as follow-up work." in system
                for system, _, _ in client.calls
            )
        )

    def test_chair_question_budget_fails_closed_after_invalid_corrections(self) -> None:
        context = bound_context()
        consensus = {"confirmed": [], "challenged": [], "unverified": []}
        valid = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "PASS",
            "summary": "No independently confirmed blockers remain.",
            "confirmed_blocker_ids": [],
            "actionable_groups": [],
            "questions": [],
        }
        too_many_questions = dict(valid)
        too_many_questions["questions"] = [
            f"Clarification question {index}" for index in range(6)
        ]

        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return too_many_questions

        client = FakeClient()
        protected_system = (
            "The questions array must contain at most 5 non-empty strings."
        )
        with patch("builtins.print"):
            with self.assertRaisesRegex(
                review.ReportShapeError, "Chair returned too many questions"
            ):
                review.complete_with_shape_repair(
                    client,
                    protected_system,
                    '{"task":"chair"}',
                    100,
                    lambda value: review.validate_chair(
                        value, consensus, context, set(), 5
                    ),
                )

        self.assertEqual(review.MODEL_COMPLETION_MAX_ATTEMPTS, len(client.calls))
        self.assertTrue(
            all(
                "at most 5 non-empty strings" in system for system, _, _ in client.calls
            )
        )

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
        verifier = raw_verifier_report("evidence-verifier", context, finding_id)
        chair = {
            "schema_version": 1,
            "role": "chair",
            "head_sha": HEAD_SHA,
            "context_sha256": context["binding"]["context_sha256"],
            "verdict": "PASS",
            "summary": "No independently confirmed blockers.",
            "confirmed_blocker_ids": [],
            "actionable_groups": [],
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
                lambda value: review.validate_raw_cross_report(
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
        class FragmentClient(review.AgentModelClient):
            supports_fragment_continuation = True

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
        class FragmentClient(review.AgentModelClient):
            supports_fragment_continuation = True

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
        class FragmentClient(review.AgentModelClient):
            supports_fragment_continuation = True

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
        class FragmentClient(review.AgentModelClient):
            supports_fragment_continuation = True

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
        largest_path = ""
        largest_size = 0
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
                    selected_size = sum(len(source["content"]) for source in sources)
                    if selected_size > largest_size:
                        largest_path = changed_path
                        largest_size = selected_size

        self.assertEqual(".github/agent-review/probe", largest_path)
        self.assertEqual(56_629, largest_size)
        self.assertEqual(7_371, limit - largest_size)
        self.assertGreaterEqual((limit - largest_size) * 100, largest_size * 13)

    def test_production_policy_route_fails_closed_above_configured_budget(self) -> None:
        repository_root = Path(__file__).resolve().parents[2]
        value = review.load_config(repository_root / ".github/agent-review/config.json")
        limit = review.normalized_limits(value)["policy_chars"]
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "AGENTS.md").write_text("x" * (limit + 1), encoding="utf-8")
            bounded = config(policy_chars=limit)
            with self.assertRaisesRegex(
                review.ReviewError, "exceeds the context budget"
            ):
                review.collect_policy(root, bounded, ["probe"], [])

    def test_invalid_trusted_artifacts_publish_both_failure_gates(
        self,
    ) -> None:
        class StatusClient:
            api_url = "https://api.github.com"

            def __init__(self) -> None:
                self.sent: list[tuple[str, str, dict]] = []

            def get_json(self, path: str) -> dict:
                if path == f"repos/{REPOSITORY}/pulls/1":
                    return {
                        "state": "open",
                        "head": {"sha": HEAD_SHA},
                        "base": {"sha": BASE_SHA, "ref": "main"},
                    }
                if path == f"repos/{REPOSITORY}/commits/{HEAD_SHA}/status":
                    return combined_ownership_status(42)
                raise AssertionError(f"Unexpected status read: {path}")

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.sent.append((method, path, payload))
                return commit_status_response(path, payload)

        class AgentClient:
            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                return []

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                if method != "POST" or not path.endswith("/comments"):
                    raise AssertionError(f"Unexpected Agent write: {method} {path}")
                return {
                    "id": 7,
                    "body": payload["body"],
                    "user": app_actor(),
                }

        for mode in ("missing", "malformed", "stale"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                metadata_path = root / "metadata.json"
                metadata = {
                    **trusted_metadata(),
                    "review_route": review.PR_ROUTE_DEFERRED,
                    "deferred": True,
                    "source_run_id": 7,
                    "context_sha256": "a" * 64,
                    "protocol_sha256": "b" * 64,
                }
                review.write_json(metadata_path, metadata)
                required = {
                    "config": root / "config.json",
                    "context": root / "context.json",
                    "specialists": root / "specialists",
                    "verifiers": root / "verifiers",
                    "final_json": root / "final.json",
                    "final_markdown": root / "final.md",
                }
                for name, path in required.items():
                    if name.endswith("s"):
                        path.mkdir()
                    else:
                        path.write_text("{}", encoding="utf-8")
                review.write_json(
                    required["context"],
                    {
                        "binding": {
                            "head_sha": HEAD_SHA,
                            "base_sha": BASE_SHA,
                            "context_sha256": metadata["context_sha256"],
                            "protocol_sha256": metadata["protocol_sha256"],
                            "model_config_sha256": MODEL_CONFIG_SHA256,
                        }
                    },
                )
                review.write_json(required["final_json"], {"verdict": "PASS"})
                required["final_markdown"].write_text(
                    "validated report\n", encoding="utf-8"
                )
                continuity = root / "continuity"
                if mode != "missing":
                    continuity.mkdir()
                status_client = StatusClient()
                with (
                    patch.object(
                        review,
                        "GitHubClient",
                        side_effect=[status_client, AgentClient()],
                    ),
                    patch.object(review, "revalidate_model_configuration_if_available"),
                    patch.object(review, "validate_context"),
                    patch.object(
                        review,
                        "load_config",
                        return_value={"max_actionable_issue_groups": 8},
                    ),
                    patch.object(
                        review,
                        "load_reports",
                        side_effect=lambda path: (
                            (_ for _ in ()).throw(
                                review.ReviewError("malformed continuity")
                            )
                            if mode == "malformed" and path == continuity
                            else []
                        ),
                    ),
                    patch.object(
                        review,
                        "validate_final_artifact",
                        return_value="validated report\n",
                    ),
                    patch.object(review, "continuity_adoptions") as adoptions,
                    patch.object(
                        review,
                        "deferred_review_binding",
                        return_value={"base_sha": BASE_SHA},
                    ),
                    patch("builtins.print"),
                    patch.dict(
                        "os.environ",
                        {
                            "GH_TOKEN": "token",
                            "AGENT_GH_TOKEN": "agent-token",
                            "COCO_AGENT_APP_LOGIN": APP_LOGIN,
                            "COCO_AGENT_APP_BOT_ID": str(APP_BOT_ID),
                        },
                        clear=True,
                    ),
                ):
                    if mode == "stale":
                        adoptions.side_effect = review.ReviewError("stale continuity")
                    result = review.command_publish(
                        SimpleNamespace(
                            metadata=metadata_path,
                            **required,
                            continuity=continuity,
                            run_url="https://github.example/runs/42",
                        )
                    )

                self.assertEqual(1, result)
                self.assertEqual(2, len(status_client.sent))
                self.assertEqual(
                    {review.ISSUE_STATUS_CONTEXT, review.STATUS_CONTEXT},
                    {payload["context"] for _, _, payload in status_client.sent},
                )
                self.assertTrue(
                    all(
                        payload["state"] == "failure"
                        for _, _, payload in status_client.sent
                    )
                )


class CrossHeadContinuityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.finding = {
            "file": ".github/scripts/agent_review.py",
            "category": "governance",
            "severity": "P1",
            "start_line": 100,
            "end_line": 110,
        }
        self.anchor = review.continuity_anchor(self.finding)
        material = {
            "anchor": self.anchor,
            "context_sha256": "a" * 64,
            "current_head_sha": HEAD_SHA,
            "first_head_sha": BASE_SHA,
            "previous_group_id": "v2-" + "1" * 64,
            "previous_head_sha": BASE_SHA,
            "previous_issue_number": 7,
            "protocol_sha256": "b" * 64,
            "pull_request": 60,
            "repository": REPOSITORY,
            "repository_id": REPOSITORY_ID,
            "schema_version": 2,
            "verification_proof_sha256": "c" * 64,
            "verifier_roles": ["evidence-verifier", "policy-skeptic"],
        }
        self.candidate = {
            **material,
            "candidate_sha256": review.sha256_text(review.canonical_json(material)),
        }
        self.context = {
            "binding": {
                "context_sha256": "a" * 64,
                "head_sha": HEAD_SHA,
                "protocol_sha256": "b" * 64,
            },
            "trusted": {"continuity_candidates": [self.candidate]},
        }
        self.groups = [
            {
                "current_group_id": "v2-" + "2" * 64,
                "anchor": self.anchor,
            }
        ]

    def relationship(self, action: str = "ADOPT", **changes: object) -> dict:
        values: dict[str, object] = {
            "schema_version": 2,
            "action": action,
            "current_group_id": self.groups[0]["current_group_id"],
            "current_anchor": self.anchor,
            "candidate_sha256": self.candidate["candidate_sha256"]
            if action == "ADOPT"
            else None,
            "previous_group_id": self.candidate["previous_group_id"]
            if action == "ADOPT"
            else None,
            "previous_issue_number": self.candidate["previous_issue_number"]
            if action == "ADOPT"
            else None,
            "previous_anchor": self.candidate["anchor"] if action == "ADOPT" else None,
        }
        return {**values, **changes}

    def report(self, role: str, relationship: dict | None = None) -> dict:
        return {
            "schema_version": 2,
            "role": role,
            "binding": self.context["binding"],
            "relationships": [relationship or self.relationship()],
        }

    def task(self) -> str:
        return review.canonical_json(
            {
                "continuity_candidates": self.context["trusted"][
                    "continuity_candidates"
                ],
                "current_groups": self.groups,
            }
        )

    def complete_continuity_with_repair(
        self, client: object, role: str
    ) -> dict[str, object]:
        return review.complete_with_shape_repair(
            client,
            "protected continuity verifier system",
            self.task(),
            100,
            lambda value: review.validate_continuity_report(
                value, role, self.context, self.groups
            ),
            cross_review_fresh_retry=True,
            return_validated_report=True,
        )

    def test_continuity_max_tokens_retries_from_fresh_complete_task(self) -> None:
        role = "evidence-verifier"
        valid = self.report(role)
        original_task = review.canonical_json(
            {
                "continuity_candidates": self.context["trusted"][
                    "continuity_candidates"
                ],
                "current_groups": self.groups,
            }
        )

        class FragmentClient(review.AgentModelClient):
            supports_fragment_continuation = True

            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []
                self.responses = [
                    review.ModelTextResponse('{"discarded":', "max_tokens"),
                    review.ModelTextResponse(json.dumps(valid), "end_turn"),
                ]

            def complete_fragment(
                self, system: str, user: str, max_tokens: int
            ) -> review.ModelTextResponse:
                self.calls.append((system, user, max_tokens))
                return self.responses.pop(0)

        client = FragmentClient()
        with patch("builtins.print"):
            result = review.complete_with_shape_repair(
                client,
                "protected continuity verifier system",
                original_task,
                100,
                lambda value: review.validate_continuity_report(
                    value, role, self.context, self.groups
                ),
                cross_review_fresh_retry=True,
            )

        self.assertEqual(valid, result)
        self.assertEqual(2, len(client.calls))

        self.assertIn(
            "Protected cross-review fresh completion correction", client.calls[1][0]
        )
        self.assertEqual(original_task, client.calls[1][1])
        self.assertNotIn("partial_response", client.calls[1][1])

    def test_continuity_non_adopt_relationships_normalize_missing_null_fields(
        self,
    ) -> None:
        for action in ("REJECT", "INSUFFICIENT"):
            with self.subTest(action=action):
                report = self.report("evidence-verifier", self.relationship(action))
                for name in (
                    "schema_version",
                    "candidate_sha256",
                    "previous_anchor",
                    "previous_group_id",
                    "previous_issue_number",
                ):
                    del report["relationships"][0][name]

                normalized = review.validate_continuity_report(
                    report, "evidence-verifier", self.context, self.groups
                )
                relationship = normalized["relationships"][0]
                self.assertEqual(
                    {
                        "action",
                        "candidate_sha256",
                        "current_anchor",
                        "current_group_id",
                        "previous_anchor",
                        "previous_group_id",
                        "previous_issue_number",
                        "schema_version",
                    },
                    set(relationship),
                )
                self.assertEqual(action, relationship["action"])
                self.assertIsNone(relationship["candidate_sha256"])
                self.assertIsNone(relationship["previous_anchor"])
                self.assertIsNone(relationship["previous_group_id"])
                self.assertIsNone(relationship["previous_issue_number"])

    def test_continuity_adopt_normalizes_missing_relationship_schema_version(
        self,
    ) -> None:
        report = self.report("evidence-verifier", self.relationship("ADOPT"))
        del report["relationships"][0]["schema_version"]

        normalized = review.validate_continuity_report(
            report, "evidence-verifier", self.context, self.groups
        )

        self.assertEqual(
            review.CONTINUITY_SCHEMA_VERSION,
            normalized["relationships"][0]["schema_version"],
        )
        self.assertEqual(
            self.candidate["candidate_sha256"],
            normalized["relationships"][0]["candidate_sha256"],
        )

    def test_continuity_adopt_requires_integer_previous_issue_number(self) -> None:
        for value in (True, 7.0, "7"):
            with self.subTest(value=value):
                report = self.report(
                    "evidence-verifier",
                    self.relationship("ADOPT", previous_issue_number=value),
                )
                with self.assertRaisesRegex(
                    review.ReportShapeError, "previous Issue number is invalid"
                ):
                    review.validate_continuity_report(
                        report, "evidence-verifier", self.context, self.groups
                    )

    def test_continuity_non_adopt_candidate_is_normalized_to_null(self) -> None:
        for role in ("evidence-verifier", "policy-skeptic"):
            for action in ("REJECT", "INSUFFICIENT"):
                with self.subTest(role=role, action=action):
                    report = self.report(
                        role,
                        self.relationship(
                            action,
                            candidate_sha256=self.candidate["candidate_sha256"],
                            previous_group_id=self.candidate["previous_group_id"],
                            previous_issue_number=self.candidate[
                                "previous_issue_number"
                            ],
                            previous_anchor=self.candidate["anchor"],
                        ),
                    )
                    normalized = review.validate_continuity_report(
                        report, role, self.context, self.groups
                    )
                    relationship = normalized["relationships"][0]
                    self.assertEqual(action, relationship["action"])
                    for field in (
                        "candidate_sha256",
                        "previous_group_id",
                        "previous_issue_number",
                        "previous_anchor",
                    ):
                        self.assertIsNone(relationship[field])

    def test_continuity_non_adopt_candidate_fixture_normalizes_without_retry(
        self,
    ) -> None:
        role = "policy-skeptic"
        invalid = self.report(
            role,
            self.relationship(
                "REJECT",
                candidate_sha256=self.candidate["candidate_sha256"],
                previous_group_id=self.candidate["previous_group_id"],
                previous_issue_number=self.candidate["previous_issue_number"],
                previous_anchor=self.candidate["anchor"],
            ),
        )

        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []
                self.responses = [invalid]

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return self.responses.pop(0)

        client = FakeClient()
        with patch("builtins.print"):
            result = self.complete_continuity_with_repair(client, role)

        self.assertEqual(1, len(client.calls))
        relationship = result["relationships"][0]
        self.assertEqual("REJECT", relationship["action"])
        self.assertIsNone(relationship["candidate_sha256"])
        self.assertIsNone(relationship["previous_group_id"])
        self.assertIsNone(relationship["previous_issue_number"])
        self.assertIsNone(relationship["previous_anchor"])

    def test_continuity_shape_repair_uses_digest_without_previous_response(
        self,
    ) -> None:
        role = "policy-skeptic"
        valid = self.report(role)
        invalid = self.report(role)
        invalid["relationships"] = "untrusted-continuity-response"
        original_task = review.canonical_json(
            {
                "continuity_candidates": self.context["trusted"][
                    "continuity_candidates"
                ],
                "current_groups": self.groups,
            }
        )

        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []
                self.responses = [invalid, valid]

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return self.responses.pop(0)

        client = FakeClient()
        with patch("builtins.print"):
            result = review.complete_with_shape_repair(
                client,
                "protected continuity verifier system",
                original_task,
                100,
                lambda value: review.validate_continuity_report(
                    value, role, self.context, self.groups
                ),
                cross_review_fresh_retry=True,
            )

        self.assertEqual(valid, result)
        self.assertEqual(2, len(client.calls))
        correction = json.loads(client.calls[1][1])
        self.assertEqual(
            {"original_task", "previous_response_sha256", "validator_message"},
            set(correction),
        )
        self.assertEqual(json.loads(original_task), correction["original_task"])
        self.assertEqual(
            review.sha256_text(review.canonical_json(invalid)),
            correction["previous_response_sha256"],
        )
        self.assertNotIn("previous_response", correction)
        self.assertNotIn("untrusted-continuity-response", client.calls[1][1])

    def test_continuity_shape_repair_fails_closed_after_three_invalid_reports(
        self,
    ) -> None:
        role = "evidence-verifier"
        invalid = self.report(role)
        invalid["relationships"] = "invalid"
        original_task = review.canonical_json(
            {
                "continuity_candidates": self.context["trusted"][
                    "continuity_candidates"
                ],
                "current_groups": self.groups,
            }
        )

        class FakeClient:
            def __init__(self) -> None:
                self.calls: list[tuple[str, str, int]] = []

            def complete(self, system: str, user: str, max_tokens: int) -> dict:
                self.calls.append((system, user, max_tokens))
                return invalid

        client = FakeClient()
        with patch("builtins.print"):
            with self.assertRaises(review.ReportShapeError):
                review.complete_with_shape_repair(
                    client,
                    "protected continuity verifier system",
                    original_task,
                    100,
                    lambda value: review.validate_continuity_report(
                        value, role, self.context, self.groups
                    ),
                    cross_review_fresh_retry=True,
                )

        self.assertEqual(review.MODEL_COMPLETION_MAX_ATTEMPTS, len(client.calls))
        for _, user, _ in client.calls[1:]:
            correction = json.loads(user)
            self.assertIn("previous_response_sha256", correction)
            self.assertNotIn("previous_response", correction)

    def test_continuity_current_anchor_binds_to_supplied_group(self) -> None:
        role = "evidence-verifier"
        for value in ({"file": "invalid"}, {**self.anchor, "start_line": 99}):
            with self.subTest(current_anchor=value):
                report = self.report(role, self.relationship(current_anchor=value))
                normalized = review.validate_continuity_report(
                    report, role, self.context, self.groups
                )

                self.assertEqual(
                    self.anchor,
                    normalized["relationships"][0]["current_anchor"],
                )
                self.assertEqual(
                    self.candidate["candidate_sha256"],
                    normalized["relationships"][0]["candidate_sha256"],
                )

    def test_continuity_current_anchor_is_bound_for_normal_relationship(self) -> None:
        normalized = review.validate_continuity_report(
            self.report("evidence-verifier"),
            "evidence-verifier",
            self.context,
            self.groups,
        )
        relationship = normalized["relationships"][0]
        self.assertEqual(
            self.groups[0]["current_group_id"], relationship["current_group_id"]
        )
        self.assertEqual(self.anchor, relationship["current_anchor"])
        self.assertEqual(
            self.candidate["previous_group_id"], relationship["previous_group_id"]
        )
        self.assertEqual(
            self.candidate["previous_issue_number"],
            relationship["previous_issue_number"],
        )
        self.assertEqual(self.candidate["anchor"], relationship["previous_anchor"])

    def test_continuity_model_shape_errors_repair_after_binding(self) -> None:
        role = "policy-skeptic"
        cases = {
            "previous_anchor": lambda report: report["relationships"][0].update(
                {"previous_anchor": {"file": "invalid"}}
            ),
            "candidate_sha256": lambda report: report["relationships"][0].update(
                {"candidate_sha256": "not-a-digest"}
            ),
            "top_level_fields": lambda report: report.update({"extra": True}),
        }

        for name, corrupt in cases.items():
            with self.subTest(shape=name):
                valid = self.report(role)
                invalid = json.loads(json.dumps(valid))
                corrupt(invalid)

                class FakeClient:
                    def __init__(self) -> None:
                        self.calls: list[tuple[str, str, int]] = []
                        self.responses = [invalid, valid]

                    def complete(self, system: str, user: str, max_tokens: int) -> dict:
                        self.calls.append((system, user, max_tokens))
                        return self.responses.pop(0)

                client = FakeClient()
                with patch("builtins.print"):
                    result = self.complete_continuity_with_repair(client, role)

                self.assertEqual(valid, result)
                self.assertEqual(2, len(client.calls))

    def test_continuity_model_shape_errors_fail_closed_after_three_attempts(
        self,
    ) -> None:
        role = "policy-skeptic"
        cases = {
            "previous_anchor": lambda report: report["relationships"][0].update(
                {"previous_anchor": {"file": "invalid"}}
            ),
            "candidate_sha256": lambda report: report["relationships"][0].update(
                {"candidate_sha256": "not-a-digest"}
            ),
            "top_level_fields": lambda report: report.update({"extra": True}),
        }

        for name, corrupt in cases.items():
            with self.subTest(shape=name):
                invalid = self.report(role)
                corrupt(invalid)

                class FakeClient:
                    def __init__(self) -> None:
                        self.calls: list[tuple[str, str, int]] = []

                    def complete(self, system: str, user: str, max_tokens: int) -> dict:
                        self.calls.append((system, user, max_tokens))
                        return invalid

                client = FakeClient()
                with patch("builtins.print"):
                    with self.assertRaises(review.ReportShapeError):
                        self.complete_continuity_with_repair(client, role)

                self.assertEqual(
                    review.MODEL_COMPLETION_MAX_ATTEMPTS, len(client.calls)
                )

    def test_continuity_identity_and_binding_errors_fail_without_repair(self) -> None:
        role = "evidence-verifier"
        cases = {
            "schema_version": lambda report: report.update({"schema_version": 1}),
            "head_sha": lambda report: report["binding"].update({"head_sha": "c" * 40}),
            "context_sha256": lambda report: report["binding"].update(
                {"context_sha256": "d" * 64}
            ),
        }

        for name, corrupt in cases.items():
            with self.subTest(protected=name):
                invalid = json.loads(json.dumps(self.report(role)))
                corrupt(invalid)

                class FakeClient:
                    def __init__(self) -> None:
                        self.calls: list[tuple[str, str, int]] = []

                    def complete(self, system: str, user: str, max_tokens: int) -> dict:
                        self.calls.append((system, user, max_tokens))
                        return invalid

                client = FakeClient()
                with patch("builtins.print"):
                    with self.assertRaises(review.ReviewError) as caught:
                        self.complete_continuity_with_repair(client, role)

                self.assertNotIsInstance(caught.exception, review.ReportShapeError)
                self.assertEqual(1, len(client.calls))

    def test_continuity_report_role_is_normalized_from_protected_task_for_each_verifier(
        self,
    ) -> None:
        for expected_role, model_role in (
            ("evidence-verifier", "policy-skeptic"),
            ("evidence-verifier", None),
            ("evidence-verifier", "evidence-verifier"),
            ("policy-skeptic", "evidence-verifier"),
            ("policy-skeptic", None),
            ("policy-skeptic", "policy-skeptic"),
        ):
            with self.subTest(expected_role=expected_role, model_role=model_role):
                report = self.report(model_role or expected_role)
                if model_role is None:
                    del report["role"]
                normalized = review.validate_continuity_report(
                    report, expected_role, self.context, self.groups
                )
                self.assertEqual(expected_role, normalized["role"])

    def test_continuity_report_role_rejects_present_non_string_values(self) -> None:
        for value in (None, 1, ["evidence-verifier"]):
            with self.subTest(value=value):
                report = self.report("evidence-verifier")
                report["role"] = value
                with self.assertRaisesRegex(review.ReviewError, "identity is invalid"):
                    review.validate_continuity_report(
                        report, "evidence-verifier", self.context, self.groups
                    )

    def test_command_continuity_enables_cross_review_fresh_retry(self) -> None:
        role = "evidence-verifier"
        result = self.report(role)
        p2_groups = [{**self.groups[0], "severity": "P2"}]
        args = SimpleNamespace(
            config=Path("config.json"),
            context=Path("context.json"),
            specialists=Path("specialists"),
            verifiers=Path("verifiers"),
            final_json=Path("final.json"),
            role=role,
            prompt_root=Path("prompts"),
            output=Path("continuity.json"),
        )
        with (
            patch.object(review, "load_config", return_value=config()),
            patch.object(review, "read_json", side_effect=[self.context, {}]),
            patch.object(review, "validate_context"),
            patch.object(review, "require_model_configuration_binding"),
            patch.object(review, "load_reports", side_effect=[[], []]),
            patch.object(review, "validate_final_artifact"),
            patch.object(review, "continuity_groups", return_value=p2_groups),
            patch.object(review, "prompt_text", return_value="strict JSON"),
            patch.object(review, "trusted_policy_text", return_value="policy"),
            patch.object(review, "AgentModelClient", return_value=object()),
            patch.object(
                review, "complete_with_shape_repair", return_value=result
            ) as complete,
            patch.object(review, "write_json") as write_json,
        ):
            self.assertEqual(0, review.command_continuity(args))

        self.assertTrue(complete.call_args.kwargs["cross_review_fresh_retry"])
        self.assertIn(
            "including when every actionable group is P2/P3",
            complete.call_args.args[1],
        )
        self.assertIn(
            "never return the ordinary verifier `NOT_NEEDED` report",
            complete.call_args.args[1],
        )
        write_json.assert_called_once_with(args.output, result)

    def test_p2_p3_actionable_continuity_requires_complete_relationships(self) -> None:
        group = {**self.groups[0], "severity": "P3"}
        valid = self.report("evidence-verifier")
        valid["relationships"][0]["current_group_id"] = group["current_group_id"]
        review.validate_continuity_report(
            valid, "evidence-verifier", self.context, [group]
        )

        not_needed = {
            "schema_version": 1,
            "role": "evidence-verifier",
            "head_sha": self.context["binding"]["head_sha"],
            "context_sha256": self.context["binding"]["context_sha256"],
            "status": "NOT_NEEDED",
            "evidence": "No P0/P1 blocker candidates were present.",
            "reviews": [],
            "context_gaps": [],
        }
        with self.assertRaises(review.ReviewError):
            review.validate_continuity_report(
                not_needed, "evidence-verifier", self.context, [group]
            )

    def test_v2_marker_is_canonical_and_v1_remains_legacy(self) -> None:
        marker = review.finding_issue_marker_v2(
            REPOSITORY,
            REPOSITORY_ID,
            60,
            BASE_SHA,
            HEAD_SHA,
            self.groups[0]["current_group_id"],
            self.anchor,
            "a" * 64,
            "b" * 64,
            "c" * 64,
        )
        parsed = review.parse_finding_issue_marker(marker + "\nDetails\n")
        self.assertEqual(2, parsed["schema_version"])
        self.assertEqual(HEAD_SHA, review.finding_marker_current_head(parsed))
        malformed = marker.replace(
            '"schema_version":2', '"schema_version":2,"extra":true'
        )
        with self.assertRaises(review.ReviewError):
            review.parse_finding_issue_marker(malformed)
        legacy = review.parse_finding_issue_marker(
            review.finding_issue_marker(60, BASE_SHA, "v1-" + "d" * 64)
        )
        self.assertEqual(1, legacy["schema_version"])
        relationship = review.continuity_relationship_marker(
            REPOSITORY,
            REPOSITORY_ID,
            60,
            BASE_SHA,
            BASE_SHA,
            self.candidate["previous_group_id"],
            7,
            self.candidate["candidate_sha256"],
            self.candidate,
            self.anchor,
            self.groups[0]["current_group_id"],
            self.anchor,
            HEAD_SHA,
            "a" * 64,
            "b" * 64,
            "c" * 64,
        )
        self.assertEqual(
            [relationship], review.continuity_relationship_markers(relationship)
        )
        with self.assertRaises(review.ReviewError):
            review.continuity_relationship_markers(
                relationship.replace('"schema_version":2', '"schema_version":2,"x":1')
            )
        with self.assertRaisesRegex(review.ReviewError, "does not match"):
            review.continuity_relationship_markers(
                relationship.replace(self.candidate["candidate_sha256"], "d" * 64, 1)
            )

    def test_candidate_inventory_rejects_duplicate_canonical_anchor(self) -> None:
        duplicate_material = {
            **{
                key: value
                for key, value in self.candidate.items()
                if key
                not in {
                    "candidate_sha256",
                    "previous_group_id",
                    "previous_issue_number",
                }
            },
            "previous_group_id": "v2-" + "3" * 64,
            "previous_issue_number": 8,
        }
        duplicate = {
            **duplicate_material,
            "candidate_sha256": review.sha256_text(
                review.canonical_json(duplicate_material)
            ),
        }
        context = {
            **self.context,
            "trusted": {"continuity_candidates": [self.candidate, duplicate]},
        }

        class Client:
            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                return []

        with self.assertRaisesRegex(review.ReviewError, "duplicate anchors"):
            review.synchronize_finding_issues(
                Client(),
                REPOSITORY,
                REPOSITORY_ID,
                60,
                HEAD_SHA,
                [],
                (1, 1),
                APP_LOGIN,
                APP_BOT_ID,
                "https://github.example/runs/1",
                "https://github.example",
                lambda: {},
                continuity_context=context,
                continuity_adopted={},
                continuity_proof_sha256="f" * 64,
            )

    def test_v2_finding_recovery_revalidates_without_legacy_head_access(self) -> None:
        stable_id = self.groups[0]["current_group_id"]
        marker = review.finding_issue_marker_v2(
            REPOSITORY,
            REPOSITORY_ID,
            60,
            BASE_SHA,
            HEAD_SHA,
            stable_id,
            self.anchor,
            "a" * 64,
            "b" * 64,
            "c" * 64,
        )
        operation = review.operation_marker(
            REPOSITORY,
            REPOSITORY_ID,
            APP_LOGIN,
            APP_BOT_ID,
            (42, 1),
            60,
            HEAD_SHA,
            stable_id,
            "finding-issue-create",
        )
        issue = finding_issue_resource(7, marker, operation)

        class Client:
            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                return [issue]

        def verify(value: object) -> dict:
            return review.verify_finding_issue(
                value,
                APP_LOGIN,
                APP_BOT_ID,
                marker,
                operation,
                "title",
                issue["body"],
                {review.FINDING_ISSUE_LABEL},
                "open",
                expected_number=7,
            )

        recovered = review.finding_issue_recovery_candidate(
            Client(), REPOSITORY, APP_LOGIN, APP_BOT_ID, marker, operation, verify
        )
        self.assertEqual(review.RecoveryState.EXACT, recovered.state)

    def test_current_head_v2_direct_reuse_requires_complete_marker_binding(
        self,
    ) -> None:
        stable_id = self.groups[0]["current_group_id"]
        finding = {
            **self.finding,
            "title": "Bound current-head finding",
            "claim": "The protected marker must bind the current group.",
            "trigger": "Run the trusted publisher.",
            "impact": "An unrelated Issue could otherwise be rewritten.",
            "evidence": "The canonical anchor differs.",
            "verification": "Check exact marker fields before PATCH.",
        }
        actionable = {
            "stable_id": stable_id,
            "legacy_finding_ids": [],
            "source_id": "source",
            "duplicate_source_ids": [],
            "kind": "confirmed-blocker",
            "finding": finding,
        }

        def marker(
            repository: str = REPOSITORY,
            repository_id: int = REPOSITORY_ID,
            anchor: dict | None = None,
        ) -> str:
            return review.finding_issue_marker_v2(
                repository,
                repository_id,
                60,
                BASE_SHA,
                HEAD_SHA,
                stable_id,
                anchor or self.anchor,
                "a" * 64,
                "b" * 64,
                "c" * 64,
            )

        class Client:
            def __init__(self, issue: dict) -> None:
                self.issue = issue
                self.writes: list[tuple[str, str, dict]] = []

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                return [self.issue]

            def get_json(self, path: str) -> dict:
                return {"name": review.FINDING_ISSUE_LABEL}

            def send_json(self, method: str, path: str, payload: dict) -> dict:
                self.writes.append((method, path, payload))
                self.issue.update(payload)
                self.issue["labels"] = [{"name": name} for name in payload["labels"]]
                return self.issue

        drifted_anchor = {**self.anchor, "start_line": 99}
        drifted_anchor["locator_sha256"] = review.sha256_text(
            review.canonical_json(
                {
                    key: value
                    for key, value in drifted_anchor.items()
                    if key != "locator_sha256"
                }
            )
        )
        cases = {
            "exact": marker(),
            "cross-repository": marker(repository="other/repository"),
            "repository-id": marker(repository_id=REPOSITORY_ID + 1),
            "anchor-drift": marker(anchor=drifted_anchor),
        }
        for name, body in cases.items():
            with self.subTest(case=name):
                client = Client(
                    {
                        "number": 7,
                        "title": "old",
                        "body": body,
                        "state": "open",
                        "labels": [{"name": review.FINDING_ISSUE_LABEL}],
                        "user": app_actor(),
                    }
                )
                synchronized = review.synchronize_finding_issues(
                    client,
                    REPOSITORY,
                    REPOSITORY_ID,
                    60,
                    HEAD_SHA,
                    [actionable],
                    (1, 1),
                    APP_LOGIN,
                    APP_BOT_ID,
                    "https://github.example/runs/1",
                    "https://github.example",
                    lambda: {},
                    continuity_context=self.context,
                    continuity_adopted={},
                    continuity_proof_sha256="f" * 64,
                )
                if name == "exact":
                    self.assertEqual(1, len(client.writes))
                    self.assertNotIn("retained", synchronized[0])
                else:
                    self.assertEqual([], client.writes)
                    self.assertTrue(synchronized[0]["retained"])

    def test_candidate_collection_rejects_non_ancestor_cross_binding_legacy_and_actor(
        self,
    ) -> None:
        marker = review.finding_issue_marker_v2(
            REPOSITORY,
            REPOSITORY_ID,
            60,
            BASE_SHA,
            BASE_SHA,
            self.candidate["previous_group_id"],
            self.anchor,
            "a" * 64,
            "b" * 64,
            "c" * 64,
        )
        trusted_issue = {
            "number": 7,
            "body": marker,
            "labels": [{"name": review.FINDING_ISSUE_LABEL}],
            "user": app_actor(),
        }

        class Client:
            def __init__(self, issue: dict, ancestor: bool = True) -> None:
                self.issue = issue
                self.ancestor = ancestor

            def paginate(self, path: str, limit: int = 1000) -> list[dict]:
                return [self.issue]

            def get_json(self, path: str) -> dict:
                return {
                    "status": "ahead" if self.ancestor else "diverged",
                    "ahead_by": 1 if self.ancestor else 0,
                    "behind_by": 0 if self.ancestor else 1,
                }

        self.assertEqual(
            1,
            len(
                review.collect_continuity_candidates(
                    Client(trusted_issue),
                    REPOSITORY,
                    REPOSITORY_ID,
                    60,
                    HEAD_SHA,
                    APP_LOGIN,
                    APP_BOT_ID,
                )
            ),
        )
        cases = {
            "cross-pr": review.finding_issue_marker_v2(
                REPOSITORY,
                REPOSITORY_ID,
                61,
                BASE_SHA,
                BASE_SHA,
                self.candidate["previous_group_id"],
                self.anchor,
                "a" * 64,
                "b" * 64,
                "c" * 64,
            ),
            "cross-repo": review.finding_issue_marker_v2(
                "other/repository",
                REPOSITORY_ID,
                60,
                BASE_SHA,
                BASE_SHA,
                self.candidate["previous_group_id"],
                self.anchor,
                "a" * 64,
                "b" * 64,
                "c" * 64,
            ),
            "legacy": review.finding_issue_marker(60, BASE_SHA, "v1-" + "e" * 64),
        }
        for name, body in cases.items():
            with self.subTest(case=name):
                issue = {**trusted_issue, "body": body}
                self.assertEqual(
                    [],
                    review.collect_continuity_candidates(
                        Client(issue),
                        REPOSITORY,
                        REPOSITORY_ID,
                        60,
                        HEAD_SHA,
                        APP_LOGIN,
                        APP_BOT_ID,
                    ),
                )
        self.assertEqual(
            [],
            review.collect_continuity_candidates(
                Client(trusted_issue, ancestor=False),
                REPOSITORY,
                REPOSITORY_ID,
                60,
                HEAD_SHA,
                APP_LOGIN,
                APP_BOT_ID,
            ),
        )
        untrusted = {**trusted_issue, "user": app_actor(APP_BOT_ID + 1, "other[bot]")}
        self.assertEqual(
            [],
            review.collect_continuity_candidates(
                Client(untrusted),
                REPOSITORY,
                REPOSITORY_ID,
                60,
                HEAD_SHA,
                APP_LOGIN,
                APP_BOT_ID,
            ),
        )

    def test_two_verifier_adoption_is_one_to_one_and_fail_closed(self) -> None:
        left = self.report("evidence-verifier")
        right = self.report("policy-skeptic")
        adopted = review.continuity_adoptions([left, right], self.context, self.groups)
        self.assertEqual(
            self.candidate["candidate_sha256"],
            adopted[self.groups[0]["current_group_id"]]["candidate_sha256"],
        )

        cases = {
            "single-verifier": [left],
            "disagreement": [
                left,
                self.report("policy-skeptic", self.relationship("INSUFFICIENT")),
            ],
            "anchor-drift": [
                left,
                self.report(
                    "policy-skeptic",
                    self.relationship(
                        previous_anchor={**self.anchor, "start_line": 99}
                    ),
                ),
            ],
            "stale-head-binding": [
                {**left, "binding": {**self.context["binding"], "head_sha": BASE_SHA}},
                right,
            ],
        }
        for name, reports in cases.items():
            with self.subTest(case=name):
                if name == "disagreement":
                    self.assertEqual(
                        {},
                        review.continuity_adoptions(reports, self.context, self.groups),
                    )
                else:
                    with self.assertRaises(review.ReviewError):
                        review.continuity_adoptions(reports, self.context, self.groups)

        extra_group = {"current_group_id": "v2-" + "3" * 64, "anchor": self.anchor}
        duplicate_reports = []
        for role in ("evidence-verifier", "policy-skeptic"):
            report = self.report(role)
            relation = self.relationship(
                current_group_id=extra_group["current_group_id"]
            )
            report["relationships"].append(relation)
            duplicate_reports.append(report)
        with self.assertRaisesRegex(review.ReviewError, "Multiple current groups"):
            review.continuity_adoptions(
                duplicate_reports, self.context, [*self.groups, extra_group]
            )

    def test_chair_data_cannot_create_continuity_and_summary_records_lineage(
        self,
    ) -> None:
        self.assertEqual(
            {},
            review.continuity_adoptions(
                [
                    self.report("evidence-verifier", self.relationship("INSUFFICIENT")),
                    self.report("policy-skeptic", self.relationship("INSUFFICIENT")),
                ],
                self.context,
                self.groups,
            ),
        )
        body = review.append_continuity_summary(
            review.COMMENT_MARKER + "\n<!-- agent-jury-run:42:1 -->\nBody\n",
            self.context,
            {self.groups[0]["current_group_id"]: self.relationship()},
            "f" * 64,
        )
        self.assertIn(review.CONTINUITY_SUMMARY_MARKER_PREFIX, body)
        self.assertIn(BASE_SHA, body)
        self.assertIn(HEAD_SHA, body)


if __name__ == "__main__":
    unittest.main(verbosity=2)
