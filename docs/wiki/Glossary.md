# 术语表

本页回答：读代码、文档或 PR 时遇到的 Coco 专有名词是什么意思，它在哪个文件里定义。

> 每条给“一句话定义 + 源文件 / 出处”。路径相对仓库根目录。英文术语保持原样。定义取自 `AGENTS.md`、`GOVERNANCE.md`、`CONTRIBUTING.md` 与源码 JavaDoc，内容对应仓库 `main` 分支。文档与实现不一致的地方以“文档与实现存在出入”开头说明，并给出权威来源。

## 快速索引

| 想找的方向 | 去哪一节 |
| --- | --- |
| Feature、计划、清单、开关注解 | 一、Feature 体系 |
| BOM、父 POM、starter、门面、裁剪 | 二、构建与模块 |
| 请求上下文、签名、加密、重放、Trace | 三、运行期与 Web |
| 消息包、日志、审计、Store、SPI | 四、通用机制 |
| Surefire、Failsafe、flatten POM | 五、测试与构建工具 |
| 各种 gate、agent 分支、CODEOWNER | 六、仓库治理与流程 |
| coco-admin、coco-generate、CodeGraph | 七、生态与工具 |

按字母排序的术语总表（便于 Ctrl+F）：`@CocoFeatures`、`@ConditionalOnCocoFeature`、Agent issue gate、Agent jury gate、autoconfigure、BOM、bundle、canonical artifact（规范制品）、CI gate、`codex/*`、CODEOWNER、`coco-admin`、`coco-generate`、`coco-parent`、CodeGraph、compatibility facade（兼容门面）、Contributor gate、`CocoConfigurer`、`CocoFeature`、`CocoFeatureManager`、`CocoFeaturePlan`、`CocoFeatureRegistry`、`CocoFeatureSelection`、`CocoMessageBundleRegistrar`、`CocoPageContext`、`CocoRequestContext`、Encryption、`StandardCocoFeatures`、Failsafe、Feature、Feature manifest、flatten POM、`ImportFilter`、prune / `prune-package`、Promotion gate、Replay、Security context、Signature、SPI、starter、Store / StoreType、Surefire、Tenant context、Trace / TraceId、依赖传播、数据权限上下文。

## 一、Feature 体系

