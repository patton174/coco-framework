from __future__ import annotations

import json
import os
import sys
import tempfile
import time
import unittest
import zipfile
from pathlib import Path
from unittest import mock


SCRIPT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

from check_public_api_compatibility import (  # noqa: E402
    BASELINE_SIGNING_FINGERPRINT,
    BASELINE_SIGNING_KEY_SHA256,
    POLICY_ID,
    input_file_records,
    input_records_sha256,
    policy_bundle_sha256,
    sha256_file,
    validate_attested_reports,
)
from path_io import (  # noqa: E402
    atomic_create_bytes,
    file_snapshot,
    logical_absolute,
    mkdir,
    read_bytes,
    rmtree,
    write_text,
)


def write_pom(path: Path, artifact: str, *, root: bool = False) -> None:
    modules = "<modules><module>fixture</module></modules>" if root else ""
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.patton174</groupId>
  <artifactId>{artifact}</artifactId>
  <version>1.0.0</version>
  {modules}
</project>
""",
        encoding="utf-8",
    )


def write_baseline_jar(path: Path, artifact: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    properties = f"groupId=io.github.patton174\nartifactId={artifact}\nversion=2.0.1\n"
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr(
            f"META-INF/maven/io.github.patton174/{artifact}/pom.properties",
            properties,
        )


class AttestedFixture:
    def __init__(self, directory: str) -> None:
        self.root = Path(directory) / "repository"
        self.workspace = Path(directory) / "workspace"
        self.root.mkdir()
        self.workspace.mkdir()
        write_pom(self.root / "pom.xml", "fixture-root", root=True)
        write_pom(self.root / "fixture" / "pom.xml", "fixture")
        self.manifest = self.root / "manifest.json"
        self.manifest.write_text(
            json.dumps(
                {
                    "schemaVersion": 3,
                    "policyId": POLICY_ID,
                    "profile": "public-api-compatibility",
                    "candidateVersionSource": "mavenProperty:revision",
                    "artifacts": [
                        {
                            "artifactId": "fixture",
                            "modulePath": "fixture",
                            "groupId": "io.github.patton174",
                            "jarName": "fixture.jar",
                            "baselineState": "present",
                            "comparison": {"targetArtifactId": "fixture"},
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        self.allowlist = self.root / "allowlist.json"
        self.allowlist.write_text(
            json.dumps(
                {
                    "schemaVersion": 3,
                    "policyId": POLICY_ID,
                    "profile": "public-api-compatibility",
                    "rules": [],
                }
            ),
            encoding="utf-8",
        )
        self.signing_key = self.root / "signing-key.asc"
        self.signing_key.write_bytes(
            (SCRIPT_DIR / "baseline-signing-key.asc").read_bytes()
        )
        self.japicmp_policy = self.root / "japicmp-policy.json"
        self.japicmp_policy.write_bytes(
            (SCRIPT_DIR / "japicmp-policy.json").read_bytes()
        )
        prefix = self.workspace / "baseline" / "fixture-2.0.1"
        self.baseline_pom = Path(f"{prefix}.pom")
        self.baseline_pom.parent.mkdir()
        self.baseline_pom.write_text(
            """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.patton174</groupId>
  <artifactId>fixture</artifactId>
  <version>2.0.1</version>
