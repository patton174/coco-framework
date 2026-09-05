---
slug: /feature-toggles
sidebar_position: 3
title: Feature Toggles
---

# Feature Toggles

All of Coco's built-in capabilities are managed through stable `CocoFeature` identifiers and can be started and stopped declaratively on the application side. For feature selection, **prefer YAML or `@CocoFeatures`**; the older `CocoConfigurer` Java hook is kept only for compatibility and is no longer recommended.

## Declaring via YAML

Disable features in `application.yml` through `coco.features.disabled`:

```yaml
coco:
  features:
    disabled:
      - mybatis-plus
      - tenant
      - data-permission
```

## Declaring via annotation

You can also declare them through Java configuration:

```java
@CocoFeatures(disabled = {
        CocoFeature.TENANT,
        CocoFeature.DATA_PERMISSION
})
@Configuration(proxyBeanMethods = false)
class ApplicationCocoConfiguration {
}
```

## Dependency propagation

Features have dependency relationships. When you disable a feature that others depend on, the features that depend on it are **automatically disabled as well**, avoiding a half-assembled state.

For example, disabling `mybatis-plus` cascades to disable `tenant`, `data-permission`, and `codegen`, which depend on it.

## Built-in feature identifiers

| Identifier | Feature | Dependencies |
|------|------|------|
| `web` | Web runtime | — |
| `mybatis-plus` | MyBatis-Plus integration | — |
| `audit` | Audit pipeline | — |
| `security` | Security context | — |
| `tenant` | Multi-tenant isolation | `mybatis-plus`, `security` |
| `data-permission` | Data permission | `mybatis-plus`, `security` |
| `openapi` | OpenAPI metadata | `web`, `security` |
| `rate-limit` | Rate limiting | `web` |
| `idempotency` | Idempotency | `web` |
| `scheduling` | Dynamic scheduled tasks | — |
| `lock` | Distributed lock | — |
| `storage` | Object storage | — |
| `messaging` | Messaging and events | — |
| `cache` | Two-level cache | — |
| `notification` | Notification channels | — |
| `captcha` | Captcha | — |
| `codegen` | Code generation | `mybatis-plus` |

## Relationship to module-level switches

`coco.features.disabled` controls the **assembly of an entire feature**. Each feature also typically has a finer-grained `enabled` property internally (such as `coco.idempotency.enabled`), used to control specific behavior once the feature is assembled. These are different layers: the feature toggle decides "whether to load this module," and the module property decides "whether it takes effect after loading."