| 术语 | 一句话定义 | 源文件 / 出处 |
| --- | --- | --- |
| Feature（功能） | 可独立启用或禁用的框架能力。当前有 16 个：`web`、`mybatis-plus`、`audit`、`security`、`tenant`、`data-permission`、`openapi`、`rate-limit`、`idempotency`、`scheduling`、`lock`、`storage`、`messaging`、`cache`、`notification`、`captcha`；Feature 的个数与成员以 `CocoFeature` 枚举为准（16 项），内置 Codegen 功能及其 feature id 已从框架移除 | `coco-foundation/coco-api/src/main/java/io/github/coco/api/feature/CocoFeature.java` |
| `CocoFeature` | Feature 的稳定枚举，`id()` 返回配置文件、构建清单和日志里使用的 kebab-case 标识（如 `MYBATIS_PLUS` → `mybatis-plus`），避免业务配置里散落字符串 | 同上 |
| `StandardCocoFeatures` | 标准 Feature 元数据的唯一登记处：每个 Feature 的 artifactId、自动配置 FQCN、Feature 依赖、裁剪用 artifactId 集合，以及 `resolve(...)`、`toManifest(...)`、`fromManifest(...)`；16 个 Feature 的 `defaultEnabled` 当前都是 `true` | `coco-foundation/coco-feature-model/src/main/java/io/github/coco/feature/model/StandardCocoFeatures.java` |
| `CocoFeatureDefinition` | 描述单个 Feature 的 record：feature、artifactId、自动配置类名、是否默认启用、依赖、裁剪 artifactId | `coco-foundation/coco-feature-model/src/main/java/io/github/coco/feature/model/CocoFeatureDefinition.java` |
| `CocoFeatureSelection` | 一次“选择”的 record（enabled / disabled 两个集合）；`merge(lower, higher)` 按 Feature 逐项覆盖，后者优先级更高 | `coco-foundation/coco-feature-model/src/main/java/io/github/coco/feature/model/CocoFeatureSelection.java` |
| Feature 计划 `CocoFeaturePlan` | 解析后的最终结果：启用集合、禁用集合与各 Feature 的定义；`disabledByDependencyFeatures()` 可区分“被依赖连带禁用”的 Feature | `coco-foundation/coco-feature-model/src/main/java/io/github/coco/feature/model/CocoFeaturePlan.java` |
| 依赖传播 | 解析时若某个已启用 Feature 的依赖没有全部启用，就把它也移除，直到不再变化。例：禁用 `mybatis-plus` 会连带让 `tenant`、`data-permission` 不生效 | `StandardCocoFeatures.resolve`；[FAQ-Troubleshooting](FAQ-Troubleshooting) |
| Feature 清单 manifest | 构建期写入产物的 `META-INF/coco/features.json`，记录最终的 Feature 计划；当前 `schemaVersion` 为 `1.1`，同时接受 `1.0`；运行期只要 classpath 上有它，就直接据此还原计划，不再合并业务配置 | `CocoFeatureManifest.java`、`CocoFeatureManifestLoader.java`（同在 `coco-feature-model` 的 `io.github.coco.feature.model` 包） |
| `@CocoFeatures` | 标在 Spring 配置类上的注解，用 `enabled` / `disabled` 以代码方式声明 Feature 开关；同一配置源对同一 Feature 既启用又禁用时，**禁用优先** | `coco-foundation/coco-api/src/main/java/io/github/coco/api/feature/CocoFeatures.java` |
| `@ConditionalOnCocoFeature` | 标在自动配置类或 `@Bean` 方法上的条件注解，按最终 Feature 计划决定是否装配；由 `OnCocoFeatureCondition` 实现，`CocoRuntimeFeatureResolver` 负责读取 manifest 或绑定属性。注意：并非每个 Feature 的自动配置都使用它（lock、cache 等 7 个模块只靠各自的 `enabled` 属性），设计意图未见文档说明，行为以各模块自动配置类源码为准 | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/feature/runtime/condition/ConditionalOnCocoFeature.java` |
| `CocoFeatureManager` | 运行期查询 Feature 状态的统一入口：`isEnabled`、`enabledFeatures`、`disabledFeatures`；默认实现 `DefaultCocoFeatureManager` | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/config/CocoFeatureManager.java` |
| `CocoConfigurer`（已废弃） | 早期的 Java 配置入口，`@Deprecated(since = "1.0.0")`，仅为兼容保留；推荐用 `coco.features.*` 配置或 `@CocoFeatures` | `coco-foundation/coco-api/src/main/java/io/github/coco/api/CocoConfigurer.java` |
| `CocoFeatureRegistry`（已废弃） | 只服务于已废弃的 `CocoConfigurer` 的注册器契约，同为 `@Deprecated(since = "1.0.0")` | `coco-foundation/coco-api/src/main/java/io/github/coco/api/feature/CocoFeatureRegistry.java` |
| `CocoFeatureSelectionCollector` | 运行期收集 `@CocoFeatures` 与 `CocoConfigurer` 声明的组件 | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/config/CocoFeatureSelectionCollector.java` |
| `CocoFeatureAutoConfigurationImportFilter` | 通过 `spring.factories` 注册的 `AutoConfigurationImportFilter`：在 `mybatis-plus` Feature 未启用时过滤 MyBatis-Plus 的自动配置，并在满足条件时一并过滤 Spring Boot 的数据源自动配置 | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/feature/runtime/autoconfigure/CocoFeatureAutoConfigurationImportFilter.java` |
| 两层开关 | “Feature 开关”（`coco.features.*`，决定装不装配、是否被裁剪）与“模块级开关”（如 `coco.rate-limit.enabled`，决定装配后的行为）是两回事，且各模块默认值不同 | [Feature-System](Feature-System)；`website/docs/feature-toggles.md` |

## 二、构建与模块

