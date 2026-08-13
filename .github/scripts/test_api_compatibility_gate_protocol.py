#!/usr/bin/env python3
"""Offline regression tests for the trusted JAR-only shadow route."""

from __future__ import annotations

import base64
import copy
import hashlib
import io
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from contextlib import redirect_stderr
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, unquote, urlparse

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
CANONICAL_POLICY_COMMIT = "170566f0c403fd0eb18edd376fdb297b7059b36b"
REPORT_OWNER_NAMES = [
    f"coco-{index:02d}.jar" for index in range(protocol.REPORT_OWNER_COUNT)
]
NAMES = REPORT_OWNER_NAMES[protocol.DIRECT_REPLACEMENT_COUNT :]
GOLDEN_POLICY_BUNDLE_SHA256 = (
    "65a32f7069e36f38e281274645f855e23c492ce17b3b84ebffe6b736e6e58ff4"
)
REACTOR_ARTIFACT_IDS = sorted(
    [
        "coco-api",
        "coco-audit",
        "coco-audit-jdbc",
        "coco-cache",
        "coco-cache-redis",
        "coco-concurrency-limit",
        "coco-concurrency-limit-redis",
        "coco-config",
        "coco-context",
        "coco-context-spring",
        "coco-cors",
        "coco-data-permission",
        "coco-exception",
        "coco-feature-audit",
        "coco-feature-codegen",
        "coco-feature-data-permission",
        "coco-feature-model",
        "coco-feature-mybatis-plus",
        "coco-feature-openapi",
        "coco-feature-runtime",
        "coco-feature-security",
        "coco-feature-tenant",
        "coco-feature-web",
        "coco-http-client",
        "coco-i18n",
        "coco-idempotency",
        "coco-idempotency-jdbc",
        "coco-idempotency-redis",
        "coco-lock",
        "coco-lock-redis",
        "coco-logging",
        "coco-maven-plugin",
        "coco-mybatis-plus",
        "coco-observability",
        "coco-openapi",
        "coco-rate-limit",
        "coco-rate-limit-redis",
        "coco-replay-redis",
        "coco-scheduler",
        "coco-security",
        "coco-security-api-key",
        "coco-security-jwt",
        "coco-security-spring",
        "coco-spring-boot-autoconfigure",
        "coco-spring-boot-starter",
        "coco-storage",
        "coco-storage-s3",
        "coco-tenant",
        "coco-test",
        "coco-test-support",
        "coco-web",
    ]
)
REACTOR_MODULE_PATHS = {
    "coco-api": "coco-foundation/coco-api",
    "coco-audit": "coco-features/coco-audit",
    "coco-audit-jdbc": "coco-features/coco-audit-jdbc",
    "coco-cache": "coco-features/coco-cache",
    "coco-cache-redis": "coco-features/coco-cache-redis",
    "coco-concurrency-limit": "coco-features/coco-concurrency-limit",
    "coco-concurrency-limit-redis": "coco-features/coco-concurrency-limit-redis",
    "coco-config": "coco-build/coco-compatibility/coco-config",
    "coco-context": "coco-foundation/coco-context",
    "coco-context-spring": "coco-foundation/coco-context-spring",
    "coco-cors": "coco-features/coco-cors",
    "coco-data-permission": "coco-features/coco-data-permission",
    "coco-exception": "coco-foundation/coco-exception",
    "coco-feature-audit": "coco-build/coco-compatibility/coco-feature-audit",
    "coco-feature-codegen": "coco-features/coco-feature-codegen",
    "coco-feature-data-permission": (
        "coco-build/coco-compatibility/coco-feature-data-permission"
    ),
    "coco-feature-model": "coco-foundation/coco-feature-model",
    "coco-feature-mybatis-plus": (
        "coco-build/coco-compatibility/coco-feature-mybatis-plus"
    ),
    "coco-feature-openapi": "coco-build/coco-compatibility/coco-feature-openapi",
    "coco-feature-runtime": "coco-build/coco-compatibility/coco-feature-runtime",
    "coco-feature-security": "coco-build/coco-compatibility/coco-feature-security",
    "coco-feature-tenant": "coco-build/coco-compatibility/coco-feature-tenant",
    "coco-feature-web": "coco-build/coco-compatibility/coco-feature-web",
    "coco-http-client": "coco-features/coco-http-client",
    "coco-i18n": "coco-foundation/coco-i18n",
    "coco-idempotency": "coco-features/coco-idempotency",
    "coco-idempotency-jdbc": "coco-features/coco-idempotency-jdbc",
    "coco-idempotency-redis": "coco-features/coco-idempotency-redis",
    "coco-lock": "coco-features/coco-lock",
    "coco-lock-redis": "coco-features/coco-lock-redis",
    "coco-logging": "coco-foundation/coco-logging",
    "coco-maven-plugin": "coco-build/coco-maven-plugin",
    "coco-mybatis-plus": "coco-features/coco-mybatis-plus",
    "coco-observability": "coco-features/coco-observability",
    "coco-openapi": "coco-features/coco-openapi",
    "coco-rate-limit": "coco-features/coco-rate-limit",
    "coco-rate-limit-redis": "coco-features/coco-rate-limit-redis",
    "coco-replay-redis": "coco-features/coco-replay-redis",
    "coco-scheduler": "coco-features/coco-scheduler",
    "coco-security": "coco-features/coco-security",
    "coco-security-api-key": "coco-features/coco-security-api-key",
    "coco-security-jwt": "coco-features/coco-security-jwt",
    "coco-security-spring": "coco-features/coco-security-spring",
    "coco-spring-boot-autoconfigure": ("coco-spring/coco-spring-boot-autoconfigure"),
    "coco-spring-boot-starter": "coco-spring/coco-spring-boot-starter",
    "coco-storage": "coco-features/coco-storage",
    "coco-storage-s3": "coco-features/coco-storage-s3",
    "coco-tenant": "coco-features/coco-tenant",
    "coco-test": "coco-build/coco-compatibility/coco-test",
    "coco-test-support": "coco-support/coco-test-support",
    "coco-web": "coco-features/coco-web",
}
REACTOR_BASELINE_IDS = frozenset(
    {
        "coco-api",
        "coco-config",
        "coco-context",
        "coco-exception",
        "coco-feature-audit",
        "coco-feature-codegen",
        "coco-feature-data-permission",
        "coco-feature-model",
        "coco-feature-mybatis-plus",
        "coco-feature-openapi",
        "coco-feature-runtime",
        "coco-feature-security",
        "coco-feature-tenant",
        "coco-feature-web",
        "coco-i18n",
        "coco-logging",
        "coco-maven-plugin",
        "coco-spring-boot-autoconfigure",
        "coco-spring-boot-starter",
        "coco-test",
    }
)
REACTOR_COMPARISON_TARGETS = {
    "coco-config": "coco-spring-boot-autoconfigure",
    "coco-feature-audit": "coco-audit",
    "coco-feature-data-permission": "coco-data-permission",
    "coco-feature-mybatis-plus": "coco-mybatis-plus",
    "coco-feature-openapi": "coco-openapi",
    "coco-feature-runtime": "coco-spring-boot-autoconfigure",
    "coco-feature-security": "coco-security",
    "coco-feature-tenant": "coco-tenant",
    "coco-feature-web": "coco-web",
    "coco-test": "coco-test-support",
}


