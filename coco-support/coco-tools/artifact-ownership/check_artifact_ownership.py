#!/usr/bin/env python3
"""Validate canonical and compatibility-facade Maven artifacts."""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence
from zipfile import BadZipFile, ZipFile


CANONICAL = "canonical"
LEGACY = "legacy"


@dataclass(frozen=True)
class ArtifactSpec:
    """Expected ownership and representative type for one Maven artifact."""

    artifact_id: str
    module_path: Path
    ownership: str
    representative_type: str | None = None

    def __post_init__(self) -> None:
        if self.ownership not in {CANONICAL, LEGACY}:
            raise ValueError(f"unsupported ownership: {self.ownership}")
        if self.ownership == CANONICAL and not self.representative_type:
            raise ValueError("canonical artifacts require a representative type")
        if self.ownership == LEGACY and self.representative_type is not None:
            raise ValueError("legacy artifacts must not declare a representative type")

    def representative_entry(self, suffix: str) -> str:
        if self.representative_type is None:
            raise ValueError("legacy artifacts do not have representative entries")
        return f"{self.representative_type.replace('.', '/')}{suffix}"


CANONICAL_ARTIFACTS = (
    ArtifactSpec(
        "coco-spring-boot-autoconfigure",
        Path("coco-spring/coco-spring-boot-autoconfigure"),
        CANONICAL,
        "io.github.coco.config.CocoConfigAutoConfiguration",
    ),
    ArtifactSpec(
        "coco-web",
        Path("coco-features/coco-web"),
        CANONICAL,
        "io.github.coco.feature.web.CocoWebAutoConfiguration",
    ),
    ArtifactSpec(
        "coco-mybatis-plus",
        Path("coco-features/coco-mybatis-plus"),
        CANONICAL,
        "io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration",
    ),
    ArtifactSpec(
        "coco-tenant",
        Path("coco-features/coco-tenant"),
        CANONICAL,
        "io.github.coco.feature.tenant.CocoTenantAutoConfiguration",
    ),
    ArtifactSpec(
        "coco-data-permission",
        Path("coco-features/coco-data-permission"),
        CANONICAL,
        "io.github.coco.feature.datapermission.CocoDataPermissionAutoConfiguration",
    ),
    ArtifactSpec(
        "coco-audit",
        Path("coco-features/coco-audit"),
        CANONICAL,
        "io.github.coco.feature.audit.CocoAuditAutoConfiguration",
    ),
    ArtifactSpec(
        "coco-openapi",
        Path("coco-features/coco-openapi"),
        CANONICAL,
        "io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration",
    ),
    ArtifactSpec(
        "coco-security",
        Path("coco-features/coco-security"),
        CANONICAL,
        "io.github.coco.feature.security.CocoSecurityAutoConfiguration",
    ),
    ArtifactSpec(
        "coco-test-support",
        Path("coco-support/coco-test-support"),
        CANONICAL,
        "io.github.coco.test.CocoTestSupport",
    ),
)

LEGACY_ARTIFACT_IDS = (
    "coco-config",
    "coco-feature-runtime",
    "coco-feature-web",
    "coco-feature-mybatis-plus",
    "coco-feature-tenant",
    "coco-feature-data-permission",
    "coco-feature-audit",
    "coco-feature-openapi",
    "coco-feature-security",
    "coco-test",
)

LEGACY_ARTIFACTS = tuple(
    ArtifactSpec(
        artifact_id,
        Path("coco-build/coco-compatibility") / artifact_id,
        LEGACY,
    )
    for artifact_id in LEGACY_ARTIFACT_IDS
)

ARTIFACTS = CANONICAL_ARTIFACTS + LEGACY_ARTIFACTS

_ALLOWED_LEGACY_META_INF_FILES = {
    "meta-inf/manifest.mf",
    "meta-inf/index.list",
}
_JAVADOC_ROOT_NON_TYPE_PAGES = {
    "allclasses-index.html",
    "allpackages-index.html",
    "deprecated-list.html",
    "help-doc.html",
    "index-all.html",
    "index.html",
    "module-summary.html",
    "overview-summary.html",
    "overview-tree.html",
    "search.html",
    "serialized-form.html",
}
_JAVADOC_PACKAGE_PAGES = {
    "package-summary.html",
    "package-tree.html",
    "package-use.html",
}


