# Coco Framework Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the initial Maven multi-module skeleton for Coco Framework without implementing concrete pagination, audit, security, tenant, data-permission, OpenAPI, or codegen behavior.

**Architecture:** The root project is an internal Maven aggregator. `coco-parent` is the business-facing parent POM, `coco-bom` manages dependency versions, `coco-api` exposes stable user-facing contracts, `coco-common` aggregates reusable infrastructure, `coco-core` holds internal foundations, `coco-spring-boot-starter` is the single runtime dependency, `coco-features/coco-feature-registry` defines feature metadata, `coco-features/coco-feature-*` modules are empty implementation shells with `coco-feature-*` artifact ids, and `coco-maven-plugin` is a no-op build plugin placeholder.

**Tech Stack:** Java 17 target, Maven multi-module build, Spring Boot 4.1.0 dependency management, JUnit Jupiter, GitHub Actions, Maven Plugin API.

## Global Constraints

- First stage creates skeleton only; do not implement pagination, audit logging, security, tenant isolation, data permissions, OpenAPI integration, or code generation behavior.
- Business projects should use `coco-parent` plus one `coco-spring-boot-starter` dependency.
- Standard features are enabled by default unless disabled.
- Java config follows `CocoConfigurer`, similar to Spring `WebMvcConfigurer`.
- Avoid annotation noise; annotations are only for declarative business policy in later feature work.
- Static facade APIs are for runtime context access and manual enrichment in later feature work.
- Repository must be Maven Central friendly with license, SCM, source jar, javadoc jar, and reproducible-build defaults.

---

## File Structure

- Create root `pom.xml` as the internal aggregator and common build parent.
- Create `.gitignore`, `LICENSE`, `README.md`, `.gitattributes`, and `.github/workflows/ci.yml`.
- Create `coco-parent/pom.xml` for business projects.
- Create `coco-bom/pom.xml` for dependency management.
- Create `coco-api` Java interfaces and tests for disabled feature config.
- Create `coco-core` minimal module with a marker class.
- Create `coco-features/coco-feature-registry` metadata resolver and tests.
- Create `coco-spring-boot-starter` minimal auto-configuration.
- Create empty feature shell modules with marker classes.
- Create `coco-maven-plugin` no-op Mojo.
- Create `coco-test` placeholder test helper module.
- Create `coco-samples/coco-sample-basic` sample Spring Boot app.

### Task 1: Maven Foundation And Repository Metadata

**Files:**
- Create: `pom.xml`
- Create: `.gitignore`
- Create: `.gitattributes`
- Create: `LICENSE`
- Create: `README.md`
- Create: `.github/workflows/ci.yml`
- Create: `coco-parent/pom.xml`
- Create: `coco-bom/pom.xml`
- Create: module POMs for all skeleton modules

**Interfaces:**
- Produces: a reactor build that can compile all modules with `mvn verify`.
- Produces: Maven properties `java.version=17`, `spring-boot.version=4.1.0`, and `coco.revision=1.0.0-SNAPSHOT`.

- [ ] **Step 1: Create Maven root and module POMs**

Create the root aggregator, business parent, BOM, and module POMs with Java 17 target and Spring Boot 4.1.0 dependency management.

- [ ] **Step 2: Create repository metadata**

Create `.gitignore`, `.gitattributes`, Apache 2.0 `LICENSE`, basic `README.md`, and GitHub Actions CI using Temurin JDK 21 with Java 17 release compilation.

- [ ] **Step 3: Verify foundation build**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -q -DskipTests verify
```

Expected: build reaches module compilation. If code modules are still empty, failures should only come from missing source files introduced by later tasks.

### Task 2: Public API Contracts

**Files:**
- Create: `coco-api/src/main/java/io/github/coco/api/CocoConfigurer.java`
- Create: `coco-api/src/main/java/io/github/coco/api/feature/CocoFeature.java`
- Create: `coco-api/src/main/java/io/github/coco/api/feature/CocoFeatureRegistry.java`
- Create: `coco-api/src/main/java/io/github/coco/api/feature/DefaultCocoFeatureRegistry.java`
- Create: `coco-api/src/test/java/io/github/coco/api/feature/DefaultCocoFeatureRegistryTest.java`

**Interfaces:**
- Produces: `CocoConfigurer#configureFeatures(CocoFeatureRegistry features)`.
- Produces: `CocoFeatureRegistry#disable(CocoFeature... features)`.
- Produces: `CocoFeatureRegistry#isDisabled(CocoFeature feature)`.
- Produces: `CocoFeatureRegistry#disabledFeatures()`.

