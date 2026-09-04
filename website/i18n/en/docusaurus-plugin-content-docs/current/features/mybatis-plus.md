---
title: MyBatis-Plus Integration
---

# MyBatis-Plus Integration

## Overview

`coco-feature-mybatis-plus` is the foundation of Coco's data access capabilities. It takes over the assembly of MyBatis-Plus's `MybatisPlusInterceptor`, unifying every SQL-rewriting capability such as pagination, tenant isolation, and data permissions onto a single interceptor chain, and orchestrates them in a stable order to prevent the plugins from interfering with each other.

The module's core responsibilities:

- Register a framework-managed `MybatisPlusInterceptor` Bean that uniformly collects all `InnerInterceptor`s.
- Through the `CocoMybatisPlusInterceptorCustomizer` extension point, let modules such as tenant, data permission, and pagination context register their own built-in interceptors on the interceptor chain in a decoupled way.
- Provide optional SQL protection (full-table update/delete interception, illegal SQL interception), disabled by default.
- Provide a default pagination built-in interceptor, and guarantee it is always placed at the tail of the chain.

This capability corresponds to the feature identifier `mybatis-plus` (`CocoFeature.MYBATIS_PLUS`), and is only assembled when `MybatisPlusInterceptor` exists on the classpath.

## How to Enable

This capability is auto-configured along with the `coco-feature-mybatis-plus` dependency, with no extra annotation required. The assembly conditions are:

- The feature `mybatis-plus` is not disabled (enabled by default).
- The classpath contains `com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor`.
- No custom `MybatisPlusInterceptor` Bean exists in the container (`@ConditionalOnMissingBean`).

To turn off the entire capability, disable it via the feature toggle:

```yaml
coco:
  features:
    disabled:
      - mybatis-plus
```

Minimal configuration example (defaults work for most scenarios):

```yaml
coco:
  mybatis-plus:
    pagination:
      enabled: true          # default pagination interceptor, enabled by default
      db-type:               # leave empty to auto-infer from the data source
      max-limit:             # max records per page, leave empty for no limit
      overflow: false        # whether page number overflow returns to the first page
      optimize-join: true    # whether the count statement optimizes JOINs
    sql-guard:
      block-attack-enabled: false   # full-table update/delete protection, disabled by default
      illegal-sql-enabled: false    # illegal SQL protection, disabled by default
```

## Interceptor Orchestration Order

The framework assembles the interceptor chain through `CocoMybatisPlusInterceptorFactory`, and the order is the most critical design of this module. `MybatisPlusInterceptor` executes `InnerInterceptor`s in the order they were added, and the wrong order would cause the pagination count SQL to lose the tenant and data permission conditions. The orchestration order in `create()` is as follows:

1. First, append all directly-registered `InnerInterceptor` Beans in the container in `@Order` order.
2. Then, execute all `CocoMybatisPlusInterceptorCustomizer`s in `@Order` order, letting them register their respective built-in interceptors on the chain (tenant isolation, data permissions, pagination context injection, etc.).
3. Next, append the SQL protection interceptors as needed (`BlockAttackInnerInterceptor`, `IllegalSQLInnerInterceptor`).
4. **Finally**, append the default pagination built-in interceptor `PaginationInnerInterceptor` (when `pagination.enabled=true`).

The reason the pagination interceptor is always placed at the tail of the chain is to ensure the tenant and data permission conditions are already rewritten onto the main query before the pagination plugin generates the `COUNT` and `LIMIT` statements, guaranteeing that the count value is consistent with the actual data.

```java
// The orchestration logic of CocoMybatisPlusInterceptorFactory#create (excerpt)
MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
innerInterceptors.orderedStream().forEach(interceptor::addInnerInterceptor);
customizers.orderedStream().forEach(customizer -> customizer.customize(interceptor));
addSqlGuardInnerInterceptors(interceptor, sqlGuard);
if (pagination.isEnabled()) {
    interceptor.addInnerInterceptor(createPaginationInnerInterceptor(pagination));
}
```

## Optional SQL Protection

`coco.mybatis-plus.sql-guard` is used to mount MyBatis-Plus's two official protection interceptors. **Both are disabled by default** to avoid changing existing SQL behavior when the business has not explicitly enabled them:

