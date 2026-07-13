#!/usr/bin/env python3

from __future__ import annotations

import argparse
import re
import sys
from collections import Counter
from pathlib import Path
from zipfile import BadZipFile, ZipFile


BOOT_LIBRARY_PREFIX = "BOOT-INF/lib/"
BOOT_INDEX_ENTRIES = ("BOOT-INF/classpath.idx", "BOOT-INF/layers.idx")
DIRECT_BOOT_LIBRARY_TOKEN = re.compile(r"BOOT-INF/lib/([^/\\]+[.]jar)")


def feature_prefixes(feature: str) -> tuple[str, str]:
    return (f"coco-{feature}-", f"coco-feature-{feature}-")


def matching_libraries(libraries: list[str], prefixes: tuple[str, ...]) -> list[str]:
    return sorted(
        library
        for library in libraries
        if any(library.startswith(prefix) for prefix in prefixes)
    )


def direct_library_name(token: str) -> str | None:
    match = DIRECT_BOOT_LIBRARY_TOKEN.fullmatch(token)
    return match.group(1) if match is not None else None


def indexed_libraries(index_contents: list[str]) -> tuple[list[str], list[str]]:
    library_tokens: set[str] = set()
    errors: set[str] = set()
    for content in index_contents:
        for line in content.splitlines():
            token = line.strip().lstrip("\ufeff")
            if token.startswith("- "):
                token = token[2:].strip()
            if len(token) >= 2 and token[0] in ("'", '"') and token[-1] == token[0]:
                token = token[1:-1]
            if token.startswith(BOOT_LIBRARY_PREFIX) and token.endswith(".jar"):
                if direct_library_name(token) is None:
                    errors.add(
                        f"Spring Boot index entry {token!r} must be a direct "
                        "BOOT-INF/lib/<filename>.jar token"
                    )
                else:
                    library_tokens.add(token)
    return sorted(library_tokens), sorted(errors)


def check_required_feature(
    errors: list[str],
    *,
    feature: str,
    subject: str,
    libraries: list[str],
    index_library_names: list[str],
    missing_indexes: list[str],
) -> None:
    matches = matching_libraries(libraries, feature_prefixes(feature))
    if len(matches) != 1:
        errors.append(
            f"{subject} requires exactly one old or canonical artifact; "
            f"found {len(matches)}: {', '.join(matches) or 'none'}"
        )
    elif not missing_indexes:
        index_matches = matching_libraries(
            index_library_names, feature_prefixes(feature)
        )
        if index_matches != matches:
            errors.append(
                f"{subject} requires exactly one Spring Boot index jar token "
                f"matching archive artifact {matches[0]}; found "
                f"{len(index_matches)}: {', '.join(index_matches) or 'none'}"
            )


def check_archive(
    archive_path: Path,
    *,
    required_features: tuple[str, ...] = (),
    forbidden_features: tuple[str, ...] = (),
    required_library_prefixes: tuple[str, ...] = (),
    forbidden_library_prefixes: tuple[str, ...] = (),
    require_codegen: bool = False,
) -> list[str]:
    errors: list[str] = []
    try:
        with ZipFile(archive_path) as archive:
            entry_names = archive.namelist()
            entries = set(entry_names)
            archive_library_tokens = [
                entry for entry in entry_names if direct_library_name(entry) is not None
            ]
            archive_library_counts = Counter(archive_library_tokens)
            libraries = sorted(
                token.removeprefix(BOOT_LIBRARY_PREFIX)
                for token in archive_library_tokens
            )
            index_contents = [
                archive.read(entry).decode("utf-8", errors="replace")
                for entry in BOOT_INDEX_ENTRIES
                if entry in entries
            ]
            index_library_tokens, index_errors = indexed_libraries(index_contents)
            index_library_names = sorted(
                token.removeprefix(BOOT_LIBRARY_PREFIX)
                for token in index_library_tokens
            )
    except (OSError, BadZipFile) as exc:
        return [f"unable to read Spring Boot archive {archive_path}: {exc}"]

    missing_indexes = [entry for entry in BOOT_INDEX_ENTRIES if entry not in entries]
    if missing_indexes:
        errors.append(
            f"missing Spring Boot index entries: {', '.join(missing_indexes)}"
        )
    errors.extend(index_errors)

    for token in index_library_tokens:
        archive_matches = archive_library_counts[token]
        if archive_matches != 1:
            errors.append(
                f"Spring Boot index jar token {token} must bind exactly one "
                f"archive jar; found {archive_matches}"
            )

    for feature in dict.fromkeys(required_features):
        check_required_feature(
            errors,
            feature=feature,
            subject=f"feature {feature}",
            libraries=libraries,
            index_library_names=index_library_names,
            missing_indexes=missing_indexes,
        )

    for feature in forbidden_features:
        prefixes = feature_prefixes(feature)
        matches = matching_libraries(libraries, prefixes)
        if matches:
            errors.append(
                f"feature {feature} must be pruned; found: {', '.join(matches)}"
            )
        for prefix in prefixes:
            if matching_libraries(index_library_names, (prefix,)):
                errors.append(
                    f"feature {feature} remains in a Spring Boot index as {prefix}"
                )

    if require_codegen and "codegen" not in required_features:
        check_required_feature(
            errors,
            feature="codegen",
            subject="codegen",
            libraries=libraries,
            index_library_names=index_library_names,
            missing_indexes=missing_indexes,
        )

    for prefix in required_library_prefixes:
        matches = matching_libraries(libraries, (prefix,))
        if not matches:
            errors.append(f"required library prefix {prefix} was not found")

    for prefix in forbidden_library_prefixes:
        matches = matching_libraries(libraries, (prefix,))
        if matches:
            errors.append(
                f"library prefix {prefix} must be pruned; found: {', '.join(matches)}"
            )
        if matching_libraries(index_library_names, (prefix,)):
            errors.append(f"library prefix {prefix} remains in a Spring Boot index")

    return errors


def parser() -> argparse.ArgumentParser:
    value = argparse.ArgumentParser(
        description="Verify old/canonical Coco feature coordinates in a Boot archive."
    )
    value.add_argument("--archive", required=True, type=Path)
    value.add_argument("--required-features", nargs="*", default=[])
    value.add_argument("--forbidden-features", nargs="*", default=[])
    value.add_argument("--required-library-prefixes", nargs="*", default=[])
    value.add_argument("--forbidden-library-prefixes", nargs="*", default=[])
    value.add_argument("--require-codegen", action="store_true")
    return value


def main(argv: list[str] | None = None) -> int:
    args = parser().parse_args(argv)
    errors = check_archive(
        args.archive,
        required_features=tuple(args.required_features),
        forbidden_features=tuple(args.forbidden_features),
        required_library_prefixes=tuple(args.required_library_prefixes),
        forbidden_library_prefixes=tuple(args.forbidden_library_prefixes),
        require_codegen=args.require_codegen,
    )
    if errors:
        for error in errors:
            print(f"::error::{error}", file=sys.stderr)
        return 1
    print(f"Coco feature coordinate contract passed for {args.archive}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
