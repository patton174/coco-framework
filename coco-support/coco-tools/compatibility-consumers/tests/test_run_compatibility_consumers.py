from __future__ import annotations

import importlib.util
import re
import shutil
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock


HARNESS_ROOT = Path(__file__).resolve().parents[1]
RUNNER_PATH = HARNESS_ROOT / "run_compatibility_consumers.py"
SPEC = importlib.util.spec_from_file_location("coco_compatibility_runner", RUNNER_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"Cannot import {RUNNER_PATH}")
RUNNER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = RUNNER
SPEC.loader.exec_module(RUNNER)


class FixtureContractTests(unittest.TestCase):
    def test_fixture_contracts(self) -> None:
        RUNNER.validate_fixture_contracts(HARNESS_ROOT / "fixtures")

    def test_canonical_fixture_rejects_a_legacy_coordinate(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixtures = Path(temporary_directory) / "fixtures"
            shutil.copytree(HARNESS_ROOT / "fixtures", fixtures)
            pom = fixtures / "canonical" / "pom.xml"
            source = pom.read_text(encoding="utf-8")
            pom.write_text(
                source.replace(
                    "<artifactId>coco-web</artifactId>",
                    "<artifactId>coco-feature-web</artifactId>",
                    1,
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(RUNNER.HarnessError, "canonical consumer"):
                RUNNER.validate_fixture_contracts(fixtures)

    def test_subprocess_calls_are_explicitly_shell_free(self) -> None:
        compact_source = re.sub(r"\s+", "", RUNNER_PATH.read_text(encoding="utf-8"))
        self.assertIn("shell=False", compact_source)
        self.assertNotIn("shell=True", compact_source)

    def test_runtime_probe_dependencies_must_remain_test_scoped(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixtures = Path(temporary_directory) / "fixtures"
            shutil.copytree(HARNESS_ROOT / "fixtures", fixtures)
            pom = fixtures / "feature-api" / "pom.xml"
            source = pom.read_text(encoding="utf-8")
            pom.write_text(
                source.replace("<scope>test</scope>", "<scope>compile</scope>", 1),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(RUNNER.HarnessError, "runtime probe"):
                RUNNER.validate_fixture_contracts(fixtures)

    def test_runtime_probe_must_refresh_a_web_application_context(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixtures = Path(temporary_directory) / "fixtures"
            shutil.copytree(HARNESS_ROOT / "fixtures", fixtures)
            source_path = (
                fixtures / "feature-api" / RUNNER.RUNTIME_FEATURE_CONSUMER_SOURCE
            )
            source = source_path.read_text(encoding="utf-8")
            source_path.write_text(
                source.replace("WebApplicationContextRunner", "RemovedContextRunner"),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                RUNNER.HarnessError, "context-refresh evidence"
            ):
                RUNNER.validate_fixture_contracts(fixtures)


class JarContractTests(unittest.TestCase):
    def test_unique_implementation_class_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            implementation = root / "implementation.jar"
            facade = root / "facade.jar"
            self._write_jar(implementation, [RUNNER.FEATURE_CLASS_ENTRIES[0]])
            self._write_jar(facade, ["META-INF/MANIFEST.MF"])

            RUNNER.assert_unique_implementation_classes(
                [implementation, facade],
                class_prefixes=("io/github/coco/feature/",),
                expected_entries=(RUNNER.FEATURE_CLASS_ENTRIES[0],),
            )

    def test_duplicate_implementation_class_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            first = root / "first.jar"
            second = root / "second.jar"
            class_entry = RUNNER.FEATURE_CLASS_ENTRIES[0]
            self._write_jar(first, [class_entry])
            self._write_jar(second, [class_entry])

            with self.assertRaisesRegex(
                RUNNER.HarnessError, "Duplicate Coco implementation"
            ):
                RUNNER.assert_unique_implementation_classes(
                    [first, second],
                    class_prefixes=("io/github/coco/feature/",),
                    expected_entries=(class_entry,),
                )

    @staticmethod
    def _write_jar(path: Path, entries: list[str]) -> None:
        with zipfile.ZipFile(path, "w") as archive:
            for entry in entries:
                archive.writestr(entry, b"fixture")


class DiagnosticContractTests(unittest.TestCase):
    def test_alignment_diagnostic_accepts_required_message(self) -> None:
        RUNNER.assert_alignment_diagnostic(
            "Coco feature artifact versions must align with '2.0.2-SNAPSHOT': "
            "io.github.patton174:coco-feature-web:2.0.1.",
            "2.0.2-SNAPSHOT",
        )

    def test_alignment_diagnostic_rejects_generic_failure(self) -> None:
        with self.assertRaisesRegex(RUNNER.HarnessError, "required version-alignment"):
            RUNNER.assert_alignment_diagnostic("BUILD FAILURE", "2.0.2-SNAPSHOT")

    def test_runtime_evidence_accepts_matching_refreshed_context_marker(self) -> None:
        evidence = "COCO_RUNTIME_REGISTRATION_OK profile=canonical-only context=example.Context"
        self.assertEqual(
            RUNNER.assert_runtime_registration_evidence(evidence, "canonical-only"),
            evidence,
        )

    def test_runtime_evidence_rejects_missing_refreshed_context_marker(self) -> None:
        with self.assertRaisesRegex(RUNNER.HarnessError, "refreshed-context evidence"):
            RUNNER.assert_runtime_registration_evidence("BUILD SUCCESS", "alias-only")


class RuntimeRegistrationRunnerTests(unittest.TestCase):
    def test_all_candidate_profiles_compile_and_run_the_runtime_probe(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixtures = Path(temporary_directory) / "fixtures"
            (fixtures / "feature-api").mkdir(parents=True)
            harness = _RecordingHarness(fixtures)

            def completed_probe(label: str, arguments: list[str], **_: object):
                classpath_name = arguments[-1]
                return RUNNER.subprocess.CompletedProcess(
                    arguments,
                    0,
                    stdout=(
                        f"COCO_RUNTIME_REGISTRATION_OK profile={classpath_name} "
                        "context=example.ServletContext"
                    ),
                )

            with (
                mock.patch.object(RUNNER, "assert_artifacts_present") as present,
                mock.patch.object(RUNNER, "assert_artifacts_absent") as absent,
                mock.patch.object(
                    RUNNER, "run_command", side_effect=completed_probe
                ) as run_command,
            ):
                RUNNER.verify_runtime_feature_registrations(harness, "2.0.2-SNAPSHOT")

            self.assertEqual(
                [call["profile"] for call in harness.classpath_calls],
                ["canonical", "aliases", "mixed"],
            )
            self.assertTrue(
                all(
                    call["compile_goal"] == "test-compile"
                    for call in harness.classpath_calls
                )
            )
            self.assertEqual(
                [call.args[1][-1] for call in run_command.call_args_list],
                ["canonical-only", "alias-only", "same-version-mixed"],
            )
            self.assertEqual(present.call_count, 5)
            absent.assert_called_once()


class _RecordingHarness:
    def __init__(self, fixtures: Path) -> None:
        self.java = "java"
        self.env: dict[str, str] = {}
        self.fixtures = fixtures
        self.classpath_calls: list[dict[str, object]] = []

    def build_classpath(self, fixture_name: str, version: str, **arguments: object):
        self.classpath_calls.append(
            {"fixture_name": fixture_name, "version": version, **arguments}
        )
        return []


if __name__ == "__main__":
    unittest.main()
