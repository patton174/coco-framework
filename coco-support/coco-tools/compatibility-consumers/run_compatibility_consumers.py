#!/usr/bin/env python3
"""Run external Maven consumers for the Coco 2.x coordinate migration."""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence
from xml.etree import ElementTree


BASELINE_VERSION = "2.0.1"
COCO_GROUP_ID = "io.github.patton174"
MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0"
XML_NAMESPACES = {"m": MAVEN_NAMESPACE}

CANONICAL_FEATURE_ARTIFACTS = (
    "coco-web",
    "coco-mybatis-plus",
    "coco-tenant",
    "coco-data-permission",
    "coco-audit",
    "coco-security",
    "coco-openapi",
)
ALIAS_FEATURE_ARTIFACTS = (
    "coco-feature-web",
    "coco-feature-mybatis-plus",
    "coco-feature-tenant",
    "coco-feature-data-permission",
    "coco-feature-audit",
    "coco-feature-security",
    "coco-feature-openapi",
)
CANONICAL_CONSUMER_ARTIFACTS = (
    "coco-spring-boot-autoconfigure",
    *CANONICAL_FEATURE_ARTIFACTS,
    "coco-test-support",
)
LEGACY_CONSUMER_ARTIFACTS = (
    "coco-config",
    "coco-feature-runtime",
    *ALIAS_FEATURE_ARTIFACTS,
    "coco-test",
)
FEATURE_CLASS_ENTRIES = (
    "io/github/coco/feature/web/CocoWebFeature.class",
    "io/github/coco/feature/mybatisplus/CocoMybatisPlusFeature.class",
    "io/github/coco/feature/tenant/CocoTenantFeature.class",
    "io/github/coco/feature/datapermission/CocoDataPermissionFeature.class",
    "io/github/coco/feature/audit/CocoAuditFeature.class",
    "io/github/coco/feature/security/CocoSecurityFeature.class",
    "io/github/coco/feature/openapi/CocoOpenApiFeature.class",
)
FEATURE_CONSUMER_CLASS = "io.github.coco.consumer.FeatureApiConsumer"
FEATURE_CONSUMER_CLASS_FILE = Path(
    "target/classes/io/github/coco/consumer/FeatureApiConsumer.class"
)
RUNTIME_FEATURE_CONSUMER_CLASS = (
    "io.github.coco.consumer.RuntimeFeatureRegistrationConsumer"
)
RUNTIME_FEATURE_CONSUMER_SOURCE = Path(
    "src/test/java/io/github/coco/consumer/RuntimeFeatureRegistrationConsumer.java"
)
RUNTIME_FEATURE_PROFILES = (
    ("canonical", "canonical-only"),
    ("aliases", "alias-only"),
    ("mixed", "same-version-mixed"),
)
FEATURE_AUTO_CONFIGURATION_CLASSES = (
    "io.github.coco.feature.web.CocoWebAutoConfiguration",
    "io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration",
    "io.github.coco.feature.tenant.CocoTenantAutoConfiguration",
    "io.github.coco.feature.datapermission.CocoDataPermissionAutoConfiguration",
    "io.github.coco.feature.audit.CocoAuditAutoConfiguration",
    "io.github.coco.feature.security.CocoSecurityAutoConfiguration",
    "io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration",
)
TEST_SUPPORT_CLASS_ENTRY = "io/github/coco/test/CocoTestSupport.class"
PUBLIC_FQCN_CONSUMER_SOURCE = Path(
    "src/main/java/io/github/coco/consumer/PublicFqcnConsumer.java"
)
DEPENDENCY_CLASSPATH_GOAL = "dependency:build-classpath"
ANSI_ESCAPE = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")

EXPECTED_CODEGEN_FILES = {
    "io/github/coco/consumer/generated/domain/widget/Widget.java",
    "io/github/coco/consumer/generated/domain/widget/WidgetRepository.java",
    "io/github/coco/consumer/generated/application/widget/WidgetApplicationService.java",
    "io/github/coco/consumer/generated/infrastructure/widget/WidgetEntity.java",
    "io/github/coco/consumer/generated/infrastructure/widget/WidgetMapper.java",
    "io/github/coco/consumer/generated/infrastructure/widget/MybatisPlusWidgetRepository.java",
    "io/github/coco/consumer/generated/interfaces/rest/widget/WidgetController.java",
    "io/github/coco/consumer/generated/interfaces/rest/widget/dto/CreateWidgetRequest.java",
    "io/github/coco/consumer/generated/interfaces/rest/widget/dto/UpdateWidgetRequest.java",
    "io/github/coco/consumer/generated/interfaces/rest/widget/dto/WidgetResponse.java",
}


