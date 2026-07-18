from __future__ import annotations

import json
import tempfile
import unittest
from collections import Counter
from datetime import UTC, datetime
from pathlib import Path

import release_file_count_preflight as preflight


ROOT_POM = """\
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>fixture-root</artifactId>
  <version>${revision}</version>
  <packaging>pom</packaging>
  <modules><module>library</module></modules>
  <properties><central.checksums>ALL</central.checksums></properties>
  <profiles>
    <profile>
      <id>release</id>
      <build><plugins>
        <plugin>
          <artifactId>maven-source-plugin</artifactId>
          <executions><execution><goals><goal>jar-no-fork</goal></goals></execution></executions>
        </plugin>
        <plugin>
          <artifactId>maven-javadoc-plugin</artifactId>
          <executions><execution><goals><goal>jar</goal></goals></execution></executions>
        </plugin>
        <plugin>
          <artifactId>maven-gpg-plugin</artifactId>
          <executions><execution><phase>verify</phase><goals><goal>sign</goal></goals></execution></executions>
        </plugin>
        <plugin>
          <artifactId>central-publishing-maven-plugin</artifactId>
          <configuration><checksums>${central.checksums}</checksums></configuration>
        </plugin>
      </plugins></build>
    </profile>
  </profiles>
</project>
"""

CHILD_POM = """\
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>example</groupId>
    <artifactId>fixture-root</artifactId>
    <version>${revision}</version>
  </parent>
  <artifactId>fixture-library</artifactId>
</project>
"""

EXPECTATIONS = {
    "schema_version": 1,
    "group_id": "example",
    "checksum_suffixes": list(preflight.CHECKSUM_SUFFIXES),
    "modules": [
        {
            "path": ".",
            "artifact_id": "fixture-root",
            "packaging": "pom",
            "artifacts": ["pom"],
        },
        {
            "path": "library",
            "artifact_id": "fixture-library",
            "packaging": "jar",
            "artifacts": ["pom", "main", "sources", "javadoc"],
        },
    ],
}


