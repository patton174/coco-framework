#!/usr/bin/env python3
"""Fail-closed, long-path-safe filesystem operations for the API gate."""

from __future__ import annotations

import fnmatch
import os
import stat
import uuid
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import BinaryIO, Iterator


@dataclass(frozen=True)
class FileSnapshot:
    contents: bytes
    metadata: os.stat_result


@dataclass(frozen=True)
class DirectoryEntry:
    path: Path
    metadata: os.stat_result


def logical_absolute(path: Path) -> Path:
    """Return a lexical absolute path without resolving links."""
    return Path(os.path.normpath(os.path.abspath(os.fspath(path))))


def io_path(path: Path) -> Path:
    """Return an extended-length path only for internal Windows filesystem I/O."""
    logical = os.fspath(logical_absolute(path))
    if os.name != "nt" or logical.startswith("\\\\?\\"):
        return Path(logical)
    if logical.startswith("\\\\"):
        return Path(f"\\\\?\\UNC\\{logical[2:]}")
    return Path(f"\\\\?\\{logical}")


def logical_from_io(path: Path) -> Path:
    text = os.fspath(path)
    if os.name != "nt":
        return logical_absolute(Path(text))
    if text.startswith("\\\\?\\UNC\\"):
        return logical_absolute(Path(f"\\\\{text[8:]}"))
    if text.startswith("\\\\?\\"):
        return logical_absolute(Path(text[4:]))
    return logical_absolute(Path(text))


def is_reparse_or_symlink(metadata: os.stat_result) -> bool:
    attributes = getattr(metadata, "st_file_attributes", 0)
    reparse_flag = getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0)
    return stat.S_ISLNK(metadata.st_mode) or bool(attributes & reparse_flag)


def lstat(path: Path) -> os.stat_result:
    return os.lstat(io_path(path))


def entry_exists(path: Path) -> bool:
    try:
        lstat(path)
        return True
    except FileNotFoundError:
        return False


def _chain(path: Path) -> tuple[Path, ...]:
    logical = logical_absolute(path)
    return (*reversed(logical.parents), logical)


def _metadata_identity(metadata: os.stat_result) -> tuple[int, int, int, int]:
    return (
        metadata.st_dev,
        metadata.st_ino,
        metadata.st_mode,
        getattr(metadata, "st_file_attributes", 0),
    )


def _validate_existing_chain(
    path: Path, *, missing_ok: bool
) -> tuple[tuple[Path, tuple[int, int, int, int]], ...]:
    chain = _chain(path)
    identities: list[tuple[Path, tuple[int, int, int, int]]] = []
    for index, item in enumerate(chain):
        try:
            metadata = lstat(item)
        except FileNotFoundError:
            if missing_ok:
                return tuple(identities)
            raise
        if is_reparse_or_symlink(metadata):
            raise ValueError(
                f"Filesystem path contains a symlink/reparse point: {item}"
            )
        if index < len(chain) - 1 and not stat.S_ISDIR(metadata.st_mode):
            raise ValueError(f"Filesystem path has a non-directory ancestor: {item}")
        identities.append((item, _metadata_identity(metadata)))
    return tuple(identities)


def canonical_identity(path: Path, label: str) -> str:
    logical = logical_absolute(path)
    before = _validate_existing_chain(logical, missing_ok=False)
    canonical = logical_from_io(Path(os.path.realpath(io_path(logical))))
    after = _validate_existing_chain(logical, missing_ok=False)
    if before != after:
        raise ValueError(
            f"{label} parent chain changed during canonicalization: {logical}"
        )
    return os.path.normcase(os.fspath(canonical))


def ensure_contained(path: Path, parent: Path, label: str) -> None:
    candidate = Path(canonical_identity(path, label))
    root = Path(canonical_identity(parent, f"{label} parent"))
    try:
        candidate.relative_to(root)
    except ValueError as exc:
        raise ValueError(f"{label} escapes its trusted directory: {path}") from exc


def directory_metadata(path: Path, label: str) -> os.stat_result:
    logical = logical_absolute(path)
    try:
        _validate_existing_chain(logical, missing_ok=False)
        metadata = lstat(logical)
    except FileNotFoundError as exc:
        raise ValueError(f"{label} is missing: {logical}") from exc
    if is_reparse_or_symlink(metadata) or not stat.S_ISDIR(metadata.st_mode):
        raise ValueError(f"{label} must be a real non-reparse directory: {logical}")
    return metadata


