---
name: coco-framework
description: Use the Coco Framework (io.github.patton174) in a Spring Boot Web service — add the starter, toggle features, and look up how each capability is configured. Trigger when a user works on a Java/Spring Boot service that uses or should use Coco, or asks how to enable a Coco feature (idempotency, tenant, rate-limit, storage, audit, lock, openapi, codegen, etc.).
---

# Coco Framework

Coco is a high-convention framework for Spring Boot Web services (Java 17+, Spring Boot 4.1). It ships replaceable "black-box" infrastructure — unified responses, global exception handling, TraceId, multi-tenancy, data permission, rate limiting, idempotency, distributed lock, object storage, audit, OpenAPI, code generation — while your business code stays plain Spring.

Docs site: https://patton174.github.io/coco-framework/

## Quick install

Business apps use `coco-parent` as the parent POM and pull one starter:

```xml
<parent>
  <groupId>io.github.patton174</groupId>
  <artifactId>coco-parent</artifactId>
  <version>${coco.version}</version>
  <relativePath/>
</parent>

<dependencies>
  <dependency>
    <groupId>io.github.patton174</groupId>
    <artifactId>coco-spring-boot-starter</artifactId>
  </dependency>
</dependencies>
```

Prefer not to inherit `coco-parent`? Import the BOM `io.github.patton174:coco-dependencies` (type `pom`, scope `import`) under `dependencyManagement` instead.

Always resolve `${coco.version}` to the newest release — use the `coco_get_latest_version` MCP tool rather than guessing.

## Feature toggles

Everything is on/off via stable `CocoFeature` ids. Disable declaratively in `application.yml`:

```yaml
coco:
  features:
    disabled:
      - mybatis-plus   # cascades: also disables tenant, data-permission, codegen
      - tenant
```

Or via `@CocoFeatures(disabled = { CocoFeature.TENANT })` on a `@Configuration`. Disabling a depended-on feature cascades to its dependents. Feature ids: `web`, `mybatis-plus`, `audit`, `security`, `tenant`, `data-permission`, `openapi`, `rate-limit`, `idempotency`, `scheduling`, `lock`, `storage`, `codegen`.

Two distinct layers: `coco.features.disabled` decides whether a module is *assembled*; per-module `coco.<x>.enabled` decides whether an assembled module *acts* (e.g. `coco.idempotency.enabled: true`).

## MCP tools available

This skill is paired with the `coco-agent-skills` MCP server (name `coco`):

- `coco_search_docs { query, topK? }` — semantic search over all Coco docs; returns ranked snippets + doc-site URLs. Use it to answer "how do I configure X".
- `coco_get_doc { docPath }` — full text of one doc page (e.g. `features/idempotency`). Use after a search when you need the whole page, not a snippet.
- `coco_list_docs` — list every doc page (path, title, URL) to discover what exists.
- `coco_dependency_snippet { style?, version? }` — ready-to-paste Maven/Gradle snippet with the latest version auto-filled. style: `parent` | `bom` | `gradle`.
- `coco_get_latest_version` — latest released version from Maven Central.
- `coco_check_version { current }` — whether an update exists for the version in use.
- `coco_index_status` — doc-index freshness (chunk count, age, whether a rebuild is recommended).

Workflow: `coco_search_docs` to locate → `coco_get_doc` for the full page → `coco_dependency_snippet` to wire up the build. Never hand-write the version — resolve it via the tools.

## Key conventions

- Controllers stay ordinary Spring; return values are auto-wrapped as `{ success, code, message, data }`.
- Infrastructure is automated; domain model, auth provider, and user/role/org model stay owned by the app.
- Every SPI can be overridden with a single `@Bean`.
- Before answering a config question, call `coco_search_docs` — do not invent property names.
