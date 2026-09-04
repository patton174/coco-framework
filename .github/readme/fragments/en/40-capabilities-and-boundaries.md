## What Coco Provides

<table>
  <tr>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Web-Servlet%20Runtime-2563eb?style=flat-square" alt="Web"/></p>
      <strong>Web Runtime</strong><br/>
      Unified responses, exception responses, trace headers, request context, access logs, request signatures, encryption, and replay protection.
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Security-Context%20Foundation-7c3aed?style=flat-square" alt="Security"/></p>
      <strong>Security Foundation</strong><br/>
      Principal context facade, resolver SPI, Web context bridge, trusted-header adapter, assertions, and propagation helpers.
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Data-MyBatis--Plus-0891b2?style=flat-square" alt="Data"/></p>
      <strong>Data Integration</strong><br/>
      MyBatis-Plus interceptor assembly, pagination, SQL guard, tenant SQL isolation, and data-permission predicates.
    </td>
  </tr>
  <tr>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Reliability-Flow%20Control-be123c?style=flat-square" alt="Reliability"/></p>
      <strong>Reliability</strong><br/>
      Rate limiting, idempotency, distributed locks, and scheduling — each with a process-local default and a replaceable store SPI.
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Platform-Storage%20%26%20Audit-16a34a?style=flat-square" alt="Platform"/></p>
      <strong>Platform</strong><br/>
      Object storage SPI with content-addressed local reference implementation, structured audit pipeline, and OpenAPI metadata.
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Config-Feature%20Control-f97316?style=flat-square" alt="Feature Control"/></p>
      <strong>Feature Control</strong><br/>
      Parent POM, BOM, one starter, declarative feature selection, dependency-aware plans, and runtime feature conditions.
    </td>
  </tr>
</table>

**→ [Capability reference](https://patton174.github.io/coco-framework/features/web-runtime)** — every feature, its config keys, and its SPI.

## Boundary

Coco owns **infrastructure**. Your application owns the **domain model, API semantics, authentication provider, and user/role/organization models**.

That line is deliberate: the framework does not guess your business, it only turns the repetitive, cross-project infrastructure into replaceable black boxes. Every SPI can be overridden with a single `@Bean`.

CRUD belongs to code generation, not runtime entity exposure — generated code is readable Java source your project keeps, edits, or deletes.

**→ [Boundary and design philosophy](https://patton174.github.io/coco-framework/overview)** — what each side is responsible for, and what stays out of scope.
