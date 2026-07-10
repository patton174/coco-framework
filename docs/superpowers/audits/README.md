# Coco Framework 审计整改总控路线图

- 日期：2026-07-09
- 范围：整合本目录下三份审计报告，形成可执行整改队列
- 输入：
  - `2026-07-09-coco-framework-audit.md`
  - `2026-07-09-coco-coupling-audit.md`
  - `2026-07-09-coco-business-architecture-audit.md`

## 定位边界

Coco Framework 的目标是帮助业务项目快速搭建生产可用的 Spring Boot Web Server。它可以服务 SaaS 系统，但不只服务 SaaS；它提供的是基础设施级顶层封装，不是零代码业务运行时。

整改时保持以下边界：

- 保留“一 starter 快速接入”的默认体验，避免让首用者在多个 starter 中做早期选择。
- starter 只组合能力，不承载功能行为；真正行为仍在 feature 模块、配置、SPI 或业务代码中。
- 安全、请求上下文、统一异常、i18n、访问日志、签名、加密、防重放、租户隔离、数据权限等基础设施应尽量开箱可靠。
- CRUD、业务模型、事务边界、自定义查询和权限模型不做运行时魔法封装，应由业务代码或代码生成器产出可读源码后由业务方接管。
- 已公开 API 不做无迁移路径的破坏性删除；如果需要收敛，先判断发布状态，再选择内部化、废弃或兼容迁移。

## 状态定义

| 状态 | 含义 |
| --- | --- |
| accepted | 结论成立，进入近期整改队列。 |
| adjusted | 问题成立，但审计建议需要按 Coco 定位调整后执行。 |
| deferred | 问题成立，但依赖前置工作或不属于 v1.0.x 闸门。 |
| refuted | 与项目定位冲突，或证据不足，不执行。 |

上表用于审计结论决策；下方 PR 队列中的 `done` 是实施状态，两者不是同一状态机。

## 归并决策

