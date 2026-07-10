#!/usr/bin/env python3
"""Trusted-base multi-agent PR review utilities for GitHub Actions."""

from __future__ import annotations

import argparse
import base64
import copy
import datetime as dt
import fnmatch
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path, PurePosixPath
from typing import Any, Iterable


SCHEMA_VERSION = 1
COMMENT_MARKER = "<!-- agent-jury:v1 -->"
LEGACY_COMMENT_MARKER = "<!-- claude-review-marker: managed by workflow -->"
STATUS_CONTEXT = "Agent jury gate"
SHA_RE = re.compile(r"^[0-9a-f]{40}$")
ROLE_RE = re.compile(r"^[a-z][a-z0-9-]{1,48}$")
HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")
JAVA_BOUNDARY_RE = re.compile(
    r"^\s*(?:@[\w.]+(?:\([^)]*\))?\s*$|"
    r"(?:(?:public|protected|private|static|final|abstract|synchronized|default)\s+)*"
    r"(?:class|interface|enum|record)\s+\w+|"
    r"(?:(?:public|protected|private|static|final|abstract|synchronized|default)\s+)+"
    r"[\w<>,.?\[\] ]+\s+\w+\s*\([^;]*\)\s*(?:throws\s+[^{]+)?\{?\s*$)"
)
TEXT_SUFFIXES = {
    ".java",
    ".kt",
    ".kts",
    ".xml",
    ".yml",
    ".yaml",
    ".json",
    ".md",
    ".properties",
    ".py",
    ".sh",
    ".ps1",
    ".js",
    ".mjs",
    ".ts",
    ".txt",
    ".toml",
    ".sql",
    ".html",
    ".css",
}


class ReviewError(RuntimeError):
    """Expected fail-closed review error."""


class GitHubNotFoundError(ReviewError):
    """A GitHub resource does not exist at the requested revision."""


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ReviewError(f"Unable to read JSON from {path}: {exc}") from exc


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(canonical_json(value) + "\n", encoding="utf-8")


def load_config(path: Path) -> dict[str, Any]:
    config = read_json(path)
    if (
        not isinstance(config, dict)
        or config.get("version", config.get("schema_version")) != SCHEMA_VERSION
    ):
        raise ReviewError("Agent review config has an unsupported schema_version.")
    if config.get("gate_name", STATUS_CONTEXT) != STATUS_CONTEXT:
        raise ReviewError(f"Agent review gate_name must remain {STATUS_CONTEXT!r}.")
    if config.get("managed_comment_marker", COMMENT_MARKER) != COMMENT_MARKER:
        raise ReviewError(
            "Agent review managed_comment_marker does not match the publisher contract."
        )
    return config


def normalized_limits(config: dict[str, Any]) -> dict[str, int]:
    legacy = config.get("limits", {})
    context = config.get("context_budget", {})
    output = config.get("output_limits", {})
    return {
        "diff_chars": int(
            legacy.get("diff_chars", context.get("pr_diff_hard_limit", 60000))
        ),
        "assembled_context_chars": int(
            legacy.get(
                "assembled_context_chars", context.get("specialist_total_limit", 96000)
            )
        ),
        "policy_chars": int(
            legacy.get(
                "policy_chars", context.get("protected_policy_and_specs_limit", 20000)
            )
        ),
        "intent_chars": int(
            legacy.get("intent_chars", context.get("pr_intent_limit", 8000))
        ),
        "patch_chars": int(
            legacy.get("patch_chars", context.get("patch_limit", 48000))
        ),
        "code_context_chars": int(legacy.get("code_context_chars", 20000)),
        "per_file_chars": int(legacy.get("per_file_chars", 12000)),
        "full_file_chars": int(legacy.get("full_file_chars", 16000)),
        "max_findings_per_agent": int(
            legacy.get("max_findings_per_agent", output.get("specialist_findings", 10))
        ),
        "max_questions_per_agent": int(
            legacy.get("max_questions_per_agent", output.get("specialist_questions", 5))
        ),
        "max_context_gaps_per_agent": int(
            legacy.get(
                "max_context_gaps_per_agent", output.get("specialist_context_gaps", 10)
            )
        ),
        "response_bytes": int(legacy.get("response_bytes", 1048576)),
        "request_timeout_seconds": int(legacy.get("request_timeout_seconds", 180)),
        "specialist_tokens": int(legacy.get("specialist_tokens", 2600)),
        "verifier_tokens": int(legacy.get("verifier_tokens", 2400)),
        "chair_tokens": int(legacy.get("chair_tokens", 2800)),
    }


def context_digest(context: dict[str, Any]) -> str:
    value = copy.deepcopy(context)
    value.setdefault("binding", {})["context_sha256"] = ""
    return hashlib.sha256(canonical_json(value).encode("utf-8")).hexdigest()


def bind_context(context: dict[str, Any]) -> dict[str, Any]:
    context.setdefault("binding", {})["context_sha256"] = ""
    context["binding"]["context_sha256"] = context_digest(context)
    return context


def validate_context(context: dict[str, Any]) -> None:
    if context.get("schema_version") != SCHEMA_VERSION:
        raise ReviewError("Context schema_version is invalid.")
    binding = context.get("binding")
    if not isinstance(binding, dict):
        raise ReviewError("Context binding is missing.")
    for name in ("base_sha", "head_sha"):
        if not SHA_RE.fullmatch(str(binding.get(name, ""))):
            raise ReviewError(f"Context {name} is invalid.")
    if not re.fullmatch(r"[0-9a-f]{64}", str(binding.get("protocol_sha256", ""))):
        raise ReviewError("Context protocol_sha256 is invalid.")
    claimed = str(binding.get("context_sha256", ""))
    if not re.fullmatch(r"[0-9a-f]{64}", claimed) or claimed != context_digest(context):
        raise ReviewError("Context SHA-256 binding is invalid.")


def safe_base_file(root: Path, relative: str) -> Path:
    candidate = (root / PurePosixPath(relative)).resolve()
    root_resolved = root.resolve()
    try:
        candidate.relative_to(root_resolved)
    except ValueError as exc:
        raise ReviewError(
            f"Context path escapes the trusted base checkout: {relative}"
        ) from exc
    return candidate


