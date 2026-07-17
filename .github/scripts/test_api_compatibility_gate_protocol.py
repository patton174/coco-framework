#!/usr/bin/env python3
"""Offline regression tests for the trusted JAR-only shadow route."""

from __future__ import annotations

import copy
import hashlib
import io
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock
from urllib.parse import urlparse

import api_compatibility_gate_protocol as protocol


REPOSITORY = "coco/framework"
REPOSITORY_ID = 11
FORK = "fork/framework"
PROTECTED_SHA = "a" * 40
CANDIDATE_SHA = "b" * 40
OTHER_SHA = "c" * 40
RUN_ID = 42
ATTEMPT = 3
WORKFLOW_ID = 7
NAMES = [f"coco-{index:02d}.jar" for index in range(32)]


def jar_bytes() -> bytes:
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        archive.writestr("example/Api.class", b"class")
    return stream.getvalue()


def write_policy(root: Path) -> None:
    directory = root / protocol.POLICY_ROOT
    directory.mkdir(parents=True)
    profile = {
        "schema_version": 1,
        "artifacts": [
            {
                "jar": name,
                "coordinate": f"io.github.coco:{name[:-4]}",
                "baseline": index < 20,
            }
            for index, name in enumerate(NAMES)
        ],
    }
    ledger = {
        "schema_version": 1,
        "baselines": [
            {
                "jar": name,
                "url": "https://example.invalid/" + name,
                "size": 5,
                "sha256": "d" * 64,
            }
            for name in NAMES[:20]
        ],
    }
    values = {
        "public-api-profile.json": profile,
        "baseline-ledger.json": ledger,
        "allowlist.json": {"schema_version": 1, "exceptions": []},
        "japicmp-key.json": {
            "schema_version": 1,
            "japicmp": {
                "version": "0.23.1",
                "url": protocol.JAPICMP_URL,
                "size": protocol.JAPICMP_SIZE,
                "sha256": protocol.JAPICMP_SHA256,
            },
        },
    }
    for name, value in values.items():
        (directory / name).write_bytes(protocol.canonical_json(value) + b"\n")


def binding() -> dict:
    return {
        "candidate_sha": CANDIDATE_SHA,
        "source_event": "pull_request",
        "source_run_id": RUN_ID,
        "source_run_attempt": ATTEMPT,
        "producer_outcome": "success",
        "artifact_name": f"{protocol.ARTIFACT_PREFIX}-{CANDIDATE_SHA}-{RUN_ID}-{ATTEMPT}",
    }


def artifact(
    extra: dict[str, bytes] | None = None, names: list[str] | None = None
) -> bytes:
    names = names or NAMES
    jars = {name: jar_bytes() for name in names}
    manifest = {
        "schema_version": 2,
        "kind": "non-authoritative-candidate-jars",
        "candidate_sha": CANDIDATE_SHA,
        "source_event": "pull_request",
        "source_run_id": RUN_ID,
        "source_run_attempt": ATTEMPT,
        "jars": [
            {
                "name": name,
                "size": len(data),
                "sha256": hashlib.sha256(data).hexdigest(),
            }
            for name, data in jars.items()
        ],
    }
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", protocol.canonical_json(manifest) + b"\n")
        for name, data in jars.items():
            archive.writestr(f"jars/{name}", data)
        for name, data in (extra or {}).items():
            archive.writestr(name, data)
    return stream.getvalue()


