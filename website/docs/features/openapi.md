---
title: OpenAPI 元数据
---

# OpenAPI 元数据

Coco OpenAPI 元数据（`coco-feature-openapi`）提供一份框架级、与渲染库无关的 API 文档基础信息契约，并在业务项目引入 SpringDoc 时把这份元数据自动写入 SpringDoc 的 `OpenAPI.info`。它只负责“文档基础信息”这一层，不生成接口清单、不接管路由扫描。模块绑定 `coco.openapi` 命名空间，默认启用，作为 Coco Feature（`CocoFeature.OPENAPI`）参与自动装配，并在 Web 与 Security 自动配置之后加载。

## 功能简介

- **`CocoOpenApiMetadata`**：承载框架级 API 文档基础信息的不可变记录，字段为 `title` / `version` / `description`，不绑定任何具体 OpenAPI 渲染库。构造时会归一化：`title` 为空回退 `Coco API`，`version` 为空回退 `1.0.0`，`description` 为空则为 `null`（通过 `descriptionOptional()` 以 `Optional` 暴露）。
- **`CocoOpenApiMetadataProvider`**：元数据提供器 SPI，文档渲染实现通过它获取框架统一的基础信息。默认实现 `DefaultCocoOpenApiMetadataProvider` 直接读取配置属性。
- **SpringDoc 适配器**：当类路径存在 SpringDoc 相关类时，注册 `cocoSpringDocOpenApiCustomizer`，把 Coco 元数据写入 SpringDoc 文档信息。

## 如何启用接入

模块默认启用。仅配置元数据时，只需在配置中填写文档信息：

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

框架依赖 Web 与 Security：OpenAPI 自动配置声明在 `CocoWebAutoConfiguration` 与 `CocoSecurityAutoConfiguration` 之后加载，以便文档信息与这两层的运行时状态保持一致。

要让元数据真正体现在渲染出的 OpenAPI 文档上，业务项目需自行引入 SpringDoc 依赖。类路径具备 SpringDoc 的 `OpenApiCustomizer`、`OpenAPI`、`Info` 等类，且 `coco.openapi.springdoc.enabled=true`（默认）时，框架自动注册 `cocoSpringDocOpenApiCustomizer`，把 `CocoOpenApiMetadata` 的标题、版本、描述写入 SpringDoc 的 `OpenAPI.info`。

## 使用示例

在需要直接读取框架统一元数据的场景（如自研文档页、健康检查暴露版本号），注入 `CocoOpenApiMetadataProvider`：

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

要替换默认元数据来源（例如从配置中心或数据库动态获取），声明自己的 `CocoOpenApiMetadataProvider` Bean 即可，`@ConditionalOnMissingBean` 会让默认实现自动退位。

## 关键配置项

绑定前缀 `coco.openapi`（对应 `CocoOpenApiProperties`）：

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `coco.openapi.enabled` | `boolean` | `true` | 是否启用 OpenAPI 元数据基础设施 |
| `coco.openapi.info.title` | `String` | `Coco API` | 文档标题 |
| `coco.openapi.info.version` | `String` | `1.0.0` | 文档版本 |
| `coco.openapi.info.description` | `String` | `Coco Framework API` | 文档描述 |
| `coco.openapi.springdoc.enabled` | `boolean` | `true` | 是否把 Coco 元数据自动写入 SpringDoc 文档信息 |

## 边界注意事项

- 本模块只提供“文档基础信息”契约，不生成 API 路径、参数或 Schema——接口清单由 SpringDoc 自身扫描控制器得到。
- SpringDoc 适配器只在类路径存在 SpringDoc 相关类时才注册；未引入 SpringDoc 时元数据仍可通过 `CocoOpenApiMetadataProvider` 读取，但不会体现在渲染文档中。
- 元数据字段会被归一化：`title`、`version` 传空会回退默认值，因此无法通过配置把它们清成空字符串。
