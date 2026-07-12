# Coco Spring Composition Specification

## Status

- Roadmap batch: `[2.0 7a/9]` implementation consolidation followed by `[2.0 7b/9]` dependency cutover
- Scope: merge the Spring configuration and feature-runtime implementation into `coco-spring-boot-autoconfigure`
- Compatibility rule: Maven ownership changes, Java packages and public FQCNs do not

## Goal

`coco-spring` should expose one implementation module and one composition-only starter:

```text
coco-spring/
  coco-spring-boot-autoconfigure/
  coco-spring-boot-starter/
```

`coco-spring-boot-autoconfigure` owns Spring Boot configuration binding, the resolved runtime feature plan, feature conditions, third-party auto-configuration filtering, Coco foundation auto-configuration, and early startup integration. `coco-spring-boot-starter` continues to contain no feature behavior and only composes dependencies.

This removes the artificial runtime split between `coco-config`, `coco-feature-runtime`, and the existing auto-configuration module without moving business or concrete feature behavior into the starter.

## Module Changes

The following implementation moves into `coco-spring-boot-autoconfigure`:

- all source, tests, and message bundles from `coco-config`;
- all source, tests, message bundles, and the auto-configuration import filter from `coco-feature-runtime`;
- the I18n `CocoCommonAutoConfiguration` binding;
- the Logging `CocoCommonLoggingAutoConfiguration` binding;
- the Feature Model `CocoFeatureRegistryAutoConfiguration` binding.

The three foundation modules retain their contracts, implementation classes, configuration property models, and message bundles. Only their Spring Boot auto-configuration classes, registration metadata, and matching tests move. This keeps foundation reusable while avoiding a reverse dependency on `coco-spring`.

Batch 7a keeps `coco-config` and `coco-feature-runtime` as source-free compatibility facades. Each facade depends only on `coco-spring-boot-autoconfigure`; it contains no duplicate classes, messages, auto-configuration imports, or `spring.factories` entries. Existing starter and feature POMs therefore continue to build while every implementation class has one physical owner.

The cutover is split because changing every concrete feature POM in the implementation PR would select unrelated feature specifications and exceed the protected `48000`-character policy budget before jury execution. Each batch must remain independently buildable and reviewable without increasing that limit.

Batch 7b rewires the reactor, dependency management, starter, and feature modules to stop publishing or depending on the two facades. Feature modules that use `@ConditionalOnCocoFeature` then depend on `coco-spring-boot-autoconfigure`, matching the target dependency direction:

```text
coco-spring-boot-starter -> coco-spring-boot-autoconfigure
coco-spring-boot-starter -> concrete features
concrete features -> coco-spring-boot-autoconfigure
coco-spring-boot-autoconfigure -> foundation
```

`coco-spring-boot-autoconfigure` must not depend on a concrete feature module.

## Compatibility Contract

The final target removes the two intermediate Maven artifacts. A direct dependency on either old artifact must be replaced with `coco-spring-boot-autoconfigure` or, for applications, the normal `coco-spring-boot-starter` dependency. The temporary 7a facades provide a buildable migration boundary only; 7b removes them after every in-repository consumer has been rewired.

Within the replacement artifact, the following remain compatible:

- packages and public FQCNs, including `io.github.coco.config.*` and `io.github.coco.feature.runtime.*`;
- existing configuration prefixes and generated configuration metadata;
- feature resolution precedence and runtime conditions;
- `spring.factories` registration of `CocoFeatureAutoConfigurationImportFilter`;
- auto-configuration FQCNs for I18n, Logging, Feature Model, Config, and Feature Runtime;
- Bean names, conditional annotations, message bundle basenames, and replacement points.

No Java package consolidation is part of this batch. Package renames would be a separate compatibility review.

## Registration Metadata

`coco-spring-boot-autoconfigure` owns the single merged auto-configuration import list. It contains:

1. `io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration`
2. `io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration`
3. `io.github.coco.feature.registry.CocoFeatureRegistryAutoConfiguration`
4. `io.github.coco.config.CocoConfigAutoConfiguration`
5. `io.github.coco.feature.runtime.autoconfigure.CocoFeatureRuntimeAutoConfiguration`
6. `io.github.coco.spring.boot.CocoAutoConfiguration`

Its `spring.factories` preserves the existing Spring application listener and environment post-processor and adds the existing feature auto-configuration import filter. Old module-local registration files are removed with their modules so that every binding is registered exactly once.

## Non-goals

- Renaming `coco-feature-*` artifacts; that remains batch 8.
- Renaming `coco-test` or moving samples and code generation; those remain later batches.
- Changing feature defaults, dependency resolution, configuration keys, starter contents beyond dependency rewiring, or concrete feature behavior.
- Moving plain I18n, Logging, or Feature Model implementation into the Spring module.

## Acceptance Criteria

- Batch 7a leaves `coco-config` and `coco-feature-runtime` as source-free facades depending only on `coco-spring-boot-autoconfigure`.
- Batch 7a contains each moved implementation class and registration exactly once and keeps all existing consumers buildable.
- Batch 7b removes both facades from the reactor and dependency management after rewiring all POM consumers.
- After batch 7b, no POM depends on either removed artifact.
- The replacement artifact contains each moved class and registration exactly once.
- Foundation main artifacts no longer contain their three Boot auto-configuration classes or auto-configuration import files.
- Existing configuration metadata, auto-configuration, runtime feature condition, import-filter, starter, and feature tests pass from the new ownership location.
- JDK 21 `mvn -B clean verify` passes with Java 17 bytecode target so removed classes and resources cannot survive from an incremental build.
- The release-profile smoke build passes with GPG skipped.
- `codegraph sync .`, README drift validation, and governance tests pass before the PR is opened.