def properties_bytes(
    artifact_id: str,
    group_id: str = "io.github.coco",
    version: str = protocol.CANDIDATE_VERSION,
) -> bytes:
    return (
        f"artifactId={artifact_id}\ngroupId={group_id}\nversion={version}\n"
    ).encode()


def pom_xml_bytes(
    artifact_id: str,
    group_id: str = "io.github.coco",
    version: str = protocol.CANDIDATE_VERSION,
    parent_fallback: bool = False,
) -> bytes:
    if parent_fallback:
        coordinates = (
            f"<parent><groupId>{group_id}</groupId><artifactId>parent</artifactId>"
            f"<version>{version}</version></parent><artifactId>{artifact_id}</artifactId>"
        )
    else:
        coordinates = (
            f"<groupId>{group_id}</groupId><artifactId>{artifact_id}</artifactId>"
            f"<version>{version}</version>"
        )
    return (
        '<project xmlns="http://maven.apache.org/POM/4.0.0">'
        f"<modelVersion>4.0.0</modelVersion>{coordinates}</project>"
    ).encode()


def jar_bytes(
    artifact_id: str = "coco-00",
    group_id: str = "io.github.coco",
    version: str = protocol.CANDIDATE_VERSION,
    *,
    xml_artifact_id: str | None = None,
    properties_path: str | None = None,
    pom_path: str | None = None,
    include_properties: bool = True,
    include_pom: bool = True,
    xml_parent_fallback: bool = False,
    pom_data: bytes | None = None,
    extra_descriptors: list[tuple[str, bytes]] | None = None,
) -> bytes:
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        archive.writestr("example/Api.class", b"class")
        descriptor_root = f"META-INF/maven/{group_id}/{artifact_id}"
        if include_properties:
            archive.writestr(
                properties_path or f"{descriptor_root}/pom.properties",
                properties_bytes(artifact_id, group_id, version),
            )
        if include_pom:
            archive.writestr(
                pom_path or f"{descriptor_root}/pom.xml",
                pom_data
                or pom_xml_bytes(
                    xml_artifact_id or artifact_id,
                    group_id,
                    version,
                    xml_parent_fallback,
                ),
            )
        for path, data in extra_descriptors or []:
            archive.writestr(path, data)
    return stream.getvalue()


def write_policy(
    root: Path,
    exceptions: list[dict[str, str]] | None = None,
    artifact_ids: list[str] | None = None,
    group_id: str = "io.github.coco",
    module_paths: dict[str, str] | None = None,
    baseline_ids: frozenset[str] | None = None,
    comparison_targets: dict[str, str] | None = None,
) -> None:
    artifact_ids = artifact_ids or [name[:-4] for name in REPORT_OWNER_NAMES]
    artifact_ids = sorted(artifact_ids)
    baseline_ids = baseline_ids or frozenset(
        artifact_ids[: protocol.PRESENT_BASELINE_COUNT]
    )
    current_only_ids = [
        artifact_id for artifact_id in artifact_ids if artifact_id not in baseline_ids
    ]
    if comparison_targets is None:
        comparison_targets = dict(
            zip(
                sorted(baseline_ids)[:10],
                current_only_ids[:10],
                strict=True,
            )
        )
    signing_key = b"fixture baseline signing key\n"
    directory = root / protocol.POLICY_ROOT
    directory.mkdir(parents=True)
    repository_policy = Path(__file__).resolve().parents[2] / protocol.POLICY_ROOT
    for shared_name in ("policy_bundle.py", "path_io.py"):
        (directory / shared_name).write_bytes(
            (repository_policy / shared_name).read_bytes()
        )
    profile = {
        "schemaVersion": protocol.PROFILE_SCHEMA_VERSION,
        "policyId": protocol.POLICY_ID,
        "profile": protocol.PROFILE_ID,
        "candidateVersionSource": protocol.CANDIDATE_VERSION_SOURCE,
        "artifacts": [
            {
                "artifactId": artifact_id,
                "baselineState": (
                    "present" if artifact_id in baseline_ids else "missing"
                ),
                "comparison": {
                    "targetArtifactId": comparison_targets.get(artifact_id, artifact_id)
                },
                "groupId": group_id,
                "jarName": f"{artifact_id}.jar",
                "modulePath": (module_paths or {}).get(
                    artifact_id, f"modules/{artifact_id}"
                ),
            }
            for artifact_id in artifact_ids
        ],
    }
    ledger = {
        "schemaVersion": 3,
        "policyId": protocol.POLICY_ID,
        "profile": protocol.PROFILE_ID,
        "origin": protocol.BASELINE_ORIGIN,
        "groupId": group_id,
        "version": protocol.BASELINE_VERSION,
        "signingFingerprint": "A" * 40,
        "signingKeySha256": hashlib.sha256(signing_key).hexdigest(),
        "artifacts": [
            (
                {
                    "artifactId": artifact_id,
                    "baselineState": "present",
                    "jarSha256": "d" * 64,
                    "jarSize": 5,
                    "pomSha256": "e" * 64,
                    "pomSize": 5,
                }
                if artifact_id in baseline_ids
                else {
                    "artifactId": artifact_id,
                    "baselineState": "missing",
                    "jarStatus": 404,
                    "pomStatus": 404,
                }
            )
            for artifact_id in artifact_ids
        ],
    }
    values = {
        "public-api-profile.json": profile,
        "baseline-sha256.json": ledger,
        "allowlist.json": {
            "schemaVersion": 3,
            "policyId": protocol.POLICY_ID,
            "profile": protocol.PROFILE_ID,
            "rules": exceptions or [],
        },
        "japicmp-policy.json": {
            "schemaVersion": 3,
            "policyId": protocol.POLICY_ID,
            "profile": protocol.PROFILE_ID,
            "findingKey": ["artifact", "class", "member", "category"],
            "allowedCategories": ["REMOVED"],
            "mavenPlugin": {
                "groupId": "com.github.siom79.japicmp",
                "artifactId": "japicmp-maven-plugin",
                "version": "0.23.1",
                "url": protocol.JAPICMP_MAVEN_PLUGIN_URL,
                "size": protocol.JAPICMP_MAVEN_PLUGIN_SIZE,
                "sha256": protocol.JAPICMP_MAVEN_PLUGIN_SHA256,
            },
            "cli": {
                "groupId": "com.github.siom79.japicmp",
                "artifactId": "japicmp",
                "version": "0.23.1",
                "url": protocol.JAPICMP_URL,
                "size": protocol.JAPICMP_SIZE,
                "sha256": protocol.JAPICMP_SHA256,
            },
        },
    }
    for name, value in values.items():
        (directory / name).write_bytes(protocol.canonical_json(value) + b"\n")
    (directory / protocol.BASELINE_SIGNING_KEY_FILE).write_bytes(signing_key)


