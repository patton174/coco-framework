# Coco Framework 痛点与优化建议（多维度审计报告）

- **审计日期：** 2026-07-09
- **审计范围：** 28 模块 / 355 主类 / 53 测试类 / ~35k LOC
- **审计方法：** 8 维度并行扫描（架构 / 功能机制 / Web / 数据-SQL / 安全模块 / API-SPI / 测试与构建 / 开发者体验），每条非低危发现由独立验证者复核
- **确认发现：** 71 条（高 6 / 中 27 / 低 38；6 个维度、9 个类别）
- **判定原则：** 与 `AGENTS.md` 中"不过度封装业务、安全默认、starter 只组合不拥有、API 小而稳"等原则对齐

---

## 总览

| 维度 | 高 | 中 | 低 | 小计 |
| --- | --- | --- | --- | --- |
| 架构 | 1 | 1 | 3 | 5 |
| 功能机制 | 0 | 4 | 3 | 7 |
| Web | 0 | 6 | 8 | 14 |
| 数据-SQL | 2 | 4 | 3 | 9 |
| 安全模块 | 1 | 0 | 5 | 6 |
| API-SPI | 0 | 4 | 3 | 7 |
| 测试与构建 | 1 | 5 | 5 | 11 |
| 开发者体验 | 1 | 3 | 5 | 9 |

| 类别 | 数量 | 类别 | 数量 |
| --- | --- | --- | --- |
| correctness | 13 | testing | 12 |
| api-design | 11 | build-release | 8 |
| security | 8 | developer-experience | 7 |
| architecture | 5 | maintainability | 4 |
| performance | 3 | | |

**整体判断：** Coco 是一个原则清晰、骨架扎实的框架，依赖方向干净（`coco-common → coco-api`，feature 不反向依赖 common），但存在"安全默认不到位、starter 边界与 README 边界不一致、骨架功能多于实际交付"三类张力。最危险的 5 条全部是"代码本身无错 + 文档承诺 → 业务方按承诺部署 → 被攻击"链路，修复工作量多数为 S 级。

---

## A. v1.0 必修：定位与安全默认

### A1. 【架构 / 高】starter 把 9 个 feature 模块打包成一个 jar，违反"只组合、不拥有"

**位置：** `coco-spring-boot-starter/pom.xml:39-78`

starter 直接依赖 `coco-feature-runtime / config / web / mybatis-plus / audit / security / tenant / data-permission / openapi / codegen`，并把 `spring-boot-starter-web` 一起拖入。任何"想用 Coco 但不想用 Web"的场景（定时任务、批处理、CLI）都无法剥离这些 jar；而 `coco-maven-plugin` 已经为"功能可裁剪"做了 PACKAGE 阶段的 jar 重写，starter 端却把这一刀切成了空话。

**建议（M）：**
- starter 只保留 `coco-api-core + coco-common-* + coco-spring-boot-autoconfigure`；
- 在 `coco-bom` 里把 feature 模块放好，业务项目按需声明；
- `coco-maven-plugin` 的 prune 逻辑因此成为"真正必要的功能"，而非现在的"无论怎么配都先装再剪"。

---

### A2. 【架构 / 高】`coco-feature-security` 只是个骨架切片

**位置：** `coco-feature-security`（11 主类 / 1 测试，新增 `CocoSecurity.java`）

它有 `CocoSecurityContextHolder + CocoSecurityContextResolver + HolderCocoSecurityContextResolver + CocoSecurity + CocoSecurityErrorCode + 自动配置`，但：

- 没有 `CocoSecurityProperties`（其他 feature 都有），所有行为硬编码；
- 没有 Web 过滤器接入 → `coco-feature-web` 完全不知道安全上下文存在；
- 没有角色/权限层级，`hasRole / hasPermission` 是裸 `Set.contains`；
- `CocoSecurityFeature` 是空 marker 类；
- `HolderCocoSecurityContextResolver` 是纯转发 shim，SPI 价值为零；
- zh_CN / en_US / 默认三个 messages.properties 中 **en_US 与默认完全一致**，无真实翻译。

**建议（M）：** 要么补完（参考 Shiro / Spring Security 子集），要么在 README 把安全功能从"标准功能列表"中移走、改为"扩展点待实现"，避免"冒烟 ok = 上线被 401"的认知落差。

**处理状态：** PR26 已按 Coco 定位调整处理。`coco-feature-security` 增加 `coco.security.*` 配置入口、Servlet 安全上下文桥接过滤器、可替换 `CocoWebSecurityContextResolver` SPI 和默认可信请求头适配器；请求结束后恢复线程原上下文，避免 ThreadLocal 泄漏。README / README_CN 已明确 security 仍是上下文门面与入口适配，不提供认证提供方、RBAC/ABAC、会话、令牌或用户存储。