class CandidateArtifactTests(unittest.TestCase):
    def test_exact_32_jars_and_non_authoritative_manifest_are_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root)
            data = artifact()
            self.assertEqual(
                32,
                len(
                    protocol.validate_candidate_artifact(
                        data,
                        hashlib.sha256(data).hexdigest(),
                        binding(),
                        protocol.load_policy(root),
                    )
                ),
            )

    def test_31_33_jars_and_pseudo_xml_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root)
            policy = protocol.load_policy(root)
            for value in (
                artifact(names=NAMES[:-1]),
                artifact(names=NAMES + ["extra.jar"]),
                artifact(extra={"reports/fake.xml": b"<pass/>"}),
            ):
                with self.subTest(size=len(value)):
                    with self.assertRaises(protocol.ProtocolError):
                        protocol.validate_candidate_artifact(
                            value, hashlib.sha256(value).hexdigest(), binding(), policy
                        )

    def test_duplicate_case_collision_and_bomb_are_rejected(self) -> None:
        duplicate = io.BytesIO()
        with zipfile.ZipFile(duplicate, "w") as archive:
            archive.writestr("manifest.json", b"{}")
            archive.writestr("manifest.json", b"{}")
        with self.assertRaises(protocol.ProtocolError):
            protocol.read_safe_zip(duplicate.getvalue())
        collision = io.BytesIO()
        with zipfile.ZipFile(collision, "w") as archive:
            archive.writestr("jars/A.jar", b"x")
            archive.writestr("jars/a.jar", b"x")
        with self.assertRaises(protocol.ProtocolError):
            protocol.read_safe_zip(collision.getvalue())
        bomb = io.BytesIO()
        with zipfile.ZipFile(bomb, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("manifest.json", b"0" * 200_000)
        with self.assertRaises(protocol.ProtocolError):
            protocol.read_safe_zip(bomb.getvalue())

    def test_missing_protected_policy_is_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(
                protocol.ProtocolError, "missing protected policy asset"
            ):
                protocol.load_policy(Path(directory))


class CheckoutTests(unittest.TestCase):
    def make_checkout(self, root: Path) -> str:
        (root / "pom.xml").write_text("<project/>", encoding="utf-8")
        subprocess.run(["git", "init", "-q"], cwd=root, check=True)
        subprocess.run(
            ["git", "config", "user.email", "test@example.com"], cwd=root, check=True
        )
        subprocess.run(["git", "config", "user.name", "Test"], cwd=root, check=True)
        subprocess.run(["git", "add", "."], cwd=root, check=True)
        subprocess.run(["git", "commit", "-qm", "base"], cwd=root, check=True)
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=root, text=True
        ).strip()

    def test_dirty_tracked_untracked_index_and_protected_pom_pollution_fail(
        self,
    ) -> None:
        for mutation in ("tracked", "untracked", "index"):
            with (
                self.subTest(mutation=mutation),
                tempfile.TemporaryDirectory() as directory,
            ):
                root = Path(directory)
                sha = self.make_checkout(root)
                if mutation == "tracked":
                    (root / "pom.xml").write_text("polluted", encoding="utf-8")
                elif mutation == "untracked":
                    (root / "pollution.txt").write_text("x", encoding="utf-8")
                else:
                    (root / "pom.xml").write_text("staged", encoding="utf-8")
                    subprocess.run(["git", "add", "pom.xml"], cwd=root, check=True)
                with self.assertRaisesRegex(protocol.ProtocolError, "checkout has"):
                    protocol.assert_clean_checkout(root, sha)


class FakeApi:
    def __init__(self, event: str = "pull_request") -> None:
        self.repository = REPOSITORY
        self.event = event
        self.run = {
            "id": RUN_ID,
            "run_attempt": ATTEMPT,
            "workflow_id": WORKFLOW_ID,
            "name": "CI",
            "path": ".github/workflows/ci.yml",
            "event": event,
            "head_sha": CANDIDATE_SHA,
            "head_branch": "feature/x"
            if event == "pull_request"
            else "gh-readonly-queue/main/x",
            "status": "completed",
            "conclusion": "success",
            "head_repository": {
                "full_name": FORK if event == "pull_request" else REPOSITORY
            },
            "pull_requests": [{"number": 3}] if event == "pull_request" else [],
        }
        self.archive = artifact()
        self.posts: list[tuple[str, object]] = []
        self.stale = False

    def get_json(self, path: str) -> object:
        route = urlparse(path).path
        if route == f"repos/{REPOSITORY}/actions/runs/{RUN_ID}":
            return copy.deepcopy(self.run)
        if route == f"repos/{REPOSITORY}":
            return {"id": REPOSITORY_ID, "default_branch": "main"}
        if route == f"repos/{REPOSITORY}/branches/main":
            return {"protected": True, "commit": {"sha": PROTECTED_SHA}}
        if route == "repos/coco/framework/pulls/3":
            return {
                "state": "open",
                "base": {"ref": "main"},
                "head": {
                    "sha": CANDIDATE_SHA,
                    "ref": self.run["head_branch"],
                    "repo": {"full_name": FORK},
                },
            }
        if route.endswith("/runs"):
            return {
                "workflow_runs": [
                    copy.deepcopy(self.run),
                    *([{**self.run, "id": RUN_ID + 1}] if self.stale else []),
                ]
            }
        if route.endswith("/jobs"):
            return {
                "jobs": [
                    {"name": protocol.SOURCE_PRODUCER_JOB, "conclusion": "success"}
                ]
            }
        if route.endswith("/artifacts"):
            return {
                "artifacts": [
                    {
                        "id": 9,
                        "name": binding()["artifact_name"],
                        "expired": False,
                        "workflow_run": {"id": RUN_ID},
                        "digest": "sha256:" + hashlib.sha256(self.archive).hexdigest(),
                    }
                ]
            }
        if route.endswith("/statuses"):
            return []
        raise AssertionError(path)

    def get_bytes(self, path: str) -> bytes:
        return self.archive

    def post_json(self, path: str, body: object) -> object:
        self.posts.append((path, body))
        return {}


