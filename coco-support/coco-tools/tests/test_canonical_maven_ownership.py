#!/usr/bin/env python3

from __future__ import annotations

import unittest
import xml.etree.ElementTree as ET
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Ownership:
    legacy_artifact: str
    canonical_artifact: str
    canonical_module: Path

    @property
    def compatibility_module(self) -> Path:
        return Path("coco-build/coco-compatibility") / self.legacy_artifact


OWNERSHIPS = (
    Ownership(
        "coco-config",
        "coco-spring-boot-autoconfigure",
        Path("coco-spring/coco-spring-boot-autoconfigure"),
    ),
    Ownership(
        "coco-feature-runtime",
        "coco-spring-boot-autoconfigure",
        Path("coco-spring/coco-spring-boot-autoconfigure"),
    ),
    Ownership(
        "coco-feature-web",
        "coco-web",
        Path("coco-features/coco-web"),
    ),
    Ownership(
        "coco-feature-mybatis-plus",
        "coco-mybatis-plus",
        Path("coco-features/coco-mybatis-plus"),
    ),
    Ownership(
        "coco-feature-audit",
        "coco-audit",
        Path("coco-features/coco-audit"),
    ),
    Ownership(
        "coco-feature-security",
        "coco-security",
        Path("coco-features/coco-security"),
    ),
    Ownership(
        "coco-feature-tenant",
        "coco-tenant",
        Path("coco-features/coco-tenant"),
    ),
    Ownership(
        "coco-feature-data-permission",
        "coco-data-permission",
        Path("coco-features/coco-data-permission"),
    ),
    Ownership(
        "coco-feature-openapi",
        "coco-openapi",
        Path("coco-features/coco-openapi"),
    ),
    Ownership(
        "coco-test",
        "coco-test-support",
        Path("coco-support/coco-test-support"),
    ),
)

RUNTIME_OWNERSHIPS = OWNERSHIPS[:-1]
LEGACY_RUNTIME_ARTIFACTS = frozenset(
    ownership.legacy_artifact for ownership in RUNTIME_OWNERSHIPS
)
CANONICAL_RUNTIME_ARTIFACTS = frozenset(
    ownership.canonical_artifact for ownership in RUNTIME_OWNERSHIPS
)
HISTORICAL_FIXTURE_ROOT = Path(
    "coco-support/coco-tools/compatibility-consumers/fixtures"
)
IGNORED_DIRECTORY_NAMES = frozenset(
    {".codegraph", ".codex", ".git", ".worktrees", "target"}
)
COCO_GROUP_IDS = frozenset(
    {
        "io.github.patton174",
        "${pom.groupId}",
        "${project.groupId}",
        "${project.parent.groupId}",
    }
)


def repository_root() -> Path:
    for candidate in Path(__file__).resolve().parents:
        if (candidate / "pom.xml").is_file() and (candidate / "AGENTS.md").is_file():
            return candidate
    raise RuntimeError("repository root not found")


def read_pom(path: Path) -> ET.Element:
    return ET.parse(path).getroot()


def local_name(name: str) -> str:
    return name.rsplit("}", 1)[-1]


def direct_child(parent: ET.Element, name: str) -> ET.Element | None:
    return next(
        (child for child in parent if local_name(child.tag) == name),
        None,
    )


def direct_children(parent: ET.Element, name: str) -> list[ET.Element]:
    return [child for child in parent if local_name(child.tag) == name]


def child_text(parent: ET.Element, name: str) -> str:
    child = direct_child(parent, name)
    if child is None or child.text is None:
        return ""
    return child.text.strip()


def project_artifact_id(project: ET.Element) -> str:
    return child_text(project, "artifactId")


def direct_dependencies(project: ET.Element) -> list[ET.Element]:
    dependencies = direct_child(project, "dependencies")
    if dependencies is None:
        return []
    return direct_children(dependencies, "dependency")


def managed_dependencies(project: ET.Element) -> list[ET.Element]:
    management = direct_child(project, "dependencyManagement")
    if management is None:
        return []
    dependencies = direct_child(management, "dependencies")
    if dependencies is None:
        return []
    return direct_children(dependencies, "dependency")


def unmanaged_dependencies(project: ET.Element) -> list[ET.Element]:
    managed = {
        id(dependency)
        for management in project.iter()
        if local_name(management.tag) == "dependencyManagement"
        for dependency in management.iter()
        if local_name(dependency.tag) == "dependency"
    }
    return [
        dependency
        for dependency in project.iter()
        if local_name(dependency.tag) == "dependency" and id(dependency) not in managed
    ]