def mutate_policy_file(root: Path, name: str, mutation: Any) -> None:
    path = root / protocol.POLICY_ROOT / name
    value = protocol.strict_json_loads(path.read_bytes())
    mutation(value)
    path.write_bytes(protocol.canonical_json(value) + b"\n")


def write_canonical_policy_commit(root: Path) -> None:
    repository = Path(__file__).resolve().parents[2]
    directory = root / protocol.POLICY_ROOT
    directory.mkdir(parents=True)
    for name in (
        "public-api-profile.json",
        "baseline-sha256.json",
        "allowlist.json",
        protocol.BASELINE_SIGNING_KEY_FILE,
        "japicmp-policy.json",
        "policy_bundle.py",
        "path_io.py",
    ):
        source = f"{CANONICAL_POLICY_COMMIT}:{protocol.POLICY_ROOT.as_posix()}/{name}"
        data = subprocess.check_output(["git", "show", source], cwd=repository)
        (directory / name).write_bytes(data)


def build_java_jar(root: Path, label: str, source: str) -> Path:
    source_root = root / label / "src" / "example"
    classes = root / label / "classes"
    source_root.mkdir(parents=True)
    classes.mkdir(parents=True)
    source_file = source_root / "Api.java"
    source_file.write_text(source, encoding="utf-8")
    javac = shutil.which("javac")
    jar = shutil.which("jar")
    if javac is None or jar is None:
        raise unittest.SkipTest("JDK javac/jar are required")
    subprocess.run(
        [javac, "-d", str(classes), str(source_file)],
        check=True,
        capture_output=True,
        text=True,
    )
    output = root / f"{label}.jar"
    subprocess.run(
        [jar, "--create", "--file", str(output), "-C", str(classes), "."],
        check=True,
        capture_output=True,
        text=True,
    )
    return output


def binding() -> dict:
    return {
        "candidate_repository": FORK,
        "candidate_sha": CANDIDATE_SHA,
        "source_event": "pull_request",
        "source_run_id": RUN_ID,
        "source_run_attempt": ATTEMPT,
        "producer_outcome": "success",
        "artifact_name": f"{protocol.ARTIFACT_PREFIX}-{CANDIDATE_SHA}-{RUN_ID}-{ATTEMPT}",
    }


def artifact(
    extra: dict[str, bytes] | None = None,
    names: list[str] | None = None,
    jar_overrides: dict[str, bytes] | None = None,
    manifest_mutation: Any = None,
) -> bytes:
    names = names or NAMES
    jars = {
        name: (jar_overrides or {}).get(name, jar_bytes(name[:-4])) for name in names
    }
    manifest = {
        "schema_version": 3,
        "kind": "non-authoritative-candidate-jars",
        "candidate_sha": CANDIDATE_SHA,
        "candidate_version": protocol.CANDIDATE_VERSION,
        "source_event": "pull_request",
        "source_run_id": RUN_ID,
        "source_run_attempt": ATTEMPT,
        "jars": [
            {
                "artifact_id": name[:-4],
                "group_id": "io.github.coco",
                "normalized_name": name,
                "size": len(data),
                "sha256": hashlib.sha256(data).hexdigest(),
                "source_path": (
                    f"modules/{name[:-4]}/target/{name[:-4]}-"
                    f"{protocol.CANDIDATE_VERSION}.jar"
                ),
                "version": protocol.CANDIDATE_VERSION,
            }
            for name, data in jars.items()
        ],
    }
    if manifest_mutation is not None:
        manifest_mutation(manifest)
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", protocol.canonical_json(manifest) + b"\n")
        for name, data in jars.items():
            archive.writestr(f"jars/{name}", data)
        for name, data in (extra or {}).items():
            archive.writestr(name, data)
    return stream.getvalue()