---

### A3. 【SQL 正确性 / 高】默认数据权限 SQL 生成器把所有值当字符串，数值列直接退化为空集

**位置：** `coco-feature-data-permission/.../DefaultCocoDataPermissionSqlPredicateProvider.java:48-54`

```java
new InExpression(column, new ParenthesedExpressionList<>(values))
```

values 全部包成 `StringValue(...)`，**不查列类型**。一旦业务把数据权限列配成 `BIGINT / INT`，生成的 SQL 是 `WHERE dept_id IN ('1','2','3')`，结果永远是空集或类型错误——这恰恰是"默认"路径，开箱即错。

**建议（M）：** 二选一：
- 把 `CocoDataPermissionRule.values()` 改为带类型（`LongValue / StringValue`），从资源列元数据传递类型；
- 或默认 provider 拒绝非文本列并要求自定义，启动时 WARN 当前命中。

---

### A4. 【安全 / 高】`@InterceptorIgnore(tenantLine=true)` 可逐 Mapper 关掉租户隔离，无审计

**位置：** `coco-feature-tenant/.../CocoTenantMybatisPlusAutoConfiguration.java:62-71`

直接注册 MP 内置的 `TenantLineInnerInterceptor`，完全接受 MP 的 `@InterceptorIgnore(tenantLine="true")`。某开发给单条查询加一个注解 → **该 SQL 跨全租户执行**。框架无任何检测、日志、拒绝。

**建议（M）：**
- 在 `CocoTenantSqlProperties` 加 `allowedBypassStatements` 白名单 + `blockAllBypasses` 开关；
- 在 `CocoTenantLineHandler`（或薄薄一层包装）里命中旁路时打 WARN 并写审计事件；
- `package-info` 与 `AGENTS.md` 必须明确写出该风险。

---

### A5. 【SQL 正确性 / 高】`security / tenant / data-permission` 全是裸 `ThreadLocal`，无 Servlet Filter，也无 `@Async` 传播 —— "BYOF"

**位置：** 5 个 `*ContextHolder` 全部 `new ThreadLocal<>()`，**不是** `InheritableThreadLocal`，也没有 transmittable 包装。

- 业务拿 starter、启用 `coco-feature-tenant`，**忘了写过滤器** → 框架既不会自动给 SQL 加租户条件，也不会拒绝；业务以为隔离生效而实际裸奔；
- 任何 `@Async / parallelStream / CompletableFuture` 跳线程后 `CocoSecurity.requirePermission` 抛 `CONTEXT_MISSING`，表象是诡异的"用户没登录"。

**建议（M）：**
- 在 `coco-common-context` 加执行器传播原语（"callWithContext"包装器）；
- 在 `coco-samples` 提供推荐适配器，展示如何把 JWT / 登录主体 → 三个 `ContextHolder`；
- 不在 starter 强制模型，但必须给出"正确用法"的可运行范例。

---

### A6. 【测试 / 高】`coco-maven-plugin` 的核心扫描器零测试

**位置：** `coco-maven-plugin/.../CocoAnnotatedFeatureScanner.java:45-65`

该扫描器决定 PACKAGE 阶段**哪些 feature 关掉、哪些 jar 被裁掉**，但 `CocoBuildFeatureConfigurationLoaderTest / CocoFeaturesMojoTest / CocoPackagePruneMojoTest` 三个测试都没调用它。一个静默失败会让 prune 阶段把用户代码连根拔起。

**建议（S）：** 给 `scan()` 加 fixture 测试：编译一个含 `@CocoFeatures` 的 TempDir，断言选择正确；再加 inner class 负向用例。

---

## B. v1.0 必修：Web 安全与正确性硬化

### B1.【安全 / 中】Replay 防重放默认 store 单进程，集群下静默失效

`CocoWebAutoConfiguration.java:451-454` 在缺少自定义 bean 时给 `InMemoryCocoReplayStore`。多实例部署 → 同样的 nonce/timestamp 在每节点独立放行，重放防护直接失效，无启动告警。

**建议：** 启动时打明显 WARN："replay is process-local, replace `CocoReplayStore` for clusters"，并提供 `JdbcCocoReplayStore` 参考实现。

---

### B2.【安全 / 中】`X-Trace-Id` 接受任意用户输入，无校验

`CocoTraceFilter.java:181-187` 只 trim + isBlank。值会回写到响应头、Set-Cookie、JSON metadata、日志。攻击者可注入超长字符串 / 日志注入字符。

