---
title: Multi-tenancy Isolation
---

# Multi-tenancy Isolation

## Overview

`coco-feature-tenant` implements row-level multi-tenancy isolation based on MyBatis-Plus's automatic SQL rewriting: business code writes queries as usual, and before the SQL executes the framework automatically appends a `tenant_id = ?` condition to the involved tables, and automatically fills in the tenant field on `INSERT`. Tenant identification is decoupled from the isolation mechanism — `CocoTenantContext` only expresses "which tenant the current caller belongs to" and is not bound to an HTTP Header, a JWT Claim, or a database field.

The module's core components:

- `CocoTenantContext` / `CocoTenantContextHolder`: the thread-level tenant context and its read/write and propagation utilities.
- `CocoTenantLineHandler`: translates the tenant context into the field name, tenant value, and ignore-table rules required by MyBatis-Plus's `TenantLineHandler`.
- `CocoTenantMybatisPlusAutoConfiguration`: registers the tenant-isolation interceptor and the ignore-governance guard into Coco's managed interceptor chain.

This capability corresponds to the feature identifier `tenant` (`CocoFeature.TENANT`) and depends on two features, `mybatis-plus` and `security` — disabling `mybatis-plus` cascades to disabling the tenant feature.

## Implementation principles

Tenant isolation is built on top of MyBatis-Plus's `TenantLineInnerInterceptor`. Through `CocoMybatisPlusInterceptorCustomizer`, the framework registers two built-in interceptors into the managed interceptor chain:

1. **`CocoTenantInterceptorIgnoreGuard` (the ignore-governance guard)**: governs `@InterceptorIgnore(tenantLine = true)` and thread-level ignore policies, preventing tenant isolation from being silently bypassed.
2. **`TenantLineInnerInterceptor(CocoTenantLineHandler)` (the isolation interceptor)**: actually performs the SQL rewriting.

`CocoTenantLineHandler` implements MyBatis-Plus's three contracts:

- `getTenantIdColumn()`: returns the tenant field name, taken from `coco.tenant.sql.tenant-id-column` (default `tenant_id`).
- `getTenantId()`: resolves the current tenant from `CocoTenantContextResolver`, then hands it to `CocoTenantIdExpressionResolver` to generate the SQL expression. The default implementation `DefaultCocoTenantIdExpressionResolver` writes `tenantId` into the condition as a string literal.
- `ignoreTable(tableName)`: skips the tenant condition when the table is in the ignore set (compared after normalizing case and leading/trailing whitespace).

## How to enable and integrate

The feature is auto-configured along with the `coco-feature-tenant` dependency, with no extra annotation required. Auto-configuration conditions:

- The features `tenant`, `mybatis-plus`, and `security` are all not disabled.
- `TenantLineInnerInterceptor` and `CocoMybatisPlusInterceptorCustomizer` are present on the classpath.
- `coco.tenant.sql.enabled` is `true` (the default).

Configuration example:

```yaml
coco:
  tenant:
    sql:
      enabled: true                 # master switch for tenant SQL isolation, enabled by default
      tenant-id-column: tenant_id   # tenant field name, default tenant_id
      fail-on-missing-context: true # whether to throw when the tenant context is missing, default true
      ignore-tables:                # tables that do not need a tenant condition appended (e.g. global dictionaries, system config tables)
        - sys_dict
        - sys_config
      interceptor-ignore:
        block-unlisted: true        # block unauthorized tenant-isolation bypasses, default true
        allowed-mapped-statements:  # whitelist of MappedStatement ID patterns allowed to bypass isolation
          - com.example.mapper.ReportMapper.rawStat
```

## Usage examples

### Setting the tenant context

The tenant context is usually written by a request-entry adapter (such as an authentication filter or gateway interceptor) before business processing, and the business and query layers only read it:

```java
// Set the current tenant at the entry point; always clean it up after the request finishes
CocoTenantContextHolder.set(CocoTenantContext.of("tenant-a", "Company A"));
try {
    // All MyBatis-Plus queries within this scope automatically append tenant_id = 'tenant-a'
    productMapper.selectList(null);
}
finally {
    CocoTenantContextHolder.clear();
}
```

### Temporarily switching tenants

For background tasks or cross-tenant maintenance scenarios, use `callWithContext` / `runWithContext` to execute under a specified tenant and automatically restore the original context:

```java
CocoTenantContextHolder.callWithContext(
        CocoTenantContext.of("tenant-b", "Company B"),
        () -> productMapper.selectList(null));
```

### Cross-thread propagation

A `ThreadLocal` context does not automatically enter child threads. When executing asynchronously, use `wrap` / `capture` to propagate it:

```java
Runnable task = CocoTenantContextHolder.wrap(() -> productMapper.selectList(null));
executor.submit(task);
```

## Key configuration

The tenant SQL isolation configuration namespace is `coco.tenant.sql` (bound by `CocoTenantSqlProperties`).

| Option | Type | Default | Description |
|--------|------|--------|------|
| `enabled` | boolean | `true` | Whether to enable the tenant SQL isolation interceptor |
| `tenant-id-column` | String | `tenant_id` | Tenant field name; falls back to the default `tenant_id` when left blank |
| `ignore-tables` | Set&lt;String&gt; | empty set | Set of table names that do not need a tenant condition appended (compared after normalization) |
| `fail-on-missing-context` | boolean | `true` | Whether to throw an exception when the tenant context is missing; when `false`, uses `NULL` as the tenant value instead |

The interceptor ignore-governance configuration namespace is `coco.tenant.sql.interceptor-ignore` (bound by `CocoTenantInterceptorIgnoreProperties`).

| Option | Type | Default | Description |
|--------|------|--------|------|
| `block-unlisted` | boolean | `true` | Whether to block tenant-isolation bypasses that are not on the whitelist |
| `allowed-mapped-statements` | Set&lt;String&gt; | empty set | Set of MyBatis `MappedStatement` ID patterns allowed to skip tenant isolation |

## Boundary considerations

- **The context must be cleaned up**: `CocoTenantContextHolder` is based on `ThreadLocal`, so after being set at the entry point it must be `clear()`ed when the request finishes; otherwise thread-pool reuse will cause tenant cross-contamination, resulting in serious data-privilege violations. Prefer auto-restoring APIs such as `callWithContext` / `runWithContext`.
- **The default for a missing context is fail-fast**: `fail-on-missing-context` defaults to `true`, so executing an isolated query without a tenant set throws `CONTEXT_MISSING` directly. This is the secure default; changing it to `false` uses `NULL` as the tenant value, which usually matches no data and must be evaluated deliberately.
- **Be careful with ignore-tables**: tables listed in `ignore-tables` have no tenant condition appended at all, and should only be used for globally shared dictionary and configuration tables. Misconfiguring a business table would cause cross-tenant data leakage.
- **Bypass governance is enabled by default**: `block-unlisted=true` blocks `@InterceptorIgnore(tenantLine = true)` bypass attempts that are not in the `allowed-mapped-statements` whitelist, preventing isolation from being silently turned off. Statements that genuinely need to bypass must be explicitly registered in the whitelist.
- **Depends on `mybatis-plus` and `security`**: disabling either cascades to disabling the tenant feature; the isolation interceptor is uniformly orchestrated by the Coco MyBatis-Plus interceptor factory and ordered before the pagination interceptor.
- **The default tenant value is a string literal**: `DefaultCocoTenantIdExpressionResolver` generates a string-literal condition. If the tenant column is a numeric type or needs to be parameterized, register a custom `CocoTenantIdExpressionResolver` Bean to replace the default implementation.