class CandidateArtifactTests(unittest.TestCase):
    def test_exact_41_jars_and_non_authoritative_manifest_are_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root)
            data = artifact()
            self.assertEqual(
                protocol.CANONICAL_CANDIDATE_COUNT,
                len(
                    protocol.validate_candidate_artifact(
                        data,
                        hashlib.sha256(data).hexdigest(),
                        binding(),
                        protocol.load_policy(root),
                    )
                ),
            )

    def test_40_42_jars_and_pseudo_xml_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root)
            policy = protocol.load_policy(root)
            for value in (
                artifact(names=NAMES[:-1]),
                artifact(names=NAMES + ["extra.jar"]),
                artifact(names=[REPORT_OWNER_NAMES[0], *NAMES[:-1]]),
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
            root = Path(directory)
            write_policy(root)
            (root / protocol.POLICY_ROOT / "public-api-profile.json").unlink()
            with self.assertRaisesRegex(
                protocol.ProtocolError, "protected policy bundle is invalid"
            ):
                protocol.load_policy(root)

    def test_manifest_stale_extra_duplicate_and_wrong_version_are_rejected(
        self,
    ) -> None:
        mutations = (
            lambda value: value.__setitem__("candidate_sha", OTHER_SHA),
            lambda value: value["jars"].append(dict(value["jars"][0])),
            lambda value: value["jars"][1].__setitem__(
                "normalized_name", value["jars"][0]["normalized_name"]
            ),
            lambda value: value["jars"][0].__setitem__("version", "9.9.9"),
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root)
            policy = protocol.load_policy(root)
            for mutation in mutations:
                with self.subTest(mutation=mutation):
                    value = artifact(manifest_mutation=mutation)
                    with self.assertRaises(protocol.ProtocolError):
                        protocol.validate_candidate_artifact(
                            value,
                            hashlib.sha256(value).hexdigest(),
                            binding(),
                            policy,
                        )

    def test_swapped_current_only_coordinates_and_missing_metadata_are_rejected(
        self,
    ) -> None:
        first = NAMES[20]
        second = NAMES[21]
        missing_metadata = io.BytesIO()
        with zipfile.ZipFile(missing_metadata, "w") as archive:
            archive.writestr("example/Api.class", b"class")
        cases = (
            {
                first: jar_bytes(second[:-4]),
                second: jar_bytes(first[:-4]),
            },
            {first: missing_metadata.getvalue()},
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root)
            policy = protocol.load_policy(root)
            for overrides in cases:
                with self.subTest(overrides=sorted(overrides)):
                    value = artifact(jar_overrides=overrides)
                    with self.assertRaises(protocol.ProtocolError):
                        protocol.validate_candidate_artifact(
                            value,
                            hashlib.sha256(value).hexdigest(),
                            binding(),
                            policy,
                        )

    def test_current_only_manifest_descriptor_and_source_rewrites_are_rejected(
        self,
    ) -> None:
        first = NAMES[20]
        second = NAMES[21]

        def swap_manifest_coordinates(value: dict[str, Any]) -> None:
            claims = {item["normalized_name"]: item for item in value["jars"]}
            for name, replacement in ((first, second), (second, first)):
                claims[name]["artifact_id"] = replacement[:-4]
                claims[name]["source_path"] = (
                    f"modules/{replacement[:-4]}/target/{replacement[:-4]}-"
                    f"{protocol.CANDIDATE_VERSION}.jar"
                )

        def change_source_path(value: dict[str, Any]) -> None:
            value["jars"][20]["source_path"] = (
                f"modules/{value['jars'][20]['artifact_id']}/other/"
                f"{value['jars'][20]['artifact_id']}-"
                f"{protocol.CANDIDATE_VERSION}.jar"
            )

        cases = (
            artifact(
                jar_overrides={
                    first: jar_bytes(second[:-4]),
                    second: jar_bytes(first[:-4]),
                },
                manifest_mutation=swap_manifest_coordinates,
            ),
            artifact(jar_overrides={first: jar_bytes(second[:-4])}),
            artifact(manifest_mutation=change_source_path),
        )
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root)
            policy = protocol.load_policy(root)
            for value in cases:
                with self.subTest(digest=hashlib.sha256(value).hexdigest()):
                    with self.assertRaises(protocol.ProtocolError):
                        protocol.validate_candidate_artifact(
                            value,
                            hashlib.sha256(value).hexdigest(),
                            binding(),
                            policy,
                        )

    def test_maven_properties_xml_paths_and_parent_fallback_are_strict(self) -> None:
        expected = {
            "artifactId": "coco-00",
            "groupId": "io.github.coco",
            "version": protocol.CANDIDATE_VERSION,
        }
        self.assertEqual(
            expected,
            protocol.validate_inner_jar(
                "coco-00.jar", jar_bytes(xml_parent_fallback=True)
            ),
        )
        nested_root = "META-INF/maven/nested/io.github.coco/coco-00"
        invalid = (
            jar_bytes(include_properties=False),
            jar_bytes(include_pom=False),
            jar_bytes(xml_artifact_id="coco-01"),
            jar_bytes(
                extra_descriptors=[
                    (
                        f"{nested_root}/pom.properties",
                        properties_bytes("coco-00"),
                    )
                ]
            ),
            jar_bytes(
                extra_descriptors=[(f"{nested_root}/pom.xml", pom_xml_bytes("coco-00"))]
            ),
            jar_bytes(
                properties_path="META-INF/maven/wrong/coco-00/pom.properties",
                pom_path="META-INF/maven/wrong/coco-00/pom.xml",
            ),
            jar_bytes(pom_path="META-INF/maven/io.github.coco/other/pom.xml"),
            jar_bytes(pom_data=b"<!DOCTYPE project><project/>"),
        )
        for value in invalid:
            with self.subTest(size=len(value)):
                with self.assertRaises(protocol.ProtocolError):
                    protocol.validate_inner_jar("coco-00.jar", value)