| 术语 | 一句话定义 | 源文件 / 出处 |
| --- | --- | --- |
| BOM（`coco-dependencies`） | 依赖管理 POM：导入 `spring-boot-dependencies`，统一管理 Coco 各模块（含旧坐标）的版本；对外的权威版本清单 | `coco-build/coco-dependencies/pom.xml` |
| `coco-parent` | 业务应用推荐的父 POM：导入 BOM，并在构建里绑定 `spring-boot:repackage`、`coco:features`、`coco:prune-package`；`coco.enforcer.skip=true`，业务应用不继承框架的 Enforcer 门禁 | `coco-build/coco-parent/pom.xml` |
| starter（`coco-spring-boot-starter`） | 业务应用唯一的常规 starter 依赖，只做能力**组合**，不承载行为；没有主代码，只有 `pom.xml`；不直接包含 `coco-rate-limit` 与 `coco-idempotency` | `coco-spring/coco-spring-boot-starter/pom.xml`；`CocoRateLimitStarterDependencyContractTest` |
| autoconfigure（`coco-spring-boot-autoconfigure`） | Spring 组合层：配置绑定、Feature 运行时、自动配置与早期启动集成；也承载原 `coco-config` 与 `coco-feature-runtime` 的实现（保留旧包名） | `coco-spring/coco-spring-boot-autoconfigure/` |
| `coco-maven-plugin` | 构建期插件：`features` goal 写 manifest、补齐已启用 Feature 的依赖、剔除已禁用 Feature 的 artifact；`prune-package` goal 处理 Spring Boot 归档 | `coco-build/coco-maven-plugin/src/main/java/io/github/coco/maven/CocoFeaturesMojo.java`、`CocoPackagePruneMojo.java` |
| 裁剪 prune | 把被禁用 Feature 的 jar 从产物里去掉。构建期按 `pruneArtifactIds` 剔除项目 artifact；打包期 `prune-package` 从 Boot 归档的 `BOOT-INF/lib/` 删除对应 jar，并同步过滤 `BOOT-INF/classpath.idx`、`BOOT-INF/layers.idx` | `CocoPackagePruneMojo.java`；`StandardCocoFeatures` |
| `prune-package` | `coco-maven-plugin` 的 goal（默认阶段 `package`）：读取 `features.json`，裁剪 Boot 归档；原归档备份为 `target/coco-prune.original.jar`；无禁用项或无归档时跳过；受 `coco.features.skip` 控制 | `coco-build/coco-maven-plugin/src/main/java/io/github/coco/maven/CocoPackagePruneMojo.java` |
| `pruneArtifactIds` | `CocoFeatureDefinition` 的字段：禁用该 Feature 时要从产物剔除的 artifactId 集合，包含旧的 `coco-feature-*` 名称，`mybatis-plus` 还包含第三方 `mybatis*` artifact | `StandardCocoFeatures.java` |
| compatibility facade（兼容门面） | 位于 `coco-build/coco-compatibility/` 的**无源码** jar，用来让已发布的旧坐标（如 `coco-config`、`coco-feature-web`）在 2.x 内继续可解析；只依赖对应的规范制品，**不得**复制实现、自动配置导入、`spring.factories` 或消息资源；任何框架模块都不得依赖它们 | `coco-build/coco-compatibility/`；`coco-support/coco-document/architecture/module-layout.md` |
| 规范制品 canonical artifact | 门面所指向的、真正承载实现的新名称制品（如 `coco-feature-web` → `coco-web`，`coco-config` → `coco-spring-boot-autoconfigure`） | 同上 |
| 编译探针 `*CompatibilityCompileProbe` | 门面模块里的测试类，以编译期引用的方式钉住旧坐标曾公开的类型；类型被移动或改名则测试编译失败 | `coco-build/coco-compatibility/coco-config/src/test/java/io/github/coco/compatibility/config/CocoConfigCompatibilityCompileProbe.java` |
| `coco-features`（聚合） | 聚合 16 个 Feature 模块的 pom；新增 Feature 模块要在它的 `<modules>` 里登记 | `coco-features/pom.xml` |
| `coco-support` | 文档、开发工具与测试支持，不进入普通业务运行时 | `GOVERNANCE.md` 的 Module ownership |
| `revision` | 根 `pom.xml` 的版本属性，本地默认 `1.0.0-SNAPSHOT`，正式版本由 `-Drevision=X.Y.Z` 注入；`coco-parent` 里 `coco.version` 等于它 | `pom.xml`、`coco-build/coco-parent/pom.xml` |
| groupId 与 Java 包根 | Maven groupId 是 `io.github.patton174`；Java 包根是 `io.github.coco`，两者不同 | `pom.xml`；各模块 `src/main/java` |
| Enforcer 门禁 | 根 `pom.xml` 在 `validate` 阶段的构建规则：禁止重复依赖版本、要求依赖收敛，并禁用一组依赖（如 `commons-logging`、`spring-boot-starter-jetty`、`spring-boot-starter-undertow`） | `pom.xml` |

