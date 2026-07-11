## What Coco Provides

<table>
  <tr>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Web-Servlet%20Runtime-2563eb?style=flat-square" alt="Web"/></p>
      <strong>Web Runtime</strong><br/>
      Unified responses, exception responses, trace headers, request context, access logs, request signatures, encryption, and process-local or shared JDBC replay protection.
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Security-Context%20Foundation-7c3aed?style=flat-square" alt="Security"/></p>
      <strong>Security Foundation</strong><br/>
      Principal context facade, resolver SPI, Web context bridge, trusted-header adapter, assertions, and propagation helpers.
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Data-MyBatis--Plus-0891b2?style=flat-square" alt="Data"/></p>
      <strong>Data Integration</strong><br/>
      MyBatis-Plus interceptor assembly, pagination, SQL guard, tenant SQL isolation, and data-permission SQL predicates.
    </td>
  </tr>
  <tr>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Config-Feature%20Control-f97316?style=flat-square" alt="Feature Control"/></p>
      <strong>Feature Control</strong><br/>
      Parent POM, <code>coco-dependencies</code> BOM, one starter, declarative feature selection, dependency-aware feature plans, and runtime feature conditions.
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Audit-Event%20Pipeline-16a34a?style=flat-square" alt="Audit"/></p>
      <strong>Audit Pipeline</strong><br/>
      Structured audit logging by default, plus formatter and recorder SPI, publisher, failure policy, and access-log adaptation.
    </td>
    <td width="33%">
      <p><img src="https://img.shields.io/badge/Build-Manifest%20%26%20Pruning-475569?style=flat-square" alt="Build Integrity"/></p>
      <strong>Build Integrity</strong><br/>
      One feature model drives dependency composition, the packaged manifest, runtime conditions, and pruning of disabled artifacts.
    </td>
  </tr>
</table>

## Boundary

<table>
  <thead>
    <tr>
      <th width="50%">Coco Encapsulates</th>
      <th width="50%">Application Owns</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Starter wiring and auto-configuration composition</td>
      <td>Domain model and API semantics</td>
    </tr>
    <tr>
      <td>Feature activation, dependency propagation, and runtime feature gating</td>
      <td>Controller shape and service orchestration</td>
    </tr>
    <tr>
      <td>Unified response, typed exceptions, i18n, trace context, and access logs</td>
      <td>Transaction boundaries and custom persistence decisions</td>
    </tr>
    <tr>
      <td>Request signatures, encryption, replay protection, security context lifecycle bridge, audit hooks, tenant SQL, and data-permission SQL</td>
      <td>Authentication provider, user model, organization model, role model, and generated CRUD code</td>
    </tr>
  </tbody>
</table>

Coco never exposes entities as runtime CRUD APIs. Application source remains owned by the business project; teams that need development-time scaffolding use the separate [coco-generate](https://github.com/patton174/coco-generate) project.

## Extension Boundaries

<table>
  <thead>
    <tr>
      <th width="25%">Area</th>
      <th width="38%">Delivered Boundary</th>
      <th width="37%">Application or Roadmap</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Replay</td>
      <td>Process-local default, explicit shared JDBC reference store, atomic key reservation, expiry cleanup, and replaceable store SPI.</td>
      <td>Database migration and availability, cluster clock synchronization, business transactions, and exactly-once semantics.</td>
    </tr>
    <tr>
      <td>Security</td>
      <td>Context facade, resolver SPI, Servlet context bridge, trusted-header adapter, assertions, and propagation primitives.</td>
      <td>Authentication provider, RBAC/ABAC model, sessions, tokens, and user storage.</td>
    </tr>
    <tr>
      <td>Audit</td>
      <td>Event contract, publisher, default best-effort structured logging, formatter and recorder SPI, failure policy, and access-log adapter.</td>
      <td>Database persistence, MQ delivery, compliance reports, and retention policy.</td>
    </tr>
    <tr>
      <td>OpenAPI</td>
      <td>Metadata provider, configuration boundary, and optional SpringDoc metadata customizer when SpringDoc is already on the application classpath.</td>
      <td>Document renderer, UI integration, and endpoint-specific documentation strategy.</td>
    </tr>
    <tr>
      <td>Source generation</td>
      <td>Outside the Framework runtime and Maven plugin; <a href="https://github.com/patton174/coco-generate">coco-generate</a> owns generator APIs and templates.</td>
      <td>Project-specific templates, business rules, review, and ongoing ownership of generated source.</td>
    </tr>
  </tbody>
</table>
