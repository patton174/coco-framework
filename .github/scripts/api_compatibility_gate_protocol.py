#!/usr/bin/env python3
"""Fail-closed protocol for the dormant trusted API compatibility shadow route."""

from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import importlib.util
import json
import os
import re
import stat
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from io import BytesIO
from pathlib import Path, PurePosixPath
from typing import Any
from xml.etree import ElementTree


SOURCE_WORKFLOW_NAME = "CI"
SOURCE_WORKFLOW_PATH = ".github/workflows/ci.yml"
SOURCE_PRODUCER_JOB = (
    "API compatibility candidate (shadow) / Produce exact candidate JARs"
)
STATUS_CONTEXT = "API compatibility trusted shadow"
ARTIFACT_PREFIX = "api-compatibility-candidate"
MANIFEST_NAME = "manifest.json"
JAR_DIRECTORY = "jars"
CANDIDATE_VERSION = "2.0.2-SNAPSHOT"
MAIN_BRANCH = "main"
ALLOWED_SOURCE_EVENTS = frozenset({"pull_request", "merge_group"})
SHA_RE = re.compile(r"[0-9a-f]{40}")
SHA256_RE = re.compile(r"[0-9a-f]{64}")
REPOSITORY_RE = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
JAR_NAME_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]*\.jar")
MERGE_GROUP_BRANCH_RE = re.compile(r"gh-readonly-queue/main/.+")
POLICY_ROOT = Path("coco-support/coco-tools/public-api-compatibility")
BASELINE_SIGNING_KEY_FILE = "baseline-signing-key.asc"
POLICY_ID = "coco-public-api-compatibility"
PROFILE_ID = "public-api-compatibility"
CANDIDATE_VERSION_SOURCE = "mavenProperty:revision"
PROFILE_SCHEMA_VERSION = 3
BASELINE_ORIGIN = "https://repo.maven.apache.org/maven2"
BASELINE_VERSION = "2.0.1"
JAPICMP_URL = (
    "https://repo.maven.apache.org/maven2/com/github/siom79/japicmp/japicmp/0.23.1/"
    "japicmp-0.23.1-jar-with-dependencies.jar"
)
JAPICMP_SIZE = 5988558
JAPICMP_SHA256 = "f2300dd9b8aca31c49a95dfad5a6794b4475f4e83809ad69f8f1e11d87014657"
JAPICMP_MAVEN_PLUGIN_URL = (
    "https://repo.maven.apache.org/maven2/com/github/siom79/japicmp/"
    "japicmp-maven-plugin/0.23.1/japicmp-maven-plugin-0.23.1.jar"
)
JAPICMP_MAVEN_PLUGIN_SIZE = 44670
JAPICMP_MAVEN_PLUGIN_SHA256 = (
    "7df259e8be0c652259ef96416fcc6f2e7ef5e5a340a4df52783350abcd77c4bb"
)
PASS = "PASS0000"
FAIL_PRODUCER = "FAIL0001"
FAIL_ARTIFACT = "FAIL0002"
FAIL_POLICY = "FAIL0003"
FAIL_BREAKING = "FAIL0004"
FAIL_INTERNAL = "FAIL0005"
VERDICTS = frozenset(
    {PASS, FAIL_PRODUCER, FAIL_ARTIFACT, FAIL_POLICY, FAIL_BREAKING, FAIL_INTERNAL}
)
MAX_ARCHIVE_BYTES = 256 * 1024 * 1024
MAX_ENTRY_BYTES = 64 * 1024 * 1024
MAX_TOTAL_BYTES = 512 * 1024 * 1024
MAX_COMPRESSION_RATIO = 100
MAX_JAR_ENTRIES = 2048
MAX_JAR_ENTRY_BYTES = 8 * 1024 * 1024
MAX_JAR_TOTAL_BYTES = 32 * 1024 * 1024
MAX_JAR_COMPRESSION_RATIO = 100
MAX_JAPICMP_XML_BYTES = 16 * 1024 * 1024
MAX_MODULE_POM_BYTES = 256 * 1024
JAPICMP_TIMEOUT_SECONDS = 60
CLASS_NAME_RE = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*")
FINDING_MEMBER_RE = re.compile(
    r"(?:<class>|<init>\([A-Za-z0-9_$.,\[\]]*\)|"
    r"[A-Za-z_$][A-Za-z0-9_$]*(?:\([A-Za-z0-9_$.,\[\]]*\))?)"
)
COORDINATE_RE = re.compile(
    r"([A-Za-z_][A-Za-z0-9_-]*(?:\.[A-Za-z_][A-Za-z0-9_-]*)*):"
    r"([A-Za-z0-9][A-Za-z0-9_.-]*)"
)
MAVEN_VERSION_RE = re.compile(r"[0-9][A-Za-z0-9._-]*")


