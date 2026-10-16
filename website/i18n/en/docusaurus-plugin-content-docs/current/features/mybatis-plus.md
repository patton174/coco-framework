---
title: MyBatis-Plus Integration
description: "How Coco assembles one MybatisPlusInterceptor chain so pagination, tenant isolation and data permission SQL rewriting run in a stable order without interfering."
seo_title: "MyBatis-Plus Interceptor Chain Integration"
---

# MyBatis-Plus Integration

## Overview

`coco-feature-mybatis-plus` is the foundation of Coco's data access capabilities. It takes over the assembly of MyBatis-Plus's `MybatisPlusInterceptor`, unifying every SQL-rewriting capability such as pagination, tenant isolation, and data permissions onto a single interceptor chain, and orchestrates them in a stable order to prevent the plugins from interfering with each other.

The module's core responsibilities:

- Register a framework-managed `MybatisPlusInterceptor` Bean that uniformly collects all `InnerInterceptor`s.
- Through the `CocoMybatisPlusInterceptorCustomizer` extension point, let modules such as tenant, data permission, and pagination context register their own built-in interceptors on the interceptor chain in a decoupled way.
- Provide optional SQL protection (full-table update/delete interception, illegal SQL interception), disabled by default.
- Provide a default pagination built-in interceptor, and guarantee it is always placed at the tail of the chain.
- Provide optional dynamic multi-datasource support (disabled by default): a single routing datasource, declarative `@CocoDataSource` switching, group load balancing and runtime registration; see "Dynamic Multi-DataSource" below.

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

## Dynamic Multi-DataSource (Optional)

