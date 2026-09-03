---
title: 数据权限
---

# 数据权限

## 功能简介

`coco-feature-data-permission` 基于 SQL 谓词注入实现行级数据权限过滤：在查询执行前，框架根据当前调用方持有的数据权限规则，自动向涉及的表追加过滤条件（如 `dept_id IN (...)` 或永假条件 `1 = 0`）。业务代码无需在每个查询里手写数据范围过滤，权限边界由框架统一保障。

与租户隔离类似，数据权限的“规则来源”与“SQL 生成”解耦：`CocoDataPermissionContext` 只表达“当前调用方对某资源拥有什么数据范围”，具体如何翻译成 SQL 由可替换的 `CocoDataPermissionSqlPredicateProvider` 决定。

模块核心组成：

- `CocoDataScope`：数据范围枚举（`ALL` / `SELF` / `CUSTOM` / `DENY`）。
- `CocoDataPermissionRule` / `CocoDataPermissionContext`：单条资源规则与规则集合。
- `CocoDataPermissionContextHolder`：线程级上下文读写与传播工具。
- `CocoMybatisPlusDataPermissionHandler` + `DefaultCocoDataPermissionSqlPredicateProvider`：把上下文规则翻译成 SQL 谓词。

该功能对应特性标识 `data-permission`（`CocoFeature.DATA_PERMISSION`），依赖 `mybatis-plus` 与 `security` 两个特性。

## 实现原理

数据权限过滤建立在 MyBatis-Plus 的 `DataPermissionInterceptor` 之上。框架通过 `CocoMybatisPlusInterceptorCustomizer` 注册一个由 `CocoMybatisPlusDataPermissionHandler` 驱动的拦截器，其 `getSqlSegment` 处理流程为：

1. **资源解析**：`CocoDataPermissionSqlResourceResolver` 根据当前表名反查它属于哪个业务资源（默认实现按 `coco.data-permission.sql.resources.<resource>.tables` 配置匹配，表名归一化后比较）。未映射到任何资源的表直接放行，不追加条件。
2. **上下文解析**：从 `CocoDataPermissionContextResolver` 读取当前数据权限上下文。缺失时按 `missing-context-policy` 处理。
3. **规则匹配**：在上下文中查找该资源对应的 `CocoDataPermissionRule`。已映射资源但无对应规则时，按 `missing-rule-policy` 处理。
4. **谓词生成**：`CocoDataPermissionSqlPredicateProvider` 根据规则生成 SQL 条件。

默认谓词提供器 `DefaultCocoDataPermissionSqlPredicateProvider` 的规则：

- `ALL`（全部数据）：不追加任何条件。
- `DENY`（拒绝访问）、范围值为空、或未配置数据范围列名：追加永假条件 `1 = 0`。
- `CUSTOM` / `SELF` 且有范围值：按配置列生成 `column IN (值...)`。若任一范围值无法按列类型转换（如 `LONG` 列遇到非数字值），整条降级为永假条件 `1 = 0`，避免范围被意外放大。

## 数据范围 CocoDataScope

| 范围 | 含义 | 默认谓词行为 |
|------|------|--------------|
| `ALL` | 允许访问全部数据 | 不追加条件 |
| `SELF` | 仅允许访问本人数据 | 按范围值生成 `IN` 条件 |
| `CUSTOM` | 自定义范围数据 | 按范围值生成 `IN` 条件 |
| `DENY` | 拒绝访问数据 | 追加永假条件 `1 = 0` |

> 默认实现只提供框架级通用策略。复杂业务模型（如按部门树递归、按数据标签）应实现自定义 `CocoDataPermissionSqlPredicateProvider` 替换默认 SPI。

## 如何启用接入

功能随 `coco-feature-data-permission` 依赖自动装配，但 **SQL 拦截默认关闭**，必须显式开启并配置资源映射才生效。装配条件：

- 特性 `data-permission`、`mybatis-plus`、`security` 均未被禁用。
- 类路径存在 `DataPermissionInterceptor` 与 `CocoMybatisPlusInterceptorCustomizer`。
- `coco.data-permission.sql.enabled` 为 `true`（默认 `false`）。

配置示例：

```yaml
coco:
  data-permission:
    sql:
      enabled: true                     # 数据权限 SQL 拦截总开关，默认关闭
      missing-context-policy: THROW     # 缺少上下文策略：THROW / DENY / IGNORE，默认 THROW
      missing-rule-policy: DENY         # 缺少资源规则策略：DENY / IGNORE，默认 DENY
      resources:                        # 业务资源到表的映射
        order:
          tables:
            - t_order
            - t_order_item
          column: dept_id               # 默认谓词生成器使用的数据范围列
          column-type: LONG             # 列值类型：STRING（默认）/ LONG
```