class BindingAndPublisherTests(unittest.TestCase):
    def payload(self, api: FakeApi) -> dict:
        return {"action": "completed", "workflow_run": copy.deepcopy(api.run)}

    def test_fork_merge_group_stale_attempt_and_head_drift_are_rejected(self) -> None:
        api = FakeApi()
        self.assertEqual(
            FORK,
            protocol.bind_source_run(
                api, self.payload(api), REPOSITORY, REPOSITORY_ID, PROTECTED_SHA, RUN_ID
            )["candidate_repository"],
        )
        merge = FakeApi("merge_group")
        self.assertEqual(
            "merge_group",
            protocol.bind_source_run(
                merge,
                self.payload(merge),
                REPOSITORY,
                REPOSITORY_ID,
                PROTECTED_SHA,
                RUN_ID,
            )["source_event"],
        )
        api.stale = True
        with self.assertRaisesRegex(protocol.ProtocolError, "stale"):
            protocol.bind_source_run(
                api, self.payload(api), REPOSITORY, REPOSITORY_ID, PROTECTED_SHA, RUN_ID
            )
        api = FakeApi()
        event = self.payload(api)
        event["workflow_run"]["head_sha"] = OTHER_SHA
        with self.assertRaisesRegex(protocol.ProtocolError, "head_sha drift"):
            protocol.bind_source_run(
                api, event, REPOSITORY, REPOSITORY_ID, PROTECTED_SHA, RUN_ID
            )

    def test_publisher_accepts_only_eight_byte_verdict_and_never_ci_gate(self) -> None:
        api = FakeApi()
        with self.assertRaisesRegex(protocol.ProtocolError, "verdict token"):
            protocol.publish_status(
                api,
                self.payload(api),
                REPOSITORY,
                REPOSITORY_ID,
                PROTECTED_SHA,
                RUN_ID,
                CANDIDATE_SHA,
                ATTEMPT,
                "PASS0000\n",
            )
        result = protocol.publish_status(
            api,
            self.payload(api),
            REPOSITORY,
            REPOSITORY_ID,
            PROTECTED_SHA,
            RUN_ID,
            CANDIDATE_SHA,
            ATTEMPT,
            protocol.FAIL_ARTIFACT,
        )
        self.assertEqual("failure", result["state"])
        self.assertEqual(protocol.STATUS_CONTEXT, api.posts[-1][1]["context"])
        self.assertNotEqual("CI gate", api.posts[-1][1]["context"])

    def test_breaking_jar_is_a_fixed_failure_token(self) -> None:
        api = FakeApi()
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root)
            with mock.patch.object(
                protocol,
                "run_semantic_checks",
                side_effect=protocol.ProtocolError("breaking API or ABI change"),
            ):
                self.assertEqual(
                    protocol.FAIL_BREAKING,
                    protocol.verify_remote_artifact(
                        api, binding(), root, root / "japicmp.jar"
                    ),
                )


class WorkflowContractTests(unittest.TestCase):
    @staticmethod
    def read(relative: str) -> str:
        return (Path(__file__).resolve().parents[2] / relative).read_text(
            encoding="utf-8"
        )

    def test_producer_is_jar_only_and_ci_gate_is_unchanged(self) -> None:
        producer = self.read(
            ".github/workflows/reusable-api-compatibility-candidate.yml"
        )
        ci = self.read(".github/workflows/ci.yml")
        self.assertIn("candidate_repository", producer)
        self.assertIn("Stage exactly 32 non-authoritative candidate JARs", producer)
        self.assertNotIn(".api-protected", producer)
        self.assertNotIn("public-api-compatibility.xml", producer)
        self.assertNotIn("proof/", producer)
        self.assertIn("needs: [test, static-analysis, codeql]", ci)
        self.assertNotIn(
            "needs: [test, static-analysis, codeql, api-compatibility-candidate]", ci
        )
        self.assertNotIn("github.event_name == 'push'", ci)

    def test_trusted_workflow_is_dormant_and_publisher_only_consumes_verdict(
        self,
    ) -> None:
        workflow = self.read(".github/workflows/api-compatibility-gate.yml")
        self.assertIn("workflow_dispatch:", workflow)
        self.assertIn("workflow_run:", workflow)
        self.assertIn("verify-jars:", workflow)
        self.assertIn("statuses: write", workflow)
        publisher = workflow.split("  publish:", 1)[1]
        self.assertNotIn("download-artifact", publisher)
        self.assertNotIn("--japicmp", publisher)
        self.assertIn("VERDICT:", publisher)
        self.assertNotIn("CI gate", publisher)
        for line in workflow.splitlines():
            if "uses: actions/" in line:
                self.assertRegex(line.split("@", 1)[1].split()[0], r"^[0-9a-f]{40}$")


if __name__ == "__main__":
    unittest.main(verbosity=2)
