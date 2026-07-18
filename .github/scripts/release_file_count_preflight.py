from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path

MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0"
NS = {"m": MAVEN_NAMESPACE}
CHECKSUM_SUFFIXES = (".md5", ".sha1", ".sha256", ".sha512")
PROPERTY_PATTERN = re.compile(r"^\$\{([^}]+)}$")
NON_NEGATIVE_INTEGER = re.compile(r"^(0|[1-9][0-9]*)$")
PERIOD_PATTERN = re.compile(r"^[0-9]{4}-(0[1-9]|1[0-2])$")


class PreflightError(RuntimeError):
    pass


@dataclass(frozen=True)
class ReactorProject:
    directory: Path
    relative_directory: str
    group_id: str
    artifact_id: str
    packaging: str


@dataclass(frozen=True)
class ExpectedModule:
    path: str
    group_id: str
    artifact_id: str
    packaging: str
    artifacts: tuple[str, ...]


@dataclass(frozen=True)
class PublishedFile:
    module: str
    kind: str
    source_path: str | None
    published_name: str


@dataclass(frozen=True)
class Inventory:
    revision: str
    reactor_projects: int
    base_files: tuple[PublishedFile, ...]
    signature_files: tuple[PublishedFile, ...]
    checksum_files: tuple[PublishedFile, ...]

    @property
    def signed_payload_files(self) -> int:
        return len(self.base_files) + len(self.signature_files)

    def to_dict(self) -> dict[str, object]:
        return {
            "revision": self.revision,
            "reactor_projects": self.reactor_projects,
            "base_file_count": len(self.base_files),
            "signature_file_count": len(self.signature_files),
            "checksum_file_count": len(self.checksum_files),
            "signed_payload_files": self.signed_payload_files,
            "checksum_suffixes": list(CHECKSUM_SUFFIXES),
            "base_files": [asdict(item) for item in self.base_files],
            "signature_files": [asdict(item) for item in self.signature_files],
            "checksum_files": [asdict(item) for item in self.checksum_files],
        }


@dataclass(frozen=True)
class Capacity:
    limit: int
    used: int
    period: str
    source: str
    count_checksums: bool
    metadata_files: int

    def assess(self, inventory: Inventory) -> CapacityAssessment:
        checksum_expansion = (
            len(inventory.checksum_files) if self.count_checksums else 0
        )
        required = (
            inventory.signed_payload_files + checksum_expansion + self.metadata_files
        )
        remaining_after_release = self.limit - self.used - required
        return CapacityAssessment(
            limit=self.limit,
            used=self.used,
            required=required,
            remaining_after_release=remaining_after_release,
            shortfall=max(0, -remaining_after_release),
            period=self.period,
            source=self.source,
            signed_payload_files=inventory.signed_payload_files,
            checksum_expansion=checksum_expansion,
            metadata_expansion=self.metadata_files,
        )


@dataclass(frozen=True)
class CapacityAssessment:
    limit: int
    used: int
    required: int
    remaining_after_release: int
    shortfall: int
    period: str
    source: str
    signed_payload_files: int
    checksum_expansion: int
    metadata_expansion: int

    @property
    def passed(self) -> bool:
        return self.remaining_after_release >= 0


def _text(element: ET.Element, path: str, default: str = "") -> str:
    child = element.find(path, NS)
    if child is None or child.text is None:
        return default
    return child.text.strip()


def _parse_pom(path: Path) -> ET.Element:
    try:
        return ET.parse(path).getroot()
    except (OSError, ET.ParseError) as error:
        raise PreflightError(f"Cannot parse Maven POM {path}: {error}") from error


