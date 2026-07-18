#!/usr/bin/env python3
"""Validate japicmp reports for the public Coco API compatibility gate."""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import re
import sys
import time
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any
from xml.etree import ElementTree

from path_io import (
    canonical_identity,
    directory_metadata,
    ensure_contained,
    entry_exists,
    file_snapshot,
    glob_files,
    logical_absolute,
    read_bytes,
)
from policy_bundle import policy_bundle_sha256


BASELINE_VERSION = "2.0.1"
PROFILE_ID = "public-api-compatibility"
INTERNAL_MARKERS = (".internal.", "$internal", ".internal$")
REPORT_RELATIVE_PATH = Path("target/japicmp/public-api-compatibility.xml")
ATTESTED_REPORT_PREFIX = Path("target/public-api-compatibility")
MAVEN_NAMESPACE = "{http://maven.apache.org/POM/4.0.0}"
ARTIFACT_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*")
CANDIDATE_VERSION_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._+-]*")
RUN_ID_PATTERN = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{7,127}")
SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")
PROTECTED_SHA_PATTERN = re.compile(r"[0-9a-f]{40}")
NON_PRIMARY_JAR_SUFFIXES = ("-sources.jar", "-javadoc.jar", "-tests.jar")
BASELINE_ORIGIN = "https://repo.maven.apache.org/maven2"
BASELINE_GROUP_ID = "io.github.patton174"
BASELINE_SIGNING_FINGERPRINT = "5A99C8EF1C30294660E533E36191CBA3A67073D5"
BASELINE_SIGNING_KEY_SHA256 = (
    "43d01e87b2f84c04794246fe15dffe5f181c037717847c9883ccffd92d1ba504"
)
POLICY_ID = "coco-public-api-compatibility"
CANDIDATE_VERSION_SOURCE = "mavenProperty:revision"
PROFILE_SCHEMA_VERSION = 3
BASELINE_LEDGER_SCHEMA_VERSION = 3
ALLOWLIST_SCHEMA_VERSION = 3
ATTESTATION_SCHEMA_VERSION = 2


@dataclass(frozen=True)
class ExpectedArtifact:
    artifact: str
    module: PurePosixPath
    candidate_artifact: str
    candidate_module: PurePosixPath
    candidate_group_id: str = BASELINE_GROUP_ID
    comparison: str = "self"
    jar_name: str = ""
    baseline_state: str = "present"
    candidate_jar_name: str = ""
    group_id: str = BASELINE_GROUP_ID


@dataclass(frozen=True)
class Manifest:
    artifacts: tuple[ExpectedArtifact, ...]


@dataclass(frozen=True)
class BaselineArtifact:
    artifact: str
    pom_sha256: str
    jar_sha256: str
    pom_size: int = 0
    jar_size: int = 0


@dataclass(frozen=True)
class BaselineLedger:
    artifacts: tuple[BaselineArtifact, ...]
    missing_artifacts: tuple[str, ...]


@dataclass(frozen=True)
class Finding:
    artifact: str
    class_name: str
    member_kind: str
    member_name: str
    status: str
    binary_compatible: str
    source_compatible: str
    report: Path

    def display(self) -> str:
        member = (
            f"{self.member_kind} {self.member_name}"
            if self.member_name
            else self.member_kind
        )
        return (
            f"{self.artifact}: {self.class_name}: {member} status={self.status} "
            f"binary={self.binary_compatible} source={self.source_compatible} "
            f"({self.report})"
        )


@dataclass(frozen=True)
class ReportData:
    artifact: str
    old_version: str | None
    old_jar: str | None
    new_version: str | None
    new_jar: str | None
    findings: tuple[Finding, ...]


def repository_root() -> Path:
    return logical_absolute(Path(__file__)).parents[3]


def default_manifest_path() -> Path:
    return Path(__file__).with_name("public-api-profile.json")


def default_baseline_ledger_path() -> Path:
    return Path(__file__).with_name("baseline-sha256.json")


def is_public_non_internal(class_name: str) -> bool:
    return not any(marker in class_name for marker in INTERNAL_MARKERS)


def _json_object(path: Path, label: str) -> dict[str, Any]:
    if not entry_exists(path):
        raise FileNotFoundError(path)
    data = json.loads(regular_file_bytes(path, label).decode("utf-8"))
    if not isinstance(data, dict):
        raise ValueError(f"{label} must be a JSON object: {path}")
    return data


def _exact_keys(value: dict[str, Any], expected: set[str], label: str) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        unknown = sorted(actual - expected)
        details = []
        if missing:
            details.append(f"missing keys {missing}")
        if unknown:
            details.append(f"unknown keys {unknown}")
        raise ValueError(f"{label} has invalid keys: {', '.join(details)}")