**建议：** 加 `CocoTraceIdValidator`（默认 `^[A-Za-z0-9_\-]{1,64}$`，通过 `CocoTraceProperties` 可覆盖以适配 W3C `traceparent` / UUID），在 filter 与 `DefaultCocoWebRequestCanonicalizer` 都调一次。

---

### B3.【安全 / 中】`CocoReplayStore.reserve` 用客户端时间戳 + TTL 算过期，时钟偏差无界

攻击者把 timestamp 推到未来 → `expiresAt` 推到未来；推到过去 → 立即到期。TTL 不变量被打破。

**建议：** 用服务端入站时间 + TTL；同时引入"全局时钟偏差上限"配置，超出拒绝。

---

### B4.【性能 / 中】`InMemoryCocoReplayStore` 的清理在写路径上同步做

`reserve()` → `cleanupIfNeeded()` 在第一个命中下一清理窗口的请求线程上跑全表扫描。100w key 时那次扫描会让所有请求卡顿。60s 默认间隔下非常容易踩。

**建议：** 把清理挪到独立的 `@Scheduled` 或守护线程；保留 CAS 作为廉价同进程 guard。

**处理状态：** PR27 已处理。`InMemoryCocoReplayStore.reserve()` 不再在请求写路径上执行全表过期 key 清理；默认内存实现改为首次占用 replay key 后懒启动 `coco-replay-cleanup` 守护线程，按 `coco.web.replay.cleanup-interval-seconds` 执行后台清理，并在 Spring Bean 销毁时关闭线程。

---

### B5.【正确性 / 中】加密过滤器把所有解密异常映射成一个错误码

真实环境会出现 IV 错误 / GCM tag 失败 / key 不匹配 / body 损坏——全部吞成同一码 → 攻击者可作为 oracle。

**建议：** 区分"格式错（客户端坏）"与"鉴权错（潜在攻击）"，前者 400 后者 401 且记审计。

**处理状态：** PR28 已处理。`CocoRequestDecryptException` 增加格式错误与认证/完整性失败分类；AES-GCM 解密器将算法不支持、IV / payload 编码错误、空 IV、payload 短于 GCM tag 长度映射为 400，将 GCM tag 校验失败、密钥不匹配等认证失败保留为 401，并补充消息资源与回归测试。审计事件接入后续可基于该分类继续扩展。

---

### B6.【安全 / 中】SQL 防护（BlockAttack / IllegalSQL）默认关闭

`CocoMybatisPlusSqlGuardProperties` 两个开关默认 false，Javadoc 自承"默认关闭以避免误伤"。但这违背"safety by default"——开了租户隔离的人最依赖这个保护。

**建议：** 默认不要翻，但：
- Javadoc 明确给出生产建议（true）；
- `create()` 在两者都 false 时打一行 INFO；
- README 给出"启用后会拦截哪些合法 SQL"的清单。

**处理状态：** PR18 已处理。保留默认关闭以避免破坏既有 SQL，在 `CocoMybatisPlusInterceptorFactory.create()` 输出生产启用建议 INFO；配置 JavaDoc 和中英文 README 已补生产启用示例及可能被拦截的正常 SQL 形态清单。

---

### B7.【安全 / 中】加密过滤器在重放过滤器之前，攻击者可在重放检查前消耗 CPU

`CocoSignatureFilter (+2) → CocoEncryptionFilter (+3) → CocoReplayFilter (+4)`。伪造请求先做完整签名校验 + 解密，再到重放校验才被拒。

**建议：** 把"轻量、廉价"的检查（trace / headers / replay key 形态）前置；加密 / HMAC 验证放后。可加一个独立的 `CocoRequestShapeFilter` @ `HIGHEST_PRECEDENCE+1` 做粗筛。

**处理状态：** PR29 已处理。新增 `CocoReplayRequestShapeFilter` 在签名验签、AES 解密和 replay store 占用之前执行防重放请求形态预检；当前顺序为 request-body `+0`、trace `+1`、replay-shape `+2`、signature `+3`、encryption `+4`、replay `+5`。预检只复用 `CocoReplayKeyResolver` 校验必需字段和 timestamp 格式，不做 HMAC、AES、store reserve 或 replay 时间窗口判断，并补充回归测试确认缺少 replay nonce 的签名请求不会进入 `CocoSignatureVerifier`。

---

### B8.【正确性 / 中】`CocoWebExceptionHandler` 兜底 `Exception.class` 抓所有 5xx，吞掉 `AsyncRequestNotUsableException` 等客户端断开信号 → 误报 500

