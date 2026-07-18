#!/usr/bin/env python3
"""Build and attest a fresh public API compatibility comparison."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
import time
import uuid
from pathlib import Path
from typing import Any, Callable
from urllib import error, request
from xml.etree import ElementTree

from check_public_api_compatibility import (
    BASELINE_GROUP_ID,
    BASELINE_ORIGIN,
    BASELINE_SIGNING_FINGERPRINT,
    BASELINE_VERSION,
    ATTESTATION_SCHEMA_VERSION,
    BaselineLedger,
    ExpectedArtifact,
    canonical_manifest_entries,
    default_baseline_ledger_path,
    default_manifest_path,
    input_file_records,
    input_records_sha256,
    load_baseline_ledger,
    load_manifest,
    policy_bundle_sha256,
    regular_file_bytes,
    sha256_bytes,
    sha256_file,
    trusted_input_paths,
    validate_attested_reports,
    validate_baseline_files,
    validate_policy_assets,
)
from path_io import (
    atomic_create_bytes,
    atomic_create_text,
    directory_metadata,
    entry_exists,
    file_snapshot,
    logical_absolute,
    mkdir,
    rmtree,
    short_process_cwd,
)


CommandRunner = Callable[[list[str], Path], None]
WINDOWS_REACTOR_ROOT_LIMIT = 180


class NoRedirectHandler(request.HTTPRedirectHandler):
    def redirect_request(
        self,
        req: request.Request,
        fp: Any,
        code: int,
        msg: str,
        headers: Any,
        newurl: str,
    ) -> request.Request | None:
        raise RuntimeError(
            f"Maven Central redirect is forbidden: {req.full_url} -> {newurl} ({code})"
        )


def repository_root() -> Path:
    return logical_absolute(Path(__file__)).parents[3]


def default_signing_key_path() -> Path:
    return Path(__file__).with_name("baseline-signing-key.asc")


def run(arguments: list[str], cwd: Path) -> None:
    process_cwd = short_process_cwd(cwd, "Maven process working directory")
    completed = subprocess.run(arguments, cwd=process_cwd, check=False)
    if completed.returncode:
        raise RuntimeError(
            f"Command failed with exit code {completed.returncode}: {arguments}"
        )


def run_capture(arguments: list[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    process_cwd = short_process_cwd(cwd, "GPG process working directory")
    return subprocess.run(
        arguments,
        cwd=process_cwd,
        check=False,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
    )


def executable(name: str) -> str:
    path = shutil.which(name)
    if path is None:
        raise RuntimeError(f"Executable is not on PATH: {name}")
    return path


def configure_jdk(jdk_home: Path) -> None:
    jdk_home = logical_absolute(jdk_home)
    suffix = ".exe" if os.name == "nt" else ""
    java = jdk_home / "bin" / f"java{suffix}"
    javac = jdk_home / "bin" / f"javac{suffix}"
    try:
        regular_file_bytes(java, "JDK java executable")
        regular_file_bytes(javac, "JDK javac executable")
    except ValueError as exc:
        raise RuntimeError(f"JDK home does not contain java/javac: {jdk_home}") from exc
    os.environ["JAVA_HOME"] = str(logical_absolute(jdk_home))
    os.environ["PATH"] = f"{jdk_home / 'bin'}{os.pathsep}{os.environ['PATH']}"


def reactor_process_root(root: Path) -> Path:
    logical = short_process_cwd(root, "Reactor Maven working directory")
    regular_file_bytes(logical / "pom.xml", "Reactor root POM")
    if os.name == "nt" and len(str(logical)) >= WINDOWS_REACTOR_ROOT_LIMIT:
        raise RuntimeError(
            "Reactor checkout path is too deep for controlled Windows Maven execution: "
            f"length={len(str(logical))}, limit={WINDOWS_REACTOR_ROOT_LIMIT - 1}, "
            f"path={logical}"
        )
    return logical


def central_opener() -> request.OpenerDirector:
    return request.build_opener(request.ProxyHandler({}), NoRedirectHandler())


def central_url(artifact: str, suffix: str) -> str:
    group_path = BASELINE_GROUP_ID.replace(".", "/")
    filename = f"{artifact}-{BASELINE_VERSION}.{suffix}"
    return f"{BASELINE_ORIGIN}/{group_path}/{artifact}/{BASELINE_VERSION}/{filename}"


def download_exact(
    opener: request.OpenerDirector,
    url: str,
    destination: Path,
    *,
    expected_size: int | None = None,
) -> bytes:
    try:
        with opener.open(url, timeout=60) as response:
            status = response.getcode()
            final_url = response.geturl()
            if status != 200 or final_url != url:
                raise RuntimeError(
                    f"Unexpected Maven Central response for {url}: "
                    f"status={status}, finalUrl={final_url}"
                )
            content_length = response.headers.get("Content-Length")
            value = response.read()
    except error.HTTPError as exc:
        raise RuntimeError(f"Maven Central returned HTTP {exc.code} for {url}") from exc
    if not value:
        raise RuntimeError(f"Maven Central returned an empty file: {url}")
    try:
        declared_size = int(content_length or "")
    except ValueError as exc:
        raise RuntimeError(
            f"Maven Central omitted an exact Content-Length: {url}"
        ) from exc
    if declared_size != len(value):
        raise RuntimeError(
            f"Maven Central Content-Length mismatch for {url}: "
            f"declared={declared_size}, actual={len(value)}"
        )
    if expected_size is not None and len(value) != expected_size:
        raise RuntimeError(
            f"Maven Central fixed size mismatch for {url}: "
            f"expected={expected_size}, actual={len(value)}"
        )
    mkdir(destination.parent, parents=True, exist_ok=True)
    atomic_create_bytes(destination, value)
    return value


def require_central_404(opener: request.OpenerDirector, url: str) -> None:
    try:
        with opener.open(url, timeout=60) as response:
            raise RuntimeError(
                f"Expected Maven Central 404 but received HTTP {response.getcode()}: {url}"
            )
    except error.HTTPError as exc:
        if exc.code != 404 or exc.geturl() != url:
            raise RuntimeError(
                f"Expected direct Maven Central 404 for {url}; "
                f"received HTTP {exc.code} at {exc.geturl()}"
            ) from exc


def gpg_path(path: Path, gpg_executable: str) -> str:
    resolved = logical_absolute(path)
    executable_text = Path(gpg_executable).as_posix().lower()
    if os.name == "nt" and "/git/usr/bin/" in executable_text:
        value = resolved.as_posix()
        drive, remainder = value.split(":", 1)
        return f"/{drive.lower()}{remainder}"
    return str(resolved)


class GpgVerifier:
    def __init__(
        self,
        signing_key: Path,
        home: Path,
        process_runner: Callable[
            [list[str], Path], subprocess.CompletedProcess[str]
        ] = run_capture,
        *,
        process_cwd: Path | None = None,
    ) -> None:
        self.executable = executable("gpg")
        self.verifier_executable = executable("gpgv")
        self.home = mkdir(home, parents=True, exist_ok=False)
        self.keyring = self.home / "trustedkeys.gpg"
        self.process_runner = process_runner
        self.process_cwd = short_process_cwd(
            process_cwd or signing_key.parent, "GPG process working directory"
        )
        key_argument = gpg_path(signing_key, self.executable)
        inspection = process_runner(
            [
                self.executable,
                "--batch",
                "--no-options",
                "--with-colons",
                "--show-keys",
                key_argument,
            ],
            self.process_cwd,
        )
        fingerprints = [
            line.split(":")[9]
            for line in (inspection.stdout or "").splitlines()
            if line.startswith("fpr:")
        ]
        if (
            inspection.returncode
            or not fingerprints
            or fingerprints[0] != BASELINE_SIGNING_FINGERPRINT
        ):
            raise RuntimeError(
                "Checked-in signing key does not have the fixed release fingerprint: "
                f"exit={inspection.returncode}, fingerprints={fingerprints}, "
                f"stderr={(inspection.stderr or '').strip()!r}"
            )
        dearmored = process_runner(
            [
                self.executable,
                "--batch",
                "--no-options",
                "--yes",
                "--dearmor",
                "--output",
                gpg_path(self.keyring, self.executable),
                key_argument,
            ],
            self.process_cwd,
        )
        if dearmored.returncode:
            raise RuntimeError(
                "Could not create the fixed release verification keyring: "
                f"{(dearmored.stderr or '').strip()}"
            )
        regular_file_bytes(self.keyring, "fixed release verification keyring")

    def verify(self, signature: Path, artifact: Path) -> None:
        completed = self.process_runner(
            [
                self.verifier_executable,
                "--keyring",
                gpg_path(self.keyring, self.verifier_executable),
                "--status-fd",
                "1",
                gpg_path(signature, self.verifier_executable),
                gpg_path(artifact, self.verifier_executable),
            ],
            self.process_cwd,
        )
        valid_fingerprints = [
            line.split()[2]
            for line in (completed.stdout or "").splitlines()
            if line.startswith("[GNUPG:] VALIDSIG ")
        ]
        if completed.returncode or valid_fingerprints != [BASELINE_SIGNING_FINGERPRINT]:
            raise RuntimeError(
                f"Invalid release signature for {artifact}: exit={completed.returncode}, "
                f"validSignatures={valid_fingerprints}, "
                f"stderr={(completed.stderr or '').strip()!r}"
            )


def fetch_verified_baselines(
    workspace: Path,
    ledger: BaselineLedger,
    signing_key: Path,
    *,
    opener: request.OpenerDirector | None = None,
    verifier_factory: Callable[..., GpgVerifier] = GpgVerifier,
) -> list[dict[str, str]]:
    baseline_directory = workspace / "baseline"
    mkdir(baseline_directory, parents=True, exist_ok=False)
    direct_opener = opener or central_opener()
    verifier = verifier_factory(
        signing_key,
        workspace / "gnupg",
        process_cwd=signing_key.parent,
    )
    records: list[dict[str, str]] = []
    for entry in ledger.artifacts:
        prefix = baseline_directory / f"{entry.artifact}-{BASELINE_VERSION}"
        paths = {
            "pom": Path(f"{prefix}.pom"),
            "pom.asc": Path(f"{prefix}.pom.asc"),
            "jar": Path(f"{prefix}.jar"),
            "jar.asc": Path(f"{prefix}.jar.asc"),
        }
        expected_sizes = {"pom": entry.pom_size, "jar": entry.jar_size}
        for suffix, path in paths.items():
            download_exact(
                direct_opener,
                central_url(entry.artifact, suffix),
                path,
                expected_size=expected_sizes.get(suffix),
            )
        validate_baseline_files(entry, paths["pom"], paths["jar"])
        verifier.verify(paths["pom.asc"], paths["pom"])
        verifier.verify(paths["jar.asc"], paths["jar"])
        records.append(
            {
                "artifact": entry.artifact,
                "pomPath": str(logical_absolute(paths["pom"])),
                "pomSha256": entry.pom_sha256,
                "pomSignaturePath": str(logical_absolute(paths["pom.asc"])),
                "pomSignatureSha256": sha256_file(
                    paths["pom.asc"], f"{entry.artifact} POM signature"
                ),
                "jarPath": str(logical_absolute(paths["jar"])),
                "jarSha256": entry.jar_sha256,
                "jarSignaturePath": str(logical_absolute(paths["jar.asc"])),
                "jarSignatureSha256": sha256_file(
                    paths["jar.asc"], f"{entry.artifact} JAR signature"
                ),
                "signingFingerprint": BASELINE_SIGNING_FINGERPRINT,
            }
        )
    for artifact in ledger.missing_artifacts:
        require_central_404(direct_opener, central_url(artifact, "pom"))
        require_central_404(direct_opener, central_url(artifact, "jar"))
    return records


def candidate_path(root: Path, entry: ExpectedArtifact, version: str) -> Path:
    return root.joinpath(
        *entry.candidate_module.parts,
        "target",
        f"{entry.candidate_artifact}-{version}.jar",
    )


def snapshot_candidates(
    root: Path,
    canonical: tuple[ExpectedArtifact, ...],
    version: str,
    *,
    phase: str,
) -> dict[tuple[str, str], tuple[Path, str]]:
    snapshots: dict[tuple[str, str], tuple[Path, str]] = {}
    for entry in canonical:
        path = candidate_path(root, entry, version)
        digest = sha256_file(path, f"{entry.artifact} {phase} candidate JAR")
        snapshots[(entry.artifact, entry.module.as_posix())] = (path, digest)
    return snapshots


def clean_expected_reports(root: Path, artifacts: tuple[ExpectedArtifact, ...]) -> None:
    for entry in artifacts:
        target = root.joinpath(*entry.module.parts, "target")
        for report_root in (target / "japicmp", target / "public-api-compatibility"):
            if entry_exists(report_root):
                rmtree(report_root, f"{entry.artifact} stale report directory")
        if entry_exists(target / "japicmp" / "public-api-compatibility.xml"):
            raise RuntimeError(
                f"Could not clean legacy japicmp report for {entry.artifact}"
            )
        if entry_exists(target / "public-api-compatibility"):
            raise RuntimeError(
                f"Could not clean attested report root for {entry.artifact}"
            )


def comparison_entries(
    artifacts: tuple[ExpectedArtifact, ...], ledger: BaselineLedger
) -> tuple[ExpectedArtifact, ...]:
    available = {entry.artifact for entry in ledger.artifacts}
    selected = tuple(entry for entry in artifacts if entry.artifact in available)
    if {entry.artifact for entry in selected} != available:
        raise RuntimeError("Comparable Maven modules do not match the baseline ledger.")
    return selected


def report_path(root: Path, entry: ExpectedArtifact, run_id: str) -> Path:
    return root.joinpath(
        *entry.module.parts,
        "target",
        "public-api-compatibility",
        run_id,
        "japicmp",
        "public-api-compatibility.xml",
    )


def write_missing_baseline_reports(
    root: Path,
    artifacts: tuple[ExpectedArtifact, ...],
    missing_artifacts: tuple[str, ...],
    candidate_version: str,
    run_id: str,
) -> None:
    expected = set(missing_artifacts)
    selected = tuple(entry for entry in artifacts if entry.artifact in expected)
    if {entry.artifact for entry in selected} != expected:
        raise RuntimeError(
            "Current-only Maven modules do not match the baseline ledger."
        )
    for entry in selected:
        path = report_path(root, entry, run_id)
        if entry_exists(path):
            raise RuntimeError(
                f"Current-only artifact produced an unexpected japicmp report: {path}"
            )
        candidate = candidate_path(root, entry, candidate_version)
        regular_file_bytes(candidate, f"{entry.artifact} current-only candidate JAR")
        report = ElementTree.Element(
            "japicmp",
            {
                "oldVersion": "n.a.",
                "newVersion": candidate_version,
                "newJar": str(logical_absolute(candidate)),
                "evidenceSource": "verified-central-404",
            },
        )
        ElementTree.SubElement(report, "classes")
        mkdir(path.parent, parents=True, exist_ok=False)
        atomic_create_bytes(
            path,
            ElementTree.tostring(report, encoding="utf-8", xml_declaration=True),
        )


def collect_fresh_reports(
    root: Path,
    artifacts: tuple[ExpectedArtifact, ...],
    run_id: str,
    started_at_ns: int,
    finished_at_ns: int,
) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for entry in artifacts:
        path = report_path(root, entry, run_id)
        report_snapshot = file_snapshot(path, f"{entry.artifact} fresh japicmp report")
        report_bytes = report_snapshot.contents
        metadata = report_snapshot.metadata
        if metadata.st_mtime_ns < started_at_ns:
            raise RuntimeError(f"Japicmp report predates trusted run: {path}")
        if (
            metadata.st_mtime_ns > finished_at_ns
            or metadata.st_mtime_ns > time.time_ns()
        ):
            raise RuntimeError(f"Japicmp report is future-dated: {path}")
        records.append(
            {
                "artifact": entry.artifact,
                "module": entry.module.as_posix(),
                "path": str(logical_absolute(path)),
                "sha256": sha256_bytes(report_bytes),
                "size": len(report_bytes),
                "mtimeNs": metadata.st_mtime_ns,
            }
        )
    return records


def write_attestation(path: Path, value: dict[str, Any]) -> None:
    atomic_create_text(
        path,
        json.dumps(value, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def fresh_run_id() -> str:
    return f"run-{uuid.uuid4().hex}"


def execute_gate(
    root: Path,
    workspace: Path,
    candidate_version: str,
    manifest_path: Path,
    allowlist_path: Path,
    ledger_path: Path,
    signing_key_path: Path,
    protected_sha: str,
    *,
    command_runner: CommandRunner = run,
) -> Path:
    root = logical_absolute(root)
    workspace = logical_absolute(workspace)
    directory_metadata(root, "Repository root")
    directory_metadata(workspace, "Trusted work directory")
    process_root = reactor_process_root(root)
    if len(protected_sha) != 40 or any(
        character not in "0123456789abcdef" for character in protected_sha
    ):
        raise RuntimeError("Protected SHA must be a lowercase 40-hex commit ID.")
    manifest = load_manifest(manifest_path)
    ledger = load_baseline_ledger(ledger_path)
    validate_policy_assets(manifest_path, signing_key_path)
    manifest_artifacts = {entry.artifact for entry in manifest.artifacts}
    ledger_artifacts = {entry.artifact for entry in ledger.artifacts}
    ledger_missing = set(ledger.missing_artifacts)
    profile_present = {
        entry.artifact
        for entry in manifest.artifacts
        if entry.baseline_state == "present"
    }
    profile_missing = {
        entry.artifact
        for entry in manifest.artifacts
        if entry.baseline_state == "missing"
    }
    if (
        ledger_artifacts != profile_present
        or ledger_missing != profile_missing
        or ledger_artifacts | ledger_missing != manifest_artifacts
    ):
        raise RuntimeError("Baseline ledger does not exactly cover the API manifest.")
    canonical = canonical_manifest_entries(manifest)
    policy_digest = policy_bundle_sha256(
        manifest_path, ledger_path, allowlist_path, signing_key_path
    )

    baseline_records = fetch_verified_baselines(workspace, ledger, signing_key_path)
    maven = executable("mvn")
    common = [
        maven,
        "-B",
        "-ntp",
        "-Dstyle.color=never",
        "-f",
        str(process_root / "pom.xml"),
        f"-Dmaven.multiModuleProjectDirectory={process_root}",
        f"-Drevision={candidate_version}",
        "-DskipTests",
    ]
    command_runner([*common, "clean", "install"], process_root)
    candidates_before = snapshot_candidates(
        root, canonical, candidate_version, phase="before"
    )
    clean_expected_reports(root, manifest.artifacts)

    input_paths = trusted_input_paths(
        root,
        manifest,
        manifest_path,
        allowlist_path,
        ledger_path,
        signing_key_path,
    )
    inputs_before = input_file_records(root, input_paths)
    run_id = fresh_run_id()
    comparable = comparison_entries(manifest.artifacts, ledger)
    projects = ",".join(entry.module.as_posix() for entry in comparable)
    started_at_ns = time.time_ns()
    command_runner(
        [
            *common,
            "-Ppublic-api-compatibility",
            f"-Dcoco.api.compatibility.baseline-directory={workspace / 'baseline'}",
            f"-Dcoco.api.compatibility.report-run-id={run_id}",
            "-pl",
            projects,
            "clean",
            "verify",
        ],
        process_root,
    )
    write_missing_baseline_reports(
        root,
        manifest.artifacts,
        ledger.missing_artifacts,
        candidate_version,
        run_id,
    )
    finished_at_ns = time.time_ns()
    reports = collect_fresh_reports(
        root, manifest.artifacts, run_id, started_at_ns, finished_at_ns
    )
    candidates_after = snapshot_candidates(
        root, canonical, candidate_version, phase="after"
    )
    inputs_after = input_file_records(root, input_paths)
    if inputs_before != inputs_after:
        raise RuntimeError("Trusted API gate inputs changed during Maven execution.")

    candidate_records = []
    for entry in canonical:
        coordinate = (entry.artifact, entry.module.as_posix())
        before_path, before_digest = candidates_before[coordinate]
        after_path, after_digest = candidates_after[coordinate]
        if (
            logical_absolute(before_path) != logical_absolute(after_path)
            or before_digest != after_digest
        ):
            raise RuntimeError(
                f"Candidate changed across compatibility comparison: {entry.artifact}"
            )
        candidate_records.append(
            {
                "artifact": entry.artifact,
                "module": entry.module.as_posix(),
                "path": str(logical_absolute(after_path)),
                "version": candidate_version,
                "sha256Before": before_digest,
                "sha256After": after_digest,
            }
        )

    attestation = {
        "schemaVersion": ATTESTATION_SCHEMA_VERSION,
        "runId": run_id,
        "repositoryRoot": str(logical_absolute(root)),
        "workspace": str(logical_absolute(workspace)),
        "protectedSha": protected_sha,
        "expectedCandidateVersion": candidate_version,
        "policyBundleSha256": policy_digest,
        "startedAtNs": started_at_ns,
        "finishedAtNs": finished_at_ns,
        "inputs": {
            "sha256": input_records_sha256(inputs_after),
            "files": inputs_after,
        },
        "baseline": {
            "ledgerSha256": sha256_file(ledger_path, "Baseline ledger"),
            "origin": BASELINE_ORIGIN,
            "version": BASELINE_VERSION,
            "signingFingerprint": BASELINE_SIGNING_FINGERPRINT,
            "verifiedMissingArtifacts": list(ledger.missing_artifacts),
            "artifacts": baseline_records,
        },
        "candidates": candidate_records,
        "reports": reports,
    }
    attestation_path = workspace / "public-api-attestation.json"
    write_attestation(attestation_path, attestation)
    findings, missing = validate_attested_reports(
        root,
        allowlist_path,
        manifest_path,
        ledger_path,
        signing_key_path,
        attestation_path,
        candidate_version,
        protected_sha,
    )
    if missing or findings:
        rendered = "\n".join(finding.display() for finding in findings)
        raise RuntimeError(
            f"Public API compatibility findings remain; missing={missing}\n{rendered}"
        )
    return attestation_path


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=repository_root())
    parser.add_argument("--candidate-version", required=True)
    parser.add_argument("--protected-sha", default=os.environ.get("GITHUB_SHA"))
    parser.add_argument("--jdk-home", type=Path, default=None)
    parser.add_argument("--work-directory", type=Path, default=None)
    parser.add_argument("--manifest", type=Path, default=default_manifest_path())
    parser.add_argument(
        "--allowlist", type=Path, default=Path(__file__).with_name("allowlist.json")
    )
    parser.add_argument(
        "--baseline-ledger", type=Path, default=default_baseline_ledger_path()
    )
    parser.add_argument("--signing-key", type=Path, default=default_signing_key_path())
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        if args.protected_sha is None:
            raise RuntimeError("--protected-sha is required outside GitHub Actions.")
        if args.jdk_home is not None:
            configure_jdk(args.jdk_home)
        executable("java")
        executable("mvn")
        executable("gpg")
        if args.work_directory is None:
            with tempfile.TemporaryDirectory(
                prefix="coco-public-api-gate-"
            ) as directory:
                attestation = execute_gate(
                    logical_absolute(args.root),
                    Path(directory),
                    args.candidate_version,
                    logical_absolute(args.manifest),
                    logical_absolute(args.allowlist),
                    logical_absolute(args.baseline_ledger),
                    logical_absolute(args.signing_key),
                    args.protected_sha,
                )
                print(f"Public API compatibility gate passed: {attestation}")
        else:
            workspace = logical_absolute(args.work_directory)
            if entry_exists(workspace):
                raise RuntimeError(
                    f"Trusted work directory must not already exist: {workspace}"
                )
            mkdir(workspace, parents=True)
            attestation = execute_gate(
                logical_absolute(args.root),
                workspace,
                args.candidate_version,
                logical_absolute(args.manifest),
                logical_absolute(args.allowlist),
                logical_absolute(args.baseline_ledger),
                logical_absolute(args.signing_key),
                args.protected_sha,
            )
            print(f"Public API compatibility gate passed: {attestation}")
    except (OSError, RuntimeError, ValueError) as exc:
        print(f"PUBLIC API TRUSTED RUNNER ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