| 主题 | 来源 | 状态 | 决策 |
| --- | --- | --- | --- |
| starter 装包过重 | framework A1, coupling M1 | adjusted | 不立即拆掉默认 Web starter。先在文档中明确 starter 是组合入口，后续可新增 `coco-core-starter` 或 `coco-web-starter`，但不破坏默认快速接入体验。 |
| `coco-feature-security` 仅为骨架 | framework A2, coupling M3 | adjusted | PR26 补 Web bridge、配置入口、可信请求头适配和文档边界；不直接补成完整 RBAC，避免文档承诺超过实现。 |
| 数据权限 SQL 数值列 | framework A3 | accepted | v1.0.x 必修。修默认谓词生成器或显式拒绝非文本列，并补单测。 |
| 租户旁路无审计 | framework A4 | accepted | v1.0.x 必修。增加旁路白名单、告警和审计事件边界。 |
| ThreadLocal 上下文传播 | framework A5 | accepted | v1.0.x 必修。提供通用传播原语和 sample 适配器，不强制业务登录模型。 |
| Maven 注解扫描器缺测试 | framework A6 | accepted | 第一批代码 PR。风险小、收益高。 |
| Replay 默认 store 单进程 | framework B1 | accepted | PR6 增加启动 WARN；PR40 补集群接入文档和显式 JDBC 共享参考实现，同时保留 `CocoReplayStore` 替换边界。 |
| TraceId 无校验 | framework B2 | accepted | 增加 validator，默认限制字符集和长度。 |
| Replay 使用客户端时间计算过期 | framework B3 | accepted | 改为服务端入站时间加 TTL，并增加时钟偏差配置。 |
| Replay 清理在写路径 | framework B4, B13 | accepted | PR27 将默认内存 replay store 的过期键清理移出请求写路径，改为懒启动后台守护线程清理。 |
| 加密异常错误码过粗 | framework B5 | accepted | PR28 将加密请求格式错误映射为 400，将密文认证/完整性失败保留为 401，并补回归测试。 |
| SQL 防护默认关闭 | framework B6 | adjusted | 不直接改默认值，PR18 已补生产建议、启动 INFO 和中英文 README 说明，避免误伤现有合法 SQL。 |
| 过滤器顺序可被消耗 CPU | framework B7 | accepted | PR29 增加 replay 请求形态预检过滤器，先做缺字段和时间戳格式粗筛，再进入签名、解密和 replay store。 |
| 客户端断开误报 500 | framework B8, D31 | accepted | `CocoWebExceptionHandler` 识别 Spring 客户端断开异常并透传，避免统一响应和异常日志误报。 |
| 过滤器异常响应请求上下文 | framework B9, quality D35 | accepted | PR20 改为显式传递请求语言，不再临时替换 `RequestContextHolder`；回归测试覆盖旧上下文保留和 `Accept-Language` 本地化。 |
| Trace MDC 恢复语义 | framework B11, quality D37 | accepted | PR19 已确认显式 null 恢复逻辑，并补默认和自定义 MDC key 的请求内覆盖、请求后恢复回归测试。 |
| 正常响应包装大响应阈值 | framework B12 | accepted | PR23 增加 `coco.web.response-wrap.max-body-bytes`，对已知长度超过阈值的正常响应跳过统一包装并输出 WARN。 |
| `CocoWebAutoConfiguration` 过大 | coupling M4 | accepted | 架构治理批次执行，按子域拆配置类。 |
| `web.context` god package | coupling M7 | deferred | 等自动配置拆分后再拆包，降低一次性改动范围。 |
| `web.security.metadata` 命名冲突 | coupling M9 | accepted | 重命名为请求元数据语义，例如 `web.request.metadata`。 |
| `CocoRequestContext` 过大 | coupling M5, M10 | deferred | 属于公共上下文结构演进，不抢安全修复窗口。 |
| sample 缺事务边界 | business B1 | accepted | sample 修正批次执行。 |
| sample 仓储抛业务异常 | business B2 | accepted | sample 修正批次执行。 |
| `CocoConfigurer` / `CocoFeatureRegistry` 价值不足 | business B3, framework C1-C2 | adjusted | 先评估发布兼容性。未稳定发布则内部化；已发布则废弃并给迁移路径。 |
| `CocoFeature` enum 封闭 | business B4 | deferred | 放到 v1.1 API 演进，不影响 v1.0.x 安全闸门。 |
| sample repository 职责混杂 | business B5 | accepted | sample 修正批次执行。 |
| sample i18n bundle 错位 | business B6 | accepted | sample 修正批次执行，并同步检查 security messages。 |
| sample secure endpoint 仅 URL 装饰 | business B7 | accepted | sample 修正批次执行，避免业务方误解端点名参与鉴权。 |
| audit/openapi/codegen 是占位 SPI | framework C10 | accepted | PR9 先收敛文档；OpenAPI 已有真实消费者，PR37 补齐 Codegen，PR38 为 Audit 增加默认结构化日志且保留持久化 SPI。 |
| OpenAPI 元数据 provider 无消费者 | framework C3 | accepted | PR30 增加可选 SpringDoc 元数据适配器；SpringDoc 在业务项目 classpath 中存在时自动把 Coco 元数据写入 `OpenAPI.info`，框架不强依赖 SpringDoc。 |
| MyBatis-Plus 自动配置字符串排序 | framework C4 | accepted | PR31 将 Coco MyBatis-Plus、租户和数据权限的自动配置排序改为类型安全引用，并补测试防止回退到 `afterName` / `beforeName` 字符串类名。 |
| Web 安全输入解析器缺直接测试 | framework C5 | accepted | PR32 补 `DefaultCocoWebRequestSecurityInputResolver` 直接测试，固定签名 / 加密 / 防重放过滤器共享输入快照边界。 |
| sample parent 版本写死 | framework C6 | adjusted | PR33 保留 Maven parent 必需的字面版本，但新增跨平台脚本和 CI 闸门，校验 sample parent 版本必须等于根 POM `revision`。 |
| audit/openapi/codegen 扩展边界验证 | framework C17 | accepted | PR24 补充 audit 发布链路、OpenAPI 元数据归一化和 codegen 替换生成器的端到端行为测试。 |
| 功能解析缺少可观测性 | framework C13, C14 | accepted | 补充最终功能计划日志、配置源摘要和依赖传播禁用诊断，避免 feature 被静默禁用后难以排查。 |
| 包裁剪缺少原始备份和运行形态断言 | framework C12, C18 | accepted | 裁剪前保留 `target/coco-prune.original.jar`，并在测试中断言裁剪后仍保留 Spring Boot 可执行 jar 关键结构。 |
| Maven Enforcer 发布闸门 | framework C7 | accepted | PR21 已在根 POM 增加 enforcer：常规构建检查依赖收敛、重复依赖声明和直接禁用依赖；release profile 拒绝 SNAPSHOT 版本。 |
| CI 跨平台矩阵 | framework C8 | accepted | PR22 将 verify job 扩展到 Ubuntu / Windows / macOS，并显式区分 JDK 21 toolchain 与 Java 17 编译目标。 |
| 完整数据库业务示例缺失 | framework C9 | accepted | PR35 新增 H2 + MyBatis-Plus full sample，跑通安全、租户、数据权限和审计链路。 |
| sample 安全材料硬编码 | framework C11 | accepted | PR36 将签名密钥和 AES key 改为环境变量或黑盒运行时临时生成，提交的 Postman 资产不再保存固定材料。 |
| 数据权限 SQL 关键路径缺测试 | framework C16, C20 | accepted | 补充资源解析器直接单测，并覆盖 missing-rule IGNORE 与 schema-qualified table 的 handler 行为。 |
| Maven 运行期 artifact 解析回退缺测试 | framework C19, quality D25 | accepted | 补充 `CocoFeaturesMojo` 单测，覆盖 Resolver 不可用和 artifact 解析失败时只保留 model dependency、不污染已解析 classpath。 |
| 安全上下文持有器边界测试缺口 | framework C15 | accepted | 补充 `CocoSecurityContextHolder` 线程隔离、异常恢复、缺上下文和 null 入参负向测试。 |
| Failsafe 声明但未绑定 | framework C21, quality D23 | accepted | PR34 将 failsafe 绑定到 `integration-test` / `verify`，并补 starter servlet 应用上下文烟测 IT。 |
| BodyCache query 触发参数解析误判 | framework B10, quality D36 | accepted | 改用 Spring URI query 解析读取触发参数名，保留 malformed query 回退，并补充编码参数名回归测试。 |
| `coco-test` 未被使用 | framework C22, quality D22 | accepted | PR25 将 `CocoTestSupport` 落成共享配置元数据测试工具，并让 `coco-feature-codegen` 测试以 test-scope 方式实际引入。 |

## PR 队列

### PR 1：审计收敛文档

状态：done。已建立本路线图，并把三份审计报告归并为可执行队列。

目标：建立本路线图，后续执行按此看板推进。

范围：

- 新增审计整改总控文档。
- 给重复审计项归并状态。
- 定义后续 PR 顺序和验收命令。

验收：

```powershell
git diff --check
codegraph sync .
codegraph status .
```

### PR 2：sample 正确示范

状态：done。sample 已补事务边界、仓储职责拆分、i18n 和 secure endpoint 语义说明。

目标：让业务方复制 sample 时不会复制错误分层和事务边界。

范围：

- `SampleOrderApplicationService` 增加事务边界示范。
- 产品存在性、库存校验从仓储实现上移到应用层。
- 拆分订单仓储和商品仓储职责。
- 修正 sample i18n bundle。
- 明确 secure endpoint 是过滤器路径匹配示例。
- 检查 `coco-samples/coco-sample-basic/pom.xml` 版本一致性，但不得混入无关版本漂移。

验收：

```powershell
mvn -B install
mvn -B -f coco-samples/coco-sample-basic/pom.xml verify
python coco-samples/coco-sample-basic/scripts/verify_business_flow.py
git diff --check
codegraph sync .
```

### PR 3：Maven feature scanner 测试

状态：done。已为构建期 `@CocoFeatures` 扫描器补充 fixture 测试。

目标：防止构建期 `@CocoFeatures` 扫描静默失效。

范围：

- 为 `CocoAnnotatedFeatureScanner.scan()` 增加 fixture 测试。
- 覆盖顶层类、内部类忽略、无法加载类不影响扫描等路径。

验收：

```powershell
mvn -pl :coco-maven-plugin -am test
git diff --check
codegraph sync .
```

