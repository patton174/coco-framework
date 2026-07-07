# Coco Config Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated `coco-config` module that merges `coco.features.disabled` from Spring Boot configuration and `CocoConfigurer` beans.

**Architecture:** `coco-config` owns configuration binding, disabled feature aggregation, and the runtime `CocoFeatureManager`. `coco-api` keeps the user-facing contracts. `coco-spring-boot-starter` depends on `coco-config` so business projects get configuration support through the existing single starter.

**Tech Stack:** Java 17, Maven multi-module build, Spring Boot 4.1.0, Spring Boot auto-configuration, JUnit Jupiter, `ApplicationContextRunner`.

## Global Constraints

- Add `coco-config` as a separate module to avoid growing `coco-core` or `coco-spring-boot-starter`.
- Do not implement pagination, audit logging, security, tenant isolation, data permissions, OpenAPI integration, code generation behavior, or Maven package pruning in this task.
- Use standard JavaDoc HTML tags for class comments.
- Keep public configuration style compatible with `CocoConfigurer`.

---

### Task 1: Add Module Wiring

**Files:**
- Modify: `pom.xml`
- Modify: `coco-bom/pom.xml`
- Modify: `coco-spring-boot-starter/pom.xml`
- Create: `coco-config/pom.xml`

**Interfaces:**
- Produces: Maven module `io.github.patton174:coco-config`.
- Produces: starter dependency on `coco-config`.

- [ ] Add `coco-config` to the root reactor before `coco-spring-boot-starter`.
- [ ] Add `coco-config` to BOM dependency management.
- [ ] Create `coco-config/pom.xml` with dependencies on `coco-api`, `coco-feature-registry`, `spring-boot`, `spring-boot-autoconfigure`, and test dependencies.
- [ ] Change `coco-spring-boot-starter` to depend on `coco-config`.
- [ ] Run `mvn -pl coco-config -am test`; expected RED until tests/classes are added.

### Task 2: Configuration Model And Manager

**Files:**
- Create: `coco-config/src/test/java/io/github/coco/config/CocoFeatureManagerTest.java`
- Create: `coco-config/src/main/java/io/github/coco/config/CocoProperties.java`
- Create: `coco-config/src/main/java/io/github/coco/config/CocoFeatureProperties.java`
- Create: `coco-config/src/main/java/io/github/coco/config/CocoFeatureManager.java`
- Create: `coco-config/src/main/java/io/github/coco/config/DefaultCocoFeatureManager.java`

**Interfaces:**
- Produces: `CocoFeatureManager#isEnabled(CocoFeature feature)`.
- Produces: `CocoFeatureManager#enabledFeatures()`.
- Produces: `CocoFeatureManager#disabledFeatures()`.

- [ ] Write failing tests for default all-enabled behavior and dependent disabled-feature propagation.
- [ ] Implement properties and manager classes.
- [ ] Run `mvn -pl coco-config -am test`; expected GREEN.

### Task 3: Spring Boot Auto-Configuration

**Files:**
- Create: `coco-config/src/test/java/io/github/coco/config/CocoConfigAutoConfigurationTest.java`
- Create: `coco-config/src/main/java/io/github/coco/config/CocoConfigAutoConfiguration.java`
- Create: `coco-config/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Interfaces:**
- Produces: Boot auto-configuration for `CocoProperties` and `CocoFeatureManager`.
- Consumes: optional `CocoConfigurer` beans.

- [ ] Write failing `ApplicationContextRunner` tests for YAML/property disabled features and `CocoConfigurer` disabled features.
- [ ] Implement auto-configuration.
- [ ] Run `mvn -pl coco-config -am test`; expected GREEN.

### Task 4: Sample And Full Verification

**Files:**
- Create: `coco-samples/coco-sample-basic/src/main/resources/application.yml`

**Interfaces:**
- Produces: sample showing configuration file based disabled features.

- [ ] Add sample `application.yml` using `coco.features.disabled`.
- [ ] Run `mvn -q verify`; expected full reactor success.
- [ ] Run `mvn -q -DskipTests install javadoc:javadoc`; expected JavaDoc success.
- [ ] Commit and push the feature branch.

## Self-Review

- Spec coverage: separate module, YAML binding, `CocoConfigurer` merging, feature manager, starter integration, and sample coverage are included.
- Scope check: concrete business features and Maven packaging pruning are out of scope.
- Placeholder scan: no task contains open-ended implementation placeholders.
