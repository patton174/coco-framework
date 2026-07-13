#!/usr/bin/env python3

from __future__ import annotations

import argparse
import codecs
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from zipfile import BadZipFile, ZipFile, ZipInfo


BOOT_LIBRARY_PREFIX = "BOOT-INF/lib/"
BOOT_INDEX_ENTRIES = ("BOOT-INF/classpath.idx", "BOOT-INF/layers.idx")
MAX_INDEX_BYTES = 1024 * 1024
DIRECT_BOOT_LIBRARY_TOKEN = re.compile(r'BOOT-INF/lib/([^/\\"\x00-\x1f\x7f]+[.]jar)')
CLASSPATH_INDEX_LINE = re.compile(r'^- "([^"\r\n]+)"$')
LAYERS_INDEX_HEADER = re.compile(r'^- "([^"\r\n]+)":$')
LAYERS_INDEX_LINE = re.compile(r'^  - "([^"\r\n]+)"$')


@dataclass(frozen=True)
class ParsedIndex:
    library_tokens: frozenset[str]
    errors: tuple[str, ...]


@dataclass(frozen=True)
class ArchiveInspection:
    readable: bool
    archive_library_tokens: frozenset[str]
    duplicate_library_tokens: frozenset[str]
    index_library_tokens: frozenset[str]
    indexes_complete: bool
    errors: tuple[str, ...]


def feature_prefixes(feature: str) -> tuple[str, str]:
    return (f"coco-{feature}-", f"coco-feature-{feature}-")


def matching_libraries(
    libraries: tuple[str, ...], prefixes: tuple[str, ...]
) -> list[str]:
    return sorted(
        library
        for library in libraries
        if any(library.startswith(prefix) for prefix in prefixes)
    )


def direct_library_name(token: str) -> str | None:
    match = DIRECT_BOOT_LIBRARY_TOKEN.fullmatch(token)
    return match.group(1) if match is not None else None


def library_names(tokens: frozenset[str]) -> tuple[str, ...]:
    return tuple(sorted(token.removeprefix(BOOT_LIBRARY_PREFIX) for token in tokens))


def looks_like_index_library_token(value: str) -> bool:
    return (
        value.startswith("BOOT-INF/lib")
        or value.startswith("BOOT-INF\\lib")
        or value.lower().endswith(".jar")
    )


def looks_like_archive_library_entry(value: str) -> bool:
    if value == BOOT_LIBRARY_PREFIX:
        return False
    return value.startswith(BOOT_LIBRARY_PREFIX) or value.startswith("BOOT-INF\\lib\\")


def malformed_index_error(index_name: str, line_number: int, detail: str) -> str:
    return f"malformed Spring Boot index {index_name}:{line_number}: {detail}"


def parse_index_library_token(
    index_name: str, line_number: int, value: str
) -> tuple[str | None, str | None]:
    if direct_library_name(value) is not None:
        return value, None
    return None, malformed_index_error(
        index_name,
        line_number,
        f"library token {value!r} must be a direct BOOT-INF/lib/<filename>.jar token",
    )


def decode_index(index_name: str, content: bytes) -> tuple[str | None, str | None]:
    payload = content
    if payload.startswith(codecs.BOM_UTF8):
        payload = payload[len(codecs.BOM_UTF8) :]
    try:
        text = payload.decode("utf-8")
    except UnicodeDecodeError as exc:
        return None, (
            f"Spring Boot index {index_name} is not valid UTF-8 at byte "
            f"{exc.start}: {exc.reason}"
        )
    if "\ufeff" in text:
        return None, malformed_index_error(
            index_name,
            text[: text.index("\ufeff")].count("\n") + 1,
            "UTF-8 BOM is allowed only at byte 0",
        )
    return text, None