### PR 4：数据权限 SQL 正确性

状态：done。默认数据权限谓词已按列类型处理，并覆盖字符串、数值、空值和 schema-qualified table 场景。

目标：修复默认数据权限谓词对数值列不可靠的问题。

范围：

- 明确数据权限规则值类型来源。
- 若暂时无法可靠推断列类型，则默认 provider 对非文本列显式拒绝并提示自定义 provider。
- 为数值列、字符串列、空值、schema-qualified table 增加测试。

验收：

```powershell
mvn -pl :coco-feature-data-permission -am test
git diff --check
codegraph sync .
```

### PR 5：租户旁路治理

状态：done。租户旁路已增加白名单、阻断开关、WARN 日志和审计事件发布边界。

目标：`@InterceptorIgnore(tenantLine=true)` 不再静默绕过租户隔离。

范围：

- 在租户配置中增加旁路白名单和阻断开关。
- 旁路命中时输出 WARN。
- 预留审计事件发布点，但不强耦合 `coco-feature-audit`。
- 文档明确该注解的生产风险。

验收：

```powershell
mvn -pl :coco-feature-tenant -am test
git diff --check
codegraph sync .
```

### PR 6：Web trace / replay 安全硬化

状态：done。已增加 traceId 校验、服务端时间 TTL、时钟偏差配置和默认 replay store 风险提示。

目标：修复 TraceId 输入和 Replay 防重放的默认安全缺口。

范围：

- 增加 `CocoTraceIdValidator` 和配置项。
- Trace filter 与 canonicalizer 使用同一校验逻辑。
- Replay 过期时间改用服务端入站时间。
- 增加时钟偏差配置。
- 默认 `InMemoryCocoReplayStore` 启动时提示单进程风险。

验收：

```powershell
mvn -pl :coco-feature-web -am test
git diff --check
codegraph sync .
```

### PR 7：上下文传播与入口适配

状态：done。common context、security、tenant、data-permission 已提供 capture / restore / wrap 传播原语。

目标：让 security / tenant / data-permission 的上下文使用方式可复制、可跨线程传播。

范围：

- 在 common context 或各 feature holder 中补齐统一的 `capture` / `restore` / `wrap` 原语。
- 提供 Servlet 入口适配样例，展示从请求主体设置上下文并清理。
- 提供 `@Async` / executor 包装示例。
- 不引入固定用户、角色、菜单、租户模型。

验收：

```powershell
mvn -pl :coco-common-context -am test
mvn -pl :coco-feature-security -am test
git diff --check
codegraph sync .
```

### PR 8：Web 自动配置拆分

状态：done。`CocoWebAutoConfiguration` 已拆为 context、body、trace、signature、encryption、replay、response、exception、i18n 等子配置。

目标：降低 `coco-feature-web` 入口类复杂度，为后续管线独立测试做准备。

范围：

- 将 `CocoWebAutoConfiguration` 拆成 trace、context、body、signature、encryption、replay、response、exception、accesslog 等配置类。
- 原入口类只保留聚合导入或兼容入口。
- 保持 bean 名称和条件装配行为兼容。

验收：

```powershell
mvn -pl :coco-feature-web -am test
git diff --check
codegraph sync .
```

### PR 9：命名与文档承诺收敛

状态：done。`web.security.metadata` 已收敛为 `web.request.metadata`，README 已将 security / audit / openapi / codegen 边界改为当前实现可承诺的表达。

目标：消除“security”双义，并把占位能力从已交付功能中撤回。

范围：

- `web.security.metadata` 重命名为请求元数据语义。
- README 将 audit/openapi/codegen 表达为扩展边界或 Roadmap，除非已补齐端到端能力。
- security 模块文档改为上下文门面和适配入口，不宣称完整认证授权产品。

验收：

```powershell
mvn -pl :coco-feature-web -am test
mvn -pl :coco-feature-security -am test
git diff --check
codegraph sync .
```

### PR 10：API/SPI 收敛

状态：done。已将低价值 Java 配置入口标记为兼容废弃路径，运行时内部改用 `MutableCocoFeatureRegistry` 收集旧入口声明；配置文件、`@CocoFeatures` 和 `CocoFeatureSelection` 成为主路径。

目标：减少公共 API 表面积，保留真实替换点。

范围：

- `CocoConfigurer`、`CocoFeatureRegistry`、`DefaultCocoFeatureRegistry` 保留二进制兼容，但标记为 `@Deprecated`。
- 业务项目迁移到 `coco.features.*` YAML 配置或 `@CocoFeatures` 注解。
- `coco-config` 使用内部 `MutableCocoFeatureRegistry` 承接旧入口，不再依赖 public default implementation。
- feature selection 数据载体只保留 `CocoFeatureSelection` 一个核心模型，其余作为序列化或配置适配层。

验收：

```powershell
mvn -pl :coco-api-core -am test
mvn -pl :coco-config -am test
mvn -pl :coco-feature-runtime -am test
git diff --check
codegraph sync .
```

### PR 11：功能解析可观测性

状态：done。最终功能计划现在能暴露依赖传播禁用集合；运行期自动配置和 Maven 构建期插件都会输出配置源摘要、启用/禁用结果和依赖传播禁用结果。

目标：让默认功能、配置文件、插件参数和注解声明合并后的结果可追踪，避免依赖传播禁用时没有诊断线索。

范围：

- `CocoFeaturePlan` 增加 `disabledByDependencyFeatures()` 诊断方法。
- `coco-config` 在生成 `CocoFeaturePlan` 时输出运行期解析来源和最终功能计划。
- `coco-maven-plugin` 在 `coco:features` 阶段输出 application.yml、插件参数、注解扫描和最终功能计划。
- 为依赖传播禁用结果增加单元测试。

验收：

```powershell
mvn -B -pl :coco-feature-registry,:coco-config,:coco-maven-plugin -am test
git diff --check
codegraph sync .
```

### PR 12：包裁剪原始备份与可执行结构断言

状态：done。`coco:prune-package` 在实际裁剪 Spring Boot jar 前会保存原始包到 `target/coco-prune.original.jar`；测试 fixture 也补齐了 Boot launcher、manifest 和 `BOOT-INF` 结构断言。