class ReleaseFileCountPreflightTests(unittest.TestCase):
    revision = "1.2.3"

    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        (self.root / "target").mkdir()
        (self.root / "library" / "target").mkdir(parents=True)
        (self.root / "pom.xml").write_text(ROOT_POM, encoding="utf-8")
        (self.root / "library" / "pom.xml").write_text(CHILD_POM, encoding="utf-8")
        expectation_path = (
            self.root / ".github" / "release" / "release-artifact-expectations.json"
        )
        expectation_path.parent.mkdir(parents=True)
        expectation_path.write_text(json.dumps(EXPECTATIONS), encoding="utf-8")
        self.sign(self.root / "target" / f"fixture-root-{self.revision}.pom")
        self.sign(
            self.root / "library" / "target" / f"fixture-library-{self.revision}.pom"
        )
        prefix = self.root / "library" / "target" / f"fixture-library-{self.revision}"
        self.sign(prefix.with_name(f"{prefix.name}.jar"))
        self.sign(prefix.with_name(f"{prefix.name}-sources.jar"))
        self.sign(prefix.with_name(f"{prefix.name}-javadoc.jar"))

    @staticmethod
    def sign(path: Path) -> None:
        path.write_bytes(f"fixture:{path.name}".encode())
        path.with_name(f"{path.name}.asc").write_text(
            f"signature:{path.name}", encoding="ascii"
        )

    def inventory(self) -> preflight.Inventory:
        return preflight.build_inventory(self.root, self.revision)

    def test_inventory_uses_actual_signed_release_output_paths(self) -> None:
        inventory = self.inventory()

        self.assertEqual(2, inventory.reactor_projects)
        self.assertEqual(5, len(inventory.base_files))
        self.assertEqual(5, len(inventory.signature_files))
        self.assertEqual(20, len(inventory.checksum_files))
        self.assertEqual(10, inventory.signed_payload_files)
        self.assertEqual(
            {
                "fixture-library-1.2.3.jar",
                "fixture-library-1.2.3-sources.jar",
                "fixture-library-1.2.3-javadoc.jar",
            },
            {
                item.published_name
                for item in inventory.base_files
                if item.module == "library" and item.kind != "pom"
            },
        )
        self.assertTrue(
            all(item.source_path is not None for item in inventory.base_files)
        )

    def test_additional_signed_attachment_fails_exact_expected_set(
        self,
    ) -> None:
        attachment = (
            self.root
            / "library"
            / "target"
            / f"fixture-library-{self.revision}-tests.jar"
        )
        self.sign(attachment)

        with self.assertRaisesRegex(preflight.PreflightError, "extra=.*tests.jar"):
            self.inventory()

    def test_non_reactor_sample_outputs_are_excluded(self) -> None:
        sample = self.root / "coco-samples" / "sample" / "target" / "sample-1.2.3.jar"
        sample.parent.mkdir(parents=True)
        self.sign(sample)

        inventory = self.inventory()

        self.assertEqual(10, inventory.signed_payload_files)
        self.assertFalse(
            any(
                "coco-samples" in (item.source_path or "")
                for item in inventory.base_files
            )
        )

    def test_missing_required_sources_fails_closed(self) -> None:
        sources = (
            self.root
            / "library"
            / "target"
            / f"fixture-library-{self.revision}-sources.jar"
        )
        sources.unlink()

        with self.assertRaisesRegex(
            preflight.PreflightError, "signed outputs differ from expectations"
        ):
            self.inventory()

    def test_missing_signature_fails_closed(self) -> None:
        signature = (
            self.root
            / "library"
            / "target"
            / f"fixture-library-{self.revision}-javadoc.jar.asc"
        )
        signature.unlink()

        with self.assertRaisesRegex(preflight.PreflightError, "javadoc.jar"):
            self.inventory()

    def test_release_profile_requires_all_checksums(self) -> None:
        root_pom = self.root / "pom.xml"
        root_pom.write_text(
            ROOT_POM.replace(
                "<central.checksums>ALL</central.checksums>",
                "<central.checksums>REQUIRED</central.checksums>",
            ),
            encoding="utf-8",
        )

        with self.assertRaisesRegex(preflight.PreflightError, "checksums as ALL"):
            self.inventory()

    def test_inventory_json_is_deterministic(self) -> None:
        first = json.dumps(self.inventory().to_dict(), sort_keys=True)
        second = json.dumps(self.inventory().to_dict(), sort_keys=True)

        self.assertEqual(first, second)

    def test_repository_expectations_preserve_exact_release_set(self) -> None:
        repository_root = Path(__file__).resolve().parents[2]
        modules = preflight.load_expectations(
            repository_root
            / ".github"
            / "release"
            / "release-artifact-expectations.json"
        )
        counts = Counter(kind for module in modules for kind in module.artifacts)

        self.assertEqual({"pom": 24, "main": 20, "sources": 17, "javadoc": 17}, counts)
        self.assertEqual(
            {
                "coco-config",
                "coco-feature-runtime",
                "coco-spring-boot-starter",
            },
            {
                module.artifact_id
                for module in modules
                if module.artifacts == ("pom", "main")
            },
        )
        self.assertEqual(
            ("pom", "main", "sources", "javadoc"),
            next(
                module.artifacts
                for module in modules
                if module.artifact_id == "coco-maven-plugin"
            ),
        )

    def test_capacity_passes_exactly_at_zero_remaining(self) -> None:
        capacity = preflight.validate_capacity(
            limit="100",
            used="70",
            period="2026-07",
            source="central-portal organization usage",
            count_checksums="true",
            metadata_files="0",
            current_period="2026-07",
        )

        assessment = capacity.assess(self.inventory())

        self.assertTrue(assessment.passed)
        self.assertEqual(0, assessment.remaining_after_release)
        self.assertEqual(0, assessment.shortfall)

    def test_capacity_failure_has_actionable_arithmetic(self) -> None:
        capacity = preflight.validate_capacity(
            limit="100",
            used="71",
            period="2026-07",
            source="central-portal organization usage",
            count_checksums="true",
            metadata_files="0",
            current_period="2026-07",
        )
        inventory = self.inventory()

        assessment = capacity.assess(inventory)
        report = preflight.render_report(inventory, assessment)

        self.assertFalse(assessment.passed)
        self.assertEqual(1, assessment.shortfall)
        for expected in (
            "Limit: **100**",
            "Used: **71**",
            "Required by this release: **30**",
            "Shortfall: **1**",
            "at least **101**",
        ):
            self.assertIn(expected, report)

    def test_invalid_or_stale_capacity_configuration_fails_closed(self) -> None:
        cases = {
            "missing-limit": ("", "1", "2026-07", "source", "non-negative integer"),
            "negative-used": ("10", "-1", "2026-07", "source", "non-negative integer"),
            "used-over-limit": ("10", "11", "2026-07", "source", "must not exceed"),
            "invalid-period": ("10", "1", "2026-13", "source", "YYYY-MM"),
            "stale-period": ("10", "1", "2026-06", "source", "stale"),
            "missing-source": ("10", "1", "2026-07", "  ", "non-empty"),
        }
        for name, (limit, used, period, source, message) in cases.items():
            with (
                self.subTest(name=name),
                self.assertRaisesRegex(preflight.PreflightError, message),
            ):
                preflight.validate_capacity(
                    limit=limit,
                    used=used,
                    period=period,
                    source=source,
                    count_checksums="true",
                    metadata_files="0",
                    current_period="2026-07",
                )

    def test_quota_expansion_is_explicitly_configurable(self) -> None:
        inventory = self.inventory()
        without_expansion = preflight.validate_capacity(
            limit="100",
            used="0",
            period="2026-07",
            source="source",
            count_checksums="false",
            metadata_files="0",
            current_period="2026-07",
        ).assess(inventory)
        with_expansion = preflight.validate_capacity(
            limit="100",
            used="0",
            period="2026-07",
            source="source",
            count_checksums="true",
            metadata_files="7",
            current_period="2026-07",
        ).assess(inventory)

        self.assertEqual(10, without_expansion.required)
        self.assertEqual(0, without_expansion.checksum_expansion)
        self.assertEqual(37, with_expansion.required)
        self.assertEqual(20, with_expansion.checksum_expansion)
        self.assertEqual(7, with_expansion.metadata_expansion)

    def test_invalid_quota_expansion_configuration_fails_closed(self) -> None:
        for field, count_checksums, metadata_files, message in (
            ("checksums", "yes", "0", "true.*false"),
            ("metadata", "true", "-1", "non-negative integer"),
        ):
            with (
                self.subTest(field=field),
                self.assertRaisesRegex(preflight.PreflightError, message),
            ):
                preflight.validate_capacity(
                    limit="100",
                    used="0",
                    period="2026-07",
                    source="source",
                    count_checksums=count_checksums,
                    metadata_files=metadata_files,
                    current_period="2026-07",
                )

    def test_cli_writes_inventory_and_summary(self) -> None:
        output = self.root / "inventory.json"
        summary = self.root / "summary.md"
        period = datetime.now(UTC).strftime("%Y-%m")

        result = preflight.main(
            [
                "--limit",
                "100",
                "--used",
                "70",
                "--period",
                period,
                "--source",
                "central-portal organization usage",
                "--count-checksums",
                "true",
                "--metadata-files",
                "0",
                "--reactor-root",
                str(self.root),
                "--revision",
                self.revision,
                "--inventory-output",
                str(output),
                "--summary-output",
                str(summary),
            ]
        )

        self.assertEqual(0, result)
        result_json = json.loads(output.read_text(encoding="utf-8"))
        self.assertEqual(10, result_json["signed_payload_files"])
        self.assertEqual(30, result_json["quota_assessment"]["required"])
        self.assertIn("Result: **PASS**", summary.read_text(encoding="utf-8"))


class ReleaseWorkflowTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.repository_root = Path(__file__).resolve().parents[2]
        cls.workflow = (
            cls.repository_root / ".github" / "workflows" / "release.yml"
        ).read_text(encoding="utf-8")

    def capacity_job(self) -> str:
        start = self.workflow.index("  central-capacity:")
        end = self.workflow.index("\n  publish:", start)
        return self.workflow[start:end]

    def test_capacity_job_is_secretless_and_cannot_publish(self) -> None:
        job = self.capacity_job()

        self.assertNotIn("secrets.", job)
        self.assertNotRegex(job, r"(?i)\bdeploy\b")
        self.assertIn("-Dgpg.skip=false", job)
        self.assertIn("clean verify", job)

    def test_capacity_job_uses_protected_current_configuration(self) -> None:
        job = self.capacity_job()

        self.assertIn("environment: coco-spring", job)
        for variable in (
            "CENTRAL_FILE_LIMIT",
            "CENTRAL_FILE_USED",
            "CENTRAL_FILE_PERIOD",
            "CENTRAL_FILE_SOURCE",
            "CENTRAL_FILE_COUNT_CHECKSUMS",
            "CENTRAL_FILE_METADATA_FILES",
        ):
            self.assertIn(f"vars.{variable}", job)
        self.assertIn("--validate-config-only", job)

    def test_publish_hard_depends_on_capacity_preflight(self) -> None:
        publish = self.workflow[self.workflow.index("  publish:") :]

        self.assertRegex(
            publish,
            r"(?s)needs:\s+.*- test\s+.*- central-capacity\s+.*Publish",
        )


if __name__ == "__main__":
    unittest.main()
