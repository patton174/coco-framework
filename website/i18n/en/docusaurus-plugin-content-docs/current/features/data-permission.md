---
title: Data Permission
---

# Data Permission

## Feature Overview

`coco-feature-data-permission` implements row-level data permission filtering based on SQL predicate injection: before a query executes, the framework automatically appends filter conditions to the involved tables (such as `dept_id IN (...)` or the always-false condition `1 = 0`) according to the data permission rules held by the current caller. Business code does not need to hand-write data-scope filtering in every query; the permission boundary is uniformly guaranteed by the framework.

Similar to tenant isolation, the data permission "rule source" and "SQL generation" are decoupled: `CocoDataPermissionContext` only expresses "what data scope the current caller has over a given resource," while how it is translated into SQL is decided by the pluggable `CocoDataPermissionSqlPredicateProvider`.

Core components of the module:

- `CocoDataScope`: Data scope enum (`ALL` / `SELF` / `CUSTOM` / `DENY`).
- `CocoDataPermissionRule` / `CocoDataPermissionContext`: A single resource rule and a collection of rules.
- `CocoDataPermissionContextHolder`: Thread-level context read/write and propagation utility.
- `CocoMybatisPlusDataPermissionHandler` + `DefaultCocoDataPermissionSqlPredicateProvider`: Translate context rules into SQL predicates.

This feature corresponds to the feature identifier `data-permission` (`CocoFeature.DATA_PERMISSION`) and depends on the two features `mybatis-plus` and `security`.

## Implementation Principle

Data permission filtering is built on top of MyBatis-Plus's `DataPermissionInterceptor`. The framework registers an interceptor driven by `CocoMybatisPlusDataPermissionHandler` through `CocoMybatisPlusInterceptorCustomizer`, and its `getSqlSegment` processing flow is:

1. **Resource resolution**: `CocoDataPermissionSqlResourceResolver` looks up which business resource the current table belongs to (the default implementation matches by the `coco.data-permission.sql.resources.<resource>.tables` configuration, comparing after table-name normalization). A table not mapped to any resource is allowed through directly, with no condition appended.
2. **Context resolution**: The current data permission context is read from `CocoDataPermissionContextResolver`. When missing, it is handled per `missing-context-policy`.
3. **Rule matching**: The `CocoDataPermissionRule` corresponding to the resource is looked up in the context. When a resource is mapped but has no corresponding rule, it is handled per `missing-rule-policy`.
4. **Predicate generation**: `CocoDataPermissionSqlPredicateProvider` generates the SQL condition based on the rule.

Rules of the default predicate provider `DefaultCocoDataPermissionSqlPredicateProvider`:

- `ALL` (all data): No condition is appended.
- `DENY` (access denied), empty scope value, or no configured data-scope column name: The always-false condition `1 = 0` is appended.
- `CUSTOM` / `SELF` with a scope value: Generates `column IN (values...)` by the configured column. If any scope value cannot be converted per the column type (such as a non-numeric value for a `LONG` column), the whole condition degrades to the always-false condition `1 = 0`, preventing the scope from being unexpectedly widened.

## Data Scope CocoDataScope

| Scope | Meaning | Default predicate behavior |
|------|------|--------------|
| `ALL` | Allow access to all data | No condition appended |
| `SELF` | Allow access to own data only | Generates an `IN` condition by the scope value |
| `CUSTOM` | Custom-scope data | Generates an `IN` condition by the scope value |
| `DENY` | Deny access to data | Appends the always-false condition `1 = 0` |

> The default implementation only provides framework-level generic strategies. Complex business models (such as recursing by a department tree or filtering by data tags) should implement a custom `CocoDataPermissionSqlPredicateProvider` to replace the default SPI.

## How to Enable and Integrate

The feature is auto-configured along with the `coco-feature-data-permission` dependency, but **SQL interception is disabled by default** and must be explicitly enabled with a configured resource mapping to take effect. Wiring conditions:

- None of the features `data-permission`, `mybatis-plus`, and `security` are disabled.
- `DataPermissionInterceptor` and `CocoMybatisPlusInterceptorCustomizer` are present on the classpath.
- `coco.data-permission.sql.enabled` is `true` (default `false`).

Configuration example:

```yaml
coco:
  data-permission:
    sql:
      enabled: true                     # Master switch for data permission SQL interception, disabled by default
      missing-context-policy: THROW     # Missing-context policy: THROW / DENY / IGNORE, default THROW
      missing-rule-policy: DENY         # Missing-resource-rule policy: DENY / IGNORE, default DENY
      resources:                        # Mapping from business resources to tables
        order:
          tables:
            - t_order
            - t_order_item
          column: dept_id               # Data-scope column used by the default predicate generator
          column-type: LONG             # Column value type: STRING (default) / LONG
```