目标：让包裁剪过程可追溯，并防止索引重写或嵌套依赖裁剪破坏 Spring Boot 可执行 jar 的基本结构。

范围：

- `CocoPackagePruneMojo` 在裁剪发生时保存原始 jar 备份。
- 包裁剪单测断言备份包仍包含被裁剪依赖和原始索引。
- 包裁剪单测断言裁剪后 jar 保留 Boot launcher、`Main-Class`、`Start-Class`、应用 class 和嵌套 lib。

验收：

```powershell
mvn -B -pl :coco-maven-plugin -am test
git diff --check
codegraph sync .
```

### PR 13：数据权限 SQL 关键路径测试

状态：done。数据权限 SQL 资源解析器现在有直接单测，handler 也覆盖了缺少资源规则时的 `IGNORE` 策略和 schema-qualified table 资源匹配。

目标：补齐数据权限 SQL 关键路径测试，避免资源映射、缺规则策略或 schema-qualified 表名行为在后续重构中回退。

范围：

- 为 `PropertyCocoDataPermissionSqlResourceResolver` 增加直接单测。
- 覆盖普通表名规范化、schema-qualified 表名解析、空资源键忽略和未知表不匹配。
- 为 `CocoMybatisPlusDataPermissionHandler` 增加 missing-rule `IGNORE` 和 schema-qualified table 谓词生成测试。

验收：

```powershell
mvn -B -pl :coco-feature-data-permission -am test
git diff --check
codegraph sync .
```

### PR 14：Maven 运行期 artifact 解析回退测试

状态：done。`CocoFeaturesMojo.applyFeatureDependencies` 现在覆盖 Maven Resolver 不可用和 `resolveArtifact` 失败两条回退路径。

目标：确保构建期功能依赖注入在 artifact 解析环境缺失或仓库解析失败时保持可降级，不阻断 Maven model dependency 写入，也不把未解析 artifact 放入 runtime classpath。

范围：

- 在 `CocoFeaturesMojoTest` 中补充 Resolver 不可用路径测试。
- 使用 JDK 动态代理模拟 `RepositorySystem.resolveArtifact` 抛出 `ArtifactResolutionException`。
- 断言回退后 `coco-feature-web` 仍写入 Maven model，`project.getArtifacts()` 保持为空。

验收：

```powershell
mvn -B -pl :coco-maven-plugin -am test
git diff --check
codegraph sync .
```

### PR 15：安全上下文持有器边界测试

状态：done。`CocoSecurityContextHolderTest` 现在覆盖线程隔离、异常恢复和负向入参路径。

目标：钉住 security 上下文的 ThreadLocal 语义和跨线程传播包装行为，避免后续重构让安全上下文泄漏、丢失或异常后无法恢复。

范围：

- 覆盖当前线程上下文不会被工作线程继承，工作线程设置上下文也不会污染调用线程。
- 覆盖包装任务抛异常后恢复工作线程原上下文。
- 覆盖 `runWithContext` 抛异常后恢复调用线程原上下文。
- 覆盖缺失上下文、null 上下文、null 快照和 null 回调的负向行为。

验收：

```powershell
mvn -B -pl :coco-feature-security -am test
git diff --check
codegraph sync .
```

### PR 16：BodyCache query 触发参数解析

状态：done。`CocoRequestBodyCachingFilter` 现在使用 Spring `UriComponentsBuilder` 解析 query 参数名，并保留 malformed query 的原始回退；同时规范化 web main 中已有的非法 UTF-8 Java 注释字节，保证该模块在 UTF-8 编译配置下可干净重编译。

目标：避免签名 / 加密 / 重放参数名包含 URL 转义字符时，BodyCache 误判为未携带安全触发参数，导致后续过滤器读不到可复读请求体。

范围：

- 替换 `isTriggerParameterPresent` 中手写 `split("&")` / `indexOf('=')` 的主路径。
- 增加 malformed query 的保守回退，避免异常 query 直接中断过滤器判断。
- 补充编码参数名触发缓存的 Web 模块回归测试。
- 将 web main 下严格 UTF-8 解码失败的 Java 源文件写回为合法 UTF-8，避免本次触发重编译后暴露编码错误。

验收：

```powershell
mvn -B -pl :coco-feature-web -am test
git diff --check
codegraph sync .
```

### PR 17：客户端断开异常处理

状态：done。`CocoWebExceptionHandler` 现在使用 Spring `DisconnectedClientHelper` 识别客户端断开异常，并将其原样抛回 MVC 容器，不再包装成 Coco 500 错误响应。

目标：避免 `AsyncRequestNotUsableException`、容器级 client abort 等请求断开信号被兜底 `Exception.class` 误判为服务器 5xx，从而污染错误率和异常告警。

范围：

- 在未处理异常兜底路径中识别 client disconnect，并跳过统一错误响应生成。
- 避免断连异常进入 Coco exception log handle。
- 补充 `AsyncRequestNotUsableException` 回归测试。

验收：

```powershell
mvn -B -pl :coco-feature-web -am test
git diff --check
codegraph sync .
```

### PR 18：MyBatis-Plus SQL 防护生产提示

状态：done。SQL 防护默认值保持关闭，避免破坏既有 SQL；框架在默认关闭时输出生产启用建议 INFO，配置 JavaDoc 和中英文 README 已补启用方式与可能被拦截的正常 SQL 形态。

目标：不强行改变业务 SQL 行为，同时让生产项目清楚知道 SQL guard 默认关闭的风险和启用前需要验证的 SQL 类型。

范围：

- `CocoMybatisPlusInterceptorFactory` 在两项 SQL guard 都关闭时输出生产建议日志。
- `CocoMybatisPlusSqlGuardProperties` JavaDoc 明确生产启用建议和兼容性验证要求。
- README / README_CN 增加 SQL guard 启用配置和可能被拦截的 SQL 清单。
- 补充默认关闭提示日志的 MyBatis-Plus 模块回归测试。

验收：

```powershell
mvn -B -pl :coco-feature-mybatis-plus -am test
git diff --check
codegraph sync .
```

### PR 19：Trace MDC 恢复回归测试

状态：done。`CocoTraceFilter.restoreMdcValue` 当前实现已使用显式 `previousMdcValue == null` 分支；本批次补充回归测试，固定默认和自定义 MDC key 的请求内临时覆盖、请求结束后恢复原值和无原值时清理的语义。