def release_plugin(project: ET.Element, artifact_id: str) -> ET.Element | None:
    profiles = direct_child(project, "profiles")
    if profiles is None:
        return None
    for profile in direct_children(profiles, "profile"):
        if child_text(profile, "id") != "release":
            continue
        build = direct_child(profile, "build")
        plugins = direct_child(build, "plugins") if build is not None else None
        if plugins is None:
            return None
        return next(
            (
                plugin
                for plugin in direct_children(plugins, "plugin")
                if child_text(plugin, "artifactId") == artifact_id
            ),
            None,
        )
    return None


def source_files(source_root: Path) -> list[Path]:
    if not source_root.is_dir():
        return []
    return sorted(path for path in source_root.rglob("*") if path.is_file())


def reactor_poms(root: Path) -> set[Path]:
    pending = [root / "pom.xml"]
    result: set[Path] = set()
    while pending:
        pom = pending.pop(0).resolve()
        if pom in result:
            continue
        project = read_pom(pom)
        result.add(pom)
        modules = direct_child(project, "modules")
        if modules is None:
            continue
        for module in direct_children(modules, "module"):
            module_path = (module.text or "").strip()
            if module_path:
                pending.append(pom.parent / module_path / "pom.xml")
    return result


def repository_poms(root: Path) -> list[Path]:
    result = []
    for pom in root.rglob("pom.xml"):
        relative = pom.relative_to(root)
        if any(part in IGNORED_DIRECTORY_NAMES for part in relative.parts):
            continue
        if relative.is_relative_to(HISTORICAL_FIXTURE_ROOT):
            continue
        result.append(pom)
    return sorted(result)


class CanonicalMavenOwnershipTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.root = repository_root()

    def test_root_and_bom_manage_canonical_and_legacy_coordinates(self) -> None:
        expected_artifacts = {
            artifact
            for ownership in OWNERSHIPS
            for artifact in (
                ownership.legacy_artifact,
                ownership.canonical_artifact,
            )
        }

        for relative_pom in (
            Path("pom.xml"),
            Path("coco-build/coco-dependencies/pom.xml"),
        ):
            with self.subTest(pom=relative_pom.as_posix()):
                dependencies_by_artifact: dict[str, list[ET.Element]] = defaultdict(
                    list
                )
                for dependency in managed_dependencies(
                    read_pom(self.root / relative_pom)
                ):
                    dependencies_by_artifact[
                        child_text(dependency, "artifactId")
                    ].append(dependency)

                self.assertEqual(
                    set(),
                    expected_artifacts - dependencies_by_artifact.keys(),
                    f"missing managed coordinates in {relative_pom.as_posix()}",
                )
                for ownership in OWNERSHIPS:
                    legacy = dependencies_by_artifact[ownership.legacy_artifact]
                    canonical = dependencies_by_artifact[ownership.canonical_artifact]
                    self.assertEqual(1, len(legacy), ownership.legacy_artifact)
                    self.assertEqual(1, len(canonical), ownership.canonical_artifact)
                    self.assertTrue(child_text(legacy[0], "groupId"))
                    self.assertEqual(
                        child_text(legacy[0], "groupId"),
                        child_text(canonical[0], "groupId"),
                    )
                    self.assertEqual(
                        child_text(legacy[0], "version"),
                        child_text(canonical[0], "version"),
                    )
                    self.assertTrue(child_text(legacy[0], "version"))

    def test_starter_uses_all_canonical_runtime_coordinates_and_no_legacy(self) -> None:
        starter = read_pom(self.root / "coco-spring/coco-spring-boot-starter/pom.xml")
        runtime_artifacts = {
            child_text(dependency, "artifactId")
            for dependency in direct_dependencies(starter)
            if child_text(dependency, "scope") not in {"provided", "test"}
        }
        renamed_artifacts = CANONICAL_RUNTIME_ARTIFACTS | LEGACY_RUNTIME_ARTIFACTS

        self.assertEqual(
            CANONICAL_RUNTIME_ARTIFACTS,
            runtime_artifacts & renamed_artifacts,
        )
        for ownership in RUNTIME_OWNERSHIPS:
            dependencies = [
                dependency
                for dependency in direct_dependencies(starter)
                if child_text(dependency, "artifactId") == ownership.canonical_artifact
                and child_text(dependency, "scope") not in {"provided", "test"}
            ]
            self.assertEqual(1, len(dependencies), ownership.canonical_artifact)
            self.assertIn(child_text(dependencies[0], "groupId"), COCO_GROUP_IDS)

    def test_legacy_modules_are_source_free_jar_facades(self) -> None:
        for ownership in OWNERSHIPS:
            with self.subTest(artifact=ownership.legacy_artifact):
                module = self.root / ownership.compatibility_module
                project = read_pom(module / "pom.xml")
                packaging = child_text(project, "packaging") or "jar"
                production_dependencies = [
                    dependency
                    for dependency in direct_dependencies(project)
                    if child_text(dependency, "scope") != "test"
                ]

                self.assertEqual(
                    ownership.legacy_artifact, project_artifact_id(project)
                )
                self.assertEqual("jar", packaging)
                self.assertEqual([], source_files(module / "src/main"))
                self.assertEqual(1, len(production_dependencies))

                dependency = production_dependencies[0]
                self.assertEqual(
                    ownership.canonical_artifact,
                    child_text(dependency, "artifactId"),
                )
                self.assertIn(child_text(dependency, "groupId"), COCO_GROUP_IDS)
                self.assertIn(child_text(dependency, "scope"), {"", "compile"})
                self.assertIn(child_text(dependency, "optional"), {"", "false"})
                self.assertIn(child_text(dependency, "type"), {"", "jar"})
                self.assertEqual("", child_text(dependency, "classifier"))
                self.assertIn(
                    child_text(dependency, "version"),
                    {"", "${project.version}", "${revision}"},
                )

    def test_release_profile_attaches_source_free_facade_archives(self) -> None:
        project = read_pom(self.root / "pom.xml")
        plugin = release_plugin(project, "maven-jar-plugin")
        self.assertIsNotNone(plugin)
        executions = direct_child(plugin, "executions")
        self.assertIsNotNone(executions)

        expected = {
            "attach-compatibility-sources": "sources",
            "attach-compatibility-javadocs": "javadoc",
        }
        actual = {}
        for execution in direct_children(executions, "execution"):
            execution_id = child_text(execution, "id")
            if execution_id not in expected:
                continue
            goals = direct_child(execution, "goals")
            configuration = direct_child(execution, "configuration")
            self.assertIsNotNone(goals)
            self.assertIsNotNone(configuration)
            self.assertEqual("package", child_text(execution, "phase"))
            self.assertEqual(
                ["jar"],
                [(goal.text or "").strip() for goal in direct_children(goals, "goal")],
            )
            self.assertEqual(
                "${project.basedir}/../facade-attachments",
                child_text(configuration, "classesDirectory"),
            )
            self.assertEqual("true", child_text(configuration, "skipIfEmpty"))
            actual[execution_id] = child_text(configuration, "classifier")

        self.assertEqual(expected, actual)
        attachment_files = source_files(
            self.root / "coco-build/coco-compatibility/facade-attachments"
        )
        self.assertTrue(attachment_files)
        self.assertFalse(
            any(path.suffix in {".java", ".class"} for path in attachment_files)
        )

    def test_canonical_modules_are_the_only_implementation_owners(self) -> None:
        artifacts_to_poms: dict[str, list[Path]] = defaultdict(list)
        for pom in reactor_poms(self.root):
            relative_pom = pom.relative_to(self.root)
            artifacts_to_poms[project_artifact_id(read_pom(pom))].append(relative_pom)

        for ownership in OWNERSHIPS:
            with self.subTest(artifact=ownership.canonical_artifact):
                canonical_pom = ownership.canonical_module / "pom.xml"
                compatibility_pom = ownership.compatibility_module / "pom.xml"
                implementation_files = source_files(
                    self.root / ownership.canonical_module / "src/main"
                )

                self.assertEqual(
                    [canonical_pom],
                    artifacts_to_poms[ownership.canonical_artifact],
                )
                self.assertEqual(
                    [compatibility_pom],
                    artifacts_to_poms[ownership.legacy_artifact],
                )
                self.assertTrue(
                    any(path.suffix == ".java" for path in implementation_files),
                    f"no Java implementation in {ownership.canonical_module.as_posix()}",
                )

    def test_repository_dependencies_do_not_use_legacy_runtime_artifacts(self) -> None:
        violations = []
        for pom in repository_poms(self.root):
            relative_pom = pom.relative_to(self.root).as_posix()
            for dependency in unmanaged_dependencies(read_pom(pom)):
                artifact = child_text(dependency, "artifactId")
                if artifact in LEGACY_RUNTIME_ARTIFACTS:
                    violations.append(f"{relative_pom}: {artifact}")

        self.assertEqual([], violations)

    def test_dependency_scan_excludes_management_only(self) -> None:
        project = ET.fromstring(
            """
            <project>
              <dependencyManagement>
                <dependencies>
                  <dependency><artifactId>coco-feature-web</artifactId></dependency>
                </dependencies>
              </dependencyManagement>
              <dependencies>
                <dependency><artifactId>coco-feature-audit</artifactId></dependency>
              </dependencies>
            </project>
            """
        )

        self.assertEqual(
            ["coco-feature-audit"],
            [
                child_text(dependency, "artifactId")
                for dependency in unmanaged_dependencies(project)
            ],
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