def load_reactor(reactor_root: Path) -> tuple[ReactorProject, ...]:
    root = reactor_root.resolve()
    pending = [root]
    seen: set[Path] = set()
    projects: list[ReactorProject] = []

    while pending:
        directory = pending.pop(0).resolve()
        if directory in seen:
            raise PreflightError(f"Duplicate reactor project directory: {directory}")
        if not directory.is_relative_to(root):
            raise PreflightError(f"Reactor module escapes repository root: {directory}")
        seen.add(directory)

        pom = directory / "pom.xml"
        project = _parse_pom(pom)
        artifact_id = _text(project, "m:artifactId")
        if not artifact_id:
            raise PreflightError(f"Maven project has no artifactId: {pom}")
        group_id = _text(project, "m:groupId") or _text(project, "m:parent/m:groupId")
        if not group_id:
            raise PreflightError(f"Maven project has no resolvable groupId: {pom}")
        packaging = _text(project, "m:packaging", "jar")
        relative = directory.relative_to(root).as_posix() or "."
        projects.append(
            ReactorProject(directory, relative, group_id, artifact_id, packaging)
        )

        for module in project.findall("m:modules/m:module", NS):
            module_name = (module.text or "").strip()
            if not module_name:
                raise PreflightError(f"Empty reactor module in {pom}")
            pending.append(directory / module_name)

    return tuple(sorted(projects, key=lambda item: item.relative_directory))


def load_expectations(path: Path) -> tuple[ExpectedModule, ...]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise PreflightError(
            f"Cannot parse release expectations {path}: {error}"
        ) from error
    if data.get("schema_version") != 1:
        raise PreflightError("Release expectations must use schema_version 1")
    if tuple(data.get("checksum_suffixes", ())) != CHECKSUM_SUFFIXES:
        raise PreflightError(
            "Release expectations checksum suffixes do not match Central ALL"
        )
    group_id = data.get("group_id")
    raw_modules = data.get("modules")
    if (
        not isinstance(group_id, str)
        or not group_id
        or not isinstance(raw_modules, list)
    ):
        raise PreflightError("Release expectations require group_id and modules")

    modules: list[ExpectedModule] = []
    allowed_artifacts = {"pom", "main", "sources", "javadoc"}
    for raw in raw_modules:
        if not isinstance(raw, dict):
            raise PreflightError("Each release expectation module must be an object")
        artifacts = tuple(raw.get("artifacts", ()))
        if (
            not artifacts
            or artifacts[0] != "pom"
            or len(set(artifacts)) != len(artifacts)
            or not set(artifacts).issubset(allowed_artifacts)
        ):
            raise PreflightError(
                f"Invalid artifact set for expected module {raw.get('path')!r}"
            )
        packaging = str(raw.get("packaging", ""))
        if (packaging == "pom" and artifacts != ("pom",)) or (
            packaging != "pom" and "main" not in artifacts
        ):
            raise PreflightError(
                f"Expected module {raw.get('path')!r} has inconsistent packaging/artifacts"
            )
        modules.append(
            ExpectedModule(
                path=str(raw.get("path", "")),
                group_id=group_id,
                artifact_id=str(raw.get("artifact_id", "")),
                packaging=packaging,
                artifacts=artifacts,
            )
        )
    paths = [item.path for item in modules]
    if any(not item for item in paths) or len(set(paths)) != len(paths):
        raise PreflightError("Release expectations contain empty or duplicate paths")
    return tuple(sorted(modules, key=lambda item: item.path))


def validate_expected_reactor(
    projects: tuple[ReactorProject, ...],
    expected_modules: tuple[ExpectedModule, ...],
) -> dict[str, ExpectedModule]:
    actual_by_path = {item.relative_directory: item for item in projects}
    expected_by_path = {item.path: item for item in expected_modules}
    if actual_by_path.keys() != expected_by_path.keys():
        missing = sorted(expected_by_path.keys() - actual_by_path.keys())
        extra = sorted(actual_by_path.keys() - expected_by_path.keys())
        raise PreflightError(
            f"Reactor differs from release expectations; missing={missing}, extra={extra}"
        )
    for path, expected in expected_by_path.items():
        actual = actual_by_path[path]
        actual_coordinate = (actual.group_id, actual.artifact_id, actual.packaging)
        expected_coordinate = (
            expected.group_id,
            expected.artifact_id,
            expected.packaging,
        )
        if actual_coordinate != expected_coordinate:
            raise PreflightError(
                f"Release coordinate drift at {path}: expected {expected_coordinate}, "
                f"found {actual_coordinate}"
            )
    return expected_by_path