**建议：** 单独识别 client disconnect / `ClientAbortException`，不计入 5xx 统计、不发告警。

**处理状态：** PR17 已处理。`handleUnhandledException` 使用 Spring `DisconnectedClientHelper` 识别客户端断开异常并原样抛出，不再生成 Coco 500 响应，也不写入异常日志。

---

### B9.【正确性 / 中】`CocoFilterExceptionResponseWriter` 用 `RequestContextHolder` 侧面拿数据，并行错误渲染下不线程安全

**建议：** 改为在过滤器链入口捕获并显式传递；或加 `ThreadLocal` 隔离。

**处理状态：** PR20 已处理。`CocoFilterExceptionResponseWriter` 不再临时替换 `RequestContextHolder`，改为从当前 `HttpServletRequest` 显式传递请求语言给 `CocoWebExceptionHandler`；回归测试覆盖过滤器异常响应按 `Accept-Language` 本地化，并确认已有请求上下文不会被替换或清理。

---

### B10.【正确性 / 中】`BodyCache` filter 触发签名 / 加密的检测用手写 `split("&")`

在 query 含 `=` 转义场景下会误判。

**建议（S）：** 改用 `UriComponentsBuilder` / `HttpServletRequest.getParameterMap()`（先 cache body）。

**处理状态：** 已在 PR16 将 BodyCache query 触发参数名解析改为 Spring `UriComponentsBuilder`，并保留 malformed query 的原始回退；补充编码参数名触发缓存的回归测试，避免签名 / 加密 / 重放参数名包含转义字符时漏掉请求体缓存。

---

### B11.【正确性 / 中】`CocoTraceFilter.restoreMdcValue` 用 `==` 判空，依赖 MDC 防御性拷贝的隐式行为

**建议（S）：** 显式 `previous == null` 判定，或用 `Optional`。

**处理状态：** PR19 已处理。当前实现已使用显式 `previousMdcValue == null` 分支，回归测试覆盖请求内 MDC 临时覆盖、默认和自定义 MDC key 请求结束后恢复原值，以及无原值时清理的行为，防止后续重构回退。

---

### B12.【性能 / 中】`ResponseBodyAdvice` 对大响应体无大小阈值

**建议：** 引入可配置阈值；超出时 WARN 并跳过包装或分片。

**处理状态：** PR23 已处理。`coco.web.response-wrap.max-body-bytes` 提供正常响应包装阈值，默认 `-1` 保持不限制；当响应 `Content-Length` 或字符串响应体字节数等已知长度超过阈值时跳过统一包装并输出 WARN，避免为估算大小提前序列化任意业务对象。

---

### B13.【正确性 / 中】`InMemoryCocoReplayStore` 清理是非阻塞的并发扫描 —— 与 B4 重复，归并处理

**处理状态：** 已归并到 PR27，与 B4 一并收口。

---

## C. v1.0 必修：架构、API 边界、测试覆盖

### C1.【API 设计 / 中】`CocoConfigurer` 像 `WebMvcConfigurer`，但只暴露 1 个方法且没有 order 契约

**建议（S）：** 要么删掉，要么补到 ≥ 3 个真正可扩展的点 + `@Order` 支持。

---

### C2.【API 设计 / 中】`CocoFeatureRegistry` 是可变接口，唯一消费者是内部 collector

**建议（S）：** 收回或封进 `internal` 包；公开 API 用 `Set<CocoFeature>` 即可。

---

### C3.【API 设计 / 中】`CocoOpenApiMetadataProvider` SPI 有默认实现但**没有消费者** —— 产出后立即丢弃

**建议（S）：** 删除，或补 springdoc 集成。

**处理状态：** PR30 已处理。`coco-feature-openapi` 在业务项目自行引入 SpringDoc / Swagger OpenAPI 类型时，会条件注册
`cocoSpringDocOpenApiCustomizer`，将 `CocoOpenApiMetadataProvider` 输出的标题、版本和描述适配到
SpringDoc 的 `OpenAPI.info`；框架不新增 SpringDoc 强依赖，并提供 `coco.openapi.springdoc.enabled`
用于关闭该适配。

---

### C4.【架构 / 中】`coco-feature-mybatis-plus` 用 `beanClass.getName()` 字符串名在 MP 自动配置之间插队

未来 MP 改类名 → 顺序错乱，租户 / 分页拦截器执行顺序错了。

**建议（S）：** 改用 `DependsOnPostProcessor` 或 `BeanFactoryPostProcessor` 显式排序。

