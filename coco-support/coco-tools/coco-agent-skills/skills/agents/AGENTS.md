# Coco Framework — agent guide

Coco is a high-convention framework for Spring Boot Web services (Java 17+, Spring Boot 4.1, group `io.github.patton174`). It provides replaceable black-box infrastructure — unified responses, global exception handling, TraceId, multi-tenancy, data permission, rate limiting, idempotency, distributed lock, object storage, audit, OpenAPI, code generation — while business code stays plain Spring.

Docs: https://patton174.github.io/coco-framework/

## Install

Use `coco-parent` as parent POM and add the starter:

```xml
<parent>
  <groupId>io.github.patton174</groupId>
  <artifactId>coco-parent</artifactId>
  <version>${coco.version}</version>
  <relativePath/>
</parent>
<dependency>
  <groupId>io.github.patton174</groupId>
  <artifactId>coco-spring-boot-starter</artifactId>
</dependency>
```

Alternative: import the BOM `io.github.patton174:coco-dependencies` (`type: pom`, `scope: import`). Resolve `${coco.version}` to the latest release via the `coco_get_latest_version` MCP tool — do not hardcode.

## Feature toggles

Disable modules declaratively in `application.yml` (`coco.features.disabled`) or via `@CocoFeatures(disabled = { ... })`. Disabling a depended-on feature cascades to its dependents.

```yaml
coco:
  features:
    disabled: [mybatis-plus, tenant]
```

Feature ids: `web`, `mybatis-plus`, `audit`, `security`, `tenant`, `data-permission`, `openapi`, `rate-limit`, `idempotency`, `scheduling`, `lock`, `storage`, `codegen`. `coco.features.disabled` controls assembly; per-module `coco.<x>.enabled` controls runtime behavior.

## MCP tools (server `coco`)

- `coco_search_docs { query, topK? }` — semantic search over Coco docs; returns ranked snippets + doc-site URLs.
- `coco_get_doc { docPath }` — full text of one doc page (e.g. `features/tenant`).
- `coco_list_docs` — list every doc page (path, title, URL).
- `coco_dependency_snippet { style?, version? }` — Maven/Gradle snippet with latest version auto-filled (style: parent | bom | gradle).
- `coco_get_latest_version` — latest released version from Maven Central.
- `coco_check_version { current }` — whether an update exists.
- `coco_index_status` — doc-index freshness / rebuild recommendation.

Register the server via `.mcp.json` at the project root (the cross-agent standard picked up by Tongyi Lingma, Trae, CodeBuddy, and others):

```json
{ "mcpServers": { "coco": { "command": "node", "args": ["<path>/coco-agent-skills/src/mcp-server.mjs"] } } }
```

## Conventions

- Controllers stay ordinary Spring; return values auto-wrap to `{ success, code, message, data }`.
- Infrastructure is automated; the app owns its domain model, auth provider, and user/role/org model.
- Every SPI is overridable with a single `@Bean`.
- Before answering a Coco configuration question, call `coco_search_docs` — do not invent property names.
