---
title: MyBatis-Plus 集成
description: "Coco 如何接管 MybatisPlusInterceptor 装配，把分页、租户隔离、数据权限等 SQL 改写能力按稳定顺序编排在同一条拦截器链上。"
seo_title: "MyBatis-Plus 拦截器链集成"
---

# MyBatis-Plus 集成

## 功能简介

`coco-feature-mybatis-plus` 是 Coco 数据访问能力的基座。它接管 MyBatis-Plus 的 `MybatisPlusInterceptor` 装配过程，把分页、租户隔离、数据权限等所有依赖 SQL 改写的能力统一到一条拦截器链上，并按稳定顺序编排，避免各插件互相干扰。

模块的核心职责：

- 注册一个由框架托管的 `MybatisPlusInterceptor` Bean，统一收集所有 `InnerInterceptor`。
- 通过 `CocoMybatisPlusInterceptorCustomizer` 扩展点，让租户、数据权限、分页上下文等模块以解耦方式向拦截器链注册自己的内置拦截器。
- 提供可选的 SQL 防护（全表更新/删除拦截、非法 SQL 拦截），默认关闭。
- 提供默认分页内置拦截器，并保证它始终排在链尾。
- 提供可选的动态多数据源（默认关闭）：单个路由数据源、`@CocoDataSource` 声明式切换、分组负载均衡和运行期增删，详见下文“动态多数据源”。

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

## 动态多数据源（可选）

引入多个数据源时，常见做法是为每个数据源各建一套 `SqlSessionFactory`，结果是 MyBatis-Plus 的启动标识重复输出，并且分页、租户隔离、数据权限、SQL 防护等拦截器需要在每套工厂上重复配置。Coco 的方案是只暴露**一个** `@Primary` 的路由数据源 `CocoRoutingDataSource`（继承 Spring 的 `AbstractRoutingDataSource`），MyBatis-Plus 因此只创建一个 `SqlSessionFactory`，所有物理数据源共用同一条 Coco 拦截器链。

该能力**默认关闭**，不改变既有单数据源应用的行为。启用后由 `CocoMybatisPlusDataSourceAutoConfiguration` 装配，它先于 `DataSourceAutoConfiguration` 与 `MybatisPlusAutoConfiguration` 运行，Spring Boot 自带的数据源自动配置随之退让。

```yaml
coco:
  mybatis-plus:
    datasource:
      enabled: true
      primary: master                  # 未指定路由键时使用的数据源
      missing-policy: fail             # 目标数据源不存在：fail（默认）/ fallback-primary
      transaction-policy: reject       # 事务内切换数据源：reject（默认）/ warn / allow
      load-balance-strategy: round-robin   # 分组负载均衡：round-robin（默认）/ random
      group-separator: "_"             # 按名称前缀自动分组，slave_1、slave_2 归入 slave 组
      sources:
        master:
          url: jdbc:mysql://127.0.0.1:3306/app_master
          username: app
          password: ${DB_MASTER_PASSWORD}
          properties:
            maximum-pool-size: 20      # 绑定到连接池（默认 HikariCP）
        slave_1:
          url: jdbc:mysql://127.0.0.1:3306/app_slave_1
          username: readonly
          password: ${DB_SLAVE_PASSWORD}
        slave_2:
          url: jdbc:mysql://127.0.0.1:3306/app_slave_2
          username: readonly
          password: ${DB_SLAVE_PASSWORD}
        report:
          group: analytics             # 也可以显式指定分组
          url: jdbc:mysql://127.0.0.1:3306/app_report
          username: report
          password: ${DB_REPORT_PASSWORD}
```

### 用 `@CocoDataSource` 切换

注解可标注在业务类、业务方法、接口以及 MyBatis Mapper 接口或方法上，方法级优先于类型级。值可以是数据源名、分组名，或以 `#` / `#{...}` 开头的 SpEL 表达式（可引用方法参数）：

```java
@Service
public class OrderQueryService {

    @CocoDataSource("slave")                    // 在 slave 组内按负载均衡策略选择 slave_1 / slave_2
    public OrderView findOrder(Long id) { ... }

    @CocoDataSource("#region + '_db'")          // SpEL，由 CocoDataSourceKeyResolver 求值
    public List<OrderView> listByRegion(String region) { ... }
}

@CocoDataSource("report")                       // 整个 Mapper 默认走 report，单个方法仍可覆盖
public interface ReportMapper extends BaseMapper<ReportEntity> { }
```

切面优先级默认高于 `@Transactional`，因此事务在目标数据源已就绪后才开启。嵌套调用按栈恢复：`A -> B -> A` 返回时会逐层回到上一层的数据源。需要手工控制时使用 `CocoDataSourceContextHolder.runWith(key, runnable)` / `callWith(key, supplier)`；跨线程传递用 `wrap(...)`。

### 扩展点（SPI）

以下接口都带 `@ConditionalOnMissingBean`，声明同类型 Bean 即可替换默认实现：

