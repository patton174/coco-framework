---
title: MyBatis-Plus 集成
---

# MyBatis-Plus 集成

## 功能简介

`coco-feature-mybatis-plus` 是 Coco 数据访问能力的基座。它接管 MyBatis-Plus 的 `MybatisPlusInterceptor` 装配过程，把分页、租户隔离、数据权限等所有依赖 SQL 改写的能力统一到一条拦截器链上，并按稳定顺序编排，避免各插件互相干扰。

模块的核心职责：

- 注册一个由框架托管的 `MybatisPlusInterceptor` Bean，统一收集所有 `InnerInterceptor`。
- 通过 `CocoMybatisPlusInterceptorCustomizer` 扩展点，让租户、数据权限、分页上下文等模块以解耦方式向拦截器链注册自己的内置拦截器。
- 提供可选的 SQL 防护（全表更新/删除拦截、非法 SQL 拦截），默认关闭。
- 提供默认分页内置拦截器，并保证它始终排在链尾。

该功能对应特性标识 `mybatis-plus`（`CocoFeature.MYBATIS_PLUS`），只有类路径存在 `MybatisPlusInterceptor` 时才会装配。

## 如何启用接入

该功能随 `coco-feature-mybatis-plus` 依赖自动装配，无需额外注解。装配条件为：

- 特性 `mybatis-plus` 未被禁用（默认启用）。
- 类路径存在 `com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor`。
- 容器中不存在自定义的 `MybatisPlusInterceptor` Bean（`@ConditionalOnMissingBean`）。

如需关闭整个功能，通过特性开关禁用即可：

```yaml
coco:
  features:
    disabled:
      - mybatis-plus
```

最小化配置示例（大部分场景使用默认值即可）：

```yaml
coco:
  mybatis-plus:
    pagination:
      enabled: true          # 默认分页拦截器，默认开启
      db-type:               # 留空由数据源自动推断
      max-limit:             # 单页最大记录数，留空不限制
      overflow: false        # 页码溢出是否回到第一页
      optimize-join: true    # count 语句是否优化 JOIN
    sql-guard:
      block-attack-enabled: false   # 全表更新/删除防护，默认关闭
      illegal-sql-enabled: false    # 非法 SQL 防护，默认关闭
```

## 拦截器编排顺序

框架通过 `CocoMybatisPlusInterceptorFactory` 组装拦截器链，顺序是这个模块最关键的设计。`MybatisPlusInterceptor` 会按 `InnerInterceptor` 的添加顺序依次执行，顺序错误会导致分页统计 SQL 把租户、数据权限条件丢失。`create()` 的编排顺序如下：

1. 先按 `@Order` 顺序追加容器中所有直接注册的 `InnerInterceptor` Bean。
2. 再按 `@Order` 顺序执行所有 `CocoMybatisPlusInterceptorCustomizer`，由它们向链上注册各自的内置拦截器（租户隔离、数据权限、分页上下文注入等）。
3. 然后按需追加 SQL 防护拦截器（`BlockAttackInnerInterceptor`、`IllegalSQLInnerInterceptor`）。
4. **最后**追加默认分页内置拦截器 `PaginationInnerInterceptor`（当 `pagination.enabled=true`）。

分页拦截器之所以始终排在链尾，是为了让租户和数据权限条件在分页插件生成 `COUNT` 与 `LIMIT` 语句之前就已经改写到主查询上，保证统计值与实际数据一致。

```java
// CocoMybatisPlusInterceptorFactory#create 的编排逻辑（摘录）
MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
innerInterceptors.orderedStream().forEach(interceptor::addInnerInterceptor);
customizers.orderedStream().forEach(customizer -> customizer.customize(interceptor));
addSqlGuardInnerInterceptors(interceptor, sqlGuard);
if (pagination.isEnabled()) {
    interceptor.addInnerInterceptor(createPaginationInnerInterceptor(pagination));
}
```

## 可选 SQL 防护

`coco.mybatis-plus.sql-guard` 用于挂载 MyBatis-Plus 官方的两个防护拦截器，**两者默认都关闭**，避免在业务未明确启用时改变既有 SQL 行为：