def _plugin(profile: ET.Element, artifact_id: str) -> ET.Element:
    for plugin in profile.findall("m:build/m:plugins/m:plugin", NS):
        if _text(plugin, "m:artifactId") == artifact_id:
            return plugin
    raise PreflightError(f"Release profile is missing {artifact_id}")


def _require_goal(
    plugin: ET.Element,
    plugin_name: str,
    goal: str,
    *,
    phase: str | None = None,
) -> None:
    for execution in plugin.findall("m:executions/m:execution", NS):
        goals = {
            (item.text or "").strip()
            for item in execution.findall("m:goals/m:goal", NS)
        }
        execution_phase = _text(execution, "m:phase")
        if goal in goals and (phase is None or execution_phase == phase):
            return
    phase_description = f" in phase {phase}" if phase else ""
    raise PreflightError(
        f"Release profile must run {plugin_name}:{goal}{phase_description}"
    )


def _resolve_property(root: ET.Element, value: str) -> str:
    match = PROPERTY_PATTERN.fullmatch(value)
    if match is None:
        return value
    property_name = match.group(1)
    properties = root.find("m:properties", NS)
    if properties is None:
        return ""
    for child in properties:
        if child.tag.rsplit("}", 1)[-1] == property_name:
            return (child.text or "").strip()
    return ""


def validate_release_profile(reactor_root: Path) -> None:
    root = _parse_pom(reactor_root.resolve() / "pom.xml")
    release_profile = None
    for profile in root.findall("m:profiles/m:profile", NS):
        if _text(profile, "m:id") == "release":
            release_profile = profile
            break
    if release_profile is None:
        raise PreflightError("Root POM is missing the release profile")

    source = _plugin(release_profile, "maven-source-plugin")
    _require_goal(source, "maven-source-plugin", "jar-no-fork")
    javadoc = _plugin(release_profile, "maven-javadoc-plugin")
    _require_goal(javadoc, "maven-javadoc-plugin", "jar")
    gpg = _plugin(release_profile, "maven-gpg-plugin")
    _require_goal(gpg, "maven-gpg-plugin", "sign", phase="verify")
    central = _plugin(release_profile, "central-publishing-maven-plugin")
    checksums = _resolve_property(root, _text(central, "m:configuration/m:checksums"))
    if checksums != "ALL":
        raise PreflightError(
            "Release profile must explicitly configure Central checksums as ALL"
        )


def _relative_source(root: Path, path: Path) -> str:
    return path.resolve().relative_to(root.resolve()).as_posix()


def _pom_signed_input(project: ReactorProject, revision: str) -> tuple[Path, Path]:
    pom = project.directory / "target" / f"{project.artifact_id}-{revision}.pom"
    signature = pom.with_name(f"{pom.name}.asc")
    if not pom.is_file() or not signature.is_file():
        raise PreflightError(
            f"{project.artifact_id} is missing its signed release POM at {signature}"
        )
    return pom, signature


def _main_extension(packaging: str) -> str:
    if packaging in {"jar", "maven-plugin"}:
        return "jar"
    if packaging in {"war", "ear", "rar"}:
        return packaging
    raise PreflightError(f"Unsupported release packaging: {packaging}")


def _signed_target_inputs(
    root: Path,
    project: ReactorProject,
    expectation: ExpectedModule,
    revision: str,
) -> tuple[PublishedFile, ...]:
    target = project.directory / "target"
    prefix = f"{project.artifact_id}-{revision}"
    if not target.is_dir():
        raise PreflightError(f"Missing release output directory: {target}")

    signed: list[PublishedFile] = []
    for signature in sorted(target.glob("*.asc"), key=lambda path: path.name):
        base = signature.with_suffix("")
        if not base.is_file() or not base.name.startswith(prefix):
            continue
        if base.suffix == ".pom":
            continue
        signed.append(
            PublishedFile(
                module=project.relative_directory,
                kind="attachment",
                source_path=_relative_source(root, base),
                published_name=base.name,
            )
        )

    names_by_kind = {
        "main": f"{prefix}.{_main_extension(project.packaging)}",
        "sources": f"{prefix}-sources.jar",
        "javadoc": f"{prefix}-javadoc.jar",
    }
    required_names = {
        names_by_kind[kind]: kind for kind in expectation.artifacts if kind != "pom"
    }
    by_name = {item.published_name: item for item in signed}
    missing = sorted(set(required_names) - set(by_name))
    extra = sorted(set(by_name) - set(required_names))
    if missing or extra:
        raise PreflightError(
            f"{project.artifact_id} signed outputs differ from expectations; "
            f"missing={missing}, extra={extra}"
        )

    return tuple(
        PublishedFile(
            module=item.module,
            kind=required_names.get(item.published_name, item.kind),
            source_path=item.source_path,
            published_name=item.published_name,
        )
        for item in signed
    )