def short_process_cwd(path: Path, label: str) -> Path:
    """Validate a logical cwd that Windows process creation can consume directly."""
    logical = logical_absolute(path)
    directory_metadata(logical, label)
    if os.name == "nt" and (
        str(logical).startswith("\\\\?\\") or len(str(logical)) >= 248
    ):
        raise ValueError(f"{label} is not a short logical Windows path: {logical}")
    return logical


def file_snapshot(path: Path, label: str, *, allow_empty: bool = False) -> FileSnapshot:
    logical = logical_absolute(path)
    try:
        parents_before = _validate_existing_chain(logical.parent, missing_ok=False)
        before = lstat(logical)
    except FileNotFoundError as exc:
        raise ValueError(f"{label} is missing: {logical}") from exc
    if is_reparse_or_symlink(before) or not stat.S_ISREG(before.st_mode):
        raise ValueError(f"{label} must be a regular non-reparse file: {logical}")
    with io_path(logical).open("rb") as source:
        contents = source.read()
    after = lstat(logical)
    parents_after = _validate_existing_chain(logical.parent, missing_ok=False)
    before_identity = (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns)
    after_identity = (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns)
    if (
        is_reparse_or_symlink(after)
        or before_identity != after_identity
        or parents_before != parents_after
    ):
        raise ValueError(f"{label} changed while it was read: {logical}")
    if not contents and not allow_empty:
        raise ValueError(f"{label} is empty: {logical}")
    return FileSnapshot(contents, after)


def read_bytes(path: Path, label: str) -> bytes:
    return file_snapshot(path, label).contents


def read_text(path: Path, label: str, *, encoding: str = "utf-8") -> str:
    return read_bytes(path, label).decode(encoding)


def mkdir(path: Path, *, parents: bool = False, exist_ok: bool = False) -> Path:
    logical = logical_absolute(path)
    existing_before = _validate_existing_chain(logical, missing_ok=True)
    if parents:
        os.makedirs(io_path(logical), exist_ok=exist_ok)
    else:
        os.mkdir(io_path(logical))
    directory_metadata(logical, "Created directory")
    existing_after = _validate_existing_chain(
        existing_before[-1][0] if existing_before else logical.anchor,
        missing_ok=False,
    )
    if existing_before and existing_before != existing_after:
        raise ValueError(
            f"Output parent chain changed while creating directory: {logical}"
        )
    return logical


def _prepare_write(path: Path, *, exclusive: bool) -> Path:
    logical = logical_absolute(path)
    directory_metadata(logical.parent, "Output parent directory")
    if entry_exists(logical):
        metadata = lstat(logical)
        if is_reparse_or_symlink(metadata) or not stat.S_ISREG(metadata.st_mode):
            raise ValueError(f"Output must be a regular non-reparse file: {logical}")
        if exclusive:
            raise FileExistsError(logical)
    return logical


@contextmanager
def binary_writer(path: Path, *, exclusive: bool = False) -> Iterator[BinaryIO]:
    logical = _prepare_write(path, exclusive=exclusive)
    parents_before = _validate_existing_chain(logical.parent, missing_ok=False)
    mode = "xb" if exclusive else "wb"
    with io_path(logical).open(mode) as output:
        yield output
    parents_after = _validate_existing_chain(logical.parent, missing_ok=False)
    if parents_before != parents_after:
        raise ValueError(f"Output parent chain changed while writing file: {logical}")
    file_snapshot(logical, "Written file", allow_empty=True)


def write_bytes(path: Path, contents: bytes, *, exclusive: bool = False) -> Path:
    with binary_writer(path, exclusive=exclusive) as output:
        output.write(contents)
    return logical_absolute(path)


def write_text(
    path: Path,
    value: str,
    *,
    encoding: str = "utf-8",
    exclusive: bool = False,
) -> Path:
    return write_bytes(path, value.encode(encoding), exclusive=exclusive)


