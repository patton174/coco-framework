from __future__ import annotations

import subprocess
import os
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest import mock
from xml.etree import ElementTree


SCRIPT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPT_DIR))

import run_regression_fixtures as fixture_runner  # noqa: E402
from run_regression_fixtures import (  # noqa: E402
    BASELINE_OPENAPI_COORDINATE,
    CURRENT_CANDIDATES,
    Finding,
    LEGACY_BASELINE_COORDINATES,
    LEGACY_DESCRIPTORS,
    LEGACY_DESCRIPTOR_PROBE_SUCCESS,
    OPENAPI_AUTO_CONFIGURATION,
    OPENAPI_FACTORY_BEAN,
    OPENAPI_METADATA_PROVIDER,
    OPENAPI_METHOD_DESCRIPTOR,
    OPENAPI_PROBE_SUCCESS,
    OPENAPI_VECTOR_NAME,
    VECTORS,
    assert_expected_findings,
    compile_jar,
    find_legacy_descriptor_candidates,
    find_legacy_runtime_candidates,
    find_reactor_current_candidates,
    member_descriptor,
    parse_args,
    prepare_legacy_descriptor_probe,
    published_coco_artifact,
    report_findings,
    run,
    run_japicmp,
    run_legacy_descriptor_probe,
    run_openapi_probe,
    verify_openapi_descriptor,
    verify_legacy_descriptor,
    verify_legacy_descriptor_japicmp,
    write_legacy_descriptor_probe,
    write_pom,
)
from path_io import entry_exists, mkdir, rmtree  # noqa: E402


