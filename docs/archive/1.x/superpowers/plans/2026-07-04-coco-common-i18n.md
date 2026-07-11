# Coco Common I18n Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `coco-common/coco-common-i18n` as the first Coco common infrastructure module for i18n messages, framework prompts, and framework exceptions.

**Architecture:** `coco-common` is an aggregate POM. `coco-common-i18n` is the reusable infrastructure artifact consumed by `coco-spring-boot-starter`. Public APIs live in `io.github.coco.common.i18n` and `io.github.coco.common.exception`; Spring Boot wiring lives in `io.github.coco.common.autoconfigure`. Message text is resolved through a Coco-owned `MessageSource`, while exceptions and prompts carry code, args, and default text without resolving locale too early.

**Tech Stack:** Java 17, Maven multi-module build, Spring Boot 4.1.0, Spring `MessageSource`, Spring Boot auto-configuration, Spring Boot configuration metadata, JUnit Jupiter, `ApplicationContextRunner`.

## Global Constraints

- Add `coco-common` as an aggregate and `coco-common-i18n` as the concrete i18n artifact; do not create `coco-base`.
- Use property namespace `coco.common.i18n`.
- Do not implement Servlet request locale resolution, global web exception handling, response wrapping, database-backed messages, frontend language-pack export, or annotation-driven i18n in this task.
- `coco-common-i18n` must not depend on `coco-config`.
- Use standard JavaDoc HTML tags for class comments, with Chinese descriptions and author/repository/module metadata.
- Keep business-facing APIs in public packages and framework implementation wiring in `autoconfigure` or internal packages.

---

### Task 1: Add Maven Module Wiring

**Files:**
- Modify: `pom.xml`
- Modify: `coco-bom/pom.xml`
- Modify: `coco-spring-boot-starter/pom.xml`
- Create: `coco-common/pom.xml`
- Create: `coco-common/coco-common-i18n/pom.xml`

**Interfaces:**
- Produces Maven artifact `io.github.patton174:coco-common-i18n`.
- Produces starter dependency on `coco-common-i18n`.

- [ ] Add `<module>coco-common</module>` to the root reactor after `coco-api` and before feature/starter modules.
- [ ] Add `coco-common` aggregate to the root reactor after `coco-api`.
- [ ] Add `coco-common-i18n` to root dependency management.
- [ ] Add `coco-common-i18n` to `coco-bom` dependency management.
- [ ] Add `coco-common-i18n` as a dependency of `coco-spring-boot-starter`.
- [ ] Create `coco-common/coco-common-i18n/pom.xml` with dependencies on `coco-api`, `spring-context`, `spring-boot`, `spring-boot-autoconfigure`, optional `spring-boot-configuration-processor`, and test dependencies `spring-boot-test`, `assertj-core`, `junit-jupiter`.
- [ ] Run `mvn -pl :coco-common-i18n -am test`; expected result is success if the empty module compiles.

### Task 2: Implement Framework Message And Exception Contracts

**Files:**
- Create: `coco-common/coco-common-i18n/src/test/java/io/github/coco/common/i18n/CocoMessageTest.java`
- Create: `coco-common/coco-common-i18n/src/test/java/io/github/coco/common/exception/CocoExceptionTest.java`
- Create: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/i18n/CocoMessage.java`
- Create: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/exception/CocoException.java`

**Interfaces:**
- Produces `CocoMessage(String code, String defaultMessage, Object... args)`.
- Produces `CocoMessage#code()`, `CocoMessage#defaultMessage()`, `CocoMessage#args()`.
- Produces `CocoException#code()`, `CocoException#args()`, `CocoException#defaultMessage()`.

- [ ] Write failing tests for `CocoMessage` preserving code/default/args and rejecting blank codes.
- [ ] Write failing tests for `CocoException` preserving code/default/args/cause and returning default text from `getMessage()`.
- [ ] Run `mvn -pl :coco-common-i18n -am test`; expected RED with missing classes.
- [ ] Implement `CocoMessage` as a record with a compact constructor that rejects blank codes and defensively copies args.
- [ ] Implement `CocoException` with constructor overloads for code-only, code/default, code/default/args, and code/default/cause. Use defensive args copies and return default message when present, otherwise code.
- [ ] Run `mvn -pl :coco-common-i18n -am test`; expected GREEN for contract tests.

### Task 3: Implement I18n Service And Properties

**Files:**
- Create: `coco-common/coco-common-i18n/src/test/java/io/github/coco/common/i18n/DefaultCocoMessageServiceTest.java`
- Create: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/CocoCommonProperties.java`
- Create: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/i18n/CocoI18nProperties.java`
- Create: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/i18n/CocoLocaleResolver.java`
- Create: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/i18n/DefaultCocoLocaleResolver.java`
- Create: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/i18n/CocoMessageService.java`
- Create: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/i18n/DefaultCocoMessageService.java`
- Create: `coco-common/coco-common-i18n/src/test/resources/messages_zh_CN.properties`
- Create: `coco-common/coco-common-i18n/src/test/resources/messages_en_US.properties`

**Interfaces:**
- Produces `CocoMessageService#getMessage(String code, Object... args)`.
- Produces `CocoMessageService#getMessage(String code, Locale locale, Object... args)`.
- Produces `CocoMessageService#getMessageOrDefault(String code, String defaultMessage, Object... args)`.
- Produces `CocoMessageService#getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args)`.
- Produces `CocoMessageService#resolve(CocoMessage message)`.
- Produces `CocoMessageService#resolve(CocoMessage message, Locale locale)`.
- Produces `CocoMessageService#resolve(CocoException exception)`.
- Produces `CocoMessageService#resolve(CocoException exception, Locale locale)`.