- [ ] **Step 1: Write failing tests for feature enable/disable API**

Write tests proving a registry starts empty, records disabled features, and ignores duplicate disabled declarations.

- [ ] **Step 2: Run API tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl coco-api test
```

Expected: compilation fails because the API types do not exist yet.

- [ ] **Step 3: Implement minimal API contracts**

Implement `CocoFeature`, `CocoFeatureRegistry`, `DefaultCocoFeatureRegistry`, and `CocoConfigurer`.

- [ ] **Step 4: Run API tests and verify GREEN**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl coco-api test
```

Expected: API tests pass.

### Task 3: Feature Registry Metadata

**Files:**
- Create: `coco-features/coco-feature-registry/src/main/java/io/github/coco/feature/registry/CocoFeatureDefinition.java`
- Create: `coco-features/coco-feature-registry/src/main/java/io/github/coco/feature/registry/StandardCocoFeatures.java`
- Create: `coco-features/coco-feature-registry/src/test/java/io/github/coco/feature/registry/StandardCocoFeaturesTest.java`

**Interfaces:**
- Consumes: `CocoFeature`.
- Produces: `StandardCocoFeatures.all()`.
- Produces: `StandardCocoFeatures.resolveEnabledFeatures(Set<CocoFeature> disabled)`.

- [ ] **Step 1: Write failing registry tests**

Write tests proving all eight standard features are registered, dependencies are declared, and disabling `MYBATIS_PLUS` also disables dependent features.

- [ ] **Step 2: Run registry tests and verify RED**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl :coco-feature-registry -am test
```

Expected: compilation fails because registry classes do not exist yet.

- [ ] **Step 3: Implement minimal registry metadata**

Implement immutable feature definitions and dependency-based disabled feature resolution.

- [ ] **Step 4: Run registry tests and verify GREEN**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -pl :coco-feature-registry -am test
```

Expected: registry tests pass.

### Task 4: Starter, Core, Feature Shells, Maven Plugin, And Sample

**Files:**
- Create: `coco-core/src/main/java/io/github/coco/core/CocoCore.java`
- Create: `coco-spring-boot-starter/src/main/java/io/github/coco/spring/boot/CocoAutoConfiguration.java`
- Create: `coco-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: marker classes in each `coco-feature-*` module.
- Create: `coco-maven-plugin/src/main/java/io/github/coco/maven/CocoFeaturesMojo.java`
- Create: `coco-test/src/main/java/io/github/coco/test/CocoTestSupport.java`
- Create: `coco-samples/coco-sample-basic/src/main/java/io/github/coco/sample/basic/CocoSampleBasicApplication.java`
- Create: `coco-samples/coco-sample-basic/src/main/java/io/github/coco/sample/basic/CocoConfig.java`

**Interfaces:**
- Consumes: `CocoConfigurer` and `CocoFeatureRegistry`.
- Produces: a no-op starter auto-configuration.
- Produces: a no-op Maven goal `coco:features`.

- [ ] **Step 1: Add minimal marker and no-op runtime classes**

Create marker classes only. Do not add concrete feature behavior.

- [ ] **Step 2: Add no-op Maven plugin goal**

Create `CocoFeaturesMojo` with `@Mojo(name = "features", threadSafe = true)`.

- [ ] **Step 3: Add sample app**

Create a sample Spring Boot application and a `CocoConfig implements CocoConfigurer` that disables tenant and data-permission.

- [ ] **Step 4: Verify reactor build**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn verify
```

Expected: full reactor build passes.

### Task 5: Final Verification And Commit

**Files:**
- Modify: all files created by prior tasks.

**Interfaces:**
- Consumes: all skeleton modules.
- Produces: committed framework skeleton.

- [ ] **Step 1: Run final verification**

Run:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn verify
git status --short
```

Expected: `mvn verify` exits 0 and git status shows only intended skeleton files.

- [ ] **Step 2: Commit skeleton**

Run:

```powershell
git add .
git commit -m "feat: scaffold coco framework skeleton"
```

Expected: commit succeeds.

## Self-Review

- Spec coverage: root Maven skeleton, parent, BOM, API, core, starter, feature registry, feature shells, Maven plugin, sample, CI, README, license, and Maven Central metadata are covered.
- Scope check: concrete pagination, logging, audit, security, tenant, data-permission, OpenAPI, and codegen behavior are out of scope for this plan.
- Placeholder scan: no task requires unbounded implementation work.
- Type consistency: API names used by registry and sample are defined in Task 2.
