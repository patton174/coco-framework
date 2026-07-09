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

## 归并决策

| 主题 | 来源 | 状态 | 决策 |
| --- | --- | --- | --- |
| starter 装包过重 | framework A1, coupling M1 | adjusted | 不立即拆掉默认 Web starter。先在文档中明确 starter 是组合入口，后续可新增 `coco-core-starter` 或 `coco-web-starter`，但不破坏默认快速接入体验。 |
| `coco-feature-security` 仅为骨架 | framework A2, coupling M3 | adjusted | 不直接补成完整 RBAC。先补 Web bridge、配置入口、上下文适配和文档边界，避免文档承诺超过实现。 |
| 数据权限 SQL 数值列 | framework A3 | accepted | v1.0.x 必修。修默认谓词生成器或显式拒绝非文本列，并补单测。 |
| 租户旁路无审计 | framework A4 | accepted | v1.0.x 必修。增加旁路白名单、告警和审计事件边界。 |
| ThreadLocal 上下文传播 | framework A5 | accepted | v1.0.x 必修。提供通用传播原语和 sample 适配器，不强制业务登录模型。 |
| Maven 注解扫描器缺测试 | framework A6 | accepted | 第一批代码 PR。风险小、收益高。 |
| Replay 默认 store 单进程 | framework B1 | accepted | 启动 WARN，文档标明集群必须替换 `CocoReplayStore`，后续提供 JDBC 参考实现。 |
| TraceId 无校验 | framework B2 | accepted | 增加 validator，默认限制字符集和长度。 |
| Replay 使用客户端时间计算过期 | framework B3 | accepted | 改为服务端入站时间加 TTL，并增加时钟偏差配置。 |
| Replay 清理在写路径 | framework B4, B13 | deferred | 先修安全语义和告警，清理线程优化放到 Web 性能治理批次。 |
| 加密异常错误码过粗 | framework B5 | deferred | 与审计事件和安全错误码整理一起做。 |
| SQL 防护默认关闭 | framework B6 | adjusted | 不直接改默认值，先补生产建议、启动 INFO 和文档说明，避免误伤现有合法 SQL。 |
| 过滤器顺序可被消耗 CPU | framework B7 | deferred | 放入 Web 安全硬化批次，先补请求形态粗筛设计。 |
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
| audit/openapi/codegen 是占位 SPI | framework C10 | adjusted | 文档改成扩展边界或 Roadmap，除非补齐端到端交付。 |
| 功能解析缺少可观测性 | framework C13, C14 | accepted | 补充最终功能计划日志、配置源摘要和依赖传播禁用诊断，避免 feature 被静默禁用后难以排查。 |

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

## 执行纪律

- 每个 PR 只处理一个主题，不把 sample、Web 重构、API 删除混在一起。
- 每次改源码后必须执行 `codegraph sync .`。
- 不提交生成产物、临时文件、无关格式化。
- 发现未提交的用户修改时，只在相关文件内协同处理，不做回滚。
- 文档承诺必须跟实现一致；未完成能力写成扩展点或 Roadmap。