class HarnessError(RuntimeError):
    """Raised when a compatibility assertion or external command fails."""


def find_repository_root(start: Path) -> Path:
    for candidate in (start, *start.parents):
        if (candidate / "pom.xml").is_file() and (candidate / "coco-support").is_dir():
            return candidate
    raise HarnessError(f"Cannot locate the Coco repository root from {start}.")


SCRIPT_DIRECTORY = Path(__file__).resolve().parent
REPOSITORY_ROOT = find_repository_root(SCRIPT_DIRECTORY)
FIXTURE_ROOT = SCRIPT_DIRECTORY / "fixtures"


def command_display(arguments: Sequence[str]) -> str:
    if os.name == "nt":
        return subprocess.list2cmdline(list(arguments))
    return shlex.join(arguments)


def run_command(
    label: str,
    arguments: Sequence[str | Path],
    *,
    cwd: Path,
    env: dict[str, str],
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    command = [str(argument) for argument in arguments]
    print(f"[RUN ] {label}")
    print(f"       {command_display(command)}")
    completed = subprocess.run(
        command,
        cwd=cwd,
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        shell=False,
        check=False,
    )
    if check and completed.returncode != 0:
        output = strip_ansi(completed.stdout).rstrip()
        raise HarnessError(
            f"{label} failed with exit code {completed.returncode}.\n{output}"
        )
    if check:
        print(f"[PASS] {label}")
    return completed


def strip_ansi(value: str) -> str:
    return ANSI_ESCAPE.sub("", value)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def directory_digest(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        if not path.is_file() or "target" in path.relative_to(root).parts:
            continue
        relative_path = path.relative_to(root).as_posix()
        digest.update(relative_path.encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def xml_text(element: ElementTree.Element, child_name: str) -> str | None:
    child = element.find(f"m:{child_name}", XML_NAMESPACES)
    if child is None or child.text is None:
        return None
    return child.text.strip()


def profile_artifact_ids(root: ElementTree.Element, profile_id: str) -> set[str]:
    for profile in root.findall("m:profiles/m:profile", XML_NAMESPACES):
        if xml_text(profile, "id") == profile_id:
            return {
                artifact_id
                for dependency in profile.findall(
                    "m:dependencies/m:dependency", XML_NAMESPACES
                )
                if (artifact_id := xml_text(dependency, "artifactId")) is not None
            }
    raise HarnessError(f"Missing Maven profile '{profile_id}'.")


def direct_coco_artifact_ids(root: ElementTree.Element) -> set[str]:
    return {
        artifact_id
        for dependency in root.findall("m:dependencies/m:dependency", XML_NAMESPACES)
        if xml_text(dependency, "groupId") == COCO_GROUP_ID
        if (artifact_id := xml_text(dependency, "artifactId")) is not None
    }


def validate_fixture_contracts(fixture_root: Path = FIXTURE_ROOT) -> None:
    required_fixtures = {
        "canonical",
        "legacy-2x",
        "feature-api",
        "test-support",
        "version-alignment",
        "codegen",
    }
    actual_fixtures = {path.name for path in fixture_root.iterdir() if path.is_dir()}
    missing_fixtures = required_fixtures - actual_fixtures
    if missing_fixtures:
        raise HarnessError(
            f"Required fixture directories are missing: {sorted(missing_fixtures)}."
        )

    parsed_poms: dict[str, ElementTree.Element] = {}
    for fixture_name in sorted(actual_fixtures):
        pom = fixture_root / fixture_name / "pom.xml"
        if not pom.is_file():
            raise HarnessError(
                f"Fixture directory does not contain pom.xml: {pom.parent}."
            )
        root = ElementTree.parse(pom).getroot()
        parsed_poms[fixture_name] = root
        if root.find("m:parent", XML_NAMESPACES) is not None:
            raise HarnessError(f"External fixture must not inherit a parent: {pom}.")

        dependencies = root.findall(".//m:dependency", XML_NAMESPACES)
        bom_imports = [
            dependency
            for dependency in dependencies
            if xml_text(dependency, "groupId") == COCO_GROUP_ID
            and xml_text(dependency, "artifactId") == "coco-dependencies"
            and xml_text(dependency, "type") == "pom"
            and xml_text(dependency, "scope") == "import"
        ]
        if (
            len(bom_imports) != 1
            or xml_text(bom_imports[0], "version") != "${coco.version}"
        ):
            raise HarnessError(
                f"Fixture must import coco-dependencies via ${{coco.version}}: {pom}."
            )

        for dependency in dependencies:
            if xml_text(dependency, "groupId") != COCO_GROUP_ID:
                continue
            artifact_id = xml_text(dependency, "artifactId")
            version = xml_text(dependency, "version")
            if artifact_id == "coco-dependencies":
                continue
            if (
                fixture_name == "version-alignment"
                and artifact_id == "coco-feature-web"
            ):
                if version != BASELINE_VERSION:
                    raise HarnessError(
                        "The intentional coco-feature-web mismatch must stay pinned to 2.0.1."
                    )
                continue
            if version is not None:
                raise HarnessError(
                    f"Coco dependency {artifact_id} must omit its version in {pom}."
                )

    feature_pom = parsed_poms["feature-api"]
    if profile_artifact_ids(feature_pom, "canonical") != set(
        CANONICAL_FEATURE_ARTIFACTS
    ):
        raise HarnessError(
            "The canonical feature profile must contain all seven canonical artifacts."
        )
    if profile_artifact_ids(feature_pom, "aliases") != set(ALIAS_FEATURE_ARTIFACTS):
        raise HarnessError(
            "The alias feature profile must contain all seven old artifacts."
        )
    if profile_artifact_ids(feature_pom, "mixed") != set(
        CANONICAL_FEATURE_ARTIFACTS + ALIAS_FEATURE_ARTIFACTS
    ):
        raise HarnessError(
            "The mixed feature profile must contain both forms of all seven artifacts."
        )

    feature_test_dependencies = {
        (xml_text(dependency, "groupId"), xml_text(dependency, "artifactId")): xml_text(
            dependency, "scope"
        )
        for dependency in feature_pom.findall(
            "m:dependencies/m:dependency", XML_NAMESPACES
        )
    }
    required_feature_test_dependencies = {
        ("org.springframework.boot", "spring-boot-test"): "test",
        ("org.assertj", "assertj-core"): "test",
    }
    if feature_test_dependencies != required_feature_test_dependencies:
        raise HarnessError(
            "The feature runtime probe must use only the required test-scoped Spring Boot test dependencies."
        )

    test_support_pom = parsed_poms["test-support"]
    if profile_artifact_ids(test_support_pom, "canonical") != {"coco-test-support"}:
        raise HarnessError("The canonical test-support profile is incomplete.")
    if profile_artifact_ids(test_support_pom, "alias") != {"coco-test"}:
        raise HarnessError("The old test-support profile is incomplete.")
    if profile_artifact_ids(test_support_pom, "mixed") != {
        "coco-test-support",
        "coco-test",
    }:
        raise HarnessError("The mixed test-support profile is incomplete.")

    if direct_coco_artifact_ids(parsed_poms["canonical"]) != set(
        CANONICAL_CONSUMER_ARTIFACTS
    ):
        raise HarnessError(
            "The canonical consumer must use only canonical Coco coordinates."
        )
    if direct_coco_artifact_ids(parsed_poms["legacy-2x"]) != set(
        LEGACY_CONSUMER_ARTIFACTS
    ):
        raise HarnessError(
            "The legacy 2.x consumer must use only published facade coordinates."
        )

    canonical_source = fixture_root / "canonical" / PUBLIC_FQCN_CONSUMER_SOURCE
    legacy_source = fixture_root / "legacy-2x" / PUBLIC_FQCN_CONSUMER_SOURCE
    if not canonical_source.is_file() or not legacy_source.is_file():
        raise HarnessError(
            "Canonical and legacy consumers must contain the public-FQCN probe."
        )
    if canonical_source.read_bytes() != legacy_source.read_bytes():
        raise HarnessError(
            "Canonical and legacy consumers must compile identical Java source."
        )

    feature_source = (
        fixture_root
        / "feature-api/src/main/java/io/github/coco/consumer/FeatureApiConsumer.java"
    ).read_text(encoding="utf-8")
    for class_entry in FEATURE_CLASS_ENTRIES:
        fqcn = class_entry.removesuffix(".class").replace("/", ".")
        if fqcn not in feature_source:
            raise HarnessError(f"Feature fixture does not reference {fqcn}.")

    runtime_source_path = fixture_root / "feature-api" / RUNTIME_FEATURE_CONSUMER_SOURCE
    if not runtime_source_path.is_file():
        raise HarnessError(
            "Feature fixture does not contain the runtime registration probe."
        )
    runtime_source = runtime_source_path.read_text(encoding="utf-8")
    required_runtime_tokens = (
        "WebApplicationContextRunner",
        "@EnableAutoConfiguration",
        "getStartupFailure()",
        "ConfigurableWebApplicationContext",
        "CocoFeaturePlan",
        *FEATURE_AUTO_CONFIGURATION_CLASSES,
    )
    missing_runtime_tokens = [
        token for token in required_runtime_tokens if token not in runtime_source
    ]
    if missing_runtime_tokens:
        raise HarnessError(
            "Feature runtime probe is missing required context-refresh evidence: "
            f"{missing_runtime_tokens}."
        )

    codegen_output = parsed_poms["codegen"].find(
        ".//m:plugin[m:artifactId='coco-maven-plugin']/m:configuration/m:outputDirectory",
        XML_NAMESPACES,
    )
    if codegen_output is None or codegen_output.text is None:
        raise HarnessError(
            "Codegen fixture must configure a generated output directory."
        )
    if not codegen_output.text.strip().startswith("${project.build.directory}/"):
        raise HarnessError(
            "Codegen output must remain under the fixture target directory."
        )


def parse_classpath(path: Path) -> list[Path]:
    if not path.is_file():
        raise HarnessError(f"Maven did not write the dependency classpath: {path}.")
    entries = [
        Path(value)
        for value in path.read_text(encoding="utf-8").strip().split(os.pathsep)
    ]
    missing = [entry for entry in entries if not entry.is_file()]
    if missing:
        raise HarnessError(f"Dependency classpath contains missing files: {missing}.")
    return entries


def class_owners(
    classpath: Iterable[Path], class_prefixes: Sequence[str]
) -> dict[str, list[Path]]:
    owners: dict[str, list[Path]] = {}
    for artifact in classpath:
        if artifact.suffix.lower() != ".jar":
            continue
        try:
            with zipfile.ZipFile(artifact) as archive:
                for entry in archive.namelist():
                    if not entry.endswith(".class"):
                        continue
                    if not any(entry.startswith(prefix) for prefix in class_prefixes):
                        continue
                    owners.setdefault(entry, []).append(artifact)
        except zipfile.BadZipFile as error:
            raise HarnessError(
                f"Invalid JAR on dependency classpath: {artifact}."
            ) from error
    return owners


def assert_unique_implementation_classes(
    classpath: Sequence[Path],
    *,
    class_prefixes: Sequence[str],
    expected_entries: Sequence[str],
) -> None:
    owners = class_owners(classpath, class_prefixes)
    duplicates = {entry: paths for entry, paths in owners.items() if len(paths) > 1}
    if duplicates:
        details = "; ".join(
            f"{entry}: {', '.join(str(path) for path in paths)}"
            for entry, paths in sorted(duplicates.items())
        )
        raise HarnessError(f"Duplicate Coco implementation classes found: {details}")
    for entry in expected_entries:
        entry_owners = owners.get(entry, [])
        if len(entry_owners) != 1:
            raise HarnessError(
                f"Expected exactly one owner for {entry}, found {len(entry_owners)}: {entry_owners}."
            )


def artifact_path(classpath: Sequence[Path], artifact_id: str, version: str) -> Path:
    matches = [
        path
        for path in classpath
        if path.suffix.lower() == ".jar"
        and path.parent.name == version
        and path.parent.parent.name == artifact_id
    ]
    if len(matches) != 1:
        raise HarnessError(
            f"Expected one {artifact_id}:{version} JAR on the classpath, found {matches}."
        )
    return matches[0]


def assert_artifacts_present(
    classpath: Sequence[Path], artifact_ids: Sequence[str], version: str
) -> None:
    for artifact_id in artifact_ids:
        artifact_path(classpath, artifact_id, version)


def assert_artifacts_absent(
    classpath: Sequence[Path], artifact_ids: Sequence[str], version: str
) -> None:
    for artifact_id in artifact_ids:
        matches = [
            path
            for path in classpath
            if path.suffix.lower() == ".jar"
            and path.parent.name == version
            and path.parent.parent.name == artifact_id
        ]
        if matches:
            raise HarnessError(
                f"Expected {artifact_id}:{version} to be absent from the classpath, found {matches}."
            )


def assert_facades_source_free(
    classpath: Sequence[Path],
    artifact_ids: Sequence[str],
    version: str,
    class_prefixes: Sequence[str],
) -> None:
    for artifact_id in artifact_ids:
        path = artifact_path(classpath, artifact_id, version)
        owners = class_owners([path], class_prefixes)
        if owners:
            sample = ", ".join(sorted(owners)[:5])
            raise HarnessError(
                f"Compatibility facade {artifact_id}:{version} contains implementation classes: {sample}."
            )


def assert_alignment_diagnostic(output: str, candidate_version: str) -> None:
    normalized = strip_ansi(output)
    expected_message = (
        f"Coco feature artifact versions must align with '{candidate_version}'"
    )
    expected_coordinate = f"{COCO_GROUP_ID}:coco-feature-web:{BASELINE_VERSION}"
    if expected_message not in normalized or expected_coordinate not in normalized:
        raise HarnessError(
            "coco:features failed without the required version-alignment diagnostic.\n"
            + normalized.rstrip()
        )


def assert_runtime_registration_evidence(output: str, classpath_name: str) -> str:
    normalized = strip_ansi(output)
    expected_marker = f"COCO_RUNTIME_REGISTRATION_OK profile={classpath_name}"
    for line in normalized.splitlines():
        if line.startswith(expected_marker):
            return line
    raise HarnessError(
        f"Runtime probe did not emit refreshed-context evidence for {classpath_name}.\n"
        + normalized.rstrip()
    )


def generated_java_files(output_directory: Path) -> set[str]:
    if not output_directory.is_dir():
        return set()
    return {
        path.relative_to(output_directory).as_posix()
        for path in output_directory.rglob("*.java")
    }


@dataclass(frozen=True)
class MavenHarness:
    maven: str
    java: str
    env: dict[str, str]
    local_repository: Path
    fixtures: Path

    def maven_base(self) -> list[str]:
        return [
            self.maven,
            "-B",
            "-ntp",
            "-Dstyle.color=never",
            f"-Dmaven.repo.local={self.local_repository}",
        ]

    def install_candidate(self, candidate_version: str) -> None:
        run_command(
            f"install candidate reactor {candidate_version}",
            [
                *self.maven_base(),
                f"-Drevision={candidate_version}",
                "-DskipTests",
                "-Dgpg.skip=true",
                "install",
            ],
            cwd=REPOSITORY_ROOT,
            env=self.env,
        )

    def run_fixture(
        self,
        fixture_name: str,
        version: str,
        goals: Sequence[str],
        *,
        profile: str | None = None,
        properties: dict[str, str | Path] | None = None,
        check: bool = True,
        label: str | None = None,
    ) -> subprocess.CompletedProcess[str]:
        fixture = self.fixtures / fixture_name
        arguments = [
            *self.maven_base(),
            "-f",
            fixture / "pom.xml",
            f"-Dcoco.version={version}",
        ]
        if profile is not None:
            arguments.append(f"-P{profile}")
        if properties:
            arguments.extend(
                f"-D{name}={value}" for name, value in sorted(properties.items())
            )
        arguments.extend(goals)
        return run_command(
            label or f"{fixture_name} ({version}, {profile or 'default'})",
            arguments,
            cwd=fixture,
            env=self.env,
            check=check,
        )

    def build_classpath(
        self,
        fixture_name: str,
        version: str,
        *,
        profile: str,
        output_name: str,
        clean: bool,
        compile_goal: str = "compile",
    ) -> list[Path]:
        fixture = self.fixtures / fixture_name
        output = fixture / "target" / output_name
        goals = ["clean", compile_goal] if clean else []
        goals.append(DEPENDENCY_CLASSPATH_GOAL)
        self.run_fixture(
            fixture_name,
            version,
            goals,
            profile=profile,
            properties={"mdep.outputFile": output},
            label=f"resolve {fixture_name} {profile} classpath at {version}",
        )
        return parse_classpath(output)


def resolve_tool(name_or_path: str, env: dict[str, str]) -> str:
    path = Path(name_or_path)
    if path.parent != Path(".") or path.is_absolute():
        if not path.is_file():
            raise HarnessError(f"Executable does not exist: {path}.")
        return str(path.resolve())
    resolved = shutil.which(name_or_path, path=env.get("PATH"))
    if resolved is None:
        raise HarnessError(f"Executable is not available on PATH: {name_or_path}.")
    return resolved


def prepare_toolchain(
    jdk_home_argument: str | None, maven_argument: str | None
) -> tuple[str, str, dict[str, str]]:
    env = os.environ.copy()
    jdk_home_value = jdk_home_argument or env.get("JAVA_HOME")
    if jdk_home_value:
        jdk_home = Path(jdk_home_value).expanduser().resolve()
        java_name = "java.exe" if os.name == "nt" else "java"
        java_path = jdk_home / "bin" / java_name
        if not java_path.is_file():
            raise HarnessError(f"JAVA_HOME does not contain {java_name}: {jdk_home}.")
        env["JAVA_HOME"] = str(jdk_home)
        env["PATH"] = str(java_path.parent) + os.pathsep + env.get("PATH", "")
        java = str(java_path)
    else:
        java = resolve_tool("java", env)

    java_result = run_command(
        "verify JDK 21",
        [java, "-version"],
        cwd=REPOSITORY_ROOT,
        env=env,
    )
    java_output = strip_ansi(java_result.stdout)
    match = re.search(r'version "(?:1\.)?(\d+)', java_output)
    if match is None or int(match.group(1)) != 21:
        raise HarnessError(
            f"JDK 21 is required, but java -version reported:\n{java_output.rstrip()}"
        )

    if maven_argument is not None:
        maven = resolve_tool(maven_argument, env)
    else:
        candidates = ("mvn.cmd", "mvn") if os.name == "nt" else ("mvn",)
        maven = ""
        for candidate in candidates:
            resolved = shutil.which(candidate, path=env.get("PATH"))
            if resolved is not None:
                maven = resolved
                break
        if not maven:
            raise HarnessError("Maven is not available on PATH.")

    maven_result = run_command(
        "verify Maven uses JDK 21",
        [maven, "-version"],
        cwd=REPOSITORY_ROOT,
        env=env,
    )
    if not re.search(r"Java version:\s*21(?:\.|\s|$)", strip_ansi(maven_result.stdout)):
        raise HarnessError(
            "Maven is not using JDK 21.\n" + strip_ansi(maven_result.stdout).rstrip()
        )
    return maven, java, env


def compile_baseline_consumers(harness: MavenHarness) -> str:
    harness.run_fixture(
        "legacy-2x",
        BASELINE_VERSION,
        ["clean", "compile"],
        label="compile public FQCNs through published 2.0.1 facade coordinates",
    )
    harness.run_fixture(
        "feature-api",
        BASELINE_VERSION,
        ["clean", "compile"],
        profile="aliases",
        label="compile all seven published 2.0.1 feature aliases",
    )
    class_file = harness.fixtures / "feature-api" / FEATURE_CONSUMER_CLASS_FILE
    if not class_file.is_file():
        raise HarnessError(f"Baseline compiler did not create {class_file}.")
    baseline_hash = sha256_file(class_file)

    harness.run_fixture(
        "test-support",
        BASELINE_VERSION,
        ["clean", "compile"],
        profile="alias",
        label="compile CocoTestSupport through coco-test:2.0.1",
    )
    return baseline_hash


def compile_dual_path_consumers(harness: MavenHarness, candidate_version: str) -> None:
    harness.run_fixture(
        "canonical",
        candidate_version,
        ["clean", "compile"],
        label="compile public FQCNs through canonical candidate coordinates",
    )
    harness.run_fixture(
        "legacy-2x",
        candidate_version,
        ["clean", "compile"],
        label="compile identical public FQCNs through candidate facades",
    )


def run_binary_compatibility(
    harness: MavenHarness, candidate_version: str, baseline_hash: str
) -> None:
    feature_fixture = harness.fixtures / "feature-api"
    class_file = feature_fixture / FEATURE_CONSUMER_CLASS_FILE
    candidate_classpath = harness.build_classpath(
        "feature-api",
        candidate_version,
        profile="aliases",
        output_name="candidate-alias-classpath.txt",
        clean=False,
    )
    if sha256_file(class_file) != baseline_hash:
        raise HarnessError(
            "Resolving the candidate classpath changed the 2.0.1 class file."
        )

    assert_artifacts_present(
        candidate_classpath, ALIAS_FEATURE_ARTIFACTS, candidate_version
    )
    assert_artifacts_present(
        candidate_classpath, CANONICAL_FEATURE_ARTIFACTS, candidate_version
    )
    assert_facades_source_free(
        candidate_classpath,
        ALIAS_FEATURE_ARTIFACTS,
        candidate_version,
        ("io/github/coco/feature/",),
    )

    runtime_classpath = os.pathsep.join(
        [
            str(feature_fixture / "target/classes"),
            *(str(path) for path in candidate_classpath),
        ]
    )
    result = run_command(
        "run unchanged 2.0.1 class files on candidate facades",
        [harness.java, "-cp", runtime_classpath, FEATURE_CONSUMER_CLASS],
        cwd=feature_fixture,
        env=harness.env,
    )
    output = strip_ansi(result.stdout)
    for class_entry in FEATURE_CLASS_ENTRIES:
        fqcn = class_entry.removesuffix(".class").replace("/", ".")
        if fqcn not in output:
            raise HarnessError(
                f"Binary consumer did not load {fqcn}.\n{output.rstrip()}"
            )


def compile_candidate_feature_consumers(
    harness: MavenHarness, candidate_version: str
) -> None:
    harness.run_fixture(
        "feature-api",
        candidate_version,
        ["clean", "compile"],
        profile="aliases",
        label="recompile unchanged source through all seven candidate aliases",
    )
    harness.run_fixture(
        "feature-api",
        candidate_version,
        ["clean", "compile"],
        profile="canonical",
        label="compile representative FQCNs through all seven canonical features",
    )
    mixed_classpath = harness.build_classpath(
        "feature-api",
        candidate_version,
        profile="mixed",
        output_name="mixed-classpath.txt",
        clean=True,
    )
    assert_artifacts_present(
        mixed_classpath,
        CANONICAL_FEATURE_ARTIFACTS + ALIAS_FEATURE_ARTIFACTS,
        candidate_version,
    )
    assert_facades_source_free(
        mixed_classpath,
        ALIAS_FEATURE_ARTIFACTS,
        candidate_version,
        ("io/github/coco/feature/",),
    )
    assert_unique_implementation_classes(
        mixed_classpath,
        class_prefixes=("io/github/coco/feature/",),
        expected_entries=FEATURE_CLASS_ENTRIES,
    )
    print(
        "[PASS] same-version canonical and alias feature mix has one implementation owner"
    )


def verify_runtime_feature_registrations(
    harness: MavenHarness, candidate_version: str
) -> None:
    feature_fixture = harness.fixtures / "feature-api"
    for profile, classpath_name in RUNTIME_FEATURE_PROFILES:
        candidate_classpath = harness.build_classpath(
            "feature-api",
            candidate_version,
            profile=profile,
            output_name=f"runtime-{profile}-classpath.txt",
            clean=True,
            compile_goal="test-compile",
        )
        assert_artifacts_present(
            candidate_classpath, CANONICAL_FEATURE_ARTIFACTS, candidate_version
        )
        if profile == "canonical":
            assert_artifacts_absent(
                candidate_classpath, ALIAS_FEATURE_ARTIFACTS, candidate_version
            )
        else:
            assert_artifacts_present(
                candidate_classpath, ALIAS_FEATURE_ARTIFACTS, candidate_version
            )

        runtime_classpath = os.pathsep.join(
            [
                str(feature_fixture / "target/test-classes"),
                str(feature_fixture / "target/classes"),
                *(str(path) for path in candidate_classpath),
            ]
        )
        result = run_command(
            f"refresh Servlet Web ApplicationContext on {classpath_name} candidate classpath",
            [
                harness.java,
                "-cp",
                runtime_classpath,
                RUNTIME_FEATURE_CONSUMER_CLASS,
                classpath_name,
            ],
            cwd=feature_fixture,
            env=harness.env,
        )
        evidence = assert_runtime_registration_evidence(result.stdout, classpath_name)
        print(f"[EVID] {evidence}")
        print(
            f"[PASS] {classpath_name} has one runtime registration for all seven Coco features"
        )


def compile_candidate_test_support_consumers(
    harness: MavenHarness, candidate_version: str
) -> None:
    harness.run_fixture(
        "test-support",
        candidate_version,
        ["clean", "compile"],
        profile="alias",
        label="compile CocoTestSupport through the candidate coco-test facade",
    )
    harness.run_fixture(
        "test-support",
        candidate_version,
        ["clean", "compile"],
        profile="canonical",
        label="compile CocoTestSupport through coco-test-support",
    )
    mixed_classpath = harness.build_classpath(
        "test-support",
        candidate_version,
        profile="mixed",
        output_name="mixed-classpath.txt",
        clean=True,
    )
    assert_artifacts_present(
        mixed_classpath, ("coco-test-support", "coco-test"), candidate_version
    )
    assert_facades_source_free(
        mixed_classpath,
        ("coco-test",),
        candidate_version,
        ("io/github/coco/test/",),
    )
    assert_unique_implementation_classes(
        mixed_classpath,
        class_prefixes=("io/github/coco/test/",),
        expected_entries=(TEST_SUPPORT_CLASS_ENTRY,),
    )
    print(
        "[PASS] same-version coco-test and coco-test-support mix has one implementation owner"
    )


def verify_alignment_failure(harness: MavenHarness, candidate_version: str) -> None:
    result = harness.run_fixture(
        "version-alignment",
        candidate_version,
        ["coco:features"],
        check=False,
        label="reject candidate starter plus pinned coco-feature-web:2.0.1",
    )
    if result.returncode == 0:
        raise HarnessError(
            "coco:features accepted a deliberately misaligned feature artifact."
        )
    assert_alignment_diagnostic(result.stdout, candidate_version)
    print("[PASS] coco:features emitted the version-alignment diagnostic")


def verify_codegen(harness: MavenHarness, version: str) -> None:
    fixture = harness.fixtures / "codegen"
    before = directory_digest(fixture)
    harness.run_fixture(
        "codegen",
        version,
        ["clean", "compile", "coco:generate"],
        label=f"compile Codegen API and invoke coco:generate at {version}",
    )
    after = directory_digest(fixture)
    if before != after:
        raise HarnessError("Codegen changed fixture files outside target/.")
    generated = generated_java_files(fixture / "target/generated-coco")
    if generated != EXPECTED_CODEGEN_FILES:
        raise HarnessError(
            f"Codegen output differs: expected {sorted(EXPECTED_CODEGEN_FILES)}, "
            f"found {sorted(generated)}."
        )
    print("[PASS] coco:generate wrote only the deterministic target/ output set")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Run Coco external Maven compatibility consumers.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument(
        "--candidate-version",
        help="Candidate Coco version passed to Maven as -Dcoco.version.",
    )
    parser.add_argument(
        "--install-candidate",
        action="store_true",
        help="Install the current reactor into the isolated temporary Maven repository first.",
    )
    parser.add_argument(
        "--baseline-only",
        action="store_true",
        help="Compile the published 2.0.1 alias/test consumers and run 2.0.1 Codegen only.",
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Validate fixture structure without requiring Java or Maven.",
    )
    parser.add_argument(
        "--jdk-home",
        help="JDK 21 home. Defaults to JAVA_HOME, then java on PATH.",
    )
    parser.add_argument(
        "--maven",
        help="Maven executable name or path. Defaults to mvn/mvn.cmd on PATH.",
    )
    return parser


def run(args: argparse.Namespace) -> None:
    validate_fixture_contracts()
    print("[PASS] standalone fixture POM and BOM contracts")
    if args.validate_only:
        return
    if args.baseline_only and args.install_candidate:
        raise HarnessError(
            "--baseline-only cannot be combined with --install-candidate."
        )
    if not args.baseline_only and not args.candidate_version:
        raise HarnessError(
            "--candidate-version is required for the full compatibility suite."
        )
    if args.candidate_version == BASELINE_VERSION:
        raise HarnessError(
            "Candidate version must differ from the published 2.0.1 baseline."
        )

    maven, java, env = prepare_toolchain(args.jdk_home, args.maven)
    tracked_codegen_digest = directory_digest(FIXTURE_ROOT / "codegen")
    with tempfile.TemporaryDirectory(prefix="coco-compat-") as temporary_directory:
        work_directory = Path(temporary_directory)
        copied_fixtures = work_directory / "fixtures"
        shutil.copytree(FIXTURE_ROOT, copied_fixtures)
        local_repository = work_directory / "m2"
        local_repository.mkdir()
        harness = MavenHarness(
            maven=maven,
            java=java,
            env=env,
            local_repository=local_repository,
            fixtures=copied_fixtures,
        )

        if args.install_candidate:
            harness.install_candidate(args.candidate_version)

        baseline_hash = compile_baseline_consumers(harness)
        if args.baseline_only:
            verify_codegen(harness, BASELINE_VERSION)
        else:
            candidate_version = args.candidate_version
            compile_dual_path_consumers(harness, candidate_version)
            run_binary_compatibility(harness, candidate_version, baseline_hash)
            compile_candidate_feature_consumers(harness, candidate_version)
            verify_runtime_feature_registrations(harness, candidate_version)
            compile_candidate_test_support_consumers(harness, candidate_version)
            verify_alignment_failure(harness, candidate_version)
            verify_codegen(harness, candidate_version)

    if directory_digest(FIXTURE_ROOT / "codegen") != tracked_codegen_digest:
        raise HarnessError("Tracked Codegen fixture files changed during the run.")


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    try:
        run(args)
    except HarnessError as error:
        print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    print("[PASS] compatibility consumer harness completed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
