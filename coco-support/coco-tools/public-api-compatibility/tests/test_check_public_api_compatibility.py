from __future__ import annotations

import io
import json
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path, PurePosixPath
from unittest import mock
from xml.etree import ElementTree


SCRIPT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

from check_public_api_compatibility import (  # noqa: E402
    BASELINE_GROUP_ID,
    POLICY_ID,
    ExpectedArtifact,
    ReportData,
    default_baseline_ledger_path,
    default_manifest_path,
    load_allowlist,
    load_baseline_ledger,
    load_manifest,
    main,
    parse_args,
    reactor_manifest,
    validate_reports,
    validate_candidate_binding,
)


REPOSITORY_ROOT = SCRIPT_DIR.parents[2]


def manifest_artifact(
    artifact: str,
    module: str,
    *,
    candidate_artifact: str | None = None,
    candidate_module: str | None = None,
    baseline_state: str = "present",
) -> dict[str, object]:
    candidate_artifact = candidate_artifact or artifact
    return {
        "artifactId": artifact,
        "modulePath": module,
        "groupId": BASELINE_GROUP_ID,
        "jarName": f"{artifact}.jar",
        "baselineState": baseline_state,
        "comparison": {"targetArtifactId": candidate_artifact},
    }


def manifest_value(artifacts: list[dict[str, object]]) -> dict[str, object]:
    return {
        "schemaVersion": 3,
        "policyId": POLICY_ID,
        "profile": "public-api-compatibility",
        "candidateVersionSource": "mavenProperty:revision",
        "artifacts": sorted(artifacts, key=lambda entry: str(entry["artifactId"])),
    }


def allowlist_value(
    *,
    rules: list[dict[str, str]] | None = None,
) -> dict[str, object]:
    return {
        "schemaVersion": 3,
        "policyId": POLICY_ID,
        "profile": "public-api-compatibility",
        "rules": rules or [],
    }


def write_json(path: Path, value: object) -> Path:
    path.write_text(json.dumps(value), encoding="utf-8")
    return path


def write_manifest(root: Path, artifacts: dict[str, str]) -> Path:
    write_reactor_pom(root, list(dict.fromkeys(artifacts.values())))
    return write_json(
        root / "manifest.json",
        manifest_value(
            [
                manifest_artifact(artifact, module)
                for artifact, module in artifacts.items()
            ]
        ),
    )


def write_reactor_pom(root: Path, modules: list[str]) -> Path:
    root.mkdir(parents=True, exist_ok=True)
    module_elements = "\n".join(f"    <module>{module}</module>" for module in modules)
    pom = root / "pom.xml"
    pom.write_text(
        f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>test</groupId>
  <artifactId>fixture-reactor</artifactId>
  <version>1.0.0</version>
  <packaging>pom</packaging>
  <modules>
{module_elements}
  </modules>
</project>
""",
        encoding="utf-8",
    )
    return pom


def write_allowlist(
    root: Path,
    *,
    rules: list[dict[str, str]] | None = None,
) -> Path:
    return write_json(
        root / "allowlist.json",
        allowlist_value(rules=rules),
    )


def write_pom(module: Path, artifact: str) -> Path:
    module.mkdir(parents=True, exist_ok=True)
    pom = module / "pom.xml"
    pom.write_text(
        f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>test</groupId>
  <artifactId>{artifact}</artifactId>
  <version>1.0.0</version>
</project>
""",
        encoding="utf-8",
    )
    return pom


