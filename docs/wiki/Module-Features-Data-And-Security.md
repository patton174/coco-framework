# 数据与安全类 Feature：mybatis-plus / tenant / data-permission / security / audit / openapi

> 本页回答：`coco-features/` 里和数据访问、请求身份、审计、接口文档相关的 6 个模块，各自的公开类型、扩展点、自动配置注册、配置属性与依赖。
> 依据：各模块 `pom.xml`、`src/main/java`、`META-INF` 实读，内容对应仓库 `main` 分支。每节按“坐标与路径 → 职责 → 公开类型 → 扩展点 → 自动配置 → 配置属性 → 依赖 → 测试 → 用户文档”组织。

## 0. 六个模块的关系

| Feature id | 模块 | Feature 依赖（`StandardCocoFeatures`） | Maven 依赖中的 Coco 模块 | 模块级开关（默认值） |
| --- | --- | --- | --- | --- |
| `mybatis-plus` | `coco-mybatis-plus` | 无 | `coco-i18n`、`coco-exception`、`coco-spring-boot-autoconfigure`；optional：`coco-api`、`coco-context` | `coco.mybatis-plus.pagination.enabled`=true |
| `security` | `coco-security` | 无 | `coco-exception`、`coco-i18n`、`coco-context`、`coco-spring-boot-autoconfigure`；测试：`coco-test` | `coco.security.web.enabled`=true |
| `tenant` | `coco-tenant` | `mybatis-plus`、`security` | `coco-exception`、`coco-i18n`、`coco-context`、`coco-spring-boot-autoconfigure`；optional：`coco-mybatis-plus` | `coco.tenant.sql.enabled`=true |
| `data-permission` | `coco-data-permission` | `mybatis-plus`、`security` | 同 tenant | `coco.data-permission.sql.enabled`=**false** |
| `audit` | `coco-audit` | 无 | `coco-i18n`、`coco-logging`、`coco-spring-boot-autoconfigure` | `coco.audit.enabled`=true |
| `openapi` | `coco-openapi` | `web`、`security` | `coco-i18n`、`coco-spring-boot-autoconfigure` | `coco.openapi.enabled`=true |

要点：

- 六个模块都使用 `@ConditionalOnCocoFeature`，所以禁用对应 Feature 后，自动配置在运行期也不会生效；`coco.features.disabled` 的依赖传播（禁用 `mybatis-plus` 会连带禁用 `tenant`、`data-permission`）见 [Feature 机制](Feature-System)。
- `tenant` 与 `data-permission` 在 Maven 里**没有**依赖 `coco-security`，它们的 `security` Feature 依赖只存在于 Feature 计划层（`StandardCocoFeatures`）。对 `coco-mybatis-plus` 的依赖是 optional，MyBatis-Plus 相关的自动配置用 `@ConditionalOnClass` 保护。
- 六个模块都有消息资源包（`coco-feature-<名>-messages`，含默认、`_en_US`、`_zh_CN`），通过各自自动配置里的 `CocoMessageBundleRegistrar` Bean 注册。包名带 `feature`：`io.github.coco.feature.<name>`。
- 每个模块有一个空的标记类 `Coco<Name>Feature`（私有构造器，无成员），不是扩展点。

## 1. coco-mybatis-plus

**坐标与路径**：`coco-features/coco-mybatis-plus`。

**职责**：MyBatis-Plus 分页、SQL 守卫与拦截器组装（pom：pagination, SQL guard, and interceptor composition）。业务 CRUD 仍由开发者或代码生成器产出，这里不做运行时 CRUD。

### 公开类型

