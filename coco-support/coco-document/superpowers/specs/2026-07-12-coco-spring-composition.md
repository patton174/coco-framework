# Coco Spring Composition Specification

## Status

- Roadmap batch: `[2.0 7a/9]` implementation consolidation followed by `[2.0 7b/9]` dependency cutover
- Scope: merge the Spring configuration and feature-runtime implementation into `coco-spring-boot-autoconfigure`
- Compatibility rule: Maven ownership changes, Java packages and public FQCNs do not
- Published baseline: `v2.0.1`; `coco-config` and `coco-feature-runtime` remain supported coordinates throughout 2.x

## Goal

`coco-spring` should expose one implementation module and one composition-only starter:

```text
coco-spring/
  coco-spring-boot-autoconfigure/
  coco-spring-boot-starter/
```

`coco-spring-boot-autoconfigure` owns Spring Boot configuration binding, the resolved runtime feature plan, feature conditions, third-party auto-configuration filtering, Coco foundation auto-configuration, and early startup integration. `coco-spring-boot-starter` continues to contain no feature behavior and only composes dependencies.

This removes the artificial runtime split between `coco-config`, `coco-feature-runtime`, and the existing auto-configuration module without moving business or concrete feature behavior into the starter.

The tree above lists primary implementation modules. Because `v2.0.1` already published both former implementation coordinates, their source-free compatibility artifacts remain in the reactor and BOM during 2.x. They may later move under `coco-build/coco-compatibility`, but that physical move must not change their groupId, artifactId, version alignment, or transitive replacement surface.

## Facade Migration Contract

`coco-config` and `coco-feature-runtime` are deprecated compatibility coordinates. Direct consumers should migrate to `coco-spring-boot-autoconfigure`; normal applications continue to depend on `coco-spring-boot-starter`. The original plan treated both facades as batch-7a-only because it assumed the cutover would finish before any 2.0 publication. The immutable `v2.0.1` release invalidated that assumption, so batch 7b now removes internal use of the facades while preserving both published coordinates through the 2.x compatibility window.

Each facade has a single dependency on `coco-spring-boot-autoconfigure`. That replacement keeps the previous compile-scope surface available transitively, including `coco-api`, `coco-i18n`, `coco-feature-model`, `spring-boot`, and `spring-boot-autoconfigure`; `coco-feature-model` continues to provide Jackson transitively. This compatibility bridge does not make unrelated transitive types a preferred API. New or migrated modules that import one of them must declare the owning foundation or Spring artifact directly.

The complete in-repository batch 7b internal-cutover inventory is:

- `pom.xml`: retain both compatibility modules and both managed coordinates;
- `coco-features/pom.xml`: retain the `coco-feature-runtime` compatibility module until a separately reviewed physical move places it under `coco-build/coco-compatibility`;
- `coco-build/coco-dependencies/pom.xml`: retain both coordinates in the published BOM for 2.x consumers;
- `coco-spring/coco-spring-boot-starter/pom.xml`: remove both facade dependencies while retaining its direct `coco-spring-boot-autoconfigure` dependency;
- `coco-features/coco-feature-audit/pom.xml`, `coco-features/coco-feature-codegen/pom.xml`, `coco-features/coco-feature-data-permission/pom.xml`, `coco-features/coco-feature-mybatis-plus/pom.xml`, `coco-features/coco-feature-openapi/pom.xml`, `coco-features/coco-feature-security/pom.xml`, `coco-features/coco-feature-tenant/pom.xml`, and `coco-features/coco-feature-web/pom.xml`: replace `coco-feature-runtime` with a direct `coco-spring-boot-autoconfigure` dependency;
- `coco-spring/coco-config/pom.xml` and `coco-features/coco-feature-runtime/pom.xml`: remain source-free compatibility artifacts whose only dependency is `coco-spring-boot-autoconfigure`.

No internal implementation or starter POM may depend on either facade after batch 7b. Repository-wide scans must distinguish the allowed compatibility definitions, reactor declarations, dependency-management entries, and BOM entries from forbidden internal dependency use. External feature authors that use `@ConditionalOnCocoFeature` should depend on `coco-spring-boot-autoconfigure` directly after the cutover.

## Foundation Test Ownership

Moving the three foundation auto-configuration classes also moves their Spring Boot test ownership. `coco-feature-model`, `coco-i18n`, and `coco-logging` retain local tests for their plain contracts, properties, message handling, feature model, and logging implementation. They intentionally do not declare `spring-boot-autoconfigure` or `spring-boot-test` merely to support hypothetical framework-integration tests.

Tests that need `ApplicationContextRunner`, Spring Boot auto-configuration loading, configuration metadata, registration resources, or classpath composition belong in `coco-spring-boot-autoconfigure` or `coco-spring-boot-starter`, alongside the integration they exercise. A future foundation test that exposes a real runtime dependency requires a separate module-boundary review; test convenience alone must not introduce a reverse dependency from foundation to the Spring composition layer.

## Module Changes

The following implementation moves into `coco-spring-boot-autoconfigure`:

- all source, tests, and message bundles from `coco-config`;
- all source, tests, message bundles, and the auto-configuration import filter from `coco-feature-runtime`;
- the I18n `CocoCommonAutoConfiguration` binding;
- the Logging `CocoCommonLoggingAutoConfiguration` binding;
- the Feature Model `CocoFeatureRegistryAutoConfiguration` binding.