class CanonicalPolicyIntegrationTests(unittest.TestCase):
    def test_check_policy_accepts_canonical_assets_and_rejects_wrong_cli_digest(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_canonical_policy_commit(root)
            self.assertEqual(
                0,
                protocol.main(["check-policy", "--protected-root", str(root)]),
            )
            self.assertEqual(
                protocol.JAPICMP_SHA256,
                protocol.load_policy(root)["japicmpPolicy"]["cli"]["sha256"],
            )
            mutate_policy_file(
                root,
                "japicmp-policy.json",
                lambda value: value["cli"].__setitem__("sha256", "f" * 64),
            )
            with redirect_stderr(io.StringIO()):
                self.assertEqual(
                    1,
                    protocol.main(["check-policy", "--protected-root", str(root)]),
                )


class ProtectedPolicyTests(unittest.TestCase):
    def test_exact_removed_allowlist_is_retained_in_policy(self) -> None:
        exception = {
            "artifact": NAMES[0][:-4],
            "class": "example.Api",
            "member": "removed()",
            "category": "REMOVED",
            "reason": "Exact removed method migration.",
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root, [exception])
            self.assertEqual(
                (exception,),
                protocol.load_policy(root)["exceptions"],
            )
        class_exception = {
            "artifact": NAMES[0][:-4],
            "class": "example.Api",
            "member": "<class>",
            "category": "REMOVED",
            "reason": "Exact removed class migration.",
        }
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root, [class_exception])
            self.assertEqual(
                (class_exception,), protocol.load_policy(root)["exceptions"]
            )

    def test_allowlist_requires_exact_finding_key(self) -> None:
        exact = {
            "artifact": NAMES[0][:-4],
            "class": "example.Api",
            "member": "removed()",
            "category": "REMOVED",
            "reason": "Exact removed method migration.",
        }
        invalid = (
            {**exact, "class": "example.*"},
            {**exact, "member": "*"},
            {**exact, "category": "MODIFIED"},
            {**exact, "artifact": "*"},
            {**exact, "member": "<artifact>"},
        )
        for exception in invalid:
            with (
                self.subTest(exception=exception),
                tempfile.TemporaryDirectory() as directory,
            ):
                root = Path(directory)
                write_policy(root, [exception])
                with self.assertRaises(protocol.ProtocolError):
                    protocol.load_policy(root)

    def test_profile_rejects_missing_target_chain_cycle_and_current_only_swap(
        self,
    ) -> None:
        mutations = (
            lambda value: value["artifacts"][0]["comparison"].__setitem__(
                "targetArtifactId", "missing-target"
            ),
            lambda value: value["artifacts"][20]["comparison"].__setitem__(
                "targetArtifactId", value["artifacts"][21]["artifactId"]
            ),
            lambda value: value["artifacts"][20]["comparison"].__setitem__(
                "targetArtifactId", value["artifacts"][0]["artifactId"]
            ),
            lambda value: value["artifacts"][30]["comparison"].__setitem__(
                "targetArtifactId", value["artifacts"][31]["artifactId"]
            ),
        )
        for mutation in mutations:
            with (
                self.subTest(mutation=mutation),
                tempfile.TemporaryDirectory() as directory,
            ):
                root = Path(directory)
                write_policy(root)
                mutate_policy_file(root, "public-api-profile.json", mutation)
                with self.assertRaises(protocol.ProtocolError):
                    protocol.load_policy(root)

    def test_v3_ledger_requires_exact_51_present_and_missing_entries(self) -> None:
        extra_missing = {
            "artifactId": "coco-99",
            "baselineState": "missing",
            "jarStatus": 404,
            "pomStatus": 404,
        }
        mutations = (
            lambda value: value["artifacts"].pop(),
            lambda value: value["artifacts"][20].__setitem__("jarSha256", "f" * 64),
            lambda value: value["artifacts"][0].pop("jarSize"),
            lambda value: value["artifacts"][0].__setitem__("jarSize", 0),
            lambda value: value["artifacts"].append(extra_missing),
        )
        for mutation in mutations:
            with (
                self.subTest(mutation=mutation),
                tempfile.TemporaryDirectory() as directory,
            ):
                root = Path(directory)
                write_policy(root)
                mutate_policy_file(root, "baseline-sha256.json", mutation)
                with self.assertRaises(protocol.ProtocolError):
                    protocol.load_policy(root)

    def test_profile_requires_exact_51_report_owners(self) -> None:
        extra_owner = {
            "artifactId": "coco-99",
            "baselineState": "missing",
            "comparison": {"targetArtifactId": "coco-99"},
            "groupId": "io.github.coco",
            "jarName": "coco-99.jar",
            "modulePath": "modules/coco-99",
        }
        mutations = (
            lambda value: value["artifacts"].pop(),
            lambda value: value["artifacts"].append(extra_owner),
        )
        for mutation in mutations:
            with (
                self.subTest(mutation=mutation),
                tempfile.TemporaryDirectory() as directory,
            ):
                root = Path(directory)
                write_policy(root)
                mutate_policy_file(root, "public-api-profile.json", mutation)
                with self.assertRaises(protocol.ProtocolError):
                    protocol.load_policy(root)

    def test_both_japicmp_tool_locks_and_finding_metadata_are_exact(self) -> None:
        mutations = (
            lambda value: value["mavenPlugin"].__setitem__("sha256", "f" * 64),
            lambda value: value["cli"].__setitem__("size", protocol.JAPICMP_SIZE - 1),
            lambda value: value["cli"].__setitem__("sha256", "f" * 64),
            lambda value: value.__setitem__("allowedCategories", ["MODIFIED"]),
            lambda value: value.__setitem__(
                "findingKey", ["artifact", "class", "category"]
            ),
        )
        for mutation in mutations:
            with (
                self.subTest(mutation=mutation),
                tempfile.TemporaryDirectory() as directory,
            ):
                root = Path(directory)
                write_policy(root)
                mutate_policy_file(root, "japicmp-policy.json", mutation)
                with self.assertRaises(protocol.ProtocolError):
                    protocol.load_policy(root)

    def test_policy_bundle_hash_matches_api_checker_canonical_payload(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root)
            policy_directory = root / protocol.POLICY_ROOT
            shared = protocol.shared_policy_bundle_module(root)
            paths = (
                policy_directory / "public-api-profile.json",
                policy_directory / "baseline-sha256.json",
                policy_directory / "allowlist.json",
                policy_directory / protocol.BASELINE_SIGNING_KEY_FILE,
                policy_directory / "japicmp-policy.json",
            )
            direct = shared.normalized_policy_bundle(*paths)
            direct_sha256 = shared.policy_bundle_sha256(*paths)
            shadow = protocol.load_policy(root)
            self.assertEqual(direct, shadow["policyBundle"])
            self.assertEqual(direct_sha256, shadow["policyBundleSha256"])
            self.assertEqual(GOLDEN_POLICY_BUNDLE_SHA256, shadow["policyBundleSha256"])

    def test_all_20_baselines_select_profile_comparison_targets(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root)
            plan = protocol.comparison_plan(protocol.load_policy(root))
            self.assertEqual(20, len(plan))
            self.assertEqual(10, sum(source != target for source, target in plan))
            self.assertEqual(
                [
                    (f"coco-{index:02d}", f"coco-{index + 20:02d}")
                    for index in range(10)
                ],
                [item for item in plan if item[0] != item[1]],
            )

    def test_baseline_url_is_exact_canonical_maven_central_path(self) -> None:
        invalid_urls = (
            "https://repo1.maven.org/maven2/io/github/coco/coco-00/2.0.1/coco-00-2.0.1.jar",
            "https://proxy.example/maven2/io/github/coco/coco-00/2.0.1/coco-00-2.0.1.jar",
            "https://repo.maven.apache.org/maven2/io/github/coco/coco-00/2.0.1/other-2.0.1.jar",
            "https://repo.maven.apache.org/maven2/io/github/coco/coco-00/2.0.1/coco-00-2.0.1.jar?cache=1",
        )
        for url in invalid_urls:
            with self.subTest(url=url):
                with self.assertRaises(protocol.ProtocolError):
                    protocol.validate_maven_central_url(url, "io.github.coco:coco-00")


