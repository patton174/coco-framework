---
slug: /overview
sidebar_position: 1
title: Overview
description: "Coco Framework is a convention-heavy Spring Boot framework for web services: technology stack, capability scope and explicit boundaries in one page."
seo_title: "Overview: Spring Boot Web Service Framework"
---

# Coco Framework

<div className="doc-badges">

[![Build](https://github.com/patton174/coco-framework/actions/workflows/ci.yml/badge.svg)](https://github.com/patton174/coco-framework/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/patton174/coco-framework)](https://github.com/patton174/coco-framework/releases)
[![Stars](https://img.shields.io/github/stars/patton174/coco-framework?style=flat&color=b4441f)](https://github.com/patton174/coco-framework/stargazers)
[![License](https://img.shields.io/badge/License-Apache--2.0-b4441f)](https://github.com/patton174/coco-framework/blob/main/LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-b4441f)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F)](https://spring.io/projects/spring-boot)
[![Maven](https://img.shields.io/badge/Maven-3.8.9%2B-C71A36)](https://maven.apache.org/)

</div>

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

## Star history

<div className="doc-star-panel">
  <div className="doc-star-bar">
    <span className="doc-star-repo">patton174 / coco-framework</span>
    <a className="doc-star-cta" href="https://github.com/patton174/coco-framework/stargazers" target="_blank" rel="noopener noreferrer">★ Star on GitHub ↗</a>
  </div>
  <a className="doc-star-stage" href="https://www.star-history.com/?repos=patton174%2Fcoco-framework&type=date&legend=bottom-right" target="_blank" rel="noopener noreferrer">
    <img className="doc-theme-light" alt="Coco Framework Star History Chart" src="https://api.star-history.com/chart?repos=patton174/coco-framework&type=date&legend=bottom-right&sealed_token=WZtqAVEpmYHgLl3AUpfxFV4e_emJFt7fNK_ep9JrVVZ-tZvSoWbTwOEfvg8WIg0WEiosjWjZYSnF9DgC86cCiKp4iJ1uqirVm49z4-xECDHKRBogVqDokZF1cp6b00IInXU9FOcrhqR1nhcwP0t2KQhtRQAFe07t-K4PpUO7ERUjlhS6iRI1085j31pQ" loading="lazy" decoding="async" />
    <img className="doc-theme-dark" alt="Coco Framework Star History Chart" src="https://api.star-history.com/chart?repos=patton174/coco-framework&type=date&theme=dark&legend=bottom-right&sealed_token=WZtqAVEpmYHgLl3AUpfxFV4e_emJFt7fNK_ep9JrVVZ-tZvSoWbTwOEfvg8WIg0WEiosjWjZYSnF9DgC86cCiKp4iJ1uqirVm49z4-xECDHKRBogVqDokZF1cp6b00IInXU9FOcrhqR1nhcwP0t2KQhtRQAFe07t-K4PpUO7ERUjlhS6iRI1085j31pQ" loading="lazy" decoding="async" />
  </a>
  <div className="doc-star-note">
    <span>If Coco Framework is useful to you, consider starring the repository on GitHub.</span>
    <a href="https://www.star-history.com/?repos=patton174%2Fcoco-framework&type=date&legend=bottom-right" target="_blank" rel="noopener noreferrer">star-history.com ↗</a>
  </div>
</div>

## Next steps

- [Getting started](/getting-started) — integrate the framework in 5 minutes
- [Feature toggles](/feature-toggles) — declaratively toggle features on and off
- [Feature overview](/features/web-runtime) — usage and integration for each feature
