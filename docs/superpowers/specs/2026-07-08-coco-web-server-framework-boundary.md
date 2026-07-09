# Coco Web Server Framework Boundary Spec

## Goal

Coco Framework should help developers quickly build Spring Boot Web servers with production-grade infrastructure already wired.

The target experience is high-convention and low-boilerplate: business applications should inherit `coco-parent`, depend on `coco-spring-boot-starter`, write ordinary Spring business code, and receive consistent Web server behavior by default.

Coco may support SaaS applications well, but SaaS is not the only product shape. The core product is a general Web server rapid-development framework.

## Product Positioning

Coco is a black-box infrastructure framework, not a black-box business runtime.

It should hide repeated framework plumbing:

- feature selection and build-time pruning
- Spring Boot auto-configuration composition
- unified response shape
- unified exception and business code handling
- i18n message resolution
- trace id propagation
- request context capture
- access logging
- request signing
- replay protection
- request encryption
- audit event pipeline
- MyBatis-Plus pagination and SQL guard
- tenant SQL isolation
- data-permission SQL conditions
- OpenAPI metadata boundaries
- code generation extension points

It should not hide business ownership:

- business domain model
- controller and API semantics
- service orchestration
- transaction boundaries
- complex queries
- persistence design
- generated CRUD code after generation
- authentication and organization model choices unless a later optional module explicitly provides them

## Top-Level Encapsulation Versus Over-Encapsulation

Top-level encapsulation means Coco owns cross-cutting infrastructure that every Web server tends to repeat. The developer should not need to re-implement response wrapping, trace headers, error response formatting, request signature verification, or feature pruning for every application.

Over-encapsulation means Coco takes control of business behavior in a way that makes the application hard to understand or debug. Coco should avoid runtime magic that silently exposes entities as APIs, creates hidden CRUD controllers, or enforces one SaaS domain model on all projects.

The rule is:

```text
Infrastructure defaults are automatic.
Business code is explicit, generated, or user-owned.
```

## Desired Application Experience

Minimal application wiring:

```xml
<parent>
    <groupId>io.github.patton174</groupId>
    <artifactId>coco-parent</artifactId>
    <version>${coco.version}</version>
</parent>

<dependencies>
    <dependency>
        <groupId>io.github.patton174</groupId>
        <artifactId>coco-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

Feature selection remains declarative:

```yaml
coco:
  features:
    disabled:
      - mybatis-plus
      - tenant
      - data-permission
```

Or Java-based:

```java
@CocoFeatures(disabled = {
        CocoFeature.TENANT,
        CocoFeature.DATA_PERMISSION
})
@Configuration(proxyBeanMethods = false)
class ApplicationCocoConfiguration {
}
```

Business controllers should stay normal Spring code:

```java
@RestController
@RequestMapping("/orders")
class OrderController {

    @PostMapping
    OrderResponse create(@RequestBody CreateOrderRequest request) {
        return this.orderService.create(request);
    }
}
```

Coco should automatically provide the response envelope, trace headers, i18n exception messages, access logs, and configured request security.

## CRUD Direction

CRUD should be handled by code generation, not by runtime entity exposure.

Code generation should create readable Java source that the business project owns:

- controller
- request and response DTOs
- service or application service shell
- repository or mapper shell
- tests or test skeletons when practical
- OpenAPI metadata or annotations where appropriate

Generated code can follow Coco conventions, but after generation it should be normal project code. Developers can edit it, delete it, or choose not to generate it.

Runtime auto-CRUD is out of scope for the core framework because it hides API design and persistence decisions too aggressively.

## Current Implementation Mapping

Implemented or partially implemented foundations:

- `coco-parent` binds Spring Boot packaging, feature assembly, and package pruning.
- `coco-spring-boot-starter` provides the single dependency entry point.
- `coco-config` merges properties, `CocoConfigurer`, annotation selections, and build manifest state.
- `coco-maven-plugin` writes feature manifests and prunes disabled feature artifacts.
- `coco-feature-runtime` filters auto-configuration according to final feature state.
- `coco-common-context` provides trace and request context foundations.
- `coco-common-exception` provides business codes, error codes, and typed exceptions.
- `coco-common-i18n` provides message bundle registration and message resolution.
- `coco-common-logging` provides log handles, log manager, lifecycle logs, and access log recording.
- `coco-feature-web` provides response wrapping, exception handling, trace filter, request context, access log capture, HMAC signature, AES-GCM request encryption, and replay protection.
- `coco-feature-mybatis-plus` provides MyBatis-Plus interceptor assembly, pagination, and SQL guard configuration.
- `coco-feature-tenant` provides tenant context and tenant SQL isolation through MyBatis-Plus.
- `coco-feature-data-permission` provides data permission context, resource mapping, and SQL predicate generation through MyBatis-Plus.
- `coco-feature-audit` provides audit recorder SPI, publisher, failure policy, and access-log-to-audit adapter.
- `coco-feature-openapi` provides OpenAPI metadata provider boundaries.
- `coco-feature-codegen` provides a code generation SPI and configuration boundary.

Not implemented as core behavior:

- full user, role, organization, menu, or RBAC product model
- complete authentication provider
- generated CRUD templates
- runtime auto-CRUD
- complete admin console
- database-backed audit persistence
- full OpenAPI document generation integration
- production distributed replay store

## Boundary Decisions

### Core Framework

Core framework modules should own Web server infrastructure and cross-cutting runtime behavior. They should remain useful to generic Web applications, internal services, admin APIs, SaaS products, and integration servers.

### Optional Business Accelerators

Business accelerators may exist later as optional modules or codegen templates. Examples:

- admin system template
- SaaS tenant management template
- user and role template
- menu and permission template
- CRUD resource template
- import/export template

These should not be mandatory for all applications.

### Code Generation

Code generation is the preferred path for repetitive business scaffolding. Generated code must be explicit, readable, and conventional.

### SPI And Overrides

Framework defaults should back off when users provide their own beans. SPI boundaries should be small and intentional.

## Non-Goals

- Do not become a zero-code application platform.
- Do not require all projects to be SaaS systems.
- Do not force one database schema or user model.
- Do not expose entities as REST APIs by default.
- Do not make hidden runtime behavior more important than readable generated code.
- Do not bury core decisions in annotations that developers cannot reason about.

## Acceptance Criteria For Future Work

When adding a feature, it should satisfy these checks:

- Can a normal Spring Boot developer understand where the behavior comes from?
- Is the default useful without configuration?
- Can the behavior be disabled or replaced?
- Does it belong to Web server infrastructure rather than business-specific policy?
- If it is business scaffolding, should it be generated code instead of runtime magic?
- Does it preserve the one-starter application experience?
- Does it respect feature pruning and runtime feature conditions?
- Does it keep module dependency direction clean?
- Does it include focused tests and, when relevant, sample coverage?

## Roadmap Guidance

Short-term priority should be to harden the Web server foundation:

- make starter behavior predictable with disabled features
- keep request security filters well-tested
- keep unified response and exception behavior stable
- improve generated metadata and documentation
- make codegen produce explicit source templates before considering higher-level business modules

Medium-term work can add optional business accelerators, but those should remain separate from the generic Web server core.

