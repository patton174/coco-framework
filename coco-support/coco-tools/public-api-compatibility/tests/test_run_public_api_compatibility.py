from __future__ import annotations

import io
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
import zipfile
from pathlib import Path, PurePosixPath
from unittest import mock
from xml.etree import ElementTree


SCRIPT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

import check_public_api_compatibility as checker  # noqa: E402
import policy_bundle  # noqa: E402
import run_public_api_compatibility as gate_runner  # noqa: E402
from check_public_api_compatibility import (  # noqa: E402
    BASELINE_GROUP_ID,
    BASELINE_ORIGIN,
    BASELINE_SIGNING_FINGERPRINT,
    BASELINE_SIGNING_KEY_SHA256,
    BASELINE_VERSION,
    CANDIDATE_VERSION_SOURCE,
    POLICY_ID,
    PROFILE_ID,
    canonical_manifest_entries,
    ExpectedArtifact,
    load_baseline_ledger,
    load_manifest,
    regular_file_bytes,
    sha256_bytes,
    sha256_file,
    validate_attested_reports,
)
from path_io import (  # noqa: E402
    atomic_create_bytes,
    atomic_create_text,
    entry_exists,
    logical_absolute,
    mkdir,
    read_bytes,
    read_text,
    rmtree,
)
from run_public_api_compatibility import (  # noqa: E402
    GpgVerifier,
    WINDOWS_REACTOR_ROOT_LIMIT,
    candidate_path,
    central_opener,
    central_url,
    clean_expected_reports,
    collect_fresh_reports,
    comparison_entries,
    download_exact,
    execute_gate,
    fresh_run_id,
    report_path,
    reactor_process_root,
    snapshot_candidates,
    write_missing_baseline_reports,
)


REPOSITORY_ROOT = SCRIPT_DIR.parents[2]


def artifact() -> ExpectedArtifact:
    return ExpectedArtifact(
        "fixture",
        PurePosixPath("fixture"),
        "fixture",
        PurePosixPath("fixture"),
    )


