#!/usr/bin/env python3
"""Fail-closed protocol for the dormant trusted API compatibility shadow route."""

from __future__ import annotations

import argparse
import hashlib
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
MAIN_BRANCH = "main"
ALLOWED_SOURCE_EVENTS = frozenset({"pull_request", "merge_group"})
SHA_RE = re.compile(r"[0-9a-f]{40}")
SHA256_RE = re.compile(r"[0-9a-f]{64}")
REPOSITORY_RE = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
JAR_NAME_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]*\.jar")
MERGE_GROUP_BRANCH_RE = re.compile(r"gh-readonly-queue/main/.+")
POLICY_ROOT = Path("coco-support/coco-tools/public-api-compatibility")
POLICY_FILES = (
    "public-api-profile.json",
    "baseline-ledger.json",
    "allowlist.json",
    "japicmp-key.json",
)
JAPICMP_URL = (
    "https://repo.maven.apache.org/maven2/com/github/siom79/japicmp/japicmp/0.23.1/"
    "japicmp-0.23.1-jar-with-dependencies.jar"
)
JAPICMP_SIZE = 5988558
JAPICMP_SHA256 = "f2300a8531b68e25b678247874a1eae13a07d6842a4a1236845481fc90c5c6c7"
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
JAPICMP_TIMEOUT_SECONDS = 60
CLASS_NAME_RE = re.compile(r"[A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*")
MEMBER_NAME_RE = re.compile(
    r"(?:<class>|<init>\([A-Za-z0-9_$.,\[\]]*\)|"
    r"[A-Za-z_$][A-Za-z0-9_$]*(?:\([A-Za-z0-9_$.,\[\]]*\))?)"
)
COORDINATE_RE = re.compile(
    r"([A-Za-z_][A-Za-z0-9_-]*(?:\.[A-Za-z_][A-Za-z0-9_-]*)*):"
    r"([A-Za-z0-9][A-Za-z0-9_.-]*)"
)
MAVEN_VERSION_RE = re.compile(r"[0-9][A-Za-z0-9._-]*")
JAPICMP_CATEGORIES = frozenset(
    {
        "ANNOTATION_ADDED",
        "ANNOTATION_DEPRECATED_ADDED",
        "ANNOTATION_MODIFIED",
        "ANNOTATION_REMOVED",
        "CLASS_GENERIC_TEMPLATE_CHANGED",
        "CLASS_GENERIC_TEMPLATE_GENERICS_CHANGED",
        "CLASS_LESS_ACCESSIBLE",
        "CLASS_NO_LONGER_PUBLIC",
        "CLASS_NOW_ABSTRACT",
        "CLASS_NOW_CHECKED_EXCEPTION",
        "CLASS_NOW_FINAL",
        "CLASS_REMOVED",
        "CLASS_TYPE_CHANGED",
        "CONSTRUCTOR_LESS_ACCESSIBLE",
        "CONSTRUCTOR_REMOVED",
        "FIELD_GENERICS_CHANGED",
        "FIELD_LESS_ACCESSIBLE",
        "FIELD_LESS_ACCESSIBLE_THAN_IN_SUPERCLASS",
        "FIELD_NO_LONGER_STATIC",
        "FIELD_NO_LONGER_TRANSIENT",
        "FIELD_NO_LONGER_VOLATILE",
        "FIELD_NOW_FINAL",
        "FIELD_NOW_STATIC",
        "FIELD_NOW_TRANSIENT",
        "FIELD_NOW_VOLATILE",
        "FIELD_REMOVED",
        "FIELD_REMOVED_IN_SUPERCLASS",
        "FIELD_STATIC_AND_OVERRIDES_STATIC",
        "FIELD_TYPE_CHANGED",
        "INTERFACE_ADDED",
        "INTERFACE_REMOVED",
        "METHOD_ABSTRACT_ADDED_IN_IMPLEMENTED_INTERFACE",
        "METHOD_ABSTRACT_ADDED_IN_SUPERCLASS",
        "METHOD_ABSTRACT_ADDED_TO_CLASS",
        "METHOD_ABSTRACT_NOW_DEFAULT",
        "METHOD_ADDED_TO_INTERFACE",
        "METHOD_ADDED_TO_PUBLIC_CLASS",
        "METHOD_DEFAULT_ADDED_IN_IMPLEMENTED_INTERFACE",
        "METHOD_IS_STATIC_AND_OVERRIDES_NOT_STATIC",
        "METHOD_LESS_ACCESSIBLE",
        "METHOD_LESS_ACCESSIBLE_THAN_IN_SUPERCLASS",
        "METHOD_MOVED_TO_SUPERCLASS",
        "METHOD_NEW_DEFAULT",
        "METHOD_NEW_STATIC_ADDED_TO_INTERFACE",
        "METHOD_NO_LONGER_STATIC",
        "METHOD_NO_LONGER_THROWS_CHECKED_EXCEPTION",
        "METHOD_NO_LONGER_VARARGS",
        "METHOD_NON_STATIC_IN_INTERFACE_NOW_STATIC",
        "METHOD_NOW_ABSTRACT",
        "METHOD_NOW_FINAL",
        "METHOD_NOW_STATIC",
        "METHOD_NOW_THROWS_CHECKED_EXCEPTION",
        "METHOD_NOW_VARARGS",
        "METHOD_PARAMETER_GENERICS_CHANGED",
        "METHOD_REMOVED",
        "METHOD_REMOVED_IN_SUPERCLASS",
        "METHOD_RETURN_TYPE_CHANGED",
        "METHOD_RETURN_TYPE_GENERICS_CHANGED",
        "METHOD_STATIC_IN_INTERFACE_NO_LONGER_STATIC",
        "SUPERCLASS_ADDED",
        "SUPERCLASS_MODIFIED_INCOMPATIBLE",
        "SUPERCLASS_REMOVED",
    }
)


