---
title: 多租户隔离
---

# 多租户隔离

## 功能简介

`coco-feature-tenant` 基于 MyBatis-Plus 的 SQL 自动改写实现行级多租户隔离：业务代码照常编写查询，框架在 SQL 执行前自动为涉及的表追加 `tenant_id = ?` 条件，`INSERT` 时自动补齐租户字段。租户的识别方式与隔离机制解耦——`CocoTenantContext` 只表达“当前调用方属于哪个租户”，不绑定 HTTP Header、JWT Claim 或数据库字段。

模块的核心组成：

- `CocoTenantContext` / `CocoTenantContextHolder`：线程级租户上下文与其读写、传播工具。
- `CocoTenantLineHandler`：把租户上下文翻译成 MyBatis-Plus `TenantLineHandler` 所需的字段名、租户值和忽略表规则。
- `CocoTenantMybatisPlusAutoConfiguration`：向 Coco 托管的拦截器链注册租户隔离拦截器与忽略治理守卫。

该功能对应特性标识 `tenant`（`CocoFeature.TENANT`），依赖 `mybatis-plus` 与 `security` 两个特性——禁用 `mybatis-plus` 会级联禁用租户功能。

## 实现原理

租户隔离建立在 MyBatis-Plus 的 `TenantLineInnerInterceptor` 之上。框架通过 `CocoMybatisPlusInterceptorCustomizer` 向托管拦截器链注册两个内置拦截器：

1. **`CocoTenantInterceptorIgnoreGuard`（忽略治理守卫）**：治理 `@InterceptorIgnore(tenantLine = true)` 及线程级忽略策略，防止租户隔离被静默绕过。
2. **`TenantLineInnerInterceptor(CocoTenantLineHandler)`（隔离拦截器）**：真正执行 SQL 改写。

`CocoTenantLineHandler` 实现了 MyBatis-Plus 的三个契约：

- `getTenantIdColumn()`：返回租户字段名，取自 `coco.tenant.sql.tenant-id-column`（默认 `tenant_id`）。
- `getTenantId()`：从 `CocoTenantContextResolver` 解析当前租户，交由 `CocoTenantIdExpressionResolver` 生成 SQL 表达式。默认实现 `DefaultCocoTenantIdExpressionResolver` 把 `tenantId` 作为字符串字面量写入条件。
- `ignoreTable(tableName)`：命中忽略表集合时跳过租户条件（大小写、首尾空白归一化后比较）。

## 如何启用接入

功能随 `coco-feature-tenant` 依赖自动装配，无需额外注解。装配条件：

- 特性 `tenant`、`mybatis-plus`、`security` 均未被禁用。
- 类路径存在 `TenantLineInnerInterceptor` 与 `CocoMybatisPlusInterceptorCustomizer`。
- `coco.tenant.sql.enabled` 为 `true`（默认）。

配置示例：

```yaml
coco:
  tenant:
    sql:
      enabled: true                 # 租户 SQL 隔离总开关，默认开启
      tenant-id-column: tenant_id   # 租户字段名，默认 tenant_id
      fail-on-missing-context: true # 缺少租户上下文时是否抛异常，默认 true
      ignore-tables:                # 无需追加租户条件的表（如全局字典、系统配置表）
        - sys_dict
        - sys_config
      interceptor-ignore:
        block-unlisted: true        # 阻断未授权的租户隔离绕过，默认 true
        allowed-mapped-statements:  # 允许绕过隔离的 MappedStatement ID 模式白名单
          - com.example.mapper.ReportMapper.rawStat
```

## 使用示例

### 设置租户上下文

租户上下文通常由请求入口适配器（如认证过滤器、网关拦截器）在处理业务前写入，业务与查询层只读取：

```java
// 在入口处设置当前租户，请求结束后务必清理
CocoTenantContextHolder.set(CocoTenantContext.of("tenant-a", "A 公司"));
try {
    // 此区间内的所有 MyBatis-Plus 查询都会自动追加 tenant_id = 'tenant-a'
    productMapper.selectList(null);
}
finally {
    CocoTenantContextHolder.clear();
}
```

### 临时切换租户

对后台任务或跨租户维护场景，可用 `callWithContext` / `runWithContext` 在指定租户下执行并自动恢复原上下文：

```java
CocoTenantContextHolder.callWithContext(
        CocoTenantContext.of("tenant-b", "B 公司"),
        () -> productMapper.selectList(null));
```

### 跨线程传播

`ThreadLocal` 上下文不会自动进入子线程。异步执行时用 `wrap` / `capture` 传播：

```java
Runnable task = CocoTenantContextHolder.wrap(() -> productMapper.selectList(null));
executor.submit(task);
```

## 关键配置

租户 SQL 隔离配置命名空间为 `coco.tenant.sql`（由 `CocoTenantSqlProperties` 绑定）。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `true` | 是否启用租户 SQL 隔离拦截器 |
| `tenant-id-column` | String | `tenant_id` | 租户字段名，留空时恢复默认 `tenant_id` |
| `ignore-tables` | Set&lt;String&gt; | 空集合 | 无需追加租户条件的表名集合（归一化后比较） |
| `fail-on-missing-context` | boolean | `true` | 缺少租户上下文时是否抛出异常；`false` 时改用 `NULL` 作为租户值 |

拦截器忽略治理配置命名空间为 `coco.tenant.sql.interceptor-ignore`（由 `CocoTenantInterceptorIgnoreProperties` 绑定）。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `block-unlisted` | boolean | `true` | 是否阻断未进入白名单的租户隔离绕过 |
| `allowed-mapped-statements` | Set&lt;String&gt; | 空集合 | 允许跳过租户隔离的 MyBatis `MappedStatement` ID 模式集合 |

## 边界注意事项

- **上下文必须清理**：`CocoTenantContextHolder` 基于 `ThreadLocal`，入口设置后必须在请求结束时 `clear()`，否则线程池复用会导致租户串线，造成严重的数据越权。优先使用 `callWithContext` / `runWithContext` 这类自动恢复的 API。
- **缺失上下文的默认是快速失败**：`fail-on-missing-context` 默认 `true`，未设置租户就执行受隔离的查询会直接抛出 `CONTEXT_MISSING`。这是安全默认值；改为 `false` 会用 `NULL` 作为租户值，通常匹配不到任何数据，需明确评估。
- **忽略表要谨慎**：`ignore-tables` 列出的表完全不追加租户条件，只应用于全局共享的字典、配置类表。误配业务表会导致跨租户数据泄露。
- **绕过治理默认开启**：`block-unlisted=true` 会阻断未在 `allowed-mapped-statements` 白名单内的 `@InterceptorIgnore(tenantLine = true)` 绕过尝试，避免隔离被静默关闭。确需绕过的语句必须显式登记到白名单。
- **依赖 `mybatis-plus` 与 `security`**：任一被禁用都会级联禁用租户功能；隔离拦截器由 Coco MyBatis-Plus 拦截器工厂统一编排，排在分页拦截器之前。
- **默认租户值为字符串字面量**：`DefaultCocoTenantIdExpressionResolver` 生成字符串字面量条件。若租户列为数值类型或需要参数化，应注册自定义 `CocoTenantIdExpressionResolver` Bean 替换默认实现。