- `block-attack-enabled`: enables `BlockAttackInnerInterceptor`, which intercepts full-table `UPDATE` / `DELETE` statements without a valid `WHERE` condition or with an always-true condition.
- `illegal-sql-enabled`: enables `IllegalSQLInnerInterceptor`, which performs stricter rule validation on indexes, `JOIN`, `OR`, function conditions, and so on.

When both are disabled, the factory prints an `INFO` hint in the startup log, recommending that you evaluate enabling them in production. In production, it is recommended to first verify with real SQL replay or in a staging environment, and only enable them item by item after confirming that batch maintenance SQL has been explicitly exempted or rewritten:

```yaml
coco:
  mybatis-plus:
    sql-guard:
      block-attack-enabled: true
      illegal-sql-enabled: true
```

## Usage Example: Extending with a Custom InnerInterceptor

There are two ways to register a custom `InnerInterceptor` on Coco's managed interceptor chain. The recommended one is `CocoMybatisPlusInterceptorCustomizer`, which is called back at the correct time (before the pagination interceptor), thereby preserving the ordering semantics.

```java
@Configuration(proxyBeanMethods = false)
class DataScopeInterceptorConfiguration {

    /**
     * Register a custom interceptor via a customizer. It can implement Ordered to control the relative order among multiple customizers.
     */
    @Bean
    CocoMybatisPlusInterceptorCustomizer myBlockAttackCustomizer() {
        return interceptor -> interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
    }
}
```

You can also declare an `InnerInterceptor` directly as a Bean, and the framework will collect it in step 1. In this approach the interceptor executes before all customizers:

```java
@Bean
@Order(0)
InnerInterceptor tenantAwareInterceptor() {
    return new MyCustomInnerInterceptor();
}
```

To fully take over the interceptor assembly, you can define your own `MybatisPlusInterceptor` Bean, and the framework's `@ConditionalOnMissingBean` will automatically step aside; however, the automatic orchestration of pagination/tenant/data permission also becomes ineffective in this case, and you are responsible for the order yourself.

## Key Configuration Items

Namespace `coco.mybatis-plus`.

### Pagination interceptor `coco.mybatis-plus.pagination`

| Configuration item | Type | Default | Description |
|--------|------|--------|------|
| `enabled` | boolean | `true` | Whether to register the default `PaginationInnerInterceptor` (always appended at the tail of the chain) |
| `db-type` | String | empty (auto-inferred) | Database type, supporting MyBatis-Plus `DbType` names such as `mysql`, `h2`, `postgre-sql`; leave empty to auto-infer from the data source, an invalid value causes a startup error |
| `overflow` | boolean | `false` | Whether to return to the first page when the page number exceeds the total number of pages |
| `max-limit` | Long | empty (no limit) | Max records per page; a value less than or equal to 0 is treated as no limit |
| `optimize-join` | boolean | `true` | Whether to optimize `JOIN` when generating the pagination `COUNT` statement |

### SQL protection `coco.mybatis-plus.sql-guard`

| Configuration item | Type | Default | Description |
|--------|------|--------|------|
| `block-attack-enabled` | boolean | `false` | Whether to enable full-table update/delete protection `BlockAttackInnerInterceptor`; evaluate enabling in production |
| `illegal-sql-enabled` | boolean | `false` | Whether to enable illegal SQL protection `IllegalSQLInnerInterceptor`; verify compatibility before enabling |

## Boundary Considerations

- **The interceptor order must not be shuffled arbitrarily**: tenant and data permissions rely on the order of "rewrite the main query first, then paginate". If you fully take over the assembly with a custom `MybatisPlusInterceptor` Bean, you must ensure the pagination interceptor is placed at the tail of the chain yourself.
- **SQL protection is disabled by default**: upgrading to this framework does not silently change existing SQL behavior; enabling `sql-guard` is a change with a large blast radius, so be sure to verify it first in a staging environment with real SQL.
- **An invalid `db-type` causes a startup failure**: `CocoMybatisPlusDbTypeResolver` throws an `INVALID_DB_TYPE` exception when it cannot parse the configured text. If unsure, leave it empty and let MyBatis-Plus auto-infer.
- **Customizer vs. direct registration**: interceptors registered via `CocoMybatisPlusInterceptorCustomizer` are placed after directly-declared `InnerInterceptor` Beans and before the pagination interceptor; the two have different ordering semantics, so clarify the target position before registering.
