#!/usr/bin/env python3
"""Run real japicmp checks for four repaired public API regressions."""

from __future__ import annotations

import argparse
import hashlib
import io
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
import uuid
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import Callable
from xml.etree import ElementTree

from path_io import (
    atomic_create_bytes,
    atomic_create_text,
    binary_writer,
    entry_exists,
    file_snapshot,
    glob_files,
    logical_absolute,
    mkdir,
    read_bytes,
    read_text,
    rmtree,
    short_process_cwd,
    unlink,
)
from check_public_api_compatibility import load_manifest


JAPICMP_VERSION = "0.23.1"
BASELINE_OPENAPI_COORDINATE = "io.github.patton174:coco-feature-openapi:2.0.1"
MAVEN_DEPENDENCY_PLUGIN_VERSION = "3.8.1"
REPORT_RELATIVE_PATH = Path("target/japicmp/public-api-compatibility.xml")
STATES = ("broken", "repaired", "current")
OPENAPI_VECTOR_NAME = "openapi-autoconfiguration-method"
OPENAPI_AUTO_CONFIGURATION = (
    "io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration"
)
OPENAPI_METADATA_PROVIDER = (
    "io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider"
)
OPENAPI_FACTORY_BEAN = (
    "io.github.coco.feature.openapi.springdoc.CocoSpringDocOpenApiCustomizerFactoryBean"
)
OPENAPI_METHOD_DESCRIPTOR = (
    "(Lio/github/coco/feature/openapi/core/CocoOpenApiMetadataProvider;)"
    "Lio/github/coco/feature/openapi/springdoc/"
    "CocoSpringDocOpenApiCustomizerFactoryBean;"
)
OPENAPI_PROBE_SUCCESS = "OPENAPI_BINARY_PROBE_OK"
LEGACY_DESCRIPTOR_PROBE_SUCCESS = "LEGACY_2_0_1_DESCRIPTOR_PROBE_OK"
LEGACY_BASELINE_COORDINATES = (
    "io.github.patton174:coco-config:2.0.1",
    "io.github.patton174:coco-feature-web:2.0.1",
    "io.github.patton174:coco-feature-tenant:2.0.1",
)


@dataclass(frozen=True)
class LegacyDescriptor:
    name: str
    baseline_artifact: str
    candidate_module: str
    candidate_artifact: str
    class_name: str
    descriptor: str
    japicmp_descriptor: str


LEGACY_DESCRIPTORS = (
    LegacyDescriptor(
        "config-feature-plan",
        "coco-config",
        "coco-spring/coco-spring-boot-autoconfigure",
        "coco-spring-boot-autoconfigure",
        "io.github.coco.config.CocoConfigAutoConfiguration",
        "(Lio/github/coco/config/CocoProperties;"
        "Lorg/springframework/beans/factory/ObjectProvider;"
        "Lorg/springframework/beans/factory/config/ConfigurableListableBeanFactory;)"
        "Lio/github/coco/feature/model/CocoFeaturePlan;",
        "cocoFeaturePlan(io.github.coco.config.CocoProperties,"
        "org.springframework.beans.factory.ObjectProvider,"
        "org.springframework.beans.factory.config.ConfigurableListableBeanFactory)",
    ),
    LegacyDescriptor(
        "web-request-parameter-resolver",
        "coco-feature-web",
        "coco-features/coco-web",
        "coco-web",
        "io.github.coco.feature.web.CocoWebContextAutoConfiguration",
        "(Lio/github/coco/feature/web/CocoWebProperties;"
        "Lio/github/coco/feature/web/context/payload/CocoPayloadParameterResolver;)"
        "Lio/github/coco/feature/web/context/CocoRequestParameterResolver;",
        "cocoRequestParameterResolver(io.github.coco.feature.web.CocoWebProperties,"
        "io.github.coco.feature.web.context.payload.CocoPayloadParameterResolver)",
    ),
    LegacyDescriptor(
        "web-trace-filter-registration",
        "coco-feature-web",
        "coco-features/coco-web",
        "coco-web",
        "io.github.coco.feature.web.CocoWebTraceAutoConfiguration",
        "(Lio/github/coco/feature/web/CocoWebProperties;"
        "Lorg/springframework/beans/factory/ObjectProvider;"
        "Lio/github/coco/feature/web/context/CocoWebRequestContextResolver;"
        "Lio/github/coco/feature/web/trace/CocoTraceIdValidator;)"
        "Lorg/springframework/boot/web/servlet/FilterRegistrationBean;",
        "cocoTraceFilterRegistration(io.github.coco.feature.web.CocoWebProperties,"
        "org.springframework.beans.factory.ObjectProvider,"
        "io.github.coco.feature.web.context.CocoWebRequestContextResolver,"
        "io.github.coco.feature.web.trace.CocoTraceIdValidator)",
    ),
    LegacyDescriptor(
        "tenant-before-prepare",
        "coco-feature-tenant",
        "coco-features/coco-tenant",
        "coco-tenant",
        "io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreGuard",
        "(Lorg/apache/ibatis/executor/statement/StatementHandler;"
        "Ljava/sql/Connection;Ljava/lang/Integer;)V",
        "beforePrepare(org.apache.ibatis.executor.statement.StatementHandler,"
        "java.sql.Connection,java.lang.Integer)",
    ),
)


@dataclass(frozen=True)
class CurrentCandidate:
    vector_name: str
    module: str
    artifact: str
    class_name: str
    descriptor: str


CURRENT_CANDIDATES = (
    CurrentCandidate(
        OPENAPI_VECTOR_NAME,
        "coco-features/coco-openapi",
        "coco-openapi",
        OPENAPI_AUTO_CONFIGURATION,
        OPENAPI_METHOD_DESCRIPTOR,
    ),
    CurrentCandidate(
        "observability-drop-listener-constructor",
        "coco-features/coco-observability",
        "coco-observability",
        "io.github.coco.observability.logging.CocoObservabilityAsyncLogDropListener",
        "(Lio/github/coco/observability/CocoLogOverflowObservation;)V",
    ),
    CurrentCandidate(
        "rate-limit-filter-constructor",
        "coco-features/coco-rate-limit",
        "coco-rate-limit",
        "io.github.coco.feature.ratelimit.CocoRateLimitFilter",
        "(Lio/github/coco/feature/ratelimit/CocoRateLimitRouteMatcher;"
        "Lio/github/coco/feature/ratelimit/CocoRateLimitKeyResolver;"
        "Lio/github/coco/feature/ratelimit/CocoRateLimitStore;"
        "Lio/github/coco/feature/web/context/CocoWebRequestContextResolver;"
        "Lio/github/coco/feature/ratelimit/CocoRateLimitResponseWriter;)V",
    ),
    CurrentCandidate(
        "i18n-locale-resolver-method",
        "coco-features/coco-web",
        "coco-web",
        "io.github.coco.feature.web.CocoWebI18nAutoConfiguration",
        "(Lio/github/coco/CocoCommonProperties;)"
        "Lio/github/coco/i18n/CocoLocaleResolver;",
    ),
)


@dataclass(frozen=True, order=True)
class Finding:
    class_name: str
    member_kind: str
    member_descriptor: str
    status: str

    def display(self) -> str:
        return (
            f"{self.class_name}: {self.member_kind} {self.member_descriptor} "
            f"status={self.status}"
        )


def source_set(
    target_path: str, target_source: str, stubs: dict[str, str]
) -> dict[str, str]:
    return {target_path: target_source, **stubs}