class InnerJarLimitTests(unittest.TestCase):
    @staticmethod
    def make_jar(
        entries: list[tuple[str, bytes]], compression: int = zipfile.ZIP_STORED
    ) -> bytes:
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w", compression) as archive:
            for name, data in entries:
                archive.writestr(name, data)
        return stream.getvalue()

    def test_inner_jar_rejects_paths_duplicates_and_case_collisions(self) -> None:
        cases = (
            self.make_jar([("../Api.class", b"x")]),
            self.make_jar([("Api.class", b"x"), ("Api.class", b"x")]),
            self.make_jar([("Api.class", b"x"), ("api.class", b"x")]),
        )
        for value in cases:
            with self.subTest(size=len(value)):
                with self.assertRaises(protocol.ProtocolError):
                    protocol.validate_inner_jar("fixture.jar", value)

    def test_inner_jar_rejects_entry_count_single_total_and_ratio_limits(self) -> None:
        cases = (
            self.make_jar(
                [
                    (f"entry-{index}.class", b"")
                    for index in range(protocol.MAX_JAR_ENTRIES + 1)
                ]
            ),
            self.make_jar([("large.class", b"x" * (protocol.MAX_JAR_ENTRY_BYTES + 1))]),
            self.make_jar(
                [
                    (f"large-{index}.class", b"x" * (7 * 1024 * 1024))
                    for index in range(5)
                ]
            ),
            self.make_jar([("bomb.class", b"0" * 200_000)], zipfile.ZIP_DEFLATED),
        )
        for value in cases:
            with self.subTest(size=len(value)):
                with self.assertRaises(protocol.ProtocolError):
                    protocol.validate_inner_jar("fixture.jar", value)


class FakeHeaders:
    def __init__(self, values: list[str] | None) -> None:
        self.values = values

    def get_all(self, name: str) -> list[str] | None:
        if name != "Content-Length":
            return None
        return self.values


class FakeResponse:
    def __init__(
        self,
        data: bytes,
        header_values: list[str] | None | object = ...,
        fail_after: int | None = None,
        failure: Exception | None = None,
    ) -> None:
        self.data = data
        self.offset = 0
        self.read_sizes: list[int] = []
        self.fail_after = fail_after
        self.failure = failure or OSError("simulated response read failure")
        values = [str(len(data))] if header_values is ... else header_values
        self.headers = FakeHeaders(values)

    def read(self, size: int) -> bytes:
        self.read_sizes.append(size)
        if self.fail_after is not None and self.offset >= self.fail_after:
            raise self.failure
        result = self.data[self.offset : self.offset + size]
        self.offset += len(result)
        return result