def _nonempty_string(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip() or value != value.strip():
        raise ValueError(f"{label} must be a non-empty trimmed string.")
    return value


def _artifact_id(value: Any, label: str) -> str:
    artifact = _nonempty_string(value, label)
    if ARTIFACT_PATTERN.fullmatch(artifact) is None:
        raise ValueError(f"{label} is not a valid Maven artifactId: {artifact!r}")
    return artifact


def _module_path(value: Any, label: str) -> PurePosixPath:
    module_text = _nonempty_string(value, label)
    module = PurePosixPath(module_text)
    if (
        module.is_absolute()
        or module_text != module.as_posix()
        or any(part in ("", ".", "..") for part in module.parts)
        or any(ARTIFACT_PATTERN.fullmatch(part) is None for part in module.parts)
    ):
        raise ValueError(
            f"{label} must be a normalized relative POSIX path: {module_text!r}"
        )
    return module


def _sha256(value: Any, label: str) -> str:
    digest = _nonempty_string(value, label)
    if SHA256_PATTERN.fullmatch(digest) is None:
        raise ValueError(f"{label} must be a lowercase SHA-256 digest.")
    return digest


def _positive_integer(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer.")
    return value


def load_japicmp_policy(path: Path) -> dict[str, Any]:
    data = _json_object(path, "Japicmp policy")
    _exact_keys(
        data,
        {
            "schemaVersion",
            "policyId",
            "profile",
            "findingKey",
            "allowedCategories",
            "mavenPlugin",
            "cli",
        },
        "Japicmp policy",
    )
    expected = {
        "schemaVersion": 3,
        "policyId": POLICY_ID,
        "profile": PROFILE_ID,
        "findingKey": ["artifact", "class", "member", "category"],
        "allowedCategories": ["REMOVED"],
        "mavenPlugin": {
            "groupId": "com.github.siom79.japicmp",
            "artifactId": "japicmp-maven-plugin",
            "version": "0.23.1",
            "url": "https://repo.maven.apache.org/maven2/com/github/siom79/japicmp/"
            "japicmp-maven-plugin/0.23.1/japicmp-maven-plugin-0.23.1.jar",
            "size": 44670,
            "sha256": "7df259e8be0c652259ef96416fcc6f2e7ef5e5a340a4df52783350abcd77c4bb",
        },
        "cli": {
            "groupId": "com.github.siom79.japicmp",
            "artifactId": "japicmp",
            "version": "0.23.1",
            "url": "https://repo.maven.apache.org/maven2/com/github/siom79/japicmp/"
            "japicmp/0.23.1/japicmp-0.23.1-jar-with-dependencies.jar",
            "size": 5988558,
            "sha256": "f2300a8531b68e25b678247874a1eae13a07d6842a4a1236845481fc90c5c6c7",
        },
    }
    if data != expected:
        raise ValueError("Japicmp policy does not match the exact gate contract.")
    return data


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def regular_file_bytes(path: Path, label: str) -> bytes:
    return read_bytes(path, label)


def sha256_file(path: Path, label: str) -> str:
    return sha256_bytes(regular_file_bytes(path, label))


def validate_policy_assets(manifest_path: Path, signing_key_path: Path) -> None:
    load_japicmp_policy(manifest_path.with_name("japicmp-policy.json"))
    if (
        sha256_file(signing_key_path, "Baseline signing key")
        != BASELINE_SIGNING_KEY_SHA256
    ):
        raise ValueError(
            "Baseline signing key raw SHA-256 does not match the policy ledger."
        )


def _path_below(path: Path, parent: Path, label: str) -> None:
    ensure_contained(path, parent, label)


def load_baseline_ledger(path: Path) -> BaselineLedger:
    data = _json_object(path, "Baseline ledger")
    _exact_keys(
        data,
        {
            "schemaVersion",
            "policyId",
            "profile",
            "origin",
            "groupId",
            "version",
            "signingFingerprint",
            "signingKeySha256",
            "artifacts",
        },
        "Baseline ledger",
    )
    if data["schemaVersion"] != BASELINE_LEDGER_SCHEMA_VERSION:
        raise ValueError(
            f"Baseline ledger schemaVersion must be {BASELINE_LEDGER_SCHEMA_VERSION}."
        )
    expected_scalars = {
        "policyId": POLICY_ID,
        "profile": PROFILE_ID,
        "origin": BASELINE_ORIGIN,
        "groupId": BASELINE_GROUP_ID,
        "version": BASELINE_VERSION,
        "signingFingerprint": BASELINE_SIGNING_FINGERPRINT,
        "signingKeySha256": BASELINE_SIGNING_KEY_SHA256,
    }
    for key, expected in expected_scalars.items():
        if data[key] != expected:
            raise ValueError(
                f"Baseline ledger {key} must be {expected!r}; found {data[key]!r}."
            )

    entries = data["artifacts"]
    if not isinstance(entries, list) or not entries:
        raise ValueError("Baseline ledger artifacts must be a non-empty array.")
    artifacts: list[BaselineArtifact] = []
    missing_artifacts_list: list[str] = []
    all_artifact_names: list[str] = []
    for index, entry in enumerate(entries):
        label = f"Baseline artifact at index {index}"
        if not isinstance(entry, dict):
            raise ValueError(f"{label} must be an object.")
        artifact = _artifact_id(entry.get("artifactId"), f"{label} artifactId")
        state = _nonempty_string(entry.get("baselineState"), f"{label} baselineState")
        all_artifact_names.append(artifact)
        if state == "missing":
            _exact_keys(
                entry,
                {"artifactId", "baselineState", "pomStatus", "jarStatus"},
                label,
            )
            if entry["pomStatus"] != 404 or entry["jarStatus"] != 404:
                raise ValueError(f"{label} must bind exact POM and JAR 404 responses.")
            missing_artifacts_list.append(artifact)
            continue
        if state != "present":
            raise ValueError(f"{label} has invalid baselineState: {state!r}")
        _exact_keys(
            entry,
            {
                "artifactId",
                "baselineState",
                "pomSha256",
                "jarSha256",
                "pomSize",
                "jarSize",
            },
            label,
        )
        artifacts.append(
            BaselineArtifact(
                artifact,
                _sha256(entry["pomSha256"], f"{label} POM SHA-256"),
                _sha256(entry["jarSha256"], f"{label} JAR SHA-256"),
                _positive_integer(entry["pomSize"], f"{label} POM size"),
                _positive_integer(entry["jarSize"], f"{label} JAR size"),
            )
        )
    if all_artifact_names != sorted(all_artifact_names):
        raise ValueError("Baseline ledger artifacts must be sorted by artifactId.")
    if len(all_artifact_names) != len(set(all_artifact_names)):
        raise ValueError("Baseline ledger contains duplicate artifacts.")
    missing_artifacts = tuple(missing_artifacts_list)
    return BaselineLedger(tuple(artifacts), missing_artifacts)


def _pom_coordinate(pom_bytes: bytes, label: str) -> tuple[str, str, str]:
    root = ElementTree.fromstring(pom_bytes)
    if root.tag != f"{MAVEN_NAMESPACE}project":
        raise ValueError(f"{label} has an unexpected XML root: {root.tag}")
    group_id = root.findtext(f"{MAVEN_NAMESPACE}groupId")
    version = root.findtext(f"{MAVEN_NAMESPACE}version")
    if group_id is None:
        group_id = root.findtext(f"{MAVEN_NAMESPACE}parent/{MAVEN_NAMESPACE}groupId")
    if version is None:
        version = root.findtext(f"{MAVEN_NAMESPACE}parent/{MAVEN_NAMESPACE}version")
    artifact = root.findtext(f"{MAVEN_NAMESPACE}artifactId")
    values = tuple((value or "").strip() for value in (group_id, artifact, version))
    if not all(values):
        raise ValueError(f"{label} does not declare a complete Maven coordinate.")
    return values  # type: ignore[return-value]


def _java_properties(value: bytes, label: str) -> dict[str, str]:
    properties: dict[str, str] = {}
    for raw_line in value.decode("ISO-8859-1").splitlines():
        line = raw_line.strip()
        if not line or line.startswith(("#", "!")):
            continue
        separator = "=" if "=" in line else ":" if ":" in line else None
        if separator is None:
            raise ValueError(f"{label} contains an invalid properties line: {line!r}")
        key, item = (part.strip() for part in line.split(separator, 1))
        if not key or key in properties:
            raise ValueError(f"{label} contains a blank or duplicate key: {key!r}")
        properties[key] = item
    return properties


def validate_baseline_files(
    entry: BaselineArtifact, pom_path: Path, jar_path: Path
) -> None:
    pom_bytes = regular_file_bytes(pom_path, f"{entry.artifact} baseline POM")
    jar_bytes = regular_file_bytes(jar_path, f"{entry.artifact} baseline JAR")
    if sha256_bytes(pom_bytes) != entry.pom_sha256:
        raise ValueError(
            f"Baseline POM SHA-256 mismatch for {entry.artifact}: {pom_path}"
        )
    if sha256_bytes(jar_bytes) != entry.jar_sha256:
        raise ValueError(
            f"Baseline JAR SHA-256 mismatch for {entry.artifact}: {jar_path}"
        )
    if len(pom_bytes) != entry.pom_size:
        raise ValueError(f"Baseline POM size mismatch for {entry.artifact}: {pom_path}")
    if len(jar_bytes) != entry.jar_size:
        raise ValueError(f"Baseline JAR size mismatch for {entry.artifact}: {jar_path}")
    expected_coordinate = (BASELINE_GROUP_ID, entry.artifact, BASELINE_VERSION)
    if (
        _pom_coordinate(pom_bytes, f"{entry.artifact} baseline POM")
        != expected_coordinate
    ):
        raise ValueError(
            f"Baseline POM coordinate mismatch for {entry.artifact}: {pom_path}"
        )
    try:
        with zipfile.ZipFile(io.BytesIO(jar_bytes)) as archive:
            names = archive.namelist()
            if len(names) != len(set(names)):
                raise ValueError(
                    f"Baseline JAR contains duplicate ZIP entries: {jar_path}"
                )
            corrupt = archive.testzip()
            if corrupt is not None:
                raise ValueError(
                    f"Baseline JAR contains a corrupt ZIP entry {corrupt}: {jar_path}"
                )
            properties_name = (
                f"META-INF/maven/{BASELINE_GROUP_ID}/{entry.artifact}/pom.properties"
            )
            if names.count(properties_name) != 1:
                raise ValueError(
                    f"Baseline JAR must contain exact Maven pom.properties: {jar_path}"
                )
            properties = _java_properties(
                archive.read(properties_name), f"{entry.artifact} pom.properties"
            )
    except zipfile.BadZipFile as exc:
        raise ValueError(f"Baseline JAR is not a readable ZIP: {jar_path}") from exc
    actual_coordinate = (
        properties.get("groupId"),
        properties.get("artifactId"),
        properties.get("version"),
    )
    if actual_coordinate != expected_coordinate:
        raise ValueError(
            f"Baseline JAR pom.properties coordinate mismatch for {entry.artifact}: "
            f"{actual_coordinate}"
        )


def load_manifest(path: Path) -> Manifest:
    data = _json_object(path, "Manifest")
    _exact_keys(
        data,
        {
            "schemaVersion",
            "policyId",
            "profile",
            "candidateVersionSource",
            "artifacts",
        },
        "Manifest",
    )
    if data["schemaVersion"] != PROFILE_SCHEMA_VERSION:
        raise ValueError(f"Manifest schemaVersion must be {PROFILE_SCHEMA_VERSION}.")
    if data["policyId"] != POLICY_ID:
        raise ValueError(f"Manifest policyId must be {POLICY_ID}.")
    if data["profile"] != PROFILE_ID:
        raise ValueError(f"Manifest profile must be {PROFILE_ID}.")
    if data["candidateVersionSource"] != CANDIDATE_VERSION_SOURCE:
        raise ValueError(
            f"Manifest candidateVersionSource must be {CANDIDATE_VERSION_SOURCE}."
        )
    if not isinstance(data["artifacts"], list) or not data["artifacts"]:
        raise ValueError("Manifest artifacts must be a non-empty array.")

    parsed: list[dict[str, Any]] = []
    seen_artifacts: set[str] = set()
    seen_modules: set[PurePosixPath] = set()
    for index, entry in enumerate(data["artifacts"]):
        label = f"Manifest artifact at index {index}"
        if not isinstance(entry, dict):
            raise ValueError(f"{label} must be an object.")
        _exact_keys(
            entry,
            {
                "modulePath",
                "groupId",
                "artifactId",
                "jarName",
                "baselineState",
                "comparison",
            },
            label,
        )
        artifact = _artifact_id(entry["artifactId"], f"{label} artifactId")
        module = _module_path(entry["modulePath"], f"{label} modulePath")
        if entry["groupId"] != BASELINE_GROUP_ID:
            raise ValueError(f"{label} groupId must be {BASELINE_GROUP_ID}.")
        jar_name = _nonempty_string(entry["jarName"], f"{label} jarName")
        expected_jar_name = f"{artifact}.jar"
        if jar_name != expected_jar_name:
            raise ValueError(
                f"{label} jarName must be {expected_jar_name!r}; found {jar_name!r}."
            )
        baseline_state = _nonempty_string(
            entry["baselineState"], f"{label} baselineState"
        )
        if baseline_state not in {"present", "missing"}:
            raise ValueError(f"{label} baselineState must be 'present' or 'missing'.")
        comparison_value = entry["comparison"]
        if not isinstance(comparison_value, dict):
            raise ValueError(f"{label} comparison must be an object.")
        _exact_keys(comparison_value, {"targetArtifactId"}, f"{label} comparison")
        target_artifact = _artifact_id(
            comparison_value["targetArtifactId"],
            f"{label} comparison targetArtifactId",
        )

        if artifact in seen_artifacts:
            raise ValueError(f"Manifest contains duplicate artifact: {artifact}")
        if module in seen_modules:
            raise ValueError(f"Manifest contains duplicate module: {module}")
        seen_artifacts.add(artifact)
        seen_modules.add(module)
        parsed.append(
            {
                "artifact": artifact,
                "module": module,
                "jar_name": jar_name,
                "baseline_state": baseline_state,
                "target_artifact": target_artifact,
            }
        )
    artifact_names = [entry["artifact"] for entry in parsed]
    if artifact_names != sorted(artifact_names):
        raise ValueError("Manifest artifacts must be sorted by artifactId.")

    by_artifact = {entry["artifact"]: entry for entry in parsed}
    artifacts: list[ExpectedArtifact] = []
    for entry in parsed:
        target = by_artifact.get(entry["target_artifact"])
        if target is None:
            raise ValueError(
                f"Manifest replacement for {entry['artifact']} targets undeclared "
                f"artifact {entry['target_artifact']}."
            )
        is_self = target["artifact"] == entry["artifact"]
        artifacts.append(
            ExpectedArtifact(
                artifact=entry["artifact"],
                module=entry["module"],
                candidate_artifact=target["artifact"],
                candidate_module=target["module"],
                candidate_group_id=BASELINE_GROUP_ID,
                comparison="self" if is_self else "directReplacement",
                jar_name=entry["jar_name"],
                baseline_state=entry["baseline_state"],
                candidate_jar_name=target["jar_name"],
                group_id=BASELINE_GROUP_ID,
            )
        )
    return Manifest(tuple(artifacts))


def canonical_manifest_entries(manifest: Manifest) -> tuple[ExpectedArtifact, ...]:
    coordinates = {
        (entry.artifact, entry.module): entry for entry in manifest.artifacts
    }
    canonical: list[ExpectedArtifact] = []
    for entry in manifest.artifacts:
        target_coordinate = (entry.candidate_artifact, entry.candidate_module)
        target = coordinates.get(target_coordinate)
        if target is None:
            raise ValueError(
                f"Manifest replacement for {entry.artifact} does not target a "
                f"declared artifact: {entry.candidate_artifact} at "
                f"{entry.candidate_module}"
            )
        if (target.candidate_artifact, target.candidate_module) != target_coordinate:
            raise ValueError(
                f"Manifest replacement for {entry.artifact} targets facade "
                f"{target.artifact}; replacement chains and cycles are forbidden."
            )
        if target_coordinate == (entry.artifact, entry.module):
            canonical.append(entry)
    return tuple(canonical)


def direct_artifact_id(pom_path: Path) -> str:
    if not entry_exists(pom_path):
        raise FileNotFoundError(pom_path)
    root = ElementTree.fromstring(regular_file_bytes(pom_path, "Module POM"))
    artifact = root.find(f"{MAVEN_NAMESPACE}artifactId")
    if artifact is None or not artifact.text or not artifact.text.strip():
        raise ValueError(f"Missing direct artifactId in module POM: {pom_path}")
    return artifact.text.strip()


def direct_property(pom_path: Path, name: str) -> str | None:
    root = ElementTree.fromstring(regular_file_bytes(pom_path, "Module POM"))
    value = root.findtext(f"{MAVEN_NAMESPACE}properties/{MAVEN_NAMESPACE}{name}")
    return value.strip() if value is not None and value.strip() else None


def reactor_manifest(root: Path) -> Manifest:
    repository = logical_absolute(root)
    directory_metadata(repository, "Repository root")
    visited: set[Path] = set()
    artifacts: list[ExpectedArtifact] = []
    seen_artifacts: set[str] = set()
    seen_modules: set[PurePosixPath] = set()

    def visit(pom_path: Path) -> None:
        resolved_pom = logical_absolute(pom_path)
        pom_bytes = regular_file_bytes(resolved_pom, "Reactor POM")
        try:
            resolved_pom.relative_to(repository)
        except ValueError as exc:
            raise ValueError(
                f"Reactor POM escapes repository root: {resolved_pom}"
            ) from exc
        if resolved_pom in visited:
            raise ValueError(
                f"Reactor POM is referenced more than once: {resolved_pom}"
            )
        visited.add(resolved_pom)

        project = ElementTree.fromstring(pom_bytes)
        for module_element in project.findall(
            f"./{MAVEN_NAMESPACE}modules/{MAVEN_NAMESPACE}module"
        ):
            module_text = (module_element.text or "").strip()
            if not module_text:
                raise ValueError(f"Empty reactor module in {resolved_pom}")
            module_dir = logical_absolute(resolved_pom.parent / module_text)
            directory_metadata(module_dir, "Reactor module")
            try:
                relative_module = module_dir.relative_to(repository)
            except ValueError as exc:
                raise ValueError(
                    f"Reactor module escapes repository root: {module_dir}"
                ) from exc

            module = PurePosixPath(relative_module.as_posix())
            module_pom = module_dir / "pom.xml"
            module_project = ElementTree.fromstring(
                regular_file_bytes(module_pom, "Module POM")
            )
            artifact = direct_artifact_id(module_pom)
            packaging = (
                module_project.findtext(f"{MAVEN_NAMESPACE}packaging") or "jar"
            ).strip()
            if not packaging:
                raise ValueError(f"Empty packaging in module POM: {module_pom}")
            if packaging != "pom":
                if artifact in seen_artifacts:
                    raise ValueError(
                        f"Reactor contains duplicate artifactId: {artifact}"
                    )
                if module in seen_modules:
                    raise ValueError(f"Reactor contains duplicate module: {module}")
                seen_artifacts.add(artifact)
                seen_modules.add(module)
                artifacts.append(ExpectedArtifact(artifact, module, artifact, module))
            visit(module_pom)

    visit(repository / "pom.xml")
    if not artifacts:
        raise ValueError(f"Reactor has no non-POM modules below {repository}")
    return Manifest(tuple(sorted(artifacts, key=lambda item: item.artifact)))


def validate_manifest_against_reactor(root: Path, manifest: Manifest) -> None:
    declared = {
        (entry.artifact, entry.module.as_posix()) for entry in manifest.artifacts
    }
    reactor = {
        (entry.artifact, entry.module.as_posix())
        for entry in reactor_manifest(root).artifacts
    }
    if declared == reactor:
        return

    missing = sorted(reactor - declared)
    unexpected = sorted(declared - reactor)
    details = []
    if missing:
        details.append(f"missing reactor artifacts {missing}")
    if unexpected:
        details.append(f"non-reactor artifacts {unexpected}")
    raise ValueError(
        "Manifest does not exactly match non-POM reactor modules: " + "; ".join(details)
    )


def artifact_id_for_report(report: Path) -> str:
    module_dir = report.parent.parent.parent
    return direct_artifact_id(module_dir / "pom.xml")


def bool_value(element: ElementTree.Element, name: str) -> str:
    return element.get(name, "n.a.")


def member_findings(
    artifact: str,
    class_name: str,
    class_element: ElementTree.Element,
    report: Path,
) -> list[Finding]:
    findings: list[Finding] = []
    for section, member_tag in (
        ("constructors", "constructor"),
        ("methods", "method"),
        ("fields", "field"),
        ("interfaces", "interface"),
    ):
        for member in class_element.findall(f"./{section}/{member_tag}"):
            status = member.get("changeStatus", "UNCHANGED")
            binary = bool_value(member, "binaryCompatible")
            source = bool_value(member, "sourceCompatible")
            if status == "REMOVED" or (
                status in ("NEW", "MODIFIED")
                and (binary == "false" or source == "false")
            ):
                findings.append(
                    Finding(
                        artifact,
                        class_name,
                        member_tag,
                        member_descriptor(member_tag, member),
                        status,
                        binary,
                        source,
                        report,
                    )
                )
    return findings


def member_descriptor(member_kind: str, member: ElementTree.Element) -> str:
    name = member.get("name", "")
    if member_kind == "constructor":
        name = "<init>"
    if member_kind not in ("constructor", "method"):
        return name
    parameters = [
        parameter.get("type", "")
        for parameter in member.findall("./parameters/parameter")
    ]
    return f"{name}({','.join(parameters)})"


def report_findings(
    report: Path, *, artifact: str | None = None, report_bytes: bytes | None = None
) -> ReportData:
    root = ElementTree.fromstring(
        report_bytes
        if report_bytes is not None
        else regular_file_bytes(report, "japicmp report")
    )
    if root.tag != "japicmp":
        raise ValueError(f"Unexpected report root element in {report}: {root.tag}")
    artifact = artifact or artifact_id_for_report(report)
    old_version = root.get("oldVersion")
    old_jar = root.get("oldJar")
    new_version = root.get("newVersion")
    new_jar = root.get("newJar")
    findings: list[Finding] = []
    for class_element in root.findall("./classes/class"):
        class_name = class_element.get("fullyQualifiedName", "")
        if not is_public_non_internal(class_name):
            continue
        status = class_element.get("changeStatus", "UNCHANGED")
        binary = bool_value(class_element, "binaryCompatible")
        source = bool_value(class_element, "sourceCompatible")
        if status == "REMOVED" or (
            status == "MODIFIED" and (binary == "false" or source == "false")
        ):
            findings.append(
                Finding(
                    artifact,
                    class_name,
                    "class",
                    class_name,
                    status,
                    binary,
                    source,
                    report,
                )
            )
        class_type = class_element.find("./classType")
        if (
            status != "NEW"
            and class_type is not None
            and class_type.get("changeStatus") not in (None, "UNCHANGED")
        ):
            findings.append(
                Finding(
                    artifact,
                    class_name,
                    "classType",
                    class_type.get("newType", class_type.get("oldType", "")),
                    class_type.get("changeStatus", "MODIFIED"),
                    binary,
                    source,
                    report,
                )
            )
        findings.extend(member_findings(artifact, class_name, class_element, report))
    return ReportData(
        artifact,
        old_version,
        old_jar,
        new_version,
        new_jar,
        tuple(findings),
    )


def _same_path(first: Path, second: Path) -> bool:
    if not entry_exists(first) or not entry_exists(second):
        return os.path.normcase(str(logical_absolute(first))) == os.path.normcase(
            str(logical_absolute(second))
        )
    return canonical_identity(first, "First compared path") == canonical_identity(
        second, "Second compared path"
    )


def _primary_candidate_jars(target: Path, artifact: str) -> tuple[Path, ...]:
    return tuple(
        sorted(
            path
            for path in glob_files(
                target, f"{artifact}-*.jar", "Candidate JAR directory"
            )
            if not path.name.endswith(NON_PRIMARY_JAR_SUFFIXES)
        )
    )


def _validate_baseline_binding(report: Path, data: ReportData) -> None:
    if data.old_version is None or data.old_version == "":
        raise ValueError(f"Missing oldVersion in japicmp report: {report}")
    if data.old_version == "n.a.":
        return
    if data.old_version != BASELINE_VERSION:
        raise ValueError(
            f"Japicmp report for {data.artifact} uses oldVersion "
            f"{data.old_version!r}; expected {BASELINE_VERSION!r}: {report}"
        )
    if data.old_jar is None or not data.old_jar.strip():
        raise ValueError(f"Missing oldJar in japicmp report: {report}")
    baseline_jar = Path(data.old_jar)
    if not baseline_jar.is_absolute():
        raise ValueError(
            f"Japicmp report for {data.artifact} uses non-absolute oldJar "
            f"{data.old_jar!r}: {report}"
        )
    expected_suffix = (
        "io",
        "github",
        "patton174",
        data.artifact,
        BASELINE_VERSION,
        f"{data.artifact}-{BASELINE_VERSION}.jar",
    )
    if tuple(baseline_jar.parts[-len(expected_suffix) :]) != expected_suffix:
        raise ValueError(
            f"Japicmp report for {data.artifact} uses oldJar {data.old_jar!r}; "
            f"expected Maven coordinate io.github.patton174:{data.artifact}:"
            f"{BASELINE_VERSION}: {report}"
        )
    baseline_snapshot = file_snapshot(baseline_jar, "Baseline JAR")
    report_snapshot = file_snapshot(report, "japicmp report")
    if report_snapshot.metadata.st_mtime_ns <= baseline_snapshot.metadata.st_mtime_ns:
        raise ValueError(
            f"Stale japicmp report for {data.artifact}: report is not newer than "
            f"baseline JAR {baseline_jar}"
        )


def validate_candidate_binding(
    root: Path, entry: ExpectedArtifact, report: Path, data: ReportData
) -> None:
    version = data.new_version
    if (
        version is None
        or CANDIDATE_VERSION_PATTERN.fullmatch(version) is None
        or version == "n.a."
    ):
        raise ValueError(
            f"Japicmp report for {data.artifact} has invalid newVersion "
            f"{version!r}: {report}"
        )
    if data.new_jar is None or not data.new_jar.strip():
        raise ValueError(f"Missing newJar in japicmp report: {report}")

    candidate = root.joinpath(
        *entry.candidate_module.parts,
        "target",
        f"{entry.candidate_artifact}-{version}.jar",
    )
    reported_candidate = Path(data.new_jar)
    if not reported_candidate.is_absolute() or not _same_path(
        reported_candidate, candidate
    ):
        raise ValueError(
            f"Japicmp report for {data.artifact} uses newJar {data.new_jar!r}; "
            f"expected canonical candidate {entry.candidate_artifact} at "
            f"{candidate}: {report}"
        )
    candidate_snapshot = file_snapshot(candidate, "Candidate JAR")
    report_snapshot = file_snapshot(report, "japicmp report")
    if report_snapshot.metadata.st_mtime_ns <= candidate_snapshot.metadata.st_mtime_ns:
        raise ValueError(
            f"Stale japicmp report for {data.artifact}: report is not newer than "
            f"candidate JAR {candidate}"
        )

    for sibling in _primary_candidate_jars(candidate.parent, entry.candidate_artifact):
        if _same_path(sibling, candidate):
            continue
        sibling_snapshot = file_snapshot(sibling, "Candidate JAR sibling")
        if (
            sibling_snapshot.metadata.st_mtime_ns
            >= candidate_snapshot.metadata.st_mtime_ns
        ):
            raise ValueError(
                f"Stale japicmp report for {data.artifact}: newer or equally recent "
                f"candidate JAR exists: {sibling}"
            )

    _validate_baseline_binding(report, data)


def load_allowlist(path: Path) -> dict[str, Any]:
    data = _json_object(path, "Allowlist")
    _exact_keys(
        data,
        {
            "schemaVersion",
            "policyId",
            "profile",
            "rules",
        },
        "Allowlist",
    )
    if data["schemaVersion"] != ALLOWLIST_SCHEMA_VERSION:
        raise ValueError(f"Allowlist schemaVersion must be {ALLOWLIST_SCHEMA_VERSION}.")
    if data["policyId"] != POLICY_ID:
        raise ValueError(f"Allowlist policyId must be {POLICY_ID}.")
    if data["profile"] != PROFILE_ID:
        raise ValueError(f"Allowlist profile must be {PROFILE_ID}.")

    rules = data["rules"]
    if not isinstance(rules, list):
        raise ValueError("Allowlist rules must be an array.")
    normalized_rules: list[dict[str, str]] = []
    seen_rules: set[tuple[str, str, str, str]] = set()
    rule_identities: list[tuple[str, str, str, str]] = []
    for index, rule in enumerate(rules):
        label = f"Allowlist rule at index {index}"
        if not isinstance(rule, dict):
            raise ValueError(f"{label} must be an object.")
        _exact_keys(rule, {"artifact", "class", "member", "category", "reason"}, label)
        artifact = _artifact_id(rule["artifact"], f"{label} artifact")
        category = _nonempty_string(rule["category"], f"{label} category")
        reason = _nonempty_string(rule["reason"], f"{label} reason")
        if category != "REMOVED":
            raise ValueError(f"Only explicit REMOVED rules are supported: {label}")
        class_name = _nonempty_string(rule["class"], f"{label} class")
        member = _nonempty_string(rule["member"], f"{label} member")
        values = (artifact, class_name, member, category, reason)
        if any("*" in value for value in values):
            raise ValueError(f"Allowlist does not permit wildcard rules: {label}")
        identity = (artifact, class_name, member, category)
        if identity in seen_rules:
            raise ValueError(f"Allowlist contains duplicate rule: {identity}")
        seen_rules.add(identity)
        rule_identities.append(identity)
        normalized_rule = {
            "artifact": artifact,
            "class": class_name,
            "member": member,
            "category": category,
            "reason": reason,
        }
        normalized_rules.append(normalized_rule)

    if rule_identities != sorted(rule_identities):
        raise ValueError("Allowlist rules must be sorted by their exact identity.")

    return {
        "schemaVersion": ALLOWLIST_SCHEMA_VERSION,
        "policyId": POLICY_ID,
        "profile": PROFILE_ID,
        "rules": normalized_rules,
    }


def _validate_allowlist_artifacts(
    allowlist: dict[str, Any], expected_artifacts: set[str]
) -> None:
    referenced = {rule["artifact"] for rule in allowlist["rules"]}
    unknown = sorted(referenced - expected_artifacts)
    if unknown:
        raise ValueError(
            f"Allowlist references artifacts absent from the manifest: {unknown}"
        )


def allowed(finding: Finding, rules: list[dict[str, Any]]) -> bool:
    member = (
        "<class>"
        if not finding.member_name
        else f"{finding.member_kind} {finding.member_name}"
    )
    for rule in rules:
        if (
            rule.get("artifact") != finding.artifact
            or rule.get("category") != finding.status
            or rule.get("class") != finding.class_name
        ):
            continue
        if rule.get("member") in {"<class>", member}:
            return True
    return False


def validate_reports(
    root: Path,
    allowlist_path: Path,
    manifest_path: Path | None = None,
) -> tuple[list[Finding], list[str]]:
    manifest = load_manifest(manifest_path or default_manifest_path())
    allowlist = load_allowlist(allowlist_path)
    canonical_manifest_entries(manifest)
    expected = {entry.artifact: entry for entry in manifest.artifacts}
    _validate_allowlist_artifacts(allowlist, set(expected))
    reactor_coordinates = {
        (entry.artifact, entry.module) for entry in manifest.artifacts
    }

    for entry in sorted(manifest.artifacts, key=lambda item: item.artifact):
        pom_path = root.joinpath(*entry.module.parts, "pom.xml")
        actual_artifact = direct_artifact_id(pom_path)
        if actual_artifact != entry.artifact:
            raise ValueError(
                f"Manifest artifact {entry.artifact} does not match module POM "
                f"artifactId {actual_artifact}: {pom_path}"
            )
        candidate_pom = root.joinpath(*entry.candidate_module.parts, "pom.xml")
        actual_candidate = direct_artifact_id(candidate_pom)
        if actual_candidate != entry.candidate_artifact:
            raise ValueError(
                f"Manifest candidate {entry.candidate_artifact} does not match "
                f"module POM artifactId {actual_candidate}: {candidate_pom}"
            )
        if (
            entry.candidate_artifact,
            entry.candidate_module,
        ) not in reactor_coordinates:
            raise ValueError(
                f"Manifest replacement for {entry.artifact} is not a declared "
                f"non-POM reactor artifact: {entry.candidate_artifact} at "
                f"{entry.candidate_module}"
            )
        if (entry.candidate_artifact, entry.candidate_module) != (
            entry.artifact,
            entry.module,
        ):
            expected_property = (
                "${maven.multiModuleProjectDirectory}/"
                f"{entry.candidate_module.as_posix()}/target/"
                f"{entry.candidate_artifact}-${{project.version}}.jar"
            )
            actual_property = direct_property(
                pom_path, "coco.api.compatibility.candidate-jar"
            )
            if actual_property != expected_property:
                raise ValueError(
                    f"Compatibility facade {entry.artifact} candidate path is "
                    f"{actual_property!r}; expected {expected_property!r}: {pom_path}"
                )

    validate_manifest_against_reactor(root, manifest)

    reports = sorted(
        glob_files(root, f"**/{REPORT_RELATIVE_PATH.as_posix()}", "Repository")
    )
    if not reports:
        raise ValueError(f"No japicmp reports found below {root}")

    report_data: dict[str, tuple[Path, ReportData]] = {}
    for report in reports:
        data = report_findings(report)
        if data.artifact not in expected:
            raise ValueError(
                f"Unexpected japicmp report artifact {data.artifact}: {report}"
            )
        if data.artifact in report_data:
            first_report = report_data[data.artifact][0]
            raise ValueError(
                f"Duplicate japicmp reports for artifact {data.artifact}: "
                f"{first_report}, {report}"
            )
        report_data[data.artifact] = (report, data)

    for artifact, (report, data) in sorted(report_data.items()):
        expected_report = root.joinpath(
            *expected[artifact].module.parts, REPORT_RELATIVE_PATH
        )
        if report != expected_report:
            raise ValueError(
                f"Japicmp report for {artifact} is at {report}; "
                f"expected {expected_report}"
            )
        validate_candidate_binding(root, expected[artifact], report, data)

    missing_reports = sorted(set(expected) - set(report_data))
    if missing_reports:
        raise ValueError(f"Missing expected japicmp reports: {missing_reports}")

    candidate_versions = {data.new_version for _, data in report_data.values()}
    if len(candidate_versions) != 1:
        raise ValueError(
            f"Japicmp reports do not share one candidate version: "
            f"{sorted(str(value) for value in candidate_versions)}"
        )

    findings: list[Finding] = []
    actual_missing: set[str] = set()
    for artifact in sorted(report_data):
        report, data = report_data[artifact]
        old_version = data.old_version
        if old_version is None or old_version == "":
            raise ValueError(f"Missing oldVersion in japicmp report: {report}")
        if old_version == "n.a.":
            actual_missing.add(artifact)
            continue
        findings.extend(
            item for item in data.findings if not allowed(item, allowlist["rules"])
        )
    return findings, sorted(actual_missing)


def trusted_input_paths(
    root: Path,
    manifest: Manifest,
    manifest_path: Path,
    allowlist_path: Path,
    ledger_path: Path,
    signing_key_path: Path,
) -> tuple[Path, ...]:
    tool_directory = logical_absolute(Path(__file__)).parent
    paths = {
        root / "pom.xml",
        manifest_path,
        allowlist_path,
        ledger_path,
        signing_key_path,
        manifest_path.with_name("japicmp-policy.json"),
        tool_directory / "check_public_api_compatibility.py",
        tool_directory / "path_io.py",
        tool_directory / "policy_bundle.py",
        tool_directory / "run_public_api_compatibility.py",
    }
    paths.update(
        root.joinpath(*entry.module.parts, "pom.xml") for entry in manifest.artifacts
    )
    for path in paths:
        regular_file_bytes(path, "Trusted input")
    return tuple(sorted((logical_absolute(path) for path in paths), key=str))


def input_file_records(root: Path, paths: tuple[Path, ...]) -> list[dict[str, str]]:
    records: list[dict[str, str]] = []
    resolved_root = logical_absolute(root)
    directory_metadata(resolved_root, "Repository root")
    for path in paths:
        resolved = logical_absolute(path)
        regular_file_bytes(resolved, "Trusted input")
        try:
            relative = resolved.relative_to(resolved_root).as_posix()
        except ValueError as exc:
            raise ValueError(
                f"Trusted input escapes repository root: {resolved}"
            ) from exc
        records.append(
            {
                "path": relative,
                "sha256": sha256_file(resolved, f"Trusted input {relative}"),
            }
        )
    return sorted(records, key=lambda item: item["path"])


def input_records_sha256(records: list[dict[str, str]]) -> str:
    canonical = json.dumps(
        records, ensure_ascii=True, separators=(",", ":"), sort_keys=True
    ).encode("ascii")
    return sha256_bytes(canonical)


def _attested_path(value: Any, label: str) -> Path:
    text = _nonempty_string(value, label)
    path = Path(text)
    if not path.is_absolute():
        raise ValueError(f"{label} must be absolute: {text!r}")
    return path


def _integer(value: Any, label: str, *, positive: bool = False) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError(f"{label} must be an integer.")
    if positive and value <= 0:
        raise ValueError(f"{label} must be positive.")
    return value


def _attested_report_path(root: Path, entry: ExpectedArtifact, run_id: str) -> Path:
    return root.joinpath(
        *entry.module.parts,
        ATTESTED_REPORT_PREFIX,
        run_id,
        "japicmp",
        "public-api-compatibility.xml",
    )


def _expected_candidate_path(
    root: Path, entry: ExpectedArtifact, expected_version: str
) -> Path:
    return root.joinpath(
        *entry.candidate_module.parts,
        "target",
        f"{entry.candidate_artifact}-{expected_version}.jar",
    )


def _validate_manifest_bindings(root: Path, manifest: Manifest) -> None:
    reactor_coordinates = {
        (entry.artifact, entry.module) for entry in manifest.artifacts
    }
    canonical_manifest_entries(manifest)
    for entry in sorted(manifest.artifacts, key=lambda item: item.artifact):
        pom_path = root.joinpath(*entry.module.parts, "pom.xml")
        actual_artifact = direct_artifact_id(pom_path)
        if actual_artifact != entry.artifact:
            raise ValueError(
                f"Manifest artifact {entry.artifact} does not match module POM "
                f"artifactId {actual_artifact}: {pom_path}"
            )
        candidate_pom = root.joinpath(*entry.candidate_module.parts, "pom.xml")
        actual_candidate = direct_artifact_id(candidate_pom)
        if actual_candidate != entry.candidate_artifact:
            raise ValueError(
                f"Manifest candidate {entry.candidate_artifact} does not match "
                f"module POM artifactId {actual_candidate}: {candidate_pom}"
            )
        if (
            entry.candidate_artifact,
            entry.candidate_module,
        ) not in reactor_coordinates:
            raise ValueError(
                f"Manifest replacement for {entry.artifact} is not a declared "
                f"non-POM reactor artifact: {entry.candidate_artifact} at "
                f"{entry.candidate_module}"
            )
        if (entry.candidate_artifact, entry.candidate_module) != (
            entry.artifact,
            entry.module,
        ):
            expected_property = (
                "${maven.multiModuleProjectDirectory}/"
                f"{entry.candidate_module.as_posix()}/target/"
                f"{entry.candidate_artifact}-${{project.version}}.jar"
            )
            actual_property = direct_property(
                pom_path, "coco.api.compatibility.candidate-jar"
            )
            if actual_property != expected_property:
                raise ValueError(
                    f"Compatibility facade {entry.artifact} candidate path is "
                    f"{actual_property!r}; expected {expected_property!r}: {pom_path}"
                )
    validate_manifest_against_reactor(root, manifest)


def validate_attested_reports(
    root: Path,
    allowlist_path: Path,
    manifest_path: Path,
    ledger_path: Path,
    signing_key_path: Path,
    attestation_path: Path,
    expected_candidate_version: str,
    expected_protected_sha: str | None = None,
) -> tuple[list[Finding], list[str]]:
    if (
        CANDIDATE_VERSION_PATTERN.fullmatch(expected_candidate_version) is None
        or expected_candidate_version == "n.a."
    ):
        raise ValueError(
            f"Invalid expected candidate version: {expected_candidate_version!r}"
        )
    resolved_root = logical_absolute(root)
    directory_metadata(resolved_root, "Repository root")
    manifest = load_manifest(manifest_path)
    allowlist = load_allowlist(allowlist_path)
    ledger = load_baseline_ledger(ledger_path)
    validate_policy_assets(manifest_path, signing_key_path)
    expected = {entry.artifact: entry for entry in manifest.artifacts}
    _validate_allowlist_artifacts(allowlist, set(expected))
    _validate_manifest_bindings(resolved_root, manifest)

    available_baselines = {entry.artifact: entry for entry in ledger.artifacts}
    missing_baselines = set(ledger.missing_artifacts)
    profile_available = {
        entry.artifact
        for entry in manifest.artifacts
        if entry.baseline_state == "present"
    }
    profile_missing = {
        entry.artifact
        for entry in manifest.artifacts
        if entry.baseline_state == "missing"
    }
    if (
        set(available_baselines) != profile_available
        or missing_baselines != profile_missing
    ):
        raise ValueError(
            "Baseline ledger present/missing sets do not match profile baselineState."
        )
    if set(available_baselines) | missing_baselines != set(expected):
        missing = sorted(set(expected) - set(available_baselines) - missing_baselines)
        extra = sorted((set(available_baselines) | missing_baselines) - set(expected))
        raise ValueError(
            "Baseline ledger does not exactly match the manifest: "
            f"missing={missing}, extra={extra}"
        )
    current_policy_bundle_sha256 = policy_bundle_sha256(
        manifest_path, ledger_path, allowlist_path, signing_key_path
    )

    attestation = _json_object(attestation_path, "Attestation")
    _exact_keys(
        attestation,
        {
            "schemaVersion",
            "runId",
            "repositoryRoot",
            "workspace",
            "protectedSha",
            "expectedCandidateVersion",
            "policyBundleSha256",
            "startedAtNs",
            "finishedAtNs",
            "inputs",
            "baseline",
            "candidates",
            "reports",
        },
        "Attestation",
    )
    if attestation["schemaVersion"] != ATTESTATION_SCHEMA_VERSION:
        raise ValueError(
            f"Attestation schemaVersion must be {ATTESTATION_SCHEMA_VERSION}."
        )
    run_id = _nonempty_string(attestation["runId"], "Attestation runId")
    if RUN_ID_PATTERN.fullmatch(run_id) is None:
        raise ValueError(f"Attestation runId is invalid: {run_id!r}")
    attested_root = _attested_path(
        attestation["repositoryRoot"], "Attestation repositoryRoot"
    )
    if not _same_path(attested_root, resolved_root):
        raise ValueError(
            f"Attestation repositoryRoot is {attested_root}; expected {resolved_root}"
        )
    workspace = _attested_path(attestation["workspace"], "Attestation workspace")
    directory_metadata(workspace, "Attestation workspace")
    protected_sha = _nonempty_string(
        attestation["protectedSha"], "Attestation protectedSha"
    )
    if PROTECTED_SHA_PATTERN.fullmatch(protected_sha) is None:
        raise ValueError("Attestation protectedSha must be a lowercase 40-hex SHA.")
    if expected_protected_sha is not None and protected_sha != expected_protected_sha:
        raise ValueError(
            f"Attestation protectedSha is {protected_sha}; expected {expected_protected_sha}."
        )
    if attestation["expectedCandidateVersion"] != expected_candidate_version:
        raise ValueError(
            "Attestation candidate version does not match the explicit expected "
            f"version {expected_candidate_version!r}."
        )
    if attestation["policyBundleSha256"] != current_policy_bundle_sha256:
        raise ValueError(
            "Attestation policy bundle SHA-256 does not match the protected checkout."
        )
    started_at_ns = _integer(
        attestation["startedAtNs"], "Attestation startedAtNs", positive=True
    )
    finished_at_ns = _integer(
        attestation["finishedAtNs"], "Attestation finishedAtNs", positive=True
    )
    if finished_at_ns < started_at_ns:
        raise ValueError("Attestation finishedAtNs precedes startedAtNs.")
    if finished_at_ns > time.time_ns():
        raise ValueError("Attestation finishedAtNs is in the future.")

    inputs = attestation["inputs"]
    if not isinstance(inputs, dict):
        raise ValueError("Attestation inputs must be an object.")
    _exact_keys(inputs, {"sha256", "files"}, "Attestation inputs")
    input_digest = _sha256(inputs["sha256"], "Attestation inputs SHA-256")
    if not isinstance(inputs["files"], list):
        raise ValueError("Attestation input files must be an array.")
    expected_input_records = input_file_records(
        resolved_root,
        trusted_input_paths(
            resolved_root,
            manifest,
            manifest_path,
            allowlist_path,
            ledger_path,
            signing_key_path,
        ),
    )
    if inputs["files"] != expected_input_records:
        raise ValueError("Attested input files or digests do not match current inputs.")
    if input_digest != input_records_sha256(expected_input_records):
        raise ValueError("Attestation aggregate input SHA-256 is invalid.")

    baseline = attestation["baseline"]
    if not isinstance(baseline, dict):
        raise ValueError("Attestation baseline must be an object.")
    _exact_keys(
        baseline,
        {
            "ledgerSha256",
            "origin",
            "version",
            "signingFingerprint",
            "verifiedMissingArtifacts",
            "artifacts",
        },
        "Attestation baseline",
    )
    if baseline["ledgerSha256"] != sha256_file(ledger_path, "Baseline ledger"):
        raise ValueError("Attestation baseline ledger SHA-256 is invalid.")
    if baseline["origin"] != BASELINE_ORIGIN or baseline["version"] != BASELINE_VERSION:
        raise ValueError("Attestation baseline origin or version is invalid.")
    if baseline["signingFingerprint"] != BASELINE_SIGNING_FINGERPRINT:
        raise ValueError("Attestation baseline signing fingerprint is invalid.")
    if baseline["verifiedMissingArtifacts"] != list(ledger.missing_artifacts):
        raise ValueError("Attestation Central 404 artifact set is invalid.")
    if not isinstance(baseline["artifacts"], list):
        raise ValueError("Attestation baseline artifacts must be an array.")

    baseline_paths: dict[str, Path] = {}
    seen_baselines: set[str] = set()
    for record in baseline["artifacts"]:
        if not isinstance(record, dict):
            raise ValueError("Attestation baseline artifact must be an object.")
        _exact_keys(
            record,
            {
                "artifact",
                "pomPath",
                "pomSha256",
                "pomSignaturePath",
                "pomSignatureSha256",
                "jarPath",
                "jarSha256",
                "jarSignaturePath",
                "jarSignatureSha256",
                "signingFingerprint",
            },
            "Attestation baseline artifact",
        )
        artifact = _artifact_id(record["artifact"], "Attested baseline artifact")
        entry = available_baselines.get(artifact)
        if entry is None or artifact in seen_baselines:
            raise ValueError(f"Unexpected or duplicate attested baseline: {artifact}")
        seen_baselines.add(artifact)
        expected_prefix = workspace / "baseline" / f"{artifact}-{BASELINE_VERSION}"
        paths = {
            "pom": _attested_path(record["pomPath"], f"{artifact} POM path"),
            "pom.asc": _attested_path(
                record["pomSignaturePath"], f"{artifact} POM signature path"
            ),
            "jar": _attested_path(record["jarPath"], f"{artifact} JAR path"),
            "jar.asc": _attested_path(
                record["jarSignaturePath"], f"{artifact} JAR signature path"
            ),
        }
        for suffix, path in paths.items():
            expected_path = Path(f"{expected_prefix}.{suffix}")
            if not _same_path(path, expected_path):
                raise ValueError(
                    f"Attested {artifact} {suffix} path is {path}; expected {expected_path}"
                )
            _path_below(path, workspace / "baseline", f"{artifact} {suffix}")
        if (
            record["pomSha256"] != entry.pom_sha256
            or record["jarSha256"] != entry.jar_sha256
        ):
            raise ValueError(f"Attested fixed baseline digest mismatch for {artifact}")
        if record["signingFingerprint"] != BASELINE_SIGNING_FINGERPRINT:
            raise ValueError(f"Attested signing fingerprint mismatch for {artifact}")
        if sha256_file(paths["pom.asc"], f"{artifact} POM signature") != _sha256(
            record["pomSignatureSha256"], f"{artifact} POM signature SHA-256"
        ):
            raise ValueError(f"Attested POM signature digest mismatch for {artifact}")
        if sha256_file(paths["jar.asc"], f"{artifact} JAR signature") != _sha256(
            record["jarSignatureSha256"], f"{artifact} JAR signature SHA-256"
        ):
            raise ValueError(f"Attested JAR signature digest mismatch for {artifact}")
        validate_baseline_files(entry, paths["pom"], paths["jar"])
        baseline_paths[artifact] = paths["jar"]
    if seen_baselines != set(available_baselines):
        raise ValueError(
            "Attestation is missing baseline artifacts: "
            f"{sorted(set(available_baselines) - seen_baselines)}"
        )

    canonical = canonical_manifest_entries(manifest)
    canonical_coordinates = {
        (entry.artifact, entry.module): entry for entry in canonical
    }
    if not isinstance(attestation["candidates"], list):
        raise ValueError("Attestation candidates must be an array.")
    candidate_records: dict[tuple[str, PurePosixPath], tuple[Path, str]] = {}
    for record in attestation["candidates"]:
        if not isinstance(record, dict):
            raise ValueError("Attestation candidate must be an object.")
        _exact_keys(
            record,
            {"artifact", "module", "path", "version", "sha256Before", "sha256After"},
            "Attestation candidate",
        )
        coordinate = (
            _artifact_id(record["artifact"], "Attested candidate artifact"),
            _module_path(record["module"], "Attested candidate module"),
        )
        entry = canonical_coordinates.get(coordinate)
        if entry is None or coordinate in candidate_records:
            raise ValueError(
                f"Unexpected or duplicate attested candidate: {coordinate}"
            )
        if record["version"] != expected_candidate_version:
            raise ValueError(f"Attested candidate version mismatch for {coordinate[0]}")
        path = _attested_path(record["path"], f"{coordinate[0]} candidate path")
        expected_path = _expected_candidate_path(
            resolved_root, entry, expected_candidate_version
        )
        if not _same_path(path, expected_path):
            raise ValueError(
                f"Attested candidate path is {path}; expected {expected_path}"
            )
        before = _sha256(record["sha256Before"], f"{coordinate[0]} before SHA-256")
        after = _sha256(record["sha256After"], f"{coordinate[0]} after SHA-256")
        current = sha256_file(path, f"{coordinate[0]} candidate JAR")
        if before != after or after != current:
            raise ValueError(
                f"Candidate JAR changed before, during, or after comparison: {path}"
            )
        candidate_records[coordinate] = (path, current)
    if set(candidate_records) != set(canonical_coordinates):
        raise ValueError(
            "Attestation candidate set does not equal self-candidate canonical entries."
        )

    if not isinstance(attestation["reports"], list):
        raise ValueError("Attestation reports must be an array.")
    report_data: dict[str, ReportData] = {}
    expected_report_paths: set[Path] = set()
    for record in attestation["reports"]:
        if not isinstance(record, dict):
            raise ValueError("Attestation report must be an object.")
        _exact_keys(
            record,
            {"artifact", "module", "path", "sha256", "size", "mtimeNs"},
            "Attestation report",
        )
        artifact = _artifact_id(record["artifact"], "Attested report artifact")
        entry = expected.get(artifact)
        if entry is None or artifact in report_data:
            raise ValueError(f"Unexpected or duplicate attested report: {artifact}")
        module = _module_path(record["module"], f"{artifact} report module")
        if module != entry.module:
            raise ValueError(
                f"Attested report module mismatch for {artifact}: {module}"
            )
        path = _attested_path(record["path"], f"{artifact} report path")
        expected_path = _attested_report_path(resolved_root, entry, run_id)
        if not _same_path(path, expected_path):
            raise ValueError(
                f"Attested report path is {path}; expected {expected_path}"
            )
        report_snapshot = file_snapshot(path, f"{artifact} japicmp report")
        report_bytes = report_snapshot.contents
        report_stat = report_snapshot.metadata
        size = _integer(record["size"], f"{artifact} report size", positive=True)
        mtime_ns = _integer(
            record["mtimeNs"], f"{artifact} report mtimeNs", positive=True
        )
        if size != len(report_bytes) or size != report_stat.st_size:
            raise ValueError(f"Attested report size mismatch for {artifact}: {path}")
        if mtime_ns != report_stat.st_mtime_ns:
            raise ValueError(f"Attested report mtime mismatch for {artifact}: {path}")
        if mtime_ns < started_at_ns or mtime_ns > finished_at_ns:
            raise ValueError(
                f"Japicmp report for {artifact} is outside the trusted run window: {path}"
            )
        if mtime_ns > time.time_ns():
            raise ValueError(f"Japicmp report for {artifact} is future-dated: {path}")
        if sha256_bytes(report_bytes) != _sha256(
            record["sha256"], f"{artifact} report SHA-256"
        ):
            raise ValueError(f"Attested report digest mismatch for {artifact}: {path}")
        data = report_findings(path, artifact=artifact, report_bytes=report_bytes)
        if data.new_version != expected_candidate_version:
            raise ValueError(
                f"Japicmp report for {artifact} self-reports candidate version "
                f"{data.new_version!r}; expected {expected_candidate_version!r}."
            )
        candidate_coordinate = (entry.candidate_artifact, entry.candidate_module)
        candidate_path = candidate_records[candidate_coordinate][0]
        if (
            data.new_jar is None
            or not Path(data.new_jar).is_absolute()
            or not _same_path(Path(data.new_jar), candidate_path)
        ):
            raise ValueError(
                f"Japicmp report for {artifact} is not bound to canonical candidate "
                f"{candidate_path}: {path}"
            )
        if artifact in available_baselines:
            if (
                data.old_version != BASELINE_VERSION
                or data.old_jar is None
                or not _same_path(Path(data.old_jar), baseline_paths[artifact])
            ):
                raise ValueError(
                    f"Japicmp report for {artifact} is not bound to the verified "
                    f"Central baseline: {path}"
                )
        elif data.old_version != "n.a." or data.old_jar not in (None, "n.a."):
            raise ValueError(
                f"Japicmp report for current-only artifact {artifact} must be n.a.: {path}"
            )
        report_data[artifact] = data
        expected_report_paths.add(logical_absolute(expected_path))
    if set(report_data) != set(expected):
        raise ValueError(
            f"Missing attested japicmp reports: {sorted(set(expected) - set(report_data))}"
        )

    actual_report_paths = {
        logical_absolute(path)
        for path in glob_files(
            resolved_root,
            f"**/{ATTESTED_REPORT_PREFIX.as_posix()}/{run_id}/japicmp/"
            "public-api-compatibility.xml",
            "Repository",
        )
    }
    if actual_report_paths != expected_report_paths:
        raise ValueError(
            "Fresh japicmp report set does not exactly equal attestation: "
            f"missing={sorted(str(path) for path in expected_report_paths - actual_report_paths)}, "
            f"extra={sorted(str(path) for path in actual_report_paths - expected_report_paths)}"
        )
    stale_reports = [
        resolved_root.joinpath(*entry.module.parts, REPORT_RELATIVE_PATH)
        for entry in manifest.artifacts
        if entry_exists(
            resolved_root.joinpath(*entry.module.parts, REPORT_RELATIVE_PATH)
        )
    ]
    if stale_reports:
        raise ValueError(
            f"Legacy fixed-path japicmp reports were not cleaned: {stale_reports}"
        )

    findings: list[Finding] = []
    actual_missing = {
        artifact for artifact, data in report_data.items() if data.old_version == "n.a."
    }
    if actual_missing != missing_baselines:
        raise ValueError(
            "Japicmp n.a. set does not exactly equal the Central 404 set: "
            f"reports={sorted(actual_missing)}, ledger={sorted(missing_baselines)}"
        )
    for artifact, data in sorted(report_data.items()):
        if artifact not in actual_missing:
            findings.extend(
                item for item in data.findings if not allowed(item, allowlist["rules"])
            )

    for coordinate, (path, expected_digest) in candidate_records.items():
        if sha256_file(path, f"{coordinate[0]} final candidate JAR") != expected_digest:
            raise ValueError(f"Candidate JAR changed during checker execution: {path}")
    return findings, []


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=repository_root())
    parser.add_argument(
        "--allowlist", type=Path, default=Path(__file__).with_name("allowlist.json")
    )
    parser.add_argument("--manifest", type=Path, default=default_manifest_path())
    parser.add_argument(
        "--baseline-ledger", type=Path, default=default_baseline_ledger_path()
    )
    parser.add_argument(
        "--signing-key",
        type=Path,
        default=Path(__file__).with_name("baseline-signing-key.asc"),
    )
    parser.add_argument("--attestation", type=Path, required=True)
    parser.add_argument("--expected-candidate-version", required=True)
    parser.add_argument(
        "--protected-sha", default=os.environ.get("GITHUB_SHA"), required=False
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        findings, missing_baseline = validate_attested_reports(
            args.root,
            args.allowlist,
            args.manifest,
            args.baseline_ledger,
            args.signing_key,
            args.attestation,
            args.expected_candidate_version,
            args.protected_sha,
        )
    except (OSError, ValueError, ElementTree.ParseError) as exc:
        print(f"PUBLIC API COMPATIBILITY ERROR: {exc}", file=sys.stderr)
        return 2
    if missing_baseline:
        print("Unexpected missing 2.0.1 baseline artifacts:", file=sys.stderr)
        for artifact in missing_baseline:
            print(f"  - {artifact}", file=sys.stderr)
    if findings:
        print("Unallowlisted public API/ABI incompatibilities:", file=sys.stderr)
        for finding in findings:
            print(f"  - {finding.display()}", file=sys.stderr)
    if missing_baseline or findings:
        return 1
    print(
        "Public API compatibility gate passed: all public non-internal reports are compatible."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