**处理状态：** PR31 已处理。`CocoMybatisPlusAutoConfiguration` 不再通过 `afterName` / `beforeName`
字符串引用 MyBatis-Plus 自动配置，改为类型安全的 `after` / `before` 排序；租户和数据权限的
MyBatis-Plus 接入也改为类型安全依赖 Coco MyBatis-Plus 自动配置。回归测试固定这些自动配置的
`afterName` / `beforeName` 为空，避免后续重新退回字符串类名排序。

---

### C5.【测试 / 中】`coco-feature-data-permission` 26 主类 / 2 测试；`coco-feature-web` 128 主类 / 4 测试

**建议（M）：** 关键路径加单测：
- `PropertyCocoDataPermissionSqlResourceResolver`（资源名归一化）；
- `DefaultCocoDataPermissionSqlPredicateProvider`（A3 数值列 bug 必测）；
- 三个安全过滤器（签名 / 加密 / 重放）。

**处理状态：** 数据权限两条关键路径已在前序数据权限批次覆盖。PR32 补充
`DefaultCocoWebRequestSecurityInputResolver` 直接单测，固定签名 / 加密 / 防重放过滤器共享的
安全输入快照边界，包括安全请求头自动纳入、规范化请求头多值保留、规范化 Cookie、query 与
payload 原始参数拆分、cached body 摘要，以及请求体解析器返回空结果时的未缓存回退。

---

### C6.【构建 / 中】`coco-samples/coco-sample-basic/pom.xml:10` 把 parent 版本硬编码为 `1.0.2`，但根 pom 的 `${revision}=1.0.0-SNAPSHOT`，`.flattened-pom.xml` 也是 SNAPSHOT

发布后第一次 `mvn install` 即失败。

**建议（S）：** 改为 `${revision}` 占位 + flatten-maven-plugin；CI 里加 `mvn -N validate` 对样本做版本一致性检查。

**处理状态：** PR33 已处理。Maven 在独立构建 sample 时无法可靠地在 parent 解析前替换
`<parent><version>${revision}</version>`，因此不采用该不可验证方案；保留 Maven 要求的字面 parent
版本，同时新增跨平台脚本校验 `coco-sample-basic` parent 版本必须等于根 POM 的 `revision`，并在 CI 的
sample 构建前执行，避免框架版本前进后 sample 仍绑定旧 parent。

---

### C7.【构建 / 中】根 pom 没有 `maven-enforcer-plugin`，发布没有 banned-dependencies / convergence / require-release-version 闸门

**建议（S）：** 加：
- `dependencyConvergence`；
- `bannedDependencies`（commons-logging / log4j-over-slf4j / spring-boot-starter-tomcat 之外的 servlet 容器）；
- `requireReleaseVersion`（仅 release profile）。

**处理状态：** PR21 已处理。根 POM 已引入 `maven-enforcer-plugin`，常规 validate 阶段检查直接依赖禁用清单、compile/runtime 依赖收敛和重复依赖声明；release profile 额外启用 `requireReleaseVersion`，默认 SNAPSHOT 版本会被拒绝，`-Drevision=1.0.0` 等正式版本可通过。

---

### C8.【构建 / 中】CI 只跑 ubuntu-latest，JDK 21 编 Java 17 字节码

**建议（S）：** 加 windows-latest / macos-latest 矩阵；CI 显式区分 compile-target=17 / toolchain=21。

**处理状态：** PR22 已处理。CI `verify` job 改为 `ubuntu-latest`、`windows-latest`、`macos-latest` 矩阵；workflow 显式声明 JDK 21 toolchain 与 Maven compiler release 17，并通过 `setup-python`、`PYTHONUTF8=1` 与 `python` 命令统一样例黑盒验证入口，样例验证脚本也在启动时将输出流重配为 UTF-8。

---

### C9.【DX / 中】唯一 sample 是内存级 order / product，MyBatis-Plus / 租户 / 数据权限 / 安全上下文 / 审计都没在 sample 里跑过

README 上列的 8 个 feature，开箱验证只能看到 web + i18n + 签名 / 加密 / 重放。

**建议（L）：** 增加 `coco-sample-tenant` 或 `coco-sample-full`：H2 + MP + 启用全部 feature + `CocoSecurity.requireRole` + 一次 `cocoAuditPublisher.publish` + 一次租户绑定查询 + 数据权限过滤。同步扩展 `verify_business_flow.py`。

---

### C10.【DX / 中】`coco-feature-audit / openapi / codegen` 三个都是"占位 SPI + NoOp"

README 描述的"audit pipeline / openapi / generated CRUD"在 starter 里**完全没有**，是文档与实现的明显 gap。