</project>
""",
            encoding="utf-8",
        )
        self.baseline_jar = Path(f"{prefix}.jar")
        write_baseline_jar(self.baseline_jar, "fixture")
        self.pom_signature = Path(f"{prefix}.pom.asc")
        self.jar_signature = Path(f"{prefix}.jar.asc")
        self.pom_signature.write_text("pom-signature", encoding="ascii")
        self.jar_signature.write_text("jar-signature", encoding="ascii")
        self.ledger = self.root / "ledger.json"
        self.ledger.write_text(
            json.dumps(
                {
                    "schemaVersion": 3,
                    "policyId": POLICY_ID,
                    "profile": "public-api-compatibility",
                    "origin": "https://repo.maven.apache.org/maven2",
                    "groupId": "io.github.patton174",
                    "version": "2.0.1",
                    "signingFingerprint": BASELINE_SIGNING_FINGERPRINT,
                    "signingKeySha256": BASELINE_SIGNING_KEY_SHA256,
                    "artifacts": [
                        {
                            "artifactId": "fixture",
                            "baselineState": "present",
                            "pomSize": self.baseline_pom.stat().st_size,
                            "jarSize": self.baseline_jar.stat().st_size,
                            "pomSha256": sha256_file(self.baseline_pom, "baseline POM"),
                            "jarSha256": sha256_file(self.baseline_jar, "baseline JAR"),
                        }
                    ],
                }
            ),
            encoding="utf-8",
        )
        self.candidate = self.root / "fixture" / "target" / "fixture-1.0.0.jar"
        self.candidate.parent.mkdir(parents=True)
        self.candidate.write_bytes(b"candidate")
        self.run_id = "run-12345678"
        self.report = (
            self.root
            / "fixture"
            / "target"
            / "public-api-compatibility"
            / self.run_id
            / "japicmp"
            / "public-api-compatibility.xml"
        )
        self.report.parent.mkdir(parents=True)
        self.write_report()
        self.input_paths = (
            self.root / "pom.xml",
            self.root / "fixture" / "pom.xml",
            self.manifest,
            self.allowlist,
            self.ledger,
            self.signing_key,
            self.japicmp_policy,
        )
        input_records = input_file_records(self.root, self.input_paths)
        self.attestation = self.workspace / "attestation.json"
        self.value = {
            "schemaVersion": 2,
            "runId": self.run_id,
            "repositoryRoot": str(self.root.resolve()),
            "workspace": str(self.workspace.resolve()),
            "protectedSha": "1" * 40,
            "expectedCandidateVersion": "1.0.0",
            "policyBundleSha256": policy_bundle_sha256(
                self.manifest,
                self.ledger,
                self.allowlist,
                self.signing_key,
            ),
            "startedAtNs": self.report.stat().st_mtime_ns - 1_000_000,
            "finishedAtNs": time.time_ns(),
            "inputs": {
                "sha256": input_records_sha256(input_records),
                "files": input_records,
            },
            "baseline": {
                "ledgerSha256": sha256_file(self.ledger, "ledger"),
                "origin": "https://repo.maven.apache.org/maven2",
                "version": "2.0.1",
                "signingFingerprint": BASELINE_SIGNING_FINGERPRINT,
                "verifiedMissingArtifacts": [],
                "artifacts": [
                    {
                        "artifact": "fixture",
                        "pomPath": str(self.baseline_pom.resolve()),
                        "pomSha256": sha256_file(self.baseline_pom, "baseline POM"),
                        "pomSignaturePath": str(self.pom_signature.resolve()),
                        "pomSignatureSha256": sha256_file(
                            self.pom_signature, "POM signature"
                        ),
                        "jarPath": str(self.baseline_jar.resolve()),
                        "jarSha256": sha256_file(self.baseline_jar, "baseline JAR"),
                        "jarSignaturePath": str(self.jar_signature.resolve()),
                        "jarSignatureSha256": sha256_file(
                            self.jar_signature, "JAR signature"
                        ),
                        "signingFingerprint": BASELINE_SIGNING_FINGERPRINT,
                    }
                ],
            },
            "candidates": [
                {
                    "artifact": "fixture",
                    "module": "fixture",
                    "path": str(self.candidate.resolve()),
                    "version": "1.0.0",
                    "sha256Before": sha256_file(self.candidate, "candidate"),
                    "sha256After": sha256_file(self.candidate, "candidate"),
                }
            ],
            "reports": [self.report_record()],
        }
        self.save()

    def write_report(self, *, candidate_version: str = "1.0.0") -> None:
        write_text(
            self.report,
            f"""<japicmp oldVersion="2.0.1" oldJar="{self.baseline_jar.resolve()}"
 newVersion="{candidate_version}" newJar="{self.candidate.resolve()}">
  <classes/>