| 接口 | 默认实现 | 用途 |
|------|----------|------|
| `CocoDataSourceProvider` | `PropertiesCocoDataSourceProvider`（读取 `sources`） | 启动期提供数据源，可从数据库或配置中心加载；可注册多个，按 `Ordered` 排序，同名时后者覆盖前者 |
| `CocoDataSourceCreator` | `DefaultCocoDataSourceCreator`（`DataSourceBuilder` + 属性绑定） | 创建物理数据源，可替换为 Druid 或密文凭据实现 |
| `CocoDataSourceLoadBalancer` | `RoundRobinCocoDataSourceLoadBalancer`、`RandomCocoDataSourceLoadBalancer`（由 `load-balance-strategy` 选择） | 在分组候选中选择具体数据源 |
| `CocoDataSourceKeyResolver` | `DefaultCocoDataSourceKeyResolver` | 把注解中的字面量或 SpEL 解析为路由键 |
| `CocoDataSourceRouter` | `ContextCocoDataSourceRouter`（读取线程上下文栈） | 决定当前路由键，可据租户上下文做无注解路由 |

运行期增删数据源注入 `CocoDataSourceRegistry`（或 `CocoRoutingDataSource`）：

```java
registry.registerDataSource("slave_3", "slave", dataSource);   // 注册或替换，被替换的连接池会被关闭
registry.unregisterDataSource("slave_3");                       // 卸载并关闭；主数据源不可卸载
```

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

### 动态多数据源 `coco.mybatis-plus.datasource`

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 是否启用动态多数据源路由 |
| `primary` | String | `master` | 主数据源名称；必须出现在已加载的数据源中，否则启动失败 |
| `missing-policy` | enum | `fail` | 目标数据源或分组不存在时：`fail` 抛出异常，`fallback-primary` 记录告警并回退主数据源 |
| `transaction-policy` | enum | `reject` | 已激活的本地事务内切换到其他数据源时：`reject` 抛出冲突异常，`warn` 记录告警，`allow` 放行 |
| `load-balance-strategy` | enum | `round-robin` | 分组负载均衡策略：`round-robin`、`random` |
| `group-separator` | String | `_` | 按名称前缀自动分组的分隔符；设为空字符串关闭自动分组 |
| `aspect-order` | int | `Ordered.HIGHEST_PRECEDENCE + 100` | `@CocoDataSource` 切面顺序，需保持高于 `@Transactional` |
| `max-key-length` | int | `128` | 解析后路由键的最大长度，必须为正数 |
| `sources.<name>.url` | String | 无 | JDBC 地址，必填 |
| `sources.<name>.username` / `password` | String | 无 | 数据库凭据，密码建议用环境变量或密钥管理注入 |
| `sources.<name>.driver-class-name` | String | 空 | JDBC 驱动类名，留空由驱动自动发现 |
| `sources.<name>.type` | Class | 空 | 连接池实现类，留空时使用类路径上的默认连接池（HikariCP） |
| `sources.<name>.group` | String | 空 | 显式分组，优先于按前缀自动分组 |
| `sources.<name>.properties.*` | Map | 空 | 绑定到连接池的附加属性，例如 `maximum-pool-size` |

## 边界注意事项

- **拦截器顺序不可随意打乱**：租户、数据权限依赖“先改写主查询、后分页”的顺序。若通过自定义 `MybatisPlusInterceptor` Bean 完全接管装配，需自行保证分页拦截器排在链尾。
- **SQL 防护默认关闭**：升级到本框架不会静默改变既有 SQL 行为；开启 `sql-guard` 属于影响面较大的变更，务必先在预发布环境用真实 SQL 验证。
- **`db-type` 非法会导致启动失败**：`CocoMybatisPlusDbTypeResolver` 无法解析配置文本时抛出 `INVALID_DB_TYPE` 异常。若不确定，保持留空由 MyBatis-Plus 自动推断。
- **定制器 vs 直接注册**：`CocoMybatisPlusInterceptorCustomizer` 注册的拦截器排在直接声明的 `InnerInterceptor` Bean 之后、分页拦截器之前；两者顺序语义不同，注册前需明确目标位置。
- **事务内不能切换物理连接**：Spring 本地事务开启后会绑定首次取得的连接，事务内再切换数据源并不会换连接。默认的 `transaction-policy=reject` 会在这种调用上直接失败；需要跨库一致性时应使用分布式事务或最终一致方案，而不是 `allow`。
- **声明式切换只覆盖同步调用**：路由键保存在线程上下文中，返回 `CompletableFuture`、响应式类型或在其他线程执行的任务不会自动继承；跨线程请用 `CocoDataSourceContextHolder.wrap(...)`。
- **多数据源共用一个 `SqlSessionFactory`**：Mapper 扫描、类型别名、拦截器链和 `GlobalConfig` 对所有数据源一致生效；如果各库需要完全不同的 MyBatis 配置，应自行声明多套 `SqlSessionFactory`，不要启用本能力。
- **配置里的拼写错误不会报错**：`properties.*` 按连接池属性名绑定，未识别的键会被忽略，请核对连接池文档中的属性名。
- **凭据不会写入日志**：数据源定义的 `toString` 对密码与附加属性取值做了脱敏；自定义 `CocoDataSourceCreator` 与日志输出同样应避免打印凭据。