**建议（M）：** 把 README 里这三项移到 "Roadmap" 或 "Extension points" 小节，避免开发者按字面理解后找不到东西。

---

### C11.【安全 / 中】Postman 生成器与 `verify_business_flow.py` 把签名密钥硬编码

**建议（S）：** 用 `${env.SAMPLE_SIGNING_KEY:-}` 形式；README 加警告。

---

### C12.【构建 / 中】`CocoPackagePruneMojo` 在 PACKAGE 阶段就地改写 boot jar，之后任何 jar 签名都失效，且当前没有"原始 jar 备份"

**处理状态：** 已在 PR12 补充 `target/coco-prune.original.jar` 原始包备份。裁剪发生时先复制原始 Spring Boot jar，再替换主 jar，便于排查裁剪差异和签名流程显式选择产物。

**建议（S）：** 把 .original 备份写到 `target/coco-prune.original.jar`；CI 上 `git status` 检测到意外产物即报错。

---

### C13.【架构 / 中】功能机制有 3 个 config 源（默认 / 配置 / 注解），各自语义不同，没有任何 trace 日志说明走了哪条路

**处理状态：** 已在 PR11 补充运行期和构建期功能解析日志。日志包含配置源摘要、最终启用/禁用集合和依赖传播禁用集合。

**建议（M）：** 在 `CocoRuntimeFeatureResolver` 完成时打一行 INFO："resolved=N from sources={default, properties, annotation} disabled-by-deps={...}"。

---

### C14.【架构 / 中】功能依赖驱动禁用是静默的、不可逆的，没有任何 log 与测试

**处理状态：** 已在 PR11 增加 `CocoFeaturePlan.disabledByDependencyFeatures()` 诊断方法、启动/构建日志和依赖传播禁用单测。

业务写着写着发现某个 feature 关了，但不知是依赖关系关的——调试地狱。

**建议（S）：** 在 `resolveEnabledFeatures` 返回时同时返回 `disabledByDependency` 集合，启动打 INFO 列出；加测试。

---

### C15.【测试 / 中】`CocoSecurityContextHolder` 无线程安全 / 负向测试

**处理状态：** 已在 PR15 扩展 `CocoSecurityContextHolderTest`，覆盖 ThreadLocal 跨线程隔离、包装任务异常后恢复工作线程上下文、临时上下文异常恢复、缺失上下文异常和 null 入参校验。

---

### C16.【测试 / 中】`PropertyCocoDataPermissionSqlResourceResolver` 仅在集成测试里被 inline 构造，无直接单测

**处理状态：** 已在 PR13 增加直接单测，覆盖普通表名规范化、schema-qualified 表名解析、空资源键忽略和未知表不匹配。

---

### C17.【测试 / 中】三个 feature（audit / openapi / codegen）的测试仅覆盖 auto-config wiring，无端到端验证

**处理状态：** PR24 已处理。audit 补充自动配置发布器向多个自定义 recorder 分发事件的测试；openapi 补充配置绑定后元数据归一化测试；codegen 补充自定义生成器读取模板配置并消费归一化请求的链路测试，同时固定请求、生成文件和结果模型的输入校验与不可变性。

---

### C18.【构建 / 中】`CocoPackagePruneMojo` 的测试断言文件存在但未断言结果是可运行的 Spring Boot jar

**处理状态：** 已在 PR12 扩展裁剪测试 fixture，生成带 Spring Boot launcher、manifest 和 `BOOT-INF` 结构的测试 jar，并断言裁剪后仍保留可执行 Boot jar 关键结构。

---

### C19.【测试 / 中】`CocoFeaturesMojo.resolveRuntimeArtifact` 回退路径从未被测试

**处理状态：** 已在 PR14 补充构建期功能依赖注入测试，覆盖 Maven Resolver 不可用和 artifact 解析失败两条回退路径，确认依赖仍写入 Maven model，失败 artifact 不会进入已解析 classpath。

---

### C20.【测试 / 中】`SQL-rewriter missing-rule IGNORE policy` 与 schema-qualified Table 未测试

**处理状态：** 已在 PR13 补充 handler 级测试，显式覆盖缺少资源规则时 `IGNORE` 策略返回空谓词，以及 schema-qualified table 能匹配资源配置并生成数据权限谓词。

---

### C21.【构建 / 中】`maven-failsafe-plugin` 声明但未绑定；无 `*IT` 集成测试

**处理状态：** PR34 已处理。根构建将 `maven-failsafe-plugin` 绑定到 `integration-test` 和
`verify` goals，并设置 `failIfNoTests=false`，避免无 IT 模块被误伤；`coco-spring-boot-starter`
新增 `CocoStarterSmokeIT`，通过真实 `SpringApplication` 启动 servlet 应用上下文，验证单 starter
接入时 Common i18n 与 Web 请求上下文基础设施可用。