| 包（`io.github.coco.feature.mybatisplus.` 之后） | 类型 | 说明 |
| --- | --- | --- |
| （根） | `CocoMybatisPlusAutoConfiguration`、`CocoMybatisPlusProperties`、`CocoMybatisPlusErrorCode` | 自动配置、属性、错误码 |
| `interceptor` | `CocoMybatisPlusInterceptorFactory` | 组装 `MybatisPlusInterceptor` |
| `interceptor` | `CocoMybatisPlusInterceptorCustomizer`（函数式接口，`customize(MybatisPlusInterceptor)`） | **其他模块向拦截器链追加内部拦截器的扩展点**；tenant、data-permission 与分页上下文都通过它接入 |
| `pagination` | `CocoPages` | 工具类：`fromContext()`（用当前 `CocoPageContext` 创建 `Page`，并应用排序）、`of(page, size)`、`toCocoPage(IPage)`（转成 `coco-api` 的 `CocoPage`） |
| `pagination` | `CocoSortable`（注解，`@Target(FIELD)`，`value()` 为排序参数名） | 标记 Entity 字段可排序 |
| `pagination` | `CocoMybatisPlusDbTypeResolver` | `resolve(String)` 把配置字符串解析为 `DbType` |
| `pagination` | `CocoPageContextInnerInterceptor` | `beforeQuery` 里把分页上下文自动注入到查询 |
| `pagination` | `CocoMybatisPlusPaginationProperties`、`CocoPageContextMybatisPlusAutoConfiguration` | 分页属性与上下文接入配置 |
| `sqlguard` | `CocoMybatisPlusSqlGuardProperties` | SQL 守卫属性 |

分页链路：`coco-web` 的 `CocoPageInterceptor` 解析 `page` / `size` / `sort` → `coco-context` 的 `CocoPageContextHolder` → 本模块的 `CocoPageContextInnerInterceptor` 与 `CocoPages` → 结果可转为 `CocoPage`。

### 拦截器组装顺序

`CocoMybatisPlusInterceptorFactory#create` 的顺序：

1. 容器里所有 `InnerInterceptor` Bean（按 `orderedStream()`）。
2. 所有 `CocoMybatisPlusInterceptorCustomizer`（按 `orderedStream()`）。
3. SQL 守卫：`sql-guard.block-attack-enabled` 为 true 时加 `BlockAttackInnerInterceptor`；`illegal-sql-enabled` 为 true 时加 `IllegalSQLInnerInterceptor`；并在日志里输出生产环境建议。
4. 分页：`pagination.enabled` 为 true 时**最后**追加 `PaginationInnerInterceptor`，源码注释的原因是避免分页插件提前改写 SQL。

### 扩展点

| 想做什么 | 做法 |
| --- | --- |
| 追加自己的拦截器 | 声明 `CocoMybatisPlusInterceptorCustomizer` Bean，或直接声明 `InnerInterceptor` Bean |
| 整体替换拦截器 | 声明 `MybatisPlusInterceptor` Bean（`mybatisPlusInterceptor` 带 `@ConditionalOnMissingBean`） |
| 关闭分页上下文自动注入 | `coco.mybatis-plus.pagination.context.enabled=false` |

### 自动配置

`AutoConfiguration.imports`：

1. `io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration`：`after MybatisPlusAutoConfiguration`、`before MybatisPlusInnerInterceptorAutoConfiguration`；`@ConditionalOnCocoFeature(MYBATIS_PLUS)` + `@ConditionalOnClass(MybatisPlusInterceptor)`；Bean：`cocoMybatisPlusMessageBundleRegistrar`、`mybatisPlusInterceptor`。
2. `io.github.coco.feature.mybatisplus.pagination.CocoPageContextMybatisPlusAutoConfiguration`：`after CocoMybatisPlusAutoConfiguration`；`coco.mybatis-plus.pagination.context.enabled` 缺省 true；Bean `cocoPageContextInterceptorCustomizer`。

当该 Feature 被禁用时，`CocoFeatureAutoConfigurationImportFilter` 还会过滤 MyBatis-Plus 官方的 5 个自动配置（见 [Spring 组合层](Module-Spring)）。构建期 `StandardCocoFeatures` 对该 Feature 的裁剪集合除 `coco-mybatis-plus` 外还包括 `mybatis`、`mybatis-spring` 与一组 `mybatis-plus-*` 第三方 artifact（见 [构建模块](Module-Build)）。

### 配置属性（前缀 `coco.mybatis-plus`）