def parse_classpath_index(index_name: str, text: str) -> ParsedIndex:
    tokens: set[str] = set()
    errors: list[str] = []
    for line_number, line in enumerate(text.splitlines(), start=1):
        if line == "":
            continue
        match = CLASSPATH_INDEX_LINE.fullmatch(line)
        if match is None:
            errors.append(
                malformed_index_error(
                    index_name,
                    line_number,
                    f"expected exact list item '- \"{BOOT_LIBRARY_PREFIX}"
                    "<filename>.jar\"'",
                )
            )
            continue
        token, error = parse_index_library_token(
            index_name, line_number, match.group(1)
        )
        if error is not None:
            errors.append(error)
        elif token in tokens:
            errors.append(
                malformed_index_error(
                    index_name,
                    line_number,
                    f"duplicate library token {token!r}",
                )
            )
        else:
            tokens.add(token)
    return ParsedIndex(frozenset(tokens), tuple(errors))


def parse_layers_index(index_name: str, text: str) -> ParsedIndex:
    tokens: set[str] = set()
    errors: list[str] = []
    current_layer: str | None = None
    seen_layers: set[str] = set()
    for line_number, line in enumerate(text.splitlines(), start=1):
        if line == "":
            continue
        header = LAYERS_INDEX_HEADER.fullmatch(line)
        if header is not None:
            current_layer = header.group(1)
            if current_layer in seen_layers:
                errors.append(
                    malformed_index_error(
                        index_name,
                        line_number,
                        f"duplicate layer header {current_layer!r}",
                    )
                )
            seen_layers.add(current_layer)
            continue
        item = LAYERS_INDEX_LINE.fullmatch(line)
        if item is None:
            errors.append(
                malformed_index_error(
                    index_name,
                    line_number,
                    "expected a quoted layer header or two-space-indented item",
                )
            )
            continue
        value = item.group(1)
        if current_layer is None:
            errors.append(
                malformed_index_error(
                    index_name,
                    line_number,
                    "layer item appears before its layer header",
                )
            )
            continue
        if not looks_like_index_library_token(value):
            if "\\" in value or ".." in value.split("/"):
                errors.append(
                    malformed_index_error(
                        index_name,
                        line_number,
                        f"unsafe layer path {value!r}",
                    )
                )
            continue
        token, error = parse_index_library_token(index_name, line_number, value)
        if error is not None:
            errors.append(error)
        elif token in tokens:
            errors.append(
                malformed_index_error(
                    index_name,
                    line_number,
                    f"duplicate library token {token!r}",
                )
            )
        else:
            tokens.add(token)
    return ParsedIndex(frozenset(tokens), tuple(errors))


def parse_index(index_name: str, content: bytes) -> ParsedIndex:
    text, error = decode_index(index_name, content)
    if error is not None:
        return ParsedIndex(frozenset(), (error,))
    assert text is not None
    if index_name == "BOOT-INF/classpath.idx":
        return parse_classpath_index(index_name, text)
    return parse_layers_index(index_name, text)


def failed_inspection(message: str) -> ArchiveInspection:
    return ArchiveInspection(
        readable=False,
        archive_library_tokens=frozenset(),
        duplicate_library_tokens=frozenset(),
        index_library_tokens=frozenset(),
        indexes_complete=False,
        errors=(message,),
    )


def read_index_entry(archive: ZipFile, index_name: str, info: ZipInfo) -> ParsedIndex:
    if info.file_size > MAX_INDEX_BYTES:
        return ParsedIndex(
            frozenset(),
            (
                f"Spring Boot index {index_name} exceeds the "
                f"{MAX_INDEX_BYTES}-byte limit: {info.file_size} bytes",
            ),
        )
    try:
        with archive.open(info) as stream:
            content = stream.read(MAX_INDEX_BYTES + 1)
    except (BadZipFile, EOFError, OSError, RuntimeError) as exc:
        return ParsedIndex(
            frozenset(),
            (
                f"unable to read Spring Boot index {index_name} "
                f"({type(exc).__name__}): {exc}",
            ),
        )
    if len(content) > MAX_INDEX_BYTES:
        return ParsedIndex(
            frozenset(),
            (
                f"Spring Boot index {index_name} exceeds the "
                f"{MAX_INDEX_BYTES}-byte limit while reading",
            ),
        )
    return parse_index(index_name, content)


