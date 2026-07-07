# Coco Framework

Coco Framework is a Spring Boot based Java framework skeleton for rapidly building web applications with strong conventions and low boilerplate.

The current stage builds the repository structure only. Concrete feature behavior such as pagination, audit logging, security, tenant isolation, data permissions, OpenAPI integration, and code generation will be added in later iterations.

## Modules

- `coco-parent`: recommended parent POM for business projects.
- `coco-bom`: dependency management BOM.
- `coco-api`: public API aggregate modules.
- `coco-api-core`: core public API contracts under `coco-api/coco-api-core`.
- `coco-common`: common infrastructure aggregate modules.
- `coco-common-context`: request context and TraceId infrastructure.
- `coco-common-exception`: common exception contracts and assertion utilities.
- `coco-common-i18n`: i18n message infrastructure; public contracts live under its `api` package.
- `coco-spring-boot-starter`: single starter dependency for business projects.
- `coco-features`: standard feature aggregate modules.
- `coco-feature-registry`: standard feature metadata artifact under `coco-features/coco-feature-registry`.
- `coco-feature-runtime`: runtime feature condition support under `coco-features/coco-feature-runtime`.
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

## Coco Log Renderer

Use the optional Node.js renderer when you want a more compact local terminal view for Coco logs.

```powershell
mvn -f coco-samples/coco-sample-basic/pom.xml spring-boot:run | node tools/coco-log-renderer/bin/coco-log-renderer.mjs
```

When an application is started with `java -jar`, Coco also tries to start the packaged Node.js renderer automatically.
If Node.js is unavailable, Coco keeps the normal JVM console output.

```yaml
coco:
  logging:
    node-renderer:
      enabled: true
      jar-only: true
      command: node
      color: always
```
