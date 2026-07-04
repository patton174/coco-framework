# Feature Activation Closed Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one complete Coco feature activation loop across API configuration, shared feature metadata, Maven build output, runtime feature lookup, and conditional auto-configuration.

**Architecture:** `coco-api-core` exposes the public annotation and feature registry contract. `coco-feature-registry` owns the shared metadata, feature selection model, resolution rules, and manifest schema. `coco-config`, `coco-maven-plugin`, and `coco-core-runtime` all consume that same model so build-time and runtime use the same decisions.

**Tech Stack:** Java 17, Spring Boot 4.1 auto-configuration, Maven Plugin API 3.8.9, Jackson for manifest JSON, SnakeYAML for build-time YAML parsing, JUnit Jupiter and AssertJ.

## Global Constraints

- Keep business usage as a single dependency on `coco-spring-boot-starter`.
- Default standard features are enabled unless explicitly disabled.
- Support code configuration with `@CocoFeatures(enabled = {...}, disabled = {...})`.
- Keep existing `coco.features.exclude` working as an alias for disabled features.
- Disabled features win over enabled features within the same configuration source.
- Code configuration has higher priority than application configuration.
- Generate `META-INF/coco/features.json` during the Maven build.
- Runtime feature checks read `features.json` when it exists and fall back to environment properties when it does not.
- JavaDoc must use standard HTML tags, include author `patton174`, and include repository URL `https://github.com/patton174/coco-framework`.
- Do not implement actual pagination, audit, tenant, or security business behavior in this task.

---

### Task 1: Public Feature Configuration Contract

**Files:**
- Create: `coco-api/coco-api-core/src/main/java/io/github/coco/api/feature/CocoFeatures.java`
- Modify: `coco-api/coco-api-core/src/main/java/io/github/coco/api/feature/CocoFeatureRegistry.java`
- Modify: `coco-api/coco-api-core/src/main/java/io/github/coco/api/feature/DefaultCocoFeatureRegistry.java`
- Modify: `coco-api/coco-api-core/src/test/java/io/github/coco/api/feature/DefaultCocoFeatureRegistryTest.java`

**Interfaces:**
- Produces: `@CocoFeatures(enabled = CocoFeature[], disabled = CocoFeature[])`.
- Produces: `CocoFeatureRegistry.include(...)`, `enable(...)`, `disable(...)`, `includedFeatures()`.

- [ ] Add the `CocoFeatures` annotation with runtime retention and type target.
- [ ] Extend `CocoFeatureRegistry` with include/enable/disable methods while keeping `exclude(...)`.
- [ ] Update `DefaultCocoFeatureRegistry` to store included and excluded sets.
- [ ] Add tests proving include/exclude aliases and null-safe behavior.

### Task 2: Shared Feature Decision Model

**Files:**
- Modify: `coco-features/coco-feature-registry/src/main/java/io/github/coco/feature/registry/CocoFeatureDefinition.java`
- Modify: `coco-features/coco-feature-registry/src/main/java/io/github/coco/feature/registry/StandardCocoFeatures.java`
- Create: `coco-features/coco-feature-registry/src/main/java/io/github/coco/feature/registry/CocoFeatureSelection.java`
- Create: `coco-features/coco-feature-registry/src/main/java/io/github/coco/feature/registry/CocoFeaturePlan.java`
- Create: `coco-features/coco-feature-registry/src/main/java/io/github/coco/feature/registry/CocoFeatureManifest.java`
- Create: `coco-features/coco-feature-registry/src/main/java/io/github/coco/feature/registry/CocoFeatureManifestEntry.java`
- Create: `coco-features/coco-feature-registry/src/main/java/io/github/coco/feature/registry/CocoFeatureManifestLoader.java`
- Modify: `coco-features/coco-feature-registry/src/test/java/io/github/coco/feature/registry/StandardCocoFeaturesTest.java`

**Interfaces:**
- Produces: `StandardCocoFeatures.resolve(CocoFeatureSelection)`.
- Produces: `CocoFeatureManifestLoader.read(InputStream)` and `load(ClassLoader)`.

- [ ] Add auto-configuration class names to standard feature definitions.
- [ ] Add `CocoFeatureSelection` with merge semantics for lower and higher priority selections.
- [ ] Add `CocoFeaturePlan` containing enabled, disabled, and definition lists.
- [ ] Add manifest records and JSON loader.
- [ ] Update registry tests for dependency cascade, code-over-property priority, and manifest round-trip.

### Task 3: Runtime Configuration Uses The Shared Plan

**Files:**
- Modify: `coco-config/src/main/java/io/github/coco/config/CocoFeatureProperties.java`
- Modify: `coco-config/src/main/java/io/github/coco/config/DefaultCocoFeatureManager.java`
- Modify: `coco-config/src/main/java/io/github/coco/config/CocoConfigAutoConfiguration.java`
- Create: `coco-config/src/main/java/io/github/coco/config/CocoFeatureSelectionCollector.java`
- Modify: `coco-config/src/test/java/io/github/coco/config/CocoConfigAutoConfigurationTest.java`
- Modify: `coco-config/src/test/java/io/github/coco/config/CocoFeatureManagerTest.java`