def inspect_open_archive(archive: ZipFile) -> ArchiveInspection:
    archive_library_tokens: set[str] = set()
    duplicate_library_tokens: set[str] = set()
    malformed_library_entries: set[str] = set()
    malformed_index_entries: set[str] = set()
    entry_counts: dict[str, int] = {}
    entry_signatures: dict[str, set[tuple[int, int, int]]] = {}
    index_infos: dict[str, list[ZipInfo]] = {entry: [] for entry in BOOT_INDEX_ENTRIES}

    for info in archive.infolist():
        name = info.orig_filename
        normalized_name = info.filename
        direct_library = direct_library_name(name) is not None
        malformed_library = not direct_library and looks_like_archive_library_entry(
            name
        )
        malformed_index = name not in index_infos and normalized_name in index_infos
        tracked = (
            direct_library
            or malformed_library
            or malformed_index
            or name in index_infos
        )
        if direct_library:
            archive_library_tokens.add(name)
        elif malformed_library:
            malformed_library_entries.add(name)
        if malformed_index:
            malformed_index_entries.add(name)
        if name in index_infos:
            index_infos[name].append(info)
        if tracked:
            entry_counts[name] = entry_counts.get(name, 0) + 1
            entry_signatures.setdefault(name, set()).add(
                (info.file_size, info.compress_size, info.CRC)
            )

    errors = [
        f"malformed archive library entry {name!r}; expected a direct "
        "BOOT-INF/lib/<filename>.jar entry"
        for name in sorted(malformed_library_entries)
    ]
    errors.extend(
        f"malformed archive index entry {name!r}; expected an exact "
        "BOOT-INF/classpath.idx or BOOT-INF/layers.idx entry"
        for name in sorted(malformed_index_entries)
    )
    for name, count in sorted(entry_counts.items()):
        if count < 2:
            continue
        variants = len(entry_signatures[name])
        detail = f"{variants} content variant{'s' if variants != 1 else ''}"
        if direct_library_name(name) is not None:
            duplicate_library_tokens.add(name)
            detail += "; an index token cannot bind a unique archive jar"
        errors.append(
            f"duplicate ZIP entry {name!r} appears {count} times with {detail}"
        )

    missing_indexes = [entry for entry, infos in index_infos.items() if not infos]
    indexes_complete = not missing_indexes
    if missing_indexes:
        errors.append(
            f"missing Spring Boot index entries: {', '.join(missing_indexes)}"
        )

    index_library_tokens: set[str] = set()
    for index_name in BOOT_INDEX_ENTRIES:
        infos = index_infos[index_name]
        if len(infos) != 1:
            if len(infos) > 1:
                indexes_complete = False
            continue
        parsed = read_index_entry(archive, index_name, infos[0])
        index_library_tokens.update(parsed.library_tokens)
        if parsed.errors:
            indexes_complete = False
            errors.extend(parsed.errors)

    return ArchiveInspection(
        readable=True,
        archive_library_tokens=frozenset(archive_library_tokens),
        duplicate_library_tokens=frozenset(duplicate_library_tokens),
        index_library_tokens=frozenset(index_library_tokens),
        indexes_complete=indexes_complete,
        errors=tuple(errors),
    )


def inspect_archive(archive_path: Path) -> ArchiveInspection:
    try:
        if not archive_path.exists():
            return failed_inspection(f"Spring Boot archive is missing: {archive_path}")
        if not archive_path.is_file():
            return failed_inspection(
                f"Spring Boot archive path is not a regular file: {archive_path}"
            )
    except OSError as exc:
        return failed_inspection(
            f"unable to inspect Spring Boot archive {archive_path} "
            f"({type(exc).__name__}, errno={exc.errno}): {exc}"
        )
    try:
        with ZipFile(archive_path) as archive:
            return inspect_open_archive(archive)
    except BadZipFile as exc:
        return failed_inspection(
            f"Spring Boot archive is not a valid ZIP file: {archive_path} "
            f"(BadZipFile): {exc}"
        )
    except OSError as exc:
        return failed_inspection(
            f"unable to read Spring Boot archive {archive_path} "
            f"({type(exc).__name__}, errno={exc.errno}): {exc}"
        )