目标：把 B11 / D37 收口为可验证行为，防止后续 trace filter 重构重新引入 MDC 泄漏或错误清理。

范围：

- 补充 trace filter 默认和自定义 MDC key 请求内覆盖和请求后恢复原值的测试。
- 保留已有无原 MDC 值时请求后清理的断言。
- 更新框架审计文档和总控路线图状态。

验收：

```powershell
mvn -B -pl :coco-feature-web -am test
git diff --check
codegraph sync .
```

### PR 20：过滤器异常响应请求上下文显式化

状态：done。`CocoFilterExceptionResponseWriter` 已停止通过 `RequestContextHolder` 临时绑定请求来驱动异常响应本地化，改为把当前请求语言显式传给 `CocoWebExceptionHandler`。

目标：把 B9 / D35 收口为可验证行为，避免过滤器异常写出阶段污染或误用线程上的旧请求上下文，同时保留过滤器错误响应的 i18n 能力。

范围：

- 为 `CocoWebExceptionHandler` 增加显式 `Locale` 的 Coco 异常处理入口，普通 Controller 异常处理继续使用原有语言解析路径。
- 自动配置传入 `coco.common.i18n.default-locale`，供显式语言为空的过滤器链路兜底。
- `CocoFilterExceptionResponseWriter` 从 `HttpServletRequest` 解析请求语言，不再设置、恢复或清理 `RequestContextHolder`。
- 补充回归测试，覆盖过滤器异常响应的英文 `Accept-Language` 本地化和旧请求上下文保留。

验收：

```powershell
mvn -B -pl :coco-feature-web -Dtest=CocoWebAutoConfigurationTest#filterExceptionWriterUsesExplicitRequestLocaleWithoutReplacingRequestAttributes test
mvn -B -pl :coco-feature-web -am test
git diff --check
codegraph sync .
```

### PR 21：Maven Enforcer 发布闸门

状态：done。根 POM 已接入 Maven Enforcer，仓库构建在 `validate` 阶段检查重复依赖声明、compile/runtime 依赖收敛和直接引入的禁用依赖；release profile 额外要求非 SNAPSHOT 版本。

目标：把 C7 收口为可执行构建闸门，避免发布前仍允许依赖冲突、直接引入 legacy logging bridge / 非默认 servlet starter，或使用 SNAPSHOT revision 触发 release 构建。

范围：

- 根 POM 增加 `maven-enforcer-plugin` 版本和 `enforce-build-gates` 执行。
- `dependencyConvergence` 排除 `provided` / `test` scope，只约束可交付依赖图。
- `bannedDependencies` 禁止直接引入 `commons-logging`、`log4j`、`log4j-over-slf4j`、Jetty / Undertow starter。
- release profile 增加 `requireReleaseVersion`，默认 SNAPSHOT 会失败，显式正式 `revision` 可通过。
- `coco-parent` 默认跳过仓库内部 enforcer，避免业务项目继承 parent 后被框架仓库发布闸门误伤。

验收：

```powershell
mvn -B validate -DskipTests
mvn -B -N -Prelease "-Drevision=1.0.0" "-Dgpg.skip=true" "-DskipTests" validate
mvn -B -N -Prelease "-Dgpg.skip=true" "-DskipTests" validate; if ($LASTEXITCODE -eq 0) { throw "release profile unexpectedly accepted a SNAPSHOT revision" } else { Write-Output "release profile rejected SNAPSHOT revision as expected"; exit 0 }
git diff --check
codegraph sync .
```

### PR 22：CI 跨平台矩阵

状态：done。GitHub Actions `CI / verify` job 已改为 Ubuntu、Windows、macOS 三系统矩阵；JDK toolchain 和 Maven 编译目标在 workflow 中明确区分；样例黑盒验证脚本可在 Windows runner 上稳定输出中文响应内容。

目标：把 C8 收口为真实 CI 覆盖，避免框架只在 Linux 上验证，同时让“JDK 21 编译 Java 17 字节码”的约束在 CI 配置里可见。

范围：

- `verify` job 使用 `matrix.os` 覆盖 `ubuntu-latest`、`windows-latest`、`macos-latest`。
- `fail-fast=false`，避免一个平台失败时隐藏其他平台结果。
- workflow env 明确 `JAVA_TOOLCHAIN_VERSION=21` 和 `MAVEN_COMPILE_RELEASE=17`。
- 样例黑盒验证统一通过 `actions/setup-python` 与 `python` 命令执行，并显式安装 `cryptography`。
- workflow 设置 `PYTHONUTF8=1`，脚本启动时将 stdout/stderr 重配为 UTF-8，避免 Windows 默认代码页无法输出中文响应内容。

验收：

```powershell
git diff --check
codegraph sync .
gh pr checks <PR> --watch --interval 15
```

### PR 23：正常响应包装大响应阈值

状态：done。正常响应包装增加可配置阈值，默认关闭限制；对已知长度超过阈值的响应跳过统一包装，避免大响应在包装层继续膨胀。

目标：收口 B12，给业务项目一个轻量、可解释的保护开关，同时不为估算大小提前序列化任意业务对象。

范围：

- `coco.web.response-wrap.max-body-bytes` 默认 `-1`，表示不限制。
- 阈值只基于已知长度判断：响应 `Content-Length` 或字符串响应体字节数。
- 超过阈值时返回原始 body 并输出 WARN。
- 配置属性、configuration metadata 测试和 response wrapping 单测同步覆盖。

验收：

```powershell
git diff --check
codegraph sync .
mvn -B -pl :coco-feature-web -am test
```

### PR 24：扩展边界端到端验证

状态：done。audit、openapi、codegen 三个轻量扩展模块补充当前承诺范围内的行为验证，避免只停留在自动配置 bean 是否存在。

目标：收口 C17，在不夸大模块能力的前提下，把扩展边界的真实使用路径固定为测试。

范围：

- audit：自动配置的 `CocoAuditPublisher` 会把事件分发到所有业务自定义 `CocoAuditRecorder`。
- openapi：配置绑定后的元数据会经过 `CocoOpenApiMetadata` 归一化，空白标题、版本和描述不会泄漏到 SPI 输出。
- codegen：业务替换 `CocoCodeGenerator` 时可以读取绑定后的模板配置，并接收归一化后的 `CocoCodegenRequest`；请求、文件和结果模型补充输入校验与不可变性断言。

