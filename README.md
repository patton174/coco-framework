<!-- Generated from .github/readme/manifest.json. Edit the source fragments, then run: node .github/readme/scripts/render.mjs --write -->

<h1><picture><source media="(prefers-color-scheme: dark)" srcset="website/static/img/brand/logo-primary-dark.svg"><img src="website/static/img/brand/logo-primary.svg" height="56" align="absmiddle" alt="Coco Framework logo"></picture>&nbsp; Coco Framework</h1>

**A Spring Boot starter for the boring parts of a web backend.** Add one dependency and stop rewriting response wrappers, exception handlers and request tracing.

[**Get started**](https://cocoframework.dev/en/getting-started) &nbsp;·&nbsp; [Documentation](https://cocoframework.dev/en/) &nbsp;·&nbsp; [Discussions](https://github.com/patton174/coco-framework/discussions) &nbsp;·&nbsp; [简体中文](README_CN.md)

![Build](https://github.com/patton174/coco-framework/actions/workflows/ci.yml/badge.svg) ![Release](https://img.shields.io/github/v/release/patton174/coco-framework) ![License](https://img.shields.io/badge/License-Apache--2.0-b4441f) ![Java](https://img.shields.io/badge/Java-17%2B-b4441f) ![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F) ![Maven](https://img.shields.io/badge/Maven-3.8.9%2B-C71A36)

---

## What is Coco

Most Spring Boot services end up with the same glue code. Coco ships it once, as a single starter:

- **Same response shape everywhere.** Controllers return plain objects; Coco wraps them.
- **Exceptions become clean responses.** One global handler, correct HTTP status codes.
- **Every request gets a TraceId.** It is read from or written to the `X-Trace-Id` header.
- **Common features are ready to switch on.** Rate limiting, idempotency, locks, cache, captcha, tenant isolation and more. Turn them on in YAML when you need them.
- **Your business code stays plain Spring.** Coco does not decide your user, role or permission model.

## Quick start

**1. Add the dependency.** Requires Java 17+, Maven 3.8.9+ and Spring Boot 4.1 (Boot 3.x is not supported). Use `coco-parent` as your parent POM, then add one starter.

```xml
<parent>
    <groupId>io.github.patton174</groupId>
    <artifactId>coco-parent</artifactId>
    <version>2.0.3</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>io.github.patton174</groupId>
        <artifactId>coco-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

**2. Write a normal controller.** Nothing Coco-specific in it.

```java
@RestController
@RequestMapping("/orders")
class OrderController {

    @GetMapping("/{id}")
    OrderResponse get(@PathVariable String id) {
        return orderService.get(id);
    }
}
```

**3. Call it.** The result is wrapped for you, and the response carries an `X-Trace-Id` header.

```json
{
  "success": true,
  "code": 200,
  "message": "Operation succeeded.",
  "data": { "id": "1", "amount": 100 }
}
```

If the controller throws, you get the same shape with `"success": false` and a matching HTTP status.

**4. Turn features on or off.** Some features, such as rate limiting, stay off until you set `coco.<feature>.enabled: true`. To keep a module out of the app completely, list it under `coco.features.disabled` (or use `@CocoFeatures`):

```yaml
coco:
  rate-limit:
    enabled: true      # opt in to a feature that is off by default
  features:
    disabled:          # leave these modules out completely
      - mybatis-plus
      - tenant
```

More: [Getting started](https://cocoframework.dev/en/getting-started) · [Feature toggles](https://cocoframework.dev/en/feature-toggles)

## Before production

Some defaults are chosen for a quick first run, not for a cluster. Check these three:

| Feature | Default | What to do |
|---|---|---|
| **SQL guard** | Off | Review your SQL first, then enable `block-attack` / `illegal-sql`. It may reject valid statements it cannot verify. [Docs](https://cocoframework.dev/en/features/mybatis-plus) |
| **Replay protection** | In-memory, one process only | With more than one instance, switch to the JDBC store or your own. Coco does not create tables; you manage the schema. [Docs](https://cocoframework.dev/en/features/request-security) |
| **Async logging** | Bounded queue; errors are always written synchronously | Plug a `CocoAsyncLogDropListener` into your metrics to see dropped logs. It reports overload, it does not guarantee delivery. [Docs](https://cocoframework.dev/en/features/infra) |

Also, only response wrapping, exception handling and TraceId work with zero setup. Most other features are opt-in, and some need Redis or a database. Each feature page lists its defaults.

## Features

| Area | What you get |
|---|---|
| Web | Unified response, global exceptions, TraceId, access log, CORS, security headers, field-level validation errors |
| Request security | Signature, encryption, replay protection |
| Data | MyBatis-Plus, pagination, tenant SQL isolation, data-permission filters |
| Traffic control | Rate limiting (sliding window, token bucket), idempotency, distributed locks, scheduling |
| Platform | File storage, local and Redis cache, messaging, notification channels, captcha (image, slider, SMS) |
| Observability | Audit events, OpenAPI metadata |

All of this ships in the latest release, **v2.0.3**.

## What Coco does not do

Coco handles infrastructure. Your application keeps:

- the domain model and API design
- login and authentication
- the user, role and organization model
- database schema and migrations

Every extension point is a normal Spring `@Bean`, so you can replace any default with your own. CRUD generation, when you want it, produces ordinary Java source that you own. It does not expose entities at runtime. More in [Overview](https://cocoframework.dev/en/overview).

## Tested end to end

Business and HTTP acceptance tests live in [`coco-admin/framework-acceptance`](https://github.com/patton174/coco-admin/tree/main/framework-acceptance):

- **Basic** (no database): responses, exceptions, i18n, trace, signature, encryption, replay protection.
- **Full** (H2 + MyBatis-Plus): security assertions, tenant SQL isolation, data-permission filtering, audit events.

## Related projects

| Project | What it is |
|---|---|
| [**Coco Framework**](https://github.com/patton174/coco-framework) | This repository: the Spring Boot starter |
| [**Coco Admin**](https://github.com/patton174/coco-admin) | An ERP product built on the framework |
| [**Coco Generate**](https://github.com/patton174/coco-generate) | Source code generator and templates |

Dependencies point one way: Admin uses Framework, and Framework never depends on the other two. Generated code belongs to your app and adds no runtime dependency.

## Community

Questions and ideas: [Discussions](https://github.com/patton174/coco-framework/discussions). Want to contribute: [CONTRIBUTING](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md). Found a vulnerability: [Security policy](https://github.com/patton174/coco-framework/security/policy).

<details>
<summary>Project stats and contributors</summary>

<br/>

<!-- COCO_STATS_START -->
<table>
  <tr>
    <td align="center"><strong>4</strong><br/>Stars</td>
    <td align="center"><strong>1</strong><br/>Forks</td>
    <td align="center"><strong>1</strong><br/>Contributors</td>
    <td align="center"><a href="https://github.com/patton174/coco-framework">Updated: 2026-10-19</a></td>
  </tr>
</table>
<!-- COCO_STATS_END -->

<!-- COCO_CONTRIBUTORS_START -->
<table>
  <tr>
    <td align="center">
      <a href="https://github.com/patton174">
        <img src="https://wsrv.nl/?url=github.com%2Fpatton174.png&w=96&h=96&fit=cover&mask=circle" width="48" height="48" alt="patton174"/><br/>
        <sub>patton174</sub>
      </a>
    </td>
  </tr>
</table>
<p><a href="https://github.com/patton174/coco-framework/graphs/contributors">View all contributors</a></p>
<!-- COCO_CONTRIBUTORS_END -->

</details>

## License

Apache License 2.0.