class ProtocolError(RuntimeError):
    """Raised when an untrusted or protected protocol invariant is violated."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ProtocolError(message)


_SHARED_POLICY_BUNDLES: dict[Path, Any] = {}


def shared_policy_bundle_module(protected_root: Path) -> Any:
    source_path = protected_root / POLICY_ROOT / "policy_bundle.py"
    require(
        source_path.is_file() and not source_path.is_symlink(),
        "shared protected policy parser is missing",
    )
    path = source_path.resolve(strict=True)
    if path in _SHARED_POLICY_BUNDLES:
        return _SHARED_POLICY_BUNDLES[path]
    spec = importlib.util.spec_from_file_location("coco_public_api_policy_bundle", path)
    require(
        spec is not None and spec.loader is not None,
        "shared protected policy parser cannot be loaded",
    )
    module = importlib.util.module_from_spec(spec)
    policy_directory = str(path.parent)
    sys.path.insert(0, policy_directory)
    try:
        spec.loader.exec_module(module)
    except Exception as exc:
        raise ProtocolError("shared protected policy parser failed to load") from exc
    finally:
        if sys.path[0] == policy_directory:
            sys.path.pop(0)
    _SHARED_POLICY_BUNDLES[path] = module
    return module


def is_int(value: object) -> bool:
    return type(value) is int


def valid_sha(value: object, message: str) -> str:
    require(isinstance(value, str) and SHA_RE.fullmatch(value) is not None, message)
    return value


def valid_sha256(value: object, message: str) -> str:
    require(isinstance(value, str) and SHA256_RE.fullmatch(value) is not None, message)
    return value


def valid_positive_int(value: object, message: str) -> int:
    require(is_int(value) and value > 0, message)
    return value


def canonical_json(value: object) -> bytes:
    return json.dumps(
        value, ensure_ascii=True, sort_keys=True, separators=(",", ":")
    ).encode("utf-8")


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def strict_json_loads(value: bytes) -> Any:
    def pairs(items: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, item in items:
            if key in result:
                raise ProtocolError("duplicate JSON key")
            result[key] = item
        return result

    try:
        return json.loads(value, object_pairs_hook=pairs)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise ProtocolError("invalid JSON") from exc


def exact_keys(value: object, keys: set[str], message: str) -> dict[str, Any]:
    require(isinstance(value, dict) and set(value) == keys, message)
    return value


def local_head(root: Path) -> str:
    try:
        result = subprocess.run(
            ["git", "-C", str(root), "rev-parse", "HEAD"],
            check=True,
            capture_output=True,
            text=True,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        raise ProtocolError("cannot resolve checkout HEAD") from exc
    return valid_sha(result.stdout.strip(), "checkout HEAD is invalid")


def assert_clean_checkout(root: Path, expected_sha: str) -> None:
    require(
        local_head(root) == valid_sha(expected_sha, "expected SHA is invalid"),
        "checkout HEAD drift",
    )
    try:
        status = subprocess.run(
            [
                "git",
                "-C",
                str(root),
                "status",
                "--porcelain=v1",
                "--untracked-files=all",
            ],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
    except (OSError, subprocess.SubprocessError) as exc:
        raise ProtocolError("cannot inspect checkout cleanliness") from exc
    require(
        status == "", "checkout has tracked, indexed, or nonignored untracked changes"
    )


def safe_path(name: str) -> str:
    require(name and "\\" not in name and "//" not in name, "unsafe archive path")
    path = PurePosixPath(name)
    require(
        not path.is_absolute()
        and all(part not in ("", ".", "..") for part in path.parts),
        "unsafe archive path",
    )
    return name


def parse_maven_properties(data: bytes) -> dict[str, str]:
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise ProtocolError("Maven pom.properties is not UTF-8") from exc
    values: dict[str, str] = {}
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        require("=" in line and "\\" not in line, "Maven pom.properties is invalid")
        key, value = line.split("=", 1)
        require(
            key in {"artifactId", "groupId", "version"}
            and key not in values
            and value == value.strip()
            and value != "",
            "Maven pom.properties is invalid",
        )
        values[key] = value
    require(
        set(values) == {"artifactId", "groupId", "version"},
        "Maven pom.properties fields are invalid",
    )
    coordinate_parts(f"{values['groupId']}:{values['artifactId']}")
    require(
        MAVEN_VERSION_RE.fullmatch(values["version"]) is not None,
        "Maven version is invalid",
    )
    return values


def parse_maven_pom_xml(data: bytes) -> dict[str, str]:
    upper = data.upper()
    require(
        b"<!DOCTYPE" not in upper and b"<!ENTITY" not in upper, "unsafe Maven pom.xml"
    )
    try:
        root = ElementTree.fromstring(data)
    except ElementTree.ParseError as exc:
        raise ProtocolError("Maven pom.xml is invalid") from exc
    require(root.tag.rsplit("}", 1)[-1] == "project", "Maven pom.xml root is invalid")

    def direct(parent: ElementTree.Element, name: str) -> str | None:
        matches = [child for child in parent if child.tag.rsplit("}", 1)[-1] == name]
        require(len(matches) <= 1, f"Maven pom.xml has duplicate direct {name}")
        if not matches:
            return None
        value = matches[0].text
        require(
            isinstance(value, str) and value == value.strip() and value != "",
            f"Maven pom.xml direct {name} is invalid",
        )
        return value

    artifact_id = direct(root, "artifactId")
    require(artifact_id is not None, "Maven pom.xml direct artifactId is missing")
    parents = [child for child in root if child.tag.rsplit("}", 1)[-1] == "parent"]
    require(len(parents) <= 1, "Maven pom.xml has duplicate parent")

    def direct_or_parent(name: str) -> str:
        value = direct(root, name)
        if value is not None:
            return value
        require(len(parents) == 1, f"Maven pom.xml {name} fallback is missing")
        parent_value = direct(parents[0], name)
        require(parent_value is not None, f"Maven pom.xml parent {name} is missing")
        return parent_value

    values = {
        "artifactId": artifact_id,
        "groupId": direct_or_parent("groupId"),
        "version": direct_or_parent("version"),
    }
    coordinate_parts(f"{values['groupId']}:{values['artifactId']}")
    require(
        MAVEN_VERSION_RE.fullmatch(values["version"]) is not None,
        "Maven pom.xml version is invalid",
    )
    return values


def parse_candidate_module_pom(data: bytes) -> dict[str, str]:
    require(
        0 < len(data) <= MAX_MODULE_POM_BYTES,
        "candidate module POM size is invalid",
    )
    upper = data.upper()
    require(
        b"<!DOCTYPE" not in upper and b"<!ENTITY" not in upper,
        "unsafe candidate module POM",
    )
    try:
        root = ElementTree.fromstring(data)
    except ElementTree.ParseError as exc:
        raise ProtocolError("candidate module POM is invalid") from exc
    require(
        root.tag.rsplit("}", 1)[-1] == "project",
        "candidate module POM root is invalid",
    )

    def direct(parent: ElementTree.Element, name: str) -> str | None:
        matches = [child for child in parent if child.tag.rsplit("}", 1)[-1] == name]
        require(len(matches) <= 1, f"candidate module POM duplicate {name}")
        if not matches:
            return None
        value = matches[0].text
        require(
            isinstance(value, str) and value == value.strip() and value != "",
            f"candidate module POM {name} is invalid",
        )
        return value

    artifact_id = direct(root, "artifactId")
    require(artifact_id is not None, "candidate module POM artifactId is missing")
    parents = [child for child in root if child.tag.rsplit("}", 1)[-1] == "parent"]
    require(len(parents) <= 1, "candidate module POM parent is duplicated")

    def direct_or_parent(name: str) -> str:
        value = direct(root, name)
        if value is not None:
            return value
        require(len(parents) == 1, f"candidate module POM {name} is missing")
        parent_value = direct(parents[0], name)
        require(
            parent_value is not None,
            f"candidate module POM parent {name} is missing",
        )
        return parent_value

    group_id = direct_or_parent("groupId")
    raw_version = direct_or_parent("version")
    coordinate_parts(f"{group_id}:{artifact_id}")
    require(
        raw_version in {CANDIDATE_VERSION, "${revision}"},
        "candidate module POM version is not the fixed revision",
    )
    return {
        "artifactId": artifact_id,
        "groupId": group_id,
        "version": CANDIDATE_VERSION,
    }


def candidate_jars(root: Path) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    resolved_root = root.resolve(strict=True)
    for candidate in root.rglob("*.jar"):
        if candidate.parent.name != "target" or candidate.name.startswith(
            ("original-", "sources-", "javadoc-")
        ):
            continue
        require(
            candidate.is_file() and not candidate.is_symlink(),
            "candidate JAR is not regular",
        )
        require(
            candidate.resolve(strict=True).is_relative_to(resolved_root),
            "candidate JAR escapes checkout",
        )
        sidecar = candidate.parent / "maven-archiver" / "pom.properties"
        require(
            sidecar.is_file() and not sidecar.is_symlink(),
            "candidate Maven metadata sidecar is missing",
        )
        metadata = parse_maven_properties(sidecar.read_bytes())
        require(
            metadata["version"] == CANDIDATE_VERSION,
            "candidate Maven version is not the fixed workflow version",
        )
        require(
            candidate.name == f"{metadata['artifactId']}-{metadata['version']}.jar",
            "candidate source JAR filename/GAV mismatch",
        )
        data = candidate.read_bytes()
        require(
            validate_inner_jar(candidate.name, data) == metadata,
            "candidate JAR embedded Maven metadata mismatch",
        )
        source_path = candidate.relative_to(root).as_posix()
        safe_path(source_path)
        result.append(
            {
                "data": data,
                "metadata": metadata,
                "normalized_name": f"{metadata['artifactId']}.jar",
                "source_path": source_path,
            }
        )
    result.sort(key=lambda item: item["normalized_name"])
    require(len(result) == 32, "candidate must produce exactly 32 JARs")
    names = [item["normalized_name"] for item in result]
    require(len(set(names)) == 32, "candidate JAR names are duplicated")
    require(
        len({name.casefold() for name in names}) == 32,
        "candidate JAR names case-collide",
    )
    require(
        all(JAR_NAME_RE.fullmatch(name) is not None for name in names),
        "candidate JAR name is invalid",
    )
    coordinates = [
        f"{item['metadata']['groupId']}:{item['metadata']['artifactId']}"
        for item in result
    ]
    require(len(set(coordinates)) == 32, "candidate Maven coordinates are duplicated")
    return result


def stage_candidate(
    candidate_root: Path,
    output_root: Path,
    candidate_sha: str,
    source_event: str,
    source_run_id: int,
    source_run_attempt: int,
) -> None:
    valid_sha(candidate_sha, "candidate SHA is invalid")
    require(source_event in ALLOWED_SOURCE_EVENTS, "candidate source event is invalid")
    valid_positive_int(source_run_id, "candidate run ID is invalid")
    valid_positive_int(source_run_attempt, "candidate run attempt is invalid")
    assert_clean_checkout(candidate_root, candidate_sha)
    require(not output_root.exists(), "candidate staging root already exists")
    jars = candidate_jars(candidate_root)
    (output_root / JAR_DIRECTORY).mkdir(parents=True)
    entries: list[dict[str, Any]] = []
    for jar in jars:
        data = jar["data"]
        require(0 < len(data) <= MAX_ENTRY_BYTES, "candidate JAR size is invalid")
        target = output_root / JAR_DIRECTORY / jar["normalized_name"]
        target.write_bytes(data)
        metadata = jar["metadata"]
        entries.append(
            {
                "artifact_id": metadata["artifactId"],
                "group_id": metadata["groupId"],
                "normalized_name": jar["normalized_name"],
                "sha256": sha256_bytes(data),
                "size": len(data),
                "source_path": jar["source_path"],
                "version": metadata["version"],
            }
        )
    manifest = {
        "candidate_sha": candidate_sha,
        "candidate_version": CANDIDATE_VERSION,
        "jars": entries,
        "kind": "non-authoritative-candidate-jars",
        "schema_version": 3,
        "source_event": source_event,
        "source_run_attempt": source_run_attempt,
        "source_run_id": source_run_id,
    }
    (output_root / MANIFEST_NAME).write_bytes(canonical_json(manifest) + b"\n")
    assert_clean_checkout(candidate_root, candidate_sha)


def read_safe_zip_entries(archive: zipfile.ZipFile) -> dict[str, bytes]:
    result: dict[str, bytes] = {}
    folded: set[str] = set()
    total = 0
    infos = archive.infolist()
    require(0 < len(infos) <= 33, "artifact entry count is invalid")
    for info in infos:
        name = safe_path(info.filename)
        require(not info.is_dir(), "artifact directories are not permitted")
        require(
            name not in result and name.casefold() not in folded,
            "duplicate or case-colliding artifact entry",
        )
        folded.add(name.casefold())
        mode = info.external_attr >> 16
        require(not stat.S_ISLNK(mode), "artifact symlink is forbidden")
        require(not (info.flag_bits & 1), "encrypted artifact entry is forbidden")
        require(
            0 <= info.file_size <= MAX_ENTRY_BYTES,
            "artifact entry is oversized",
        )
        require(
            info.compress_size >= 0,
            "artifact entry compression metadata is invalid",
        )
        if info.file_size:
            require(
                info.compress_size > 0
                and info.file_size / info.compress_size <= MAX_COMPRESSION_RATIO,
                "artifact compression ratio is unsafe",
            )
        total += info.file_size
        require(total <= MAX_TOTAL_BYTES, "artifact total size is oversized")
        data = archive.read(info)
        require(len(data) == info.file_size, "artifact entry size drift")
        result[name] = data
    return result


def read_safe_zip(archive_bytes: bytes) -> dict[str, bytes]:
    require(len(archive_bytes) <= MAX_ARCHIVE_BYTES, "artifact archive is oversized")
    try:
        with zipfile.ZipFile(BytesIO(archive_bytes), "r") as archive:
            return read_safe_zip_entries(archive)
    except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
        raise ProtocolError("invalid artifact ZIP") from exc


def read_safe_zip_path(path: Path) -> dict[str, bytes]:
    require(
        path.is_file()
        and not path.is_symlink()
        and path.stat().st_size <= MAX_ARCHIVE_BYTES,
        "artifact archive file is invalid",
    )
    try:
        with zipfile.ZipFile(path, "r") as archive:
            return read_safe_zip_entries(archive)
    except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
        raise ProtocolError("invalid artifact ZIP") from exc


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while chunk := source.read(64 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def validate_inner_jar(name: str, data: bytes) -> dict[str, str]:
    require(0 < len(data) <= MAX_ENTRY_BYTES, f"inner JAR size is invalid: {name}")
    seen: set[str] = set()
    folded: set[str] = set()
    total = 0
    descriptors: dict[str, list[tuple[str, bytes]]] = {
        "pom.properties": [],
        "pom.xml": [],
    }
    try:
        with zipfile.ZipFile(BytesIO(data), "r") as archive:
            infos = archive.infolist()
            require(
                0 < len(infos) <= MAX_JAR_ENTRIES,
                f"inner JAR entry count is invalid: {name}",
            )
            for info in infos:
                entry_name = safe_path(info.filename)
                require(
                    entry_name not in seen and entry_name.casefold() not in folded,
                    f"inner JAR has duplicate or case-colliding entries: {name}",
                )
                seen.add(entry_name)
                folded.add(entry_name.casefold())
                mode = info.external_attr >> 16
                file_type = stat.S_IFMT(mode)
                require(
                    file_type in {0, stat.S_IFREG, stat.S_IFDIR}
                    and not stat.S_ISLNK(mode),
                    f"inner JAR special entry is forbidden: {name}",
                )
                require(
                    not (info.flag_bits & 1),
                    f"inner JAR encrypted entry is forbidden: {name}",
                )
                if info.is_dir():
                    require(
                        info.file_size == 0,
                        f"inner JAR directory size is invalid: {name}",
                    )
                    continue
                require(
                    0 <= info.file_size <= MAX_JAR_ENTRY_BYTES,
                    f"inner JAR entry is oversized: {name}",
                )
                require(
                    info.compress_size >= 0,
                    f"inner JAR compression metadata is invalid: {name}",
                )
                if info.file_size:
                    require(
                        info.compress_size > 0
                        and info.file_size / info.compress_size
                        <= MAX_JAR_COMPRESSION_RATIO,
                        f"inner JAR compression ratio is unsafe: {name}",
                    )
                total += info.file_size
                require(
                    total <= MAX_JAR_TOTAL_BYTES,
                    f"inner JAR total size is oversized: {name}",
                )
                entry_data = archive.read(info)
                require(
                    len(entry_data) == info.file_size,
                    f"inner JAR entry size drift: {name}",
                )
                parts = PurePosixPath(entry_name).parts
                if (
                    len(parts) >= 3
                    and parts[0] == "META-INF"
                    and parts[1] == "maven"
                    and parts[-1] in descriptors
                ):
                    descriptors[parts[-1]].append((entry_name, entry_data))
    except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
        raise ProtocolError(f"candidate JAR is invalid: {name}") from exc
    require(
        len(descriptors["pom.properties"]) == 1 and len(descriptors["pom.xml"]) == 1,
        f"inner JAR must contain exactly one Maven pom.properties and pom.xml: {name}",
    )
    properties_path, properties_data = descriptors["pom.properties"][0]
    pom_path, pom_data = descriptors["pom.xml"][0]
    require(
        PurePosixPath(properties_path).parent == PurePosixPath(pom_path).parent,
        f"inner JAR Maven descriptors are not in the same directory: {name}",
    )
    properties_metadata = parse_maven_properties(properties_data)
    xml_metadata = parse_maven_pom_xml(pom_data)
    require(
        properties_metadata == xml_metadata,
        f"inner JAR Maven XML/properties metadata mismatch: {name}",
    )
    expected_directory = (
        f"META-INF/maven/{properties_metadata['groupId']}/"
        f"{properties_metadata['artifactId']}"
    )
    require(
        properties_path == f"{expected_directory}/pom.properties"
        and pom_path == f"{expected_directory}/pom.xml",
        f"inner JAR Maven metadata path/GAV mismatch: {name}",
    )
    return properties_metadata


def coordinate_parts(value: object) -> tuple[str, str]:
    require(isinstance(value, str), "profile coordinate is invalid")
    match = COORDINATE_RE.fullmatch(value)
    require(match is not None, "profile coordinate is invalid")
    return match.group(1), match.group(2)


def validate_maven_central_url(url: object, coordinate: str) -> str:
    require(isinstance(url, str), "baseline ledger URL is invalid")
    group, artifact = coordinate_parts(coordinate)
    parsed = urllib.parse.urlsplit(url)
    try:
        port = parsed.port
    except ValueError as exc:
        raise ProtocolError("baseline ledger URL is invalid") from exc
    require(
        parsed.scheme == "https"
        and parsed.netloc == "repo.maven.apache.org"
        and parsed.query == ""
        and parsed.fragment == ""
        and parsed.username is None
        and parsed.password is None
        and port is None,
        "baseline ledger URL must use canonical Maven Central",
    )
    prefix = f"/maven2/{group.replace('.', '/')}/{artifact}/"
    require(parsed.path.startswith(prefix), "baseline ledger URL path is invalid")
    remainder = parsed.path.removeprefix(prefix)
    parts = remainder.split("/")
    require(len(parts) == 2, "baseline ledger URL path is invalid")
    version, filename = parts
    require(
        MAVEN_VERSION_RE.fullmatch(version) is not None
        and filename == f"{artifact}-{version}.jar"
        and urllib.parse.unquote(parsed.path) == parsed.path,
        "baseline ledger URL path is invalid",
    )
    return url


def policy_artifact_id(value: object, message: str) -> str:
    require(
        isinstance(value, str) and JAR_NAME_RE.fullmatch(f"{value}.jar") is not None,
        message,
    )
    return value


def policy_string(value: object, message: str) -> str:
    require(isinstance(value, str) and value != "" and value == value.strip(), message)
    return value


def validate_policy_bundle(
    bundle: dict[str, Any], bundle_sha256: str
) -> dict[str, Any]:
    value = exact_keys(
        bundle,
        {
            "allowlist",
            "baselineLedger",
            "japicmpPolicy",
            "profile",
            "schemaVersion",
            "signingKeySha256",
        },
        "policy bundle schema is invalid",
    )
    require(value["schemaVersion"] == 3, "policy bundle version is invalid")
    profile = exact_keys(
        value["profile"],
        {
            "artifacts",
            "candidateVersionSource",
            "policyId",
            "profile",
            "schemaVersion",
        },
        "profile schema is invalid",
    )
    require(
        profile["schemaVersion"] == 3
        and profile["policyId"] == POLICY_ID
        and profile["profile"] == PROFILE_ID
        and profile["candidateVersionSource"] == CANDIDATE_VERSION_SOURCE
        and isinstance(profile["artifacts"], list)
        and len(profile["artifacts"]) == 32,
        "profile metadata is invalid",
    )
    artifacts: list[dict[str, Any]] = []
    artifact_ids: list[str] = []
    module_paths: list[str] = []
    for entry in profile["artifacts"]:
        item = exact_keys(
            entry,
            {
                "artifactId",
                "baselineState",
                "comparison",
                "groupId",
                "jarName",
                "modulePath",
            },
            "profile artifact is invalid",
        )
        artifact_id = policy_artifact_id(
            item["artifactId"], "profile artifactId is invalid"
        )
        group_id, coordinate_artifact = coordinate_parts(
            f"{item['groupId']}:{artifact_id}"
        )
        require(
            group_id == item["groupId"]
            and coordinate_artifact == artifact_id
            and item["jarName"] == f"{artifact_id}.jar"
            and item["baselineState"] in {"present", "missing"},
            "profile artifact identity is invalid",
        )
        require(isinstance(item["modulePath"], str), "profile modulePath is invalid")
        module_path = safe_path(item["modulePath"])
        require(
            "target" not in PurePosixPath(module_path).parts
            and all(
                re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", part) is not None
                for part in PurePosixPath(module_path).parts
            ),
            "profile modulePath is invalid",
        )
        comparison = exact_keys(
            item["comparison"],
            {"targetArtifactId"},
            "profile comparison is invalid",
        )
        policy_artifact_id(
            comparison["targetArtifactId"], "profile comparison target is invalid"
        )
        artifacts.append(item)
        artifact_ids.append(artifact_id)
        module_paths.append(module_path)
    require(
        artifact_ids == sorted(artifact_ids)
        and len(set(artifact_ids)) == 32
        and len({item.casefold() for item in artifact_ids}) == 32,
        "profile artifact inventory is not canonical",
    )
    require(
        len(set(module_paths)) == 32
        and len({item.casefold() for item in module_paths}) == 32,
        "profile modulePath inventory is not unique",
    )
    artifacts_by_id = {item["artifactId"]: item for item in artifacts}
    present_ids = {
        item["artifactId"] for item in artifacts if item["baselineState"] == "present"
    }
    require(
        len(present_ids) == 20 and len(artifacts) - len(present_ids) == 12,
        "profile baseline state counts are invalid",
    )
    replacements = 0
    for item in artifacts:
        target_id = item["comparison"]["targetArtifactId"]
        require(target_id in artifacts_by_id, "profile comparison target is missing")
        target = artifacts_by_id[target_id]
        require(
            target["comparison"]["targetArtifactId"] == target_id,
            "profile comparison chains and cycles are forbidden",
        )
        if item["baselineState"] == "missing":
            require(
                target_id == item["artifactId"],
                "missing-baseline comparison must target itself",
            )
        if target_id != item["artifactId"]:
            replacements += 1
    require(replacements == 10, "profile replacement count is invalid")

    ledger = exact_keys(
        value["baselineLedger"],
        {
            "artifacts",
            "groupId",
            "origin",
            "policyId",
            "profile",
            "schemaVersion",
            "signingFingerprint",
            "signingKeySha256",
            "version",
        },
        "baseline ledger schema is invalid",
    )
    require(
        ledger["schemaVersion"] == 3
        and ledger["policyId"] == POLICY_ID
        and ledger["profile"] == PROFILE_ID
        and ledger["origin"] == BASELINE_ORIGIN
        and ledger["version"] == BASELINE_VERSION
        and isinstance(ledger["groupId"], str)
        and all(item["groupId"] == ledger["groupId"] for item in artifacts)
        and isinstance(ledger["signingFingerprint"], str)
        and re.fullmatch(r"[0-9A-F]{40}", ledger["signingFingerprint"]) is not None
        and ledger["signingKeySha256"]
        == valid_sha256(value["signingKeySha256"], "policy signing key SHA is invalid")
        and isinstance(ledger["artifacts"], list)
        and len(ledger["artifacts"]) == 32,
        "baseline ledger metadata is invalid",
    )
    baselines: dict[str, dict[str, Any]] = {}
    ledger_ids: list[str] = []
    for entry in ledger["artifacts"]:
        require(isinstance(entry, dict), "baseline ledger artifact is invalid")
        state = entry.get("baselineState")
        if state == "present":
            item = exact_keys(
                entry,
                {
                    "artifactId",
                    "baselineState",
                    "jarSha256",
                    "jarSize",
                    "pomSha256",
                    "pomSize",
                },
                "present baseline ledger artifact is invalid",
            )
            artifact_id = policy_artifact_id(
                item["artifactId"], "baseline ledger artifactId is invalid"
            )
            require(
                is_int(item["jarSize"])
                and 0 < item["jarSize"] <= MAX_ENTRY_BYTES
                and is_int(item["pomSize"])
                and 0 < item["pomSize"] <= MAX_ENTRY_BYTES,
                "baseline ledger size is invalid",
            )
            jar_sha256 = valid_sha256(item["jarSha256"], "baseline JAR SHA is invalid")
            valid_sha256(item["pomSha256"], "baseline POM SHA is invalid")
            url = (
                f"{ledger['origin']}/{ledger['groupId'].replace('.', '/')}/"
                f"{artifact_id}/{ledger['version']}/"
                f"{artifact_id}-{ledger['version']}.jar"
            )
            validate_maven_central_url(url, f"{ledger['groupId']}:{artifact_id}")
            baselines[artifact_id] = {
                "jarSha256": jar_sha256,
                "jarSize": item["jarSize"],
                "pomSha256": item["pomSha256"],
                "pomSize": item["pomSize"],
                "url": url,
            }
        elif state == "missing":
            item = exact_keys(
                entry,
                {"artifactId", "baselineState", "jarStatus", "pomStatus"},
                "missing baseline ledger artifact is invalid",
            )
            artifact_id = policy_artifact_id(
                item["artifactId"], "baseline ledger artifactId is invalid"
            )
            require(
                item["jarStatus"] == 404 and item["pomStatus"] == 404,
                "missing baseline status is invalid",
            )
        else:
            raise ProtocolError("baseline ledger state is invalid")
        require(
            artifact_id in artifacts_by_id
            and artifacts_by_id[artifact_id]["baselineState"] == state,
            "baseline ledger/profile state mismatch",
        )
        ledger_ids.append(artifact_id)
    require(
        ledger_ids == sorted(ledger_ids)
        and len(set(ledger_ids)) == 32
        and set(ledger_ids) == set(artifact_ids)
        and set(baselines) == present_ids,
        "baseline ledger coverage is invalid",
    )

    japicmp_policy = exact_keys(
        value["japicmpPolicy"],
        {
            "allowedCategories",
            "cli",
            "findingKey",
            "mavenPlugin",
            "policyId",
            "profile",
            "schemaVersion",
        },
        "japicmp policy schema is invalid",
    )
    require(
        japicmp_policy["schemaVersion"] == 3
        and japicmp_policy["policyId"] == POLICY_ID
        and japicmp_policy["profile"] == PROFILE_ID
        and japicmp_policy["findingKey"] == ["artifact", "class", "member", "category"]
        and japicmp_policy["allowedCategories"] == ["REMOVED"],
        "japicmp finding policy is invalid",
    )
    tool_keys = {"artifactId", "groupId", "sha256", "size", "url", "version"}
    maven_plugin = exact_keys(
        japicmp_policy["mavenPlugin"], tool_keys, "japicmp Maven plugin lock is invalid"
    )
    cli = exact_keys(japicmp_policy["cli"], tool_keys, "japicmp CLI lock is invalid")
    require(
        maven_plugin
        == {
            "groupId": "com.github.siom79.japicmp",
            "artifactId": "japicmp-maven-plugin",
            "version": "0.23.1",
            "url": JAPICMP_MAVEN_PLUGIN_URL,
            "size": JAPICMP_MAVEN_PLUGIN_SIZE,
            "sha256": JAPICMP_MAVEN_PLUGIN_SHA256,
        }
        and cli
        == {
            "groupId": "com.github.siom79.japicmp",
            "artifactId": "japicmp",
            "version": "0.23.1",
            "url": JAPICMP_URL,
            "size": JAPICMP_SIZE,
            "sha256": JAPICMP_SHA256,
        },
        "japicmp tool locks are invalid",
    )

    allowlist = exact_keys(
        value["allowlist"],
        {"policyId", "profile", "rules", "schemaVersion"},
        "allowlist schema is invalid",
    )
    require(
        allowlist["schemaVersion"] == 3
        and allowlist["policyId"] == POLICY_ID
        and allowlist["profile"] == PROFILE_ID
        and isinstance(allowlist["rules"], list),
        "allowlist metadata is invalid",
    )
    identities: list[tuple[str, str, str, str]] = []
    exceptions: list[dict[str, str]] = []
    for entry in allowlist["rules"]:
        item = exact_keys(
            entry,
            {"artifact", "category", "class", "member", "reason"},
            "allowlist rule is invalid",
        )
        artifact_id = policy_artifact_id(
            item["artifact"], "allowlist artifact is invalid"
        )
        class_name = policy_string(item["class"], "allowlist class is invalid")
        member = policy_string(item["member"], "allowlist member is invalid")
        category = policy_string(item["category"], "allowlist category is invalid")
        reason = policy_string(item["reason"], "allowlist reason is invalid")
        require(
            artifact_id in present_ids
            and CLASS_NAME_RE.fullmatch(class_name) is not None
            and FINDING_MEMBER_RE.fullmatch(member) is not None
            and category in japicmp_policy["allowedCategories"]
            and category != "MODIFIED"
            and not any(
                "*" in part for part in (artifact_id, class_name, member, category)
            ),
            "allowlist rule is not an exact finding exception",
        )
        identity = (artifact_id, class_name, member, category)
        require(identity not in identities, "allowlist rule is duplicated")
        identities.append(identity)
        exceptions.append(
            {
                "artifact": artifact_id,
                "class": class_name,
                "member": member,
                "category": category,
                "reason": reason,
            }
        )
    require(identities == sorted(identities), "allowlist rules are not sorted")
    return {
        "artifacts": artifacts,
        "artifactsById": artifacts_by_id,
        "baselines": baselines,
        "exceptions": tuple(exceptions),
        "japicmpPolicy": japicmp_policy,
        "policyBundle": value,
        "policyBundleSha256": valid_sha256(
            bundle_sha256, "policy bundle SHA is invalid"
        ),
    }


def load_policy(root: Path) -> dict[str, Any]:
    directory = root / POLICY_ROOT
    profile = directory / "public-api-profile.json"
    ledger = directory / "baseline-sha256.json"
    allowlist = directory / "allowlist.json"
    signing_key = directory / BASELINE_SIGNING_KEY_FILE
    japicmp_policy = directory / "japicmp-policy.json"
    try:
        shared = shared_policy_bundle_module(root)
        bundle = shared.normalized_policy_bundle(
            profile, ledger, allowlist, signing_key, japicmp_policy
        )
        bundle_sha256 = shared.policy_bundle_sha256(
            profile, ledger, allowlist, signing_key, japicmp_policy
        )
        return validate_policy_bundle(bundle, bundle_sha256)
    except ProtocolError:
        raise
    except Exception as exc:
        raise ProtocolError("protected policy bundle is invalid") from exc


def validate_candidate_artifact(
    archive: bytes, digest: str, binding: dict[str, Any], policy: dict[str, Any]
) -> dict[str, bytes]:
    require(
        sha256_bytes(archive) == valid_sha256(digest, "artifact digest is invalid"),
        "artifact digest mismatch",
    )
    files = read_safe_zip(archive)
    return validate_candidate_files(files, binding, policy)


def validate_candidate_artifact_path(
    archive: Path, digest: str, binding: dict[str, Any], policy: dict[str, Any]
) -> dict[str, bytes]:
    require(
        sha256_file(archive) == valid_sha256(digest, "artifact digest is invalid"),
        "artifact digest mismatch",
    )
    return validate_candidate_files(read_safe_zip_path(archive), binding, policy)


def validate_candidate_files(
    files: dict[str, bytes], binding: dict[str, Any], policy: dict[str, Any]
) -> dict[str, bytes]:
    manifest_bytes = files.get(MANIFEST_NAME)
    require(manifest_bytes is not None, "candidate manifest is missing")
    manifest = exact_keys(
        strict_json_loads(manifest_bytes),
        {
            "candidate_sha",
            "candidate_version",
            "jars",
            "kind",
            "schema_version",
            "source_event",
            "source_run_attempt",
            "source_run_id",
        },
        "candidate manifest is invalid",
    )
    require(
        manifest["schema_version"] == 3
        and manifest["kind"] == "non-authoritative-candidate-jars",
        "candidate manifest is invalid",
    )
    require(
        manifest["candidate_version"] == CANDIDATE_VERSION,
        "candidate manifest version is invalid",
    )
    for key in ("candidate_sha", "source_event", "source_run_id", "source_run_attempt"):
        require(manifest[key] == binding[key], "candidate manifest binding drift")
    require(
        isinstance(manifest["jars"], list) and len(manifest["jars"]) == 32,
        "candidate manifest JAR list is invalid",
    )
    expected_artifacts = {item["jarName"]: item for item in policy["artifacts"]}
    expected = list(expected_artifacts)
    expected_paths = {MANIFEST_NAME, *(f"{JAR_DIRECTORY}/{name}" for name in expected)}
    require(set(files) == expected_paths, "candidate artifact inventory is invalid")
    claims: dict[str, dict[str, Any]] = {}
    for claim in manifest["jars"]:
        item = exact_keys(
            claim,
            {
                "artifact_id",
                "group_id",
                "normalized_name",
                "sha256",
                "size",
                "source_path",
                "version",
            },
            "candidate manifest JAR claim is invalid",
        )
        normalized_name = item["normalized_name"]
        require(
            isinstance(normalized_name, str)
            and normalized_name in expected_artifacts
            and normalized_name not in claims,
            "candidate manifest JAR name is invalid",
        )
        profile = expected_artifacts[normalized_name]
        group_id = profile["groupId"]
        artifact_id = profile["artifactId"]
        require(
            item["group_id"] == group_id
            and item["artifact_id"] == artifact_id
            and normalized_name == f"{artifact_id}.jar",
            "candidate manifest Maven coordinate mismatch",
        )
        require(
            item["version"] == CANDIDATE_VERSION,
            "candidate manifest Maven version mismatch",
        )
        require(
            isinstance(item["source_path"], str),
            "candidate manifest source path is invalid",
        )
        source_path = safe_path(item["source_path"])
        require(
            source_path
            == (
                f"{profile['modulePath']}/target/{artifact_id}-{CANDIDATE_VERSION}.jar"
            ),
            "candidate manifest source path/GAV mismatch",
        )
        valid_sha256(item["sha256"], "candidate manifest JAR SHA is invalid")
        require(
            is_int(item["size"]) and 0 < item["size"] <= MAX_ENTRY_BYTES,
            "candidate manifest JAR size is invalid",
        )
        claims[normalized_name] = item
    require(set(claims) == set(expected), "candidate manifest JAR claims are invalid")
    require(
        len({item["source_path"] for item in claims.values()}) == 32,
        "candidate manifest source paths are duplicated",
    )
    jars: dict[str, bytes] = {}
    for name in expected:
        data = files[f"{JAR_DIRECTORY}/{name}"]
        require(
            len(data) == claims[name]["size"]
            and sha256_bytes(data) == claims[name]["sha256"],
            "candidate JAR claim mismatch",
        )
        embedded = validate_inner_jar(name, data)
        claim = claims[name]
        require(
            embedded
            == {
                "artifactId": claim["artifact_id"],
                "groupId": claim["group_id"],
                "version": claim["version"],
            },
            "candidate JAR embedded Maven coordinate mismatch",
        )
        jars[name] = data
    return jars


def stream_response_to_file(
    response: Any,
    destination: Path,
    expected_size: int,
    expected_digest: str,
    max_bytes: int,
) -> None:
    require(not destination.exists(), "artifact download destination already exists")
    valid_sha256(expected_digest, "artifact download digest is invalid")
    require(
        is_int(expected_size) and 0 < expected_size <= max_bytes,
        "artifact download expected size is invalid",
    )
    try:
        content_lengths = response.headers.get_all("Content-Length")
        require(
            isinstance(content_lengths, list)
            and len(content_lengths) == 1
            and isinstance(content_lengths[0], str)
            and re.fullmatch(r"[1-9][0-9]*", content_lengths[0]) is not None,
            "artifact download Content-Length is invalid",
        )
        content_length = int(content_lengths[0])
        require(
            content_length == expected_size and content_length <= max_bytes,
            "artifact download Content-Length is invalid",
        )
        digest = hashlib.sha256()
        total = 0
        with destination.open("xb") as target:
            while True:
                read_size = min(64 * 1024, max_bytes + 1 - total)
                require(read_size > 0, "artifact download exceeds size limit")
                chunk = response.read(read_size)
                if not chunk:
                    break
                require(
                    isinstance(chunk, bytes) and len(chunk) <= read_size,
                    "artifact download returned an invalid chunk",
                )
                total += len(chunk)
                require(total <= max_bytes, "artifact download exceeds size limit")
                digest.update(chunk)
                target.write(chunk)
        require(total == expected_size, "artifact download size mismatch")
        require(
            digest.hexdigest() == expected_digest,
            "artifact download digest mismatch",
        )
    except Exception as exc:
        destination.unlink(missing_ok=True)
        if isinstance(exc, ProtocolError):
            raise
        raise ProtocolError("artifact download stream failed") from exc


class GitHubApi:
    def __init__(self, repository: str, token: str, api_url: str) -> None:
        require(
            REPOSITORY_RE.fullmatch(repository) is not None, "repository is invalid"
        )
        require(token != "", "GitHub token is required")
        self.repository = repository
        self.token = token
        self.api_url = api_url.rstrip("/")

    def request(self, method: str, path: str, body: object | None = None) -> bytes:
        request = urllib.request.Request(
            f"{self.api_url}/{path.lstrip('/')}",
            data=None if body is None else canonical_json(body),
            method=method,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self.token}",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read()
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
            raise ProtocolError("GitHub API request failed") from exc

    def get_json(self, path: str) -> Any:
        return strict_json_loads(self.request("GET", path))

    def download_artifact(
        self,
        path: str,
        destination: Path,
        expected_size: int,
        expected_digest: str,
    ) -> None:
        request = urllib.request.Request(
            f"{self.api_url}/{path.lstrip('/')}",
            headers={
                "Accept": "application/vnd.github+json",
                "Accept-Encoding": "identity",
                "Authorization": f"Bearer {self.token}",
                "Cache-Control": "no-cache, no-store",
                "Pragma": "no-cache",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                stream_response_to_file(
                    response,
                    destination,
                    expected_size,
                    expected_digest,
                    MAX_ARCHIVE_BYTES,
                )
        except ProtocolError:
            raise
        except (
            OSError,
            urllib.error.HTTPError,
            urllib.error.URLError,
            TimeoutError,
        ) as exc:
            destination.unlink(missing_ok=True)
            raise ProtocolError("GitHub artifact download failed") from exc

    def post_json(self, path: str, body: object) -> Any:
        return strict_json_loads(self.request("POST", path, body))


def paged(api: GitHubApi, path: str, key: str) -> list[dict[str, Any]]:
    value = api.get_json(f"{path}{'&' if '?' in path else '?'}per_page=100&page=1")
    require(
        isinstance(value, dict) and isinstance(value.get(key), list),
        "GitHub list response is invalid",
    )
    require(
        all(isinstance(item, dict) for item in value[key]),
        "GitHub list item is invalid",
    )
    return value[key]


def bind_source_run(
    api: GitHubApi,
    event: dict[str, Any],
    repository: str,
    repository_id: int,
    protected_sha: str,
    source_run_id: int,
) -> dict[str, Any]:
    valid_sha(protected_sha, "protected SHA is invalid")
    valid_positive_int(repository_id, "repository ID is invalid")
    snapshot = event.get("workflow_run")
    require(
        event.get("action") == "completed"
        and isinstance(snapshot, dict)
        and snapshot.get("id") == source_run_id,
        "workflow_run event is invalid",
    )
    run = api.get_json(f"repos/{repository}/actions/runs/{source_run_id}")
    require(isinstance(run, dict), "source run is invalid")
    for key in (
        "id",
        "run_attempt",
        "workflow_id",
        "name",
        "path",
        "event",
        "head_sha",
        "head_branch",
        "status",
        "conclusion",
    ):
        require(run.get(key) == snapshot.get(key), f"source run {key} drift")
    require(
        run.get("name") == SOURCE_WORKFLOW_NAME
        and run.get("path") == SOURCE_WORKFLOW_PATH
        and run.get("status") == "completed",
        "source workflow identity is invalid",
    )
    source_event = run.get("event")
    require(source_event in ALLOWED_SOURCE_EVENTS, "source event is invalid")
    candidate_sha = valid_sha(run.get("head_sha"), "candidate SHA is invalid")
    attempt = valid_positive_int(
        run.get("run_attempt"), "source run attempt is invalid"
    )
    workflow_id = valid_positive_int(
        run.get("workflow_id"), "source workflow ID is invalid"
    )
    repo = api.get_json(f"repos/{repository}")
    branch = api.get_json(f"repos/{repository}/branches/{MAIN_BRANCH}")
    require(
        isinstance(repo, dict)
        and repo.get("id") == repository_id
        and repo.get("default_branch") == MAIN_BRANCH,
        "repository binding is invalid",
    )
    require(
        isinstance(branch, dict)
        and branch.get("protected") is True
        and branch.get("commit", {}).get("sha") == protected_sha,
        "protected main head drift",
    )
    candidate_repository = repository
    pr_number = 0
    if source_event == "pull_request":
        prs = run.get("pull_requests")
        require(
            isinstance(prs, list) and len(prs) == 1 and is_int(prs[0].get("number")),
            "source PR binding is invalid",
        )
        pr_number = prs[0]["number"]
        pr = api.get_json(f"repos/{repository}/pulls/{pr_number}")
        require(
            isinstance(pr, dict)
            and pr.get("state") == "open"
            and pr.get("base", {}).get("ref") == MAIN_BRANCH,
            "PR is stale or invalid",
        )
        head = pr.get("head", {})
        require(
            head.get("sha") == candidate_sha
            and head.get("ref") == run.get("head_branch"),
            "PR head drift",
        )
        candidate_repository = head.get("repo", {}).get("full_name")
        require(
            isinstance(candidate_repository, str)
            and candidate_repository == run.get("head_repository", {}).get("full_name"),
            "fork repository drift",
        )
    else:
        require(
            isinstance(run.get("head_branch"), str)
            and MERGE_GROUP_BRANCH_RE.fullmatch(run["head_branch"]) is not None,
            "merge_group synthetic ref is invalid",
        )
        require(
            run.get("head_repository", {}).get("full_name") == repository,
            "merge_group repository drift",
        )
    runs = paged(
        api,
        f"repos/{repository}/actions/workflows/{workflow_id}/runs?event={source_event}&head_sha={candidate_sha}",
        "workflow_runs",
    )
    exact = [
        (item.get("id"), item.get("run_attempt"))
        for item in runs
        if item.get("event") == source_event and item.get("head_sha") == candidate_sha
    ]
    require(exact and max(exact) == (source_run_id, attempt), "source run is stale")
    jobs = paged(
        api,
        f"repos/{repository}/actions/runs/{source_run_id}/attempts/{attempt}/jobs",
        "jobs",
    )
    producers = [job for job in jobs if job.get("name") == SOURCE_PRODUCER_JOB]
    require(
        len(producers) == 1
        and producers[0].get("conclusion")
        in {"success", "failure", "cancelled", "skipped", "timed_out"},
        "producer job binding is invalid",
    )
    return {
        "candidate_sha": candidate_sha,
        "candidate_repository": candidate_repository,
        "source_event": source_event,
        "source_run_id": source_run_id,
        "source_run_attempt": attempt,
        "producer_outcome": producers[0]["conclusion"],
        "artifact_name": f"{ARTIFACT_PREFIX}-{candidate_sha}-{source_run_id}-{attempt}",
        "pr_number": pr_number,
    }


def artifact_metadata(api: GitHubApi, binding: dict[str, Any]) -> dict[str, Any]:
    artifacts = paged(
        api,
        f"repos/{api.repository}/actions/runs/{binding['source_run_id']}/artifacts",
        "artifacts",
    )
    matches = [
        item for item in artifacts if item.get("name") == binding["artifact_name"]
    ]
    require(len(matches) == 1, "candidate artifact is missing or duplicated")
    artifact = matches[0]
    require(
        artifact.get("expired") is False
        and artifact.get("workflow_run", {}).get("id") == binding["source_run_id"],
        "candidate artifact binding is invalid",
    )
    valid_positive_int(artifact.get("id"), "candidate artifact ID is invalid")
    require(
        is_int(artifact.get("size_in_bytes"))
        and 0 < artifact["size_in_bytes"] <= MAX_ARCHIVE_BYTES,
        "candidate artifact size is invalid",
    )
    valid_sha256(
        str(artifact.get("digest", "")).removeprefix("sha256:"),
        "candidate artifact digest is invalid",
    )
    return artifact


def github_file_bytes(api: GitHubApi, repository: str, sha: str, path: str) -> bytes:
    safe_path(path)
    quoted = urllib.parse.quote(path, safe="/")
    payload = api.get_json(f"repos/{repository}/contents/{quoted}?ref={sha}")
    require(isinstance(payload, dict), "candidate module POM response is invalid")
    require(
        payload.get("type") == "file"
        and payload.get("path") == path
        and payload.get("encoding") == "base64"
        and is_int(payload.get("size"))
        and 0 < payload["size"] <= MAX_MODULE_POM_BYTES
        and isinstance(payload.get("content"), str),
        "candidate module POM response is invalid",
    )
    encoded = payload["content"]
    compact = encoded.replace("\n", "")
    require(
        "\r" not in compact and compact.strip() == compact,
        "candidate module POM base64 is invalid",
    )
    try:
        data = base64.b64decode(compact, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ProtocolError("candidate module POM base64 is invalid") from exc
    require(len(data) == payload["size"], "candidate module POM size mismatch")
    return data


def validate_candidate_module_poms(
    api: GitHubApi, binding: dict[str, Any], policy: dict[str, Any]
) -> None:
    repository = binding["candidate_repository"]
    sha = binding["candidate_sha"]
    for artifact in policy["artifacts"]:
        module_pom = f"{artifact['modulePath']}/pom.xml"
        metadata = parse_candidate_module_pom(
            github_file_bytes(api, repository, sha, module_pom)
        )
        require(
            metadata
            == {
                "artifactId": artifact["artifactId"],
                "groupId": artifact["groupId"],
                "version": CANDIDATE_VERSION,
            },
            "candidate module POM/profile identity mismatch",
        )


class NoRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(
        self,
        request: urllib.request.Request,
        file_pointer: Any,
        code: int,
        message: str,
        headers: Any,
        new_url: str,
    ) -> None:
        return None


def download(url: str, destination: Path, digest: str, size: int | None = None) -> None:
    require(
        url.startswith("https://repo.maven.apache.org/maven2/")
        and "?" not in url
        and "#" not in url,
        "protected download URL is not canonical Maven Central",
    )
    request = urllib.request.Request(
        url,
        headers={
            "Accept-Encoding": "identity",
            "Cache-Control": "no-cache, no-store",
            "Pragma": "no-cache",
            "User-Agent": "coco-api-compatibility-shadow/1",
        },
    )
    opener = urllib.request.build_opener(
        urllib.request.ProxyHandler({}), NoRedirectHandler()
    )
    try:
        with opener.open(request, timeout=60) as response:
            require(response.geturl() == url, "protected download redirected")
            require(response.status == 200, "protected download status is invalid")
            content_lengths = response.headers.get_all("Content-Length")
            require(
                isinstance(content_lengths, list)
                and len(content_lengths) == 1
                and isinstance(content_lengths[0], str)
                and re.fullmatch(r"[1-9][0-9]*", content_lengths[0]) is not None,
                "protected download Content-Length is invalid",
            )
            response_size = int(content_lengths[0])
            require(
                response_size <= MAX_ENTRY_BYTES
                and (size is None or response_size == size),
                "protected download length header mismatch",
            )
            stream_response_to_file(
                response,
                destination,
                response_size,
                digest,
                size if size is not None else MAX_ENTRY_BYTES,
            )
    except Exception as exc:
        destination.unlink(missing_ok=True)
        if isinstance(exc, ProtocolError):
            raise
        raise ProtocolError("protected download failed") from exc


def xml_tag(element: ElementTree.Element) -> str:
    return element.tag.rsplit("}", 1)[-1]


def behavior_member(element: ElementTree.Element, kind: str) -> str:
    parameters: list[str] = []
    for candidate in element.iter():
        if xml_tag(candidate) == "parameter":
            parameter_type = candidate.get("type")
            require(
                isinstance(parameter_type, str)
                and CLASS_NAME_RE.fullmatch(parameter_type.removesuffix("[]"))
                is not None,
                "japicmp parameter type is invalid",
            )
            parameters.append(parameter_type)
    if kind == "constructor":
        return f"<init>({','.join(parameters)})"
    name = element.get("name")
    require(
        isinstance(name, str) and re.fullmatch(r"[A-Za-z_$][A-Za-z0-9_$]*", name),
        "japicmp member name is invalid",
    )
    return f"{name}({','.join(parameters)})" if kind == "method" else name


def parse_japicmp_findings(
    xml_path: Path, artifact: str
) -> frozenset[tuple[str, str, str, str]]:
    require(
        xml_path.is_file() and not xml_path.is_symlink(),
        "japicmp XML output is missing",
    )
    data = xml_path.read_bytes()
    require(
        0 < len(data) <= MAX_JAPICMP_XML_BYTES,
        "japicmp XML output size is invalid",
    )
    upper = data.upper()
    require(
        b"<!DOCTYPE" not in upper and b"<!ENTITY" not in upper,
        "unsafe japicmp XML",
    )
    try:
        root = ElementTree.fromstring(data)
    except ElementTree.ParseError as exc:
        raise ProtocolError("japicmp XML output is invalid") from exc
    require(xml_tag(root) == "japicmp", "japicmp XML root is invalid")
    findings: set[tuple[str, str, str, str]] = set()

    def compatibility(element: ElementTree.Element) -> tuple[str, str, str]:
        status = element.get("changeStatus", "UNCHANGED")
        binary = element.get("binaryCompatible", "n.a.")
        source = element.get("sourceCompatible", "n.a.")
        require(
            status in {"UNCHANGED", "NEW", "MODIFIED", "REMOVED"}
            and binary in {"true", "false", "n.a."}
            and source in {"true", "false", "n.a."},
            "japicmp compatibility metadata is invalid",
        )
        return status, binary, source

    class_sections = [element for element in root if xml_tag(element) == "classes"]
    require(len(class_sections) == 1, "japicmp classes section is invalid")
    for class_element in class_sections[0]:
        require(xml_tag(class_element) == "class", "japicmp class entry is invalid")
        class_name = class_element.get("fullyQualifiedName") or class_element.get(
            "name"
        )
        require(
            isinstance(class_name, str)
            and CLASS_NAME_RE.fullmatch(class_name) is not None,
            "japicmp class name is invalid",
        )
        if any(
            marker in class_name for marker in (".internal.", "$internal", ".internal$")
        ):
            continue
        status, binary, source = compatibility(class_element)
        direct_changes = [
            child for child in class_element if xml_tag(child) == "compatibilityChanges"
        ]
        require(
            len(direct_changes) <= 1,
            "japicmp class compatibility changes are duplicated",
        )
        has_direct_incompatibility = bool(
            direct_changes
            and any(
                xml_tag(change) == "compatibilityChange"
                and (
                    change.get("binaryCompatible") == "false"
                    or change.get("sourceCompatible") == "false"
                )
                for change in direct_changes[0]
            )
        )
        if status == "REMOVED" or (
            status == "MODIFIED"
            and has_direct_incompatibility
            and (binary == "false" or source == "false")
        ):
            findings.add((artifact, class_name, "<class>", status))

        class_types = [
            child for child in class_element if xml_tag(child) == "classType"
        ]
        require(len(class_types) <= 1, "japicmp class type is duplicated")
        if status != "NEW" and class_types:
            class_type_status = class_types[0].get("changeStatus", "UNCHANGED")
            require(
                class_type_status in {"UNCHANGED", "NEW", "MODIFIED", "REMOVED"},
                "japicmp class type status is invalid",
            )
            if class_type_status != "UNCHANGED":
                findings.add((artifact, class_name, "<class>", class_type_status))

        sections = {
            "constructors": "constructor",
            "methods": "method",
            "fields": "field",
            "interfaces": "interface",
        }
        seen_sections: set[str] = set()
        for section in class_element:
            section_tag = xml_tag(section)
            if section_tag not in sections:
                continue
            require(
                section_tag not in seen_sections,
                "japicmp member section is duplicated",
            )
            seen_sections.add(section_tag)
            member_kind = sections[section_tag]
            for member in section:
                require(
                    xml_tag(member) == member_kind,
                    "japicmp member entry is invalid",
                )
                member_status, member_binary, member_source = compatibility(member)
                if member_status == "REMOVED" or (
                    member_status in {"NEW", "MODIFIED"}
                    and (member_binary == "false" or member_source == "false")
                ):
                    findings.add(
                        (
                            artifact,
                            class_name,
                            behavior_member(member, member_kind),
                            member_status,
                        )
                    )
    return frozenset(findings)


def invoke_japicmp(old_jar: Path, new_jar: Path, japicmp: Path, xml_path: Path) -> int:
    require(not xml_path.exists(), "japicmp XML output path already exists")
    command = [
        "java",
        "-Xmx512m",
        "-XX:MaxMetaspaceSize=192m",
        "-jar",
        str(japicmp),
        "--old",
        str(old_jar),
        "--new",
        str(new_jar),
        "--only-modified",
        "--xml-file",
        str(xml_path),
        "--error-on-binary-incompatibility",
        "--error-on-source-incompatibility",
    ]
    try:
        result = subprocess.run(
            command,
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=JAPICMP_TIMEOUT_SECONDS,
        )
    except subprocess.TimeoutExpired as exc:
        raise ProtocolError("japicmp timed out") from exc
    require(result.returncode in {0, 1}, "japicmp execution failed")
    return result.returncode


def compare_jars(
    artifact: str,
    old_jar: Path,
    new_jar: Path,
    japicmp: Path,
    xml_path: Path,
    exceptions: tuple[dict[str, str], ...],
) -> None:
    returncode = invoke_japicmp(old_jar, new_jar, japicmp, xml_path)
    findings = parse_japicmp_findings(xml_path, artifact)
    if returncode == 0:
        require(not findings, "japicmp exit/findings mismatch")
        return
    require(findings, "japicmp failed without incompatibility findings")
    allowed = frozenset(
        (
            rule["artifact"],
            rule["class"],
            rule["member"],
            rule["category"],
        )
        for rule in exceptions
    )
    unallowed = findings - allowed
    if unallowed:
        raise ProtocolError("breaking API or ABI change")


def comparison_plan(policy: dict[str, Any]) -> tuple[tuple[str, str], ...]:
    return tuple(
        (
            entry["artifactId"],
            entry["comparison"]["targetArtifactId"],
        )
        for entry in policy["artifacts"]
        if entry["baselineState"] == "present"
    )


def run_semantic_checks(
    jars: dict[str, bytes], policy: dict[str, Any], japicmp: Path, work: Path
) -> None:
    cli = policy["japicmpPolicy"]["cli"]
    require(
        japicmp.is_file()
        and japicmp.stat().st_size == cli["size"]
        and sha256_bytes(japicmp.read_bytes()) == cli["sha256"],
        "japicmp pin mismatch",
    )
    for artifact_id, target_id in comparison_plan(policy):
        entry = policy["artifactsById"][artifact_id]
        target = policy["artifactsById"][target_id]
        new_jar = work / "candidate" / target["jarName"]
        old_jar = work / "baseline" / f"{artifact_id}.jar"
        new_jar.parent.mkdir(parents=True, exist_ok=True)
        old_jar.parent.mkdir(parents=True, exist_ok=True)
        new_jar.write_bytes(jars[target["jarName"]])
        baseline = policy["baselines"][artifact_id]
        download(
            baseline["url"],
            old_jar,
            baseline["jarSha256"],
            baseline["jarSize"],
        )
        require(
            validate_inner_jar(old_jar.name, old_jar.read_bytes())
            == {
                "artifactId": artifact_id,
                "groupId": entry["groupId"],
                "version": BASELINE_VERSION,
            },
            "baseline JAR embedded Maven coordinate mismatch",
        )
        compare_jars(
            artifact_id,
            old_jar,
            new_jar,
            japicmp,
            work / f"{artifact_id}.xml",
            policy["exceptions"],
        )


def verify_remote_artifact(
    api: GitHubApi, binding: dict[str, Any], protected_root: Path, japicmp: Path
) -> str:
    if binding["producer_outcome"] != "success":
        return FAIL_PRODUCER
    try:
        policy = load_policy(protected_root)
    except ProtocolError:
        return FAIL_POLICY
    try:
        validate_candidate_module_poms(api, binding, policy)
        artifact = artifact_metadata(api, binding)
        with tempfile.TemporaryDirectory() as download_directory:
            archive = Path(download_directory) / "candidate-artifact.zip"
            digest = str(artifact["digest"]).removeprefix("sha256:")
            api.download_artifact(
                f"repos/{api.repository}/actions/artifacts/{artifact['id']}/zip",
                archive,
                artifact["size_in_bytes"],
                digest,
            )
            jars = validate_candidate_artifact_path(archive, digest, binding, policy)
    except ProtocolError:
        return FAIL_ARTIFACT
    try:
        with tempfile.TemporaryDirectory() as directory:
            run_semantic_checks(jars, policy, japicmp, Path(directory))
        return PASS
    except ProtocolError as exc:
        return (
            FAIL_BREAKING if str(exc) == "breaking API or ABI change" else FAIL_POLICY
        )
    except (OSError, subprocess.SubprocessError):
        return FAIL_INTERNAL


def publish_status(
    api: GitHubApi,
    event: dict[str, Any],
    repository: str,
    repository_id: int,
    protected_sha: str,
    source_run_id: int,
    expected_candidate_sha: str,
    expected_attempt: int,
    verdict: str,
) -> dict[str, str]:
    require(
        verdict in VERDICTS and len(verdict.encode("ascii")) == 8,
        "publisher verdict token is invalid",
    )
    binding = bind_source_run(
        api, event, repository, repository_id, protected_sha, source_run_id
    )
    require(
        binding["candidate_sha"] == expected_candidate_sha
        and binding["source_run_attempt"] == expected_attempt,
        "publisher binding drift",
    )
    statuses = api.get_json(
        f"repos/{repository}/commits/{expected_candidate_sha}/statuses?per_page=100"
    )
    require(isinstance(statuses, list), "status response is invalid")
    current = (source_run_id, expected_attempt)
    for status_value in statuses:
        if status_value.get("context") != STATUS_CONTEXT:
            continue
        match = re.fullmatch(
            r"Trusted shadow (?:passed|failed) \(run ([1-9][0-9]*) attempt ([1-9][0-9]*)\)",
            str(status_value.get("description", "")),
        )
        if match and (int(match.group(1)), int(match.group(2))) > current:
            return {"published": "false", "state": "skipped"}
    if binding["producer_outcome"] != "success":
        verdict = FAIL_PRODUCER
    state = "success" if verdict == PASS else "failure"
    description = f"Trusted shadow {'passed' if state == 'success' else 'failed'} (run {source_run_id} attempt {expected_attempt})"
    api.post_json(
        f"repos/{repository}/statuses/{expected_candidate_sha}",
        {
            "state": state,
            "context": STATUS_CONTEXT,
            "description": description,
            "target_url": f"https://github.com/{repository}/actions/runs/{source_run_id}/attempts/{expected_attempt}",
        },
    )
    return {"published": "true", "state": state}


def api_from_environment(repository: str) -> GitHubApi:
    return GitHubApi(
        repository,
        os.environ.get("GH_TOKEN", ""),
        os.environ.get("GITHUB_API_URL", "https://api.github.com"),
    )


def binding_arguments(command: argparse.ArgumentParser) -> None:
    command.add_argument("--event-path", type=Path, required=True)
    command.add_argument("--repository", required=True)
    command.add_argument("--repository-id", type=int, required=True)
    command.add_argument("--protected-sha", required=True)
    command.add_argument("--source-run-id", type=int, required=True)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    stage = commands.add_parser("stage-candidate")
    stage.add_argument("--candidate-root", type=Path, required=True)
    stage.add_argument("--output-root", type=Path, required=True)
    stage.add_argument("--candidate-sha", required=True)
    stage.add_argument("--source-event", required=True)
    stage.add_argument("--source-run-id", type=int, required=True)
    stage.add_argument("--source-run-attempt", type=int, required=True)
    clean = commands.add_parser("assert-clean")
    clean.add_argument("--root", type=Path, required=True)
    clean.add_argument("--sha", required=True)
    policy = commands.add_parser("check-policy")
    policy.add_argument("--protected-root", type=Path, required=True)
    bind = commands.add_parser("bind")
    binding_arguments(bind)
    bind.add_argument("--output", type=Path, required=True)
    verify = commands.add_parser("verify")
    binding_arguments(verify)
    verify.add_argument("--protected-root", type=Path, required=True)
    verify.add_argument("--japicmp", type=Path, required=True)
    verify.add_argument("--output", type=Path, required=True)
    publish = commands.add_parser("publish")
    binding_arguments(publish)
    publish.add_argument("--expected-candidate-sha", required=True)
    publish.add_argument("--expected-source-run-attempt", type=int, required=True)
    publish.add_argument("--verdict", required=True)
    try:
        args = parser.parse_args(argv)
        if args.command == "stage-candidate":
            stage_candidate(
                args.candidate_root,
                args.output_root,
                args.candidate_sha,
                args.source_event,
                args.source_run_id,
                args.source_run_attempt,
            )
        elif args.command == "assert-clean":
            assert_clean_checkout(args.root, args.sha)
        elif args.command == "check-policy":
            load_policy(args.protected_root)
        else:
            event = strict_json_loads(args.event_path.read_bytes())
            require(isinstance(event, dict), "workflow event is invalid")
            api = api_from_environment(args.repository)
            binding = bind_source_run(
                api,
                event,
                args.repository,
                args.repository_id,
                args.protected_sha,
                args.source_run_id,
            )
            if args.command == "bind":
                args.output.write_bytes(canonical_json(binding) + b"\n")
            elif args.command == "verify":
                verdict = verify_remote_artifact(
                    api, binding, args.protected_root, args.japicmp
                )
                args.output.write_bytes(verdict.encode("ascii"))
            else:
                publish_status(
                    api,
                    event,
                    args.repository,
                    args.repository_id,
                    args.protected_sha,
                    args.source_run_id,
                    args.expected_candidate_sha,
                    args.expected_source_run_attempt,
                    args.verdict,
                )
    except (OSError, ProtocolError, subprocess.SubprocessError) as exc:
        print(f"API COMPATIBILITY PROTOCOL ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