验收：

```powershell
git diff --check
codegraph sync .
mvn -B -pl :coco-feature-audit,:coco-feature-openapi,:coco-feature-codegen -am test
```

### PR 25：`coco-test` 共享测试工具落地

状态：done。`coco-test` 不再只是空标记模块，开始承载仓库内部共享测试工具，并被真实 feature 模块测试使用。

目标：收口 C22 / D22，避免测试支持模块长期没有使用方，也避免各模块重复实现配置元数据资源读取和命名节点查找逻辑。

范围：

- `CocoTestSupport` 增加配置元数据 JSON 读取、通用 JSON 资源读取、命名节点查找和必需节点查找工具。
- `coco-test` 增加自身单测，固定资源缺失、空资源路径和命名节点缺失行为。
- 根 POM 纳入 `coco-test` dependency management。
- `coco-feature-codegen` 以 test scope 引入 `coco-test`，并改用 `CocoTestSupport` 读取配置元数据。

验收：

```powershell
git diff --check
codegraph sync .
mvn -B -pl :coco-test,:coco-feature-codegen -am test
```

### PR 26：Security Web 上下文桥接

状态：done。`coco-feature-security` 补齐配置入口和 Servlet 请求生命周期桥接，但仍不绑定认证提供方、用户模型、RBAC/ABAC 或会话/token 存储。

目标：收口 A2 / M3 / D27 的核心边界，让安全上下文可以由 Web 入口可靠设置、清理和恢复，同时保持业务认证模型可替换。

范围：

- 新增 `CocoSecurityProperties`，绑定 `coco.security.*` 配置命名空间。
- 新增 `CocoWebSecurityContextResolver` SPI，业务可替换为 Spring Security、JWT、Session 或网关认证适配。
- 新增 `CocoSecurityWebFilter`，在 Servlet 请求内设置安全上下文，结束后恢复线程原上下文。
- 新增默认可信请求头适配器，默认关闭 header 读取，避免直接信任外部客户端输入。
- README / README_CN 将 security 描述更新为上下文桥接与扩展边界，不承诺完整认证授权产品。

验收：

```powershell
git diff --check
codegraph sync .
mvn -B -pl :coco-feature-security -am test
```

### PR 27：Replay 内存存储后台清理

状态：done。默认 `InMemoryCocoReplayStore` 不再在请求写路径上扫描全部已占用 replay key，过期键清理改为首次使用后懒启动的后台 daemon 任务。

目标：收口 B4 / B13，避免高并发或大 key 集合下由单个请求线程承担全表过期清理，同时保持默认内存实现的单进程开发体验。

范围：

- `reserve()` 只执行当前 key 的原子占用与过期覆盖判断，不再触发全表清理。
- 默认内存 store 在首次 `reserve()` 时懒启动 `coco-replay-cleanup` 守护线程，按 `coco.web.replay.cleanup-interval-seconds` 清理过期 key。
- 默认 store 实现 `AutoCloseable`，Spring Bean 销毁时关闭后台清理线程。
- 补充 `InMemoryCocoReplayStoreTest`，固定写路径不清理、过期同 key 可复用和未过期同 key 拒绝行为。

验收：

```powershell
git diff --check
codegraph sync .
mvn -B -pl :coco-feature-web -am test
```

### PR 28：加密异常分类

状态：done。加密过滤器不再把所有解密失败都映射成同一个 401 错误码，格式错误与认证失败分开处理。

目标：收口 B5，让坏格式请求返回 400，密文认证或完整性失败返回 401，减少错误语义混淆并为后续审计事件接入保留清晰边界。

范围：

- `CocoRequestDecryptException` 增加失败分类：`MALFORMED_REQUEST` 与 `AUTHENTICATION_FAILED`。
- `AesGcmCocoRequestDecryptor` 将算法不支持、IV / payload 编码错误、空 IV、payload 短于 GCM tag 长度标记为格式错误。
- `CocoEncryptionFilter` 将格式错误映射为 `coco.web.encryption.malformed-request` 请求异常，将 GCM tag、密钥不匹配等认证失败保留为 `coco.web.encryption.decrypt-failed` 未认证异常。
- 补充中英文消息资源和 Web 加密过滤器回归测试。

验收：

```powershell
git diff --check
codegraph sync .
mvn -B -pl :coco-feature-web -am test
```

### PR 29：Replay 请求形态预检

状态：done。新增 `CocoReplayRequestShapeFilter`，在签名 HMAC、AES 解密和 replay store 占用之前完成防重放协议形态粗筛。

目标：收口 B7，避免明显缺少 replay 材料或 timestamp 格式错误的签名 / 加密 / 强制防重放请求先消耗验签、解密等 CPU。

范围：

- 新增 `CocoReplayRequestShape` 共享 helper，统一 replay 是否需要保护、必需字段和 timestamp 格式校验。
- 新增 `CocoReplayRequestShapeFilter`，复用当前 `CocoReplayKeyResolver`，只做请求形态预检，不做 HMAC、AES、store reserve 或时间窗口判断。
- Web filter 顺序调整为 request-body `+0`、trace `+1`、replay-shape `+2`、signature `+3`、encryption `+4`、replay `+5`。
- 补充自动配置顺序测试，以及缺少 replay nonce 的签名请求不会进入 `CocoSignatureVerifier` 的回归测试。

验收：

```powershell
git diff --check
codegraph sync .
mvn -B -pl :coco-feature-web -am test
```

### PR 30：OpenAPI SpringDoc 元数据适配

状态：done。`CocoOpenApiMetadataProvider` 现在有生产消费者：业务项目 classpath 中存在 SpringDoc / Swagger OpenAPI 类型时，Coco 会自动注册 SpringDoc `OpenApiCustomizer`，把 Coco 元数据写入 `OpenAPI.info`。

目标：收口 C3，避免 OpenAPI 元数据 SPI 只注册 bean 而没有任何文档渲染链路消费，同时保持 Coco 不强依赖 SpringDoc。

范围：