OPENAPI_PATH = "io/github/coco/feature/openapi/CocoOpenApiAutoConfiguration.java"
OPENAPI_STUBS = {
    "io/github/coco/feature/openapi/core/CocoOpenApiMetadataProvider.java": """
package io.github.coco.feature.openapi.core;
public interface CocoOpenApiMetadataProvider { }
""",
    "io/github/coco/feature/openapi/CocoOpenApiProperties.java": """
package io.github.coco.feature.openapi;
public final class CocoOpenApiProperties { }
""",
    "io/github/coco/feature/openapi/springdoc/CocoSpringDocOpenApiCustomizerFactoryBean.java": """
package io.github.coco.feature.openapi.springdoc;
public final class CocoSpringDocOpenApiCustomizerFactoryBean { }
""",
}

OBSERVABILITY_PATH = (
    "io/github/coco/observability/logging/CocoObservabilityAsyncLogDropListener.java"
)
OBSERVABILITY_STUBS = {
    "io/github/coco/observability/CocoLogOverflowObservation.java": """
package io.github.coco.observability;
public final class CocoLogOverflowObservation { }
""",
    "io/github/coco/logging/core/CocoAsyncLogDropListener.java": """
package io.github.coco.logging.core;
public interface CocoAsyncLogDropListener { }
""",
    "org/springframework/beans/factory/ObjectProvider.java": """
package org.springframework.beans.factory;
public interface ObjectProvider<T> { }
""",
}

RATE_LIMIT_PATH = "io/github/coco/feature/ratelimit/CocoRateLimitFilter.java"
RATE_LIMIT_STUBS = {
    "io/github/coco/feature/ratelimit/CocoRateLimitRouteMatcher.java": """
package io.github.coco.feature.ratelimit;
public interface CocoRateLimitRouteMatcher { }
""",
    "io/github/coco/feature/ratelimit/CocoRateLimitKeyResolver.java": """
package io.github.coco.feature.ratelimit;
public interface CocoRateLimitKeyResolver { }
""",
    "io/github/coco/feature/ratelimit/CocoRateLimitStore.java": """
package io.github.coco.feature.ratelimit;
public interface CocoRateLimitStore { }
""",
    "io/github/coco/feature/ratelimit/CocoRateLimitResponseWriter.java": """
package io.github.coco.feature.ratelimit;
public interface CocoRateLimitResponseWriter { }
""",
    "io/github/coco/feature/ratelimit/CocoRateLimitRequestHandler.java": """
package io.github.coco.feature.ratelimit;
public final class CocoRateLimitRequestHandler { }
""",
    "io/github/coco/feature/web/context/CocoWebRequestContextResolver.java": """
package io.github.coco.feature.web.context;
public interface CocoWebRequestContextResolver { }
""",
}

I18N_PATH = "io/github/coco/feature/web/CocoWebI18nAutoConfiguration.java"
I18N_STUBS = {
    "io/github/coco/CocoCommonProperties.java": """
package io.github.coco;
public final class CocoCommonProperties { }
""",
    "io/github/coco/i18n/CocoLocaleFallbackPolicy.java": """
package io.github.coco.i18n;
public interface CocoLocaleFallbackPolicy { }
""",
    "io/github/coco/i18n/CocoLocaleResolver.java": """
package io.github.coco.i18n;
public interface CocoLocaleResolver { }
""",
    "org/springframework/beans/factory/ObjectProvider.java": """
package org.springframework.beans.factory;
public interface ObjectProvider<T> { }
""",
}