---

### C22.【测试 / 中】`coco-test` 模块提供 `CocoTestSupport` 但没有任何 test 模块 import

**处理状态：** PR25 已处理。`CocoTestSupport` 已落成共享配置元数据测试工具，提供配置元数据 JSON 读取、通用 JSON 资源读取和命名节点查找能力；`coco-feature-codegen` 以 test scope 引入 `coco-test` 并改用该工具读取配置元数据，同时 `coco-test` 自身补充工具行为单测。

---

### C23.【DX / 中】`verify_business_flow.py` 不是跨平台的（`subprocess java -jar`）

---

### C24.【测试 / 中】`CocoAnnotatedFeatureScanner` 已有描述（A6），归并

---

## D. 低优 / 卫生级（38 条）

| # | 维度 | 类别 | 标题 |
| --- | --- | --- | --- |
| D1 | api-spi | build-release | Schema-version 兼容好但隐式 disable 行为未文档化、未测试 |
| D2 | api-spi | testing | `CocoFeatureRegistry` 静默丢弃 null vararg；无测试钉住"disable-wins"行为 |
| D3 | api-spi | api-design | `@CocoFeatures` / `@ConditionalOnCocoFeature` 注解清晰但被锁定在封闭 enum |
| D4 | architecture | architecture | 两套平行的"security"分类法 —— `coco-feature-security` 与 `coco-feature-web.security.metadata` 无共享契约 |
| D5 | architecture | maintainability | 模块名 `coco-feature-data-permission` 与包名 `io.github.coco.feature.datapermission`（无连字符） |
| D6 | architecture | architecture | 8 个 `Coco*Feature.java` marker 类是 23-24 行纯文档，与 AGENTS.md "avoid opaque switches" 相悖 |
| D7 | architecture | maintainability | `coco-common-i18n` 模块名但拥有 `CocoCommonAutoConfiguration` 与 `CocoCommonProperties` |
| D8 | architecture | api-design | `CocoOpenApiAutoConfiguration` 的 `@AutoConfigureAfter` 同时绑 web 与 security |
| D9 | data-sql | correctness | `PaginationInnerInterceptor` 追加在最后，依赖 customizer 注册顺序保证租户 / 数据权限先于分页 |
| D10 | data-sql | security | `CocoTenantSqlProperties.ignoreTables` 归一化小写，但 `TenantLineInnerInterceptor` 内部也小写；跨方言在带引号标识符下分歧 |
| D11 | data-sql | correctness | `DataPermissionInterceptor` 早于 `TenantLineInnerInterceptor`（字母序），`TenantLineHandler.ignoreTable` 不考虑 schema-qualified，子查询可能被双重改写 |
| D12 | data-sql | api-design | `CocoDataPermissionSqlPredicateContext.resourceProperties()` 在 cache miss 时返回新空对象，谓词 provider 拿不到稳定引用 |
| D13 | dx-docs | developer-experience | sample 的 `application.yml` 硬编码 Windows 风格 `node` 路径，macOS / Linux runner 上静默失败 |
| D14 | dx-docs | developer-experience | sample 声明两个 i18n bundle（messages, coco-messages）但都未从框架 ship，首用者会看到混乱的 bundle 解析顺序 |
| D15 | dx-docs | testing | audit / openapi / codegen 测试仅覆盖 auto-config wiring |
| D16 | feature-mechanism | build-release | manifest 格式非正式，写入字段有未读字段 |
| D17 | feature-mechanism | maintainability | 同一个"启用 / 禁用"概念有 3 个独立数据载体 |
| D18 | feature-mechanism | api-design | `@ConditionalOnCocoFeature` 没有 `matchIfMissing` / 反义 / 多 feature 支持 |
| D19 | feature-mechanism | build-release | `CocoPackagePruneMojo` 在 PACKAGE 阶段就地改写 boot jar，作废后续任何 jar 签名（与 C12 重叠） |
| D20 | feature-mechanism | testing | prune-package 测试断言文件存在但未断言结果可运行（与 C18 重叠） |
| D21 | quality-test | testing | `SQL-rewriter missing-rule IGNORE policy` 未测试（与 C20 重叠） |
| D22 | quality-test | testing | `coco-test` 模块的 `CocoTestSupport` 未被任何模块引用（与 C22 重叠） |
| D23 | quality-test | build-release | `maven-failsafe-plugin` 声明但未绑定（与 C21 重叠） |
| D24 | quality-test | developer-experience | sample `verify_business_flow.py` 非跨平台（与 C23 重叠） |
| D25 | quality-test | testing | `CocoFeaturesMojo.resolveRuntimeArtifact` 回退路径未测试（与 C19 重叠） |
| D26 | security-module | maintainability | default 与 en_US message bundle 字节相同，无真实英文翻译 |
| D27 | security-module | architecture | 无 `CocoSecurityProperties`，所有行为硬编码无配置入口 |
| D28 | security-module | api-design | `CocoSecurityFeature` 是空的占位类，无契约无行为 |
| D29 | security-module | api-design | `HolderCocoSecurityContextResolver` 是静态委托 shim，SPI 价值为零 |
| D30 | security-module | api-design | `CocoSecurityContext.anonymous()` 硬编码单例 "anonymous" principal，无可配置性 |
| D31 | web | correctness | `CocoWebExceptionHandler.handleUnhandledException` 抓所有 5xx，吞掉客户端断开信号（与 B8 重叠，PR17 已处理） |
| D32 | web | security | 过滤器顺序：加密先于重放，攻击者可消耗 CPU 后才被拒（与 B7 重叠，PR29 已处理） |
| D33 | web | correctness | Trace filter 在校验前解析并存储用户提供的 TraceId，被签名 / 加密当作 key material 上下文使用 |
| D34 | web | api-design | `AsyncCocoLogSink` 在溢出时静默丢 WARN / INFO，无指标无回调 |
| D35 | web | correctness | `CocoFilterExceptionResponseWriter` 用 `RequestContextHolder` 侧面拿数据，并行错误渲染下不线程安全（与 B9 重叠，PR20 已处理） |
| D36 | web | correctness | `BodyCache` filter 触发签名 / 加密的检测用手写 `split("&")`（与 B10 重叠，PR16 已处理） |
| D37 | web | correctness | `CocoTraceFilter.restoreMdcValue` 用 `==` 判空（与 B11 重叠，PR19 已处理） |
| D38 | web | performance | `CocoWebExceptionHandler.localizedFailure` 在日志路径上急切本地化整个 cause chain |