class JarReadError(Exception):
    """Raised when a required JAR cannot be read completely."""


def find_repository_root(start: Path | None = None) -> Path:
    """Find the Coco repository root from this script or a supplied path."""
    location = (start or Path(__file__)).resolve()
    current = location if location.is_dir() else location.parent
    for candidate in (current, *current.parents):
        if (candidate / "pom.xml").is_file() and (
            candidate / "coco-support/coco-tools"
        ).is_dir():
            return candidate
    raise ValueError(f"cannot locate repository root from {location}")


def validate_artifact_version(value: str) -> str:
    """Reject empty versions and values that could escape a target directory."""
    version = value.strip()
    if not version:
        raise argparse.ArgumentTypeError("artifact version must not be empty")
    if Path(version).name != version or "/" in version or "\\" in version:
        raise argparse.ArgumentTypeError(
            "artifact version must be a single Maven version segment"
        )
    return version


def artifact_jar_path(
    repository_root: Path,
    spec: ArtifactSpec,
    version: str,
    classifier: str | None = None,
) -> Path:
    """Return the expected target path for an artifact and classifier."""
    suffix = f"-{classifier}" if classifier else ""
    filename = f"{spec.artifact_id}-{version}{suffix}.jar"
    return repository_root / spec.module_path / "target" / filename


def read_jar_entries(path: Path) -> tuple[str, ...]:
    """Read every JAR member and verify member CRCs."""
    try:
        with ZipFile(path) as archive:
            corrupt_member = archive.testzip()
            if corrupt_member is not None:
                raise JarReadError(f"CRC check failed for member {corrupt_member}")
            return tuple(
                info.filename.replace("\\", "/") for info in archive.infolist()
            )
    except JarReadError:
        raise
    except (
        BadZipFile,
        EOFError,
        NotImplementedError,
        OSError,
        RuntimeError,
        ValueError,
    ) as exc:
        detail = str(exc) or exc.__class__.__name__
        raise JarReadError(detail) from exc


def _load_required_jar(
    path: Path, artifact_label: str, jar_label: str
) -> tuple[tuple[str, ...] | None, list[str]]:
    if not path.is_file():
        return None, [f"{artifact_label} missing {jar_label} JAR: {path}"]
    try:
        return read_jar_entries(path), []
    except JarReadError as exc:
        return None, [f"{artifact_label} unreadable {jar_label} JAR {path}: {exc}"]


def _is_allowed_legacy_metadata(entry: str) -> bool:
    if entry.endswith("/"):
        return True
    normalized = entry.lower()
    if normalized in _ALLOWED_LEGACY_META_INF_FILES:
        return True
    if normalized.startswith("meta-inf/maven/"):
        return normalized.rsplit("/", maxsplit=1)[-1] in {
            "pom.properties",
            "pom.xml",
        }
    if normalized.startswith(
        ("meta-inf/license", "meta-inf/notice", "meta-inf/dependencies")
    ):
        return "/" not in normalized.removeprefix("meta-inf/")
    return False


def _legacy_main_errors(entries: Iterable[str], artifact_label: str) -> list[str]:
    files = sorted(entry for entry in entries if not entry.endswith("/"))
    classes = [entry for entry in files if entry.lower().endswith(".class")]
    resources = [
        entry
        for entry in files
        if not entry.lower().endswith(".class")
        and not _is_allowed_legacy_metadata(entry)
    ]
    errors = []
    if classes:
        errors.append(
            f"{artifact_label} legacy main JAR contains .class entries: "
            + ", ".join(classes)
        )
    if resources:
        errors.append(
            f"{artifact_label} legacy main JAR contains implementation resources "
            f"(only JAR metadata is allowed): {', '.join(resources)}"
        )
    return errors


def _javadoc_type_pages(entries: Iterable[str]) -> list[str]:
    pages = []
    for entry in entries:
        normalized = entry.replace("\\", "/")
        if not normalized.lower().endswith(".html"):
            continue
        if normalized.lower().startswith(("legal/", "resource-files/", "script-dir/")):
            continue
        basename = normalized.rsplit("/", maxsplit=1)[-1]
        if basename in _JAVADOC_PACKAGE_PAGES:
            continue
        if "/" not in normalized and basename in _JAVADOC_ROOT_NON_TYPE_PAGES:
            continue
        pages.append(normalized)
    return sorted(pages)


