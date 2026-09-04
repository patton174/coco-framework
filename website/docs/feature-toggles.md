---
slug: /feature-toggles
sidebar_position: 3
title: 特性开关
---

# 特性开关

Coco 的所有内置能力都通过稳定的 `CocoFeature` 标识管理，可以在应用侧声明式地启停。功能选择**优先使用 YAML 或 `@CocoFeatures`**；旧的 `CocoConfigurer` Java 钩子仅保留兼容，已不再推荐。

## 通过 YAML 声明

在 `application.yml` 中通过 `coco.features.disabled` 禁用功能：

```yaml
coco:
  features:
    disabled:
      - mybatis-plus
      - tenant
      - data-permission
```

## 通过注解声明

也可以通过 Java 配置声明：

```java
@CocoFeatures(disabled = {
        CocoFeature.TENANT,
        CocoFeature.DATA_PERMISSION
})
@Configuration(proxyBeanMethods = false)
class ApplicationCocoConfiguration {
}
```

## 依赖传播

功能之间存在依赖关系。当你禁用一个被依赖的功能时，依赖它的功能会被**自动一并禁用**，避免半装配状态。

例如禁用 `mybatis-plus` 会级联禁用依赖它的 `tenant`、`data-permission`、`codegen`。

## 内置功能标识

| 标识 | 功能 | 依赖 |
|------|------|------|
| `web` | Web 运行时 | — |
| `mybatis-plus` | MyBatis-Plus 集成 | — |
| `audit` | 审计管道 | — |
| `security` | 安全上下文 | — |
| `tenant` | 多租户隔离 | `mybatis-plus`, `security` |
| `data-permission` | 数据权限 | `mybatis-plus`, `security` |
| `openapi` | OpenAPI 元数据 | `web`, `security` |
| `rate-limit` | 限流 | `web` |
| `idempotency` | 幂等 | `web` |
| `scheduling` | 动态定时任务 | — |
| `lock` | 分布式锁 | — |
| `storage` | 对象存储 | — |
| `messaging` | 消息与事件 | — |
| `cache` | 两层缓存 | — |
| `codegen` | 代码生成 | `mybatis-plus` |

## 与模块级开关的关系

`coco.features.disabled` 控制的是**整个功能的装配**。而每个功能内部通常还有更细粒度的 `enabled` 属性（如 `coco.idempotency.enabled`），用于在功能已装配的前提下控制具体行为。两者是不同层级：特性开关决定"是否加载这个模块"，模块属性决定"加载后是否生效"。