VECTORS = (
    {
        "name": "openapi-autoconfiguration-method",
        "target_class": OPENAPI_AUTO_CONFIGURATION,
        "expected_broken": (
            Finding(
                "io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration",
                "method",
                "cocoSpringDocOpenApiCustomizer("
                "io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider)",
                "REMOVED",
            ),
        ),
        "sources": {
            "old": source_set(
                OPENAPI_PATH,
                """
package io.github.coco.feature.openapi;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;
import io.github.coco.feature.openapi.springdoc.CocoSpringDocOpenApiCustomizerFactoryBean;
public final class CocoOpenApiAutoConfiguration {
    public CocoSpringDocOpenApiCustomizerFactoryBean cocoSpringDocOpenApiCustomizer(
            CocoOpenApiMetadataProvider metadataProvider) { return null; }
}
""",
                OPENAPI_STUBS,
            ),
            "broken": source_set(
                OPENAPI_PATH,
                """
package io.github.coco.feature.openapi;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;
import io.github.coco.feature.openapi.springdoc.CocoSpringDocOpenApiCustomizerFactoryBean;
public final class CocoOpenApiAutoConfiguration {
    public CocoSpringDocOpenApiCustomizerFactoryBean cocoSpringDocOpenApiCustomizer(
            CocoOpenApiMetadataProvider metadataProvider,
            CocoOpenApiProperties properties) { return null; }
}
""",
                OPENAPI_STUBS,
            ),
            "repaired": source_set(
                OPENAPI_PATH,
                """
package io.github.coco.feature.openapi;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;
import io.github.coco.feature.openapi.springdoc.CocoSpringDocOpenApiCustomizerFactoryBean;
public final class CocoOpenApiAutoConfiguration {
    public CocoSpringDocOpenApiCustomizerFactoryBean cocoSpringDocOpenApiCustomizer(
            CocoOpenApiMetadataProvider metadataProvider,
            CocoOpenApiProperties properties) { return null; }
    public CocoSpringDocOpenApiCustomizerFactoryBean cocoSpringDocOpenApiCustomizer(
            CocoOpenApiMetadataProvider metadataProvider) { return null; }
}
""",
                OPENAPI_STUBS,
            ),
        },
    },
    {
        "name": "observability-drop-listener-constructor",
        "target_class": (
            "io.github.coco.observability.logging.CocoObservabilityAsyncLogDropListener"
        ),
        "expected_broken": (
            Finding(
                "io.github.coco.observability.logging."
                "CocoObservabilityAsyncLogDropListener",
                "constructor",
                "<init>(io.github.coco.observability.CocoLogOverflowObservation)",
                "REMOVED",
            ),
        ),
        "sources": {
            "old": source_set(
                OBSERVABILITY_PATH,
                """
package io.github.coco.observability.logging;
import io.github.coco.observability.CocoLogOverflowObservation;
public final class CocoObservabilityAsyncLogDropListener {
    public CocoObservabilityAsyncLogDropListener(
            CocoLogOverflowObservation observation) { }
}
""",
                OBSERVABILITY_STUBS,
            ),
            "broken": source_set(
                OBSERVABILITY_PATH,
                """
package io.github.coco.observability.logging;
import io.github.coco.observability.CocoLogOverflowObservation;
import org.springframework.beans.factory.ObjectProvider;
public final class CocoObservabilityAsyncLogDropListener {
    public CocoObservabilityAsyncLogDropListener(
            ObjectProvider<CocoLogOverflowObservation> observationProvider) { }
}
""",
                OBSERVABILITY_STUBS,
            ),
            "repaired": source_set(
                OBSERVABILITY_PATH,
                """
package io.github.coco.observability.logging;
import io.github.coco.observability.CocoLogOverflowObservation;
import org.springframework.beans.factory.ObjectProvider;
public final class CocoObservabilityAsyncLogDropListener {
    public CocoObservabilityAsyncLogDropListener(
            CocoLogOverflowObservation observation) { }
    public CocoObservabilityAsyncLogDropListener(
            ObjectProvider<CocoLogOverflowObservation> observationProvider) { }
}
""",
                OBSERVABILITY_STUBS,
            ),
        },
    },
    {
        "name": "rate-limit-filter-constructor",
        "target_class": "io.github.coco.feature.ratelimit.CocoRateLimitFilter",
        "expected_broken": (
            Finding(
                "io.github.coco.feature.ratelimit.CocoRateLimitFilter",
                "constructor",
                "<init>(io.github.coco.feature.ratelimit.CocoRateLimitRouteMatcher,"
                "io.github.coco.feature.ratelimit.CocoRateLimitKeyResolver,"
                "io.github.coco.feature.ratelimit.CocoRateLimitStore,"
                "io.github.coco.feature.web.context.CocoWebRequestContextResolver,"
                "io.github.coco.feature.ratelimit.CocoRateLimitResponseWriter)",
                "REMOVED",
            ),
        ),
        "sources": {
            "old": source_set(
                RATE_LIMIT_PATH,
                """
package io.github.coco.feature.ratelimit;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
public final class CocoRateLimitFilter {
    public CocoRateLimitFilter(CocoRateLimitRouteMatcher routeMatcher,
            CocoRateLimitKeyResolver keyResolver, CocoRateLimitStore store,
            CocoWebRequestContextResolver requestContextResolver,
            CocoRateLimitResponseWriter responseWriter) { }
}
""",
                RATE_LIMIT_STUBS,
            ),
            "broken": source_set(
                RATE_LIMIT_PATH,
                """
package io.github.coco.feature.ratelimit;
public final class CocoRateLimitFilter {
    public CocoRateLimitFilter(CocoRateLimitRouteMatcher routeMatcher,
            CocoRateLimitRequestHandler requestHandler) { }
}
""",
                RATE_LIMIT_STUBS,
            ),
            "repaired": source_set(
                RATE_LIMIT_PATH,
                """
package io.github.coco.feature.ratelimit;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
public final class CocoRateLimitFilter {
    public CocoRateLimitFilter(CocoRateLimitRouteMatcher routeMatcher,
            CocoRateLimitRequestHandler requestHandler) { }
    public CocoRateLimitFilter(CocoRateLimitRouteMatcher routeMatcher,
            CocoRateLimitKeyResolver keyResolver, CocoRateLimitStore store,
            CocoWebRequestContextResolver requestContextResolver,
            CocoRateLimitResponseWriter responseWriter) { }
}
""",
                RATE_LIMIT_STUBS,
            ),
        },
    },
    {
        "name": "i18n-locale-resolver-method",
        "target_class": "io.github.coco.feature.web.CocoWebI18nAutoConfiguration",
        "expected_broken": (
            Finding(
                "io.github.coco.feature.web.CocoWebI18nAutoConfiguration",
                "method",
                "cocoWebLocaleResolver(io.github.coco.CocoCommonProperties)",
                "REMOVED",
            ),
        ),
        "sources": {
            "old": source_set(
                I18N_PATH,
                """
package io.github.coco.feature.web;
import io.github.coco.CocoCommonProperties;
import io.github.coco.i18n.CocoLocaleResolver;
public final class CocoWebI18nAutoConfiguration {
    public CocoLocaleResolver cocoWebLocaleResolver(
            CocoCommonProperties properties) { return null; }
}
""",
                I18N_STUBS,
            ),
            "broken": source_set(
                I18N_PATH,
                """
package io.github.coco.feature.web;
import io.github.coco.CocoCommonProperties;
import io.github.coco.i18n.CocoLocaleFallbackPolicy;
import io.github.coco.i18n.CocoLocaleResolver;
public final class CocoWebI18nAutoConfiguration {
    public CocoLocaleResolver cocoWebLocaleResolver(
            CocoCommonProperties properties,
            CocoLocaleFallbackPolicy fallbackPolicy) { return null; }
}
""",
                I18N_STUBS,
            ),
            "repaired": source_set(
                I18N_PATH,
                """
package io.github.coco.feature.web;
import io.github.coco.CocoCommonProperties;
import io.github.coco.i18n.CocoLocaleFallbackPolicy;
import io.github.coco.i18n.CocoLocaleResolver;
public final class CocoWebI18nAutoConfiguration {
    public CocoLocaleResolver cocoWebLocaleResolver(
            CocoCommonProperties properties) { return null; }
    public CocoLocaleResolver cocoWebLocaleResolver(
            CocoCommonProperties properties,
            CocoLocaleFallbackPolicy fallbackPolicy) { return null; }
}
""",
                I18N_STUBS,
            ),
        },
    },
)


def run(arguments: list[str], cwd: Path) -> None:
    process_cwd = external_process_cwd(cwd)
    completed = subprocess.run(
        arguments,
        cwd=process_cwd,
        check=False,
        text=True,
        encoding="utf-8",
        errors="replace",
        capture_output=True,
    )
    if completed.returncode:
        details = "\n".join(
            output.strip()
            for output in (completed.stdout, completed.stderr)
            if output and output.strip()
        )
        suffix = f"\n{details}" if details else ""
        raise RuntimeError(
            f"Command failed with exit code {completed.returncode}: {arguments}{suffix}"
        )


