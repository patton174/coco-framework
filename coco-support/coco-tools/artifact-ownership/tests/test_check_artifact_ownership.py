from __future__ import annotations

import argparse
import sys
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile


SCRIPT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

import check_artifact_ownership as checker  # noqa: E402


class ArtifactOwnershipTest(unittest.TestCase):
    VERSION = "2.0.2-SNAPSHOT"

    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)

    def write_jar(
        self,
        spec: checker.ArtifactSpec,
        entries: list[str],
        classifier: str | None = None,
    ) -> Path:
        path = checker.artifact_jar_path(self.root, spec, self.VERSION, classifier)
        path.parent.mkdir(parents=True, exist_ok=True)
        with ZipFile(path, "w") as archive:
            for entry in entries:
                archive.writestr(entry, f"content for {entry}")
        return path

    @staticmethod
    def canonical_spec() -> checker.ArtifactSpec:
        return checker.ArtifactSpec(
            "canonical-sample",
            Path("modules/canonical-sample"),
            checker.CANONICAL,
            "io.github.coco.SampleImplementation",
        )

    @staticmethod
    def legacy_spec() -> checker.ArtifactSpec:
        return checker.ArtifactSpec(
            "legacy-sample",
            Path("modules/legacy-sample"),
            checker.LEGACY,
        )

    def test_inventory_covers_all_canonical_and_legacy_artifacts(self) -> None:
        self.assertEqual(9, len(checker.CANONICAL_ARTIFACTS))
        self.assertEqual(10, len(checker.LEGACY_ARTIFACTS))
        self.assertEqual(
            {
                "coco-spring-boot-autoconfigure",
                "coco-web",
                "coco-mybatis-plus",
                "coco-tenant",
                "coco-data-permission",
                "coco-audit",
                "coco-openapi",
                "coco-security",
                "coco-test-support",
            },
            {spec.artifact_id for spec in checker.CANONICAL_ARTIFACTS},
        )
        self.assertEqual(
            set(checker.LEGACY_ARTIFACT_IDS),
            {spec.artifact_id for spec in checker.LEGACY_ARTIFACTS},
        )

    def test_canonical_main_jar_contains_representative_class(self) -> None:
        spec = self.canonical_spec()
        self.write_jar(spec, [spec.representative_entry(".class")])

        self.assertEqual([], checker.check_artifact(self.root, spec, self.VERSION))

    def test_canonical_attached_jars_contain_representative_type(self) -> None:
        spec = self.canonical_spec()
        self.write_jar(spec, [spec.representative_entry(".class")])
        self.write_jar(spec, [spec.representative_entry(".java")], classifier="sources")
        self.write_jar(spec, [spec.representative_entry(".html")], classifier="javadoc")

        self.assertEqual(
            [],
            checker.check_artifact(
                self.root, spec, self.VERSION, require_attached=True
            ),
        )

    def test_legacy_jars_allow_only_metadata_and_empty_attached_docs(self) -> None:
        spec = self.legacy_spec()
        self.write_jar(
            spec,
            [
                "META-INF/MANIFEST.MF",
                "META-INF/maven/io.github.coco/legacy-sample/pom.xml",
                "META-INF/maven/io.github.coco/legacy-sample/pom.properties",
                "META-INF/LICENSE.txt",
            ],
        )
        self.write_jar(spec, ["META-INF/MANIFEST.MF"], classifier="sources")
        self.write_jar(
            spec,
            ["index.html", "allclasses-index.html"],
            classifier="javadoc",
        )

        self.assertEqual(
            [],
            checker.check_artifact(
                self.root, spec, self.VERSION, require_attached=True
            ),
        )

    def test_missing_and_unreadable_main_jars_are_reported(self) -> None:
        spec = self.canonical_spec()
        missing_errors = checker.check_artifact(self.root, spec, self.VERSION)
        self.assertIn("missing main JAR", missing_errors[0])

        path = checker.artifact_jar_path(self.root, spec, self.VERSION)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(b"not a zip file")

        unreadable_errors = checker.check_artifact(self.root, spec, self.VERSION)
        self.assertIn("unreadable main JAR", unreadable_errors[0])

    def test_canonical_missing_representative_entries_are_reported(self) -> None:
        spec = self.canonical_spec()
        self.write_jar(spec, ["io/github/coco/Other.class"])
        self.write_jar(spec, ["io/github/coco/Other.java"], classifier="sources")
        self.write_jar(spec, ["io/github/coco/Other.html"], classifier="javadoc")

        errors = checker.check_artifact(
            self.root, spec, self.VERSION, require_attached=True
        )

        self.assertEqual(3, len(errors))
        self.assertIn("missing representative class", errors[0])
        self.assertIn("missing representative source", errors[1])
        self.assertIn("missing representative type page", errors[2])

    def test_legacy_main_rejects_classes_and_implementation_resources(self) -> None:
        spec = self.legacy_spec()
        self.write_jar(
            spec,
            [
                "io/github/coco/Legacy.class",
                "META-INF/spring/org.springframework.boot.autoconfigure."
                "AutoConfiguration.imports",
                "coco-feature-web-messages.properties",
            ],
        )

        errors = checker.check_artifact(self.root, spec, self.VERSION)

        self.assertEqual(2, len(errors))
        self.assertIn("contains .class entries", errors[0])
        self.assertIn("contains implementation resources", errors[1])
        self.assertIn("AutoConfiguration.imports", errors[1])
        self.assertIn("messages.properties", errors[1])

    def test_legacy_attached_jars_reject_java_and_type_pages(self) -> None:
        spec = self.legacy_spec()
        self.write_jar(spec, ["META-INF/MANIFEST.MF"])
        self.write_jar(spec, ["io/github/coco/Legacy.java"], classifier="sources")
        self.write_jar(
            spec,
            [
                "io/github/coco/Legacy.html",
                "io/github/coco/Index.html",
                "io/github/coco/Uppercase.HTML",
                "io/github/coco/class-use/Legacy.html",
                "io/github/coco/package-summary.html",
            ],
            classifier="javadoc",
        )

        errors = checker.check_artifact(
            self.root, spec, self.VERSION, require_attached=True
        )

        self.assertEqual(2, len(errors))
        self.assertIn("sources JAR contains .java entries", errors[0])
        self.assertIn("Javadoc JAR contains type pages", errors[1])
        self.assertIn("io/github/coco/Index.html", errors[1])
        self.assertNotIn("package-summary.html", errors[1])

    def test_require_attached_reports_both_missing_jars(self) -> None:
        spec = self.canonical_spec()
        self.write_jar(spec, [spec.representative_entry(".class")])

        errors = checker.check_artifact(
            self.root, spec, self.VERSION, require_attached=True
        )

        self.assertEqual(2, len(errors))
        self.assertIn("missing sources JAR", errors[0])
        self.assertIn("missing Javadoc JAR", errors[1])

    def test_repository_check_aggregates_artifact_errors(self) -> None:
        canonical = self.canonical_spec()
        legacy = self.legacy_spec()
        self.write_jar(canonical, ["wrong/Type.class"])
        self.write_jar(legacy, ["wrong/Legacy.class"])

        errors = checker.check_repository(
            self.root,
            self.VERSION,
            specs=(canonical, legacy),
        )

        self.assertEqual(2, len(errors))
        self.assertIn("canonical-sample", errors[0])
        self.assertIn("legacy-sample", errors[1])

    def test_repository_root_is_found_from_nested_script_path(self) -> None:
        script = self.root / "coco-support/coco-tools/artifact-ownership/checker.py"
        script.parent.mkdir(parents=True)
        script.touch()
        (self.root / "pom.xml").touch()

        self.assertEqual(self.root, checker.find_repository_root(script))

    def test_artifact_version_rejects_path_segments(self) -> None:
        self.assertEqual(self.VERSION, checker.validate_artifact_version(self.VERSION))
        for invalid in ("", "../2.0.0", "nested/2.0.0", "nested\\2.0.0"):
            with self.subTest(invalid=invalid):
                with self.assertRaises(argparse.ArgumentTypeError):
                    checker.validate_artifact_version(invalid)


if __name__ == "__main__":
    unittest.main()