def append_required_artifact_error(
    errors: list[str],
    *,
    subject: str,
    prefixes: tuple[str, ...],
    archive_libraries: tuple[str, ...],
    index_libraries: tuple[str, ...],
    indexes_complete: bool,
    exactly_one: bool,
) -> None:
    archive_matches = matching_libraries(archive_libraries, prefixes)
    index_matches = matching_libraries(index_libraries, prefixes)
    cardinality_valid = (
        len(archive_matches) == 1 if exactly_one else bool(archive_matches)
    )
    expectation = (
        "exactly one old or canonical artifact"
        if exactly_one
        else "at least one matching artifact"
    )
    if not cardinality_valid:
        errors.append(
            f"{subject} requires {expectation} in the archive; archive found "
            f"{len(archive_matches)}: {', '.join(archive_matches) or 'none'}"
        )
        return
    if indexes_complete and index_matches != archive_matches:
        errors.append(
            f"{subject} requires matching archive artifacts in the archive and "
            "Spring Boot indexes; "
            f"archive found {len(archive_matches)}: "
            f"{', '.join(archive_matches) or 'none'}; "
            f"index found {len(index_matches)}: "
            f"{', '.join(index_matches) or 'none'}"
        )


def append_forbidden_artifact_error(
    errors: list[str],
    *,
    subject: str,
    prefixes: tuple[str, ...],
    archive_libraries: tuple[str, ...],
    index_libraries: tuple[str, ...],
) -> None:
    archive_matches = matching_libraries(archive_libraries, prefixes)
    index_matches = matching_libraries(index_libraries, prefixes)
    if not archive_matches and not index_matches:
        return
    errors.append(
        f"{subject} must be pruned from the archive and Spring Boot indexes; "
        f"archive found {len(archive_matches)}: "
        f"{', '.join(archive_matches) or 'none'}; "
        f"index found {len(index_matches)}: "
        f"{', '.join(index_matches) or 'none'}"
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
    inspection = inspect_archive(archive_path)
    errors = list(inspection.errors)
    if not inspection.readable:
        return errors

    for token in sorted(inspection.index_library_tokens):
        if token in inspection.duplicate_library_tokens:
            continue
        if token not in inspection.archive_library_tokens:
            errors.append(
                f"Spring Boot index jar token {token} does not bind exactly one "
                "archive jar; found 0"
            )

    archive_libraries = library_names(inspection.archive_library_tokens)
    index_libraries = library_names(inspection.index_library_tokens)
    normalized_required_features = list(dict.fromkeys(required_features))
    if require_codegen and "codegen" not in normalized_required_features:
        normalized_required_features.append("codegen")
    for feature in normalized_required_features:
        append_required_artifact_error(
            errors,
            subject="codegen"
            if feature == "codegen" and require_codegen
            else f"feature {feature}",
            prefixes=feature_prefixes(feature),
            archive_libraries=archive_libraries,
            index_libraries=index_libraries,
            indexes_complete=inspection.indexes_complete,
            exactly_one=True,
        )

    for feature in dict.fromkeys(forbidden_features):
        append_forbidden_artifact_error(
            errors,
            subject=f"feature {feature}",
            prefixes=feature_prefixes(feature),
            archive_libraries=archive_libraries,
            index_libraries=index_libraries,
        )

    for prefix in dict.fromkeys(required_library_prefixes):
        append_required_artifact_error(
            errors,
            subject=f"required library prefix {prefix}",
            prefixes=(prefix,),
            archive_libraries=archive_libraries,
            index_libraries=index_libraries,
            indexes_complete=inspection.indexes_complete,
            exactly_one=False,
        )

    for prefix in dict.fromkeys(forbidden_library_prefixes):
        append_forbidden_artifact_error(
            errors,
            subject=f"library prefix {prefix}",
            prefixes=(prefix,),
            archive_libraries=archive_libraries,
            index_libraries=index_libraries,
        )

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
