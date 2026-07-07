# Coco API Core And Module I18n Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `coco-api` and `coco-core` into aggregate modules with real jar children, and let framework modules register their own i18n message bundles.

**Architecture:** `coco-api` becomes an aggregate POM with `coco-api-core`; `coco-core` becomes an aggregate POM with `coco-core-runtime`. `coco-common-i18n` owns a message-bundle registration SPI and composes application, module, and framework basenames into the Coco-owned `MessageSource`.

**Tech Stack:** Java 17, Maven multi-module build, Spring Boot 4.1.0, Spring `MessageSource`, Spring Boot auto-configuration, JUnit Jupiter, `ApplicationContextRunner`.

## Global Constraints

- Keep existing Java package names unchanged.
- Starter and BOM must depend on actual jar artifacts, not aggregate POMs.
- `coco-common-i18n` must not depend on `coco-config`, `coco-core-runtime`, or feature modules.
- Module message resources use artifact-style basenames such as `coco-config-messages`.
- Business application `messages*` resources stay first; `coco-messages*` stays last as the framework fallback.
- Class JavaDoc must keep standard HTML tags, Chinese descriptions, author `patton174`, repository URL, and module metadata.

---

### Task 1: Convert API And Core To Aggregate Modules

**Files:**
- Modify: `pom.xml`
- Modify: `coco-api/pom.xml`
- Create: `coco-api/coco-api-core/pom.xml`
- Move: `coco-api/src/**` to `coco-api/coco-api-core/src/**`
- Modify: `coco-core/pom.xml`
- Create: `coco-core/coco-core-runtime/pom.xml`
- Move: `coco-core/src/**` to `coco-core/coco-core-runtime/src/**`
- Modify: `coco-bom/pom.xml`
- Modify: `coco-spring-boot-starter/pom.xml`
- Modify: `coco-common/coco-common-i18n/pom.xml`
- Modify: `coco-config/pom.xml`
- Modify: `coco-features/coco-feature-registry/pom.xml`

**Interfaces:**
- Produces Maven artifact `io.github.patton174:coco-api-core`.
- Produces Maven artifact `io.github.patton174:coco-core-runtime`.
- Keeps Java package `io.github.coco.api`.
- Keeps Java package `io.github.coco.core`.

- [ ] Update `coco-api/pom.xml` to packaging `pom` and module `coco-api-core`.
- [ ] Add `coco-api/coco-api-core/pom.xml` with parent `coco-api`, artifactId `coco-api-core`, and existing JUnit test dependency.
- [ ] Move all current `coco-api/src` files into `coco-api/coco-api-core/src`.
- [ ] Update JavaDoc module metadata from `coco-api` to `coco-api-core`.
- [ ] Update `coco-core/pom.xml` to packaging `pom` and module `coco-core-runtime`.
- [ ] Add `coco-core/coco-core-runtime/pom.xml` with parent `coco-core`, artifactId `coco-core-runtime`, and dependency on `coco-api-core`.
- [ ] Move all current `coco-core/src` files into `coco-core/coco-core-runtime/src`.
- [ ] Update JavaDoc module metadata from `coco-core` to `coco-core-runtime`.
- [ ] Replace dependencyManagement and direct dependencies from `coco-api` to `coco-api-core`.
- [ ] Replace dependencyManagement and starter dependency from `coco-core` to `coco-core-runtime`.
- [ ] Run `mvn -pl :coco-api-core,:coco-core-runtime -am test`; expected result is build success.

### Task 2: Add Message Bundle Registration SPI

**Files:**
- Create: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/i18n/CocoMessageBundleRegistrar.java`
- Create: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/i18n/CocoMessageBundleRegistry.java`
- Create: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/i18n/DefaultCocoMessageBundleRegistry.java`
- Modify: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/autoconfigure/CocoCommonAutoConfiguration.java`
- Modify: `coco-common/coco-common-i18n/src/test/java/io/github/coco/common/autoconfigure/CocoCommonAutoConfigurationTest.java`
- Create: `coco-common/coco-common-i18n/src/test/resources/module-messages_zh_CN.properties`

**Interfaces:**
- Produces `CocoMessageBundleRegistrar#registerBundles(CocoMessageBundleRegistry registry)`.
- Produces `CocoMessageBundleRegistry#add(String basename)`.
- Produces package-private `DefaultCocoMessageBundleRegistry#basenames()`.