class TrustedPublicApiRunnerTest(unittest.TestCase):
    def test_download_binds_content_length_and_fixed_ledger_size(self) -> None:
        class Response:
            def __init__(self, url: str, value: bytes, declared: int) -> None:
                self.url = url
                self.value = value
                self.headers = {"Content-Length": str(declared)}

            def __enter__(self):
                return self

            def __exit__(self, *_args: object) -> None:
                return None

            def getcode(self) -> int:
                return 200

            def geturl(self) -> str:
                return self.url

            def read(self) -> bytes:
                return self.value

        class Opener:
            def __init__(self, response: Response) -> None:
                self.response = response

            def open(self, _url: str, timeout: int) -> Response:
                self.assert_timeout = timeout
                return self.response

        with tempfile.TemporaryDirectory() as directory:
            url = "https://repo.maven.apache.org/maven2/fixture.jar"
            value = b"central artifact"
            destination = Path(directory) / "fixture.jar"
            download_exact(
                Opener(Response(url, value, len(value))),
                url,
                destination,
                expected_size=len(value),
            )
            self.assertEqual(value, read_bytes(destination, "downloaded fixture"))

            with self.assertRaisesRegex(RuntimeError, "Content-Length mismatch"):
                download_exact(
                    Opener(Response(url, value, len(value) + 1)),
                    url,
                    Path(directory) / "wrong-header.jar",
                    expected_size=len(value),
                )
            with self.assertRaisesRegex(RuntimeError, "fixed size mismatch"):
                download_exact(
                    Opener(Response(url, value, len(value))),
                    url,
                    Path(directory) / "wrong-ledger.jar",
                    expected_size=len(value) + 1,
                )

    def test_shared_repository_policy_bundle_has_one_golden_hash(self) -> None:
        profile = SCRIPT_DIR / "public-api-profile.json"
        ledger = SCRIPT_DIR / "baseline-sha256.json"
        allowlist = SCRIPT_DIR / "allowlist.json"
        signing_key = SCRIPT_DIR / "baseline-signing-key.asc"
        expected = "124c0a3320bf6eee901487d6c2f9f8553d097129b3c7dcb6e1d2314b9bb66427"

        self.assertIs(checker.policy_bundle_sha256, policy_bundle.policy_bundle_sha256)
        self.assertIs(
            gate_runner.policy_bundle_sha256, policy_bundle.policy_bundle_sha256
        )
        self.assertEqual(
            expected,
            policy_bundle.policy_bundle_sha256(profile, ledger, allowlist, signing_key),
        )
        normalized = policy_bundle.normalized_policy_bundle(
            profile, ledger, allowlist, signing_key
        )
        self.assertEqual(3, normalized["schemaVersion"])
        self.assertEqual(
            {"mavenPlugin", "cli"},
            set(normalized["japicmpPolicy"]) & {"mavenPlugin", "cli"},
        )

    @unittest.skipUnless(os.name == "nt", "Windows reactor path contract")
    def test_too_deep_reactor_checkout_is_rejected_before_maven(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            while len(str(root)) < WINDOWS_REACTOR_ROOT_LIMIT:
                root /= f"checkout-segment-{len(root.parts):02d}"
            mkdir(root, parents=True)
            atomic_create_text(root / "pom.xml", "<project/>")
            with self.assertRaisesRegex(RuntimeError, "checkout path is too deep"):
                reactor_process_root(root)
            rmtree(Path(directory), "deep reactor checkout fixture")

    def test_run_id_keeps_full_uuid_entropy_with_windows_safe_length(self) -> None:
        run_id = fresh_run_id()
        self.assertRegex(run_id, r"^run-[0-9a-f]{32}$")
        self.assertEqual(36, len(run_id))

    def test_gpg_verifier_uses_only_a_dedicated_public_keyring(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            key = root / "release-key.asc"
            key.write_text("fixed public key", encoding="utf-8")
            signature = root / "artifact.jar.asc"
            signature.write_bytes(b"signature")
            artifact_path = root / "artifact.jar"
            artifact_path.write_bytes(b"artifact")
            calls: list[list[str]] = []

            def process_runner(
                arguments: list[str], cwd: Path
            ) -> subprocess.CompletedProcess[str]:
                calls.append(arguments)
                if "--show-keys" in arguments:
                    return subprocess.CompletedProcess(
                        arguments,
                        0,
                        stdout=f"fpr:::::::::{BASELINE_SIGNING_FINGERPRINT}:\n",
                        stderr="",
                    )
                if "--dearmor" in arguments:
                    output = Path(arguments[arguments.index("--output") + 1])
                    output.write_bytes(b"public keyring")
                    return subprocess.CompletedProcess(
                        arguments, 0, stdout="", stderr=""
                    )
                return subprocess.CompletedProcess(
                    arguments,
                    0,
                    stdout=f"[GNUPG:] VALIDSIG {BASELINE_SIGNING_FINGERPRINT} 0 0\n",
                    stderr="gpgv: Good signature",
                )

            with mock.patch(
                "run_public_api_compatibility.executable",
                side_effect=["gpg", "gpgv"],
            ):
                verifier = GpgVerifier(
                    key, root / "gnupg", process_runner, process_cwd=root
                )
                verifier.verify(signature, artifact_path)

            flattened = " ".join(part for call in calls for part in call)
            self.assertNotIn("--import", flattened)
            self.assertIn("--no-options", calls[0])
            self.assertIn("--dearmor", calls[1])
            self.assertEqual("gpgv", calls[2][0])
            self.assertIn("--keyring", calls[2])

    def test_gpg_verifier_uses_short_cwd_with_deep_home_and_arguments(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            key = root / "release-key.asc"
            key.write_text("fixed public key", encoding="utf-8")
            deep_home = root
            while len(str(deep_home / "gnupg" / "trustedkeys.gpg")) < 280:
                deep_home /= f"gpg-segment-{len(deep_home.parts):02d}-abcdefghij"
            calls: list[tuple[list[str], Path]] = []

            def process_runner(
                arguments: list[str], cwd: Path
            ) -> subprocess.CompletedProcess[str]:
                calls.append((arguments, cwd))
                if "--show-keys" in arguments:
                    return subprocess.CompletedProcess(
                        arguments,
                        0,
                        stdout=f"fpr:::::::::{BASELINE_SIGNING_FINGERPRINT}:\n",
                        stderr="",
                    )
                if "--dearmor" in arguments:
                    output = Path(arguments[arguments.index("--output") + 1])
                    atomic_create_bytes(output, b"public keyring")
                    return subprocess.CompletedProcess(arguments, 0, "", "")
                return subprocess.CompletedProcess(
                    arguments,
                    0,
                    f"[GNUPG:] VALIDSIG {BASELINE_SIGNING_FINGERPRINT} 0 0\n",
                    "",
                )

            with mock.patch(
                "run_public_api_compatibility.executable",
                side_effect=["gpg", "gpgv"],
            ):
                verifier = GpgVerifier(
                    key,
                    deep_home / "gnupg",
                    process_runner,
                    process_cwd=root,
                )
                signature = deep_home / "artifact.jar.asc"
                artifact_path = deep_home / "artifact.jar"
                atomic_create_bytes(signature, b"signature")
                atomic_create_bytes(artifact_path, b"artifact")
                verifier.verify(signature, artifact_path)

            self.assertEqual(3, len(calls))
            self.assertTrue(all(cwd == logical_absolute(root) for _, cwd in calls))
            self.assertTrue(all("\\\\?\\" not in str(cwd) for _, cwd in calls))
            self.assertTrue(
                any(
                    any(len(argument) >= 280 for argument in arguments)
                    for arguments, _ in calls
                )
            )
            rmtree(root, "deep GPG verifier fixture")

    def test_workflow_uses_only_attested_runner_with_one_exact_version(self) -> None:
        workflow = (REPOSITORY_ROOT / ".github/workflows/reusable-tests.yml").read_text(
            encoding="utf-8"
        )
        step = workflow.split("- name: Run API and ABI compatibility self-test", 1)[1]
        step = step.split("- name: Verify candidate artifact ownership", 1)[0]
        self.assertIn("PR-head diagnostic only", workflow)
        self.assertIn("04c47a shadow route", workflow)
        self.assertIn("run_public_api_compatibility.py", step)
        self.assertIn('--candidate-version "${PUBLIC_API_CANDIDATE_VERSION}"', step)
        self.assertIn('--work-directory "${api_workspace}"', step)
        self.assertIn('test -s "${api_workspace}/public-api-attestation.json"', step)
        self.assertIn("run_regression_fixtures.py", step)
        self.assertNotIn("check_public_api_compatibility.py", step)
        self.assertNotIn("mvn -B -ntp -Ppublic-api-compatibility", step)

    def test_trusted_runner_uses_clean_install_and_clean_profile_verify(self) -> None:
        source = (SCRIPT_DIR / "run_public_api_compatibility.py").read_text(
            encoding="utf-8"
        )
        self.assertIn(
            'command_runner([*common, "clean", "install"], process_root)', source
        )
        self.assertIn('"-f",\n        str(process_root / "pom.xml")', source)
        self.assertIn('f"-Dmaven.multiModuleProjectDirectory={process_root}"', source)
        self.assertIn('"-Ppublic-api-compatibility",', source)
        self.assertIn('"clean",\n            "verify",', source)

    def test_repository_ledger_is_fixed_sorted_and_complete(self) -> None:
        ledger_path = SCRIPT_DIR / "baseline-sha256.json"
        ledger = load_baseline_ledger(ledger_path)

        self.assertEqual(20, len(ledger.artifacts))
        self.assertEqual(31, len(ledger.missing_artifacts))
        self.assertEqual(
            sorted(entry.artifact for entry in ledger.artifacts),
            [entry.artifact for entry in ledger.artifacts],
        )
        data = json.loads(ledger_path.read_text(encoding="utf-8"))
        self.assertEqual(POLICY_ID, data["policyId"])
        self.assertEqual(BASELINE_SIGNING_KEY_SHA256, data["signingKeySha256"])
        self.assertEqual(BASELINE_SIGNING_FINGERPRINT, data["signingFingerprint"])
        missing_entries = [
            entry for entry in data["artifacts"] if entry["baselineState"] == "missing"
        ]
        self.assertTrue(
            all(
                entry["pomStatus"] == entry["jarStatus"] == 404
                for entry in missing_entries
            )
        )
        self.assertEqual(31, len(missing_entries))
        self.assertTrue(
            all(
                entry["pomSize"] > 0 and entry["jarSize"] > 0
                for entry in data["artifacts"]
                if entry["baselineState"] == "present"
            )
        )

    def test_profile_comparison_set_is_exactly_the_20_available_baselines(self) -> None:
        manifest = load_manifest(SCRIPT_DIR / "public-api-profile.json")
        ledger = load_baseline_ledger(SCRIPT_DIR / "baseline-sha256.json")

        selected = comparison_entries(manifest.artifacts, ledger)

        self.assertEqual(20, len(selected))
        self.assertEqual(
            {entry.artifact for entry in ledger.artifacts},
            {entry.artifact for entry in selected},
        )
        self.assertFalse(
            set(ledger.missing_artifacts) & {entry.artifact for entry in selected}
        )

    def test_canonical_json_is_stable_across_object_key_order(self) -> None:
        first = {"z": 1, "a": {"y": 2, "b": 3}}
        second = {"a": {"b": 3, "y": 2}, "z": 1}
        self.assertEqual(
            policy_bundle.canonical_json_bytes(first),
            policy_bundle.canonical_json_bytes(second),
        )

    def test_central_urls_are_exact_and_opener_disables_environment_proxy(self) -> None:
        self.assertEqual(
            "https://repo.maven.apache.org/maven2/io/github/patton174/"
            "coco-api/2.0.1/coco-api-2.0.1.jar",
            central_url("coco-api", "jar"),
        )
        opener = central_opener()
        proxy_handlers = [
            handler
            for handler in opener.handlers
            if handler.__class__.__name__ == "ProxyHandler"
        ]
        self.assertTrue(
            all(getattr(handler, "proxies", {}) == {} for handler in proxy_handlers)
        )

    def test_profile_uses_verified_baseline_files_and_run_scoped_output(self) -> None:
        namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
        root = ElementTree.parse(REPOSITORY_ROOT / "pom.xml").getroot()
        execution = root.find(
            ".//m:profile[m:id='public-api-compatibility']//m:execution"
            "[m:id='public-api-compatibility']/m:configuration",
            namespace,
        )
        self.assertIsNotNone(execution)
        self.assertEqual(
            "${coco.api.compatibility.baseline-directory}/"
            "${project.artifactId}-${coco.api.compatibility.baseline}.jar",
            execution.findtext("m:oldVersion/m:file/m:path", namespaces=namespace),
        )
        self.assertEqual(
            "${project.build.directory}/public-api-compatibility/"
            "${coco.api.compatibility.report-run-id}",
            execution.findtext("m:projectBuildDir", namespaces=namespace),
        )

    def test_clean_removes_legacy_and_run_scoped_reports(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            legacy = root / "fixture" / "target" / "japicmp"
            attested = root / "fixture" / "target" / "public-api-compatibility"
            legacy.mkdir(parents=True)
            attested.mkdir(parents=True)
            (legacy / "public-api-compatibility.xml").write_text("old")
            (attested / "old-run.xml").write_text("old")

            clean_expected_reports(root, (artifact(),))

            self.assertFalse(legacy.exists())
            self.assertFalse(attested.exists())

    def test_plugin_not_producing_report_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            now = time.time_ns()
            with self.assertRaisesRegex(ValueError, "is missing"):
                collect_fresh_reports(
                    Path(directory), (artifact(),), "run-12345678", now, now + 1
                )

    def test_current_only_report_is_bound_to_verified_404_and_candidate(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            version = "1.0.0"
            run_id = "run-12345678"
            candidate = candidate_path(root, artifact(), version)
            candidate.parent.mkdir(parents=True)
            candidate.write_bytes(b"candidate")

            write_missing_baseline_reports(
                root, (artifact(),), ("fixture",), version, run_id
            )

            report = report_path(root, artifact(), run_id)
            parsed = ElementTree.parse(report).getroot()
            self.assertEqual("n.a.", parsed.get("oldVersion"))
            self.assertEqual(version, parsed.get("newVersion"))
            self.assertEqual(str(candidate.resolve()), parsed.get("newJar"))
            self.assertEqual("verified-central-404", parsed.get("evidenceSource"))
            with self.assertRaisesRegex(RuntimeError, "unexpected japicmp report"):
                write_missing_baseline_reports(
                    root, (artifact(),), ("fixture",), version, run_id
                )

    def test_empty_forged_report_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = report_path(root, artifact(), "run-12345678")
            report.parent.mkdir(parents=True)
            report.touch()
            now = time.time_ns()

            with self.assertRaisesRegex(ValueError, "is empty"):
                collect_fresh_reports(
                    root, (artifact(),), "run-12345678", now - 1, now + 1
                )

    def test_old_and_future_dated_reports_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = report_path(root, artifact(), "run-12345678")
            report.parent.mkdir(parents=True)
            report.write_text("<japicmp/>", encoding="utf-8")
            now = time.time_ns()
            old = now - 10_000_000
            os.utime(report, ns=(old, old))
            with self.assertRaisesRegex(RuntimeError, "predates"):
                collect_fresh_reports(root, (artifact(),), "run-12345678", now, now + 1)

            future = time.time_ns() + 10_000_000_000
            os.utime(report, ns=(future, future))
            with self.assertRaisesRegex(RuntimeError, "future-dated"):
                collect_fresh_reports(root, (artifact(),), "run-12345678", now, now + 1)

    def test_candidate_snapshot_rejects_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            real = root / "real.jar"
            real.write_bytes(b"candidate")
            candidate = candidate_path(root, artifact(), "1.0.0")
            candidate.parent.mkdir(parents=True)
            try:
                candidate.symlink_to(real)
            except OSError as exc:
                self.skipTest(f"File symlinks are unavailable: {exc}")

            with self.assertRaisesRegex(ValueError, "non-symlink"):
                snapshot_candidates(root, (artifact(),), "1.0.0", phase="before")

    def test_same_path_content_replacement_changes_digest_even_with_mtime_rollback(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "baseline.jar"
            path.write_bytes(b"published")
            original_mtime = path.stat().st_mtime_ns
            original_digest = sha256_file(path, "baseline")

            path.write_bytes(b"forged-value")
            os.utime(path, ns=(original_mtime, original_mtime))

            self.assertNotEqual(original_digest, sha256_file(path, "baseline"))

    def test_regular_file_reader_rejects_empty_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "empty"
            path.touch()
            with self.assertRaisesRegex(ValueError, "empty"):
                regular_file_bytes(path, "fixture")

    def test_regular_file_reader_deterministically_rejects_symlink(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "candidate.jar"
            path.write_bytes(b"candidate")
            with mock.patch("path_io.is_reparse_or_symlink", return_value=True):
                with self.assertRaisesRegex(ValueError, "symlink/reparse|non-reparse"):
                    regular_file_bytes(path, "candidate")

    def test_deep_execute_gate_binds_full_inventory_and_attestation(self) -> None:
        version = "1.0.0"
        names = [f"artifact-{index:02d}" for index in range(51)]
        available = names[:20]

        def pom_bytes(artifact_id: str) -> bytes:
            return (
                '<project xmlns="http://maven.apache.org/POM/4.0.0">'
                "<modelVersion>4.0.0</modelVersion>"
                f"<groupId>{BASELINE_GROUP_ID}</groupId>"
                f"<artifactId>{artifact_id}</artifactId>"
                f"<version>{BASELINE_VERSION}</version>"
                "</project>\n"
            ).encode("utf-8")

        def jar_bytes(artifact_id: str) -> bytes:
            output = io.BytesIO()
            properties = (
                f"groupId={BASELINE_GROUP_ID}\n"
                f"artifactId={artifact_id}\n"
                f"version={BASELINE_VERSION}\n"
            )
            with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
                archive.writestr(
                    f"META-INF/maven/{BASELINE_GROUP_ID}/{artifact_id}/pom.properties",
                    properties,
                )
            return output.getvalue()

        baseline_payloads = {
            name: (pom_bytes(name), jar_bytes(name)) for name in available
        }

        with tempfile.TemporaryDirectory() as directory:
            sandbox = Path(directory)
            deep = sandbox
            while (
                len(str(deep / "trusted-workspace" / "public-api-attestation.json"))
                < 317
            ):
                deep /= f"gate-segment-{len(deep.parts):02d}-abcdefghij"
            root = sandbox / "repository"
            workspace = deep / "trusted-workspace"
            module_prefix = PurePosixPath("deep-reactor")
            while (
                len(
                    str(
                        root.joinpath(*module_prefix.parts, names[0])
                        / "target"
                        / "public-api-compatibility"
                        / "run-1234567890abcdef1234567890abcdef"
                        / "japicmp"
                        / "public-api-compatibility.xml"
                    )
                )
                < 280
                or len(
                    str(
                        root.joinpath(
                            *module_prefix.parts,
                            "policy",
                            "public-api-profile.json",
                        )
                    )
                )
                < 280
            ):
                module_prefix /= f"module-segment-{len(module_prefix.parts):02d}"
            module_paths = {name: (module_prefix / name).as_posix() for name in names}
            policy = root.joinpath(*module_prefix.parts, "policy")
            mkdir(policy, parents=True)
            mkdir(workspace, parents=True)

            modules = "".join(
                f"<module>{module_paths[name]}</module>" for name in names
            )
            atomic_create_text(
                root / "pom.xml",
                '<project xmlns="http://maven.apache.org/POM/4.0.0">'
                "<modelVersion>4.0.0</modelVersion>"
                f"<groupId>{BASELINE_GROUP_ID}</groupId>"
                "<artifactId>fixture-root</artifactId><version>1.0.0</version>"
                f"<packaging>pom</packaging><modules>{modules}</modules></project>\n",
            )

            manifest_entries = []
            for index, name in enumerate(names):
                target_index = index + 10 if index < 10 else index
                target = names[target_index]
                manifest_entries.append(
                    {
                        "artifactId": name,
                        "modulePath": module_paths[name],
                        "groupId": BASELINE_GROUP_ID,
                        "jarName": f"{name}.jar",
                        "baselineState": (
                            "present" if name in available else "missing"
                        ),
                        "comparison": {"targetArtifactId": target},
                    }
                )
                properties = ""
                if index < 10:
                    properties = (
                        "<properties><coco.api.compatibility.candidate-jar>"
                        "${maven.multiModuleProjectDirectory}/"
                        f"{module_paths[target]}/target/{target}-${{project.version}}.jar"
                        "</coco.api.compatibility.candidate-jar></properties>"
                    )
                module = root / module_paths[name]
                mkdir(module, parents=True)
                atomic_create_text(
                    module / "pom.xml",
                    '<project xmlns="http://maven.apache.org/POM/4.0.0">'
                    "<modelVersion>4.0.0</modelVersion>"
                    f"<groupId>{BASELINE_GROUP_ID}</groupId>"
                    f"<artifactId>{name}</artifactId><version>{version}</version>"
                    f"{properties}</project>\n",
                )

            manifest_path = policy / "public-api-profile.json"
            allowlist_path = policy / "allowlist.json"
            ledger_path = policy / "baseline-sha256.json"
            japicmp_policy_path = policy / "japicmp-policy.json"
            signing_key_path = policy / "baseline-signing-key.asc"
            atomic_create_text(
                manifest_path,
                json.dumps(
                    {
                        "schemaVersion": 3,
                        "policyId": POLICY_ID,
                        "profile": PROFILE_ID,
                        "candidateVersionSource": CANDIDATE_VERSION_SOURCE,
                        "artifacts": manifest_entries,
                    },
                    indent=2,
                )
                + "\n",
            )
            atomic_create_text(
                allowlist_path,
                json.dumps(
                    {
                        "schemaVersion": 3,
                        "policyId": POLICY_ID,
                        "profile": PROFILE_ID,
                        "rules": [],
                    },
                    indent=2,
                )
                + "\n",
            )
            atomic_create_text(
                ledger_path,
                json.dumps(
                    {
                        "schemaVersion": 3,
                        "policyId": POLICY_ID,
                        "profile": PROFILE_ID,
                        "origin": BASELINE_ORIGIN,
                        "groupId": BASELINE_GROUP_ID,
                        "version": BASELINE_VERSION,
                        "signingFingerprint": BASELINE_SIGNING_FINGERPRINT,
                        "signingKeySha256": BASELINE_SIGNING_KEY_SHA256,
                        "artifacts": [
                            (
                                {
                                    "artifactId": name,
                                    "baselineState": "present",
                                    "pomSize": len(baseline_payloads[name][0]),
                                    "jarSize": len(baseline_payloads[name][1]),
                                    "pomSha256": sha256_bytes(
                                        baseline_payloads[name][0]
                                    ),
                                    "jarSha256": sha256_bytes(
                                        baseline_payloads[name][1]
                                    ),
                                }
                                if name in available
                                else {
                                    "artifactId": name,
                                    "baselineState": "missing",
                                    "pomStatus": 404,
                                    "jarStatus": 404,
                                }
                            )
                            for name in names
                        ],
                    },
                    indent=2,
                )
                + "\n",
            )
            atomic_create_bytes(
                signing_key_path,
                read_bytes(
                    SCRIPT_DIR / "baseline-signing-key.asc",
                    "repository baseline signing key",
                ),
            )
            atomic_create_bytes(
                japicmp_policy_path,
                read_bytes(
                    SCRIPT_DIR / "japicmp-policy.json",
                    "repository japicmp policy",
                ),
            )

            def trusted_inputs(
                input_root: Path,
                manifest: object,
                input_manifest: Path,
                input_allowlist: Path,
                input_ledger: Path,
                input_signing_key: Path,
            ) -> tuple[Path, ...]:
                artifact_entries = manifest.artifacts
                paths = {
                    input_root / "pom.xml",
                    input_manifest,
                    input_allowlist,
                    input_ledger,
                    input_signing_key,
                    input_manifest.with_name("japicmp-policy.json"),
                }
                paths.update(
                    input_root.joinpath(*entry.module.parts, "pom.xml")
                    for entry in artifact_entries
                )
                return tuple(
                    sorted((logical_absolute(path) for path in paths), key=str)
                )

            gpg_calls: list[tuple[list[str], Path]] = []

            def gpg_process(
                arguments: list[str], cwd: Path
            ) -> subprocess.CompletedProcess[str]:
                gpg_calls.append((arguments, cwd))
                if "--show-keys" in arguments:
                    return subprocess.CompletedProcess(
                        arguments,
                        0,
                        stdout=f"fpr:::::::::{BASELINE_SIGNING_FINGERPRINT}:\n",
                        stderr="",
                    )
                if "--dearmor" in arguments:
                    output = Path(arguments[arguments.index("--output") + 1])
                    atomic_create_bytes(output, b"public keyring")
                    return subprocess.CompletedProcess(arguments, 0, "", "")
                return subprocess.CompletedProcess(
                    arguments,
                    0,
                    f"[GNUPG:] VALIDSIG {BASELINE_SIGNING_FINGERPRINT} 0 0\n",
                    "",
                )

            def fetch_baselines(
                input_workspace: Path, ledger: object, input_signing_key: Path
            ) -> list[dict[str, str]]:
                baseline = input_workspace / "baseline"
                mkdir(baseline)
                verifier = GpgVerifier(
                    input_signing_key,
                    input_workspace / "gnupg",
                    gpg_process,
                    process_cwd=root,
                )
                records = []
                for entry in ledger.artifacts:
                    pom, jar = baseline_payloads[entry.artifact]
                    prefix = baseline / f"{entry.artifact}-{BASELINE_VERSION}"
                    paths = {
                        "pom": Path(f"{prefix}.pom"),
                        "pom.asc": Path(f"{prefix}.pom.asc"),
                        "jar": Path(f"{prefix}.jar"),
                        "jar.asc": Path(f"{prefix}.jar.asc"),
                    }
                    atomic_create_bytes(paths["pom"], pom)
                    atomic_create_bytes(paths["jar"], jar)
                    atomic_create_bytes(paths["pom.asc"], b"POM signature")
                    atomic_create_bytes(paths["jar.asc"], b"JAR signature")
                    verifier.verify(paths["pom.asc"], paths["pom"])
                    verifier.verify(paths["jar.asc"], paths["jar"])
                    records.append(
                        {
                            "artifact": entry.artifact,
                            "pomPath": str(logical_absolute(paths["pom"])),
                            "pomSha256": entry.pom_sha256,
                            "pomSignaturePath": str(logical_absolute(paths["pom.asc"])),
                            "pomSignatureSha256": sha256_file(
                                paths["pom.asc"], "POM signature"
                            ),
                            "jarPath": str(logical_absolute(paths["jar"])),
                            "jarSha256": entry.jar_sha256,
                            "jarSignaturePath": str(logical_absolute(paths["jar.asc"])),
                            "jarSignatureSha256": sha256_file(
                                paths["jar.asc"], "JAR signature"
                            ),
                            "signingFingerprint": BASELINE_SIGNING_FINGERPRINT,
                        }
                    )
                return records

            commands: list[list[str]] = []

            def command_runner(arguments: list[str], _cwd: Path) -> None:
                commands.append(arguments)
                self.assertEqual(logical_absolute(root), _cwd)
                self.assertEqual(
                    str(logical_absolute(root / "pom.xml")),
                    arguments[arguments.index("-f") + 1],
                )
                self.assertIn(
                    f"-Dmaven.multiModuleProjectDirectory={logical_absolute(root)}",
                    arguments,
                )
                manifest = load_manifest(manifest_path)
                if arguments[-2:] == ["clean", "install"]:
                    for entry in canonical_manifest_entries(manifest):
                        candidate = candidate_path(root, entry, version)
                        mkdir(candidate.parent, parents=True, exist_ok=True)
                        atomic_create_bytes(
                            candidate, f"candidate:{entry.artifact}".encode("ascii")
                        )
                    return
                self.assertEqual(["clean", "verify"], arguments[-2:])
                run_id = arguments[
                    next(
                        index
                        for index, item in enumerate(arguments)
                        if item.startswith("-Dcoco.api.compatibility.report-run-id=")
                    )
                ].split("=", 1)[1]
                ledger = load_baseline_ledger(ledger_path)
                for entry in comparison_entries(manifest.artifacts, ledger):
                    candidate = root.joinpath(
                        *entry.candidate_module.parts,
                        "target",
                        f"{entry.candidate_artifact}-{version}.jar",
                    )
                    report = report_path(root, entry, run_id)
                    mkdir(report.parent, parents=True)
                    atomic_create_text(
                        report,
                        '<japicmp oldVersion="2.0.1" '
                        f'oldJar="{workspace / "baseline" / f"{entry.artifact}-2.0.1.jar"}" '
                        f'newVersion="{version}" newJar="{candidate}"><classes/>'
                        "</japicmp>\n",
                    )

            try:
                with (
                    mock.patch.object(
                        gate_runner, "fetch_verified_baselines", fetch_baselines
                    ),
                    mock.patch.object(
                        gate_runner, "trusted_input_paths", trusted_inputs
                    ),
                    mock.patch.object(checker, "trusted_input_paths", trusted_inputs),
                    mock.patch.object(gate_runner, "executable", return_value="mvn"),
                ):
                    attestation = execute_gate(
                        root,
                        workspace,
                        version,
                        manifest_path,
                        allowlist_path,
                        ledger_path,
                        signing_key_path,
                        "2" * 40,
                        command_runner=command_runner,
                    )
                    self.assertEqual(
                        ([], []),
                        validate_attested_reports(
                            root,
                            allowlist_path,
                            manifest_path,
                            ledger_path,
                            signing_key_path,
                            attestation,
                            version,
                            "2" * 40,
                        ),
                    )
                evidence = json.loads(read_text(attestation, "deep attestation"))
                self.assertEqual(51, len(evidence["reports"]))
                self.assertEqual(41, len(evidence["candidates"]))
                self.assertEqual(20, len(evidence["baseline"]["artifacts"]))
                self.assertEqual(
                    31, len(evidence["baseline"]["verifiedMissingArtifacts"])
                )
                self.assertEqual("2" * 40, evidence["protectedSha"])
                self.assertGreaterEqual(len(str(manifest_path)), 280)
                self.assertGreaterEqual(len(str(evidence["reports"][0]["path"])), 280)
                self.assertGreaterEqual(len(str(attestation)), 280)
                serialized = json.dumps(evidence)
                self.assertNotIn("\\\\?\\", serialized)
                self.assertNotIn(
                    "\\\\?\\", " ".join(part for call in commands for part in call)
                )
                self.assertEqual(42, len(gpg_calls))
                self.assertTrue(
                    all(cwd == logical_absolute(root) for _, cwd in gpg_calls)
                )
                self.assertTrue(
                    any(
                        any(len(argument) >= 280 for argument in arguments)
                        for arguments, _ in gpg_calls
                    )
                )
            finally:
                if entry_exists(sandbox):
                    rmtree(sandbox, "deep execute gate fixture")


if __name__ == "__main__":
    unittest.main()