def run_capture(arguments: list[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    process_cwd = external_process_cwd(cwd)
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


def external_process_cwd(preferred: Path) -> Path:
    try:
        return short_process_cwd(preferred, "External process working directory")
    except ValueError as exc:
        if os.name != "nt" or "is not a short logical Windows path" not in str(exc):
            raise
        return short_process_cwd(
            repository_root(), "Repository process working directory"
        )


def short_process_temp_root() -> Path:
    candidates = [Path(tempfile.gettempdir())]
    if os.name == "nt" and os.environ.get("LOCALAPPDATA"):
        candidates.append(Path(os.environ["LOCALAPPDATA"]) / "Temp")
    for candidate in candidates:
        try:
            validated = short_process_cwd(candidate, "Process staging root")
        except ValueError:
            continue
        root = validated / "coco-public-api-process"
        mkdir(root, parents=True, exist_ok=True)
        return short_process_cwd(root, "Process staging root")
    raise RuntimeError(
        "No validated short process staging root is available for fixture tools."
    )


@contextmanager
def short_process_stage(label: str):
    root = short_process_temp_root()
    stage = root / f"{label}-{uuid.uuid4().hex}"
    mkdir(stage)
    try:
        yield short_process_cwd(stage, "Fixture process staging directory")
    finally:
        if entry_exists(stage):
            rmtree(stage, "Fixture process staging directory")


def write_sources(directory: Path, sources: dict[str, str]) -> None:
    for relative_path, source in sources.items():
        path = directory / relative_path
        mkdir(path.parent, parents=True, exist_ok=True)
        atomic_create_text(path, source.strip() + "\n")


def compile_jar(work: Path, name: str, sources: dict[str, str]) -> Path:
    jar_path = work / f"{name}.jar"
    mkdir(work, parents=True, exist_ok=True)
    with short_process_stage(f"compile-{name}") as stage:
        source_root = stage / "sources"
        classes = stage / "classes"
        write_sources(source_root, sources)
        mkdir(classes)
        source_files = sorted(
            str(path)
            for path in glob_files(source_root, "**/*.java", "Fixture sources")
        )
        if not source_files:
            raise RuntimeError(f"Fixture source set is empty: {name}")
        run(
            [
                executable("javac"),
                "--release",
                "17",
                "-d",
                str(classes),
                *source_files,
            ],
            stage,
        )
        class_files = sorted(glob_files(classes, "**/*.class", "Fixture classes"))
        if not class_files:
            raise RuntimeError(
                f"javac produced no class files for fixture source set: {name}"
            )
        staged_jar = stage / f"{name}.jar"
        with binary_writer(staged_jar, exclusive=True) as output:
            with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
                for class_file in class_files:
                    archive.writestr(
                        class_file.relative_to(classes).as_posix(),
                        read_bytes(class_file, "Compiled fixture class"),
                    )
        staged_bytes = read_bytes(staged_jar, "Staged fixture JAR")
        atomic_create_bytes(jar_path, staged_bytes)
        copied_bytes = read_bytes(jar_path, "Copied fixture JAR")
        if (
            hashlib.sha256(copied_bytes).digest()
            != hashlib.sha256(staged_bytes).digest()
        ):
            raise RuntimeError(f"Fixture JAR digest changed while copying: {jar_path}")
    return jar_path


def repository_root() -> Path:
    return logical_absolute(Path(__file__)).parents[3]


def verify_current_candidate(
    specification: CurrentCandidate,
    candidate_jar: Path,
    process_runner: Callable[
        [list[str], Path], subprocess.CompletedProcess[str]
    ] = run_capture,
) -> None:
    try:
        candidate_bytes = read_bytes(candidate_jar, "Reactor candidate JAR")
    except ValueError as exc:
        raise RuntimeError(
            f"{specification.vector_name} reactor candidate must be a real JAR: "
            f"{candidate_jar}"
        ) from exc
    class_entry = specification.class_name.replace(".", "/") + ".class"
    try:
        with zipfile.ZipFile(io.BytesIO(candidate_bytes)) as archive:
            if class_entry not in archive.namelist():
                raise RuntimeError(
                    f"{specification.vector_name} reactor candidate does not contain "
                    f"{specification.class_name}: {candidate_jar}"
                )
    except zipfile.BadZipFile as exc:
        raise RuntimeError(
            f"{specification.vector_name} reactor candidate is not a readable JAR: "
            f"{candidate_jar}"
        ) from exc

    arguments = [
        executable("javap"),
        "-classpath",
        str(candidate_jar),
        "-s",
        "-p",
        specification.class_name,
    ]
    completed = process_runner(arguments, candidate_jar.parent)
    output = "\n".join((completed.stdout or "", completed.stderr or ""))
    if completed.returncode:
        raise RuntimeError(
            f"javap could not inspect {specification.vector_name} reactor candidate "
            f"(exit {completed.returncode}): {candidate_jar}\n{output.strip()}"
        )
    if specification.descriptor not in output:
        raise RuntimeError(
            f"{specification.vector_name} reactor candidate is missing the required "
            f"binary descriptor {specification.descriptor}: {candidate_jar}"
        )


def verify_openapi_descriptor(
    candidate_jar: Path,
    process_runner: Callable[
        [list[str], Path], subprocess.CompletedProcess[str]
    ] = run_capture,
) -> None:
    verify_current_candidate(CURRENT_CANDIDATES[0], candidate_jar, process_runner)


def find_reactor_current_candidates(
    root: Path, candidate_version: str
) -> dict[str, Path]:
    candidates: dict[str, Path] = {}
    for specification in CURRENT_CANDIDATES:
        candidate = (
            root
            / specification.module
            / "target"
            / f"{specification.artifact}-{candidate_version}.jar"
        )
        verify_current_candidate(specification, candidate)
        candidates[specification.vector_name] = candidate
    return candidates


def find_reactor_openapi_candidate(root: Path, candidate_version: str) -> Path:
    return find_reactor_current_candidates(root, candidate_version)[OPENAPI_VECTOR_NAME]


def write_openapi_probe(work: Path) -> Path:
    source = work / "probe-source" / "OpenApiBinaryConsumerProbe.java"
    mkdir(source.parent, parents=True, exist_ok=True)
    atomic_create_text(
        source,
        f"""import java.lang.reflect.Proxy;
import {OPENAPI_AUTO_CONFIGURATION};
import {OPENAPI_METADATA_PROVIDER};
import {OPENAPI_FACTORY_BEAN};

public final class OpenApiBinaryConsumerProbe {{
    public static void main(String[] args) {{
        CocoOpenApiMetadataProvider provider = (CocoOpenApiMetadataProvider)
                Proxy.newProxyInstance(
                        CocoOpenApiMetadataProvider.class.getClassLoader(),
                        new Class<?>[] {{CocoOpenApiMetadataProvider.class}},
                        (proxy, method, arguments) -> null);
        CocoOpenApiAutoConfiguration configuration =
                new CocoOpenApiAutoConfiguration();
        CocoSpringDocOpenApiCustomizerFactoryBean result =
                configuration.cocoSpringDocOpenApiCustomizer(provider);
        System.out.println("{OPENAPI_PROBE_SUCCESS}");
    }}
}}
""",
    )
    return source


def prepare_openapi_probe(work: Path) -> tuple[Path, tuple[Path, ...]]:
    probe_root = work / "openapi-binary-probe"
    mkdir(probe_root, parents=True, exist_ok=True)
    pom = probe_root / "pom.xml"
    group_id, artifact_id, version = BASELINE_OPENAPI_COORDINATE.split(":")
    atomic_create_text(
        pom,
        f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.coco.compatibility-fixture</groupId>
  <artifactId>openapi-binary-consumer-probe</artifactId>
  <version>1.0.0</version>
  <dependencies>
    <dependency>
      <groupId>{group_id}</groupId>
      <artifactId>{artifact_id}</artifactId>
      <version>{version}</version>
    </dependency>
  </dependencies>
</project>
""",
    )
    classpath_file = probe_root / "baseline-classpath.txt"
    run(
        [
            executable("mvn"),
            "-B",
            "-ntp",
            "-f",
            str(pom),
            f"org.apache.maven.plugins:maven-dependency-plugin:"
            f"{MAVEN_DEPENDENCY_PLUGIN_VERSION}:build-classpath",
            f"-Dmdep.outputFile={classpath_file}",
        ],
        probe_root,
    )
    try:
        classpath_text = read_text(classpath_file, "OpenAPI baseline classpath").strip()
    except ValueError as exc:
        raise RuntimeError(
            "Maven did not produce a classpath for "
            f"{BASELINE_OPENAPI_COORDINATE}: {classpath_file}"
        ) from exc
    if not classpath_text:
        raise RuntimeError(
            "Maven produced an empty classpath for "
            f"{BASELINE_OPENAPI_COORDINATE}: {classpath_file}"
        )
    baseline_classpath = tuple(
        Path(item) for item in classpath_text.split(os.pathsep) if item
    )
    baseline_jar_name = f"{artifact_id}-{version}.jar"
    baseline_jars = [
        path for path in baseline_classpath if path.name == baseline_jar_name
    ]
    if len(baseline_jars) != 1:
        raise RuntimeError(
            "Expected Maven to resolve exactly one baseline OpenAPI JAR for "
            f"{BASELINE_OPENAPI_COORDINATE}, found: {baseline_jars}"
        )

    source = write_openapi_probe(probe_root)
    classes = probe_root / "classes"
    mkdir(classes)
    run(
        [
            executable("javac"),
            "--release",
            "17",
            "-classpath",
            os.pathsep.join(str(path) for path in baseline_classpath),
            "-d",
            str(classes),
            str(source),
        ],
        probe_root,
    )
    if not entry_exists(classes / "OpenApiBinaryConsumerProbe.class"):
        raise RuntimeError("javac did not produce OpenApiBinaryConsumerProbe.class")
    runtime_dependencies = tuple(
        path for path in baseline_classpath if path not in baseline_jars
    )
    return classes, runtime_dependencies


def run_openapi_probe(
    candidate_jar: Path,
    probe_classes: Path,
    runtime_dependencies: tuple[Path, ...],
    expect_success: bool,
    process_runner: Callable[
        [list[str], Path], subprocess.CompletedProcess[str]
    ] = run_capture,
) -> None:
    classpath = (candidate_jar, probe_classes, *runtime_dependencies)
    completed = process_runner(
        [
            executable("java"),
            "-classpath",
            os.pathsep.join(str(path) for path in classpath),
            "OpenApiBinaryConsumerProbe",
        ],
        probe_classes.parent,
    )
    stdout = (completed.stdout or "").strip()
    stderr = (completed.stderr or "").strip()
    if expect_success:
        if completed.returncode != 0 or stdout != OPENAPI_PROBE_SUCCESS or stderr:
            raise RuntimeError(
                "OpenAPI binary consumer probe did not pass exactly: "
                f"exit={completed.returncode}, stdout={stdout!r}, stderr={stderr!r}"
            )
        return
    expected_linkage = re.fullmatch(
        rf'Exception in thread "main" java\.lang\.NoSuchMethodError: '
        rf"'{re.escape(OPENAPI_FACTORY_BEAN)} "
        rf"{re.escape(OPENAPI_AUTO_CONFIGURATION)}\."
        rf"cocoSpringDocOpenApiCustomizer\("
        rf"{re.escape(OPENAPI_METADATA_PROVIDER)}\)'"
        rf"\r?\n\s+at OpenApiBinaryConsumerProbe\.main\("
        rf"OpenApiBinaryConsumerProbe\.java:\d+\)",
        stderr,
    )
    if completed.returncode == 0 or not expected_linkage or stdout:
        raise RuntimeError(
            "Broken OpenAPI candidate did not fail with the expected method linkage "
            f"error: exit={completed.returncode}, stdout={stdout!r}, stderr={stderr!r}"
        )


def verify_legacy_descriptor(
    descriptor: LegacyDescriptor,
    candidate_jar: Path,
    process_runner: Callable[
        [list[str], Path], subprocess.CompletedProcess[str]
    ] = run_capture,
) -> None:
    try:
        candidate_bytes = read_bytes(candidate_jar, "Legacy descriptor candidate JAR")
    except ValueError as exc:
        raise RuntimeError(
            f"{descriptor.name} candidate JAR is missing: {candidate_jar}"
        ) from exc
    class_entry = descriptor.class_name.replace(".", "/") + ".class"
    try:
        with zipfile.ZipFile(io.BytesIO(candidate_bytes)) as archive:
            if class_entry not in archive.namelist():
                raise RuntimeError(
                    f"{descriptor.name} candidate does not contain "
                    f"{descriptor.class_name}: {candidate_jar}"
                )
    except zipfile.BadZipFile as exc:
        raise RuntimeError(
            f"{descriptor.name} candidate is not a readable JAR: {candidate_jar}"
        ) from exc

    completed = process_runner(
        [
            executable("javap"),
            "-classpath",
            str(candidate_jar),
            "-s",
            "-p",
            descriptor.class_name,
        ],
        candidate_jar.parent,
    )
    output = "\n".join((completed.stdout or "", completed.stderr or ""))
    if completed.returncode:
        raise RuntimeError(
            f"javap could not inspect {descriptor.name} "
            f"(exit {completed.returncode}): {candidate_jar}\n{output.strip()}"
        )
    if descriptor.descriptor not in output:
        raise RuntimeError(
            f"{descriptor.name} candidate is missing the required binary descriptor "
            f"{descriptor.descriptor}: {candidate_jar}"
        )


def find_legacy_descriptor_candidates(
    root: Path, candidate_version: str
) -> tuple[Path, ...]:
    candidates: dict[tuple[str, str], Path] = {}
    ordered: list[Path] = []
    for descriptor in LEGACY_DESCRIPTORS:
        key = (descriptor.candidate_module, descriptor.candidate_artifact)
        candidate = candidates.get(key)
        if candidate is None:
            candidate = (
                root
                / descriptor.candidate_module
                / "target"
                / f"{descriptor.candidate_artifact}-{candidate_version}.jar"
            )
            try:
                read_bytes(candidate, "Legacy descriptor candidate JAR")
            except ValueError as exc:
                raise RuntimeError(
                    f"Expected exact real {descriptor.candidate_artifact} reactor "
                    f"candidate JAR: {candidate}"
                ) from exc
            candidates[key] = candidate
            ordered.append(candidate)
        verify_legacy_descriptor(descriptor, candidate)
    return tuple(ordered)


def published_coco_artifact(path: Path) -> str | None:
    if len(path.parents) < 5 or path.parent.name != "2.0.1":
        return None
    artifact = path.parent.parent.name
    if (
        path.name != f"{artifact}-2.0.1.jar"
        or path.parents[2].name != "patton174"
        or path.parents[3].name != "github"
        or path.parents[4].name != "io"
    ):
        return None
    return artifact


def find_legacy_runtime_candidates(
    root: Path, candidate_version: str, published_artifacts: set[str]
) -> tuple[Path, ...]:
    profile_path = Path(__file__).with_name("public-api-profile.json")
    profile = load_manifest(profile_path)
    entries = {entry.artifact: entry for entry in profile.artifacts}
    candidates: dict[tuple[str, str], Path] = {}
    for artifact in sorted(published_artifacts):
        entry = entries.get(artifact)
        if entry is None:
            raise RuntimeError(
                f"Published runtime artifact is absent from the API profile: {artifact}"
            )
        coordinate = (entry.candidate_module.as_posix(), entry.candidate_artifact)
        path = (
            root
            / entry.candidate_module.as_posix()
            / "target"
            / f"{entry.candidate_artifact}-{candidate_version}.jar"
        )
        try:
            read_bytes(path, "Runtime closure candidate JAR")
        except ValueError as exc:
            raise RuntimeError(
                f"Expected exact real runtime closure candidate JAR for {artifact}: "
                f"{path}"
            ) from exc
        candidates[coordinate] = path
    return tuple(candidates[key] for key in sorted(candidates))


def write_legacy_descriptor_probe(work: Path) -> Path:
    source = work / "probe-source" / "LegacyDescriptorConsumerProbe.java"
    mkdir(source.parent, parents=True, exist_ok=True)
    atomic_create_text(
        source,
        f"""import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import io.github.coco.api.CocoConfigurer;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.config.CocoConfigAutoConfiguration;
import io.github.coco.config.CocoFeatureProperties;
import io.github.coco.config.CocoProperties;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreGuard;
import io.github.coco.feature.tenant.sql.CocoTenantSqlProperties;
import io.github.coco.feature.web.CocoWebContextAutoConfiguration;
import io.github.coco.feature.web.CocoWebProperties;
import io.github.coco.feature.web.CocoWebTraceAutoConfiguration;
import io.github.coco.feature.web.context.CocoRequestParameterResolver;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.payload.CocoPayloadParameterResolver;
import io.github.coco.feature.web.trace.CocoTraceIdValidator;
import jakarta.servlet.DispatcherType;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

public final class LegacyDescriptorConsumerProbe {{
    public static void main(String[] args) throws Throwable {{
        ClassLoader originalContextLoader = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader noManifest = new URLClassLoader(new URL[0], null)) {{
            Thread.currentThread().setContextClassLoader(noManifest);
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ObjectProvider<CocoConfigurer> configurers = beanFactory.getBeanProvider(CocoConfigurer.class);
        CocoProperties configProperties = new CocoProperties();
        CocoFeatureProperties configFeatures = configProperties.getFeatures();
        configFeatures.setDisabled(Set.of(CocoFeature.TENANT));
        require(configProperties.getFeatures() == configFeatures, "config-direct-live-features");
        CocoConfigAutoConfiguration configConfiguration = new CocoConfigAutoConfiguration();
        CocoFeaturePlan directPlan = configConfiguration.cocoFeaturePlan(
                configProperties, configurers, beanFactory);
        require(!directPlan.isEnabled(CocoFeature.TENANT), "config-direct enabled="
                + directPlan.enabledFeatures() + " disabled=" + directPlan.disabledFeatures()
                + " selection=" + configProperties.getFeatures().toSelection());

        CocoWebContextAutoConfiguration contextConfiguration = new CocoWebContextAutoConfiguration();
        CocoWebProperties webProperties = new CocoWebProperties();
        CocoRequestParameterResolver directRequestResolver = contextConfiguration
                .cocoRequestParameterResolver(webProperties, null);
        require(directRequestResolver != null, "context-direct");

        CocoWebRequestContextResolver requestContextResolver = (CocoWebRequestContextResolver)
                java.lang.reflect.Proxy.newProxyInstance(
                        CocoWebRequestContextResolver.class.getClassLoader(),
                        new Class<?>[] {{ CocoWebRequestContextResolver.class }},
                        (proxy, method, arguments) -> null);
        CocoWebTraceAutoConfiguration traceConfiguration = new CocoWebTraceAutoConfiguration();
        FilterRegistrationBean<?> directRegistration = traceConfiguration.cocoTraceFilterRegistration(
                webProperties,
                beanFactory.getBeanProvider(io.github.coco.logging.access.CocoAccessLogRecorder.class),
                requestContextResolver,
                null);
        assertPublishedRegistration(directRegistration, "trace-direct");

        CocoTenantSqlProperties tenantProperties = new CocoTenantSqlProperties();
        tenantProperties.getInterceptorIgnore().setBlockUnlisted(false);
        AtomicInteger eventCount = new AtomicInteger();
        CocoTenantInterceptorIgnoreGuard tenantGuard = new CocoTenantInterceptorIgnoreGuard(
                tenantProperties, event -> eventCount.incrementAndGet());
        MappedStatement update = mappedStatement();
        StatementHandler statementHandler = new RoutingStatementHandler(
                null, update, new Object(), RowBounds.DEFAULT, null, update.getBoundSql(new Object()));
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        try {{
            tenantGuard.beforePrepare(statementHandler, null, null);
        }} finally {{
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }}
        require(eventCount.get() == 1, "tenant-direct");

        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        CocoProperties handledProperties = new CocoProperties();
        MethodHandle featuresHandle = lookup.unreflect(CocoProperties.class.getDeclaredMethod("getFeatures"));
        CocoFeatureProperties handledFeatures = (CocoFeatureProperties) featuresHandle.invoke(handledProperties);
        handledFeatures.setDisabled(Set.of(CocoFeature.TENANT));
        require(featuresHandle.invoke(handledProperties) == handledFeatures, "config-handle-live-features");
        MethodHandle configHandle = lookup.unreflect(CocoConfigAutoConfiguration.class.getDeclaredMethod(
                "cocoFeaturePlan", CocoProperties.class, ObjectProvider.class,
                ConfigurableListableBeanFactory.class));
        CocoFeaturePlan handledPlan = (CocoFeaturePlan) configHandle.invoke(
                configConfiguration, handledProperties, configurers, beanFactory);
        require(!handledPlan.isEnabled(CocoFeature.TENANT), "config-handle enabled="
                + handledPlan.enabledFeatures() + " disabled=" + handledPlan.disabledFeatures());

        MethodHandle contextHandle = lookup.unreflect(CocoWebContextAutoConfiguration.class.getDeclaredMethod(
                "cocoRequestParameterResolver", CocoWebProperties.class,
                CocoPayloadParameterResolver.class));
        require(contextHandle.invoke(contextConfiguration, webProperties, null) != null, "context-handle");

        MethodHandle traceHandle = lookup.unreflect(CocoWebTraceAutoConfiguration.class.getDeclaredMethod(
                "cocoTraceFilterRegistration", CocoWebProperties.class, ObjectProvider.class,
                CocoWebRequestContextResolver.class, CocoTraceIdValidator.class));
        FilterRegistrationBean<?> handledRegistration = (FilterRegistrationBean<?>) traceHandle.invoke(
                traceConfiguration, webProperties,
                beanFactory.getBeanProvider(io.github.coco.logging.access.CocoAccessLogRecorder.class),
                requestContextResolver, null);
        assertPublishedRegistration(handledRegistration, "trace-handle");

        MethodHandle tenantHandle = lookup.unreflect(CocoTenantInterceptorIgnoreGuard.class.getDeclaredMethod(
                "beforePrepare", StatementHandler.class, Connection.class, Integer.class));
        eventCount.set(0);
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        try {{
            tenantHandle.invoke(tenantGuard, statementHandler, null, null);
        }} finally {{
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }}
        require(eventCount.get() == 1, "tenant-handle");
        System.out.println("{LEGACY_DESCRIPTOR_PROBE_SUCCESS}");
        }} finally {{
            Thread.currentThread().setContextClassLoader(originalContextLoader);
        }}
    }}

    private static MappedStatement mappedStatement() {{
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration();
        return new MappedStatement.Builder(configuration, "probe.Mapper.update",
                new StaticSqlSource(configuration, "update probe set value = 1"),
                SqlCommandType.UPDATE).build();
    }}

    private static void assertPublishedRegistration(FilterRegistrationBean<?> registration, String label) {{
        require(registration.getOrder() == Integer.MIN_VALUE + 1, label + "-order");
        require(registration.isAsyncSupported(), label + "-async");
        require(registration.determineDispatcherTypes().equals(EnumSet.allOf(DispatcherType.class)),
                label + "-dispatchers");
        require(registration.getUrlPatterns().isEmpty(), label + "-patterns");
        require(!registration.isMatchAfter(), label + "-match-after");
    }}

    private static void require(boolean condition, String label) {{
        if (!condition) {{
            throw new AssertionError(label);
        }}
    }}
}}
""",
    )
    return source


def verify_legacy_probe_bytecode(
    classes: Path,
    process_runner: Callable[
        [list[str], Path], subprocess.CompletedProcess[str]
    ] = run_capture,
) -> None:
    completed = process_runner(
        [
            executable("javap"),
            "-classpath",
            str(classes),
            "-c",
            "-verbose",
            "LegacyDescriptorConsumerProbe",
        ],
        classes.parent,
    )
    output = "\n".join((completed.stdout or "", completed.stderr or ""))
    if completed.returncode:
        raise RuntimeError(
            f"javap could not inspect the compiled legacy consumer (exit "
            f"{completed.returncode}):\n{output.strip()}"
        )
    for descriptor in LEGACY_DESCRIPTORS:
        method_name = descriptor.japicmp_descriptor.split("(", 1)[0]
        owner = descriptor.class_name.replace(".", "/")
        reference = f"{owner}.{method_name}:{descriptor.descriptor}"
        instruction = re.compile(
            rf"invokevirtual\s+#\d+\s+// Method {re.escape(reference)}"
        )
        if instruction.search(output) is None:
            raise RuntimeError(
                f"Legacy consumer bytecode is not bound to invokevirtual {reference}."
            )
    for reference in (
        "io/github/coco/config/CocoProperties.getFeatures:"
        "()Lio/github/coco/config/CocoFeatureProperties;",
        "io/github/coco/config/CocoFeatureProperties.setDisabled:(Ljava/util/Set;)V",
    ):
        instruction = re.compile(
            rf"invokevirtual\s+#\d+\s+// Method {re.escape(reference)}"
        )
        if instruction.search(output) is None:
            raise RuntimeError(
                f"Legacy consumer bytecode is not bound to invokevirtual {reference}."
            )


def prepare_legacy_descriptor_probe(
    work: Path,
) -> tuple[Path, tuple[Path, ...], dict[str, Path], set[str]]:
    probe_root = work / "legacy-descriptor-probe"
    mkdir(probe_root, parents=True, exist_ok=True)
    dependencies = []
    baseline_names = set()
    for coordinate in LEGACY_BASELINE_COORDINATES:
        group_id, artifact_id, version = coordinate.split(":")
        dependencies.append(
            "    <dependency>\n"
            f"      <groupId>{group_id}</groupId>\n"
            f"      <artifactId>{artifact_id}</artifactId>\n"
            f"      <version>{version}</version>\n"
            "    </dependency>"
        )
        baseline_names.add(f"{artifact_id}-{version}.jar")
    pom = probe_root / "pom.xml"
    atomic_create_text(
        pom,
        """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.coco.compatibility-fixture</groupId>
  <artifactId>legacy-descriptor-consumer-probe</artifactId>
  <version>1.0.0</version>
  <dependencies>
"""
        + "\n".join(dependencies)
        + """
    <dependency>
      <groupId>com.baomidou</groupId>
      <artifactId>mybatis-plus-jsqlparser-4.9</artifactId>
      <version>3.5.16</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-nop</artifactId>
      <version>2.0.17</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
</project>
""",
    )
    classpath_file = probe_root / "baseline-classpath.txt"
    run(
        [
            executable("mvn"),
            "-B",
            "-ntp",
            "-f",
            str(pom),
            f"org.apache.maven.plugins:maven-dependency-plugin:"
            f"{MAVEN_DEPENDENCY_PLUGIN_VERSION}:build-classpath",
            f"-Dmdep.outputFile={classpath_file}",
        ],
        probe_root,
    )
    try:
        classpath_text = read_text(
            classpath_file, "Legacy descriptor baseline classpath"
        ).strip()
    except ValueError as exc:
        raise RuntimeError(
            f"Maven did not produce the legacy descriptor classpath: {classpath_file}"
        ) from exc
    baseline_classpath = tuple(
        Path(item) for item in classpath_text.split(os.pathsep) if item
    )
    resolved_baselines = [
        path for path in baseline_classpath if path.name in baseline_names
    ]
    if {path.name for path in resolved_baselines} != baseline_names:
        raise RuntimeError(
            "Maven did not resolve every exact 2.0.1 descriptor baseline JAR: "
            f"{resolved_baselines}"
        )

    source = write_legacy_descriptor_probe(probe_root)
    classes = probe_root / "classes"
    mkdir(classes)
    run(
        [
            executable("javac"),
            "--release",
            "17",
            "-classpath",
            os.pathsep.join(str(path) for path in baseline_classpath),
            "-d",
            str(classes),
            str(source),
        ],
        probe_root,
    )
    if not entry_exists(classes / "LegacyDescriptorConsumerProbe.class"):
        raise RuntimeError("javac did not produce LegacyDescriptorConsumerProbe.class")
    verify_legacy_probe_bytecode(classes)
    published_artifacts = {
        artifact
        for path in baseline_classpath
        if (artifact := published_coco_artifact(path)) is not None
    }
    runtime_dependencies = tuple(
        path for path in baseline_classpath if published_coco_artifact(path) is None
    )
    baselines = {
        path.name.removesuffix("-2.0.1.jar"): path for path in resolved_baselines
    }
    return classes, runtime_dependencies, baselines, published_artifacts


def verify_legacy_descriptor_japicmp(
    work: Path,
    root: Path,
    candidate_version: str,
    baselines: dict[str, Path],
) -> None:
    for descriptor in LEGACY_DESCRIPTORS:
        candidate = (
            root
            / descriptor.candidate_module
            / "target"
            / f"{descriptor.candidate_artifact}-{candidate_version}.jar"
        )
        report = run_japicmp(
            work / descriptor.name,
            baselines[descriptor.baseline_artifact],
            candidate,
            descriptor.class_name,
        )
        findings = report_findings(report)
        if findings:
            rendered = "\n".join(item.display() for item in findings)
            raise RuntimeError(
                f"Published descriptor japicmp regression for {descriptor.name}:\n"
                f"{rendered}"
            )


def run_legacy_descriptor_probe(
    candidate_jars: tuple[Path, ...],
    probe_classes: Path,
    runtime_dependencies: tuple[Path, ...],
    process_runner: Callable[
        [list[str], Path], subprocess.CompletedProcess[str]
    ] = run_capture,
) -> None:
    classpath = (*candidate_jars, probe_classes, *runtime_dependencies)
    completed = process_runner(
        [
            executable("java"),
            "-classpath",
            os.pathsep.join(str(path) for path in classpath),
            "LegacyDescriptorConsumerProbe",
        ],
        probe_classes.parent,
    )
    stdout = (completed.stdout or "").strip()
    stderr = (completed.stderr or "").strip()
    if completed.returncode != 0 or stdout != LEGACY_DESCRIPTOR_PROBE_SUCCESS:
        raise RuntimeError(
            "Legacy 2.0.1 descriptor probe did not pass exactly: "
            f"exit={completed.returncode}, stdout={stdout!r}, stderr={stderr!r}"
        )


def write_pom(work: Path, old_jar: Path, new_jar: Path, target_class: str) -> Path:
    pom = work / "pom.xml"
    atomic_create_text(
        pom,
        f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.github.coco.compatibility-fixture</groupId>
  <artifactId>fixture</artifactId>
  <version>1.0.0</version>
  <build>
    <plugins>
      <plugin>
        <groupId>com.github.siom79.japicmp</groupId>
        <artifactId>japicmp-maven-plugin</artifactId>
        <version>{JAPICMP_VERSION}</version>
        <executions>
          <execution>
            <id>public-api-compatibility</id>
            <phase>verify</phase>
            <goals><goal>cmp</goal></goals>
            <configuration>
              <oldVersion><file><path>{old_jar.as_posix()}</path></file></oldVersion>
              <newVersion><file><path>{new_jar.as_posix()}</path></file></newVersion>
              <parameter>
                <accessModifier>public</accessModifier>
                <includeSynthetic>false</includeSynthetic>
                <onlyModified>true</onlyModified>
                <ignoreMissingClasses>true</ignoreMissingClasses>
                <includes>
                  <include>{target_class}</include>
                </includes>
              </parameter>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
""",
    )
    return pom


def member_descriptor(
    class_name: str, member_kind: str, member: ElementTree.Element
) -> str:
    name = "<init>" if member_kind == "constructor" else member.get("name", "")
    parameters = ",".join(
        parameter.get("type", "")
        for parameter in member.findall("./parameters/parameter")
    )
    if member_kind in ("constructor", "method"):
        return f"{name}({parameters})"
    return member.get("name", class_name if member_kind == "class" else "")


def report_findings(report: Path) -> tuple[Finding, ...]:
    root = ElementTree.fromstring(read_bytes(report, "japicmp fixture report"))
    findings: list[Finding] = []
    for class_element in root.findall("./classes/class"):
        class_name = class_element.get("fullyQualifiedName", "")
        class_status = class_element.get("changeStatus", "UNCHANGED")
        if class_status == "REMOVED":
            findings.append(Finding(class_name, "class", class_name, class_status))
            continue
        class_type = class_element.find("./classType")
        if class_type is not None and class_type.get("changeStatus") not in (
            None,
            "UNCHANGED",
        ):
            findings.append(
                Finding(
                    class_name,
                    "classType",
                    class_type.get("newType", class_type.get("oldType", "")),
                    class_type.get("changeStatus", "MODIFIED"),
                )
            )
        for section, member_kind in (
            ("constructors", "constructor"),
            ("methods", "method"),
            ("fields", "field"),
            ("interfaces", "interface"),
        ):
            for member in class_element.findall(f"./{section}/{member_kind}"):
                status = member.get("changeStatus", "UNCHANGED")
                incompatible = (
                    member.get("binaryCompatible") == "false"
                    or member.get("sourceCompatible") == "false"
                )
                if status == "REMOVED" or (
                    status in ("NEW", "MODIFIED") and incompatible
                ):
                    findings.append(
                        Finding(
                            class_name,
                            member_kind,
                            member_descriptor(class_name, member_kind, member),
                            status,
                        )
                    )
    return tuple(sorted(findings))


def assert_expected_findings(
    vector_name: str,
    state: str,
    actual: tuple[Finding, ...],
    expected: tuple[Finding, ...],
) -> None:
    normalized_expected = tuple(sorted(expected))
    if actual == normalized_expected:
        return
    expected_text = ", ".join(item.display() for item in normalized_expected) or "none"
    actual_text = ", ".join(item.display() for item in actual) or "none"
    raise RuntimeError(
        f"Unexpected japicmp findings for {vector_name}/{state}. "
        f"Expected: {expected_text}. Actual: {actual_text}."
    )


def run_japicmp(
    work: Path,
    old_jar: Path,
    new_jar: Path,
    target_class: str,
    command_runner: Callable[[list[str], Path], None] = run,
) -> Path:
    mkdir(work, parents=True, exist_ok=True)
    report = work / REPORT_RELATIVE_PATH
    if entry_exists(report):
        unlink(report, "Stale japicmp fixture report")
    old_bytes = read_bytes(old_jar, "Old fixture JAR")
    new_bytes = read_bytes(new_jar, "New fixture JAR")
    with short_process_stage("japicmp") as stage:
        staged_old = stage / "old.jar"
        staged_new = stage / "new.jar"
        atomic_create_bytes(staged_old, old_bytes)
        atomic_create_bytes(staged_new, new_bytes)
        if (
            hashlib.sha256(read_bytes(staged_old, "Staged old JAR")).digest()
            != hashlib.sha256(old_bytes).digest()
            or hashlib.sha256(read_bytes(staged_new, "Staged new JAR")).digest()
            != hashlib.sha256(new_bytes).digest()
        ):
            raise RuntimeError("Fixture JAR digest changed in process staging.")
        pom = write_pom(stage, staged_old, staged_new, target_class)
        command_runner(
            [executable("mvn"), "-B", "-ntp", "-f", str(pom), "verify"],
            stage,
        )
        staged_report = stage / REPORT_RELATIVE_PATH
        try:
            report_snapshot = file_snapshot(
                staged_report, "staged japicmp fixture report"
            )
        except ValueError as exc:
            if " is empty:" in str(exc):
                raise RuntimeError(
                    f"japicmp produced an empty report: {report}"
                ) from exc
            raise RuntimeError(
                f"japicmp completed successfully but did not produce report: {report}"
            ) from exc
        mkdir(report.parent, parents=True, exist_ok=True)
        atomic_create_bytes(report, report_snapshot.contents)
        if (
            hashlib.sha256(read_bytes(report, "Copied japicmp report")).digest()
            != hashlib.sha256(report_snapshot.contents).digest()
        ):
            raise RuntimeError(f"japicmp report digest changed while copying: {report}")
    return report


def check_vector(
    vector: dict[str, object], root: Path, current_candidates: dict[str, Path]
) -> None:
    sources = vector["sources"]
    old_jar = compile_jar(root, "old", sources["old"])
    probe = (
        prepare_openapi_probe(root) if vector["name"] == OPENAPI_VECTOR_NAME else None
    )
    for state in STATES:
        candidate_jar = (
            current_candidates[str(vector["name"])]
            if state == "current"
            else compile_jar(root, state, sources[state])
        )
        comparison = root / f"compare-{state}"
        report = run_japicmp(comparison, old_jar, candidate_jar, vector["target_class"])
        findings = report_findings(report)
        expected = vector["expected_broken"] if state == "broken" else ()
        assert_expected_findings(vector["name"], state, findings, expected)
        if probe is not None:
            probe_classes, runtime_dependencies = probe
            run_openapi_probe(
                candidate_jar,
                probe_classes,
                runtime_dependencies,
                expect_success=state != "broken",
            )
        print(f"PASS {vector['name']} old-vs-{state}")


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk-home", type=Path, default=None)
    parser.add_argument(
        "--candidate-version",
        required=True,
        help="exact reactor candidate version used in all four real current JAR names",
    )
    return parser.parse_args(argv)


def configure_jdk(jdk_home: Path) -> None:
    jdk_home = logical_absolute(jdk_home)
    executable_suffix = ".exe" if os.name == "nt" else ""
    java = jdk_home / "bin" / f"java{executable_suffix}"
    javac = jdk_home / "bin" / f"javac{executable_suffix}"
    try:
        read_bytes(java, "JDK java executable")
        read_bytes(javac, "JDK javac executable")
    except ValueError as exc:
        raise RuntimeError(f"JDK home does not contain java/javac: {jdk_home}") from exc
    os.environ["JAVA_HOME"] = str(jdk_home)
    os.environ["PATH"] = f"{jdk_home / 'bin'}{os.pathsep}{os.environ['PATH']}"


def main(argv: list[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        if args.jdk_home is not None:
            configure_jdk(args.jdk_home)
        executable("javac")
        executable("mvn")
        current_candidates = find_reactor_current_candidates(
            repository_root(), args.candidate_version
        )
        for vector in VECTORS:
            with short_process_stage("api-fixture") as directory:
                check_vector(vector, directory, current_candidates)
        legacy_candidates = find_legacy_descriptor_candidates(
            repository_root(), args.candidate_version
        )
        with short_process_stage("legacy-descriptor-fixture") as probe_root:
            probe_classes, runtime_dependencies, baselines, published_artifacts = (
                prepare_legacy_descriptor_probe(probe_root)
            )
            verify_legacy_descriptor_japicmp(
                probe_root / "japicmp",
                repository_root(),
                args.candidate_version,
                baselines,
            )
            runtime_candidates = find_legacy_runtime_candidates(
                repository_root(), args.candidate_version, published_artifacts
            )
            candidate_closure = tuple(
                dict.fromkeys((*legacy_candidates, *runtime_candidates))
            )
            run_legacy_descriptor_probe(
                candidate_closure, probe_classes, runtime_dependencies
            )
        for descriptor in LEGACY_DESCRIPTORS:
            print(f"PASS legacy-2.0.1-descriptor {descriptor.name}")
    except (OSError, RuntimeError, ElementTree.ParseError) as exc:
        print(f"PUBLIC API REGRESSION FIXTURE ERROR: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