## 三、运行期与 Web

| 术语 | 一句话定义 | 源文件 / 出处 |
| --- | --- | --- |
| `CocoRequestContext` | 当前请求的统一上下文快照：TraceId、HTTP 方法、请求路径与扩展属性，供日志、审计、租户、数据权限等能力共享；通过 `CocoRequestContextHolder` 访问 | `coco-foundation/coco-context/src/main/java/io/github/coco/context/CocoRequestContext.java` |
| Trace / TraceId | 一次请求的链路标识。`CocoTraceFilter` 从请求头读取，缺失则生成，写入 `CocoTraceContext` 和响应头，请求结束后清理；统一响应 `CocoApiResponse` 也可带 `traceId`。默认头名是 `X-Trace-Id`（`coco.web.trace.header-name`），默认 MDC key 是 `traceId`（`coco.web.trace.mdc-key`），常量见 `CocoTraceProperties` | `coco-features/coco-web/src/main/java/io/github/coco/feature/web/trace/CocoTraceFilter.java`；`coco-foundation/coco-context/.../trace/CocoTraceContext.java` |
| 统一响应 / response wrap | 把控制器返回值包装成 `CocoApiResponse`（success / code / message / data 等）的机制，由 `CocoResponseWrapAdvice` 完成；`@CocoIgnoreResponseWrap` 可跳过；`coco.web.response-wrap.enabled` 默认 `true` | `coco-features/coco-web/src/main/java/io/github/coco/feature/web/response/` |
| `CocoPageContext` | 当前请求的分页与排序参数（`page` 从 1 开始、`size`、排序列表）。Web 层的 `CocoPageInterceptor` 在入口设置，MyBatis-Plus 一侧读取，查询层无需显式传参 | `coco-foundation/coco-context/src/main/java/io/github/coco/context/CocoPageContext.java`；`coco-features/coco-web/.../page/CocoPageInterceptor.java` |
| Signature（请求签名） | 对请求做 HMAC 类签名校验的能力，由 `CocoSignatureFilter` 执行，默认算法 `HMAC-SHA256`，元数据默认取自 `X-Coco-*` 请求头；密钥放在 `coco.web.signature.secrets`（只在文档里写键名，不写值） | `coco-features/coco-web/src/main/java/io/github/coco/feature/web/signature/` |
| Encryption（请求加密） | 对请求体做解密的能力，由 `CocoEncryptionFilter` 执行，默认算法 `AES-GCM`；密钥放在 `coco.web.encryption.keys`（只写键名） | `coco-features/coco-web/src/main/java/io/github/coco/feature/web/encryption/` |
| Replay（防重放） | 基于请求时间戳与随机串在有效窗口内占用“防重放键”，阻止签名或加密请求被重复提交；`CocoReplayStore` 有 `IN_MEMORY`（进程内，默认）、`REDIS`、`JDBC` 三类实现，JDBC 为需显式选择的参考实现 | `coco-features/coco-web/src/main/java/io/github/coco/feature/web/replay/` |
| 访问日志 access log | 记录每个请求的结构化访问日志；契约与格式化在 `coco-logging`，抓取开关在 `coco.web.access-log` | `coco-foundation/coco-logging/`；`coco-features/coco-web/.../accesslog/` |
| Security context（安全上下文） | `coco-security` 提供的**上下文传递机制**（`CocoSecurityContext` / `CocoSecurityContextHolder`），不是认证实现；认证选择属于下游应用。其 Header 解析器信任上游 HTTP 头，开启会输出 WARN，必须置于可信网关之后 | `coco-features/coco-security/src/main/java/io/github/coco/feature/security/context/` |
| Tenant context（租户上下文） | 当前请求的租户信息（`CocoTenantContext` / `CocoTenantContextHolder`），供 MyBatis-Plus 租户 SQL 隔离使用；`CocoTenantLineHandler` 负责注入租户条件，默认列名 `tenant_id` | `coco-features/coco-tenant/src/main/java/io/github/coco/feature/tenant/` |
| 数据权限上下文 | 当前请求的数据权限规则（`CocoDataPermissionContext`），供数据权限 SQL 条件使用；SQL 注入默认关闭（`coco.data-permission.sql.enabled=false`） | `coco-features/coco-data-permission/src/main/java/io/github/coco/feature/datapermission/` |
| `CocoMybatisPlusInterceptorCustomizer` | MyBatis-Plus 拦截器链的扩展点，租户与数据权限通过它向链上追加内部拦截器 | `coco-features/coco-mybatis-plus/src/main/java/io/github/coco/feature/mybatisplus/interceptor/CocoMybatisPlusInterceptorCustomizer.java` |
| `cocoTaskExecutor` | 框架提供的异步线程池 Bean（`coco.async.*`），用 `CocoContextTaskDecorator` 把请求上下文传到异步线程 | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/spring/boot/async/` |

## 四、通用机制

| 术语 | 一句话定义 | 源文件 / 出处 |
| --- | --- | --- |
| SPI | 框架里真实存在的替换点：接口 + `@ConditionalOnMissingBean` 的默认实现，业务提供自己的 Bean 即可替换。`AGENTS.md` 要求只有存在真实替换点才新增 SPI | `AGENTS.md` Development Rules；例：`CocoAuditRecorder` |
| `CocoMessageBundleRegistrar` | `@FunctionalInterface`：模块用它向 `coco-i18n` 声明自己的消息资源包，`registerBundles(registry)` 里写 `registry.add("<bundle 名>")`；每个模块的自动配置通常提供一个这样的 Bean | `coco-foundation/coco-i18n/src/main/java/io/github/coco/i18n/CocoMessageBundleRegistrar.java` |
| 消息包 bundle | 某个模块的国际化资源，位于 `src/main/resources/<bundle>.properties` 加 `_zh_CN` / `_en_US` 后缀；用户可见文本应放这里而不是硬编码。基础包名是 `coco-messages`，各模块有自己的名字（如 `coco-feature-security-messages`）；`coco-feature-model` 的包名仍叫历史名 `coco-feature-registry-messages` | `coco-foundation/coco-i18n/`；例：`coco-features/coco-security/src/main/resources/coco-feature-security-messages.properties` |
| `CocoMessageService` | 按 code 与 Locale 取得国际化消息的服务；Bean `cocoMessageSource` 会合并用户配置的 basename、各模块 registrar 以及 `coco-messages` | `coco-foundation/coco-i18n/src/main/java/io/github/coco/i18n/CocoMessageService.java`；`CocoCommonAutoConfiguration` |
| Store / StoreType | 状态型 Feature 的存储抽象：`Coco<X>Store` 接口 + `store-type` 属性。默认多为 `IN_MEMORY`（cache 为 `LOCAL`），可切到 Redis（cache 的二级缓存写作 `two-level`） | 例：`CocoReplayStoreType`、`coco-features/coco-lock/` |
| fail-fast 缺失依赖配置 | 形如 `Coco<X>RedisMissingDependencyAutoConfiguration` 的自动配置：模块已启用、`store-type` 指向 Redis（cache 为 `two-level`）、但 classpath 缺 `StringRedisTemplate` 时，**有意**让应用启动失败并给出明确错误；仅 lock、cache、captcha 三个模块有 | `coco-features/coco-lock/src/main/java/io/github/coco/feature/lock/CocoLockRedisMissingDependencyAutoConfiguration.java` 等 |
| 自动配置注册文件 | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（每行一个自动配置 FQCN）；早期扩展点（`SpringApplicationRunListener`、`EnvironmentPostProcessor`、`AutoConfigurationImportFilter`）另放在 `META-INF/spring.factories` | `coco-spring/coco-spring-boot-autoconfigure/src/main/resources/META-INF/` |
| Coco 异常与错误码 | `CocoException` 与 `CocoErrorCode` 是异常契约；各模块有自己的 `Coco<X>ErrorCode`；`CocoWebExceptionHandler` 把异常转成统一响应 | `coco-foundation/coco-exception/`；`coco-features/coco-web/.../exception/` |
| `CocoLogHandle` | `coco-logging` 的日志句柄抽象，配合 `CocoLogManager`、`CocoLogSink` 输出；`coco.logging.async.enabled` 为 true 时包一层异步 sink | `coco-foundation/coco-logging/src/main/java/io/github/coco/logging/core/CocoLogHandle.java` |
| Audit recorder / 审计事件管线 | `coco-audit` 的审计事件发布与记录管线；默认以结构化日志输出，`CocoAuditRecorder` 是函数式扩展点 | `coco-features/coco-audit/src/main/java/io/github/coco/feature/audit/core/CocoAuditRecorder.java` |

## 五、测试与构建工具

| 术语 | 一句话定义 | 源文件 / 出处 |
| --- | --- | --- |
| Surefire | 运行 `*Test` 单元测试的 Maven 插件（`3.6.0`，`useModulePath=false`） | 根 `pom.xml` |
| Failsafe | 运行 `*IT` 集成测试的 Maven 插件（`3.6.0`），在 `integration-test` / `verify` 阶段执行，`failIfNoTests=false`；全仓库仅 2 个 IT | 根 `pom.xml`；[Guide-Testing](Guide-Testing) |
| archive smoke（归档冒烟） | `coco-feature-archive-smoke` 模块 + `FeatureArchiveSmokeIT`：构建一个真实的 Boot 归档，校验 Feature 清单与被裁剪的库 | `coco-support/coco-feature-archive-smoke/` |
| flatten POM（`.flattened-pom.xml`） | `flatten-maven-plugin`（`resolveCiFriendliesOnly`）在 `process-resources` 阶段为各模块生成的展开 POM，用来解析 `${revision}`；已在 `.gitignore`，不要提交 | 根 `pom.xml`、`.gitignore` |
| `CocoTestSupport` | `coco-test` 里唯一的类，提供读取配置元数据与 JSON 测试资源的静态工具 | `coco-support/coco-test/src/main/java/io/github/coco/test/CocoTestSupport.java` |
| SpotBugs / Checkstyle | 静态分析，**不绑定** Maven 生命周期；CI 里单独调用 `spotbugs:check`（目前只出报告）与 `checkstyle:check`（阻塞） | `.github/workflows/reusable-static-analysis.yml` |
| `hs_err_pid*.log` / `replay_pid*.log` | JVM 在崩溃时写到当前工作目录的诊断文件（后者与 JIT 编译重放有关）；被 `*.log` 忽略，不要提交；处理办法见 [FAQ-Troubleshooting](FAQ-Troubleshooting) | `.gitignore` |

## 六、仓库治理与流程

| 术语 | 一句话定义 | 源文件 / 出处 |
| --- | --- | --- |
| `dev` / `main` | `dev` 是贡献者 PR 的集成分支，`main` 是受保护的发布分支，只有所有者能把 `dev` 晋升进去 | `CONTRIBUTING.md`、`GOVERNANCE.md` |
| `dev-<login>` | 外部贡献者的分支命名，后缀必须是作者自己的 GitHub 登录名 | `CONTRIBUTING.md` |
| `codex/*` agent 分支 | 维护者 / Agent 的改动分支；默认前缀 `codex/`，允许的前缀来自仓库变量 `COCO_AGENT_BRANCH_PREFIXES`（例如 `codex/,claude/`）。前缀只是标签而不是身份 | `AGENTS.md`、`CONTRIBUTING.md` |
| Open Agent Pull Request | 受保护的工作流：由 Coco App 为 agent 分支开 PR，使人类维护者能提供必需的审批 | `.github/workflows/agent-open-pr.yml` |
| CI gate | 汇总跨平台 `Test`、`Static analysis`、`CodeQL` 的稳定状态上下文 | `.github/workflows/ci.yml` |
| Agent jury gate | 多 Agent 评审（5 位独立专家、2 位独立验证者、1 位主席）的稳定状态上下文；P0/P1 仅当两位验证者一致才阻塞 | `.github/workflows/agent-review.yml`；`AGENTS.md` |
| Agent issue gate | 带 `coco-agent-review` 标记的未关闭绑定 Issue 会阻塞它 | `.github/workflows/agent-issue-gate.yml` |
| Contributor gate | PR 到 `dev` 的准入检查：分支命名、后缀是否为作者登录名、作者权限 | `.github/workflows/contributor-gate.yml` |
| Promotion gate | PR 到 `main` 的晋升检查：仅所有者、仅来自 `dev`、`dev` 不落后 `main`、无未关闭绑定评审发现 | `.github/workflows/promotion-gate.yml` |
| CODEOWNER | `.github/CODEOWNERS` 里的代码所有者，目前默认是 `@patton174`；合并要求一次**当前 head** 的 CODEOWNER 审批 | `.github/CODEOWNERS` |
| Coco App / Release App | 专用 GitHub App：前者开 agent PR、发布评审评论与绑定 Issue、执行符合条件的 merge commit，不提供人工审批；后者只负责创建 `v*` 发布标签 | `GOVERNANCE.md`、`AGENTS.md` |
| `coco-spring` / `coco-agent` 环境 | GitHub Environments，只接受 `main`，禁用管理员绕过；`coco-spring` 还要求维护者批准后才提供发布密钥 | `GOVERNANCE.md`、`release.md` |
| Central Portal / `PUBLISHED` | Maven Central 的发布入口及其最终状态；Release 工作流等到 `PUBLISHED` 才创建标签 | `release.md`、`.github/workflows/release.yml` |
| README fragments | `README.md` / `README_CN.md` 由 `.github/readme/fragments/` 下的片段经 `render.mjs` 确定性生成，**不要直接改** README | `AGENTS.md`、`.github/readme/` |

## 七、生态与工具

| 术语 | 一句话定义 | 源文件 / 出处 |
| --- | --- | --- |
| `coco-framework` | 本仓库：独立的 Web 服务器基础框架，**不得**依赖 `coco-admin` 或 `coco-generate` | `AGENTS.md` Ecosystem Boundaries |
| `coco-admin` | 基于框架构建的 ERP 产品（独立仓库）；ERP 领域、认证选择、组织权限模型、工作流、报表和事务属于它；`framework-acceptance` 负责框架的业务 / HTTP 验收 | `AGENTS.md`、`release.md` |
| `coco-generate` | 开发期源码生成器与模板平台（独立仓库）；生成的 CRUD 源码交给业务项目持有，业务应用运行期不依赖它 | `AGENTS.md` |
| CodeGraph | 仓库本地的代码图谱工具；存在 `.codegraph/` 时先用它定位代码，改完源码执行 `codegraph sync .`；`.codegraph/` 已被 `.gitignore` 忽略 | `AGENTS.md`、`CONTRIBUTING.md` |
| `coco-agent-skills` | Node 包 `@patton174/coco-agent-skills`：MCP 文档检索服务器，索引来自官网文档 | `coco-support/coco-tools/coco-agent-skills/` |
| 官网 / `website` | Docusaurus 站点（`https://cocoframework.dev/`），面向业务使用者的文档，源码在 `website/` | `website/package.json`、`.github/workflows/docs.yml` |
| `coco-document` | 仓库级架构、发布、审计、计划与规格文档目录（无 pom），含 `architecture/module-layout.md`、`release.md` 与 `superpowers/{audits,plans,specs}` | `coco-support/coco-document/` |

## 相关页面

- [Coco Framework 开发者 Wiki](Home)
- [项目总览](Project-Overview)
- [架构总览](Architecture)
- [Feature 机制：构建期与运行期](Feature-System)
- [约定与规范](Conventions)
- [模块参考总览](Module-Overview)
- [FAQ 与故障排查](FAQ-Troubleshooting)
- [速查：开发任务到文件/目录](Where-To-Find-Things)
- 仓库文档：[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)、[GOVERNANCE.md](https://github.com/patton174/coco-framework/blob/main/GOVERNANCE.md)、[模块布局 module-layout.md](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/architecture/module-layout.md)