- [ ] Write failing tests that use `ResourceBundleMessageSource` and verify Chinese and English message resolution.
- [ ] Write failing tests for missing-code fallback and blank-code rejection.
- [ ] Run `mvn -pl :coco-common-i18n -am test`; expected RED with missing service/properties classes.
- [ ] Implement `CocoI18nProperties` with defaults: basename `messages`, `coco-messages`; default locale `Locale.SIMPLIFIED_CHINESE`; fallback-to-system-locale `false`; use-code-as-default-message `true`.
- [ ] Implement `CocoCommonProperties` as `@ConfigurationProperties(prefix = "coco.common")` and mark the `i18n` nested property with `@NestedConfigurationProperty`.
- [ ] Implement `DefaultCocoLocaleResolver` to return configured default locale.
- [ ] Implement `DefaultCocoMessageService` with code validation, locale fallback to `CocoLocaleResolver`, and overloads for `CocoMessage` and `CocoException`.
- [ ] Run `mvn -pl :coco-common-i18n -am test`; expected GREEN for service tests.

### Task 4: Implement Spring Boot Auto-Configuration And Metadata

**Files:**
- Create: `coco-common/coco-common-i18n/src/test/java/io/github/coco/common/autoconfigure/CocoCommonAutoConfigurationTest.java`
- Create: `coco-common/coco-common-i18n/src/test/java/io/github/coco/common/CocoCommonConfigurationMetadataTest.java`
- Create: `coco-common/coco-common-i18n/src/main/java/io/github/coco/common/autoconfigure/CocoCommonAutoConfiguration.java`
- Create: `coco-common/coco-common-i18n/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `coco-common/coco-common-i18n/src/main/resources/coco-messages.properties`
- Create: `coco-common/coco-common-i18n/src/main/resources/coco-messages_zh_CN.properties`
- Create: `coco-common/coco-common-i18n/src/main/resources/coco-messages_en_US.properties`

**Interfaces:**
- Produces Spring bean named `cocoMessageSource`.
- Produces `CocoLocaleResolver` bean when missing.
- Produces `CocoMessageService` bean when missing.
- Produces configuration metadata for `coco.common.i18n.*`.

- [ ] Write failing `ApplicationContextRunner` tests for auto-created `CocoMessageService`, property overrides, framework message resolution, and preserving an application primary `messageSource`.
- [ ] Write failing metadata test that asserts `spring-configuration-metadata.json` contains `coco.common.i18n.basename`, `coco.common.i18n.default-locale`, `coco.common.i18n.fallback-to-system-locale`, and `coco.common.i18n.use-code-as-default-message`.
- [ ] Run `mvn -pl :coco-common-i18n -am test`; expected RED with missing auto-configuration.
- [ ] Implement `CocoCommonAutoConfiguration` with `@AutoConfiguration`, `@EnableConfigurationProperties(CocoCommonProperties.class)`, `ResourceBundleMessageSource`, `@Bean("cocoMessageSource")`, and `@ConditionalOnMissingBean` where appropriate.
- [ ] Add auto-configuration imports file.
- [ ] Add default framework message bundles with codes `coco.error.unknown`, `coco.error.invalid-argument`, and `coco.error.missing-message-code`.
- [ ] Run `mvn -pl :coco-common-i18n -am test`; expected GREEN.

### Task 5: Add Sample Configuration And Full Verification

**Files:**
- Modify: `coco-samples/coco-sample-basic/src/main/resources/application.yml`
- Create: `coco-samples/coco-sample-basic/src/main/resources/messages_zh_CN.properties`
- Create: `coco-samples/coco-sample-basic/src/main/resources/messages_en_US.properties`

**Interfaces:**
- Produces sample `coco.common.i18n` configuration.
- Produces sample business message bundle override pattern.

- [ ] Add sample `coco.common.i18n` configuration under the existing `coco` root.
- [ ] Add sample message bundle entries such as `sample.hello=你好，Coco` and `sample.hello=Hello, Coco`.
- [ ] Run `mvn -pl :coco-common-i18n -am test`; expected GREEN.
- [ ] Run `mvn -q verify`; expected full reactor success.
- [ ] Run `mvn -q -DskipTests install javadoc:javadoc`; expected JavaDoc success.
- [ ] Run `rg -n "coco-base|coco\\.base|io\\.github\\.coco\\.base|CocoBase" -g "*.java" -g "*.xml" -g "*.yml" -g "*.properties" -g "*.md"`; expected no unintended base naming.
- [ ] Commit implementation with message `feat: add coco common i18n infrastructure`.
- [ ] Push `feature/coco-common-i18n` and check GitHub Actions.

## Self-Review

- Spec coverage: module wiring, public/internal boundary, i18n service, framework exception/message prompts, Spring Boot auto-configuration, metadata, sample, and full verification are covered.
- Scope check: request locale resolution, global exception handling, response translation, database messages, frontend export, and annotation i18n are out of scope.
- Type consistency: property namespace is `coco.common.i18n`; artifact is `coco-common-i18n`; public packages use `io.github.coco.common`.
