---
slug: /overview
sidebar_position: 1
title: Overview
---

# Coco Framework

**A convention-heavy rapid development framework for Spring Boot web services, built for shipping production-ready Java services.**

Coco Framework helps teams stand up Spring Boot web services quickly: the framework provides convention-heavy, replaceable black-box infrastructure, while the business side keeps using the ordinary Java / Spring programming model.

It fits SaaS systems, internal services, admin backends, integration services, and general-purpose web APIs. It is **not** a zero-code business runtime, and it does not force every project onto a single shared model of users, roles, menus, organizations, or tenants.

:::tip[Design philosophy]
Infrastructure is automated by default; business code stays explicit, generatable, and owned by you.
:::

## Technology stack

| Dimension | Version |
|------|------|
| Java | 17+ |
| Spring Boot | 4.1 |
| MyBatis-Plus | 3.5.x |
| Build | Maven (`coco-parent` parent POM) |
| License | Apache 2.0 |

## Capability scope

The framework provides the following pluggable capabilities, each of which can be toggled on or off via a feature toggle:

- **Web runtime** — unified response wrapping, global exception handling, TraceId propagation, request body caching, field-level parameter validation errors.
- **Request security** — request decryption (AES-GCM), signature verification (HMAC-SHA256), replay protection.
- **Security context** — security context bridging, security response headers (CSP/HSTS/nosniff, etc.), CORS.
- **Data integration** — MyBatis-Plus interceptor orchestration, pagination and sorting, optional SQL protection.
- **Multi-tenancy and data permission** — SQL-rewrite-based tenant isolation and data permission filtering.
- **Flow control and reliability** — rate limiting, idempotency, distributed locks, dynamic scheduled tasks.
- **Platform capabilities** — object storage, audit pipeline, OpenAPI metadata, code generation.

## Boundaries

The framework owns the **infrastructure**; the business application owns the **domain model, API semantics, authentication providers, and user/role/organization models**.

This boundary is deliberate: the framework does not guess at your business, it only turns the repetitive, cross-project-consistent infrastructure into replaceable black boxes. Every SPI can be overridden with your own implementation through a single `@Bean`.

## Next steps

- [Getting started](/getting-started) — integrate the framework in 5 minutes
- [Feature toggles](/feature-toggles) — declaratively toggle features on and off
- [Feature overview](/features/web-runtime) — usage and integration for each feature