---

## E. 建议的实施路线图

### Q3（v1.0.x 必修）

1. **A1** starter 重构 —— `coco-spring-boot-starter` 瘦身
2. **A5** context 传播原语 + sample 适配器
3. **A3** 数据权限数值列 bug 修复
4. **A4** 租户旁路白名单 + 审计
5. **A6 + B1 + B2 + B3** 安全过滤器测试与硬化
6. **C6 / C7 / C8 / C12** 构建 / CI 闸门 + 原始 jar 备份

### Q4（v1.1 改进）

1. **A2** 安全模块补完 OR 从 README 撤回（建议先撤回，标 v1.2 重写）
2. **C9 / C10** 新增 `coco-sample-tenant` + README 修正
3. **C1-C5 / C13-C21** API 收敛 + 内部接口降级 + 测试覆盖
4. **D 组** 卫生条目批量处理

---

## F. 结论

Coco 是一个原则清晰、骨架扎实的框架，但存在"安全默认不到位、starter 边界与 README 边界不一致、骨架功能多于实际交付"三类张力。最危险的 5 条（A3 / A4 / A5 / B1 / B2）都是"代码本身没错 + 文档承诺 → 业务方按承诺部署 → 被攻击"链路；修复代码改动量都**小**（多数 S 级），但需要把"安全默认"作为 v1.0 不可妥协的闸门来对待。

**优先级判断：** 如果只能修 3 个，先做 A1（starter 边界）+ A5（context 传播）+ A3（数值列 bug）—— 它们分别解决"边界契约"、"跨线程正确性"、"开箱即错"三类问题，命中率最高。

---

## 附录 A. 审计方法学

- **维度：** 架构 / 功能机制 / Web / 数据-SQL / 安全模块 / API-SPI / 测试与构建 / 开发者体验
- **每维度：** 一个 find agent 产出 `moduleSummary + findings[evidence/problem/recommendation/effort]`
- **非低危发现：** 一个独立 verify agent 复核 isReal / severityAdjusted / recommendationSound，默认 isReal=false
- **判定原则：** 与 `AGENTS.md` 中"不过度封装业务、安全默认、starter 只组合不拥有、API 小而稳"对齐
- **剔除原则：** 与项目"有意识原则"相悖的"过度优化"建议（如要求消除 feature SPI、要求 starter 重新拆开等）一律 refuted
- **数据：** 84 个 agent 调用 / 1,157 次工具调用 / 2.9M tokens / ~9 分钟 wall-clock