- [ ] Add a failing auto-configuration test that registers `module-messages` and resolves `module.hello`.
- [ ] Implement `CocoMessageBundleRegistrar` and `CocoMessageBundleRegistry`.
- [ ] Implement `DefaultCocoMessageBundleRegistry` with blank-name rejection, duplicate removal, and insertion order preservation.
- [ ] Change `CocoCommonAutoConfiguration#cocoMessageSource` to accept `ObjectProvider<CocoMessageBundleRegistrar>`.
- [ ] Compose basenames in this order: configured basenames except `coco-messages`, registrar basenames, then `coco-messages`.
- [ ] Run `mvn -pl :coco-common-i18n -am test`; expected result is build success.

### Task 3: Register Config And Feature Registry Message Bundles

**Files:**
- Modify: `coco-config/pom.xml`
- Modify: `coco-config/src/main/java/io/github/coco/config/CocoConfigAutoConfiguration.java`
- Create: `coco-config/src/main/resources/coco-config-messages.properties`
- Create: `coco-config/src/main/resources/coco-config-messages_zh_CN.properties`
- Create: `coco-config/src/main/resources/coco-config-messages_en_US.properties`
- Modify: `coco-config/src/test/java/io/github/coco/config/CocoConfigAutoConfigurationTest.java`
- Modify: `coco-features/coco-feature-registry/pom.xml`
- Create: `coco-features/coco-feature-registry/src/main/java/io/github/coco/feature/registry/CocoFeatureRegistryAutoConfiguration.java`
- Create: `coco-features/coco-feature-registry/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `coco-features/coco-feature-registry/src/main/resources/coco-feature-registry-messages.properties`
- Create: `coco-features/coco-feature-registry/src/main/resources/coco-feature-registry-messages_zh_CN.properties`
- Create: `coco-features/coco-feature-registry/src/main/resources/coco-feature-registry-messages_en_US.properties`
- Create: `coco-features/coco-feature-registry/src/test/java/io/github/coco/feature/registry/CocoFeatureRegistryAutoConfigurationTest.java`

**Interfaces:**
- Produces bean `CocoMessageBundleRegistrar cocoConfigMessageBundleRegistrar()`.
- Produces bean `CocoMessageBundleRegistrar cocoFeatureRegistryMessageBundleRegistrar()`.
- Produces message code `coco.config.features.disabled.invalid`.
- Produces message code `coco.feature.registry.not-found`.

- [ ] Add `coco-common-i18n` dependency to `coco-config`.
- [ ] Add config message resources with Chinese and English text.
- [ ] Add `cocoConfigMessageBundleRegistrar` bean to `CocoConfigAutoConfiguration`.
- [ ] Add config auto-configuration test that combines `CocoCommonAutoConfiguration` and resolves `coco.config.features.disabled.invalid`.
- [ ] Add `coco-common-i18n` and `spring-boot-autoconfigure` dependencies to `coco-feature-registry`.
- [ ] Add `CocoFeatureRegistryAutoConfiguration` with a registrar for `coco-feature-registry-messages`.
- [ ] Add feature-registry auto-configuration imports file.
- [ ] Add feature-registry message resources with Chinese and English text.
- [ ] Add feature-registry auto-configuration test that resolves `coco.feature.registry.not-found`.
- [ ] Run `mvn -pl :coco-config,:coco-feature-registry -am test`; expected result is build success.

### Task 4: Documentation, Verification, And PR

**Files:**
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-07-04-coco-api-core-i18n-design.md`

**Interfaces:**
- Produces documented module layout and dependency names.
- Produces a GitHub PR for the current branch when GitHub is reachable.

- [ ] Update README module list to mention `coco-api-core` and `coco-core-runtime`.
- [ ] Update the design doc if implementation details changed.
- [ ] Run `rg -n "<artifactId>coco-api</artifactId>|<artifactId>coco-core</artifactId>" -g "pom.xml"`; expected remaining matches are aggregate parent references only.
- [ ] Run `mvn -q verify`; expected result is build success.
- [ ] Run `mvn -q -DskipTests install javadoc:javadoc`; expected result is build success.
- [ ] Commit with message `refactor: split api core and register module messages`.
- [ ] Push `feature/coco-common-i18n`.
- [ ] Run `gh pr list --head feature/coco-common-i18n` and create a PR to `main` if none exists.
