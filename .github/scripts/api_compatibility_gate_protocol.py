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
    "https://repo1.maven.org/maven2/com/github/siom79/japicmp/japicmp/0.23.1/"
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
        require(
            isinstance(item["jar"], str)
            and JAR_NAME_RE.fullmatch(item["jar"]) is not None,
            "profile jar is invalid",
        )
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
        require(
            isinstance(item["url"], str) and item["url"].startswith("https://"),
            "baseline ledger URL is invalid",
        )
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
    require(isinstance(allowlist["exceptions"], list), "allowlist is invalid")
    return {"artifacts": artifacts, "baselines": ledger_by_jar}


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
        try:
            with zipfile.ZipFile(BytesIO(data), "r") as jar:
                require(jar.testzip() is None, "candidate JAR is corrupt")
        except zipfile.BadZipFile as exc:
            raise ProtocolError("candidate JAR is not a ZIP") from exc
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


def download(url: str, destination: Path, digest: str, size: int) -> None:
    try:
        with urllib.request.urlopen(url, timeout=60) as response:
            data = response.read(size + 1)
    except (urllib.error.URLError, TimeoutError) as exc:
        raise ProtocolError("protected download failed") from exc
    require(
        len(data) == size and sha256_bytes(data) == digest,
        "protected download pin mismatch",
    )
    destination.write_bytes(data)


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
        command = [
            "java",
            "-jar",
            str(japicmp),
            "--old",
            str(old_jar),
            "--new",
            str(new_jar),
            "--only-modified",
            "--error-on-binary-incompatibility-modifications",
            "--error-on-source-incompatibility-modifications",
        ]
        result = subprocess.run(command, check=False, capture_output=True, text=True)
        if result.returncode != 0:
            raise ProtocolError("breaking API or ABI change")


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
