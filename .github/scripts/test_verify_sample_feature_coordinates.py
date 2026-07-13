from __future__ import annotations

import tempfile
import unittest
import warnings
from pathlib import Path
from zipfile import ZipFile

import verify_sample_feature_coordinates as verifier


class SampleFeatureCoordinateTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.archive = Path(self.temporary_directory.name) / "sample.jar"

    def write_archive(
        self, libraries: list[str], index_lines: list[str] | None = None
    ) -> None:
        lines = (
            index_lines
            if index_lines is not None
            else [f'- "BOOT-INF/lib/{library}"' for library in libraries]
        )
        with ZipFile(self.archive, "w") as archive:
            for library in libraries:
                archive.writestr(f"BOOT-INF/lib/{library}", "library")
            for entry in verifier.BOOT_INDEX_ENTRIES:
                archive.writestr(entry, "\n".join(lines))

    def test_old_coordinates_pass_before_source_moves(self) -> None:
        self.write_archive(
            [
                "coco-feature-web-2.0.2.jar",
                "coco-feature-audit-2.0.2.jar",
                "coco-feature-codegen-2.0.2.jar",
            ]
        )

        self.assertEqual(
            [],
            verifier.check_archive(
                self.archive,
                required_features=("web", "audit"),
                require_codegen=True,
            ),
        )

    def test_canonical_and_mixed_coordinates_pass_during_migration(self) -> None:
        self.write_archive(
            [
                "coco-web-2.0.2.jar",
                "coco-feature-audit-2.0.2.jar",
                "coco-security-2.0.2.jar",
                "coco-codegen-2.0.2.jar",
            ]
        )

        self.assertEqual(
            [],
            verifier.check_archive(
                self.archive,
                required_features=("web", "audit", "security"),
                require_codegen=True,
            ),
        )

    def test_duplicate_or_missing_feature_coordinate_fails(self) -> None:
        self.write_archive(
            [
                "coco-web-2.0.2.jar",
                "coco-feature-web-2.0.2.jar",
            ]
        )

        errors = verifier.check_archive(
            self.archive,
            required_features=("web", "audit"),
        )

        self.assertEqual(2, len(errors))
        self.assertIn("found 2", errors[0])
        self.assertIn("found 0", errors[1])

    def test_forbidden_feature_and_library_are_checked_in_indexes(self) -> None:
        self.write_archive(
            ["coco-web-2.0.2.jar"],
            [
                '- "BOOT-INF/lib/coco-web-2.0.2.jar"',
                "BOOT-INF/lib/coco-feature-tenant-2.0.2.jar",
                "BOOT-INF/lib/mybatis-3.5.0.jar",
            ],
        )

        errors = verifier.check_archive(
            self.archive,
            required_features=("web",),
            forbidden_features=("tenant",),
            forbidden_library_prefixes=("mybatis-",),
        )

        self.assertEqual(4, len(errors))
        self.assertEqual(
            2,
            sum("must bind exactly one archive jar" in error for error in errors),
        )
        self.assertTrue(any("tenant" in error for error in errors))
        self.assertTrue(any("mybatis-" in error for error in errors))

    def test_index_substring_collisions_are_not_artifact_tokens(self) -> None:
        self.write_archive(
            [
                "coco-web-2.0.2.jar",
                "not-coco-feature-tenant-2.0.2.jar",
                "not-mybatis-3.5.0.jar",
            ],
            [
                '- "BOOT-INF/lib/coco-web-2.0.2.jar"',
                '- "BOOT-INF/lib/not-coco-feature-tenant-2.0.2.jar"',
                '- "BOOT-INF/lib/not-mybatis-3.5.0.jar"',
            ],
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

    def test_required_feature_must_have_an_exact_index_jar_token(self) -> None:
        self.write_archive(
            ["coco-web-2.0.2.jar", "not-coco-web-2.0.2.jar"],
            ['- "BOOT-INF/lib/not-coco-web-2.0.2.jar"'],
        )

        errors = verifier.check_archive(
            self.archive,
            required_features=("web",),
        )

        self.assertEqual(1, len(errors))
        self.assertIn("Spring Boot index jar token", errors[0])

    def test_required_feature_index_token_binds_exact_archive_artifact(
        self,
    ) -> None:
        invalid_tokens = {
            "wrong-version": "BOOT-INF/lib/coco-web-2.0.1.jar",
            "legacy-canonical-mismatch": "BOOT-INF/lib/coco-feature-web-2.0.2.jar",
            "nested-path": "BOOT-INF/lib/nested/coco-web-2.0.2.jar",
            "parent-traversal": "BOOT-INF/lib/../coco-web-2.0.2.jar",
        }

        for name, token in invalid_tokens.items():
            with self.subTest(name=name):
                self.write_archive(
                    ["coco-web-2.0.2.jar"],
                    [f'- "{token}"'],
                )

                errors = verifier.check_archive(
                    self.archive,
                    required_features=("web",),
                )

                self.assertTrue(errors)
                if name in {"nested-path", "parent-traversal"}:
                    self.assertTrue(
                        any(
                            "direct BOOT-INF/lib/<filename>.jar token" in error
                            for error in errors
                        )
                    )
                else:
                    self.assertTrue(
                        any(
                            "must bind exactly one archive jar" in error
                            for error in errors
                        )
                    )

    def test_required_feature_rejects_duplicate_archive_jar_entry(self) -> None:
        library = "coco-web-2.0.2.jar"
        token = f"BOOT-INF/lib/{library}"
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            with ZipFile(self.archive, "w") as archive:
                archive.writestr(token, "first")
                archive.writestr(token, "second")
                for entry in verifier.BOOT_INDEX_ENTRIES:
                    archive.writestr(entry, f'- "{token}"')

        errors = verifier.check_archive(
            self.archive,
            required_features=("web",),
        )

        self.assertTrue(
            any(
                "must bind exactly one archive jar; found 2" in error
                for error in errors
            )
        )

    def test_codegen_requirement_covers_coordinate_matrix(self) -> None:
        cases = {
            "old": (["coco-feature-codegen-2.0.2.jar"], []),
            "canonical": (["coco-codegen-2.0.2.jar"], []),
            "missing": ([], ["found 0"]),
            "duplicate": (
                [
                    "coco-feature-codegen-2.0.2.jar",
                    "coco-codegen-2.0.2.jar",
                ],
                ["found 2"],
            ),
        }

        for name, (libraries, expected_errors) in cases.items():
            with self.subTest(name=name):
                self.write_archive(libraries)

                errors = verifier.check_archive(
                    self.archive,
                    require_codegen=True,
                )

                self.assertEqual(len(expected_errors), len(errors))
                for expected_error in expected_errors:
                    self.assertTrue(
                        any(expected_error in error for error in errors),
                        errors,
                    )

    def test_codegen_requirement_binds_exact_index_artifact(self) -> None:
        self.write_archive(
            ["coco-codegen-2.0.2.jar"],
            ['- "BOOT-INF/lib/coco-feature-codegen-2.0.2.jar"'],
        )

        errors = verifier.check_archive(
            self.archive,
            require_codegen=True,
        )

        self.assertTrue(
            any("must bind exactly one archive jar" in error for error in errors)
        )
        self.assertTrue(any("Spring Boot index jar token" in error for error in errors))

    def test_required_library_and_boot_indexes_are_enforced(self) -> None:
        with ZipFile(self.archive, "w") as archive:
            archive.writestr("BOOT-INF/lib/coco-web-2.0.2.jar", "library")

        errors = verifier.check_archive(
            self.archive,
            required_features=("web",),
            required_library_prefixes=("h2-",),
        )

        self.assertEqual(2, len(errors))
        self.assertIn("missing Spring Boot index", errors[0])
        self.assertIn("h2-", errors[1])


if __name__ == "__main__":
    unittest.main(verbosity=2)
