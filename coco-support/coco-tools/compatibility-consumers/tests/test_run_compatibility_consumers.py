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

    def test_feature_consumer_must_keep_the_live_basename_runtime_probe(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixtures = Path(temporary_directory) / "fixtures"
            shutil.copytree(HARNESS_ROOT / "fixtures", fixtures)
            source_path = (
                fixtures
                / "feature-api/src/main/java/io/github/coco/consumer/FeatureApiConsumer.java"
            )
            source = source_path.read_text(encoding="utf-8")
            source_path.write_text(
                source.replace(RUNNER.I18N_BASENAME_CONSUMER_EVIDENCE, "REMOVED"),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                RUNNER.HarnessError, "live basename-list evidence"
            ):
                RUNNER.validate_fixture_contracts(fixtures)

    def test_feature_consumer_must_keep_the_common_locale_factory_probe(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixtures = Path(temporary_directory) / "fixtures"
            shutil.copytree(HARNESS_ROOT / "fixtures", fixtures)
            source_path = (
                fixtures
                / "feature-api/src/main/java/io/github/coco/consumer/FeatureApiConsumer.java"
            )
            source = source_path.read_text(encoding="utf-8")
            source_path.write_text(
                source.replace(
                    RUNNER.COMMON_LOCALE_FACTORY_CONSUMER_EVIDENCE, "REMOVED"
                ),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(
                RUNNER.HarnessError, "common locale factory ABI evidence"
            ):
                RUNNER.validate_fixture_contracts(fixtures)

    def test_feature_consumer_must_keep_the_locale_pass_through_probe(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixtures = Path(temporary_directory) / "fixtures"
            shutil.copytree(HARNESS_ROOT / "fixtures", fixtures)
            source_path = (
                fixtures
                / "feature-api/src/main/java/io/github/coco/consumer/FeatureApiConsumer.java"
            )
            source = source_path.read_text(encoding="utf-8")
            source_path.write_text(
                source.replace(RUNNER.LOCALE_PASS_THROUGH_CONSUMER_EVIDENCE, "REMOVED"),
                encoding="utf-8",
            )

            with self.assertRaisesRegex(RUNNER.HarnessError, "locale pass-through evidence"):
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

    def test_runtime_evidence_accepts_one_exact_refreshed_context_marker(self) -> None:
        evidence = "COCO_RUNTIME_REGISTRATION_OK profile=canonical-only"
        self.assertEqual(
            RUNNER.assert_runtime_registration_evidence(evidence, "canonical-only"),
            evidence,
        )

    def test_runtime_evidence_rejects_missing_refreshed_context_marker(self) -> None:
        with self.assertRaisesRegex(RUNNER.HarnessError, "refreshed-context"):
            RUNNER.assert_runtime_registration_evidence("BUILD SUCCESS", "alias-only")

    def test_protected_markers_reject_forged_and_duplicate_lines(self) -> None:
        protected_markers = (
            (
                "COCO_RUNTIME_REGISTRATION_OK profile=canonical-only",
                lambda output: RUNNER.assert_runtime_registration_evidence(
                    output, "canonical-only"
                ),
            ),
            (
                RUNNER.I18N_BASENAME_CONSUMER_EVIDENCE,
                RUNNER.assert_i18n_basename_consumer_evidence,
            ),
            (
                RUNNER.COMMON_LOCALE_FACTORY_CONSUMER_EVIDENCE,
                RUNNER.assert_common_locale_factory_consumer_evidence,
            ),
            (
                RUNNER.LOCALE_PASS_THROUGH_CONSUMER_EVIDENCE,
                RUNNER.assert_locale_pass_through_consumer_evidence,
            ),
        )
        for marker, assertion in protected_markers:
            self.assertEqual(assertion(marker), marker)
            for forged in ("prefix " + marker, marker + " suffix", marker + "\n" + marker):
                with self.assertRaises(RUNNER.HarnessError):
                    assertion(forged)

    def test_i18n_basename_evidence_accepts_the_old_consumer_marker(self) -> None:
        self.assertEqual(
            RUNNER.assert_i18n_basename_consumer_evidence(
                RUNNER.I18N_BASENAME_CONSUMER_EVIDENCE
            ),
            RUNNER.I18N_BASENAME_CONSUMER_EVIDENCE,
        )

    def test_i18n_basename_evidence_rejects_missing_old_consumer_marker(self) -> None:
        with self.assertRaisesRegex(RUNNER.HarnessError, "live basename-list"):
            RUNNER.assert_i18n_basename_consumer_evidence("BUILD SUCCESS")

    def test_common_locale_factory_evidence_accepts_the_old_consumer_marker(
        self,
    ) -> None:
        self.assertEqual(
            RUNNER.assert_common_locale_factory_consumer_evidence(
                RUNNER.COMMON_LOCALE_FACTORY_CONSUMER_EVIDENCE
            ),
            RUNNER.COMMON_LOCALE_FACTORY_CONSUMER_EVIDENCE,
        )

    def test_common_locale_factory_evidence_rejects_missing_old_consumer_marker(
        self,
    ) -> None:
        with self.assertRaisesRegex(RUNNER.HarnessError, "common locale factory"):
            RUNNER.assert_common_locale_factory_consumer_evidence("BUILD SUCCESS")

    def test_locale_pass_through_evidence_requires_one_exact_marker(self) -> None:
        self.assertEqual(
            RUNNER.assert_locale_pass_through_consumer_evidence(
                RUNNER.LOCALE_PASS_THROUGH_CONSUMER_EVIDENCE
            ),
            RUNNER.LOCALE_PASS_THROUGH_CONSUMER_EVIDENCE,
        )
        with self.assertRaisesRegex(RUNNER.HarnessError, "exactly one locale"):
            RUNNER.assert_locale_pass_through_consumer_evidence("BUILD SUCCESS")
        with self.assertRaisesRegex(RUNNER.HarnessError, "exactly one locale"):
            RUNNER.assert_locale_pass_through_consumer_evidence(
                "forged " + RUNNER.LOCALE_PASS_THROUGH_CONSUMER_EVIDENCE
            )
        with self.assertRaisesRegex(RUNNER.HarnessError, "exactly one locale"):
            RUNNER.assert_locale_pass_through_consumer_evidence(
                RUNNER.LOCALE_PASS_THROUGH_CONSUMER_EVIDENCE
                + "\n"
                + RUNNER.LOCALE_PASS_THROUGH_CONSUMER_EVIDENCE
            )

    def test_class_hash_check_rejects_mutation(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            class_file = Path(temporary_directory) / "FeatureApiConsumer.class"
            class_file.write_bytes(b"baseline")
            baseline_hash = RUNNER.sha256_file(class_file)
            RUNNER.assert_class_file_unchanged(class_file, baseline_hash, "baseline")
            class_file.write_bytes(b"mutated")
            with self.assertRaisesRegex(RUNNER.HarnessError, "changed the fixed 2.0.1"):
                RUNNER.assert_class_file_unchanged(class_file, baseline_hash, "test")


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
                    stdout=f"COCO_RUNTIME_REGISTRATION_OK profile={classpath_name}",
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


class BinaryCompatibilityRunnerTests(unittest.TestCase):
    def test_runs_fixed_class_on_aliases_then_canonical_without_recompiling(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixtures = Path(temporary_directory) / "fixtures"
            class_file = self._write_feature_consumer_class(fixtures)
            baseline_hash = RUNNER.sha256_file(class_file)
            dependencies = [Path("first.jar"), Path("second.jar"), Path("third.jar")]
            harness = _RecordingHarness(fixtures, dependencies)

            def completed_consumer(_: str, arguments: list[str], **__: object):
                output = "\n".join(
                    (
                        RUNNER.I18N_BASENAME_CONSUMER_EVIDENCE,
                        RUNNER.COMMON_LOCALE_FACTORY_CONSUMER_EVIDENCE,
                        RUNNER.LOCALE_PASS_THROUGH_CONSUMER_EVIDENCE,
                        *(entry.removesuffix(".class").replace("/", ".")
                          for entry in RUNNER.FEATURE_CLASS_ENTRIES),
                    )
                )
                return RUNNER.subprocess.CompletedProcess(arguments, 0, stdout=output)

            with (
                mock.patch.object(RUNNER, "assert_artifacts_present"),
                mock.patch.object(RUNNER, "assert_artifacts_absent") as absent,
                mock.patch.object(RUNNER, "assert_facades_source_free"),
                mock.patch.object(
                    RUNNER,
                    "assert_class_file_unchanged",
                    wraps=RUNNER.assert_class_file_unchanged,
                ) as hash_check,
                mock.patch.object(RUNNER, "run_command", side_effect=completed_consumer) as run_command,
            ):
                RUNNER.run_binary_compatibility(harness, "2.0.2-SNAPSHOT", baseline_hash)

            self.assertEqual([call["profile"] for call in harness.classpath_calls], ["aliases", "canonical"])
            self.assertTrue(all(call["clean"] is False for call in harness.classpath_calls))
            self.assertEqual(hash_check.call_count, 6)
            self.assertEqual(
                [call.args[1][2].split(RUNNER.os.pathsep)[0] for call in run_command.call_args_list],
                [str(fixtures / "feature-api/target/classes")] * 2,
            )
            self.assertEqual(
                [call.args[1][2].split(RUNNER.os.pathsep)[-3:] for call in run_command.call_args_list],
                [[str(dependency) for dependency in dependencies]] * 2,
            )
            absent.assert_called_once()

    def test_propagates_canonical_run_failure_after_alias_checks(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            fixtures = Path(temporary_directory) / "fixtures"
            class_file = self._write_feature_consumer_class(fixtures)
            harness = _RecordingHarness(fixtures)
            successful_output = "\n".join(
                (
                    RUNNER.I18N_BASENAME_CONSUMER_EVIDENCE,
                    RUNNER.COMMON_LOCALE_FACTORY_CONSUMER_EVIDENCE,
                    RUNNER.LOCALE_PASS_THROUGH_CONSUMER_EVIDENCE,
                    *(entry.removesuffix(".class").replace("/", ".")
                      for entry in RUNNER.FEATURE_CLASS_ENTRIES),
                )
            )
            successful_run = RUNNER.subprocess.CompletedProcess([], 0, stdout=successful_output)

            with (
                mock.patch.object(RUNNER, "assert_artifacts_present"),
                mock.patch.object(RUNNER, "assert_artifacts_absent") as absent,
                mock.patch.object(RUNNER, "assert_facades_source_free"),
                mock.patch.object(
                    RUNNER,
                    "run_command",
                    side_effect=(successful_run, RUNNER.HarnessError("canonical failure")),
                ),
            ):
                with self.assertRaisesRegex(RUNNER.HarnessError, "canonical failure"):
                    RUNNER.run_binary_compatibility(
                        harness, "2.0.2-SNAPSHOT", RUNNER.sha256_file(class_file)
                    )

            self.assertEqual([call["profile"] for call in harness.classpath_calls], ["aliases", "canonical"])
            absent.assert_called_once()

    def test_missing_consumer_class_hash_is_a_harness_error(self) -> None:
        with self.assertRaisesRegex(RUNNER.HarnessError, "Cannot read class file"):
            RUNNER.sha256_file(Path("missing/FeatureApiConsumer.class"))

    @staticmethod
    def _write_feature_consumer_class(fixtures: Path) -> Path:
        class_file = fixtures / "feature-api" / RUNNER.FEATURE_CONSUMER_CLASS_FILE
        class_file.parent.mkdir(parents=True)
        class_file.write_bytes(b"fixed-2.0.1-bytecode")
        return class_file


class _RecordingHarness:
    def __init__(self, fixtures: Path, classpath: list[Path] | None = None) -> None:
        self.java = "java"
        self.env: dict[str, str] = {}
        self.fixtures = fixtures
        self.classpath_calls: list[dict[str, object]] = []
        self.classpath = classpath or []

    def build_classpath(self, fixture_name: str, version: str, **arguments: object):
        self.classpath_calls.append(
            {"fixture_name": fixture_name, "version": version, **arguments}
        )
        return self.classpath


if __name__ == "__main__":
    unittest.main()
