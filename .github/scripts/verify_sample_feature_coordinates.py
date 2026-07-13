#!/usr/bin/env python3

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from zipfile import BadZipFile, ZipFile


BOOT_LIBRARY_PREFIX = "BOOT-INF/lib/"
BOOT_INDEX_ENTRIES = ("BOOT-INF/classpath.idx", "BOOT-INF/layers.idx")


def feature_prefixes(feature: str) -> tuple[str, str]:
    return (f"coco-{feature}-", f"coco-feature-{feature}-")


def matching_libraries(libraries: list[str], prefixes: tuple[str, ...]) -> list[str]:
    return sorted(
        library
        for library in libraries
        if any(library.startswith(prefix) for prefix in prefixes)
    )


def indexed_libraries(index_contents: list[str]) -> list[str]:
    libraries: set[str] = set()
    for content in index_contents:
        for line in content.splitlines():
            token = line.strip().lstrip("\ufeff")
            if token.startswith("- "):
                token = token[2:].strip()
            if len(token) >= 2 and token[0] in ("'", '"') and token[-1] == token[0]:
                token = token[1:-1]
            if token.startswith(BOOT_LIBRARY_PREFIX) and token.endswith(".jar"):
                libraries.add(Path(token).name)
    return sorted(libraries)


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
            entries = set(archive.namelist())
            libraries = sorted(
                Path(entry).name
                for entry in entries
                if entry.startswith(BOOT_LIBRARY_PREFIX) and entry.endswith(".jar")
            )
            index_contents = [
                archive.read(entry).decode("utf-8", errors="replace")
                for entry in BOOT_INDEX_ENTRIES
                if entry in entries
            ]
            index_library_names = indexed_libraries(index_contents)
    except (OSError, BadZipFile) as exc:
        return [f"unable to read Spring Boot archive {archive_path}: {exc}"]

    missing_indexes = [entry for entry in BOOT_INDEX_ENTRIES if entry not in entries]
    if missing_indexes:
        errors.append(
            f"missing Spring Boot index entries: {', '.join(missing_indexes)}"
        )

    for feature in required_features:
        matches = matching_libraries(libraries, feature_prefixes(feature))
        if len(matches) != 1:
            errors.append(
                f"feature {feature} requires exactly one old or canonical artifact; "
                f"found {len(matches)}: {', '.join(matches) or 'none'}"
            )
        elif not missing_indexes:
            index_matches = matching_libraries(
                index_library_names, feature_prefixes(feature)
            )
            if len(index_matches) != 1:
                errors.append(
                    f"feature {feature} requires exactly one Spring Boot index jar token; "
                    f"found {len(index_matches)}: {', '.join(index_matches) or 'none'}"
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

    if require_codegen:
        matches = matching_libraries(libraries, feature_prefixes("codegen"))
        if len(matches) != 1:
            errors.append(
                "codegen requires exactly one old or canonical artifact; "
                f"found {len(matches)}: {', '.join(matches) or 'none'}"
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