def build_inventory(
    reactor_root: Path,
    revision: str,
    expectations_path: Path | None = None,
) -> Inventory:
    if not revision or "SNAPSHOT" in revision.upper():
        raise PreflightError(
            "Inventory revision must be a non-SNAPSHOT release version"
        )
    root = reactor_root.resolve()
    validate_release_profile(root)
    projects = load_reactor(root)
    expectations = load_expectations(
        expectations_path
        or root / ".github" / "release" / "release-artifact-expectations.json"
    )
    expected_by_path = validate_expected_reactor(projects, expectations)
    base_files: list[PublishedFile] = []
    signature_files: list[PublishedFile] = []

    for project in projects:
        expectation = expected_by_path[project.relative_directory]
        pom, pom_signature = _pom_signed_input(project, revision)
        pom_name = f"{project.artifact_id}-{revision}.pom"
        base_files.append(
            PublishedFile(
                module=project.relative_directory,
                kind="pom",
                source_path=_relative_source(root, pom),
                published_name=pom_name,
            )
        )
        signature_files.append(
            PublishedFile(
                module=project.relative_directory,
                kind="signature",
                source_path=_relative_source(root, pom_signature),
                published_name=f"{pom_name}.asc",
            )
        )

        if project.packaging == "pom":
            continue
        for item in _signed_target_inputs(root, project, expectation, revision):
            base_files.append(item)
            source = root / (item.source_path or "")
            signature = source.with_name(f"{source.name}.asc")
            signature_files.append(
                PublishedFile(
                    module=item.module,
                    kind="signature",
                    source_path=_relative_source(root, signature),
                    published_name=f"{item.published_name}.asc",
                )
            )

    base_files.sort(key=lambda item: (item.module, item.published_name))
    signature_files.sort(key=lambda item: (item.module, item.published_name))
    checksum_files = sorted(
        (
            PublishedFile(
                module=item.module,
                kind="checksum",
                source_path=None,
                published_name=f"{item.published_name}{suffix}",
            )
            for item in base_files
            for suffix in CHECKSUM_SUFFIXES
        ),
        key=lambda item: (item.module, item.published_name),
    )
    return Inventory(
        revision=revision,
        reactor_projects=len(projects),
        base_files=tuple(base_files),
        signature_files=tuple(signature_files),
        checksum_files=tuple(checksum_files),
    )


def _parse_non_negative(name: str, raw: str) -> int:
    if not NON_NEGATIVE_INTEGER.fullmatch(raw):
        raise PreflightError(f"{name} must be a non-negative integer; got {raw!r}")
    return int(raw)


def _parse_boolean(name: str, raw: str) -> bool:
    if raw not in {"true", "false"}:
        raise PreflightError(f"{name} must be 'true' or 'false'; got {raw!r}")
    return raw == "true"


def validate_capacity(
    *,
    limit: str,
    used: str,
    period: str,
    source: str,
    count_checksums: str,
    metadata_files: str,
    current_period: str | None = None,
) -> Capacity:
    parsed_limit = _parse_non_negative("limit", limit)
    parsed_used = _parse_non_negative("used", used)
    if parsed_limit == 0:
        raise PreflightError("limit must be greater than zero")
    if parsed_used > parsed_limit:
        raise PreflightError(
            f"used ({parsed_used}) must not exceed limit ({parsed_limit})"
        )
    if not PERIOD_PATTERN.fullmatch(period):
        raise PreflightError(f"period must use YYYY-MM; got {period!r}")
    expected_period = current_period or datetime.now(UTC).strftime("%Y-%m")
    if period != expected_period:
        raise PreflightError(
            f"capacity configuration is stale: period {period}, current period {expected_period}"
        )
    normalized_source = source.strip()
    if not normalized_source or "\n" in normalized_source or "\r" in normalized_source:
        raise PreflightError(
            "source must be a non-empty single-line provenance reference"
        )
    return Capacity(
        parsed_limit,
        parsed_used,
        period,
        normalized_source,
        _parse_boolean("count_checksums", count_checksums),
        _parse_non_negative("metadata_files", metadata_files),
    )