- 新增无硬依赖的 SpringDoc 元数据适配器，通过 FactoryBean 和运行期代理暴露 `OpenApiCustomizer`。
- 新增 `coco.openapi.springdoc.enabled`，允许业务项目关闭自动适配。
- SpringDoc 类型不存在时不注册适配器，不改变仅使用 Coco OpenAPI 元数据 SPI 的项目。
- 补充自动配置、元数据适配和配置 metadata 测试。
- README / README_CN 更新 OpenAPI 已交付边界。

验收：

```powershell
git diff --check
codegraph sync .
mvn -B -pl :coco-feature-openapi -am test
```

### PR 31：MyBatis-Plus 自动配置类型安全排序

状态：done。Coco MyBatis-Plus 自动配置、租户 MyBatis-Plus 接入和数据权限 MyBatis-Plus 接入不再依赖字符串类名排序。

目标：收口 C4，避免 MyBatis-Plus 或 Coco 自动配置类名变化后排序静默失效，导致 MP 默认拦截器 bean 抢先创建或租户 / 数据权限拦截器顺序错乱。

范围：

- `CocoMybatisPlusAutoConfiguration` 使用类型安全 `after = MybatisPlusAutoConfiguration.class` 和 `before = MybatisPlusInnerInterceptorAutoConfiguration.class`。
- `CocoTenantMybatisPlusAutoConfiguration` 使用类型安全 `after = {CocoTenantAutoConfiguration.class, CocoMybatisPlusAutoConfiguration.class}`。
- `CocoDataPermissionMybatisPlusAutoConfiguration` 使用类型安全 `after = {CocoDataPermissionAutoConfiguration.class, CocoMybatisPlusAutoConfiguration.class}`。
- 补充自动配置注解回归测试，断言相关 `afterName` / `beforeName` 不再使用字符串类名。

验收：

```powershell
git diff --check
codegraph sync .
mvn -B -pl :coco-feature-mybatis-plus,:coco-feature-tenant,:coco-feature-data-permission -am test
```

### PR 32：Web 安全输入解析器测试补强

状态：done。签名、加密和防重放过滤器共享的安全输入快照解析器已有直接单测。

目标：收口 C5 的 Web 测试缺口，避免安全过滤器依赖的请求头、Cookie、query、payload 和 cached body 元数据解析行为只被大型自动配置测试间接覆盖。

范围：

- `DefaultCocoWebRequestSecurityInputResolver` 直接测试自动纳入签名 / 加密 / 防重放配置中的安全请求头。
- 覆盖规范化请求头多值快照、合并值和签名值不参与 canonical header 的边界。
- 覆盖规范化 Cookie 原始值保留，不受普通上下文裁剪阈值影响。
- 覆盖 query 与 cached form payload 原始参数拆分、合并参数和 `FORM` payload source。
- 覆盖 `CocoRequestBodyResolver` 返回 `null` 时回退为未缓存请求体输入。

验收：

```powershell
git diff --check
codegraph sync .
mvn -B -pl :coco-feature-web -am test
```

### PR 33：sample parent 版本联动

状态：done。`coco-sample-basic` 的 parent 版本一致性已有自动校验。

目标：收口 C6，保证框架根版本推进后，sample 会跟随当前 `revision` 验证，而不是继续绑定旧 parent 后在首次安装或 CI 中失败。

范围：

- 不采用 `<parent><version>${revision}</version>`：Maven 独立解析 sample parent 时不会可靠替换该表达式。
- 新增 `verify_parent_version.py`，直接比较根 POM `revision` 和 sample 的 `coco-parent` 版本。
- CI 在 sample `verify` 前执行版本一致性脚本，提前暴露 root 版本推进但 sample parent 未同步的问题。

验收：

```powershell
python coco-samples/coco-sample-basic/scripts/verify_parent_version.py
mvn -B -f coco-samples/coco-sample-basic/pom.xml verify
git diff --check
```

### PR 34：Failsafe 集成测试闸门

状态：done。仓库已有真实 `*IT`，并且 Maven verify/install 生命周期会执行 failsafe。

目标：收口 C21 / D23，避免 `maven-failsafe-plugin` 只停留在版本声明，导致集成测试文件即使新增也不会进入默认验证路径。

范围：

- 根 POM 在 build plugins 中绑定 `maven-failsafe-plugin` 的 `integration-test` 和 `verify` goals。
- failsafe 配置 `failIfNoTests=false`，让暂时没有 IT 的模块不失败。
- `coco-spring-boot-starter` 增加 `CocoStarterSmokeIT`，真实启动 servlet Spring Boot 应用上下文。
- IT 禁用需要业务数据源的 MyBatis-Plus / tenant / data-permission 功能，聚焦 starter 默认 Web / Common 基础设施。

验收：

```powershell
mvn -B -pl :coco-spring-boot-starter -am verify
git diff --check
codegraph sync .
```

### PR 35：完整数据库业务示例

状态：done。仓库已有独立 full sample，通过一个 starter 跑通安全、租户、数据权限、审计和 MyBatis-Plus。

目标：收口 C9，并用业务黑盒流证明 README 中的数据与上下文能力可以协同工作，而不只是在各 feature
模块的自动配置测试中分别存在。

范围：

- 新增 `coco-sample-full`，只额外引入 H2，保留单 starter 接入方式。
- trusted-header bridge 建立安全主体；样例入口适配器把租户和主体映射为 tenant / data-permission 上下文。
- MyBatis-Plus 查询同时受 `tenant_id` 和 `owner_id` 条件约束。
- 业务服务显式调用 `CocoSecurity.requireRole`，并通过 `CocoAuditPublisher` 发布查询审计事件。
- JUnit 和 Python 黑盒流验证角色拒绝、缺少租户、跨租户隔离、数据范围和审计结果。
- CI 在 Ubuntu、Windows、macOS 构建并运行 full sample，同时断言完整 feature artifact 未被裁剪。

验收：

```powershell
mvn -B install
mvn -B -f coco-samples/coco-sample-full/pom.xml verify
python coco-samples/coco-sample-full/scripts/verify_business_flow.py
git diff --check
codegraph sync .
```

### PR 36：样例安全材料外置

状态：done。basic sample、Python 黑盒脚本和 Postman 资产不再保存固定签名密钥或 AES key。

