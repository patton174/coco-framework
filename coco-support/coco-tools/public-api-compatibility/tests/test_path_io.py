from __future__ import annotations

import ctypes
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

import path_io  # noqa: E402
from path_io import (  # noqa: E402
    atomic_create_bytes,
    atomic_create_text,
    canonical_identity,
    entry_exists,
    file_snapshot,
    glob_files,
    io_path,
    logical_absolute,
    mkdir,
    read_text,
    rmtree,
)
from check_public_api_compatibility import load_manifest  # noqa: E402


def deep_path(root: Path, filename: str, minimum: int = 280) -> Path:
    parent = root
    index = 0
    while len(str(parent / filename)) < minimum:
        parent /= f"segment-{index:02d}-abcdefghij"
        index += 1
    mkdir(parent, parents=True)
    return parent / filename


class PathIoTest(unittest.TestCase):
    def test_real_long_report_path_supports_snapshot_hash_and_walk(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = deep_path(root, "public-api-compatibility.xml")
            self.assertGreaterEqual(len(str(report)), 280)

            atomic_create_text(report, "<japicmp/>")

            snapshot = file_snapshot(report, "long report")
            self.assertEqual(b"<japicmp/>", snapshot.contents)
            self.assertEqual("<japicmp/>", read_text(report, "long report"))
            self.assertEqual(
                (logical_absolute(report),),
                glob_files(root, "**/public-api-compatibility.xml", "long tree"),
            )
            self.assertNotIn("\\\\?\\", str(logical_absolute(report)))
            rmtree(root, "long path test tree")

    def test_short_path_control_uses_the_same_checked_operations(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "short.txt"
            atomic_create_text(path, "short")
            self.assertEqual("short", read_text(path, "short file"))
            self.assertEqual(
                canonical_identity(path, "short file"),
                canonical_identity(logical_absolute(path), "short file"),
            )

    def test_deep_profile_json_is_read_through_checked_long_path_io(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            profile = deep_path(root, "public-api-profile.json")
            atomic_create_bytes(
                profile,
                read_text(
                    SCRIPT_DIR / "public-api-profile.json", "repository API profile"
                ).encode("utf-8"),
            )
            self.assertEqual(32, len(load_manifest(profile).artifacts))
            rmtree(root, "deep profile test tree")

    def test_atomic_create_never_overwrites_an_existing_target(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "attestation.json"
            atomic_create_text(path, "first")
            with self.assertRaises(FileExistsError):
                atomic_create_text(path, "second")
            self.assertEqual("first", read_text(path, "attestation"))

    def test_atomic_create_failure_removes_temporary_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "attestation.json"
            with mock.patch.object(path_io.os, "link", side_effect=OSError("blocked")):
                with self.assertRaisesRegex(OSError, "blocked"):
                    atomic_create_text(target, "value")
            self.assertFalse(entry_exists(target))
            self.assertEqual((), glob_files(root, "*.tmp", "atomic workspace"))

    def test_atomic_replace_failure_removes_target_and_temporary_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "attestation.json"
            with mock.patch.object(
                path_io.os, "replace", side_effect=OSError("replace blocked")
            ):
                with self.assertRaisesRegex(OSError, "replace blocked"):
                    atomic_create_text(target, "value")
            self.assertFalse(entry_exists(target))
            self.assertEqual((), glob_files(root, "*.tmp", "atomic workspace"))

    def test_atomic_create_write_failure_removes_partial_temporary_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "attestation.json"
            original = path_io.write_bytes

            def failing_write(path: Path, contents: bytes, *, exclusive: bool) -> Path:
                original(path, contents, exclusive=exclusive)
                raise OSError("write failed")

            with mock.patch.object(path_io, "write_bytes", side_effect=failing_write):
                with self.assertRaisesRegex(OSError, "write failed"):
                    atomic_create_text(target, "value")
            self.assertFalse(entry_exists(target))
            self.assertEqual((), glob_files(root, "*.tmp", "atomic workspace"))

    def test_atomic_create_parent_revalidation_failure_cleans_temporary(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / "attestation.json"
            original_write = path_io.write_bytes
            original_validate = path_io._validate_existing_chain
            wrote_temporary = False
            failed_validation = False

            def tracked_write(path: Path, contents: bytes, *, exclusive: bool) -> Path:
                nonlocal wrote_temporary
                result = original_write(path, contents, exclusive=exclusive)
                wrote_temporary = True
                return result

            def failing_validation(
                path: Path, *, missing_ok: bool
            ) -> tuple[tuple[Path, tuple[int, int, int, int]], ...]:
                nonlocal failed_validation
                if (
                    wrote_temporary
                    and not failed_validation
                    and logical_absolute(path) == logical_absolute(root)
                ):
                    failed_validation = True
                    raise ValueError("parent changed")
                return original_validate(path, missing_ok=missing_ok)

            with (
                mock.patch.object(path_io, "write_bytes", side_effect=tracked_write),
                mock.patch.object(
                    path_io,
                    "_validate_existing_chain",
                    side_effect=failing_validation,
                ),
            ):
                with self.assertRaisesRegex(ValueError, "parent changed"):
                    atomic_create_text(target, "value")
            self.assertFalse(entry_exists(target))
            self.assertEqual((), glob_files(root, "*.tmp", "atomic workspace"))

    def test_parent_identity_change_during_read_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.xml"
            atomic_create_text(path, "<japicmp/>")
            original = path_io._validate_existing_chain
            parent_calls = 0

            def drifting_chain(
                candidate: Path, *, missing_ok: bool
            ) -> tuple[tuple[Path, tuple[int, int, int, int]], ...]:
                nonlocal parent_calls
                result = original(candidate, missing_ok=missing_ok)
                if logical_absolute(candidate) == logical_absolute(path.parent):
                    parent_calls += 1
                    if parent_calls == 2:
                        item, identity = result[-1]
                        result = (
                            *result[:-1],
                            (item, (identity[0] + 1, *identity[1:])),
                        )
                return result

            with mock.patch.object(
                path_io, "_validate_existing_chain", side_effect=drifting_chain
            ):
                with self.assertRaisesRegex(ValueError, "changed while it was read"):
                    file_snapshot(path, "report")

    @unittest.skipUnless(os.name == "nt", "Windows junction semantics")
    def test_real_windows_junction_parent_is_rejected_for_read_and_remove(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            real = root / "real"
            junction = root / "junction"
            mkdir(real)
            atomic_create_text(real / "report.xml", "<japicmp/>")
            completed = subprocess.run(
                ["cmd", "/d", "/c", "mklink", "/J", str(junction), str(real)],
                check=False,
                capture_output=True,
                text=True,
            )
            if completed.returncode:
                self.skipTest(f"Could not create a real junction: {completed.stderr}")
            try:
                with self.assertRaisesRegex(ValueError, "symlink/reparse"):
                    file_snapshot(junction / "report.xml", "junction report")
                with self.assertRaisesRegex(ValueError, "symlink/reparse|non-reparse"):
                    rmtree(junction, "junction tree")
            finally:
                os.rmdir(io_path(junction))

    @unittest.skipUnless(os.name == "nt", "Windows path aliases")
    def test_windows_case_and_8_3_aliases_have_one_canonical_identity(self) -> None:
        with tempfile.TemporaryDirectory(prefix="Coco Path Alias ") as directory:
            path = Path(directory) / "Mixed Case Artifact.jar"
            atomic_create_text(path, "candidate")
            case_alias = Path(str(path).swapcase())
            self.assertEqual(
                canonical_identity(path, "candidate"),
                canonical_identity(case_alias, "case alias"),
            )

            buffer = ctypes.create_unicode_buffer(32768)
            length = ctypes.windll.kernel32.GetShortPathNameW(
                str(path), buffer, len(buffer)
            )
            if not length or buffer.value.casefold() == str(path).casefold():
                self.skipTest("8.3 aliases are disabled on this volume")
            self.assertEqual(
                canonical_identity(path, "candidate"),
                canonical_identity(Path(buffer.value), "8.3 alias"),
            )

    def test_entry_points_do_not_reintroduce_naked_filesystem_io(self) -> None:
        forbidden = (
            ".read_text(",
            ".read_bytes(",
            ".write_text(",
            ".write_bytes(",
            ".lstat(",
            ".stat(",
            ".resolve(",
            ".is_symlink(",
            "shutil.rmtree(",
            "os.replace(",
        )
        for name in (
            "check_public_api_compatibility.py",
            "run_public_api_compatibility.py",
            "run_regression_fixtures.py",
        ):
            source = read_text(SCRIPT_DIR / name, f"{name} source")
            with self.subTest(name=name):
                for token in forbidden:
                    self.assertNotIn(token, source)
                self.assertNotIn('"\\\\?\\\\"', source)


if __name__ == "__main__":
    unittest.main()
