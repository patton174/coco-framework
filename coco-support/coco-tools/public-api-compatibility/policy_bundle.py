#!/usr/bin/env python3
"""Shared canonical policy loading and hashing for protected API gate consumers."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

from path_io import read_bytes


POLICY_BUNDLE_SCHEMA_VERSION = 3


def _object_without_duplicates(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, item in pairs:
        if key in value:
            raise ValueError(f"Protected policy JSON contains duplicate key: {key!r}")
        value[key] = item
    return value


def load_json_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(
            read_bytes(path, label).decode("utf-8"),
            object_pairs_hook=_object_without_duplicates,
        )
    except UnicodeDecodeError as exc:
        raise ValueError(f"{label} must be UTF-8 JSON: {path}") from exc
    if not isinstance(value, dict):
        raise ValueError(f"{label} must be a JSON object: {path}")
    return value


def canonical_json_bytes(value: Any) -> bytes:
    return (
        json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":"))
        + "\n"
    ).encode("ascii")


def normalized_policy_bundle(
    profile_path: Path,
    ledger_path: Path,
    allowlist_path: Path,
    signing_key_path: Path,
    japicmp_policy_path: Path | None = None,
) -> dict[str, Any]:
    japicmp_policy = japicmp_policy_path or profile_path.with_name(
        "japicmp-policy.json"
    )
    signing_key_sha256 = hashlib.sha256(
        read_bytes(signing_key_path, "Baseline signing key")
    ).hexdigest()
    return {
        "schemaVersion": POLICY_BUNDLE_SCHEMA_VERSION,
        "profile": load_json_object(profile_path, "Public API profile"),
        "baselineLedger": load_json_object(ledger_path, "Baseline ledger"),
        "allowlist": load_json_object(allowlist_path, "Public API allowlist"),
        "japicmpPolicy": load_json_object(japicmp_policy, "Japicmp policy"),
        "signingKeySha256": signing_key_sha256,
    }


def policy_bundle_sha256(
    profile_path: Path,
    ledger_path: Path,
    allowlist_path: Path,
    signing_key_path: Path,
    japicmp_policy_path: Path | None = None,
) -> str:
    return hashlib.sha256(
        canonical_json_bytes(
            normalized_policy_bundle(
                profile_path,
                ledger_path,
                allowlist_path,
                signing_key_path,
                japicmp_policy_path,
            )
        )
    ).hexdigest()