class BoundedArtifactDownloadTests(unittest.TestCase):
    def test_stream_uses_bounded_chunks_and_incremental_digest(self) -> None:
        data = b"x" * 150_000
        response = FakeResponse(data)
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "artifact.zip"
            protocol.stream_response_to_file(
                response,
                destination,
                len(data),
                hashlib.sha256(data).hexdigest(),
                200_000,
            )
            self.assertEqual(data, destination.read_bytes())
            self.assertTrue(response.read_sizes)
            self.assertTrue(all(0 < size <= 64 * 1024 for size in response.read_sizes))

    def test_content_length_precheck_is_single_canonical_bounded_value(self) -> None:
        invalid_headers = (
            None,
            ["8", "8"],
            ["eight"],
            ["0"],
            ["+8"],
            [" 8"],
            ["9"],
        )
        for values in invalid_headers:
            with (
                self.subTest(values=values),
                tempfile.TemporaryDirectory() as directory,
            ):
                response = FakeResponse(b"12345678", header_values=values)
                destination = Path(directory) / "artifact.zip"
                with self.assertRaisesRegex(protocol.ProtocolError, "Content-Length"):
                    protocol.stream_response_to_file(
                        response,
                        destination,
                        8,
                        hashlib.sha256(b"12345678").hexdigest(),
                        8,
                    )
                self.assertEqual([], response.read_sizes)
                self.assertFalse(destination.exists())

    def test_limit_plus_one_and_digest_failure_stop_and_cleanup(self) -> None:
        cases = (
            (
                FakeResponse(b"123456789", header_values=["8"]),
                hashlib.sha256(b"12345678").hexdigest(),
            ),
            (FakeResponse(b"12345678"), "0" * 64),
            (
                FakeResponse(b"1234567", header_values=["8"]),
                hashlib.sha256(b"12345678").hexdigest(),
            ),
            (
                FakeResponse(b"12345678", fail_after=0),
                hashlib.sha256(b"12345678").hexdigest(),
            ),
        )
        for response, digest in cases:
            with (
                self.subTest(digest=digest),
                tempfile.TemporaryDirectory() as directory,
            ):
                destination = Path(directory) / "artifact.zip"
                with self.assertRaises(protocol.ProtocolError):
                    protocol.stream_response_to_file(
                        response, destination, 8, digest, 8
                    )
                self.assertFalse(destination.exists())
                self.assertTrue(all(size <= 9 for size in response.read_sizes))

    def test_runtime_and_custom_read_exceptions_are_normalized_and_cleaned(
        self,
    ) -> None:
        class CustomReadError(Exception):
            pass

        for failure in (RuntimeError("runtime"), CustomReadError("custom")):
            with (
                self.subTest(failure=type(failure).__name__),
                tempfile.TemporaryDirectory() as directory,
            ):
                response = FakeResponse(b"12345678", fail_after=0, failure=failure)
                destination = Path(directory) / "artifact.zip"
                with self.assertRaisesRegex(
                    protocol.ProtocolError, "artifact download stream failed"
                ):
                    protocol.stream_response_to_file(
                        response,
                        destination,
                        8,
                        hashlib.sha256(b"12345678").hexdigest(),
                        8,
                    )
                self.assertFalse(destination.exists())

    def test_invalid_zip_download_is_cleaned_by_remote_verifier(self) -> None:
        api = FakeApi()
        api.archive = b"not-a-zip"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root)
            self.assertEqual(
                protocol.FAIL_ARTIFACT,
                protocol.verify_remote_artifact(
                    api, binding(), root, root / "unused-japicmp.jar"
                ),
            )
            self.assertIsNotNone(api.last_download_destination)
            self.assertFalse(api.last_download_destination.exists())


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
        self.last_download_destination: Path | None = None
        self.module_pom_overrides: dict[str, bytes] = {}

    def get_json(self, path: str) -> object:
        parsed = urlparse(path)
        route = parsed.path
        contents_marker = "/contents/"
        if contents_marker in route:
            repository_route, encoded_path = route.split(contents_marker, 1)
            if repository_route != f"repos/{FORK}" or parse_qs(parsed.query) != {
                "ref": [CANDIDATE_SHA]
            }:
                raise AssertionError(path)
            module_pom_path = unquote(encoded_path)
            if not module_pom_path.startswith(
                "modules/"
            ) or not module_pom_path.endswith("/pom.xml"):
                raise AssertionError(path)
            artifact_id = Path(module_pom_path).parent.name
            data = self.module_pom_overrides.get(
                module_pom_path,
                pom_xml_bytes(
                    artifact_id,
                    version="${revision}",
                    parent_fallback=True,
                ),
            )
            return {
                "content": base64.b64encode(data).decode("ascii"),
                "encoding": "base64",
                "path": module_pom_path,
                "size": len(data),
                "type": "file",
            }
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
                        "size_in_bytes": len(self.archive),
                        "workflow_run": {"id": RUN_ID},
                        "digest": "sha256:" + hashlib.sha256(self.archive).hexdigest(),
                    }
                ]
            }
        if route.endswith("/statuses"):
            return []
        raise AssertionError(path)

    def download_artifact(
        self,
        path: str,
        destination: Path,
        expected_size: int,
        expected_digest: str,
    ) -> None:
        self.assert_download(path, expected_size, expected_digest)
        self.last_download_destination = destination
        destination.write_bytes(self.archive)

    def assert_download(
        self, path: str, expected_size: int, expected_digest: str
    ) -> None:
        if not path.endswith("/artifacts/9/zip"):
            raise AssertionError(path)
        if expected_size != len(self.archive):
            raise AssertionError(expected_size)
        if expected_digest != hashlib.sha256(self.archive).hexdigest():
            raise AssertionError(expected_digest)

    def post_json(self, path: str, body: object) -> object:
        self.posts.append((path, body))
        return {}