def write_report(
    root: Path,
    artifact: str,
    old_version: str | None,
    classes: str = "",
    *,
    module: str | None = None,
) -> Path:
    module_path = root / (module or artifact)
    write_pom(module_path, artifact)
    candidate = module_path / "target" / f"{artifact}-1.0.0.jar"
    candidate.parent.mkdir(parents=True, exist_ok=True)
    candidate.write_bytes(b"candidate")
    report = module_path / "target" / "japicmp" / "public-api-compatibility.xml"
    report.parent.mkdir(parents=True)
    old_version_attribute = (
        "" if old_version is None else f' oldVersion="{old_version}"'
    )
    old_jar_attribute = ""
    baseline = None
    if old_version not in (None, "n.a."):
        baseline = (
            root
            / "local-repository"
            / "io"
            / "github"
            / "patton174"
            / artifact
            / old_version
            / f"{artifact}-{old_version}.jar"
        )
        baseline.parent.mkdir(parents=True, exist_ok=True)
        baseline.write_bytes(b"baseline")
        old_jar_attribute = f' oldJar="{baseline.resolve()}"'
    report.write_text(
        f"""<?xml version="1.0" encoding="UTF-8"?>
<japicmp{old_version_attribute}{old_jar_attribute} newVersion="1.0.0" newJar="{candidate.resolve()}">
  <classes>{classes}</classes>
</japicmp>
""",
        encoding="utf-8",
    )
    inputs = [candidate]
    if baseline is not None:
        inputs.append(baseline)
    newest_input = max(path.stat().st_mtime_ns for path in inputs)
    if report.stat().st_mtime_ns <= newest_input:
        report_time = newest_input + 1_000_000
        os.utime(report, ns=(report_time, report_time))
    return report


def validate_fixture(
    root: Path,
    *,
    artifact: str = "fixture",
    old_version: str | None = "2.0.1",
    classes: str = "",
    rules: list[dict[str, str]] | None = None,
) -> tuple[list[object], list[str]]:
    write_report(root, artifact, old_version, classes)
    manifest = write_manifest(root, {artifact: artifact})
    allowlist = write_allowlist(root, rules=rules)
    return validate_reports(root, allowlist, manifest)