def protocol_manifest(base_root: Path, config: dict[str, Any]) -> dict[str, Any]:
    prompt_paths: list[str] = []
    roles = config.get("roles")
    if isinstance(roles, dict):
        for group in ("specialists", "verifiers"):
            values = roles.get(group, [])
            if isinstance(values, list):
                for value in values:
                    if isinstance(value, dict) and value.get("prompt_path"):
                        prompt_paths.append(str(value["prompt_path"]))
        chair = roles.get("chair")
        if isinstance(chair, dict) and chair.get("prompt_path"):
            prompt_paths.append(str(chair["prompt_path"]))

    files: list[dict[str, str]] = []
    for relative in dict.fromkeys(prompt_paths):
        path = safe_base_file(base_root, relative)
        if not path.is_file():
            raise ReviewError(f"Configured Agent prompt is missing at base: {relative}")
        files.append(
            {"path": relative, "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        )

    script_path = Path(__file__).resolve()
    files.append(
        {
            "path": ".github/scripts/agent_review.py",
            "sha256": hashlib.sha256(script_path.read_bytes()).hexdigest(),
        }
    )
    material = {
        "schema_version": SCHEMA_VERSION,
        "config_sha256": sha256_text(canonical_json(config)),
        "files": sorted(files, key=lambda item: item["path"]),
    }
    return {**material, "protocol_sha256": sha256_text(canonical_json(material))}


def clip_text(value: str, limit: int, label: str, omissions: list[str]) -> str:
    if len(value) <= limit:
        return value
    omissions.append(f"{label}: clipped from {len(value)} to {limit} characters")
    marker = "\n[context clipped by trusted builder]"
    if limit <= len(marker):
        return marker[:limit]
    return value[: limit - len(marker)] + marker


def numbered_text(value: str, start: int = 1) -> str:
    return "\n".join(
        f"{number:6d} {line}" for number, line in enumerate(value.splitlines(), start)
    )


def dynamic_hunks(patch: str, content: str, before: int = 8, after: int = 3) -> str:
    lines = content.splitlines()
    ranges: list[tuple[int, int]] = []
    for patch_line in patch.splitlines():
        match = HUNK_RE.match(patch_line)
        if not match:
            continue
        new_start = max(1, int(match.group(1)))
        new_count = int(match.group(2) or "1")
        start_index = max(0, new_start - 1 - before)
        search_floor = max(0, new_start - 1 - 30)
        for index in range(new_start - 2, search_floor - 1, -1):
            if JAVA_BOUNDARY_RE.match(lines[index]):
                start_index = index
                break
        end_index = min(len(lines), new_start - 1 + max(new_count, 1) + after)
        ranges.append((start_index, end_index))
    if not ranges:
        return numbered_text("\n".join(lines[:200]))
    merged: list[list[int]] = []
    for start, end in sorted(ranges):
        if merged and start <= merged[-1][1] + 2:
            merged[-1][1] = max(merged[-1][1], end)
        else:
            merged.append([start, end])
    chunks = []
    for start, end in merged:
        chunks.append(numbered_text("\n".join(lines[start:end]), start + 1))
    return "\n\n... context gap ...\n\n".join(chunks)


class GitHubClient:
    def __init__(self, token: str, api_url: str = "https://api.github.com") -> None:
        if not token:
            raise ReviewError("GH_TOKEN is required.")
        self.token = token
        self.api_url = api_url.rstrip("/")

    def request(
        self,
        method: str,
        path: str,
        *,
        accept: str = "application/vnd.github+json",
        payload: dict[str, Any] | None = None,
        max_bytes: int = 4 * 1024 * 1024,
    ) -> tuple[bytes, dict[str, str]]:
        url = path if path.startswith("http") else f"{self.api_url}/{path.lstrip('/')}"
        data = None if payload is None else canonical_json(payload).encode("utf-8")
        request = urllib.request.Request(
            url,
            method=method,
            data=data,
            headers={
                "Accept": accept,
                "Authorization": f"Bearer {self.token}",
                "X-GitHub-Api-Version": "2022-11-28",
                "User-Agent": "coco-agent-review-jury",
                "Content-Type": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                body = response.read(max_bytes + 1)
                if len(body) > max_bytes:
                    raise ReviewError("GitHub API response exceeded the bounded size.")
                headers = {
                    key.lower(): value for key, value in response.headers.items()
                }
                return body, headers
        except urllib.error.HTTPError as exc:
            detail = ""
            try:
                error_body = exc.read(4097)
                if len(error_body) <= 4096:
                    error_payload = json.loads(error_body)
                    if isinstance(error_payload, dict) and isinstance(
                        error_payload.get("message"), str
                    ):
                        detail = " " + error_payload["message"].replace("\n", " ")[:300]
            except (OSError, UnicodeDecodeError, json.JSONDecodeError):
                pass
            if exc.code == 404:
                raise GitHubNotFoundError(
                    f"GitHub API returned HTTP 404 for {method} {path}.{detail}"
                ) from exc
            raise ReviewError(
                f"GitHub API returned HTTP {exc.code} for {method} {path}.{detail}"
            ) from exc
        except urllib.error.URLError as exc:
            raise ReviewError(
                f"GitHub API request failed for {method} {path}."
            ) from exc

    def get_json(self, path: str) -> Any:
        body, _ = self.request("GET", path)
        try:
            return json.loads(body)
        except json.JSONDecodeError as exc:
            raise ReviewError("GitHub API returned invalid JSON.") from exc

    def get_raw(self, path: str, accept: str, max_bytes: int) -> bytes:
        body, _ = self.request("GET", path, accept=accept, max_bytes=max_bytes)
        return body

    def paginate(self, path: str, limit: int = 1000) -> list[Any]:
        separator = "&" if "?" in path else "?"
        page = 1
        values: list[Any] = []
        while True:
            batch = self.get_json(f"{path}{separator}per_page=100&page={page}")
            if not isinstance(batch, list):
                raise ReviewError("GitHub paginated endpoint did not return an array.")
            values.extend(batch)
            if len(values) > limit:
                raise ReviewError("GitHub paginated response exceeded the item limit.")
            if len(batch) < 100:
                return values
            page += 1

    def send_json(self, method: str, path: str, payload: dict[str, Any]) -> Any:
        body, _ = self.request(method, path, payload=payload)
        if not body:
            return None
        try:
            return json.loads(body)
        except json.JSONDecodeError as exc:
            raise ReviewError("GitHub API write returned invalid JSON.") from exc

    def file_text(
        self, repository: str, path: str, ref: str, max_bytes: int
    ) -> str | None:
        encoded_path = urllib.parse.quote(path, safe="/")
        encoded_ref = urllib.parse.quote(ref, safe="")
        try:
            payload = self.get_json(
                f"repos/{repository}/contents/{encoded_path}?ref={encoded_ref}"
            )
        except GitHubNotFoundError:
            return None
        if not isinstance(payload, dict) or payload.get("encoding") != "base64":
            return None
        try:
            raw = base64.b64decode(str(payload.get("content", "")), validate=False)
        except (ValueError, TypeError):
            return None
        if len(raw) > max_bytes or b"\x00" in raw:
            return None
        try:
            return raw.decode("utf-8")
        except UnicodeDecodeError:
            return None


def parse_pom(path: Path, relative: str) -> dict[str, Any] | None:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError):
        return None
    namespace = ""
    if root.tag.startswith("{"):
        namespace = root.tag.split("}", 1)[0] + "}"

    def text_at(parent: ET.Element, name: str) -> str:
        node = parent.find(f"{namespace}{name}")
        return "" if node is None or node.text is None else node.text.strip()

    artifact = text_at(root, "artifactId")
    modules_node = root.find(f"{namespace}modules")
    modules = (
        []
        if modules_node is None
        else [
            (node.text or "").strip()
            for node in modules_node.findall(f"{namespace}module")
            if (node.text or "").strip()
        ]
    )
    dependencies: list[str] = []
    dependencies_node = root.find(f"{namespace}dependencies")
    if dependencies_node is not None:
        for dependency in dependencies_node.findall(f"{namespace}dependency"):
            dep_artifact = text_at(dependency, "artifactId")
            if dep_artifact.startswith("coco-"):
                dependencies.append(dep_artifact)
    return {
        "path": relative,
        "artifact_id": artifact,
        "modules": modules,
        "coco_dependencies": sorted(set(dependencies)),
    }


def module_map(base_root: Path, max_modules: int = 80) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    queue = [Path("pom.xml")]
    seen: set[Path] = set()
    while queue and len(result) < max_modules:
        relative = queue.pop(0)
        if relative in seen:
            continue
        seen.add(relative)
        parsed = parse_pom(base_root / relative, relative.as_posix())
        if not parsed:
            continue
        result.append(parsed)
        parent = relative.parent
        for module in parsed["modules"]:
            queue.append(parent / module / "pom.xml")
    return result


def corresponding_test(path: str) -> str | None:
    marker = "/src/main/java/"
    if marker not in f"/{path}":
        return None
    prefix, suffix = path.split("src/main/java/", 1)
    if not suffix.endswith(".java"):
        return None
    return f"{prefix}src/test/java/{suffix[:-5]}Test.java"


def nearest_module_pom(base_root: Path, file_path: str) -> str | None:
    current = safe_base_file(base_root, file_path).parent
    root = base_root.resolve()
    while current == root or root in current.parents:
        pom = current / "pom.xml"
        if pom.is_file():
            return pom.relative_to(root).as_posix()
        if current == root:
            return None
        current = current.parent
    return None


def collect_policy(
    base_root: Path,
    config: dict[str, Any],
    changed_paths: list[str],
    omissions: list[str],
) -> list[dict[str, str]]:
    context_config = config.get("context", {})
    paths: list[str] = list(
        context_config.get(
            "always", config.get("protected_policy_paths", ["AGENTS.md"])
        )
    )
    required_paths = set(paths)
    path_rules = list(context_config.get("path_rules", []))
    path_rules.extend(
        {
            "patterns": mapping.get("path_globs", []),
            "files": mapping.get("spec_paths", []),
        }
        for mapping in config.get("spec_path_mappings", [])
        if isinstance(mapping, dict)
    )
    for rule in path_rules:
        patterns = rule.get("patterns", [])
        if any(
            fnmatch.fnmatch(path, pattern)
            for path in changed_paths
            for pattern in patterns
        ):
            paths.extend(rule.get("files", []))
    unique_paths = list(dict.fromkeys(paths))
    limit = normalized_limits(config)["policy_chars"]
    sources: list[dict[str, str]] = []
    used = 0
    for relative in unique_paths:
        path = safe_base_file(base_root, str(relative))
        if not path.is_file():
            if str(relative) in required_paths:
                raise ReviewError(
                    f"Required trusted policy is missing at base: {relative}"
                )
            omissions.append(f"trusted policy missing at base: {relative}")
            continue
        content = path.read_text(encoding="utf-8", errors="replace")
        remaining = limit - used
        if remaining <= 0:
            if str(relative) in required_paths:
                raise ReviewError(
                    f"Required trusted policy exceeds the context budget: {relative}"
                )
            omissions.append(f"trusted policy omitted by budget: {relative}")
            continue
        if str(relative) in required_paths and len(content) > remaining:
            raise ReviewError(
                f"Required trusted policy exceeds the context budget: {relative}"
            )
        clipped = clip_text(content, remaining, f"trusted policy {relative}", omissions)
        sources.append({"source": str(relative), "content": clipped})
        used += len(clipped)
    return sources


def build_code_contexts(
    client: GitHubClient,
    repository: str,
    head_sha: str,
    base_root: Path,
    files: list[dict[str, Any]],
    config: dict[str, Any],
    omissions: list[str],
) -> list[dict[str, Any]]:
    limits = normalized_limits(config)
    total_limit = int(limits.get("code_context_chars", 20000))
    per_file = int(limits.get("per_file_chars", 12000))
    full_file = int(limits.get("full_file_chars", 16000))
    contexts: list[dict[str, Any]] = []
    used = 0
    added: set[str] = set()

    def add_context(
        source: str, kind: str, content: str, line_count: int | None = None
    ) -> None:
        nonlocal used
        if source in added or not content:
            return
        remaining = total_limit - used
        if remaining <= 0:
            omissions.append(f"code context omitted by budget: {source}")
            return
        clipped = clip_text(
            content, min(per_file, remaining), f"code context {source}", omissions
        )
        item: dict[str, Any] = {"source": source, "kind": kind, "content": clipped}
        if line_count is not None:
            item["line_count"] = line_count
        contexts.append(item)
        added.add(source)
        used += len(clipped)

    for entry in files:
        filename = str(entry.get("filename", ""))
        if not filename or Path(filename).suffix.lower() not in TEXT_SUFFIXES:
            omissions.append(f"binary or unsupported changed file: {filename}")
            continue
        status = str(entry.get("status", ""))
        patch = str(entry.get("patch") or "")
        content: str | None
        if status == "removed":
            base_path = safe_base_file(base_root, filename)
            content = (
                base_path.read_text(encoding="utf-8", errors="replace")
                if base_path.is_file()
                else None
            )
            kind = "base-removed-file"
        else:
            content = client.file_text(repository, filename, head_sha, max_bytes=256000)
            kind = "head-file"
        if content is None:
            omissions.append(f"changed file content unavailable: {filename}")
        elif len(content) <= full_file:
            add_context(
                filename, kind, numbered_text(content), len(content.splitlines())
            )
        else:
            add_context(
                filename,
                f"{kind}-dynamic-hunks",
                dynamic_hunks(patch, content),
                len(content.splitlines()),
            )

        test_path = corresponding_test(filename)
        if test_path:
            candidate = safe_base_file(base_root, test_path)
            if candidate.is_file():
                test_content = candidate.read_text(encoding="utf-8", errors="replace")
                add_context(
                    test_path,
                    "related-base-test",
                    numbered_text(test_content),
                    len(test_content.splitlines()),
                )

        pom_path = nearest_module_pom(base_root, filename)
        if pom_path:
            candidate = safe_base_file(base_root, pom_path)
            add_context(
                pom_path,
                "module-pom",
                numbered_text(candidate.read_text(encoding="utf-8", errors="replace")),
            )

        path_parts = PurePosixPath(filename).parts
        if "src" in path_parts:
            module_root = PurePosixPath(*path_parts[: path_parts.index("src")])
            resources = [
                module_root
                / "src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
                module_root
                / "src/main/resources/META-INF/additional-spring-configuration-metadata.json",
            ]
            for resource in resources:
                candidate = safe_base_file(base_root, resource.as_posix())
                if candidate.is_file():
                    value = candidate.read_text(encoding="utf-8", errors="replace")
                    add_context(
                        resource.as_posix(),
                        "related-base-resource",
                        numbered_text(value),
                    )
    return contexts


def current_maintainer_approval(
    client: GitHubClient,
    repository: str,
    pr_number: int,
    head_sha: str,
) -> tuple[bool, list[str]]:
    reviews = client.paginate(
        f"repos/{repository}/pulls/{pr_number}/reviews", limit=500
    )
    latest: dict[str, dict[str, Any]] = {}
    for review in reviews:
        user = review.get("user") or {}
        login = str(user.get("login", ""))
        if not login or user.get("type") == "Bot" or login.endswith("[bot]"):
            continue
        latest[login] = review
    approvers: list[str] = []
    for login, review in latest.items():
        if review.get("state") != "APPROVED" or review.get("commit_id") != head_sha:
            continue
        permission = client.get_json(
            f"repos/{repository}/collaborators/{login}/permission"
        )
        if str(permission.get("permission", "")) in {"write", "maintain", "admin"}:
            approvers.append(login)
    return bool(approvers), sorted(approvers)


def build_context(
    client: GitHubClient,
    repository: str,
    pr: dict[str, Any],
    files: list[dict[str, Any]],
    commits: list[dict[str, Any]],
    diff_text: str,
    base_root: Path,
    config: dict[str, Any],
) -> dict[str, Any]:
    limits = normalized_limits(config)
    max_diff = int(limits.get("diff_chars", 60000))
    if len(diff_text) > max_diff:
        raise ReviewError(
            f"PR diff has {len(diff_text)} characters; split the PR before Agent review."
        )
    omissions: list[str] = []
    bounded_diff = clip_text(
        diff_text,
        int(limits.get("patch_chars", 48000)),
        "PR diff",
        omissions,
    )
    changed_paths = [str(entry.get("filename", "")) for entry in files]
    policy = collect_policy(base_root, config, changed_paths, omissions)
    intent_limit = int(limits.get("intent_chars", 8000))
    title = str(pr.get("title") or "")
    body = str(pr.get("body") or "")
    commit_messages = [
        str((entry.get("commit") or {}).get("message") or "") for entry in commits[:20]
    ]
    intent = clip_text(
        canonical_json(
            {"title": title, "body": body, "commit_messages": commit_messages}
        ),
        intent_limit,
        "PR intent",
        omissions,
    )
    code_contexts = build_code_contexts(
        client,
        repository,
        str(pr["head"]["sha"]),
        base_root,
        files,
        config,
        omissions,
    )
    manifest = [
        {
            "filename": str(entry.get("filename", "")),
            "status": str(entry.get("status", "")),
            "additions": int(entry.get("additions") or 0),
            "deletions": int(entry.get("deletions") or 0),
            "changes": int(entry.get("changes") or 0),
        }
        for entry in files
    ]
    protocol = protocol_manifest(base_root, config)
    context: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "binding": {
            "repository": repository,
            "pr_number": int(pr["number"]),
            "base_sha": str(pr["base"]["sha"]),
            "head_sha": str(pr["head"]["sha"]),
            "protocol_sha256": protocol["protocol_sha256"],
            "context_sha256": "",
        },
        "trusted": {
            "policy": policy,
            "module_map": module_map(base_root),
            "protocol": protocol,
        },
        "untrusted": {
            "intent_json": intent,
            "manifest": manifest,
            "diff": bounded_diff,
            "code_contexts": code_contexts,
        },
        "omissions": omissions,
    }
    max_context = int(limits.get("assembled_context_chars", 96000))
    while (
        len(canonical_json(context)) > max_context
        and context["untrusted"]["code_contexts"]
    ):
        removed = context["untrusted"]["code_contexts"].pop()
        context["omissions"].append(
            f"code context removed by total budget: {removed['source']}"
        )
    if len(canonical_json(context)) > max_context:
        raise ReviewError(
            "Mandatory Agent review context exceeds the configured budget."
        )
    bind_context(context)
    validate_context(context)
    return context


def classify_pr(pr: dict[str, Any], repository: str) -> bool:
    head_repo = str(((pr.get("head") or {}).get("repo") or {}).get("full_name") or "")
    user = pr.get("user") or {}
    login = str(user.get("login") or "")
    return (
        head_repo == repository
        and user.get("type") != "Bot"
        and not login.endswith("[bot]")
    )


def command_prepare(args: argparse.Namespace) -> int:
    config = load_config(args.config)
    client = GitHubClient(
        os.environ.get("GH_TOKEN", ""),
        os.environ.get("GITHUB_API_URL", "https://api.github.com"),
    )
    pr = client.get_json(f"repos/{args.repository}/pulls/{args.pr_number}")
    if pr.get("state") != "open" or (pr.get("base") or {}).get("ref") != "main":
        raise ReviewError(
            "Agent review accepts only open pull requests targeting main."
        )
    base_sha = str((pr.get("base") or {}).get("sha") or "")
    head_sha = str((pr.get("head") or {}).get("sha") or "")
    if not SHA_RE.fullmatch(base_sha) or not SHA_RE.fullmatch(head_sha):
        raise ReviewError("GitHub returned invalid PR commit SHAs.")
    if args.expected_head_sha and args.expected_head_sha != head_sha:
        raise ReviewError("The dispatched head SHA does not match the pull request.")

    files = client.paginate(
        f"repos/{args.repository}/pulls/{args.pr_number}/files", limit=500
    )
    if args.event_name == "repository_dispatch":
        author = str((pr.get("user") or {}).get("login") or "")
        head_repo = str(
            (((pr.get("head") or {}).get("repo") or {}).get("full_name")) or ""
        )
        filenames = sorted(str(item.get("filename", "")) for item in files)
        if (
            (pr.get("head") or {}).get("ref") != "automation/readme-insights"
            or author != "github-actions[bot]"
            or head_repo != args.repository
            or filenames != ["README.md", "README_CN.md"]
            or not args.expected_head_sha
        ):
            raise ReviewError(
                "repository_dispatch is reserved for the bound README automation PR."
            )

    trusted = classify_pr(pr, args.repository)
    ignored = args.event_name == "pull_request_review" and trusted
    approved = False
    approvers: list[str] = []
    context_sha = ""
    if trusted and not ignored:
        commits = client.paginate(
            f"repos/{args.repository}/pulls/{args.pr_number}/commits", limit=250
        )
        diff_bytes = client.get_raw(
            f"repos/{args.repository}/pulls/{args.pr_number}",
            "application/vnd.github.v3.diff",
            max_bytes=1024 * 1024,
        )
        diff_text = diff_bytes.decode("utf-8", errors="replace")
        context = build_context(
            client,
            args.repository,
            pr,
            files,
            commits,
            diff_text,
            args.base_root,
            config,
        )
        latest = client.get_json(f"repos/{args.repository}/pulls/{args.pr_number}")
        if (latest.get("base") or {}).get("sha") != base_sha or (
            latest.get("head") or {}
        ).get("sha") != head_sha:
            raise ReviewError(
                "Pull request changed while Agent context was being built."
            )
        write_json(args.context_output, context)
        context_sha = str(context["binding"]["context_sha256"])
    elif not trusted:
        approved, approvers = current_maintainer_approval(
            client, args.repository, args.pr_number, head_sha
        )

    metadata = {
        "schema_version": SCHEMA_VERSION,
        "repository": args.repository,
        "pr_number": args.pr_number,
        "base_sha": base_sha,
        "head_sha": head_sha,
        "trusted": trusted,
        "ignored": ignored,
        "maintainer_approved": approved,
        "maintainer_approvers": approvers,
        "context_sha256": context_sha,
        "protocol_sha256": (
            str(context["binding"]["protocol_sha256"])
            if trusted and not ignored
            else ""
        ),
        "run_id": os.environ.get("GITHUB_RUN_ID", "0"),
        "run_attempt": os.environ.get("GITHUB_RUN_ATTEMPT", "0"),
    }
    write_json(args.metadata_output, metadata)
    print(canonical_json(metadata))
    return 0


class AnthropicClient:
    def __init__(self, config: dict[str, Any]) -> None:
        key = os.environ.get("ANTHROPIC_API_KEY", "")
        if not key:
            raise ReviewError("ANTHROPIC_API_KEY is required.")
        base_url = os.environ.get(
            "ANTHROPIC_BASE_URL", "https://api.anthropic.com"
        ).rstrip("/")
        parsed_url = urllib.parse.urlparse(base_url)
        if (
            parsed_url.scheme != "https"
            or not parsed_url.netloc
            or parsed_url.username is not None
            or parsed_url.password is not None
            or parsed_url.query
            or parsed_url.fragment
        ):
            raise ReviewError(
                "ANTHROPIC_BASE_URL must be an HTTPS origin or /v1 endpoint without credentials or query data."
            )
        if not base_url.endswith("/v1"):
            base_url += "/v1"
        self.endpoint = f"{base_url}/messages"
        self.key = key
        self.model = os.environ.get("CLAUDE_MODEL", "claude-sonnet-4-6")
        limits = normalized_limits(config)
        self.max_response_bytes = limits["response_bytes"]
        self.timeout = limits["request_timeout_seconds"]

    def complete(self, system: str, user: str, max_tokens: int) -> dict[str, Any]:
        payload = canonical_json(
            {
                "model": self.model,
                "max_tokens": max_tokens,
                "temperature": 0,
                "system": system,
                "messages": [{"role": "user", "content": user}],
            }
        ).encode("utf-8")
        request = urllib.request.Request(
            self.endpoint,
            method="POST",
            data=payload,
            headers={
                "x-api-key": self.key,
                "anthropic-version": "2023-06-01",
                "content-type": "application/json",
                "user-agent": "coco-agent-review-jury",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                body = response.read(self.max_response_bytes + 1)
                if len(body) > self.max_response_bytes:
                    raise ReviewError("Anthropic response exceeded the bounded size.")
        except urllib.error.HTTPError as exc:
            raise ReviewError(f"Anthropic API returned HTTP {exc.code}.") from exc
        except urllib.error.URLError as exc:
            raise ReviewError("Anthropic API transport failed.") from exc
        try:
            envelope = json.loads(body)
        except json.JSONDecodeError as exc:
            raise ReviewError("Anthropic API returned invalid JSON.") from exc
        if not isinstance(envelope, dict) or not isinstance(
            envelope.get("content"), list
        ):
            raise ReviewError("Anthropic API returned an invalid response envelope.")
        stop_reason = envelope.get("stop_reason")
        if stop_reason != "end_turn":
            raise ReviewError(
                f"Anthropic response did not complete (stop_reason={stop_reason!r})."
            )
        if any(
            item.get("type") == "refusal"
            for item in envelope["content"]
            if isinstance(item, dict)
        ):
            raise ReviewError("Anthropic refused the review.")
        text = "\n\n".join(
            str(item.get("text"))
            for item in envelope["content"]
            if isinstance(item, dict)
            and item.get("type") == "text"
            and isinstance(item.get("text"), str)
        ).strip()
        if not text:
            raise ReviewError("Anthropic response contained no text.")
        try:
            value = json.loads(text)
        except json.JSONDecodeError as exc:
            raise ReviewError("Agent output was not strict JSON.") from exc
        if not isinstance(value, dict):
            raise ReviewError("Agent output must be a JSON object.")
        return value


def role_map(config: dict[str, Any], key: str) -> dict[str, dict[str, Any]]:
    values = config.get(key, config.get("roles", {}).get(key))
    if not isinstance(values, list):
        raise ReviewError(f"Config {key} must be an array.")
    result: dict[str, dict[str, Any]] = {}
    for value in values:
        if not isinstance(value, dict) or not ROLE_RE.fullmatch(
            str(value.get("id", ""))
        ):
            raise ReviewError(f"Config {key} contains an invalid role.")
        role_id = str(value["id"])
        if role_id in result:
            raise ReviewError(f"Config {key} contains duplicate role id: {role_id}")
        result[role_id] = value
    return result


def prompt_text(root: Path, name: str, configured_path: str | None = None) -> str:
    relative = f"prompts/{name}.md"
    if configured_path:
        prefix = ".github/agent-review/"
        normalized = PurePosixPath(configured_path).as_posix()
        if not normalized.startswith(prefix):
            raise ReviewError(
                f"Configured prompt must stay under {prefix}: {configured_path}"
            )
        relative = normalized[len(prefix) :]
    path = safe_base_file(root, relative)
    if not path.is_file():
        raise ReviewError(f"Prompt file is missing: {name}.md")
    return path.read_text(encoding="utf-8")


def trusted_policy_text(context: dict[str, Any]) -> str:
    sources = context.get("trusted", {}).get("policy", [])
    return "\n\n".join(
        f"### Source: {item['source']}\n{item['content']}"
        for item in sources
        if isinstance(item, dict) and item.get("source") and item.get("content")
    )


def untrusted_context(context: dict[str, Any], blind: bool = False) -> dict[str, Any]:
    value = copy.deepcopy(context)
    value.pop("binding", None)
    value.get("trusted", {}).pop("policy", None)
    if blind:
        value.get("untrusted", {})["intent_json"] = "[withheld from blind reviewer]"
    return value


def context_file_set(context: dict[str, Any]) -> set[str]:
    paths = {
        str(item.get("filename", ""))
        for item in context.get("untrusted", {}).get("manifest", [])
        if isinstance(item, dict)
    }
    paths.update(
        str(item.get("source", ""))
        for item in context.get("untrusted", {}).get("code_contexts", [])
        if isinstance(item, dict)
    )
    return {path for path in paths if path}


def require_string(value: Any, field: str, minimum: int = 1) -> str:
    if not isinstance(value, str) or len(value.strip()) < minimum:
        raise ReviewError(f"Agent field {field} must be a non-empty string.")
    return value.strip()


def require_exact_fields(value: dict[str, Any], expected: set[str], label: str) -> None:
    actual = set(value)
    if actual != expected:
        missing = sorted(expected - actual)
        unexpected = sorted(actual - expected)
        raise ReviewError(
            f"{label} schema fields mismatch (missing={missing}, unexpected={unexpected})."
        )


def validate_specialist_report(
    report: dict[str, Any],
    role: str,
    context: dict[str, Any],
    max_findings: int,
    max_questions: int = 5,
    max_context_gaps: int = 10,
) -> dict[str, Any]:
    require_exact_fields(
        report,
        {
            "schema_version",
            "role",
            "head_sha",
            "context_sha256",
            "findings",
            "questions",
            "context_gaps",
        },
        f"Specialist {role}",
    )
    binding = context["binding"]
    if report.get("schema_version") != SCHEMA_VERSION or report.get("role") != role:
        raise ReviewError(f"Specialist report identity mismatch for {role}.")
    if (
        report.get("head_sha") != binding["head_sha"]
        or report.get("context_sha256") != binding["context_sha256"]
    ):
        raise ReviewError(f"Specialist report binding mismatch for {role}.")
    findings = report.get("findings")
    if not isinstance(findings, list) or len(findings) > max_findings:
        raise ReviewError(f"Specialist {role} returned an invalid findings array.")
    allowed_files = context_file_set(context)
    seen: set[str] = set()
    for index, finding in enumerate(findings, 1):
        if not isinstance(finding, dict):
            raise ReviewError(f"Specialist {role} finding must be an object.")
        require_exact_fields(
            finding,
            {
                "id",
                "severity",
                "category",
                "file",
                "start_line",
                "end_line",
                "title",
                "claim",
                "trigger",
                "impact",
                "evidence",
                "verification",
                "confidence",
            },
            f"Specialist {role} finding",
        )
        finding_id = require_string(finding.get("id"), "id")
        if finding_id != f"{role}:f{index}" or finding_id in seen:
            raise ReviewError(
                f"Specialist {role} finding IDs must be contiguous and unique."
            )
        seen.add(finding_id)
        severity = finding.get("severity")
        if severity not in {"P0", "P1", "P2", "P3"}:
            raise ReviewError(f"Specialist {role} returned an invalid severity.")
        filename = require_string(finding.get("file"), "file")
        if filename not in allowed_files:
            raise ReviewError(
                f"Specialist {role} cited a file absent from its context: {filename}"
            )
        start = finding.get("start_line")
        end = finding.get("end_line")
        if (
            not isinstance(start, int)
            or not isinstance(end, int)
            or start < 1
            or end < start
        ):
            raise ReviewError(f"Specialist {role} returned invalid line anchors.")
        category = require_string(finding.get("category"), "category", 3)
        if not ROLE_RE.fullmatch(category):
            raise ReviewError(f"Specialist {role} returned an invalid category.")
        for field in ("title", "claim", "impact", "evidence", "verification"):
            require_string(finding.get(field), field, 3)
        if severity in {"P0", "P1"}:
            require_string(finding.get("trigger"), "trigger", 8)
        elif not isinstance(finding.get("trigger"), str):
            raise ReviewError(f"Specialist {role} trigger must be a string.")
        confidence = finding.get("confidence")
        if not isinstance(confidence, int) or not 0 <= confidence <= 100:
            raise ReviewError(f"Specialist {role} returned invalid confidence.")
    field_limits = {
        "questions": max_questions,
        "context_gaps": max_context_gaps,
    }
    for field, maximum in field_limits.items():
        values = report.get(field)
        if (
            not isinstance(values, list)
            or len(values) > maximum
            or any(not isinstance(value, str) or not value.strip() for value in values)
        ):
            raise ReviewError(
                f"Specialist {role} field {field} must be a string array."
            )
    return report


def command_specialist(args: argparse.Namespace) -> int:
    config = load_config(args.config)
    context = read_json(args.context)
    validate_context(context)
    roles = role_map(config, "specialists")
    if args.role not in roles:
        raise ReviewError(f"Unknown specialist role: {args.role}")
    role = roles[args.role]
    blind_intent = bool(
        role.get("blind_intent")
        or role.get("include_pr_intent") is False
        or (role.get("intent_isolation") or {}).get("enabled")
    )
    payload = canonical_json(untrusted_context(context, blind_intent))
    protected_task = {
        "binding": context["binding"],
        "role": args.role,
        "input_sha256": sha256_text(payload),
    }
    system = "\n\n".join(
        [
            prompt_text(args.prompt_root, "specialist", role.get("prompt_path")),
            f"## Protected task metadata\n{canonical_json(protected_task)}",
            f"## Assigned role\nID: {args.role}\nFocus: {role.get('focus', role.get('lens', ''))}",
            f"## Trusted Coco policy\n{trusted_policy_text(context)}",
        ]
    )
    limits = normalized_limits(config)
    max_tokens = int(role.get("max_tokens", limits["specialist_tokens"]))
    report = AnthropicClient(config).complete(system, payload, max_tokens)
    validate_specialist_report(
        report,
        args.role,
        context,
        limits["max_findings_per_agent"],
        limits["max_questions_per_agent"],
        limits["max_context_gaps_per_agent"],
    )
    write_json(args.output, report)
    return 0


def load_reports(directory: Path) -> list[dict[str, Any]]:
    if not directory.is_dir():
        raise ReviewError(f"Report directory does not exist: {directory}")
    paths = sorted(directory.rglob("*.json"))
    if not paths:
        raise ReviewError(f"No JSON reports found under {directory}")
    return [read_json(path) for path in paths]


def require_complete_role_set(
    reports: list[dict[str, Any]], expected_roles: set[str], label: str
) -> None:
    actual_roles = [str(report.get("role")) for report in reports]
    if len(actual_roles) != len(expected_roles) or set(actual_roles) != expected_roles:
        raise ReviewError(
            f"{label} report set is incomplete or contains duplicate roles."
        )


def high_findings(reports: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        finding
        for report in reports
        for finding in report.get("findings", [])
        if finding.get("severity") in {"P0", "P1"}
    ]


def validate_cross_report(
    report: dict[str, Any],
    role: str,
    context: dict[str, Any],
    finding_ids: set[str],
    max_context_gaps: int = 10,
) -> dict[str, Any]:
    raw_schema = "verifications" in report and "reviews" not in report
    if raw_schema:
        require_exact_fields(
            report,
            {
                "schema_version",
                "role",
                "head_sha",
                "context_sha256",
                "evidence",
                "verifications",
                "context_gaps",
            },
            f"Cross-review {role}",
        )
    else:
        require_exact_fields(
            report,
            {
                "schema_version",
                "role",
                "head_sha",
                "context_sha256",
                "status",
                "evidence",
                "reviews",
                "context_gaps",
            },
            f"Cross-review {role}",
        )
    binding = context["binding"]
    if report.get("schema_version") != SCHEMA_VERSION or report.get("role") != role:
        raise ReviewError(f"Cross-review report identity mismatch for {role}.")
    if (
        report.get("head_sha") != binding["head_sha"]
        or report.get("context_sha256") != binding["context_sha256"]
    ):
        raise ReviewError(f"Cross-review report binding mismatch for {role}.")
    report_evidence = require_string(report.get("evidence"), "evidence", 8)
    reviews = report.get("verifications") if raw_schema else report.get("reviews")
    if not isinstance(reviews, list):
        raise ReviewError(f"Cross-review {role} verifications must be an array.")
    seen: set[str] = set()
    normalized: list[dict[str, Any]] = []
    for review in reviews:
        if not isinstance(review, dict):
            raise ReviewError(f"Cross-review {role} entry must be an object.")
        if raw_schema:
            require_exact_fields(
                review,
                {"finding_id", "status", "reason", "evidence", "verification"},
                f"Cross-review {role} verification",
            )
        else:
            require_exact_fields(
                review,
                {"finding_id", "action", "reason", "evidence", "verification"},
                f"Cross-review {role} verification",
            )
        finding_id = require_string(review.get("finding_id"), "finding_id")
        if finding_id not in finding_ids or finding_id in seen:
            raise ReviewError(
                f"Cross-review {role} referenced an unknown or duplicate finding."
            )
        seen.add(finding_id)
        action = review.get("status") if raw_schema else review.get("action")
        if action not in {"AGREE", "DISAGREE", "UNVERIFIED"}:
            raise ReviewError(f"Cross-review {role} returned an invalid action.")
        evidence = require_string(review.get("evidence"), "evidence", 8)
        reason = require_string(review.get("reason"), "reason", 8)
        verification = require_string(review.get("verification"), "verification", 8)
        normalized.append(
            {
                "finding_id": finding_id,
                "action": action,
                "reason": reason,
                "evidence": evidence,
                "verification": verification,
            }
        )
    if seen != finding_ids:
        raise ReviewError(f"Cross-review {role} did not address every P0/P1 finding.")
    context_gaps = report.get("context_gaps")
    if (
        not isinstance(context_gaps, list)
        or len(context_gaps) > max_context_gaps
        or any(
            not isinstance(value, str) or not value.strip() for value in context_gaps
        )
    ):
        raise ReviewError(f"Cross-review {role} context_gaps must be a string array.")
    status = "COMPLETE" if finding_ids else "NOT_NEEDED"
    if not raw_schema and report.get("status") != status:
        raise ReviewError(f"Cross-review {role} returned an invalid status.")
    if raw_schema:
        report.clear()
        report.update(
            {
                "schema_version": SCHEMA_VERSION,
                "role": role,
                "head_sha": binding["head_sha"],
                "context_sha256": binding["context_sha256"],
                "status": status,
                "evidence": report_evidence,
                "reviews": normalized,
                "context_gaps": context_gaps,
            }
        )
    return report


def command_cross(args: argparse.Namespace) -> int:
    config = load_config(args.config)
    context = read_json(args.context)
    validate_context(context)
    specialist_roles = role_map(config, "specialists")
    reports = load_reports(args.reports)
    require_complete_role_set(reports, set(specialist_roles), "Specialist")
    for report in reports:
        limits = normalized_limits(config)
        validate_specialist_report(
            report,
            str(report.get("role")),
            context,
            limits["max_findings_per_agent"],
            limits["max_questions_per_agent"],
            limits["max_context_gaps_per_agent"],
        )
    claims = high_findings(reports)
    finding_ids = {str(finding["id"]) for finding in claims}
    verifiers = role_map(config, "verifiers")
    if args.role not in verifiers:
        raise ReviewError(f"Unknown verifier role: {args.role}")
    verifier = verifiers[args.role]
    user = canonical_json(
        {
            "claims": claims,
            "review_context": untrusted_context(context),
        }
    )
    protected_task = {
        "binding": context["binding"],
        "role": args.role,
        "input_sha256": sha256_text(user),
    }
    system = "\n\n".join(
        [
            prompt_text(args.prompt_root, "cross-review", verifier.get("prompt_path")),
            f"## Protected task metadata\n{canonical_json(protected_task)}",
            f"## Assigned verifier\nID: {args.role}\nFocus: {verifier.get('focus', verifier.get('lens', ''))}",
            f"## Trusted Coco policy\n{trusted_policy_text(context)}",
        ]
    )
    max_tokens = int(
        verifier.get("max_tokens", normalized_limits(config)["verifier_tokens"])
    )
    report = AnthropicClient(config).complete(system, user, max_tokens)
    validate_cross_report(
        report,
        args.role,
        context,
        finding_ids,
        normalized_limits(config)["max_context_gaps_per_agent"],
    )
    write_json(args.output, report)
    return 0


def compute_consensus(
    specialist_reports: list[dict[str, Any]], verifier_reports: list[dict[str, Any]]
) -> dict[str, Any]:
    findings = {
        str(finding["id"]): finding for finding in high_findings(specialist_reports)
    }
    votes = {
        str(report["role"]): {
            str(entry["finding_id"]): entry for entry in report.get("reviews", [])
        }
        for report in verifier_reports
    }
    required = {"evidence-verifier", "policy-skeptic"}
    if len(verifier_reports) != len(required) or set(votes) != required:
        raise ReviewError("Verifier report set is incomplete.")
    result = {"confirmed": [], "challenged": [], "unverified": []}
    for finding_id, finding in findings.items():
        entries = {role: role_votes[finding_id] for role, role_votes in votes.items()}
        actions = {entry["action"] for entry in entries.values()}
        item = {"finding": finding, "verification": entries}
        if actions == {"AGREE"}:
            result["confirmed"].append(item)
        elif "DISAGREE" in actions:
            result["challenged"].append(item)
        else:
            result["unverified"].append(item)
    return result


def validate_chair(
    chair: dict[str, Any],
    consensus: dict[str, Any],
    context: dict[str, Any],
    allowed_followups: set[str] | None = None,
    max_questions: int = 5,
) -> None:
    require_exact_fields(
        chair,
        {
            "schema_version",
            "role",
            "head_sha",
            "context_sha256",
            "verdict",
            "summary",
            "confirmed_blocker_ids",
            "follow_up_finding_ids",
            "questions",
        },
        "Chair report",
    )
    binding = context["binding"]
    if chair.get("schema_version") != SCHEMA_VERSION or chair.get("role") != "chair":
        raise ReviewError("Chair report identity is invalid.")
    if (
        chair.get("head_sha") != binding["head_sha"]
        or chair.get("context_sha256") != binding["context_sha256"]
    ):
        raise ReviewError("Chair report binding is invalid.")
    confirmed = sorted(item["finding"]["id"] for item in consensus["confirmed"])
    chair_ids = chair.get("confirmed_blocker_ids")
    if not isinstance(chair_ids, list) or sorted(chair_ids) != confirmed:
        raise ReviewError(
            "Chair attempted to add, remove, or replace confirmed blockers."
        )
    expected = "BLOCK" if confirmed else "PASS"
    if chair.get("verdict") != expected:
        raise ReviewError("Chair verdict contradicts deterministic consensus.")
    require_string(chair.get("summary"), "summary", 8)
    for field in ("follow_up_finding_ids", "questions"):
        values = chair.get(field)
        if not isinstance(values, list) or any(
            not isinstance(value, str) or not value.strip() for value in values
        ):
            raise ReviewError(f"Chair field {field} must be a string array.")
    if len(chair["questions"]) > max_questions:
        raise ReviewError("Chair returned too many questions.")
    if allowed_followups is not None and not set(
        chair["follow_up_finding_ids"]
    ).issubset(allowed_followups):
        raise ReviewError(
            "Chair referenced an unknown or blocking finding as follow-up work."
        )


def markdown_text(value: Any, maximum: int | None = None) -> str:
    text = str(value).replace("\r", " ").replace("\x00", "")
    text = text.replace("<", "&lt;").replace(">", "&gt;").strip()
    if maximum is not None and len(text) > maximum:
        return text[: max(0, maximum - 3)].rstrip() + "..."
    return text


def render_finding(item: dict[str, Any]) -> str:
    finding = item["finding"]
    return (
        f"- **{markdown_text(finding['severity'])} {markdown_text(finding['title'], 200)}** "
        f"`{markdown_text(finding['file'])}:{finding['start_line']}` "
        f"({markdown_text(finding['id'])})\n"
        f"  {markdown_text(finding['claim'], 500)} Trigger: {markdown_text(finding['trigger'], 350)} "
        f"Impact: {markdown_text(finding['impact'], 500)}"
    )


def render_review(
    context: dict[str, Any],
    specialist_reports: list[dict[str, Any]],
    verifier_reports: list[dict[str, Any]],
    consensus: dict[str, Any],
    chair: dict[str, Any],
) -> str:
    binding = context["binding"]
    specialist_rows = []
    for report in sorted(specialist_reports, key=lambda value: value["role"]):
        count = len(report.get("findings", []))
        high = sum(
            1
            for finding in report.get("findings", [])
            if finding["severity"] in {"P0", "P1"}
        )
        specialist_rows.append(f"| `{report['role']}` | Complete | {count} | {high} |")
    verifier_rows = []
    for report in sorted(verifier_reports, key=lambda value: value["role"]):
        status = report.get("status", "Complete")
        verifier_rows.append(
            f"| `{report['role']}` | {markdown_text(status)} | {len(report.get('reviews', []))} |"
        )
    lines = [
        COMMENT_MARKER,
        "### Agent Review Jury",
        "",
        f"**Verdict: {chair['verdict']}** - {markdown_text(chair['summary'])}",
        "",
        f"Reviewed head: `{binding['head_sha']}`  ",
        f"Protocol SHA-256: `{binding['protocol_sha256']}`  ",
        f"Context SHA-256: `{binding['context_sha256']}`",
        "",
        "#### Specialists",
        "",
        "| Role | Status | Findings | P0/P1 claims |",
        "| --- | --- | ---: | ---: |",
        *specialist_rows,
        "",
        "#### Cross Review",
        "",
        "| Verifier | Status | Claims checked |",
        "| --- | --- | ---: |",
        *verifier_rows,
        "",
        "#### Chair",
        "",
        "| Role | Status | Verdict |",
        "| --- | --- | --- |",
        f"| `chair` | Complete | **{markdown_text(chair['verdict'])}** |",
        "",
        "#### Confirmed Blockers",
        "",
    ]
    if consensus["confirmed"]:
        lines.extend(render_finding(item) for item in consensus["confirmed"])
    else:
        lines.append("No independently confirmed blockers.")
    lower = [
        finding
        for report in specialist_reports
        for finding in report.get("findings", [])
        if finding.get("severity") in {"P2", "P3"}
    ]
    lines.extend(["", "#### Follow-up Findings", ""])
    if lower:
        lines.extend(
            f"- **{markdown_text(finding['severity'])} {markdown_text(finding['title'])}** "
            f"`{markdown_text(finding['file'])}:{finding['start_line']}` ({markdown_text(finding['id'])})"
            for finding in lower
        )
    else:
        lines.append("No P2/P3 findings.")
    questions = list(
        dict.fromkeys(
            [
                question
                for report in specialist_reports
                for question in report.get("questions", [])
            ]
            + list(chair.get("questions", []))
        )
    )
    if questions:
        lines.extend(["", "#### Clarifying Questions", ""])
        lines.extend(f"- {markdown_text(question)}" for question in questions)
    challenged = consensus["challenged"] + consensus["unverified"]
    lines.extend(
        ["", "<details>", "<summary>Challenged or unverified claims</summary>", ""]
    )
    if challenged:
        for item in challenged:
            finding = item["finding"]
            lines.append(
                f"- `{markdown_text(finding['id'])}` {markdown_text(finding['title'])}"
            )
            for role, vote in sorted(item["verification"].items()):
                lines.append(
                    f"  - `{role}`: **{vote['action']}** - {markdown_text(vote['evidence'])}"
                )
    else:
        lines.append("None.")
    lines.extend(
        [
            "",
            "</details>",
            "",
            "<details>",
            "<summary>Context sources and omissions</summary>",
            "",
        ]
    )
    lines.append(
        f"- PR diff: {len(context.get('untrusted', {}).get('diff', ''))} characters"
    )
    lines.append(
        f"- Changed-file manifest: {len(context.get('untrusted', {}).get('manifest', []))} files"
    )
    lines.append(
        f"- Base module map: {len(context.get('trusted', {}).get('module_map', []))} modules"
    )
    for item in context.get("trusted", {}).get("policy", []):
        lines.append(f"- Policy: `{markdown_text(item['source'])}`")
    for item in context.get("untrusted", {}).get("code_contexts", []):
        lines.append(
            f"- Code context: `{markdown_text(item['source'])}` ({markdown_text(item['kind'])})"
        )
    for omission in context.get("omissions", []):
        lines.append(f"- Omitted: {markdown_text(omission)}")
    lines.extend(["", "</details>"])
    return "\n".join(lines).rstrip() + "\n"


def command_chair(args: argparse.Namespace) -> int:
    config = load_config(args.config)
    context = read_json(args.context)
    validate_context(context)
    specialist_reports = load_reports(args.specialists)
    verifier_reports = load_reports(args.verifiers)
    specialists = role_map(config, "specialists")
    verifiers = role_map(config, "verifiers")
    require_complete_role_set(specialist_reports, set(specialists), "Chair specialist")
    require_complete_role_set(verifier_reports, set(verifiers), "Chair verifier")
    limits = normalized_limits(config)
    for report in specialist_reports:
        validate_specialist_report(
            report,
            str(report["role"]),
            context,
            limits["max_findings_per_agent"],
            limits["max_questions_per_agent"],
            limits["max_context_gaps_per_agent"],
        )
    finding_ids = {str(finding["id"]) for finding in high_findings(specialist_reports)}
    for report in verifier_reports:
        validate_cross_report(
            report,
            str(report["role"]),
            context,
            finding_ids,
            limits["max_context_gaps_per_agent"],
        )
    consensus = compute_consensus(specialist_reports, verifier_reports)
    deterministic = {
        "confirmed_blocker_ids": [
            item["finding"]["id"] for item in consensus["confirmed"]
        ],
        "challenged_ids": [item["finding"]["id"] for item in consensus["challenged"]],
        "unverified_ids": [item["finding"]["id"] for item in consensus["unverified"]],
        "required_verdict": "BLOCK" if consensus["confirmed"] else "PASS",
    }
    user = canonical_json(
        {
            "pr_intent": context.get("untrusted", {}).get("intent_json", ""),
            "specialist_reports": specialist_reports,
            "verifier_reports": verifier_reports,
        }
    )
    protected_task = {
        "binding": context["binding"],
        "deterministic_consensus": deterministic,
        "input_sha256": sha256_text(user),
        "role": "chair",
    }
    chair_config = config.get("roles", {}).get("chair", {})
    if not isinstance(chair_config, dict) or chair_config.get("id", "chair") != "chair":
        raise ReviewError("Agent review chair configuration is invalid.")
    system = "\n\n".join(
        [
            prompt_text(args.prompt_root, "chair", chair_config.get("prompt_path")),
            f"## Protected task metadata\n{canonical_json(protected_task)}",
            f"## Assigned chair\nFocus: {chair_config.get('lens', '')}",
            f"## Trusted Coco policy\n{trusted_policy_text(context)}",
        ]
    )
    max_tokens = limits["chair_tokens"]
    chair = AnthropicClient(config).complete(system, user, max_tokens)
    allowed_followups = {
        str(finding["id"])
        for report in specialist_reports
        for finding in report.get("findings", [])
        if finding.get("severity") in {"P2", "P3"}
    }
    validate_chair(
        chair,
        consensus,
        context,
        allowed_followups,
        limits["max_questions_per_agent"],
    )
    final = {
        "schema_version": SCHEMA_VERSION,
        "binding": context["binding"],
        "verdict": chair["verdict"],
        "chair": chair,
        "consensus": consensus,
        "specialist_roles": sorted(specialists),
        "verifier_roles": sorted(verifiers),
    }
    write_json(args.output_json, final)
    args.output_markdown.parent.mkdir(parents=True, exist_ok=True)
    args.output_markdown.write_text(
        render_review(context, specialist_reports, verifier_reports, consensus, chair),
        encoding="utf-8",
    )
    return 0


def validate_final_artifact(
    final: dict[str, Any],
    context: dict[str, Any],
    specialist_reports: list[dict[str, Any]],
    verifier_reports: list[dict[str, Any]],
    config: dict[str, Any],
) -> str:
    require_exact_fields(
        final,
        {
            "schema_version",
            "binding",
            "verdict",
            "chair",
            "consensus",
            "specialist_roles",
            "verifier_roles",
        },
        "Final jury artifact",
    )
    if (
        final.get("schema_version") != SCHEMA_VERSION
        or final.get("binding") != context["binding"]
    ):
        raise ReviewError("Final jury artifact binding is invalid.")

    specialists = role_map(config, "specialists")
    verifiers = role_map(config, "verifiers")
    if final.get("specialist_roles") != sorted(specialists):
        raise ReviewError("Final jury specialist role set is invalid.")
    if final.get("verifier_roles") != sorted(verifiers):
        raise ReviewError("Final jury verifier role set is invalid.")
    require_complete_role_set(specialist_reports, set(specialists), "Final specialist")
    require_complete_role_set(verifier_reports, set(verifiers), "Final verifier")

    limits = normalized_limits(config)
    for report in specialist_reports:
        validate_specialist_report(
            report,
            str(report["role"]),
            context,
            limits["max_findings_per_agent"],
            limits["max_questions_per_agent"],
            limits["max_context_gaps_per_agent"],
        )
    finding_ids = {str(finding["id"]) for finding in high_findings(specialist_reports)}
    for report in verifier_reports:
        validate_cross_report(
            report,
            str(report["role"]),
            context,
            finding_ids,
            limits["max_context_gaps_per_agent"],
        )

    consensus = compute_consensus(specialist_reports, verifier_reports)
    if canonical_json(final.get("consensus")) != canonical_json(consensus):
        raise ReviewError(
            "Final jury consensus does not match the independently recomputed result."
        )
    chair = final.get("chair")
    if not isinstance(chair, dict):
        raise ReviewError("Final jury chair report is invalid.")
    allowed_followups = {
        str(finding["id"])
        for report in specialist_reports
        for finding in report.get("findings", [])
        if finding.get("severity") in {"P2", "P3"}
    }
    validate_chair(
        chair,
        consensus,
        context,
        allowed_followups,
        limits["max_questions_per_agent"],
    )
    if final.get("verdict") != chair["verdict"]:
        raise ReviewError(
            "Final jury verdict does not match the validated chair report."
        )
    return render_review(
        context, specialist_reports, verifier_reports, consensus, chair
    )


def managed_comment_order(body: str) -> tuple[int, int]:
    match = re.search(r"<!-- agent-jury-run:(\d+):(\d+) -->", body)
    if not match:
        return (0, 0)
    return (int(match.group(1)), int(match.group(2)))


def upsert_comment(
    client: GitHubClient,
    repository: str,
    pr_number: int,
    body: str,
    run_order: tuple[int, int],
) -> None:
    comments = client.paginate(
        f"repos/{repository}/issues/{pr_number}/comments", limit=500
    )
    managed = [
        comment
        for comment in comments
        if str((comment.get("user") or {}).get("login") or "") == "github-actions[bot]"
        and str(comment.get("body") or "").startswith(
            (COMMENT_MARKER, LEGACY_COMMENT_MARKER)
        )
    ]
    previous = max(
        managed,
        key=lambda comment: managed_comment_order(str(comment.get("body") or "")),
        default=None,
    )
    if previous:
        if managed_comment_order(str(previous.get("body") or "")) > run_order:
            raise ReviewError(
                "A newer Agent jury run already owns the managed comment."
            )
        client.send_json(
            "PATCH",
            f"repos/{repository}/issues/comments/{previous['id']}",
            {"body": body},
        )
    else:
        client.send_json(
            "POST", f"repos/{repository}/issues/{pr_number}/comments", {"body": body}
        )


def publish_status(
    client: GitHubClient,
    repository: str,
    head_sha: str,
    state: str,
    description: str,
    target_url: str,
) -> None:
    client.send_json(
        "POST",
        f"repos/{repository}/statuses/{head_sha}",
        {
            "state": state,
            "context": STATUS_CONTEXT,
            "description": description[:140],
            "target_url": target_url,
        },
    )


def command_mark_pending(args: argparse.Namespace) -> int:
    metadata = read_json(args.metadata)
    if metadata.get("ignored"):
        return 0
    client = GitHubClient(
        os.environ.get("GH_TOKEN", ""),
        os.environ.get("GITHUB_API_URL", "https://api.github.com"),
    )
    publish_status(
        client,
        str(metadata["repository"]),
        str(metadata["head_sha"]),
        "pending",
        "Agent jury review in progress",
        args.run_url,
    )
    return 0


def command_mark_failed(args: argparse.Namespace) -> int:
    metadata = read_json(args.metadata)
    if metadata.get("ignored"):
        return 0
    client = GitHubClient(
        os.environ.get("GH_TOKEN", ""),
        os.environ.get("GITHUB_API_URL", "https://api.github.com"),
    )
    publish_status(
        client,
        str(metadata["repository"]),
        str(metadata["head_sha"]),
        "failure",
        "Agent jury preparation failed",
        args.run_url,
    )
    return 0


def command_publish(args: argparse.Namespace) -> int:
    metadata = read_json(args.metadata)
    if metadata.get("ignored"):
        print(canonical_json({"state": "ignored"}))
        return 0
    client = GitHubClient(
        os.environ.get("GH_TOKEN", ""),
        os.environ.get("GITHUB_API_URL", "https://api.github.com"),
    )
    repository = str(metadata["repository"])
    pr_number = int(metadata["pr_number"])
    head_sha = str(metadata["head_sha"])

    def require_current_pr() -> dict[str, Any]:
        value = client.get_json(f"repos/{repository}/pulls/{pr_number}")
        if (value.get("head") or {}).get("sha") != head_sha or (
            value.get("base") or {}
        ).get("sha") != metadata["base_sha"]:
            raise ReviewError("Pull request changed before Agent jury publication.")
        return value

    require_current_pr()

    if metadata.get("trusted"):
        required_paths = (
            args.config,
            args.context,
            args.specialists,
            args.verifiers,
            args.final_json,
            args.final_markdown,
        )
        if all(path.exists() for path in required_paths):
            try:
                config = load_config(args.config)
                context = read_json(args.context)
                validate_context(context)
                binding = context["binding"]
                if (
                    binding.get("head_sha") != head_sha
                    or binding.get("base_sha") != metadata.get("base_sha")
                    or binding.get("context_sha256") != metadata.get("context_sha256")
                    or binding.get("protocol_sha256") != metadata.get("protocol_sha256")
                ):
                    raise ReviewError(
                        "Prepared context does not match publication metadata."
                    )
                specialist_reports = load_reports(args.specialists)
                verifier_reports = load_reports(args.verifiers)
                final = read_json(args.final_json)
                review_body = validate_final_artifact(
                    final,
                    context,
                    specialist_reports,
                    verifier_reports,
                    config,
                )
                try:
                    provided_markdown = args.final_markdown.read_text(encoding="utf-8")
                except OSError as exc:
                    raise ReviewError(
                        "Unable to read the rendered jury report."
                    ) from exc
                if provided_markdown != review_body:
                    raise ReviewError(
                        "Rendered jury Markdown does not match validated reports."
                    )
                verdict = final["verdict"]
                state = "success" if verdict == "PASS" else "failure"
                description = (
                    "Agent jury passed"
                    if verdict == "PASS"
                    else "Agent jury confirmed blockers"
                )
            except ReviewError as exc:
                state = "failure"
                description = "Agent jury artifact validation failed"
                review_body = (
                    "\n".join(
                        [
                            COMMENT_MARKER,
                            "### Agent Review Jury",
                            "",
                            "**Verdict: BLOCK**",
                            "",
                            f"The jury artifacts failed deterministic validation: {markdown_text(exc)}",
                        ]
                    )
                    + "\n"
                )
        else:
            state = "failure"
            description = "Agent jury failed closed"
            review_body = (
                "\n".join(
                    [
                        COMMENT_MARKER,
                        "### Agent Review Jury",
                        "",
                        "**Verdict: BLOCK**",
                        "",
                        "The jury did not complete. Inspect the linked workflow run and rerun after fixing the review infrastructure.",
                    ]
                )
                + "\n"
            )
    else:
        approved, approvers = current_maintainer_approval(
            client, repository, pr_number, head_sha
        )
        state = "success" if approved else "pending"
        description = (
            "Maintainer approved no-secret path"
            if approved
            else "Jury skipped; maintainer approval required"
        )
        approver_text = (
            ", ".join(f"`@{markdown_text(login)}`" for login in approvers) or "None yet"
        )
        review_body = (
            "\n".join(
                [
                    COMMENT_MARKER,
                    "### Agent Review Jury",
                    "",
                    "The secret-backed jury was not run because this PR comes from a fork or bot account.",
                    "Repository Anthropic secrets were not exposed.",
                    "",
                    f"Reviewed head: `{head_sha}`  ",
                    f"Current-head maintainer approval: {approver_text}",
                ]
            )
            + "\n"
        )

    try:
        run_order = (
            int(str(metadata.get("run_id", "0"))),
            int(str(metadata.get("run_attempt", "0"))),
        )
    except ValueError as exc:
        raise ReviewError("Agent jury run identity is invalid.") from exc
    timestamp = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()
    run_marker = f"<!-- agent-jury-run:{run_order[0]}:{run_order[1]} -->"
    review_body = review_body.replace(
        COMMENT_MARKER, f"{COMMENT_MARKER}\n{run_marker}", 1
    )
    body = (
        review_body.rstrip()
        + f"\n\n<sub>Updated {timestamp} - [workflow run]({args.run_url})</sub>\n"
    )
    require_current_pr()
    try:
        upsert_comment(client, repository, pr_number, body, run_order)
    except ReviewError:
        require_current_pr()
        publish_status(
            client,
            repository,
            head_sha,
            "failure",
            "Agent jury comment publication failed",
            args.run_url,
        )
        raise
    require_current_pr()
    publish_status(client, repository, head_sha, state, description, args.run_url)
    print(canonical_json({"state": state, "description": description}))
    return 1 if state == "failure" else 0


def command_roles(args: argparse.Namespace) -> int:
    config = load_config(args.config)
    key = "specialists" if args.kind == "specialist" else "verifiers"
    print(
        canonical_json([{"id": role["id"]} for role in role_map(config, key).values()])
    )
    return 0


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description=__doc__)
    commands = result.add_subparsers(dest="command", required=True)

    prepare = commands.add_parser("prepare")
    prepare.add_argument("--repository", required=True)
    prepare.add_argument("--pr-number", required=True, type=int)
    prepare.add_argument("--event-name", required=True)
    prepare.add_argument("--expected-head-sha", default="")
    prepare.add_argument("--base-root", required=True, type=Path)
    prepare.add_argument("--config", required=True, type=Path)
    prepare.add_argument("--context-output", required=True, type=Path)
    prepare.add_argument("--metadata-output", required=True, type=Path)
    prepare.set_defaults(handler=command_prepare)

    specialist = commands.add_parser("specialist")
    specialist.add_argument("--role", required=True)
    specialist.add_argument("--config", required=True, type=Path)
    specialist.add_argument("--prompt-root", required=True, type=Path)
    specialist.add_argument("--context", required=True, type=Path)
    specialist.add_argument("--output", required=True, type=Path)
    specialist.set_defaults(handler=command_specialist)

    cross = commands.add_parser("cross-review")
    cross.add_argument("--role", required=True)
    cross.add_argument("--config", required=True, type=Path)
    cross.add_argument("--prompt-root", required=True, type=Path)
    cross.add_argument("--context", required=True, type=Path)
    cross.add_argument("--reports", required=True, type=Path)
    cross.add_argument("--output", required=True, type=Path)
    cross.set_defaults(handler=command_cross)

    chair = commands.add_parser("chair")
    chair.add_argument("--config", required=True, type=Path)
    chair.add_argument("--prompt-root", required=True, type=Path)
    chair.add_argument("--context", required=True, type=Path)
    chair.add_argument("--specialists", required=True, type=Path)
    chair.add_argument("--verifiers", required=True, type=Path)
    chair.add_argument("--output-json", required=True, type=Path)
    chair.add_argument("--output-markdown", required=True, type=Path)
    chair.set_defaults(handler=command_chair)

    roles = commands.add_parser("roles")
    roles.add_argument("--config", required=True, type=Path)
    roles.add_argument("--kind", required=True, choices=("specialist", "verifier"))
    roles.set_defaults(handler=command_roles)

    pending = commands.add_parser("mark-pending")
    pending.add_argument("--metadata", required=True, type=Path)
    pending.add_argument("--run-url", required=True)
    pending.set_defaults(handler=command_mark_pending)

    failed = commands.add_parser("mark-failed")
    failed.add_argument("--metadata", required=True, type=Path)
    failed.add_argument("--run-url", required=True)
    failed.set_defaults(handler=command_mark_failed)

    publish = commands.add_parser("publish")
    publish.add_argument("--metadata", required=True, type=Path)
    publish.add_argument("--config", required=True, type=Path)
    publish.add_argument("--context", required=True, type=Path)
    publish.add_argument("--specialists", required=True, type=Path)
    publish.add_argument("--verifiers", required=True, type=Path)
    publish.add_argument("--final-json", required=True, type=Path)
    publish.add_argument("--final-markdown", required=True, type=Path)
    publish.add_argument("--run-url", required=True)
    publish.set_defaults(handler=command_publish)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        return int(args.handler(args))
    except ReviewError as exc:
        print(f"::error::{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