def atomic_create_bytes(path: Path, contents: bytes) -> Path:
    logical = logical_absolute(path)
    mkdir(logical.parent, parents=True, exist_ok=True)
    if entry_exists(logical):
        raise FileExistsError(logical)
    temporary = logical.with_name(f".{logical.name}.{uuid.uuid4().hex}.tmp")
    linked = False
    try:
        write_bytes(temporary, contents, exclusive=True)
        parents_before = _validate_existing_chain(logical.parent, missing_ok=False)
        if entry_exists(logical):
            raise FileExistsError(logical)
        os.link(
            io_path(temporary),
            io_path(logical),
            src_dir_fd=None,
            dst_dir_fd=None,
            follow_symlinks=False,
        )
        linked = True
        temporary_metadata = lstat(temporary)
        target_metadata = lstat(logical)
        if (
            is_reparse_or_symlink(temporary_metadata)
            or is_reparse_or_symlink(target_metadata)
            or (temporary_metadata.st_dev, temporary_metadata.st_ino)
            != (target_metadata.st_dev, target_metadata.st_ino)
        ):
            raise ValueError(f"Atomic create link identity mismatch: {logical}")
        parents_after_link = _validate_existing_chain(logical.parent, missing_ok=False)
        if parents_before != parents_after_link:
            raise ValueError(
                f"Output parent chain changed during atomic create: {logical}"
            )
        os.replace(io_path(temporary), io_path(logical))
        parents_after_replace = _validate_existing_chain(
            logical.parent, missing_ok=False
        )
        if parents_before != parents_after_replace:
            raise ValueError(
                f"Output parent chain changed during atomic replace: {logical}"
            )
    except BaseException:
        if linked and entry_exists(logical):
            unlink(logical, "Failed atomic create result")
        if entry_exists(temporary):
            unlink(temporary, "Atomic create temporary file")
        raise
    if entry_exists(temporary):
        unlink(temporary, "Atomic create temporary file")
    file_snapshot(logical, "Atomic create result", allow_empty=True)
    return logical


def atomic_create_text(path: Path, value: str, *, encoding: str = "utf-8") -> Path:
    return atomic_create_bytes(path, value.encode(encoding))


def unlink(path: Path, label: str) -> None:
    logical = logical_absolute(path)
    _validate_existing_chain(logical.parent, missing_ok=False)
    metadata = lstat(logical)
    if is_reparse_or_symlink(metadata) or not stat.S_ISREG(metadata.st_mode):
        raise ValueError(f"{label} must be a regular non-reparse file: {logical}")
    os.unlink(io_path(logical))


def scandir(path: Path, label: str) -> tuple[DirectoryEntry, ...]:
    logical = logical_absolute(path)
    directory_metadata(logical, label)
    records: list[DirectoryEntry] = []
    with os.scandir(io_path(logical)) as entries:
        for entry in entries:
            metadata = entry.stat(follow_symlinks=False)
            child = logical / entry.name
            if is_reparse_or_symlink(metadata):
                raise ValueError(f"{label} contains a symlink/reparse point: {child}")
            records.append(DirectoryEntry(child, metadata))
    return tuple(records)


def rmtree(path: Path, label: str) -> None:
    logical = logical_absolute(path)
    directory_metadata(logical, label)
    for entry in scandir(logical, label):
        if stat.S_ISDIR(entry.metadata.st_mode):
            rmtree(entry.path, label)
        elif stat.S_ISREG(entry.metadata.st_mode):
            unlink(entry.path, label)
        else:
            raise ValueError(f"{label} contains an unsupported entry: {entry.path}")
    _validate_existing_chain(logical.parent, missing_ok=False)
    os.rmdir(io_path(logical))


def walk_files(root: Path, label: str) -> tuple[Path, ...]:
    files: list[Path] = []

    def visit(directory: Path) -> None:
        for entry in scandir(directory, label):
            if stat.S_ISDIR(entry.metadata.st_mode):
                visit(entry.path)
            elif stat.S_ISREG(entry.metadata.st_mode):
                files.append(entry.path)
            else:
                raise ValueError(f"{label} contains an unsupported entry: {entry.path}")

    visit(logical_absolute(root))
    return tuple(files)


def glob_files(root: Path, pattern: str, label: str) -> tuple[Path, ...]:
    logical_root = logical_absolute(root)
    return tuple(
        path
        for path in walk_files(logical_root, label)
        if fnmatch.fnmatchcase(path.relative_to(logical_root).as_posix(), pattern)
    )


def utime(path: Path, *, ns: tuple[int, int]) -> None:
    file_snapshot(path, "Timestamp target")
    os.utime(io_path(path), ns=ns, follow_symlinks=False)
    file_snapshot(path, "Timestamp result")