</japicmp>
""",
        )

    def report_record(self) -> dict[str, object]:
        snapshot = file_snapshot(self.report, "fixture report")
        return {
            "artifact": "fixture",
            "module": "fixture",
            "path": str(logical_absolute(self.report)),
            "sha256": sha256_file(self.report, "report"),
            "size": snapshot.metadata.st_size,
            "mtimeNs": snapshot.metadata.st_mtime_ns,
        }

    def refresh_report(self) -> None:
        self.value["reports"] = [self.report_record()]
        self.value["finishedAtNs"] = time.time_ns()
        self.save()

    def save(self) -> None:
        mkdir(self.attestation.parent, parents=True, exist_ok=True)
        write_text(self.attestation, json.dumps(self.value), encoding="utf-8")

    def validate(self) -> tuple[list[object], list[str]]:
        with mock.patch(
            "check_public_api_compatibility.trusted_input_paths",
            return_value=self.input_paths,
        ):
            return validate_attested_reports(
                self.root,
                self.allowlist,
                self.manifest,
                self.ledger,
                self.signing_key,
                self.attestation,
                "1.0.0",
                "1" * 40,
            )


class AttestedPublicApiCompatibilityTest(unittest.TestCase):
    def test_valid_attestation_passes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = AttestedFixture(directory)
            self.assertEqual(([], []), fixture.validate())

    def test_protected_sha_mismatch_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = AttestedFixture(directory)
            fixture.value["protectedSha"] = "2" * 40
            fixture.save()
            with self.assertRaisesRegex(ValueError, "protectedSha"):
                fixture.validate()

    def test_deep_workspace_baseline_and_attestation_pass(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = AttestedFixture(directory)
            deep_workspace = Path(directory)
            index = 0
            while len(str(deep_workspace / "baseline" / "fixture-2.0.1.jar")) < 280:
                deep_workspace /= f"workspace-segment-{index:02d}-abcdefghij"
                index += 1
            mkdir(deep_workspace / "baseline", parents=True)

            moved: dict[str, Path] = {}
            for suffix, source in {
                "pom": fixture.baseline_pom,
                "jar": fixture.baseline_jar,
                "pom.asc": fixture.pom_signature,
                "jar.asc": fixture.jar_signature,
            }.items():
                destination = deep_workspace / "baseline" / f"fixture-2.0.1.{suffix}"
                atomic_create_bytes(destination, read_bytes(source, suffix))
                moved[suffix] = destination

            fixture.workspace = deep_workspace
            fixture.baseline_pom = moved["pom"]
            fixture.baseline_jar = moved["jar"]
            fixture.pom_signature = moved["pom.asc"]
            fixture.jar_signature = moved["jar.asc"]
            fixture.attestation = deep_workspace / "evidence" / "attestation.json"
            fixture.value["workspace"] = str(logical_absolute(deep_workspace))
            baseline_record = fixture.value["baseline"]["artifacts"][0]
            baseline_record.update(
                {
                    "pomPath": str(logical_absolute(fixture.baseline_pom)),
                    "pomSignaturePath": str(logical_absolute(fixture.pom_signature)),
                    "jarPath": str(logical_absolute(fixture.baseline_jar)),
                    "jarSignaturePath": str(logical_absolute(fixture.jar_signature)),
                }
            )
            fixture.write_report()
            fixture.refresh_report()

            self.assertGreaterEqual(len(str(fixture.baseline_jar)), 280)
            self.assertGreaterEqual(len(str(fixture.attestation)), 280)
            self.assertEqual(([], []), fixture.validate())
            rmtree(Path(directory), "deep attested fixture tree")

    def test_plugin_not_producing_report_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = AttestedFixture(directory)
            fixture.report.unlink()
            with self.assertRaisesRegex(ValueError, "is missing"):
                fixture.validate()

    def test_empty_forged_report_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = AttestedFixture(directory)
            fixture.report.write_bytes(b"")
            with self.assertRaisesRegex(ValueError, "is empty"):
                fixture.validate()

    def test_future_dated_report_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = AttestedFixture(directory)
            future = time.time_ns() + 10_000_000_000
            os.utime(fixture.report, ns=(future, future))
            fixture.value["reports"] = [fixture.report_record()]
            fixture.save()
            with self.assertRaisesRegex(ValueError, "outside the trusted run window"):
                fixture.validate()

    def test_report_candidate_version_cannot_be_self_reported(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = AttestedFixture(directory)
            fixture.write_report(candidate_version="9.9.9")
            fixture.refresh_report()
            with self.assertRaisesRegex(ValueError, "self-reports candidate version"):
                fixture.validate()

    def test_candidate_change_after_attestation_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = AttestedFixture(directory)
            fixture.candidate.write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "changed before, during, or after"):
                fixture.validate()

    def test_candidate_symlink_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = AttestedFixture(directory)
            real = fixture.root / "real-candidate.jar"
            real.write_bytes(fixture.candidate.read_bytes())
            fixture.candidate.unlink()
            try:
                fixture.candidate.symlink_to(real)
            except OSError as exc:
                self.skipTest(f"File symlinks are unavailable: {exc}")
            with self.assertRaisesRegex(ValueError, "non-symlink"):
                fixture.validate()

    def test_baseline_same_path_tamper_with_mtime_rollback_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = AttestedFixture(directory)
            original_mtime = fixture.baseline_jar.stat().st_mtime_ns
            fixture.baseline_jar.write_bytes(b"forged")
            os.utime(fixture.baseline_jar, ns=(original_mtime, original_mtime))
            with self.assertRaisesRegex(ValueError, "SHA-256 mismatch"):
                fixture.validate()

    def test_report_digest_change_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            fixture = AttestedFixture(directory)
            fixture.report.write_text("<japicmp/>", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "size mismatch|digest mismatch"):
                fixture.validate()


if __name__ == "__main__":
    unittest.main()