class CandidateModulePomTests(unittest.TestCase):
    def test_exact_head_module_poms_match_profile_and_mapping_drift_fails(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_policy(root)
            policy = protocol.load_policy(root)
            api = FakeApi()
            protocol.validate_candidate_module_poms(api, binding(), policy)
            api.module_pom_overrides["modules/coco-20/pom.xml"] = pom_xml_bytes(
                "coco-21",
                version="${revision}",
                parent_fallback=True,
            )
            with self.assertRaisesRegex(
                protocol.ProtocolError, "module POM/profile identity mismatch"
            ):
                protocol.validate_candidate_module_poms(api, binding(), policy)


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
        self.assertIn(
            "Stage exactly 41 profile-derived canonical candidate JARs", producer
        )
        self.assertIn("-Drevision=2.0.2-SNAPSHOT", producer)
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
        protocol_source = self.read(
            ".github/scripts/api_compatibility_gate_protocol.py"
        )
        self.assertIn("workflow_dispatch:", workflow)
        self.assertIn("workflow_run:", workflow)
        self.assertIn("verify-jars:", workflow)
        self.assertIn("timeout-minutes: 30", workflow)
        self.assertIn("statuses: write", workflow)
        self.assertIn("--noproxy '*' --max-redirs 0", workflow)
        self.assertNotIn("curl --fail --location", workflow)
        self.assertIn("repo.maven.apache.org", workflow)
        self.assertNotIn("repo1.maven.org", workflow)
        self.assertIn("%{url_effective}", workflow)
        self.assertIn('"-Xmx512m"', protocol_source)
        self.assertIn('"-XX:MaxMetaspaceSize=192m"', protocol_source)
        self.assertIn("timeout=JAPICMP_TIMEOUT_SECONDS", protocol_source)
        self.assertIn('"--error-on-binary-incompatibility"', protocol_source)
        self.assertIn('"--error-on-source-incompatibility"', protocol_source)
        self.assertNotIn("incompatibility-modifications", protocol_source)
        self.assertIn("urllib.request.ProxyHandler({})", protocol_source)
        self.assertIn("NoRedirectHandler()", protocol_source)
        self.assertIn('"Cache-Control": "no-cache, no-store"', protocol_source)
        self.assertIn("stream_response_to_file", protocol_source)
        self.assertIn('get_all("Content-Length")', protocol_source)
        self.assertNotIn("def get_bytes", protocol_source)
        self.assertIn('"candidate_version": CANDIDATE_VERSION', protocol_source)
        self.assertIn('"normalized_name"', protocol_source)
        publisher = workflow.split("  publish:", 1)[1]
        self.assertNotIn("download-artifact", publisher)
        self.assertNotIn("--japicmp", publisher)
        self.assertIn("VERDICT:", publisher)
        self.assertNotIn("CI gate", publisher)
        for line in workflow.splitlines():
            if "uses: actions/" in line:
                self.assertRegex(line.split("@", 1)[1].split()[0], r"^[0-9a-f]{40}$")


@unittest.skipUnless(
    os.environ.get("COCO_REACTOR_INTEGRATION_ROOT"),
    "set COCO_REACTOR_INTEGRATION_ROOT to run real reactor staging integration",
)
class RealReactorStagingIntegrationTests(unittest.TestCase):
    def test_real_reactor_build_stages_bare_names_and_passes_verifier_inventory(
        self,
    ) -> None:
        root = Path(os.environ["COCO_REACTOR_INTEGRATION_ROOT"]).resolve(strict=True)
        candidate_sha = protocol.local_head(root)
        protocol.assert_clean_checkout(root, candidate_sha)
        maven = shutil.which("mvn") or shutil.which("mvn.cmd")
        if maven is None:
            raise unittest.SkipTest("Maven is required")
        subprocess.run(
            [
                maven,
                "-B",
                "-ntp",
                f"-Drevision={protocol.CANDIDATE_VERSION}",
                "-DskipTests",
                "clean",
                "package",
            ],
            cwd=root,
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.STDOUT,
            timeout=300,
        )
        protocol.assert_clean_checkout(root, candidate_sha)
        with tempfile.TemporaryDirectory() as directory:
            temporary = Path(directory)
            stage = temporary / "stage"
            protocol.stage_candidate(
                root,
                stage,
                candidate_sha,
                "pull_request",
                RUN_ID,
                ATTEMPT,
            )
            staged_names = sorted(path.name for path in (stage / "jars").iterdir())
            canonical_ids = sorted(
                artifact_id
                for artifact_id in REACTOR_ARTIFACT_IDS
                if REACTOR_COMPARISON_TARGETS.get(artifact_id, artifact_id)
                == artifact_id
            )
            self.assertEqual(
                [f"{artifact_id}.jar" for artifact_id in canonical_ids],
                staged_names,
            )
            self.assertTrue(
                all(protocol.CANDIDATE_VERSION not in name for name in staged_names)
            )
            archive_stream = io.BytesIO()
            with zipfile.ZipFile(archive_stream, "w", zipfile.ZIP_DEFLATED) as archive:
                for path in sorted(stage.rglob("*")):
                    if path.is_file():
                        archive.write(path, path.relative_to(stage).as_posix())
            policy_root = temporary / "policy"
            write_policy(
                policy_root,
                artifact_ids=REACTOR_ARTIFACT_IDS,
                group_id="io.github.patton174",
                module_paths=REACTOR_MODULE_PATHS,
                baseline_ids=REACTOR_BASELINE_IDS,
                comparison_targets=REACTOR_COMPARISON_TARGETS,
            )
            value = archive_stream.getvalue()
            actual_binding = {
                **binding(),
                "candidate_repository": "local/reactor",
                "candidate_sha": candidate_sha,
            }
            policy = protocol.load_policy(policy_root)

            class LocalPomApi:
                def get_json(self, path: str) -> object:
                    parsed = urlparse(path)
                    prefix = "repos/local/reactor/contents/"
                    if not parsed.path.startswith(prefix) or parse_qs(parsed.query) != {
                        "ref": [candidate_sha]
                    }:
                        raise AssertionError(path)
                    module_pom_path = unquote(parsed.path.removeprefix(prefix))
                    data = (root / module_pom_path).read_bytes()
                    return {
                        "content": base64.b64encode(data).decode("ascii"),
                        "encoding": "base64",
                        "path": module_pom_path,
                        "size": len(data),
                        "type": "file",
                    }

            protocol.validate_candidate_module_poms(
                LocalPomApi(), actual_binding, policy
            )
            self.assertEqual(
                protocol.CANONICAL_CANDIDATE_COUNT,
                len(
                    protocol.validate_candidate_artifact(
                        value,
                        hashlib.sha256(value).hexdigest(),
                        actual_binding,
                        policy,
                    )
                ),
            )


@unittest.skipUnless(
    os.environ.get("COCO_JAPICMP_INTEGRATION_JAR"),
    "set COCO_JAPICMP_INTEGRATION_JAR to run real japicmp integration",
)
class RealJapicmpIntegrationTests(unittest.TestCase):
    def test_real_compatible_breaking_and_exact_removed_allowlist(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            supplied = Path(os.environ["COCO_JAPICMP_INTEGRATION_JAR"])
            self.assertEqual(protocol.JAPICMP_SIZE, supplied.stat().st_size)
            japicmp = supplied.resolve(strict=True)
            old = build_java_jar(
                root,
                "old",
                """package example;
public class Api {
    public void keep() {}
    public void removed() {}
    public void other() {}
}
""",
            )
            compatible = build_java_jar(
                root,
                "compatible",
                """package example;
public class Api {
    public void keep() {}
    public void removed() {}
    public void other() {}
    public void added() {}
}
""",
            )
            one_removed = build_java_jar(
                root,
                "one-removed",
                """package example;
public class Api {
    public void keep() {}
    public void other() {}
}
""",
            )
            two_removed = build_java_jar(
                root,
                "two-removed",
                """package example;
public class Api {
    public void keep() {}
}
""",
            )
            self.assertEqual(
                0,
                protocol.invoke_japicmp(
                    old, compatible, japicmp, root / "compatible.xml"
                ),
            )
            self.assertEqual(
                1,
                protocol.invoke_japicmp(
                    old, one_removed, japicmp, root / "one-removed.xml"
                ),
            )
            artifact_id = NAMES[0][:-4]
            exact = (
                {
                    "artifact": artifact_id,
                    "class": "example.Api",
                    "member": "removed()",
                    "category": "REMOVED",
                    "reason": "Exact removed method migration.",
                },
            )
            protocol.compare_jars(
                artifact_id,
                old,
                one_removed,
                japicmp,
                root / "allowlisted.xml",
                exact,
            )
            with self.assertRaisesRegex(
                protocol.ProtocolError, "breaking API or ABI change"
            ):
                protocol.compare_jars(
                    artifact_id,
                    old,
                    two_removed,
                    japicmp,
                    root / "unallowlisted.xml",
                    exact,
                )


if __name__ == "__main__":
    unittest.main(verbosity=2)
