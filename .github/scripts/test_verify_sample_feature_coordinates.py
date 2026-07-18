from __future__ import annotations

import codecs
import tempfile
import unittest
import warnings
from pathlib import Path
from zipfile import ZipFile, ZipInfo

import verify_sample_feature_coordinates as verifier


class SampleFeatureCoordinateTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.archive = Path(self.temporary_directory.name) / "sample.jar"

    @staticmethod
    def classpath_index(libraries: list[str]) -> str:
        return "\n".join(f'- "BOOT-INF/lib/{library}"' for library in libraries)

    @staticmethod
    def layers_index(libraries: list[str]) -> str:
        lines = ['- "dependencies":']
        lines.extend(f'  - "BOOT-INF/lib/{library}"' for library in libraries)
        lines.extend(['- "application":', '  - "BOOT-INF/classes/"'])
        return "\n".join(lines)

    def write_archive(
        self,
        libraries: list[str],
        *,
        indexed_libraries: list[str] | None = None,
        classpath_libraries: list[str] | None = None,
        layers_libraries: list[str] | None = None,
        classpath: str | bytes | None = None,
        layers: str | bytes | None = None,
        include_indexes: tuple[str, ...] = verifier.BOOT_INDEX_ENTRIES,
        extra_entries: tuple[tuple[str | ZipInfo, str | bytes], ...] = (),
    ) -> None:
        indexed = libraries if indexed_libraries is None else indexed_libraries
        classpath_indexed = (
            indexed if classpath_libraries is None else classpath_libraries
        )
        layers_indexed = indexed if layers_libraries is None else layers_libraries
        classpath_content = (
            self.classpath_index(classpath_indexed) if classpath is None else classpath
        )
        layers_content = self.layers_index(layers_indexed) if layers is None else layers
        with ZipFile(self.archive, "w") as archive:
            for library in libraries:
                archive.writestr(f"BOOT-INF/lib/{library}", f"library:{library}")
            if "BOOT-INF/classpath.idx" in include_indexes:
                archive.writestr("BOOT-INF/classpath.idx", classpath_content)
            if "BOOT-INF/layers.idx" in include_indexes:
                archive.writestr("BOOT-INF/layers.idx", layers_content)
            for name, content in extra_entries:
                archive.writestr(name, content)

    @staticmethod
    def raw_zip_info(name: str) -> ZipInfo:
        info = ZipInfo("placeholder")
        info.filename = name
        info.orig_filename = name
        return info

    def assert_errors_contain(
        self, errors: list[str], expected_fragments: tuple[str, ...]
    ) -> None:
        for fragment in expected_fragments:
            self.assertTrue(any(fragment in error for error in errors), errors)

    def test_coordinate_migration_matrix_preserves_existing_coverage(self) -> None:
        cases = {
            "old": (
                [
                    "coco-feature-web-2.0.2.jar",
                    "coco-feature-audit-2.0.2.jar",
                    "coco-feature-codegen-2.0.2.jar",
                ],
                ("web", "audit"),
                True,
                (),
            ),
            "canonical-mixed": (
                [
                    "coco-web-2.0.2.jar",
                    "coco-feature-audit-2.0.2.jar",
                    "coco-security-2.0.2.jar",
                    "coco-codegen-2.0.2.jar",
                ],
                ("web", "audit", "security"),
                True,
                (),
            ),
            "missing": (
                ["coco-web-2.0.2.jar"],
                ("web", "audit"),
                False,
                ("feature audit", "found 0"),
            ),
            "old-canonical-duplicate": (
                ["coco-web-2.0.2.jar", "coco-feature-web-2.0.2.jar"],
                ("web",),
                False,
                ("feature web", "found 2"),
            ),
        }

        for name, (libraries, features, require_codegen, expected) in cases.items():
            with self.subTest(name=name):
                self.write_archive(libraries)

                errors = verifier.check_archive(
                    self.archive,
                    required_features=features,
                    require_codegen=require_codegen,
                )

                if expected:
                    self.assert_errors_contain(errors, expected)
                else:
                    self.assertEqual([], errors)

    def test_forbidden_rules_collapse_archive_and_index_diagnostics(self) -> None:
        self.write_archive(
            [
                "coco-web-2.0.2.jar",
                "coco-feature-tenant-2.0.2.jar",
                "mybatis-3.5.0.jar",
            ]
        )

        errors = verifier.check_archive(
            self.archive,
            required_features=("web",),
            forbidden_features=("tenant",),
            forbidden_library_prefixes=("mybatis-",),
        )

        self.assertEqual(2, len(errors))
        self.assertTrue(
            all("archive and Spring Boot indexes" in error for error in errors)
        )
        self.assert_errors_contain(
            errors, ("feature tenant", "library prefix mybatis-")
        )

    def test_index_substring_collisions_are_not_artifact_tokens(self) -> None:
        self.write_archive(
            [
                "coco-web-2.0.2.jar",
                "not-coco-feature-tenant-2.0.2.jar",
                "not-mybatis-3.5.0.jar",
            ]
        )

        self.assertEqual(
            [],
            verifier.check_archive(
                self.archive,
                required_features=("web",),
                forbidden_features=("tenant",),
                forbidden_library_prefixes=("mybatis-",),
            ),
        )

    def test_required_feature_exact_binding_matrix(self) -> None:
        cases = {
            "exact": (
                ["coco-web-2.0.2.jar"],
                ["coco-web-2.0.2.jar"],
                (),
            ),
            "wrong-version": (
                ["coco-web-2.0.2.jar"],
                ["coco-web-2.0.1.jar"],
                ("does not bind exactly one archive jar", "matching archive artifact"),
            ),
            "legacy-canonical-mismatch": (
                ["coco-web-2.0.2.jar"],
                ["coco-feature-web-2.0.2.jar"],
                ("does not bind exactly one archive jar", "matching archive artifact"),
            ),
            "missing-token": (
                ["coco-web-2.0.2.jar", "not-coco-web-2.0.2.jar"],
                ["not-coco-web-2.0.2.jar"],
                ("matching archive artifact",),
            ),
        }

        for name, (libraries, indexed_libraries, expected) in cases.items():
            with self.subTest(name=name):
                self.write_archive(libraries, indexed_libraries=indexed_libraries)

                errors = verifier.check_archive(
                    self.archive,
                    required_features=("web",),
                )

                if expected:
                    self.assert_errors_contain(errors, expected)
                else:
                    self.assertEqual([], errors)

    def test_each_index_validates_required_feature_independently(self) -> None:
        web = "coco-web-2.0.2.jar"
        audit = "coco-audit-2.0.2.jar"
        cases = {
            "classpath-missing-layers-present": (
                [audit],
                [web, audit],
                "classpath.idx",
            ),
            "layers-missing-classpath-present": ([web, audit], [audit], "layers.idx"),
        }

        for name, (
            classpath_libraries,
            layers_libraries,
            failing_index,
        ) in cases.items():
            with self.subTest(name=name):
                self.write_archive(
                    [web, audit],
                    classpath_libraries=classpath_libraries,
                    layers_libraries=layers_libraries,
                )

                errors = verifier.check_archive(
                    self.archive,
                    required_features=("web",),
                )

                self.assertEqual(1, len(errors), errors)
                self.assert_errors_contain(
                    errors,
                    (failing_index, "feature web", "index found 0"),
                )

    def test_boot_index_files_are_optional(self) -> None:
        library = "coco-web-2.0.2.jar"
        cases = {
            "neither": (),
            "classpath-only": ("BOOT-INF/classpath.idx",),
            "layers-only": ("BOOT-INF/layers.idx",),
        }

        for name, include_indexes in cases.items():
            with self.subTest(name=name):
                self.write_archive([library], include_indexes=include_indexes)

                self.assertEqual(
                    [],
                    verifier.check_archive(
                        self.archive,
                        required_features=("web",),
                    ),
                )

    def test_empty_present_index_is_not_masked_by_other_index(self) -> None:
        library = "coco-web-2.0.2.jar"
        cases = {
            "classpath-empty": ("", None, "classpath.idx"),
            "layers-empty": (None, "", "layers.idx"),
        }

        for name, (classpath, layers, failing_index) in cases.items():
            with self.subTest(name=name):
                self.write_archive(
                    [library],
                    classpath=classpath,
                    layers=layers,
                )

                errors = verifier.check_archive(
                    self.archive,
                    required_features=("web",),
                )

                self.assertEqual(1, len(errors), errors)
                self.assert_errors_contain(
                    errors,
                    (failing_index, "feature web", "index found 0"),
                )

    def test_each_index_binds_old_or_canonical_to_exact_archive_coordinate(
        self,
    ) -> None:
        canonical = "coco-web-2.0.2.jar"
        old = "coco-feature-web-2.0.2.jar"
        cases = {
            "classpath-old-layers-canonical": ([old], [canonical], "classpath.idx"),
            "classpath-canonical-layers-old": ([canonical], [old], "layers.idx"),
        }

        for name, (
            classpath_libraries,
            layers_libraries,
            failing_index,
        ) in cases.items():
            with self.subTest(name=name):
                self.write_archive(
                    [canonical],
                    classpath_libraries=classpath_libraries,
                    layers_libraries=layers_libraries,
                )

                errors = verifier.check_archive(
                    self.archive,
                    required_features=("web",),
                )

                self.assertEqual(2, len(errors), errors)
                self.assertTrue(all(failing_index in error for error in errors), errors)
                self.assert_errors_contain(
                    errors,
                    (old, canonical, "does not bind exactly one archive jar"),
                )

    def test_index_parser_rejects_malformed_library_token_matrix(self) -> None:
        library = "coco-web-2.0.2.jar"
        token = f"BOOT-INF/lib/{library}"

        def index_pair(value: str) -> tuple[str, str]:
            return f'- "{value}"', f'- "dependencies":\n  - "{value}"'

        valid_classpath, valid_layers = index_pair(token)
        cases: dict[str, tuple[str | bytes, str | bytes]] = {
            "missing-prefix": index_pair(library),
            "missing-suffix": index_pair("BOOT-INF/lib/coco-web-2.0.2"),
            "nested-path": index_pair(f"BOOT-INF/lib/nested/{library}"),
            "parent-traversal": index_pair(f"BOOT-INF/lib/../{library}"),
            "backslash-path": index_pair(rf"BOOT-INF\lib\{library}"),
            "unquoted": (
                f"- {token}",
                f'- "dependencies":\n  - {token}',
            ),
            "raw-token": (token, f'- "dependencies":\n  {token}'),
            "comment": (f"# {valid_classpath}", f'- "dependencies":\n  # {token}'),
            "duplicate-token": (
                f"{valid_classpath}\n{valid_classpath}",
                f'{valid_layers}\n  - "{token}"',
            ),
            "mid-file-bom": (
                f"{valid_classpath}\n\ufeff{valid_classpath}",
                f'{valid_layers}\n\ufeff  - "{token}"',
            ),
            "invalid-utf8": (b"\xff", b"\xff"),
        }

        for name, (classpath, layers) in cases.items():
            for index_name in verifier.BOOT_INDEX_ENTRIES:
                with self.subTest(name=name, index=index_name):
                    self.write_archive(
                        [library],
                        classpath=(
                            classpath
                            if index_name == "BOOT-INF/classpath.idx"
                            else valid_classpath
                        ),
                        layers=(
                            layers
                            if index_name == "BOOT-INF/layers.idx"
                            else valid_layers
                        ),
                    )

                    errors = verifier.check_archive(
                        self.archive,
                        required_features=("web",),
                    )

                    self.assertEqual(1, len(errors), errors)
                    self.assertTrue(
                        "malformed Spring Boot index" in errors[0]
                        or "not valid UTF-8" in errors[0],
                        errors,
                    )
                    self.assertIn(index_name, errors[0])

    def test_index_parser_accepts_one_leading_utf8_bom_per_index(self) -> None:
        library = "coco-web-2.0.2.jar"
        classpath = self.classpath_index([library]).encode("utf-8")
        layers = self.layers_index([library]).encode("utf-8")

        for index_name in verifier.BOOT_INDEX_ENTRIES:
            with self.subTest(index=index_name):
                self.write_archive(
                    [library],
                    classpath=codecs.BOM_UTF8 + classpath
                    if index_name == "BOOT-INF/classpath.idx"
                    else classpath,
                    layers=codecs.BOM_UTF8 + layers
                    if index_name == "BOOT-INF/layers.idx"
                    else layers,
                )

                self.assertEqual(
                    [],
                    verifier.check_archive(
                        self.archive,
                        required_features=("web",),
                    ),
                )

    def test_malformed_archive_library_entry_matrix(self) -> None:
        malformed_entries: tuple[tuple[str, str | ZipInfo], ...] = (
            ("nested", "BOOT-INF/lib/nested/coco-web-2.0.2.jar"),
            ("parent-traversal", "BOOT-INF/lib/../coco-web-2.0.2.jar"),
            ("missing-suffix", "BOOT-INF/lib/coco-web-2.0.2"),
            (
                "backslash",
                self.raw_zip_info(r"BOOT-INF\lib\coco-web-2.0.2.jar"),
            ),
        )

        for name, entry in malformed_entries:
            with self.subTest(name=name):
                self.write_archive([], extra_entries=((entry, "library"),))

                errors = verifier.check_archive(self.archive)

                self.assertEqual(1, len(errors), errors)
                self.assertIn("malformed archive library entry", errors[0])

    def test_required_library_prefix_matches_archive_and_indexes(self) -> None:
        cases = {
            "exact": (
                ["coco-web-2.0.2.jar", "h2-2.2.224.jar"],
                ["coco-web-2.0.2.jar", "h2-2.2.224.jar"],
                (),
            ),
            "missing-index-token": (
                ["coco-web-2.0.2.jar", "h2-2.2.224.jar"],
                ["coco-web-2.0.2.jar"],
                ("required library prefix h2-", "Spring Boot index"),
            ),
            "orphan-index-token": (
                ["coco-web-2.0.2.jar"],
                ["coco-web-2.0.2.jar", "h2-2.2.224.jar"],
                (
                    "does not bind exactly one archive jar",
                    "required library prefix h2-",
                ),
            ),
        }

        for name, (libraries, indexed_libraries, expected) in cases.items():
            with self.subTest(name=name):
                self.write_archive(libraries, indexed_libraries=indexed_libraries)

                errors = verifier.check_archive(
                    self.archive,
                    required_library_prefixes=("h2-",),
                )

                if expected:
                    self.assert_errors_contain(errors, expected)
                else:
                    self.assertEqual([], errors)

    def test_codegen_requirement_matrix_has_one_normalized_production_path(
        self,
    ) -> None:
        coordinates = {
            "old": (["coco-feature-codegen-2.0.2.jar"], ()),
            "canonical": (["coco-codegen-2.0.2.jar"], ()),
            "missing": ([], ("found 0",)),
            "duplicate": (
                ["coco-feature-codegen-2.0.2.jar", "coco-codegen-2.0.2.jar"],
                ("found 2",),
            ),
        }
        modes = {
            "flag": ((), True),
            "required-feature": (("codegen",), False),
            "both": (("codegen",), True),
        }

        for coordinate, (libraries, expected) in coordinates.items():
            for mode, (features, require_codegen) in modes.items():
                with self.subTest(coordinate=coordinate, mode=mode):
                    self.write_archive(libraries)

                    errors = verifier.check_archive(
                        self.archive,
                        required_features=features,
                        require_codegen=require_codegen,
                    )

                    if expected:
                        self.assertEqual(1, len(errors), errors)
                        self.assert_errors_contain(errors, expected)
                        if require_codegen:
                            self.assertTrue(errors[0].startswith("codegen requires"))
                    else:
                        self.assertEqual([], errors)

    def test_duplicate_zip_entry_matrix_is_one_structural_error(self) -> None:
        library = "coco-web-2.0.2.jar"
        token = f"BOOT-INF/lib/{library}"
        cases = {
            "library": (token, "conflicting duplicate library"),
            "classpath-index": (
                "BOOT-INF/classpath.idx",
                '- "BOOT-INF/lib/conflicting-1.0.jar"',
            ),
            "layers-index": (
                "BOOT-INF/layers.idx",
                '- "dependencies":\n  - "BOOT-INF/lib/conflicting-1.0.jar"',
            ),
        }

        for name, duplicate in cases.items():
            with self.subTest(name=name), warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                self.write_archive(
                    [library],
                    extra_entries=(duplicate,),
                )

                errors = verifier.check_archive(
                    self.archive,
                    required_features=("web",),
                )

                self.assertEqual(1, len(errors), errors)
                self.assert_errors_contain(
                    errors,
                    (
                        "duplicate ZIP entry",
                        "appears 2 times",
                        "2 content variants",
                    ),
                )

    def test_index_read_is_bounded_by_real_zip_entry_size(self) -> None:
        class CountingZipFile(ZipFile):
            inventory_calls = 0

            def infolist(self) -> list[ZipInfo]:
                self.inventory_calls += 1
                return super().infolist()

        library = "coco-web-2.0.2.jar"
        oversized = b"x" * (verifier.MAX_INDEX_BYTES + 1)
        self.write_archive([library], classpath=oversized)

        with CountingZipFile(self.archive) as archive:
            inspection = verifier.inspect_open_archive(archive)

        self.assertEqual(1, archive.inventory_calls)
        errors = list(inspection.errors)
        self.assertEqual(1, len(errors), errors)
        self.assertIn("exceeds", errors[0])
        self.assertIn("byte limit", errors[0])

    def test_archive_read_failures_are_categorized(self) -> None:
        missing = Path(self.temporary_directory.name) / "missing.jar"
        directory = Path(self.temporary_directory.name) / "directory.jar"
        corrupt = Path(self.temporary_directory.name) / "corrupt.jar"
        directory.mkdir()
        corrupt.write_bytes(b"not a zip archive")

        cases = {
            "missing": (missing, "archive is missing"),
            "not-file": (directory, "archive path is not a regular file"),
            "not-zip": (corrupt, "archive is not a valid ZIP file"),
        }
        for name, (path, expected) in cases.items():
            with self.subTest(name=name):
                errors = verifier.check_archive(path, required_features=("web",))

                self.assertEqual(1, len(errors), errors)
                self.assertIn(expected, errors[0])

    def test_required_rules_without_indexes_use_archive_cardinality(self) -> None:
        with ZipFile(self.archive, "w") as archive:
            archive.writestr("BOOT-INF/lib/coco-web-2.0.2.jar", "library")

        errors = verifier.check_archive(
            self.archive,
            required_features=("web",),
            required_library_prefixes=("h2-",),
        )

        self.assertEqual(1, len(errors), errors)
        self.assert_errors_contain(errors, ("required library prefix h2-", "archive"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