The three foundation modules retain their contracts, implementation classes, configuration property models, and message bundles. Only their Spring Boot auto-configuration classes, registration metadata, and matching tests move. This keeps foundation reusable while avoiding a reverse dependency on `coco-spring`.

Batch 7a keeps `coco-config` and `coco-feature-runtime` as source-free compatibility facades. Each facade depends only on `coco-spring-boot-autoconfigure`; it contains no duplicate classes, messages, auto-configuration imports, or `spring.factories` entries. Existing starter and feature POMs therefore continue to build while every implementation class has one physical owner.

The cutover was split because all nine consumer POMs together select the complete Web, Audit, and Codegen specifications and leave no safe room in the protected `48000`-character policy budget. Path mappings remain module-wide so POM, source, resources, and module metadata never silently lose their required feature specifications. Governance tests therefore lock batch 7b into independently buildable sub-batches and prove that each sub-batch fits without omission:

1. `7b1`: starter, data-permission, MyBatis-Plus, OpenAPI, security, and tenant;
2. `7b2`: Web;
3. `7b3`: Audit;
4. `7b4`: Codegen and the final repository-wide no-internal-facade assertion.

Each intermediate sub-batch may leave only the explicitly scheduled later consumers on `coco-feature-runtime`. Every changed consumer must switch atomically, and the final sub-batch must prove that all nine implementation consumers use `coco-spring-boot-autoconfigure` directly.

Batch 7b rewires the starter and feature implementation modules so they stop depending on the two facades. The reactor and dependency management continue publishing the compatibility coordinates, but those coordinates are leaves from the primary architecture's perspective and are not used to compose framework modules. Feature modules that use `@ConditionalOnCocoFeature` then depend on `coco-spring-boot-autoconfigure`, matching the target dependency direction:

```text
coco-spring-boot-starter -> coco-spring-boot-autoconfigure
coco-spring-boot-starter -> concrete features
concrete features -> coco-spring-boot-autoconfigure
coco-spring-boot-autoconfigure -> foundation
```

`coco-spring-boot-autoconfigure` must not depend on a concrete feature module.

## Compatibility Contract

The 2.x target keeps the two old Maven artifacts as deprecated, source-free compatibility facades. A new direct dependency on either old artifact should be replaced with `coco-spring-boot-autoconfigure` or, for applications, the normal `coco-spring-boot-starter` dependency. Their final removal is a next-major-version operation and is not part of batch 7b.

The compatibility artifacts retain normal JAR packaging in this batch because that is the already tested 7a behavior. Converting them to Maven relocation POMs is a separate decision: it must prove Maven Resolver dependency mediation, plugin behavior, IDE import, and a real external consumer before changing artifact type.

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

Its `spring.factories` preserves the existing Spring application listener and environment post-processor and adds the existing feature auto-configuration import filter. The compatibility artifacts contain no module-local registration files, so every binding is registered exactly once.

## Non-goals

- Renaming `coco-feature-*` artifacts; that remains batch 8.
- Renaming `coco-test` or moving samples and code generation; those remain later batches.
- Removing the published `coco-config` or `coco-feature-runtime` coordinates; that is not allowed within 2.x.
- Converting the compatibility JARs to Maven relocation POMs or physically moving their module directories.
- Changing feature defaults, dependency resolution, configuration keys, starter contents beyond dependency rewiring, or concrete feature behavior.
- Moving plain I18n, Logging, or Feature Model implementation into the Spring module.

## Acceptance Criteria

- Batch 7a leaves `coco-config` and `coco-feature-runtime` as source-free facades depending only on `coco-spring-boot-autoconfigure`.
- Batch 7a contains each moved implementation class and registration exactly once and keeps all existing consumers buildable.
- Batch 7b keeps both facade coordinates in the reactor, root dependency management, and published BOM.
- After batch 7b, only the two compatibility POMs define the old artifactIds; starter and feature implementation POMs contain no dependency on either old coordinate and use `coco-spring-boot-autoconfigure` directly.
- Each facade remains source-free and contains no auto-configuration imports, `spring.factories`, messages, templates, or duplicate implementation resources.
- A focused compatibility test resolves the old coordinates and compiles representative existing public FQCNs through their transitive replacement surface.
- The replacement artifact contains each moved class and registration exactly once.
- Foundation main artifacts no longer contain their three Boot auto-configuration classes or auto-configuration import files.
- Existing configuration metadata, auto-configuration, runtime feature condition, import-filter, starter, and feature tests pass from the new ownership location.
- JDK 21 `mvn -B clean verify` passes with Java 17 bytecode target so duplicate classes and resources cannot survive from an incremental build.
- The release-profile smoke build passes with GPG skipped.
- `codegraph sync .`, README drift validation, and governance tests pass before the PR is opened.

## Verification Ownership

Governance policy-routing tests use `collect_policy()` to prove deterministic path-to-spec routing, complete policy loading, omission handling, and the `48000`-character budget. Their migration-path fixtures may include a planned physical location. They do not prove that a candidate module exists, compiles, resolves an old coordinate, or runs an existing consumer.

Canonical integration owns those executable claims: the tracked current facade POMs and sample consumers are built by the reactor Maven install and sample Maven verification in `reusable-tests.yml`, and the resulting Boot archives are inspected by `verify_sample_feature_coordinates.py`. A physical compatibility-module move must update that canonical Maven/Python integration in the same implementation PR; it must not be represented as an implemented module solely by a governance routing fixture.