## Usage Examples

### Setting the data permission context

The context is typically written by the permission framework during the authentication/authorization phase after computing the rules based on the current user's roles, departments, etc.:

```java
// The current user can only see data of departments 10 and 11 for the order resource
CocoDataPermissionRule orderRule = new CocoDataPermissionRule(
        "order", CocoDataScope.CUSTOM, Set.of("10", "11"));
CocoDataPermissionContext context = CocoDataPermissionContext.of(Set.of(orderRule));

CocoDataPermissionContextHolder.set(context);
try {
    // When querying t_order / t_order_item, dept_id IN (10, 11) is automatically appended
    orderMapper.selectList(null);
}
finally {
    CocoDataPermissionContextHolder.clear();
}
```

### Convenient rule construction and temporary context

```java
// Shortcut construction for all-data / deny-access
CocoDataPermissionRule all = CocoDataPermissionRule.all("order");
CocoDataPermissionRule deny = CocoDataPermissionRule.deny("order");

// Execute within the specified context and restore automatically
CocoDataPermissionContextHolder.callWithContext(
        CocoDataPermissionContext.of(Set.of(all)),
        () -> orderMapper.selectList(null));
```

### Cross-thread propagation

```java
Runnable task = CocoDataPermissionContextHolder.wrap(() -> orderMapper.selectList(null));
executor.submit(task);
```

## Key Configuration

The data permission SQL integration configuration namespace is `coco.data-permission.sql` (bound by `CocoDataPermissionSqlProperties`).

| Configuration item | Type | Default | Description |
|--------|------|--------|------|
| `enabled` | boolean | `false` | Whether to enable data permission SQL interception; must be explicitly enabled |
| `missing-context-policy` | enum | `THROW` | Missing-context policy: `THROW` (throw an exception) / `DENY` (append `1=0`) / `IGNORE` (skip filtering) |
| `missing-rule-policy` | enum | `DENY` | When a resource is mapped but has no rule: `DENY` (append `1=0`) / `IGNORE` (skip filtering) |
| `resources` | Map | empty | Mapping from business resource identifiers to resource configurations |

A single resource configuration `coco.data-permission.sql.resources.<resource>` (bound by `CocoDataPermissionSqlResourceProperties`).

| Configuration item | Type | Default | Description |
|--------|------|--------|------|
| `tables` | List&lt;String&gt; | empty | Data table names associated with the resource (compared with the SQL table name after normalization) |
| `column` | String | empty | Data-scope column name used by the default predicate generator; when unconfigured, any non-full scope degrades to `1=0` |
| `column-type` | enum | `STRING` | Data-scope column value type: `STRING` (string literal) / `LONG` (long-integer literal) |

## Boundary Considerations

- **Disabled by default, must be explicitly enabled**: `coco.data-permission.sql.enabled` defaults to `false`. Merely registering the SPI Bean will not intercept SQL; you must turn on the switch and configure the `resources` mapping.
- **The context must be cleaned up**: `CocoDataPermissionContextHolder` is based on `ThreadLocal`; after setting it at the entry point, always `clear()`, otherwise thread pool reuse will cause permission crossover and privilege escalation. Prefer `callWithContext` / `runWithContext`.
- **Safe default of deny-on-missing**: A missing context defaults to `THROW` and a missing rule defaults to `DENY`, avoiding silently widening the data scope. Changing to `IGNORE` allows through full-table data, which is a change with a very large impact and must be evaluated explicitly (such as for background tasks or public query scenarios).
- **Tables of unmapped resources are not protected**: Only tables appearing in some resource's `tables` list are filtered. If a business table is missing from the `resources` configuration, its queries will not have any data permission condition appended.
- **The column name/column type must be correct**: When `column` is not configured, any non-`ALL` scope degrades to `1=0`; a `column-type` that does not match the actual column type (such as configuring a numeric column as `STRING`) may generate unexpected SQL, and a `LONG` column encountering a non-numeric scope value degrades entirely to the always-false condition.
- **The default implementation is fairly generic**: `DefaultCocoDataPermissionSqlPredicateProvider` only supports single-column `IN` filtering. Complex models such as department-tree recursion or multi-column combinations should implement a custom `CocoDataPermissionSqlPredicateProvider` and register it as a Bean to override the default implementation.
- **Depends on `mybatis-plus` and `security`**: Disabling either cascades to disable the data permission feature; the interceptor is uniformly orchestrated by the Coco MyBatis-Plus interceptor factory and is placed before the pagination interceptor.
