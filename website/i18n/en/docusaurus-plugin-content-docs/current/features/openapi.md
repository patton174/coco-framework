---
title: OpenAPI Metadata
---

# OpenAPI Metadata

Coco OpenAPI metadata (`coco-feature-openapi`) provides a framework-level, rendering-library-agnostic contract for the basic information of API documentation, and automatically writes this metadata into SpringDoc's `OpenAPI.info` when a business project introduces SpringDoc. It is responsible only for the "documentation basic information" layer; it does not generate an endpoint inventory and does not take over route scanning. The module binds the `coco.openapi` namespace, is enabled by default, participates in auto-configuration as a Coco Feature (`CocoFeature.OPENAPI`), and loads after the Web and Security auto-configurations.

## Overview

- **`CocoOpenApiMetadata`**: an immutable record carrying framework-level API documentation basic information, with the fields `title` / `version` / `description`, not bound to any specific OpenAPI rendering library. It is normalized at construction: an empty `title` falls back to `Coco API`, an empty `version` falls back to `1.0.0`, and an empty `description` becomes `null` (exposed as an `Optional` via `descriptionOptional()`).
- **`CocoOpenApiMetadataProvider`**: the metadata provider SPI, through which documentation rendering implementations obtain the framework's unified basic information. The default implementation `DefaultCocoOpenApiMetadataProvider` reads the configuration properties directly.
- **SpringDoc adapter**: when SpringDoc-related classes exist on the classpath, it registers `cocoSpringDocOpenApiCustomizer` to write the Coco metadata into the SpringDoc documentation information.

## How to Enable

The module is enabled by default. To configure only the metadata, just fill in the documentation information in the configuration:

```yaml
coco:
  openapi:
    enabled: true
    info:
      title: 订单服务 API
      version: 2.1.0
      description: 订单域对外接口文档
    springdoc:
      enabled: true
```

The framework depends on Web and Security: the OpenAPI auto-configuration is declared to load after `CocoWebAutoConfiguration` and `CocoSecurityAutoConfiguration`, so that the documentation information stays consistent with the runtime state of these two layers.

For the metadata to actually be reflected in the rendered OpenAPI documentation, the business project needs to introduce the SpringDoc dependency itself. When the classpath has SpringDoc's `OpenApiCustomizer`, `OpenAPI`, `Info`, and other classes, and `coco.openapi.springdoc.enabled=true` (the default), the framework automatically registers `cocoSpringDocOpenApiCustomizer` to write the title, version, and description of `CocoOpenApiMetadata` into SpringDoc's `OpenAPI.info`.

## Usage Example

In scenarios where you need to read the framework's unified metadata directly (such as a self-built documentation page or exposing the version number in a health check), inject `CocoOpenApiMetadataProvider`:

```java
@RestController
public class ApiInfoController {

    private final CocoOpenApiMetadataProvider metadataProvider;

    public ApiInfoController(CocoOpenApiMetadataProvider metadataProvider) {
        this.metadataProvider = metadataProvider;
    }

    @GetMapping("/api-info")
    public Map<String, String> apiInfo() {
        CocoOpenApiMetadata metadata = metadataProvider.metadata();
        Map<String, String> info = new LinkedHashMap<>();
        info.put("title", metadata.title());
        info.put("version", metadata.version());
        metadata.descriptionOptional().ifPresent(desc -> info.put("description", desc));
        return info;
    }
}
```

To replace the default metadata source (for example, fetching it dynamically from a configuration center or a database), just declare your own `CocoOpenApiMetadataProvider` Bean, and `@ConditionalOnMissingBean` will let the default implementation step aside automatically.

## Key Configuration Items

Binding prefix `coco.openapi` (corresponding to `CocoOpenApiProperties`):

| Configuration item | Type | Default | Description |
| --- | --- | --- | --- |
| `coco.openapi.enabled` | `boolean` | `true` | Whether to enable the OpenAPI metadata infrastructure |
| `coco.openapi.info.title` | `String` | `Coco API` | Documentation title |
| `coco.openapi.info.version` | `String` | `1.0.0` | Documentation version |
| `coco.openapi.info.description` | `String` | `Coco Framework API` | Documentation description |
| `coco.openapi.springdoc.enabled` | `boolean` | `true` | Whether to automatically write the Coco metadata into the SpringDoc documentation information |

## Boundary Considerations

- This module only provides the "documentation basic information" contract; it does not generate API paths, parameters, or Schemas — the endpoint inventory is obtained by SpringDoc scanning the controllers itself.
- The SpringDoc adapter is registered only when SpringDoc-related classes exist on the classpath; when SpringDoc is not introduced, the metadata can still be read through `CocoOpenApiMetadataProvider`, but it will not be reflected in the rendered documentation.
- The metadata fields are normalized: passing an empty `title` or `version` falls back to the default value, so you cannot clear them to empty strings through configuration.
