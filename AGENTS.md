# Coco Framework Agent Guide

## Project Positioning

Coco Framework is a high-convention Spring Boot Web server framework for rapidly building production-ready Web services.

It is not limited to SaaS systems, and it is not a zero-code business runtime. The framework should provide a strong black-box foundation for repeated infrastructure concerns while leaving business code, domain design, generated CRUD code, custom queries, and transaction boundaries under developer control.

## Core Principles

- Prefer top-level encapsulation for repeated Web server infrastructure: starter wiring, feature activation, build-time pruning, runtime conditions, unified responses, exceptions, i18n, trace context, access logs, request security, audit pipeline, MyBatis-Plus integration, tenant SQL isolation, and data-permission SQL integration.
- Avoid over-encapsulation of business behavior: do not hide CRUD behind runtime magic, do not expose entities automatically as APIs, and do not force one user/role/menu/tenant model onto every application.
- CRUD and standard business scaffolding should be generated as readable source code through code generation, then owned by the business project.
- Annotations should express clear business or framework intent. They must not become opaque switches that make behavior hard to debug.
- Defaults should be useful and safe, but every major integration point should remain configurable or replaceable through properties, beans, or SPI.
- Business developers are expected to understand normal Java, Spring Boot, Maven, and the generated code they keep. Coco should reduce boilerplate, not remove engineering ownership.

## Current Architecture

- `coco-parent` is the recommended parent POM for business applications. It imports the BOM, runs Spring Boot repackage, runs `coco:features`, and runs `coco:prune-package`.
- `coco-spring-boot-starter` is the single normal starter dependency for applications. It brings common infrastructure and standard feature modules.
- `coco-api-core` contains stable public contracts such as `CocoConfigurer`, `CocoFeature`, `CocoFeatureRegistry`, and `@CocoFeatures`.
- `coco-common-*` modules contain reusable infrastructure: context, exception contracts, i18n, and logging.
- `coco-config` binds `coco.*` configuration and computes the final runtime feature plan.
- `coco-feature-registry` owns standard feature metadata, dependencies, manifest model, and feature resolution.
- `coco-feature-runtime` filters auto-configuration by feature state.
- `coco-feature-web` owns Servlet Web integration: response wrapping, exception response handling, trace, request context, access logging, signatures, encryption, and replay protection.
- `coco-feature-mybatis-plus` owns MyBatis-Plus interceptors, pagination, and SQL guard integration.
- `coco-feature-tenant` owns tenant context and MyBatis-Plus tenant SQL isolation.
- `coco-feature-data-permission` owns data permission context, resource mapping, and MyBatis-Plus data-permission SQL conditions.
- `coco-feature-audit`, `coco-feature-openapi`, and `coco-feature-codegen` currently provide extension boundaries and initial SPI/metadata capabilities.
- `coco-maven-plugin` creates `META-INF/coco/features.json`, applies enabled feature dependencies, and prunes disabled feature artifacts from Spring Boot packages.

## Development Rules

- If `.codegraph/` exists, use CodeGraph before grep/find or broad file reads when understanding or locating code.
- After source code changes, run `codegraph sync .` automatically so the local code graph stays current before follow-up exploration.
- Keep changes scoped to the requested feature, module, or document.
- Match existing Maven module boundaries and Java package names.
- Keep public APIs small and stable. Add SPI only when there is a real replacement point.
- Preserve business-facing simplicity: one starter, declarative configuration, clear override points.
- Prefer generated source code for business CRUD over runtime dynamic controllers.
- Avoid adding feature behavior to `coco-spring-boot-starter`; starters should compose capabilities, not own behavior.
- Do not make common modules depend on concrete feature modules.
- Use standard JavaDoc HTML tags for public classes. Existing project docs and JavaDoc use Chinese descriptions; continue that style where appropriate.
- Keep message text in module message bundles instead of hard-coding user-visible text.
- Do not introduce unrelated refactors, formatting churn, or generated build artifacts into commits.

## Verification

Use JDK 21. The Maven compiler target is Java 17.

Common commands:

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn verify
```

Focused module verification:

```powershell
mvn -pl :module-artifact-id -am test
```

Sample verification:

```powershell
mvn -B install
mvn -B -f coco-samples/coco-sample-basic/pom.xml verify
python coco-samples/coco-sample-basic/scripts/verify_business_flow.py
```

Release profile smoke check without local GPG:

```powershell
mvn -B -Prelease -Drevision=1.0.0 -Dgpg.skip=true -DskipTests verify
```

## Git Workflow

- Work on `codex-dev-*` or focused feature branches, then merge through PR into `main`.
- Keep `main` release-ready.
- Use SemVer release tags such as `v1.0.2`.
- Fetch uses HTTPS remote; push is configured to SSH.
- Before reporting completion, check `git status -sb` and mention any untracked or ignored output only if it matters.