def render_report(inventory: Inventory, assessment: CapacityAssessment) -> str:
    status = "PASS" if assessment.passed else "FAIL"
    lines = [
        "## Maven Central file-count preflight",
        "",
        f"- Result: **{status}**",
        f"- Period: `{assessment.period}`",
        f"- Source: `{assessment.source}`",
        f"- Limit: **{assessment.limit}**",
        f"- Used: **{assessment.used}**",
        f"- Required by this release: **{assessment.required}**",
        f"- Remaining after release: **{assessment.remaining_after_release}**",
        f"- Shortfall: **{assessment.shortfall}**",
        f"- Reactor projects: **{inventory.reactor_projects}**",
        f"- Unsigned payload inputs: **{len(inventory.base_files)}**",
        f"- Signature files: **{len(inventory.signature_files)}**",
        f"- Signed payload before quota expansion: **{assessment.signed_payload_files}**",
        f"- Configured checksum files produced: **{len(inventory.checksum_files)}**",
        f"- Quota-counted checksum expansion: **{assessment.checksum_expansion}**",
        f"- Quota-counted metadata expansion: **{assessment.metadata_expansion}**",
    ]
    if not assessment.passed:
        minimum_limit = assessment.used + assessment.required
        lines.extend(
            [
                "",
                "Capacity is insufficient. Raise the organization limit to at least "
                f"**{minimum_limit}**, reduce recorded usage, or wait for the next "
                "period. Do not remove required POM, main, sources, javadoc, "
                "signature, or checksum files.",
            ]
        )
    return "\n".join(lines) + "\n"


def write_inventory(
    path: Path, inventory: Inventory, assessment: CapacityAssessment
) -> None:
    output = inventory.to_dict()
    output["quota_assessment"] = {
        **asdict(assessment),
        "passed": assessment.passed,
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(output, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def append_summary(path: Path, report: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8", newline="\n") as output:
        output.write(report)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Inventory signed reactor outputs and enforce Central capacity."
    )
    parser.add_argument("--limit", required=True)
    parser.add_argument("--used", required=True)
    parser.add_argument("--period", required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--count-checksums", required=True)
    parser.add_argument("--metadata-files", required=True)
    parser.add_argument("--validate-config-only", action="store_true")
    parser.add_argument("--reactor-root", type=Path, default=Path.cwd())
    parser.add_argument("--revision")
    parser.add_argument("--expectations", type=Path)
    parser.add_argument("--inventory-output", type=Path)
    parser.add_argument("--summary-output", type=Path)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        capacity = validate_capacity(
            limit=args.limit,
            used=args.used,
            period=args.period,
            source=args.source,
            count_checksums=args.count_checksums,
            metadata_files=args.metadata_files,
        )
        if args.validate_config_only:
            print(
                "Maven Central capacity configuration is current: "
                f"period={capacity.period} limit={capacity.limit} "
                f"used={capacity.used} count_checksums={capacity.count_checksums} "
                f"metadata_files={capacity.metadata_files} source={capacity.source}"
            )
            return 0
        if not args.revision:
            raise PreflightError("--revision is required for release inventory")

        inventory = build_inventory(args.reactor_root, args.revision, args.expectations)
        assessment = capacity.assess(inventory)
        report = render_report(inventory, assessment)
        if args.inventory_output:
            write_inventory(args.inventory_output, inventory, assessment)
        if args.summary_output:
            append_summary(args.summary_output, report)
        print(report, end="")
        return 0 if assessment.passed else 1
    except PreflightError as error:
        print(f"Maven Central file-count preflight error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
