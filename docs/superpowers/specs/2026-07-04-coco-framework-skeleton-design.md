# Coco Framework Skeleton Design

## Goal

Coco Framework is a Spring Boot based Java framework for rapidly building web applications with strong conventions and minimal business boilerplate.

The first implementation stage only builds the repository skeleton, Maven module layout, publishing foundation, GitHub Actions CI, and extension boundaries. It does not implement pagination, audit logging, security, tenant isolation, data permissions, OpenAPI integration, or code generation behavior yet.

## Product Experience

Business projects should use Coco with a clean entry point:

```xml
<parent>
    <groupId>io.github.patton174</groupId>
    <artifactId>coco-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>

<dependencies>
    <dependency>
        <groupId>io.github.patton174</groupId>
        <artifactId>coco-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

All standard framework abilities are enabled by default. Business projects only declare disabled features or custom parameters.

Configuration file style:

```yaml
coco:
  features:
    disabled:
      - tenant
      - data-permission
```

Configuration class style:

```java
@Configuration(proxyBeanMethods = false)
public class CocoConfig implements CocoConfigurer {

    @Override
    public void configureFeatures(CocoFeatureRegistry features) {
        features.disable(
                CocoFeature.TENANT,
                CocoFeature.DATA_PERMISSION
        );
    }
}
```

The configuration class follows the Spring `WebMvcConfigurer` style. It should be type-safe and natural for business developers.

## Repository Layout

The framework repository is a Maven multi-module project:

```text
coco-framework
+-- coco-parent
+-- coco-bom
+-- coco-api
+-- coco-common
+   +-- coco-common-i18n
+-- coco-core
+-- coco-spring-boot-starter
+-- coco-features
+   +-- coco-feature-registry
+   +-- coco-feature-web
+   +-- coco-feature-mybatis-plus
+   +-- coco-feature-audit
+   +-- coco-feature-security
+   +-- coco-feature-tenant
+   +-- coco-feature-data-permission
+   +-- coco-feature-openapi
+   +-- coco-feature-codegen
+-- coco-maven-plugin
+-- coco-test
+-- coco-samples
```

## Module Responsibilities

### coco-parent

Provides the recommended parent POM for business projects. The skeleton stage configures compiler settings, testing defaults, Spring Boot packaging defaults, and a managed `coco-maven-plugin` entry.

### coco-bom

Provides dependency management for Coco modules and third-party libraries. Projects that cannot use `coco-parent` can import this BOM.

### coco-api

Contains public APIs used directly by business code:

- `CocoConfigurer`
- `CocoFeature`
- `CocoFeatureRegistry`
- feature configuration interfaces
- public annotations that declare business policy
- public static facade APIs

This module must stay small, stable, and light on dependencies.

### coco-common

Aggregates reusable framework infrastructure modules. The first child module is `coco-common-i18n`, which owns framework message resolution, framework prompts, and common exception message codes.

### coco-core

Contains internal foundation code:

- common exception model
- common response model
- context abstractions
- SPI support
- reflection and bytecode utilities
- configuration binding utilities
- feature metadata model

It must not depend on concrete Web, MyBatis-Plus, Security, or OpenAPI implementations.

### coco-spring-boot-starter

The only normal dependency business projects need to declare. It brings the public API and core runtime entry points, but it does not contain complete feature implementations.

### coco-features

Aggregates standard feature modules under one physical directory. Each child directory keeps its publishable `coco-feature-*` artifact id.

### coco-feature-registry

Defines standard feature metadata:

- feature id
- Maven coordinates
- default enabled state
- dependencies
- conflicts
- load order
- required third-party conditions

`coco-maven-plugin` uses this registry during build packaging.

### coco-maven-plugin

Build-time assembly plugin. Its target behavior is:

- read `application.yml`, `application.yaml`, and `application.properties`
- inspect compiled configuration classes that implement `CocoConfigurer`
- resolve disabled features
- assemble only enabled `coco-feature-*` modules into the final package
- generate `target/coco/features-report.json`

The first skeleton stage creates the module and a minimal no-op goal structure so the project compiles before feature assembly is implemented.

### coco-feature-*

Feature implementation modules. They are not implemented in the first stage. They exist as isolated module shells so later work can add behavior one capability at a time.

## API Boundary Rules

Coco should avoid annotation noise.

Annotations are only for declarative business policy tied to a class, method, field, or entity. Examples for later stages include pagination policy, operation logging, data audit, permission checks, tenant ignore rules, and data scopes.

Static facade APIs are for runtime context access and manual enrichment. Examples for later stages include current user, current tenant, trace id, audit tags, and response helpers.

Configuration files and `CocoConfigurer` implementations are for global defaults and feature parameters.

Automatic mechanisms should not require annotations when the framework can safely infer behavior.

## Standard Feature Set

Standard features planned for later implementation:

- `web`
- `mybatis-plus`
- `audit`
- `security`
- `tenant`
- `data-permission`
- `openapi`
- `codegen`

All standard features are enabled by default unless disabled.

Planned dependencies:

- `audit` is independent; its access-log adapter composes with Web when present, and future database audit adapters must not make MyBatis-Plus a core dependency
- `tenant` depends on `mybatis-plus` and `security`
- `data-permission` depends on `mybatis-plus` and `security`
- `openapi` depends on `web` and `security`
- `codegen` depends on `mybatis-plus`

If a base feature is disabled, dependent features are disabled automatically.

## First Implementation Scope

The first implementation stage should create:

- Maven multi-module skeleton
- root aggregator POM
- `coco-parent`
- `coco-bom`
- `coco-api`
- `coco-core`
- `coco-spring-boot-starter`
- `coco-feature-registry`
- empty feature modules
- `coco-maven-plugin` minimal no-op goal
- `coco-test`
- `coco-samples`
- GitHub Actions CI for build and tests
- basic README
- license and Maven Central friendly project metadata

The first implementation stage should not create concrete feature behavior.

## Maven Central And GitHub Requirements

The repository must be suitable for publishing to Maven Central:

- provisional `groupId` that can be renamed before first public release
- license metadata
- developer metadata using project-level default values
- SCM metadata using project-level default values
- source and javadoc jar configuration
- reproducible build-friendly settings

GitHub Actions should initially run validation on push and pull request:

- checkout
- set up JDK
- cache Maven dependencies
- run Maven verify

Release automation and signing can be added after the skeleton compiles cleanly and coordinates are finalized.

## Release Decisions Not Required For Skeleton

Before the first public Maven Central release, the following must be finalized:

- final Maven `groupId`
- Java baseline version
- Spring Boot baseline version
- license
- GitHub repository URL
- Maven Central publishing account and signing setup