## 使用示例

### 设置数据权限上下文

上下文通常由权限框架在认证/鉴权阶段根据当前用户的角色、部门等计算出规则后写入：

```java
// 当前用户对 order 资源只能看部门 10、11 的数据
CocoDataPermissionRule orderRule = new CocoDataPermissionRule(
        "order", CocoDataScope.CUSTOM, Set.of("10", "11"));
CocoDataPermissionContext context = CocoDataPermissionContext.of(Set.of(orderRule));

CocoDataPermissionContextHolder.set(context);
try {
    // 查询 t_order / t_order_item 时自动追加 dept_id IN (10, 11)
    orderMapper.selectList(null);
}
finally {
    CocoDataPermissionContextHolder.clear();
}
```

### 便捷规则构造与临时上下文

```java
// 全部数据 / 拒绝访问的快捷构造
CocoDataPermissionRule all = CocoDataPermissionRule.all("order");
CocoDataPermissionRule deny = CocoDataPermissionRule.deny("order");

// 在指定上下文中执行并自动恢复
CocoDataPermissionContextHolder.callWithContext(
        CocoDataPermissionContext.of(Set.of(all)),
        () -> orderMapper.selectList(null));
```

### 跨线程传播

```java
Runnable task = CocoDataPermissionContextHolder.wrap(() -> orderMapper.selectList(null));
executor.submit(task);
```

## 关键配置

数据权限 SQL 接入配置命名空间为 `coco.data-permission.sql`（由 `CocoDataPermissionSqlProperties` 绑定）。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 是否启用数据权限 SQL 拦截，需显式开启 |
| `missing-context-policy` | 枚举 | `THROW` | 缺少上下文策略：`THROW`（抛异常）/ `DENY`（追加 `1=0`）/ `IGNORE`（忽略过滤） |
| `missing-rule-policy` | 枚举 | `DENY` | 已映射资源但无规则时：`DENY`（追加 `1=0`）/ `IGNORE`（忽略过滤） |
| `resources` | Map | 空 | 业务资源标识到资源配置的映射 |

单个资源配置 `coco.data-permission.sql.resources.<resource>`（由 `CocoDataPermissionSqlResourceProperties` 绑定）。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `tables` | List&lt;String&gt; | 空 | 该资源关联的数据表名（归一化后与 SQL 表名比较） |
| `column` | String | 空 | 默认谓词生成器使用的数据范围列名；未配置时非全量范围一律降级为 `1=0` |
| `column-type` | 枚举 | `STRING` | 数据范围列值类型：`STRING`（字符串字面量）/ `LONG`（长整数字面量） |

## 边界注意事项

- **默认关闭，需显式开启**：`coco.data-permission.sql.enabled` 默认 `false`。仅注册 SPI Bean 不会拦截 SQL，必须开启开关并配置 `resources` 映射。
- **上下文必须清理**：`CocoDataPermissionContextHolder` 基于 `ThreadLocal`，入口设置后务必 `clear()`，否则线程池复用会造成权限串线与越权。优先使用 `callWithContext` / `runWithContext`。
- **缺失即拒绝的安全默认**：缺少上下文默认 `THROW`、缺少规则默认 `DENY`，避免静默扩大数据范围。改为 `IGNORE` 会放行全表数据，属于影响面很大的变更，需明确评估（如后台任务、公开查询场景）。
- **未映射资源的表不受保护**：只有出现在某资源 `tables` 列表中的表才会被过滤。业务表如果漏配 `resources`，其查询不会追加任何数据权限条件。
- **列名/列类型必须正确**：未配置 `column` 时，非 `ALL` 范围一律降级为 `1=0`；`column-type` 与实际列类型不符（如数值列配成 `STRING`）可能生成非预期 SQL，`LONG` 列遇到非数字范围值会整体降级为永假条件。
- **默认实现较通用**：`DefaultCocoDataPermissionSqlPredicateProvider` 仅支持单列 `IN` 过滤。部门树递归、多列组合等复杂模型应实现自定义 `CocoDataPermissionSqlPredicateProvider` 并注册为 Bean 覆盖默认实现。
- **依赖 `mybatis-plus` 与 `security`**：任一被禁用都会级联禁用数据权限功能；拦截器由 Coco MyBatis-Plus 拦截器工厂统一编排，排在分页拦截器之前。