class RegressionFixtureRunnerTest(unittest.TestCase):
    def test_fixture_vectors_have_unique_explicit_target_classes(self) -> None:
        target_classes = [vector["target_class"] for vector in VECTORS]

        self.assertEqual(4, len(target_classes))
        self.assertEqual(4, len(set(target_classes)))
        for vector in VECTORS:
            self.assertEqual(
                vector["expected_broken"][0].class_name,
                vector["target_class"],
            )

    def test_japicmp_pom_includes_exact_target_class(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            target_class = "sample.ExactPublicApi"
            pom = write_pom(
                Path(directory), Path("old.jar"), Path("new.jar"), target_class
            )
            root = ElementTree.parse(pom).getroot()
            namespace = {"m": "http://maven.apache.org/POM/4.0.0"}

            includes = root.findall(".//m:parameter/m:includes/m:include", namespace)
            self.assertEqual(1, len(includes))
            self.assertEqual(target_class, includes[0].text)

    def test_openapi_baseline_uses_published_coordinate(self) -> None:
        self.assertEqual(
            "io.github.patton174:coco-feature-openapi:2.0.1",
            BASELINE_OPENAPI_COORDINATE,
        )

    def test_legacy_descriptor_probe_binds_exact_2_0_1_surface(self) -> None:
        self.assertEqual(
            (
                "io.github.patton174:coco-config:2.0.1",
                "io.github.patton174:coco-feature-web:2.0.1",
                "io.github.patton174:coco-feature-tenant:2.0.1",
            ),
            LEGACY_BASELINE_COORDINATES,
        )
        self.assertEqual(
            {
                "config-feature-plan",
                "web-request-parameter-resolver",
                "web-trace-filter-registration",
                "tenant-before-prepare",
            },
            {descriptor.name for descriptor in LEGACY_DESCRIPTORS},
        )
        self.assertEqual(4, len({item.descriptor for item in LEGACY_DESCRIPTORS}))

    @mock.patch("run_regression_fixtures.verify_legacy_descriptor")
    def test_legacy_candidates_select_canonical_reactor_jars(
        self, verify_descriptor: mock.Mock
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            expected = []
            seen = set()
            for descriptor in LEGACY_DESCRIPTORS:
                key = (descriptor.candidate_module, descriptor.candidate_artifact)
                if key in seen:
                    continue
                seen.add(key)
                target = root / descriptor.candidate_module / "target"
                target.mkdir(parents=True)
                candidate = target / f"{descriptor.candidate_artifact}-1.0.0.jar"
                candidate.write_bytes(b"candidate")
                expected.append(candidate)

            self.assertEqual(
                tuple(expected), find_legacy_descriptor_candidates(root, "1.0.0")
            )
            self.assertEqual(4, verify_descriptor.call_count)

    def test_legacy_runtime_replaces_all_published_coco_jars_from_profile(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            api = root / "coco-foundation/coco-api/target/coco-api-1.0.0.jar"
            autoconfigure = (
                root / "coco-spring/coco-spring-boot-autoconfigure/target/"
                "coco-spring-boot-autoconfigure-1.0.0.jar"
            )
            for candidate in (api, autoconfigure):
                candidate.parent.mkdir(parents=True, exist_ok=True)
                candidate.write_bytes(b"candidate")

            actual = find_legacy_runtime_candidates(
                root, "1.0.0", {"coco-api", "coco-feature-runtime"}
            )

            self.assertEqual((api, autoconfigure), actual)

    def test_published_coco_artifact_requires_exact_central_layout(self) -> None:
        exact = Path("repository/io/github/patton174/coco-api/2.0.1/coco-api-2.0.1.jar")
        self.assertEqual("coco-api", published_coco_artifact(exact))
        self.assertIsNone(
            published_coco_artifact(
                Path("repository/io/github/patton174/coco-api/2.0.0/coco-api-2.0.0.jar")
            )
        )

    @mock.patch("run_regression_fixtures.executable", return_value="javap")
    def test_missing_legacy_descriptor_fails_closed(
        self, _executable: mock.Mock
    ) -> None:
        descriptor = LEGACY_DESCRIPTORS[0]
        with tempfile.TemporaryDirectory() as directory:
            candidate = Path(directory) / "candidate.jar"
            class_entry = descriptor.class_name.replace(".", "/") + ".class"
            with zipfile.ZipFile(candidate, "w") as archive:
                archive.writestr(class_entry, b"fixture")
            completed = subprocess.CompletedProcess(
                ["javap"], 0, stdout="descriptor: ()V", stderr=""
            )

            with self.assertRaisesRegex(RuntimeError, "required binary descriptor"):
                verify_legacy_descriptor(
                    descriptor,
                    candidate,
                    lambda _arguments, _cwd: completed,
                )

    @mock.patch("run_regression_fixtures.executable", return_value="java")
    def test_legacy_descriptor_probe_requires_exact_success(
        self, _executable: mock.Mock
    ) -> None:
        success = subprocess.CompletedProcess(
            ["java"],
            0,
            stdout=f"{LEGACY_DESCRIPTOR_PROBE_SUCCESS}\n",
            stderr="",
        )
        run_legacy_descriptor_probe(
            (Path("candidate.jar"),),
            Path("probe-classes"),
            (),
            lambda _arguments, _cwd: success,
        )

        warning = subprocess.CompletedProcess(
            ["java"],
            0,
            stdout=f"{LEGACY_DESCRIPTOR_PROBE_SUCCESS}\n",
            stderr="SLF4J(W): No SLF4J providers were found.\n",
        )
        run_legacy_descriptor_probe(
            (Path("candidate.jar"),),
            Path("probe-classes"),
            (),
            lambda _arguments, _cwd: warning,
        )

        noisy = subprocess.CompletedProcess(
            ["java"],
            0,
            stdout=f"{LEGACY_DESCRIPTOR_PROBE_SUCCESS}\nextra\n",
            stderr="",
        )
        with self.assertRaisesRegex(RuntimeError, "did not pass exactly"):
            run_legacy_descriptor_probe(
                (Path("candidate.jar"),),
                Path("probe-classes"),
                (),
                lambda _arguments, _cwd: noisy,
            )

    def test_openapi_fixture_sources_model_real_binary_descriptor(self) -> None:
        vector = next(item for item in VECTORS if item["name"] == OPENAPI_VECTOR_NAME)
        sources = vector["sources"]

        self.assertEqual({"old", "broken", "repaired"}, set(sources))
        self.assertEqual(
            "(Lio/github/coco/feature/openapi/core/CocoOpenApiMetadataProvider;)"
            "Lio/github/coco/feature/openapi/springdoc/"
            "CocoSpringDocOpenApiCustomizerFactoryBean;",
            OPENAPI_METHOD_DESCRIPTOR,
        )
        for state in ("old", "broken", "repaired"):
            source_set = sources[state]
            self.assertIn(
                "io/github/coco/feature/openapi/core/CocoOpenApiMetadataProvider.java",
                source_set,
            )
            self.assertIn(
                "io/github/coco/feature/openapi/springdoc/"
                "CocoSpringDocOpenApiCustomizerFactoryBean.java",
                source_set,
            )
            auto_configuration = source_set[
                "io/github/coco/feature/openapi/CocoOpenApiAutoConfiguration.java"
            ]
            self.assertIn(f"import {OPENAPI_METADATA_PROVIDER};", auto_configuration)
            self.assertIn(f"import {OPENAPI_FACTORY_BEAN};", auto_configuration)

        self.assertEqual(
            (
                Finding(
                    OPENAPI_AUTO_CONFIGURATION,
                    "method",
                    f"cocoSpringDocOpenApiCustomizer({OPENAPI_METADATA_PROVIDER})",
                    "REMOVED",
                ),
            ),
            vector["expected_broken"],
        )

    @mock.patch("run_regression_fixtures.verify_current_candidate")
    def test_reactor_current_selects_all_exact_module_candidate_jars(
        self, verify_candidate: mock.Mock
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            expected = {}
            for specification in CURRENT_CANDIDATES:
                candidate = (
                    root
                    / specification.module
                    / "target"
                    / f"{specification.artifact}-2.0.2-SNAPSHOT.jar"
                )
                candidate.parent.mkdir(parents=True, exist_ok=True)
                candidate.touch()
                expected[specification.vector_name] = candidate

            self.assertEqual(
                expected,
                find_reactor_current_candidates(root, "2.0.2-SNAPSHOT"),
            )
            self.assertEqual(4, verify_candidate.call_count)

    def test_missing_reactor_current_candidate_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(
                RuntimeError, "reactor candidate must be a real JAR"
            ):
                find_reactor_current_candidates(Path(directory), "2.0.2-SNAPSHOT")

    def test_candidate_version_is_required_by_cli(self) -> None:
        with self.assertRaises(SystemExit):
            parse_args([])
        self.assertEqual(
            "2.0.2-SNAPSHOT",
            parse_args(["--candidate-version", "2.0.2-SNAPSHOT"]).candidate_version,
        )

    def test_legacy_probe_contains_direct_calls_and_declared_tenant_lookup(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source = write_legacy_descriptor_probe(Path(directory)).read_text(
                encoding="utf-8"
            )
        self.assertIn("configConfiguration.cocoFeaturePlan(", source)
        self.assertIn("configProperties.getFeatures()", source)
        self.assertIn('getDeclaredMethod("getFeatures")', source)
        self.assertNotIn("configProperties.setFeatures(", source)
        self.assertIn(".cocoRequestParameterResolver(webProperties, null)", source)
        self.assertIn("traceConfiguration.cocoTraceFilterRegistration(", source)
        self.assertIn("tenantGuard.beforePrepare(statementHandler, null, null)", source)
        self.assertIn(
            "CocoTenantInterceptorIgnoreGuard.class.getDeclaredMethod(", source
        )
        self.assertEqual(5, source.count("lookup.unreflect("))

    @mock.patch("run_regression_fixtures.executable", return_value="javap")
    def test_missing_openapi_descriptor_fails_closed(
        self, _executable: mock.Mock
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            candidate = Path(directory) / "coco-openapi.jar"
            class_entry = OPENAPI_AUTO_CONFIGURATION.replace(".", "/") + ".class"
            with zipfile.ZipFile(candidate, "w") as archive:
                archive.writestr(class_entry, b"fixture")

            completed = subprocess.CompletedProcess(
                ["javap"],
                0,
                stdout="public cocoSpringDocOpenApiCustomizer();",
                stderr="",
            )
            with self.assertRaisesRegex(RuntimeError, "required binary descriptor"):
                verify_openapi_descriptor(candidate, lambda _arguments, _cwd: completed)

    @mock.patch("run_regression_fixtures.executable", return_value="java")
    def test_openapi_probe_broken_candidate_is_red(
        self, _executable: mock.Mock
    ) -> None:
        signature = (
            f"'{OPENAPI_FACTORY_BEAN} {OPENAPI_AUTO_CONFIGURATION}."
            f"cocoSpringDocOpenApiCustomizer({OPENAPI_METADATA_PROVIDER})'"
        )
        completed = subprocess.CompletedProcess(
            ["java"],
            1,
            stdout="",
            stderr=(
                f'Exception in thread "main" java.lang.NoSuchMethodError: {signature}\n'
                "\tat OpenApiBinaryConsumerProbe.main("
                "OpenApiBinaryConsumerProbe.java:18)"
            ),
        )

        run_openapi_probe(
            Path("broken.jar"),
            Path("probe-classes"),
            (),
            expect_success=False,
            process_runner=lambda _arguments, _cwd: completed,
        )

    @mock.patch("run_regression_fixtures.executable", return_value="java")
    def test_openapi_probe_broken_candidate_rejects_non_exact_error(
        self, _executable: mock.Mock
    ) -> None:
        completed = subprocess.CompletedProcess(
            ["java"],
            1,
            stdout="",
            stderr=(
                "java.lang.IllegalStateException: java.lang.NoSuchMethodError "
                f"{OPENAPI_FACTORY_BEAN} {OPENAPI_METADATA_PROVIDER}"
            ),
        )

        with self.assertRaisesRegex(RuntimeError, "expected method linkage error"):
            run_openapi_probe(
                Path("broken.jar"),
                Path("probe-classes"),
                (),
                expect_success=False,
                process_runner=lambda _arguments, _cwd: completed,
            )

    @mock.patch("run_regression_fixtures.executable", return_value="java")
    def test_openapi_probe_repaired_and_current_candidates_are_green(
        self, _executable: mock.Mock
    ) -> None:
        completed = subprocess.CompletedProcess(
            ["java"], 0, stdout=f"{OPENAPI_PROBE_SUCCESS}\n", stderr=""
        )

        for candidate in (Path("repaired.jar"), Path("reactor-current.jar")):
            with self.subTest(candidate=candidate):
                run_openapi_probe(
                    candidate,
                    Path("probe-classes"),
                    (),
                    expect_success=True,
                    process_runner=lambda _arguments, _cwd: completed,
                )

    @mock.patch("run_regression_fixtures.executable", return_value="java")
    def test_openapi_probe_success_output_is_exact(
        self, _executable: mock.Mock
    ) -> None:
        completed = subprocess.CompletedProcess(
            ["java"], 0, stdout=f"{OPENAPI_PROBE_SUCCESS}\nextra\n", stderr=""
        )

        with self.assertRaisesRegex(RuntimeError, "did not pass exactly"):
            run_openapi_probe(
                Path("candidate.jar"),
                Path("probe-classes"),
                (),
                expect_success=True,
                process_runner=lambda _arguments, _cwd: completed,
            )

    def test_report_findings_returns_exact_removed_member_descriptors(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / "report.xml"
            report.write_text(
                """<japicmp>
  <classes>
    <class fullyQualifiedName="sample.Api" changeStatus="MODIFIED">
      <classType changeStatus="UNCHANGED" oldType="CLASS" newType="CLASS"/>
      <constructors>
        <constructor name="sample.Api" changeStatus="REMOVED"
            binaryCompatible="false" sourceCompatible="false">
          <parameters><parameter type="sample.Dependency"/></parameters>
        </constructor>
      </constructors>
      <methods>
        <method name="kept" changeStatus="UNCHANGED"
            binaryCompatible="true" sourceCompatible="true">
          <parameters/>
        </method>
        <method name="removed" changeStatus="REMOVED"
            binaryCompatible="false" sourceCompatible="false">
          <parameters><parameter type="int"/></parameters>
        </method>
      </methods>
    </class>
  </classes>
</japicmp>
""",
                encoding="utf-8",
            )

            self.assertEqual(
                (
                    Finding(
                        "sample.Api",
                        "constructor",
                        "<init>(sample.Dependency)",
                        "REMOVED",
                    ),
                    Finding("sample.Api", "method", "removed(int)", "REMOVED"),
                ),
                report_findings(report),
            )

    def test_member_descriptor_uses_stable_constructor_name(self) -> None:
        from xml.etree import ElementTree

        member = ElementTree.fromstring(
            '<constructor name="different.Api"><parameters/></constructor>'
        )
        self.assertEqual(
            "<init>()", member_descriptor("sample.Api", "constructor", member)
        )

    def test_expected_findings_rejects_extra_finding(self) -> None:
        expected = (Finding("sample.Api", "method", "removed()", "REMOVED"),)
        actual = expected + (Finding("sample.Api", "field", "extra", "REMOVED"),)

        with self.assertRaisesRegex(RuntimeError, "extra"):
            assert_expected_findings("vector", "broken", actual, expected)

    @mock.patch("run_regression_fixtures.subprocess.run")
    def test_run_reports_subprocess_output_on_failure(
        self, subprocess_run: mock.Mock
    ) -> None:
        subprocess_run.return_value = subprocess.CompletedProcess(
            ["tool"], 7, stdout="standard output", stderr="standard error"
        )

        with self.assertRaisesRegex(RuntimeError, "standard error"):
            run(["tool"], Path.cwd())

    @mock.patch("run_regression_fixtures.executable", return_value="mvn")
    def test_successful_japicmp_without_report_fails_closed(
        self, _executable: mock.Mock
    ) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old_jar = root / "old.jar"
            new_jar = root / "new.jar"
            old_jar.write_bytes(b"old fixture jar")
            new_jar.write_bytes(b"new fixture jar")

            with self.assertRaisesRegex(RuntimeError, "did not produce report"):
                run_japicmp(
                    root / "comparison",
                    old_jar,
                    new_jar,
                    "sample.Api",
                    lambda _args, _cwd: None,
                )

    @unittest.skipUnless(
        os.environ.get("COCO_PUBLIC_API_INTEGRATION_VERSION"),
        "set COCO_PUBLIC_API_INTEGRATION_VERSION after building exact reactor JARs",
    )
    def test_real_legacy_consumer_runs_without_recompilation(self) -> None:
        version = os.environ["COCO_PUBLIC_API_INTEGRATION_VERSION"]
        repository = SCRIPT_DIR.parents[2]
        with tempfile.TemporaryDirectory(
            prefix="coco-legacy-integration-"
        ) as directory:
            work = Path(directory)
            classes, dependencies, baselines, published = (
                prepare_legacy_descriptor_probe(work)
            )
            primary = find_legacy_descriptor_candidates(repository, version)
            runtime = find_legacy_runtime_candidates(repository, version, published)
            closure = tuple(dict.fromkeys((*primary, *runtime)))
            verify_legacy_descriptor_japicmp(
                work / "japicmp", repository, version, baselines
            )
            run_legacy_descriptor_probe(closure, classes, dependencies)

    @mock.patch("run_regression_fixtures.executable", return_value="mvn")
    def test_empty_japicmp_report_fails_closed(self, _executable: mock.Mock) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            old_jar = root / "old.jar"
            new_jar = root / "new.jar"
            old_jar.write_bytes(b"old fixture jar")
            new_jar.write_bytes(b"new fixture jar")

            def write_empty_report(_arguments: list[str], cwd: Path) -> None:
                report = cwd / "target/japicmp/public-api-compatibility.xml"
                report.parent.mkdir(parents=True)
                report.touch()

            with self.assertRaisesRegex(RuntimeError, "empty report"):
                run_japicmp(
                    root / "comparison",
                    old_jar,
                    new_jar,
                    "sample.Api",
                    write_empty_report,
                )

    def test_deep_fixture_executes_real_javac_and_maven(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            sandbox = Path(directory)
            work = sandbox
            while (
                len(
                    str(
                        work
                        / "comparison"
                        / "target"
                        / "japicmp"
                        / "public-api-compatibility.xml"
                    )
                )
                < 280
            ):
                work /= f"fixture-segment-{len(work.parts):02d}-abcdefghij"
            mkdir(work, parents=True)
            deep_tmp = work / "deep-process-temp"
            mkdir(deep_tmp)
            sources = {
                "sample/Api.java": (
                    "package sample; public class Api { "
                    'public String value() { return "compatible"; } }'
                )
            }
            try:
                with mock.patch.object(
                    fixture_runner.tempfile, "gettempdir", return_value=str(deep_tmp)
                ):
                    old_jar = compile_jar(work, "old", sources)
                    new_jar = compile_jar(work, "new", sources)
                    report = run_japicmp(
                        work / "comparison", old_jar, new_jar, "sample.Api"
                    )
                self.assertGreaterEqual(len(str(report)), 280)
                self.assertEqual((), report_findings(report))
                self.assertNotIn("\\\\?\\", str(report))
            finally:
                if entry_exists(sandbox):
                    rmtree(sandbox, "deep javac and Maven fixture")


if __name__ == "__main__":
    unittest.main()