- `block-attack-enabled`：启用 `BlockAttackInnerInterceptor`，拦截没有有效 `WHERE` 条件或恒真条件的全表 `UPDATE` / `DELETE`。
- `illegal-sql-enabled`：启用 `IllegalSQLInnerInterceptor`，对索引、`JOIN`、`OR`、函数条件等做较严格的规则校验。

当两者都关闭时，工厂会在启动日志打印一条 `INFO` 提示，建议在生产环境评估开启。生产环境建议先用真实 SQL 回放或预发布环境验证，确认批量维护 SQL 已显式豁免或改写后，再逐项开启：

```yaml
coco:
  mybatis-plus:
    sql-guard:
      block-attack-enabled: true
      illegal-sql-enabled: true
```

## 使用示例：扩展自定义 InnerInterceptor

向 Coco 托管的拦截器链注册自定义 `InnerInterceptor` 有两种方式，推荐使用 `CocoMybatisPlusInterceptorCustomizer`，它会在正确的时机（分页拦截器之前）被回调，从而保证顺序语义。

```java
@Configuration(proxyBeanMethods = false)
class DataScopeInterceptorConfiguration {

    /**
     * 通过定制器注册自定义拦截器。可实现 Ordered 控制多个定制器之间的相对顺序。
     */
    @Bean
    CocoMybatisPlusInterceptorCustomizer myBlockAttackCustomizer() {
        return interceptor -> interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
    }
}
```

也可以直接把 `InnerInterceptor` 声明为 Bean，框架会在第 1 步收集它。此方式下拦截器会排在所有定制器之前执行：

```java
@Bean
@Order(0)
InnerInterceptor tenantAwareInterceptor() {
    return new MyCustomInnerInterceptor();
}
```

如需完全接管拦截器装配，可以自定义一个 `MybatisPlusInterceptor` Bean，框架的 `@ConditionalOnMissingBean` 会自动退让，但此时分页/租户/数据权限的自动编排也将失效，需要自行负责顺序。

## 关键配置项

命名空间 `coco.mybatis-plus`。

### 分页拦截器 `coco.mybatis-plus.pagination`

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `true` | 是否注册默认的 `PaginationInnerInterceptor`（始终追加在链尾） |
| `db-type` | String | 空（自动推断） | 数据库类型，支持 `mysql`、`h2`、`postgre-sql` 等 MyBatis-Plus `DbType` 名称；留空由数据源自动推断，非法值启动报错 |
| `overflow` | boolean | `false` | 页码超出总页数时是否回到第一页 |
| `max-limit` | Long | 空（不限制） | 单页最大记录数；小于等于 0 视为不限制 |
| `optimize-join` | boolean | `true` | 生成分页 `COUNT` 语句时是否优化 `JOIN` |

### SQL 防护 `coco.mybatis-plus.sql-guard`

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `block-attack-enabled` | boolean | `false` | 是否启用全表更新/删除防护 `BlockAttackInnerInterceptor`，生产建议评估开启 |
| `illegal-sql-enabled` | boolean | `false` | 是否启用非法 SQL 防护 `IllegalSQLInnerInterceptor`，启用前需验证兼容性 |

## 边界注意事项

- **拦截器顺序不可随意打乱**：租户、数据权限依赖“先改写主查询、后分页”的顺序。若通过自定义 `MybatisPlusInterceptor` Bean 完全接管装配，需自行保证分页拦截器排在链尾。
- **SQL 防护默认关闭**：升级到本框架不会静默改变既有 SQL 行为；开启 `sql-guard` 属于影响面较大的变更，务必先在预发布环境用真实 SQL 验证。
- **`db-type` 非法会导致启动失败**：`CocoMybatisPlusDbTypeResolver` 无法解析配置文本时抛出 `INVALID_DB_TYPE` 异常。若不确定，保持留空由 MyBatis-Plus 自动推断。
- **定制器 vs 直接注册**：`CocoMybatisPlusInterceptorCustomizer` 注册的拦截器排在直接声明的 `InnerInterceptor` Bean 之后、分页拦截器之前；两者顺序语义不同，注册前需明确目标位置。