**Interfaces:**
- Consumes: `CocoFeatureSelection`, `CocoFeaturePlan`, `CocoFeatures`.
- Produces: Spring bean `CocoFeaturePlan`.

- [ ] Add `enabled`, `disabled`, and `exclude` alias handling to `CocoFeatureProperties`.
- [ ] Collect `@CocoFeatures` from Spring bean definitions without requiring business code to implement an interface.
- [ ] Merge properties, `CocoConfigurer`, and annotation selections into one plan.
- [ ] Prefer `META-INF/coco/features.json` when present.
- [ ] Update tests for properties, annotation configuration, configurer aliases, and manifest precedence.

### Task 4: Maven Plugin Generates Manifest And Applies Feature Dependencies

**Files:**
- Modify: `coco-maven-plugin/pom.xml`
- Modify: `coco-maven-plugin/src/main/java/io/github/coco/maven/CocoFeaturesMojo.java`
- Create: `coco-maven-plugin/src/main/java/io/github/coco/maven/CocoBuildFeatureConfigurationLoader.java`
- Create: `coco-maven-plugin/src/main/java/io/github/coco/maven/CocoAnnotatedFeatureScanner.java`
- Create: `coco-maven-plugin/src/test/java/io/github/coco/maven/CocoBuildFeatureConfigurationLoaderTest.java`
- Create: `coco-maven-plugin/src/test/java/io/github/coco/maven/CocoFeaturesMojoTest.java`
- Modify: `coco-parent/pom.xml`

**Interfaces:**
- Consumes: application YAML/properties and compiled classes annotated with `@CocoFeatures`.
- Produces: `${project.build.outputDirectory}/META-INF/coco/features.json`.
- Produces: Maven runtime dependencies for enabled feature modules.

- [ ] Add plugin dependencies on `coco-api-core`, `coco-feature-registry`, Maven project APIs, Jackson, SnakeYAML, JUnit, and AssertJ.
- [ ] Parse `coco.features.enabled`, `coco.features.disabled`, and `coco.features.exclude`.
- [ ] Scan compiled project classes for `@CocoFeatures`.
- [ ] Generate deterministic JSON manifest under `META-INF/coco/features.json`.
- [ ] Add enabled feature module dependencies to the Maven project model if they are not already present.
- [ ] Bind `coco:features` in `coco-parent` so business projects inherit the build step.

### Task 5: Runtime Feature Conditions

**Files:**
- Modify: `coco-core/coco-core-runtime/pom.xml`
- Create: `coco-core/coco-core-runtime/src/main/java/io/github/coco/core/feature/ConditionalOnCocoFeature.java`
- Create: `coco-core/coco-core-runtime/src/main/java/io/github/coco/core/feature/OnCocoFeatureCondition.java`
- Create: `coco-core/coco-core-runtime/src/main/java/io/github/coco/core/feature/CocoRuntimeFeatureResolver.java`
- Create: `coco-core/coco-core-runtime/src/test/java/io/github/coco/core/feature/OnCocoFeatureConditionTest.java`
- Modify: feature auto-configuration classes under `coco-features/coco-feature-*`.
- Modify: feature module POMs under `coco-features/coco-feature-*`.

**Interfaces:**
- Produces: `@ConditionalOnCocoFeature(CocoFeature.X)`.
- Consumes: `features.json` or environment fallback.

- [ ] Implement the condition annotation and Spring Boot condition.
- [ ] Resolve feature state from classpath manifest first, then environment properties.
- [ ] Annotate each feature auto-configuration with the matching feature condition.
- [ ] Add `coco-core-runtime` dependency to feature modules that use the condition.
- [ ] Add tests proving disabled features skip their auto-configuration.

### Task 6: Sample And End-To-End Verification

**Files:**
- Modify: `coco-samples/coco-sample-basic/src/main/java/io/github/coco/sample/basic/CocoConfig.java`
- Modify: `coco-samples/coco-sample-basic/src/main/resources/application.yml`
- Add test files only if the existing sample structure supports them without introducing a full web application test.

**Interfaces:**
- Consumes: inherited `coco:features` execution from `coco-parent`.
- Produces: sample build output with `META-INF/coco/features.json`.

- [ ] Use `@CocoFeatures(disabled = {CocoFeature.TENANT, CocoFeature.DATA_PERMISSION})` in the sample configuration.
- [ ] Keep application YAML focused on i18n or feature settings not represented by the annotation.
- [ ] Run focused module tests.
- [ ] Run `mvn -q verify`.
- [ ] Run `mvn -q -DskipTests install javadoc:javadoc`.
- [ ] Inspect sample `target/classes/META-INF/coco/features.json`.
- [ ] Commit, push, create PR, wait for CI, and merge after checks pass.

## Self-Review

- Spec coverage: all approved requirements map to tasks 1 through 6.
- Placeholder scan: no TBD or open-ended implementation placeholder remains.
- Scope check: the task is intentionally long but still one subsystem: feature activation infrastructure.
- Type consistency: all shared types are defined in tasks before consumers use them.