| 键 | 默认值 |
| --- | --- |
| `pagination.enabled` | `true` |
| `pagination.db-type` | 空（自动解析） |
| `pagination.overflow` | `false` |
| `pagination.max-limit` | 空 |
| `pagination.optimize-join` | `true` |
| `pagination.context.enabled` | 缺省 `true`（条件属性，无字段） |
| `sql-guard.block-attack-enabled` | `false` |
| `sql-guard.illegal-sql-enabled` | `false` |

有 `META-INF/additional-spring-configuration-metadata.json`。

**测试**：2 个 `*Test`。**用户文档**：[mybatis-plus](https://github.com/patton174/coco-framework/blob/main/website/docs/features/mybatis-plus.md)、[pagination](https://github.com/patton174/coco-framework/blob/main/website/docs/features/pagination.md)。

## 2. coco-security

**坐标与路径**：`coco-features/coco-security`。

**职责**：安全上下文 Feature（pom：Security context feature implementation）。它提供“当前主体是谁”的抽象与 Web 入口，**不是认证实现**——认证方案、权限模型属于下游应用（见 [`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)）。

### 公开类型

| 包（`io.github.coco.feature.security.` 之后） | 类型 | 说明 |
| --- | --- | --- |
| （根） | `CocoSecurity` | 静态入口：`current()`、`principal()`、`isAuthenticated()`、`requireCurrent()`、`requireAuthenticated()`、`requirePrincipal()`、`hasRole(role)`、`requireRole(role)`、`hasPermission(permission)`、`requirePermission(permission)` |
| （根） | `CocoSecurityAutoConfiguration`、`CocoSecurityProperties`、`CocoSecurityErrorCode` | 自动配置、属性、错误码 |
| `context` | `CocoSecurityContext`（record：`principal`、`authenticated`；`authenticated(principal)`、`anonymous()`） | 安全上下文 |
| `context` | `CocoSecurityPrincipal`（record；`of(principalId, principalName)`、`hasRole`、`hasPermission`、`attribute`） | 主体 |
| `context` | `CocoSecurityContextHolder` | `ThreadLocal` 持有器，带 `capture` / `restore` / `wrap` / `runWithContext` / `callWithContext` |
| `context` | `CocoSecurityContextResolver`（函数式接口，`Optional<CocoSecurityContext> resolve()`）、`HolderCocoSecurityContextResolver` | 非 Web 场景的上下文解析，默认读 Holder |
| `web` | `CocoSecurityWebFilter` | Servlet 过滤器：用 `CocoWebSecurityContextResolver` 解析并写入 Holder |
| `web` | `CocoWebSecurityContextResolver`（函数式接口，`resolve(HttpServletRequest)`）、`HeaderCocoWebSecurityContextResolver` | **Web 场景的扩展点**；默认实现从上游 HTTP 头读取主体 |
| `web` | `CocoSecurityWebProperties`、`CocoSecurityWebHeaderProperties` | 属性 |

### 扩展点与安全注意

- 业务接入自己的认证：声明 `CocoWebSecurityContextResolver` Bean（Servlet 场景，`@ConditionalOnMissingBean`），或在认证成功后自己调用 `CocoSecurityContextHolder.set(...)`；非 Web 场景声明 `CocoSecurityContextResolver` Bean。
- 默认的 `HeaderCocoWebSecurityContextResolver` 信任上游传入的头（`X-Coco-Principal-Id`、`X-Coco-Principal-Name`、`X-Coco-Roles`、`X-Coco-Permissions`）。头解析**默认关闭**（`web.header.enabled=false`），开启时自动配置会输出 WARN：该解析器不得在没有可信网关的情况下直接暴露公网。需要提供自定义解析器 Bean 才会消除告警。

### 自动配置

`io.github.coco.feature.security.CocoSecurityAutoConfiguration`：`@ConditionalOnCocoFeature(SECURITY)`；Bean：`cocoSecurityMessageBundleRegistrar`、`cocoSecurityContextResolver`（默认 Holder）、`cocoWebSecurityContextResolver`（`@ConditionalOnClass(Filter)` + Servlet）、`cocoSecurityWebFilterRegistration`（`FilterRegistrationBean<CocoSecurityWebFilter>`，名称 `cocoSecurityWebFilter`，顺序 `HIGHEST_PRECEDENCE + 5`，条件 `coco.security.web.enabled` 缺省 true）。本模块没有 `additional-spring-configuration-metadata.json`（属性元数据由注解处理器生成）。

### 配置属性（前缀 `coco.security`）

| 键 | 默认值 |
| --- | --- |
| `web.enabled` | `true` |
| `web.header.enabled` | `false` |
| `web.header.principal-id-header-name` | `X-Coco-Principal-Id` |
| `web.header.principal-name-header-name` | `X-Coco-Principal-Name` |
| `web.header.roles-header-name` | `X-Coco-Roles` |
| `web.header.permissions-header-name` | `X-Coco-Permissions` |
| `web.header.authority-delimiter` | `","` |

**测试**：3 个 `*Test`；以 `test` 作用域依赖 `coco-test`（仓库里目前唯一的使用者）。**用户文档**：[security-context](https://github.com/patton174/coco-framework/blob/main/website/docs/features/security-context.md)。

## 3. coco-tenant

**坐标与路径**：`coco-features/coco-tenant`。**职责**：租户上下文与 MyBatis-Plus 租户 SQL 隔离。租户模型（租户从哪来、怎么存）由业务决定，框架只提供上下文与 SQL 注入。

### 公开类型（`io.github.coco.feature.tenant.` 之后）

| 包 | 类型 | 说明 |
| --- | --- | --- |
| （根） | `CocoTenantAutoConfiguration`、`CocoTenantProperties`、`CocoTenantErrorCode` | 自动配置、属性、错误码 |
| `context` | `CocoTenantContext`（record：`tenantId`、`tenantName`、`attributes`；`of(id, name)`、`attribute(name)`） | 租户上下文 |
| `context` | `CocoTenantContextHolder` | `current()`、`requireCurrent()`、`set`、`clear`、`capture`、`restore`、`wrap*`、`runWithContext`、`callWithContext` |
| `context` | `CocoTenantContextResolver`（函数式接口，`Optional<CocoTenantContext> resolve()`）、`HolderCocoTenantContextResolver` | **扩展点**：自定义租户来源（如从 JWT / 请求头取） |
| `sql` | `CocoTenantLineHandler` | 把租户上下文转换为 MyBatis-Plus `TenantLineHandler` 所需的字段名、租户值、忽略表 |
| `sql` | `CocoTenantIdExpressionResolver`（函数式接口，`Expression resolve(CocoTenantContext)`）、`DefaultCocoTenantIdExpressionResolver` | **扩展点**：租户值的 SQL 表达式 |
| `sql` | `CocoTenantInterceptorIgnoreGuard`（`InnerInterceptor`）、`CocoTenantInterceptorIgnoreProperties`、`CocoTenantInterceptorIgnoreDecision`、`CocoTenantInterceptorIgnoreEvent`、`CocoTenantInterceptorIgnoreEventPublisher`（函数式接口）、`NoOpCocoTenantInterceptorIgnoreEventPublisher` | **忽略治理**：检测 `@InterceptorIgnore(tenantLine = true)` 或线程级忽略，避免租户隔离被静默绕过；事件发布器可桥接到审计 / 告警 / 指标 |
| `sql` | `CocoTenantSqlProperties`、`CocoTenantMybatisPlusAutoConfiguration` | 属性与接入配置 |

### 自动配置

`AutoConfiguration.imports`：

1. `io.github.coco.feature.tenant.CocoTenantAutoConfiguration`：`@ConditionalOnCocoFeature(TENANT)`；Bean：`cocoTenantMessageBundleRegistrar`、`CocoTenantContextResolver`（默认 Holder）。
2. `io.github.coco.feature.tenant.sql.CocoTenantMybatisPlusAutoConfiguration`：`after {CocoTenantAutoConfiguration, CocoMybatisPlusAutoConfiguration}`；`@ConditionalOnCocoFeature(TENANT)`；`@ConditionalOnClass({TenantLineInnerInterceptor, CocoMybatisPlusInterceptorCustomizer})`；`coco.tenant.sql.enabled` 缺省 true；Bean：`CocoTenantIdExpressionResolver`、`cocoTenantMybatisPlusInterceptorCustomizer`（先加 `CocoTenantInterceptorIgnoreGuard`，再加 `TenantLineInnerInterceptor(CocoTenantLineHandler)`；事件发布器通过 `ObjectProvider` 取，缺省 `NoOp`）。

### 配置属性（前缀 `coco.tenant.sql`）

| 键 | 默认值 |
| --- | --- |
| `enabled` | `true` |
| `tenant-id-column` | `"tenant_id"` |
| `ignore-tables` | 空 |
| `fail-on-missing-context` | `true`（缺租户上下文默认失败） |
| `interceptor-ignore.block-unlisted` | `true` |
| `interceptor-ignore.allowed-mapped-statements` | 空 |

忽略表与“放行的 MappedStatement”都需要显式配置。有 `additional-spring-configuration-metadata.json`。**测试**：3 个 `*Test`。**用户文档**：[tenant](https://github.com/patton174/coco-framework/blob/main/website/docs/features/tenant.md)。

## 4. coco-data-permission

**坐标与路径**：`coco-features/coco-data-permission`。**职责**：数据权限上下文与 MyBatis-Plus 数据权限 SQL 条件。SQL 注入默认关闭。

### 公开类型（`io.github.coco.feature.datapermission.` 之后）

| 包 | 类型 | 说明 |
| --- | --- | --- |
| （根） | `CocoDataPermissionAutoConfiguration`、`CocoDataPermissionProperties`、`CocoDataPermissionErrorCode` | 自动配置、属性、错误码 |
| `context` | `CocoDataPermissionContext`（record：`rules`；`of(rules)`、`empty()`、`rule(resource)`） | 数据权限上下文 |
| `context` | `CocoDataPermissionRule`（record：`resource`、`scope`、`values`；`all(resource)`、`deny(resource)`） | 单个资源的规则 |
| `context` | `CocoDataScope`（枚举：`ALL`、`SELF`、`CUSTOM`、`DENY`） | 数据范围 |
| `context` | `CocoDataPermissionContextHolder`、`CocoDataPermissionContextResolver`（函数式接口）、`HolderCocoDataPermissionContextResolver` | Holder 带 `capture` / `restore` / `wrap*` / `runWithContext` / `callWithContext`；Resolver 是自定义规则来源的扩展点 |
| `mybatisplus` | `CocoMybatisPlusDataPermissionHandler`、`CocoDataPermissionMybatisPlusAutoConfiguration` | 接入 MyBatis-Plus `DataPermissionInterceptor` |
| `sql` | `CocoDataPermissionSqlPredicateProvider`（函数式接口，`Optional<Expression> predicate(CocoDataPermissionSqlPredicateContext)`）、`DefaultCocoDataPermissionSqlPredicateProvider` | **扩展点**：生成 SQL 谓词 |
| `sql` | `CocoDataPermissionSqlResourceResolver`（函数式接口，`Optional<String> resolve(CocoDataPermissionSqlResourceContext)`）、`PropertyCocoDataPermissionSqlResourceResolver` | **扩展点**：把 SQL 表映射到资源名；默认读 `sql.resources` 配置 |
| `sql` | `CocoDataPermissionMissingContextPolicy`（`THROW`、`DENY`、`IGNORE`）、`CocoDataPermissionMissingRulePolicy`（`DENY`、`IGNORE`）、`CocoDataPermissionSqlColumnType`（`STRING`、`LONG`） | 策略与列类型 |
| `sql` | `CocoDataPermissionSqlProperties`、`CocoDataPermissionSqlResourceProperties`、`CocoDataPermissionSqlPredicateContext`、`CocoDataPermissionSqlResourceContext` | 属性与上下文 |

### 自动配置

1. `io.github.coco.feature.datapermission.CocoDataPermissionAutoConfiguration`：`@ConditionalOnCocoFeature(DATA_PERMISSION)`；Bean：`cocoDataPermissionMessageBundleRegistrar`、`CocoDataPermissionContextResolver`（默认 Holder）。
2. `io.github.coco.feature.datapermission.mybatisplus.CocoDataPermissionMybatisPlusAutoConfiguration`：`after {CocoDataPermissionAutoConfiguration, CocoMybatisPlusAutoConfiguration}`；`@ConditionalOnCocoFeature(DATA_PERMISSION)`；`@ConditionalOnClass(name = {DataPermissionInterceptor, CocoMybatisPlusInterceptorCustomizer})`；Bean：`CocoDataPermissionSqlResourceResolver`、`CocoDataPermissionSqlPredicateProvider`，以及仅在 `coco.data-permission.sql.enabled=true` 时创建的 `cocoDataPermissionMybatisPlusInterceptorCustomizer`（加 `DataPermissionInterceptor(CocoMybatisPlusDataPermissionHandler)`）。

### 配置属性（前缀 `coco.data-permission.sql`）

| 键 | 默认值 |
| --- | --- |
| `enabled` | `false` |
| `missing-context-policy` | `THROW`（缺上下文时抛框架异常；`DENY` 追加永假条件；`IGNORE` 不加条件） |
| `missing-rule-policy` | `DENY`（缺资源规则时追加永假条件；`IGNORE` 不加条件） |
| `resources.<name>.tables` | 空列表 |
| `resources.<name>.column` | 无 |
| `resources.<name>.column-type` | `STRING`（另有 `LONG`） |

有 `additional-spring-configuration-metadata.json`。**测试**：4 个 `*Test`。**用户文档**：[data-permission](https://github.com/patton174/coco-framework/blob/main/website/docs/features/data-permission.md)。

## 5. coco-audit

**坐标与路径**：`coco-features/coco-audit`。**职责**：审计事件发布管道、默认结构化日志、格式化与记录器 SPI，以及把访问日志转成审计事件的适配器。设计规格：`coco-support/coco-document/superpowers/specs/2026-07-10-coco-audit-feature-independence.md`、`2026-07-10-coco-default-audit-logging.md`。

### 公开类型（`io.github.coco.feature.audit.` 之后）

| 包 | 类型 | 说明 |
| --- | --- | --- |
| （根） | `CocoAuditAutoConfiguration`、`CocoAuditProperties` | 自动配置与属性 |
| `core` | `CocoAuditEvent`（不可变；`builder(type)`，可选字段 `action`、`resourceType`、`resourceId`、`traceId`、`actor`、`tenantId`、`success`、`occurredAt`、`attribute(name, value)`） | 审计事件 |
| `core` | `CocoAuditPublisher`（函数式接口，`publish(event)`）、`CompositeCocoAuditPublisher` | 发布器；组合发布器把事件分发给所有记录器 |
| `core` | `CocoAuditRecorder`（函数式接口，`record(event)`）、`LoggingCocoAuditRecorder`、`NoOpCocoAuditRecorder` | **记录器 SPI**：业务可注册多个，写数据库 / MQ 等 |
| `core` | `CocoAuditFormatter`（函数式接口，`String format(event)`）、`DefaultCocoAuditFormatter` | **格式化 SPI** |
| `core` | `CocoAuditErrorHandler`（函数式接口，`handle(event, recorder, failure)`）、`PolicyCocoAuditErrorHandler`、`CocoAuditFailurePolicy`（`IGNORE`、`THROW`） | 记录失败处理 |
| `accesslog` | `CocoAccessLogAuditRecorder` | 把 `coco-logging` 的 `CocoAccessLogRecorder` 适配为审计事件 |

### 自动配置

`io.github.coco.feature.audit.CocoAuditAutoConfiguration`：`@AutoConfiguration(after = CocoCommonLoggingAutoConfiguration)`；`@ConditionalOnCocoFeature(AUDIT)`。Bean（都带 `@ConditionalOnMissingBean` 并受 `coco.audit.enabled` 缺省 true 控制）：

- `cocoAuditLogHandleRegistrar`：注册审计日志句柄（`coco.audit.logging.enabled`）。
- `cocoAuditFormatter`：`CocoAuditFormatter`。
- `cocoAuditRecorder`：默认日志记录器，要求容器里有 `CocoLogManager` 且 `coco.audit.logging.enabled`；已有其他 `CocoAuditRecorder` 时不创建。
- `cocoAuditErrorHandler`：按 `failure-policy` 处理。
- `cocoAuditPublisher`：`@ConditionalOnBean(CocoAuditRecorder)`，收集所有 `CocoAuditRecorder`。
- `cocoAccessLogAuditRecorder`：`CocoAccessLogRecorder`，条件 `coco.audit.access-log.enabled` 缺省 true，依赖 `CocoAuditPublisher`。

### 配置属性（前缀 `coco.audit`）

| 键 | 默认值 |
| --- | --- |
| `enabled` | `true` |
| `failure-policy` | `IGNORE`（另有 `THROW`） |
| `logging.enabled` | `true` |
| `logging.logger-name` | `io.github.coco.audit` |
| `logging.level` | `INFO` |
| `access-log.enabled` | `true` |

有 `additional-spring-configuration-metadata.json`。**测试**：3 个 `*Test`。**用户文档**：[audit](https://github.com/patton174/coco-framework/blob/main/website/docs/features/audit.md)。

## 6. coco-openapi

**坐标与路径**：`coco-features/coco-openapi`。**职责**：OpenAPI 元数据提供器；在 classpath 上有 SpringDoc 时把 Coco 元数据适配进去（见 [`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)）。模块自身不依赖 SpringDoc。

### 公开类型（`io.github.coco.feature.openapi.` 之后）

| 包 | 类型 | 说明 |
| --- | --- | --- |
| （根） | `CocoOpenApiAutoConfiguration`、`CocoOpenApiProperties` | 自动配置与属性 |
| `core` | `CocoOpenApiMetadata`（record：`title`、`version`、`description`） | 元数据 |
| `core` | `CocoOpenApiMetadataProvider`（函数式接口，`metadata()`）、`DefaultCocoOpenApiMetadataProvider` | **扩展点**：自定义元数据来源 |
| `springdoc` | `CocoSpringDocOpenApiCustomizerFactoryBean`（`FactoryBean<Object>`） | 用代理按名称适配 `org.springdoc.core.customizers.OpenApiCustomizer`，避免编译期依赖 SpringDoc |

### 自动配置

`io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration`：`afterName` 为 `CocoWebAutoConfiguration` 与 `CocoSecurityAutoConfiguration`；`@ConditionalOnCocoFeature(OPENAPI)`；Bean：`cocoOpenApiMessageBundleRegistrar`、`CocoOpenApiMetadataProvider`（条件 `coco.openapi.enabled` 缺省 true）、`cocoSpringDocOpenApiCustomizer`（需要 `OpenApiCustomizer`、`OpenAPI`、`Info` 三个类在 classpath 上，且 `coco.openapi.springdoc.enabled` 缺省 true）。

### 配置属性（前缀 `coco.openapi`）

| 键 | 默认值 |
| --- | --- |
| `enabled` | `true` |
| `info.title` | `"Coco API"` |
| `info.version` | `"1.0.0"` |
| `info.description` | `"Coco Framework API"` |
| `springdoc.enabled` | `true` |

有 `additional-spring-configuration-metadata.json`。**测试**：2 个 `*Test`（另有 3 个非 `*Test` 命名的辅助 Java 文件）。**用户文档**：[openapi](https://github.com/patton174/coco-framework/blob/main/website/docs/features/openapi.md)。

## 7. 相关页面

- [模块参考总览](Module-Overview)
- [coco-web：Web 运行时](Module-Web)
- [基础设施类 Feature](Module-Features-Infrastructure)
- [Spring 组合层](Module-Spring)
- [Feature 机制：构建期与运行期](Feature-System)
- [约定与规范](Conventions)
- [指南：新增一个 Feature 模块](Guide-Add-Feature-Module)
