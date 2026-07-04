# Coco Framework

Coco Framework is a Spring Boot based Java framework skeleton for rapidly building web applications with strong conventions and low boilerplate.

The current stage builds the repository structure only. Concrete feature behavior such as pagination, audit logging, security, tenant isolation, data permissions, OpenAPI integration, and code generation will be added in later iterations.

## Modules

- `coco-parent`: recommended parent POM for business projects.
- `coco-bom`: dependency management BOM.
- `coco-api`: public API aggregate modules.
- `coco-api-core`: core public API contracts under `coco-api/coco-api-core`.
- `coco-common`: common infrastructure aggregate modules.
- `coco-common-core`: common contracts and lightweight runtime utilities.
- `coco-common-i18n-api`: shared message code and message descriptor contracts.
- `coco-common-exception`: common exception contracts and assertion utilities.
- `coco-common-i18n`: i18n message and framework prompt infrastructure.
- `coco-core`: internal foundation aggregate modules.
- `coco-core-runtime`: internal runtime foundations under `coco-core/coco-core-runtime`.
- `coco-spring-boot-starter`: single starter dependency for business projects.
- `coco-features`: standard feature aggregate modules.
- `coco-feature-registry`: standard feature metadata artifact under `coco-features/coco-feature-registry`.
- `coco-feature-*`: feature implementation artifacts under `coco-features/coco-feature-*`.
- `coco-maven-plugin`: build-time feature assembly plugin shell.
- `coco-test`: shared test support shell.
- `coco-samples`: sample applications.

## Build

Use JDK 21 to build the project. The Maven compiler target is Java 17.

```powershell
$env:JAVA_HOME='D:\Programs\Java\jdk_21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn verify
```