目标：收口 C11，防止示例中的固定安全材料被业务项目照搬，并保持一条命令可执行的 CI 黑盒流。

范围：

- `application.yml` 通过 `SAMPLE_SIGNING_KEY` 和 `SAMPLE_ENCRYPTION_KEY` 读取安全材料。
- 黑盒脚本在环境变量缺失时生成临时签名密钥、AES key 和 IV，只传入子 Java 进程环境。
- Postman 生成器从环境读取材料，不再包含固定 secret、key 或 IV。
- 仓库提交的 Postman collection / environment 保持安全材料槽位为空。
- 本地 Postman 资产默认输出到 Git 忽略的 `target/postman`，签名密钥不进入 collection variable。
- 架构测试断言配置使用环境占位符，并阻止历史固定材料重新进入生成器和 Postman 资产。
- 重新生成资产时同步校正非法加密载荷场景，使其断言当前框架返回的 400 错误。

验收：

```powershell
mvn -B -f coco-samples/coco-sample-basic/pom.xml verify
python coco-samples/coco-sample-basic/scripts/generate_postman_import.py
python coco-samples/coco-sample-basic/scripts/verify_business_flow.py
git diff --check
codegraph sync .
```

### PR 37：默认 CRUD 源码生成

状态：done。Codegen 不再注册 No-Op 默认实现，业务项目可以显式生成并接管普通 Java CRUD 源码。

目标：收口 C10 中最后一个真实空壳，同时坚持“生成源码，不做运行时 auto-CRUD”的框架边界。

范围：

- 默认 `CocoCodeGenerator` 使用可替换模板位置和编码的真实模板生成器。
- 内置 `crud` 模板生成 Controller、DTO、应用服务、领域模型、仓储契约和 MyBatis-Plus 基础设施源码。
- `coco-maven-plugin` 提供不绑定默认生命周期的 `coco:generate` goal，从严格 YAML 规格显式生成。
- `CocoGeneratedFileWriter` 预检全部路径和碰撞，默认拒绝覆盖并支持 dry-run。
- 测试覆盖模板渲染、非法路径、重复输出、覆盖策略、YAML 校验、Mojo 行为和生成源码编译。

验收：

```powershell
mvn -B -pl :coco-feature-codegen,:coco-maven-plugin -am verify
git diff --check
codegraph sync .
```

### PR 38：默认结构化审计日志

状态：done。Audit 默认实现不再静默丢弃事件，提供可替换的结构化日志输出。

目标：在不引入数据库审计模型的前提下，让单 starter 项目启用 Audit 后立即获得可观测输出。

范围：

- 默认 `CocoAuditRecorder` 改为通过独立 Coco log handle 输出稳定单行 JSON。
- 新增 `CocoAuditFormatter` SPI；业务提供 Formatter 或 Recorder bean 时默认实现回退。
- 增加 `coco.audit.logging.enabled`、`logger-name` 和 `level` 配置与元数据。
- Formatter 固定字段顺序、属性 key 排序和控制字符转义，避免日志注入与不稳定输出。
- 数据库、MQ、防篡改、保留期限和合规报表继续明确为业务侧职责。

验收：

```powershell
mvn -B -pl :coco-feature-audit -am verify
mvn -B verify
git diff --check
codegraph sync .
```

### PR 39：Audit 功能依赖解耦

状态：done。移除标准功能图中 Audit 对 Web 和 MyBatis-Plus 的伪依赖。

目标：让无数据库 Web 服务、批处理和其他 Spring Boot 服务可以独立启用审计事件与默认结构化日志，不因关闭 MyBatis-Plus 或 Web 被构建期和运行期功能解析器连带裁掉。

范围：

- `CocoFeature.AUDIT` 不再声明 `WEB` 或 `MYBATIS_PLUS` 依赖。
- 访问日志审计适配器继续通过现有 Bean 条件自然组合，不把可选事件来源升级为功能依赖。
- 未来表变更审计应作为可选适配器接入 `CocoAuditPublisher`，不得反向污染 Audit 核心依赖。
- 功能清单、Maven 依赖应用和禁用传播测试固定新的独立边界。

验收：

```powershell
mvn -B -pl :coco-feature-registry,:coco-maven-plugin -am verify
mvn -B verify
git diff --check
codegraph sync .
```

### PR 40：JDBC 共享防重放存储

状态：done。默认内存 Store 保持单实例定位；集群业务可以显式选择 JDBC 参考实现，并继续掌握表结构、迁移和事务边界。

目标：完成 B1 中尚未落地的 JDBC 参考实现，让使用现有关系型数据库的多实例服务不必从零实现 `CocoReplayStore`，同时不把数据库生命周期和 exactly-once 业务语义封装进 Web 框架。

范围：

- 增加 `coco.web.replay.store-type=jdbc` 和严格校验的 `coco.web.replay.jdbc.table-name` 配置；默认仍为 `in-memory`。
- `JdbcCocoReplayStore` 使用 SHA-256 摘要、过期条件更新、插入和数据库唯一键完成跨实例原子占用，避免 check-then-insert。
- JDBC Store 首次使用后懒启动过期记录清理任务；占用路径数据库异常向上抛出，使受保护请求失败关闭，异步清理失败记录 WARN 后重试。
- JDBC 自动配置只在业务已有唯一 `JdbcOperations` 候选时生效，不创建 DataSource、不执行 DDL、不猜测多数据源；自定义 `CocoReplayStore` 继续优先。
- 中英文 README 提供最小配置和表契约，并明确数据库迁移、可用性、集群时钟、业务事务和 exactly-once 语义由业务负责。
- H2 与自动配置测试覆盖首次占用、有效重复、过期重占用、并发竞争、清理、摘要存储、非法表名、默认内存和自定义 Bean 回退。

验收：

```powershell
mvn -B -pl :coco-feature-web -am verify
mvn -B verify
git diff --check
codegraph sync .
```

## 执行纪律

- 每个 PR 只处理一个主题，不把 sample、Web 重构、API 删除混在一起。
- 每次改源码后必须执行 `codegraph sync .`。
- 不提交生成产物、临时文件、无关格式化。
- 发现未提交的用户修改时，只在相关文件内协同处理，不做回滚。
- 文档承诺必须跟实现一致；未完成能力写成扩展点或 Roadmap。
