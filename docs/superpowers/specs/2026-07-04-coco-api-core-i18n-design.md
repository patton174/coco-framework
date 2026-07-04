# Coco API Core And Module I18n Design

## 目标

本阶段整理两个基础问题：

1. `coco-api` 和 `coco-core` 是否也采用聚合多模块结构。
2. 除 `coco-common-i18n` 外，其他框架模块如何接入统一国际化消息能力。

目标是让模块结构保持统一，同时不为了统一目录而过度拆分；国际化接入要支持按模块自然打包，业务项目只启用或依赖哪些模块，就只携带这些模块自己的消息资源。

## 模块结构决策

`coco-api` 调整为聚合模块，实际对外契约 jar 放入 `coco-api/coco-api-core`：

```text
coco-api
+-- coco-api-core
```

`coco-api-core` 承载当前已有的业务侧稳定契约：

- `io.github.coco.api.CocoConfigurer`
- `io.github.coco.api.feature.CocoFeature`
- `io.github.coco.api.feature.CocoFeatureRegistry`
- `io.github.coco.api.feature.DefaultCocoFeatureRegistry`

Java 包名不改。只迁移 Maven 物理结构和 artifactId，避免业务侧未来因为包名变动产生不必要的迁移成本。

`coco-core` 调整为聚合模块，实际内部运行时 jar 放入 `coco-core/coco-core-runtime`：

```text
coco-core
+-- coco-core-runtime
```

`coco-core-runtime` 先承载当前已有的 `io.github.coco.core.CocoCore`。后续如果内部基础能力变多，再根据真实边界继续拆出：

- `coco-core-context`
- `coco-core-reflection`
- `coco-core-spi`
- `coco-core-model`

本阶段不提前创建这些空模块。

## Maven 依赖方向

根聚合模块：

```text
coco-framework
+-- coco-api
+-- coco-common
+-- coco-core
+-- coco-config
+-- coco-spring-boot-starter
+-- coco-features
```

实际 jar 坐标：

```text
io.github.patton174:coco-api-core
io.github.patton174:coco-common-i18n
io.github.patton174:coco-core-runtime
```

依赖方向：

```text
coco-api-core
  <- coco-common-i18n
  <- coco-core-runtime
  <- coco-config
  <- coco-feature-registry
  <- coco-spring-boot-starter
```

`coco-common-i18n` 可以依赖 `coco-api-core`，但不能依赖 `coco-config`、`coco-core-runtime` 或任意 feature 模块。

`coco-core-runtime` 可以依赖 `coco-api-core` 和 `coco-common-i18n`。如果 core 内部需要抛出框架异常或声明框架消息，必须使用 i18n 的 `CocoMessage` / `CocoException` 边界，不直接绑定业务模块。

`coco-config` 可以依赖 `coco-api-core`、`coco-common-i18n`、`coco-feature-registry`，用于把配置错误、特性解析错误接入统一消息。

`coco-spring-boot-starter` 依赖实际 jar，不依赖聚合 POM：

```text
coco-api-core
coco-common-i18n
coco-core-runtime
coco-config
```

## 其他模块接入国际化

每个框架模块可以携带自己的消息资源。资源命名与 artifactId 保持一致：

```text
coco-config-messages.properties
coco-config-messages_zh_CN.properties
coco-config-messages_en_US.properties

coco-feature-registry-messages.properties
coco-feature-registry-messages_zh_CN.properties
coco-feature-registry-messages_en_US.properties
```

后续 feature 模块也按同一规则：

```text
coco-feature-web-messages*.properties
coco-feature-audit-messages*.properties
coco-feature-mybatis-plus-messages*.properties
```

`coco-common-i18n` 保留自身基础消息包：

```text
coco-messages.properties
coco-messages_zh_CN.properties
coco-messages_en_US.properties
```

这些消息只表达框架基础错误，例如未知错误、非法参数、缺失消息编码。具体模块自己的提示放在模块自己的 `*-messages` 资源里。

## 消息包发现机制

`coco-common-i18n` 增加一个轻量注册机制，用于声明模块消息包：

```java
public interface CocoMessageBundleRegistrar {

    void registerBundles(CocoMessageBundleRegistry registry);
}
```

注册表只暴露添加 basename 的能力：

```java
public interface CocoMessageBundleRegistry {

    void add(String basename);
}
```

每个模块可以通过自动配置注册自己的消息包：

```java
@Bean
CocoMessageBundleRegistrar cocoConfigMessageBundleRegistrar() {
    return registry -> registry.add("coco-config-messages");
}
```

`CocoCommonAutoConfiguration` 汇总所有 `CocoMessageBundleRegistrar`，并将它们合并进 Coco 自有 `MessageSource` 的 basename 列表。

默认顺序：

```text
messages
coco-config-messages
coco-feature-registry-messages
其他模块消息包
coco-messages
```

业务项目的 `messages*` 始终优先，用于覆盖框架提示。`coco-messages` 始终兜底，避免基础错误没有默认文本。

## 消息编码规范

消息编码按模块分区：

```text
coco.common.error.unknown
coco.common.error.invalid-argument
coco.config.features.exclude.invalid
coco.config.features.dependency.disabled
coco.feature.registry.not-found
```

编码使用小写短横线风格。模块名前缀使用逻辑边界，不强制完全等于包名，但必须能看出归属模块。

## 异常与消息使用边界

适合使用 `CocoException` 或 `CocoMessage` 的场景：

- 框架内部配置错误。
- 特性解析错误。
- 后续 web 层统一异常响应。
- 后续 audit/security/tenant 等模块的框架级提示。

不适合使用 i18n 的场景：

- JavaDoc 或日志里给开发者看的固定说明。
- 编译期插件的内部调试日志。
- 只在测试断言中使用的文本。

日志可以记录 message code 和参数，是否解析成本地化文本由具体日志场景决定。接口响应和用户可见提示必须通过 `CocoMessageService` 解析。

## 本阶段实施范围

本阶段实施：

- `coco-api` 聚合化，新增 `coco-api/coco-api-core`。
- `coco-core` 聚合化，新增 `coco-core/coco-core-runtime`。
- 更新 root POM、BOM、starter、相关模块依赖。
- `coco-common-i18n` 增加模块消息包注册机制。
- `coco-config` 接入 `coco-config-messages*`。
- `coco-feature-registry` 接入 `coco-feature-registry-messages*`。
- 更新测试覆盖消息包发现和模块消息解析。

本阶段不实施：

- Web 全局异常处理。
- 统一响应体翻译。
- request header 语言识别。
- 数据库消息源。
- 所有 feature 模块的具体业务消息。
- Maven 插件的完整 feature 裁剪逻辑。

## 验收标准

- `coco-api` 和 `coco-core` 均为聚合 POM。
- 当前 Java 包名保持不变。
- `coco-spring-boot-starter` 不依赖聚合 POM，只依赖实际 jar。
- `coco-common-i18n` 能自动收集已注册的模块消息包。
- `coco-config` 和 `coco-feature-registry` 的消息可以通过 `CocoMessageService` 解析。
- 未依赖的 feature 模块不会因为 i18n 机制被额外打包进业务项目。
- `mvn -q verify` 通过。
- `mvn -q -DskipTests install javadoc:javadoc` 通过。