class PublicApiCompatibilityFindingTest(unittest.TestCase):
    def test_removed_constructor_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            findings, missing = validate_fixture(
                Path(directory),
                classes="""
<class fullyQualifiedName="sample.PublicType" changeStatus="MODIFIED" binaryCompatible="true" sourceCompatible="true">
  <classType changeStatus="UNCHANGED" oldType="CLASS" newType="CLASS"/>
  <constructors>
    <constructor name="PublicType" changeStatus="REMOVED" binaryCompatible="false" sourceCompatible="false"/>
  </constructors>
</class>
""",
            )
            self.assertFalse(missing)
            self.assertEqual(1, len(findings))
            self.assertEqual("constructor", findings[0].member_kind)

    def test_exact_member_allowlist_does_not_cover_sibling_descriptor(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            findings, missing = validate_fixture(
                Path(directory),
                classes="""
<class fullyQualifiedName="sample.PublicType" changeStatus="MODIFIED" binaryCompatible="true" sourceCompatible="true">
  <classType changeStatus="UNCHANGED" oldType="CLASS" newType="CLASS"/>
  <methods>
    <method name="removed" changeStatus="REMOVED" binaryCompatible="false" sourceCompatible="false">
      <parameters><parameter type="java.lang.String"/></parameters>
    </method>
    <method name="removed" changeStatus="REMOVED" binaryCompatible="false" sourceCompatible="false">
      <parameters><parameter type="int"/></parameters>
    </method>
  </methods>
</class>
""",
                rules=[
                    {
                        "artifact": "fixture",
                        "class": "sample.PublicType",
                        "member": "method removed(java.lang.String)",
                        "category": "REMOVED",
                        "reason": "Exact migration descriptor.",
                    }
                ],
            )
            self.assertFalse(missing)
            self.assertEqual(["removed(int)"], [item.member_name for item in findings])

    def test_exact_class_allowlist_covers_only_named_migration_class(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            findings, missing = validate_fixture(
                Path(directory),
                rules=[
                    {
                        "artifact": "fixture",
                        "class": "sample.MovedConfiguration",
                        "member": "<class>",
                        "category": "REMOVED",
                        "reason": "The exact named configuration class moved.",
                    }
                ],
                classes="""
<class fullyQualifiedName="sample.MovedConfiguration" changeStatus="REMOVED" binaryCompatible="false" sourceCompatible="false">
  <constructors><constructor name="MovedConfiguration" changeStatus="REMOVED" binaryCompatible="false" sourceCompatible="false"/></constructors>
</class>
<class fullyQualifiedName="sample.Unrelated" changeStatus="REMOVED" binaryCompatible="false" sourceCompatible="false"/>
""",
            )
            self.assertFalse(missing)
            self.assertEqual(1, len(findings))
            self.assertEqual("sample.Unrelated", findings[0].class_name)

    def test_new_and_compatible_interface_default_are_ignored(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            findings, missing = validate_fixture(
                Path(directory),
                classes="""
<class fullyQualifiedName="sample.InterfaceType" changeStatus="MODIFIED" binaryCompatible="true" sourceCompatible="true">
  <classType changeStatus="UNCHANGED" oldType="INTERFACE" newType="INTERFACE"/>
  <methods><method name="defaultMethod" changeStatus="NEW" binaryCompatible="true" sourceCompatible="true"/></methods>
</class>
<class fullyQualifiedName="sample.NewType" changeStatus="NEW" binaryCompatible="true" sourceCompatible="true"/>
""",
            )
            self.assertFalse(missing)
            self.assertFalse(findings)

    def test_incompatible_interface_method_addition_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            findings, missing = validate_fixture(
                Path(directory),
                classes="""
<class fullyQualifiedName="sample.InterfaceType" changeStatus="MODIFIED" binaryCompatible="true" sourceCompatible="true">
  <classType changeStatus="UNCHANGED" oldType="INTERFACE" newType="INTERFACE"/>
  <methods><method name="abstractMethod" changeStatus="NEW" binaryCompatible="false" sourceCompatible="false"/></methods>
</class>
""",
            )
            self.assertFalse(missing)
            self.assertEqual(1, len(findings))
            self.assertEqual("abstractMethod()", findings[0].member_name)

    def test_record_shape_change_is_reported_even_when_flags_are_optimistic(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            findings, missing = validate_fixture(
                Path(directory),
                classes="""
<class fullyQualifiedName="sample.Shape" changeStatus="MODIFIED" binaryCompatible="true" sourceCompatible="true">
  <classType changeStatus="MODIFIED" oldType="CLASS" newType="RECORD"/>
</class>
""",
            )
            self.assertFalse(missing)
            self.assertEqual(1, len(findings))
            self.assertEqual("classType", findings[0].member_kind)

    def test_internal_public_class_is_out_of_scope(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            findings, missing = validate_fixture(
                Path(directory),
                classes="""
<class fullyQualifiedName="sample.internal.Implementation" changeStatus="REMOVED" binaryCompatible="false" sourceCompatible="false"/>
""",
            )
            self.assertFalse(missing)
            self.assertFalse(findings)


class PublicApiCompatibilityReportSetTest(unittest.TestCase):
    def test_no_reports_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_pom(root / "fixture", "fixture")
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(root)
            with self.assertRaisesRegex(ValueError, "No japicmp reports"):
                validate_reports(root, allowlist, manifest)

    def test_partial_report_set_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_report(root, "first", "2.0.1")
            write_pom(root / "second", "second")
            manifest = write_manifest(root, {"first": "first", "second": "second"})
            allowlist = write_allowlist(root)
            with self.assertRaisesRegex(
                ValueError, r"Missing expected japicmp reports: \['second'\]"
            ):
                validate_reports(root, allowlist, manifest)

    def test_duplicate_artifact_reports_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_report(root, "fixture", "2.0.1")
            write_report(root, "fixture", "2.0.1", module="copy")
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(root)
            with self.assertRaisesRegex(ValueError, "Duplicate japicmp reports"):
                validate_reports(root, allowlist, manifest)

    def test_unexpected_na_artifact_report_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_report(root, "fixture", "2.0.1")
            write_report(root, "unknown", "n.a.")
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(root)
            with self.assertRaisesRegex(
                ValueError, "Unexpected japicmp report artifact unknown"
            ):
                validate_reports(root, allowlist, manifest)

    def test_malformed_report_xml_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = write_report(root, "fixture", "2.0.1")
            report.write_text("<japicmp>", encoding="utf-8")
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(root)
            with self.assertRaises(ElementTree.ParseError):
                validate_reports(root, allowlist, manifest)

    def test_unexpected_report_xml_root_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = write_report(root, "fixture", "2.0.1")
            report.write_text("<not-japicmp/>", encoding="utf-8")
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(root)
            with self.assertRaisesRegex(ValueError, "Unexpected report root element"):
                validate_reports(root, allowlist, manifest)

    def test_report_under_wrong_module_path_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_pom(root / "fixture", "fixture")
            write_report(root, "fixture", "2.0.1", module="wrong-module")
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(root)
            with self.assertRaisesRegex(
                ValueError, "Japicmp report for fixture is at .*expected"
            ):
                validate_reports(root, allowlist, manifest)

    def test_report_older_than_replaced_candidate_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = write_report(root, "fixture", "2.0.1")
            candidate = root / "fixture/target/fixture-1.0.0.jar"
            candidate_time = report.stat().st_mtime_ns + 1_000_000_000
            candidate.write_bytes(b"replacement candidate")
            os.utime(candidate, ns=(candidate_time, candidate_time))
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(root)

            with self.assertRaisesRegex(ValueError, "Stale japicmp report"):
                validate_reports(root, allowlist, manifest)

    def test_report_for_older_version_rejects_newer_candidate_jar(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = write_report(root, "fixture", "2.0.1")
            current_candidate = root / "fixture/target/fixture-1.0.1.jar"
            current_candidate.write_bytes(b"new version candidate")
            candidate_time = (
                root / "fixture/target/fixture-1.0.0.jar"
            ).stat().st_mtime_ns + 1_000_000
            os.utime(current_candidate, ns=(candidate_time, candidate_time))
            report_time = candidate_time + 1_000_000
            os.utime(report, ns=(report_time, report_time))
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(root)

            with self.assertRaisesRegex(
                ValueError, "newer or equally recent candidate JAR exists"
            ):
                validate_reports(root, allowlist, manifest)

    def test_report_candidate_metadata_is_required(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report = write_report(root, "fixture", "2.0.1")
            report.write_text(
                report.read_text(encoding="utf-8").replace(' newVersion="1.0.0"', ""),
                encoding="utf-8",
            )
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(root)

            with self.assertRaisesRegex(ValueError, "invalid newVersion"):
                validate_reports(root, allowlist, manifest)


class PublicApiCompatibilityBindingTest(unittest.TestCase):
    def test_report_binds_baseline_coordinate_and_canonical_replacement(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline = (
                root
                / "repository/io/github/patton174/legacy-facade/2.0.1"
                / "legacy-facade-2.0.1.jar"
            )
            candidate = root / "canonical/target/canonical-1.0.0.jar"
            report = root / "facade/target/japicmp/public-api-compatibility.xml"
            for path, content in (
                (baseline, b"baseline"),
                (candidate, b"candidate"),
                (report, b"report"),
            ):
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
            newest = max(baseline.stat().st_mtime_ns, candidate.stat().st_mtime_ns)
            os.utime(report, ns=(newest + 1_000_000, newest + 1_000_000))
            entry = ExpectedArtifact(
                "legacy-facade",
                PurePosixPath("facade"),
                "canonical",
                PurePosixPath("canonical"),
            )
            data = ReportData(
                "legacy-facade",
                "2.0.1",
                str(baseline.resolve()),
                "1.0.0",
                str(candidate.resolve()),
                (),
            )

            validate_candidate_binding(root, entry, report, data)

            wrong_baseline = ReportData(
                data.artifact,
                data.old_version,
                str(candidate.resolve()),
                data.new_version,
                data.new_jar,
                (),
            )
            with self.assertRaisesRegex(ValueError, "expected Maven coordinate"):
                validate_candidate_binding(root, entry, report, wrong_baseline)

            wrong_candidate = ReportData(
                data.artifact,
                data.old_version,
                data.old_jar,
                data.new_version,
                str(baseline.resolve()),
                (),
            )
            with self.assertRaisesRegex(ValueError, "expected canonical candidate"):
                validate_candidate_binding(root, entry, report, wrong_candidate)


class PublicApiCompatibilityBaselineTest(unittest.TestCase):
    def test_exact_baseline_is_required_for_normal_report(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "uses oldVersion '2.0.0'"):
                validate_fixture(Path(directory), old_version="2.0.0")

    def test_missing_old_version_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "Missing oldVersion"):
                validate_fixture(Path(directory), old_version=None)

    def test_n_a_report_is_returned_as_evidence_for_ledger_validation(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            findings, missing = validate_fixture(Path(directory), old_version="n.a.")
            self.assertFalse(findings)
            self.assertEqual(["fixture"], missing)

    def test_allowlist_cannot_define_missing_baseline_authority(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            value = allowlist_value()
            value["missingBaselineArtifacts"] = ["fixture"]
            path = write_json(Path(directory) / "allowlist.json", value)
            with self.assertRaisesRegex(ValueError, "unknown keys"):
                load_allowlist(path)


class PublicApiCompatibilityPomTest(unittest.TestCase):
    def test_missing_module_pom_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(root)
            with self.assertRaises(FileNotFoundError):
                validate_reports(root, allowlist, manifest)

    def test_malformed_module_pom_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            pom = write_pom(root / "fixture", "fixture")
            pom.write_text("<project>", encoding="utf-8")
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(root)
            with self.assertRaises(ElementTree.ParseError):
                validate_reports(root, allowlist, manifest)

    def test_manifest_and_module_pom_artifacts_must_match(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_pom(root / "fixture", "different")
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(root)
            with self.assertRaisesRegex(ValueError, "does not match module POM"):
                validate_reports(root, allowlist, manifest)


class PublicApiCompatibilityConfigurationTest(unittest.TestCase):
    @mock.patch(
        "check_public_api_compatibility.validate_attested_reports",
        return_value=([], []),
    )
    def test_default_cli_manifest_is_wired_through_parse_args_and_main(
        self, validate: mock.Mock
    ) -> None:
        expected_manifest = default_manifest_path()
        expected_ledger = default_baseline_ledger_path()
        args = parse_args(
            [
                "--attestation",
                "attestation.json",
                "--expected-candidate-version",
                "1.0.0",
            ]
        )
        self.assertEqual(expected_manifest, args.manifest)
        self.assertEqual(expected_ledger, args.baseline_ledger)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            allowlist = root / "allowlist.json"
            attestation = root / "attestation.json"
            with redirect_stdout(io.StringIO()):
                result = main(
                    [
                        "--root",
                        str(root),
                        "--allowlist",
                        str(allowlist),
                        "--attestation",
                        str(attestation),
                        "--expected-candidate-version",
                        "1.0.0",
                    ]
                )

        self.assertEqual(0, result)
        validate.assert_called_once_with(
            root,
            allowlist,
            expected_manifest,
            expected_ledger,
            SCRIPT_DIR / "baseline-signing-key.asc",
            attestation,
            "1.0.0",
            None,
        )

    def test_missing_and_malformed_allowlist_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(FileNotFoundError):
                load_allowlist(root / "missing.json")
            malformed = root / "malformed.json"
            malformed.write_text("{", encoding="utf-8")
            with self.assertRaises(json.JSONDecodeError):
                load_allowlist(malformed)

    def test_invalid_allowlist_structure_is_rejected(self) -> None:
        invalid_values = (
            [],
            {"baseline": "2.0.0", "missingBaselineArtifacts": [], "rules": []},
            {
                "baseline": "2.0.1",
                "missingBaselineArtifacts": "fixture",
                "rules": [],
            },
            {
                "baseline": "2.0.1",
                "missingBaselineArtifacts": ["fixture", "fixture"],
                "rules": [],
            },
        )
        for value in invalid_values:
            with self.subTest(value=value), tempfile.TemporaryDirectory() as directory:
                path = write_json(Path(directory) / "allowlist.json", value)
                with self.assertRaises(ValueError):
                    load_allowlist(path)

    def test_invalid_rule_scope_keys_and_shape_are_rejected(self) -> None:
        invalid_rules = (
            "not-an-object",
            {
                "artifact": "fixture",
                "scope": "package",
                "category": "REMOVED",
                "reason": "Invalid scope.",
            },
            {
                "artifact": "fixture",
                "scope": "class",
                "category": "REMOVED",
                "reason": "Missing class key.",
            },
            {
                "artifact": "fixture",
                "scope": "artifact",
                "category": "REMOVED",
                "reason": "Unknown key.",
                "class": "sample.Type",
            },
            {
                "artifact": "fixture",
                "scope": "artifact",
                "status": "MODIFIED",
                "reason": "Unsupported status.",
            },
            {
                "artifact": "*",
                "scope": "artifact",
                "category": "REMOVED",
                "reason": "Wildcard.",
            },
        )
        for rule in invalid_rules:
            with self.subTest(rule=rule), tempfile.TemporaryDirectory() as directory:
                path = write_json(
                    Path(directory) / "allowlist.json",
                    allowlist_value(rules=[rule]),
                )
                with self.assertRaises(ValueError):
                    load_allowlist(path)

    def test_missing_and_malformed_manifest_are_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(FileNotFoundError):
                load_manifest(root / "missing.json")
            malformed = root / "malformed.json"
            malformed.write_text("{", encoding="utf-8")
            with self.assertRaises(json.JSONDecodeError):
                load_manifest(malformed)

    def test_invalid_manifest_structure_is_rejected(self) -> None:
        invalid_values = (
            [],
            {"profile": "wrong", "baseline": "2.0.1", "artifacts": []},
            {
                "profile": "public-api-compatibility",
                "baseline": "2.0.0",
                "artifacts": [{"artifact": "fixture", "module": "fixture"}],
            },
            {
                "profile": "public-api-compatibility",
                "baseline": "2.0.1",
                "artifacts": [{"artifact": "fixture", "module": "../fixture"}],
            },
            {
                "profile": "public-api-compatibility",
                "baseline": "2.0.1",
                "artifacts": [{"artifact": "fixture", "module": "..\\fixture"}],
            },
            {
                "profile": "public-api-compatibility",
                "baseline": "2.0.1",
                "artifacts": [
                    {"artifact": "fixture", "module": "one"},
                    {"artifact": "fixture", "module": "two"},
                ],
            },
            {
                "profile": "public-api-compatibility",
                "baseline": "2.0.1",
                "artifacts": [
                    {"artifact": "one", "module": "same"},
                    {"artifact": "two", "module": "same"},
                ],
            },
        )
        for value in invalid_values:
            with self.subTest(value=value), tempfile.TemporaryDirectory() as directory:
                path = write_json(Path(directory) / "manifest.json", value)
                with self.assertRaises(ValueError):
                    load_manifest(path)

    def test_replacement_target_cannot_be_another_facade(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_reactor_pom(root, ["facade-a", "facade-b", "canonical"])
            manifest = write_json(
                root / "manifest.json",
                manifest_value(
                    [
                        manifest_artifact(
                            "facade-a",
                            "facade-a",
                            candidate_artifact="facade-b",
                            candidate_module="facade-b",
                        ),
                        manifest_artifact(
                            "facade-b",
                            "facade-b",
                            candidate_artifact="canonical",
                            candidate_module="canonical",
                        ),
                        manifest_artifact("canonical", "canonical"),
                    ]
                ),
            )
            allowlist = write_allowlist(root)

            with self.assertRaisesRegex(ValueError, "chains and cycles"):
                validate_reports(root, allowlist, manifest)

    def test_replacement_cycle_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_reactor_pom(root, ["facade-a", "facade-b"])
            manifest = write_json(
                root / "manifest.json",
                manifest_value(
                    [
                        manifest_artifact(
                            "facade-a",
                            "facade-a",
                            candidate_artifact="facade-b",
                            candidate_module="facade-b",
                        ),
                        manifest_artifact(
                            "facade-b",
                            "facade-b",
                            candidate_artifact="facade-a",
                            candidate_module="facade-a",
                        ),
                    ]
                ),
            )
            allowlist = write_allowlist(root)

            with self.assertRaisesRegex(ValueError, "chains and cycles"):
                validate_reports(root, allowlist, manifest)

    def test_allowlist_cannot_reference_artifact_outside_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_report(root, "fixture", "2.0.1")
            manifest = write_manifest(root, {"fixture": "fixture"})
            allowlist = write_allowlist(
                root,
                rules=[
                    {
                        "artifact": "unknown",
                        "class": "sample.Type",
                        "member": "<class>",
                        "category": "REMOVED",
                        "reason": "Unknown authority entry.",
                    }
                ],
            )
            with self.assertRaisesRegex(
                ValueError, r"artifacts absent from the manifest: \['unknown'\]"
            ):
                validate_reports(root, allowlist, manifest)

    def test_manifest_missing_reactor_module_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_report(root, "first", "2.0.1")
            write_pom(root / "second", "second")
            manifest = write_manifest(root, {"first": "first"})
            write_reactor_pom(root, ["first", "second"])
            allowlist = write_allowlist(root)

            with self.assertRaisesRegex(
                ValueError, "missing reactor artifacts.*second"
            ):
                validate_reports(root, allowlist, manifest)

    def test_manifest_non_reactor_module_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write_report(root, "first", "2.0.1")
            write_report(root, "second", "2.0.1")
            manifest = write_manifest(root, {"first": "first", "second": "second"})
            write_reactor_pom(root, ["first"])
            allowlist = write_allowlist(root)

            with self.assertRaisesRegex(ValueError, "non-reactor artifacts.*second"):
                validate_reports(root, allowlist, manifest)

    def test_repository_allowlist_and_manifest_are_exact_and_aligned(self) -> None:
        allowlist = load_allowlist(SCRIPT_DIR / "allowlist.json")
        manifest = load_manifest(SCRIPT_DIR / "public-api-profile.json")
        artifacts = {entry.artifact for entry in manifest.artifacts}
        referenced = {rule["artifact"] for rule in allowlist["rules"]}
        self.assertEqual(POLICY_ID, allowlist["policyId"])
        self.assertEqual(
            {"schemaVersion", "policyId", "profile", "rules"}, set(allowlist)
        )
        self.assertTrue(referenced <= artifacts)
        self.assertTrue(all(rule["reason"] for rule in allowlist["rules"]))
        self.assertTrue(all("*" not in json.dumps(rule) for rule in allowlist["rules"]))
        self.assertTrue(
            all(
                set(rule) == {"artifact", "class", "member", "category", "reason"}
                for rule in allowlist["rules"]
            )
        )

    def test_repository_profile_closes_reports_candidates_and_missing_baselines(
        self,
    ) -> None:
        manifest = load_manifest(SCRIPT_DIR / "public-api-profile.json")
        ledger = load_baseline_ledger(SCRIPT_DIR / "baseline-sha256.json")
        self.assertEqual(32, len(manifest.artifacts))
        self.assertEqual(
            22,
            sum(entry.comparison == "self" for entry in manifest.artifacts),
        )
        self.assertEqual(20, len(ledger.artifacts))
        self.assertEqual(12, len(ledger.missing_artifacts))
        self.assertEqual(
            {entry.artifact for entry in ledger.artifacts},
            {
                entry.artifact
                for entry in manifest.artifacts
                if entry.baseline_state == "present"
            },
        )
        self.assertEqual(
            set(ledger.missing_artifacts),
            {
                entry.artifact
                for entry in manifest.artifacts
                if entry.baseline_state == "missing"
            },
        )
        self.assertEqual(
            {entry.artifact for entry in manifest.artifacts},
            {entry.artifact for entry in ledger.artifacts}
            | set(ledger.missing_artifacts),
        )
        self.assertTrue(
            all(
                entry.jar_name == f"{entry.artifact}.jar"
                for entry in manifest.artifacts
            )
        )
        replacements = {
            entry.artifact: entry.candidate_artifact
            for entry in manifest.artifacts
            if entry.comparison == "directReplacement"
        }
        self.assertEqual(
            {
                "coco-config": "coco-spring-boot-autoconfigure",
                "coco-feature-audit": "coco-audit",
                "coco-feature-data-permission": "coco-data-permission",
                "coco-feature-mybatis-plus": "coco-mybatis-plus",
                "coco-feature-openapi": "coco-openapi",
                "coco-feature-runtime": "coco-spring-boot-autoconfigure",
                "coco-feature-security": "coco-security",
                "coco-feature-tenant": "coco-tenant",
                "coco-feature-web": "coco-web",
                "coco-test": "coco-test-support",
            },
            replacements,
        )

    def test_manifest_exactly_matches_every_non_pom_reactor_module(self) -> None:
        manifest = load_manifest(SCRIPT_DIR / "public-api-profile.json")
        actual = reactor_manifest(REPOSITORY_ROOT)
        manifest_pairs = tuple(
            (entry.artifact, entry.module.as_posix()) for entry in manifest.artifacts
        )
        reactor_pairs = tuple(
            (entry.artifact, entry.module.as_posix()) for entry in actual.artifacts
        )

        self.assertEqual(reactor_pairs, tuple(sorted(manifest_pairs)))


if __name__ == "__main__":
    unittest.main()