When several datasources are introduced, a common approach is one `SqlSessionFactory` per datasource, which repeats the MyBatis-Plus startup banner and forces pagination, tenant isolation, data permission and SQL guard interceptors to be configured on every factory. Coco instead exposes a **single** `@Primary` routing datasource, `CocoRoutingDataSource` (extending Spring's `AbstractRoutingDataSource`). MyBatis-Plus therefore creates only one `SqlSessionFactory`, and every physical datasource shares the same Coco interceptor chain.

The capability is **disabled by default** and does not change existing single-datasource applications. When enabled it is assembled by `CocoMybatisPlusDataSourceAutoConfiguration`, which runs before `DataSourceAutoConfiguration` and `MybatisPlusAutoConfiguration`, so Spring Boot's own datasource auto-configuration steps aside.

```yaml
coco:
  mybatis-plus:
    datasource:
      enabled: true
      primary: master                  # used when no routing key is present
      missing-policy: fail             # target datasource missing: fail (default) / fallback-primary
      transaction-policy: reject       # switching inside a transaction: reject (default) / warn / allow
      load-balance-strategy: round-robin   # group load balancing: round-robin (default) / random
      group-separator: "_"             # automatic prefix grouping: slave_1 and slave_2 join group slave
      sources:
        master:
          url: jdbc:mysql://127.0.0.1:3306/app_master
          username: app
          password: ${DB_MASTER_PASSWORD}
          properties:
            maximum-pool-size: 20      # bound to the connection pool (HikariCP by default)
        slave_1:
          url: jdbc:mysql://127.0.0.1:3306/app_slave_1
          username: readonly
          password: ${DB_SLAVE_PASSWORD}
        slave_2:
          url: jdbc:mysql://127.0.0.1:3306/app_slave_2
          username: readonly
          password: ${DB_SLAVE_PASSWORD}
        report:
          group: analytics             # a group can also be set explicitly
          url: jdbc:mysql://127.0.0.1:3306/app_report
          username: report
          password: ${DB_REPORT_PASSWORD}
```

### Switching with `@CocoDataSource`

The annotation can be placed on business classes, business methods, interfaces, and MyBatis Mapper interfaces or methods; a method-level declaration wins over a type-level one. The value is a datasource name, a group name, or a SpEL expression starting with `#` / `#{...}` (method parameters are available):

```java
@Service
public class OrderQueryService {

    @CocoDataSource("slave")                    // picks slave_1 / slave_2 within the slave group
    public OrderView findOrder(Long id) { ... }

    @CocoDataSource("#region + '_db'")          // SpEL, evaluated by CocoDataSourceKeyResolver
    public List<OrderView> listByRegion(String region) { ... }
}

@CocoDataSource("report")                       // the whole Mapper defaults to report; a method can still override
public interface ReportMapper extends BaseMapper<ReportEntity> { }
```

The aspect runs before `@Transactional` by default, so a transaction opens only after the target datasource is in place. Nested calls restore as a stack: after `A -> B -> A` returns, each level goes back to the previous datasource. For manual control use `CocoDataSourceContextHolder.runWith(key, runnable)` / `callWith(key, supplier)`; use `wrap(...)` to carry the context across threads.

### Extension Points (SPI)

All of these carry `@ConditionalOnMissingBean`; declare a Bean of the same type to replace the default:

| Interface | Default | Purpose |
|------|----------|------|
| `CocoDataSourceProvider` | `PropertiesCocoDataSourceProvider` (reads `sources`) | Supplies datasources at startup, e.g. from a database or config center; several can be registered, ordered by `Ordered`, and a later one overrides an earlier one on the same name |
| `CocoDataSourceCreator` | `DefaultCocoDataSourceCreator` (`DataSourceBuilder` + property binding) | Creates the physical datasource; replace it for Druid or encrypted credentials |
| `CocoDataSourceLoadBalancer` | `RoundRobinCocoDataSourceLoadBalancer`, `RandomCocoDataSourceLoadBalancer` (chosen by `load-balance-strategy`) | Picks one datasource among a group's candidates |
| `CocoDataSourceKeyResolver` | `DefaultCocoDataSourceKeyResolver` | Resolves the annotation literal or SpEL into a routing key |
| `CocoDataSourceRouter` | `ContextCocoDataSourceRouter` (reads the thread context stack) | Decides the current routing key; can route by tenant context without annotations |

Add or remove datasources at runtime by injecting `CocoDataSourceRegistry` (or `CocoRoutingDataSource`):

```java
registry.registerDataSource("slave_3", "slave", dataSource);   // register or replace; a replaced pool is closed
registry.unregisterDataSource("slave_3");                       // remove and close; the primary cannot be removed
```

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

### Dynamic multi-datasource `coco.mybatis-plus.datasource`

| Configuration item | Type | Default | Description |
|--------|------|--------|------|
| `enabled` | boolean | `false` | Whether to enable dynamic multi-datasource routing |
| `primary` | String | `master` | Primary datasource name; it must be among the loaded datasources, otherwise startup fails |
| `missing-policy` | enum | `fail` | When the target datasource or group does not exist: `fail` throws, `fallback-primary` logs a warning and falls back to the primary |
| `transaction-policy` | enum | `reject` | When switching to another datasource inside an active local transaction: `reject` throws a conflict exception, `warn` logs a warning, `allow` lets it through |
| `load-balance-strategy` | enum | `round-robin` | Group load-balancing strategy: `round-robin`, `random` |
| `group-separator` | String | `_` | Delimiter for automatic prefix grouping; an empty string turns automatic grouping off |
| `aspect-order` | int | `Ordered.HIGHEST_PRECEDENCE + 100` | Order of the `@CocoDataSource` aspect; keep it above `@Transactional` |
| `max-key-length` | int | `128` | Maximum length of a resolved routing key; must be positive |
| `sources.<name>.url` | String | none | JDBC URL, required |
| `sources.<name>.username` / `password` | String | none | Database credentials; inject the password from an environment variable or secret manager |
| `sources.<name>.driver-class-name` | String | empty | JDBC driver class; leave empty for driver auto-discovery |
| `sources.<name>.type` | Class | empty | Connection pool class; when empty the default pool on the classpath (HikariCP) is used |
| `sources.<name>.group` | String | empty | Explicit group, which takes precedence over automatic prefix grouping |
| `sources.<name>.properties.*` | Map | empty | Extra properties bound onto the pool, e.g. `maximum-pool-size` |

## Boundary Considerations

- **The interceptor order must not be shuffled arbitrarily**: tenant and data permissions rely on the order of "rewrite the main query first, then paginate". If you fully take over the assembly with a custom `MybatisPlusInterceptor` Bean, you must ensure the pagination interceptor is placed at the tail of the chain yourself.
- **SQL protection is disabled by default**: upgrading to this framework does not silently change existing SQL behavior; enabling `sql-guard` is a change with a large blast radius, so be sure to verify it first in a staging environment with real SQL.
- **An invalid `db-type` causes a startup failure**: `CocoMybatisPlusDbTypeResolver` throws an `INVALID_DB_TYPE` exception when it cannot parse the configured text. If unsure, leave it empty and let MyBatis-Plus auto-infer.
- **Customizer vs. direct registration**: interceptors registered via `CocoMybatisPlusInterceptorCustomizer` are placed after directly-declared `InnerInterceptor` Beans and before the pagination interceptor; the two have different ordering semantics, so clarify the target position before registering.
- **A transaction cannot change its physical connection**: once a Spring local transaction starts it is bound to the first connection it obtained, so switching the datasource inside it does not switch connections. The default `transaction-policy=reject` fails such calls outright; for cross-database consistency use a distributed transaction or an eventual-consistency design rather than `allow`.
- **Declarative switching covers synchronous calls only**: the routing key lives in the thread context, so methods returning `CompletableFuture`, reactive types, or tasks run on other threads do not inherit it; use `CocoDataSourceContextHolder.wrap(...)` to carry it across threads.
- **All datasources share one `SqlSessionFactory`**: Mapper scanning, type aliases, the interceptor chain and `GlobalConfig` apply uniformly to every datasource; if the databases need entirely different MyBatis configuration, declare several `SqlSessionFactory` instances yourself and do not enable this capability.
- **Typos in configuration are not reported**: `properties.*` is bound by connection pool property name and unrecognised keys are ignored, so check the property names against the pool's documentation.
- **Credentials are not logged**: the datasource definition's `toString` masks the password and the values of extra properties; a custom `CocoDataSourceCreator` and your own logging should avoid printing credentials as well.