def check_artifact(
    repository_root: Path,
    spec: ArtifactSpec,
    version: str,
    require_attached: bool = False,
) -> list[str]:
    """Check one artifact and return every ownership violation."""
    artifact_label = f"[{spec.ownership}:{spec.artifact_id}]"
    main_path = artifact_jar_path(repository_root, spec, version)
    main_entries, errors = _load_required_jar(main_path, artifact_label, "main")

    if main_entries is not None:
        if spec.ownership == CANONICAL:
            representative_class = spec.representative_entry(".class")
            if representative_class not in main_entries:
                errors.append(
                    f"{artifact_label} canonical main JAR is missing representative "
                    f"class: {representative_class}"
                )
        else:
            errors.extend(_legacy_main_errors(main_entries, artifact_label))

    if not require_attached:
        return errors

    sources_path = artifact_jar_path(repository_root, spec, version, "sources")
    source_entries, source_errors = _load_required_jar(
        sources_path, artifact_label, "sources"
    )
    errors.extend(source_errors)

    javadoc_path = artifact_jar_path(repository_root, spec, version, "javadoc")
    javadoc_entries, javadoc_errors = _load_required_jar(
        javadoc_path, artifact_label, "Javadoc"
    )
    errors.extend(javadoc_errors)

    if source_entries is not None:
        if spec.ownership == CANONICAL:
            representative_source = spec.representative_entry(".java")
            if representative_source not in source_entries:
                errors.append(
                    f"{artifact_label} canonical sources JAR is missing representative "
                    f"source: {representative_source}"
                )
        else:
            java_entries = sorted(
                entry for entry in source_entries if entry.lower().endswith(".java")
            )
            if java_entries:
                errors.append(
                    f"{artifact_label} legacy sources JAR contains .java entries: "
                    + ", ".join(java_entries)
                )

    if javadoc_entries is not None:
        if spec.ownership == CANONICAL:
            representative_page = spec.representative_entry(".html")
            if representative_page not in javadoc_entries:
                errors.append(
                    f"{artifact_label} canonical Javadoc JAR is missing representative "
                    f"type page: {representative_page}"
                )
        else:
            type_pages = _javadoc_type_pages(javadoc_entries)
            if type_pages:
                errors.append(
                    f"{artifact_label} legacy Javadoc JAR contains type pages: "
                    + ", ".join(type_pages)
                )

    return errors


def check_repository(
    repository_root: Path,
    version: str,
    require_attached: bool = False,
    specs: Sequence[ArtifactSpec] = ARTIFACTS,
) -> list[str]:
    """Check every configured ownership artifact."""
    errors = []
    for spec in specs:
        errors.extend(check_artifact(repository_root, spec, version, require_attached))
    return errors


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Validate canonical implementation JARs and source-free legacy facades."
        )
    )
    parser.add_argument(
        "--version",
        required=True,
        type=validate_artifact_version,
        help="Maven artifact version used in target JAR filenames",
    )
    parser.add_argument(
        "--repository-root",
        type=Path,
        help="repository root; defaults to the root containing this script",
    )
    parser.add_argument(
        "--require-attached",
        action="store_true",
        help="also validate sources and Javadoc JARs",
    )
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        repository_root = (
            args.repository_root.resolve()
            if args.repository_root is not None
            else find_repository_root()
        )
    except (OSError, ValueError) as exc:
        print(
            f"artifact ownership check could not locate the repository: {exc}",
            file=sys.stderr,
        )
        return 2

    if not repository_root.is_dir() or not (repository_root / "pom.xml").is_file():
        print(
            f"artifact ownership check requires a repository root with pom.xml: "
            f"{repository_root}",
            file=sys.stderr,
        )
        return 2

    errors = check_repository(
        repository_root,
        args.version,
        require_attached=args.require_attached,
    )
    if errors:
        print(
            f"artifact ownership check failed for version {args.version} "
            f"with {len(errors)} error(s):",
            file=sys.stderr,
        )
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1

    attached_description = (
        "main, sources, and Javadoc JARs" if args.require_attached else "main JARs"
    )
    print(
        f"artifact ownership check passed for version {args.version}: "
        f"{len(ARTIFACTS)} artifacts, {attached_description}."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