class ProtocolError(RuntimeError):
    """Raised when an untrusted or protected protocol invariant is violated."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ProtocolError(message)


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


def candidate_jars(root: Path) -> list[Path]:
    result: list[Path] = []
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
        result.append(candidate)
    result.sort(key=lambda item: item.name)
    require(len(result) == 32, "candidate must produce exactly 32 JARs")
    names = [item.name for item in result]
    require(len(set(names)) == 32, "candidate JAR names are duplicated")
    require(
        len({name.casefold() for name in names}) == 32,
        "candidate JAR names case-collide",
    )
    require(
        all(JAR_NAME_RE.fullmatch(name) is not None for name in names),
        "candidate JAR name is invalid",
    )
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
        data = jar.read_bytes()
        require(0 < len(data) <= MAX_ENTRY_BYTES, "candidate JAR size is invalid")
        target = output_root / JAR_DIRECTORY / jar.name
        target.write_bytes(data)
        entries.append(
            {"name": jar.name, "sha256": sha256_bytes(data), "size": len(data)}
        )
    manifest = {
        "candidate_sha": candidate_sha,
        "jars": entries,
        "kind": "non-authoritative-candidate-jars",
        "schema_version": 2,
        "source_event": source_event,
        "source_run_attempt": source_run_attempt,
        "source_run_id": source_run_id,
    }
    (output_root / MANIFEST_NAME).write_bytes(canonical_json(manifest) + b"\n")
    assert_clean_checkout(candidate_root, candidate_sha)


def read_safe_zip(archive_bytes: bytes) -> dict[str, bytes]:
    require(len(archive_bytes) <= MAX_ARCHIVE_BYTES, "artifact archive is oversized")
    result: dict[str, bytes] = {}
    folded: set[str] = set()
    total = 0
    try:
        with zipfile.ZipFile(BytesIO(archive_bytes), "r") as archive:
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
                require(
                    not (info.flag_bits & 1), "encrypted artifact entry is forbidden"
                )
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
                        and info.file_size / info.compress_size
                        <= MAX_COMPRESSION_RATIO,
                        "artifact compression ratio is unsafe",
                    )
                total += info.file_size
                require(total <= MAX_TOTAL_BYTES, "artifact total size is oversized")
                data = archive.read(info)
                require(len(data) == info.file_size, "artifact entry size drift")
                result[name] = data
    except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
        raise ProtocolError("invalid artifact ZIP") from exc
    return result


def validate_inner_jar(name: str, data: bytes) -> None:
    require(0 < len(data) <= MAX_ENTRY_BYTES, f"inner JAR size is invalid: {name}")
    seen: set[str] = set()
    folded: set[str] = set()
    total = 0
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
                require(
                    len(archive.read(info)) == info.file_size,
                    f"inner JAR entry size drift: {name}",
                )
    except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
        raise ProtocolError(f"candidate JAR is invalid: {name}") from exc


def policy_file(root: Path, name: str) -> Any:
    path = root / POLICY_ROOT / name
    require(
        path.is_file() and not path.is_symlink(),
        f"missing protected policy asset: {name}",
    )
    data = path.read_bytes()
    value = strict_json_loads(data)
    require(
        data == canonical_json(value) + b"\n",
        f"protected policy asset is not canonical: {name}",
    )
    return value


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


def validate_allowlist(
    value: object, baseline_names: list[str]
) -> frozenset[tuple[str, str, str, str]]:
    require(isinstance(value, list), "allowlist exceptions are invalid")
    exceptions: list[tuple[str, str, str, str]] = []
    for entry in value:
        item = exact_keys(
            entry,
            {"artifact", "category", "class", "member"},
            "allowlist exception is invalid",
        )
        artifact = item["artifact"]
        class_name = item["class"]
        member = item["member"]
        category = item["category"]
        require(
            isinstance(artifact, str) and artifact in baseline_names,
            "allowlist artifact is invalid",
        )
        require(
            isinstance(class_name, str)
            and CLASS_NAME_RE.fullmatch(class_name) is not None,
            "allowlist class must be exact",
        )
        require(
            isinstance(member, str) and MEMBER_NAME_RE.fullmatch(member) is not None,
            "allowlist member must be exact",
        )
        require(
            isinstance(category, str) and category in JAPICMP_CATEGORIES,
            "allowlist category is invalid",
        )
        exceptions.append((artifact, class_name, member, category))
    require(
        exceptions == sorted(exceptions) and len(set(exceptions)) == len(exceptions),
        "allowlist exceptions must be uniquely sorted",
    )
    return frozenset(exceptions)


def load_policy(root: Path) -> dict[str, Any]:
    profile = exact_keys(
        policy_file(root, POLICY_FILES[0]),
        {"artifacts", "schema_version"},
        "profile schema is invalid",
    )
    ledger = exact_keys(
        policy_file(root, POLICY_FILES[1]),
        {"baselines", "schema_version"},
        "ledger schema is invalid",
    )
    allowlist = exact_keys(
        policy_file(root, POLICY_FILES[2]),
        {"exceptions", "schema_version"},
        "allowlist schema is invalid",
    )
    key = exact_keys(
        policy_file(root, POLICY_FILES[3]),
        {"japicmp", "schema_version"},
        "japicmp key schema is invalid",
    )
    require(
        profile["schema_version"]
        == ledger["schema_version"]
        == allowlist["schema_version"]
        == key["schema_version"]
        == 1,
        "policy schema version is invalid",
    )
    artifacts = profile["artifacts"]
    require(
        isinstance(artifacts, list) and len(artifacts) == 32,
        "profile must declare exactly 32 artifacts",
    )
    names: list[str] = []
    baseline_names: list[str] = []
    for entry in artifacts:
        item = exact_keys(
            entry, {"baseline", "jar", "coordinate"}, "profile artifact is invalid"
        )
        require(
            isinstance(item["baseline"], bool) and isinstance(item["coordinate"], str),
            "profile artifact is invalid",
        )
        _, artifact_id = coordinate_parts(item["coordinate"])
        require(
            isinstance(item["jar"], str)
            and JAR_NAME_RE.fullmatch(item["jar"]) is not None,
            "profile jar is invalid",
        )
        require(item["jar"] == f"{artifact_id}.jar", "profile coordinate/JAR mismatch")
        names.append(item["jar"])
        if item["baseline"]:
            baseline_names.append(item["jar"])
    require(
        names == sorted(names)
        and len(set(names)) == 32
        and len({name.casefold() for name in names}) == 32,
        "profile inventory is not canonical",
    )
    require(len(baseline_names) == 20, "profile must declare 20 baseline artifacts")
    require(
        len(names) - len(baseline_names) == 12, "profile must declare 12 n.a. artifacts"
    )
    require(
        isinstance(ledger["baselines"], list) and len(ledger["baselines"]) == 20,
        "baseline ledger is invalid",
    )
    ledger_by_jar: dict[str, dict[str, Any]] = {}
    for entry in ledger["baselines"]:
        item = exact_keys(
            entry, {"jar", "sha256", "size", "url"}, "baseline ledger entry is invalid"
        )
        require(
            isinstance(item["jar"], str) and item["jar"] in baseline_names,
            "baseline ledger jar is invalid",
        )
        valid_sha256(item["sha256"], "baseline ledger SHA is invalid")
        require(
            is_int(item["size"])
            and item["size"] > 0
            and item["size"] <= MAX_ENTRY_BYTES,
            "baseline ledger size is invalid",
        )
        coordinate = next(
            artifact["coordinate"]
            for artifact in artifacts
            if artifact["jar"] == item["jar"]
        )
        validate_maven_central_url(item["url"], coordinate)
        require(item["jar"] not in ledger_by_jar, "baseline ledger has duplicates")
        ledger_by_jar[item["jar"]] = item
    require(
        set(ledger_by_jar) == set(baseline_names),
        "baseline ledger does not match profile",
    )
    japicmp = exact_keys(
        key["japicmp"], {"sha256", "size", "url", "version"}, "japicmp key is invalid"
    )
    require(
        japicmp
        == {
            "version": "0.23.1",
            "url": JAPICMP_URL,
            "size": JAPICMP_SIZE,
            "sha256": JAPICMP_SHA256,
        },
        "japicmp key is not pinned",
    )
    exceptions = validate_allowlist(allowlist["exceptions"], baseline_names)
    return {
        "artifacts": artifacts,
        "baselines": ledger_by_jar,
        "exceptions": exceptions,
    }


def validate_candidate_artifact(
    archive: bytes, digest: str, binding: dict[str, Any], policy: dict[str, Any]
) -> dict[str, bytes]:
    require(
        sha256_bytes(archive) == valid_sha256(digest, "artifact digest is invalid"),
        "artifact digest mismatch",
    )
    files = read_safe_zip(archive)
    manifest_bytes = files.get(MANIFEST_NAME)
    require(manifest_bytes is not None, "candidate manifest is missing")
    manifest = exact_keys(
        strict_json_loads(manifest_bytes),
        {
            "candidate_sha",
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
        manifest["schema_version"] == 2
        and manifest["kind"] == "non-authoritative-candidate-jars",
        "candidate manifest is invalid",
    )
    for key in ("candidate_sha", "source_event", "source_run_id", "source_run_attempt"):
        require(manifest[key] == binding[key], "candidate manifest binding drift")
    require(
        isinstance(manifest["jars"], list) and len(manifest["jars"]) == 32,
        "candidate manifest JAR list is invalid",
    )
    expected = [item["jar"] for item in policy["artifacts"]]
    expected_paths = {MANIFEST_NAME, *(f"{JAR_DIRECTORY}/{name}" for name in expected)}
    require(set(files) == expected_paths, "candidate artifact inventory is invalid")
    claims: dict[str, dict[str, Any]] = {}
    for claim in manifest["jars"]:
        item = exact_keys(
            claim, {"name", "sha256", "size"}, "candidate manifest JAR claim is invalid"
        )
        require(
            isinstance(item["name"], str)
            and item["name"] in expected
            and item["name"] not in claims,
            "candidate manifest JAR name is invalid",
        )
        valid_sha256(item["sha256"], "candidate manifest JAR SHA is invalid")
        require(
            is_int(item["size"]) and 0 < item["size"] <= MAX_ENTRY_BYTES,
            "candidate manifest JAR size is invalid",
        )
        claims[item["name"]] = item
    require(set(claims) == set(expected), "candidate manifest JAR claims are invalid")
    jars: dict[str, bytes] = {}
    for name in expected:
        data = files[f"{JAR_DIRECTORY}/{name}"]
        require(
            len(data) == claims[name]["size"]
            and sha256_bytes(data) == claims[name]["sha256"],
            "candidate JAR claim mismatch",
        )
        validate_inner_jar(name, data)
        jars[name] = data
    return jars


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

    def get_bytes(self, path: str) -> bytes:
        return self.request("GET", path)

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
    valid_sha256(
        str(artifact.get("digest", "")).removeprefix("sha256:"),
        "candidate artifact digest is invalid",
    )
    return artifact


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


def download(url: str, destination: Path, digest: str, size: int) -> None:
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
            content_length = response.headers.get("Content-Length")
            require(
                content_length is not None and int(content_length) == size,
                "protected download length header mismatch",
            )
            data = response.read(size + 1)
    except (
        ValueError,
        urllib.error.HTTPError,
        urllib.error.URLError,
        TimeoutError,
    ) as exc:
        raise ProtocolError("protected download failed") from exc
    require(
        len(data) == size and sha256_bytes(data) == digest,
        "protected download pin mismatch",
    )
    destination.write_bytes(data)


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
        b"<!DOCTYPE" not in upper and b"<!ENTITY" not in upper, "unsafe japicmp XML"
    )
    try:
        root = ElementTree.fromstring(data)
    except ElementTree.ParseError as exc:
        raise ProtocolError("japicmp XML output is invalid") from exc
    require(xml_tag(root) == "japicmp", "japicmp XML root is invalid")

    findings: set[tuple[str, str, str, str]] = set()

    def walk(
        element: ElementTree.Element,
        class_name: str | None,
        member: str,
    ) -> None:
        tag = xml_tag(element)
        if tag == "class":
            class_name = element.get("fullyQualifiedName") or element.get("name")
            require(
                isinstance(class_name, str)
                and CLASS_NAME_RE.fullmatch(class_name) is not None,
                "japicmp class name is invalid",
            )
            member = "<class>"
        elif tag in {"method", "constructor", "field"}:
            require(class_name is not None, "japicmp member has no class")
            member = behavior_member(element, tag)
        elif tag == "compatibilityChange":
            require(class_name is not None, "japicmp change has no class")
            category = element.get("type")
            binary = element.get("binaryCompatible")
            source = element.get("sourceCompatible")
            require(
                category in JAPICMP_CATEGORIES
                and binary in {"true", "false"}
                and source in {"true", "false"},
                "japicmp compatibility change is invalid",
            )
            if binary == "false" or source == "false":
                findings.add((artifact, class_name, member, category))
        for child in element:
            walk(child, class_name, member)

    walk(root, None, "<class>")
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
    exceptions: frozenset[tuple[str, str, str, str]],
) -> None:
    returncode = invoke_japicmp(old_jar, new_jar, japicmp, xml_path)
    findings = parse_japicmp_findings(xml_path, artifact)
    if returncode == 0:
        require(not findings, "japicmp exit/findings mismatch")
        return
    require(findings, "japicmp failed without incompatibility findings")
    unallowed = findings - exceptions
    if unallowed:
        raise ProtocolError("breaking API or ABI change")


def run_semantic_checks(
    jars: dict[str, bytes], policy: dict[str, Any], japicmp: Path, work: Path
) -> None:
    require(
        japicmp.is_file()
        and japicmp.stat().st_size == JAPICMP_SIZE
        and sha256_bytes(japicmp.read_bytes()) == JAPICMP_SHA256,
        "japicmp pin mismatch",
    )
    for entry in policy["artifacts"]:
        if not entry["baseline"]:
            continue
        name = entry["jar"]
        new_jar = work / "candidate" / name
        old_jar = work / "baseline" / name
        new_jar.parent.mkdir(parents=True, exist_ok=True)
        old_jar.parent.mkdir(parents=True, exist_ok=True)
        new_jar.write_bytes(jars[name])
        baseline = policy["baselines"][name]
        download(baseline["url"], old_jar, baseline["sha256"], baseline["size"])
        compare_jars(
            name,
            old_jar,
            new_jar,
            japicmp,
            work / f"{name}.xml",
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
        artifact = artifact_metadata(api, binding)
        archive = api.get_bytes(
            f"repos/{api.repository}/actions/artifacts/{artifact['id']}/zip"
        )
        jars = validate_candidate_artifact(
            archive, str(artifact["digest"]).removeprefix("sha256:"), binding, policy
        )
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
