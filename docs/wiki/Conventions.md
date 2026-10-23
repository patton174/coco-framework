# 约定与规范

> 本页回答：给 Coco Framework 写代码、加模块、改构建或提 PR 时，必须遵守哪些约定；哪些是 `AGENTS.md` 明文规定的，哪些是从现有代码里总结出来的惯例，哪些在文档里没有定论。
> 约定分两类标注：**规定**（来自 `AGENTS.md` / `CONTRIBUTING.md` / `GOVERNANCE.md`，有明文）、**惯例**（来自现有代码的观察，给出示例路径）。文档之间或代码之间不一致的地方，会以“文档与实现存在出入：…”或“设计意图未见文档说明，…”说明，并给出应参照的来源。开发规则以 [`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 为准；分支、PR 与发布流程以工作流、仓库实际设置和 [`CONTRIBUTING.md`](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md)、[`GOVERNANCE.md`](https://github.com/patton174/coco-framework/blob/main/GOVERNANCE.md) 为准（见 §5）。本 Wiki 只是导读。

## 1. 开发规则（规定，来自 `AGENTS.md`）

| 规则 | 落地时的含义 |
| --- | --- |
| 改动范围只限于所请求的功能、模块或文档 | 不顺手重构、不做格式化整理、不提交构建产物（`target/`、`.flattened-pom.xml` 等） |
| 匹配现有 Maven 模块边界与 Java 包名 | 新代码放进语义对应的模块，沿用该模块的包名风格（见 §2.2） |
| 公共 API 保持小而稳定；只有存在真实替换点时才加 SPI | 先问“业务方真的需要替换这里吗”；需要时用接口 + `@ConditionalOnMissingBean` 默认实现（见 §3.3） |
| 保持对业务友好的简单性：一个 starter、声明式配置、清晰的覆盖点 | 不要要求业务方引入第二个 starter |
| 业务 CRUD 用生成的可读源码，不用运行时动态 Controller | 框架里不做“自动把实体暴露成 API”之类的魔法 |
| 注解表达清晰意图，不要做成不透明的开关 | 新注解必须能说清楚“它声明了什么业务/框架意图” |
| 默认值有用且安全；主要集成点可通过属性、Bean 或 SPI 替换 | 新增能力同时提供属性与可替换 Bean |
| 避免给 `coco-spring-boot-starter` 增加 Feature 行为 | starter 只组合依赖（见 [架构总览](Architecture)） |
| 通用模块不得依赖具体 Feature 模块 | foundation 与 autoconfigure 不依赖 `coco-features/*` |
| 公共类使用标准 JavaDoc HTML 标签；现有文档与 JavaDoc 使用中文描述，保持这一风格 | 见 §2.5 |
| 用户可见文本放进模块消息包，不要硬编码 | 见 §2.4 |
| 不要引入无关重构、格式噪音或生成的构建产物 | 提交前 `git status -sb` 检查 |
| GitHub Actions 文件用小写 kebab-case 命名；仅被复用的工作流以 `reusable-` 开头；Python 自动化用 snake_case；Node.js 自动化用小写 kebab-case；工作流文件重命名必须保留稳定的 required check 上下文 | 例如 `ci.yml`、`reusable-tests.yml`、`.github/scripts/test_agent_review.py` |
| 框架不依赖 `coco-admin` / `coco-generate`；框架内不放业务样例 | 业务/HTTP 验收由 `coco-admin/framework-acceptance` 承担 |
| 若存在 `.codegraph/`，先用 CodeGraph 定位代码；改源码后运行 `codegraph sync .` | 见 [开发环境与常见陷阱](Dev-Environment) |

## 2. 命名与结构（惯例，示例路径均已核对）

### 2.1 groupId、artifactId 与包根

| 项 | 取值 | 说明 |
| --- | --- | --- |
| Maven `groupId` | `io.github.patton174` | 所有制品相同 |
| Java 包根 | `io.github.coco` | **与 groupId 不同**，勿混用 |
| artifactId | `coco-<名称>` | 模块目录名与 artifactId 一致，例如 `coco-features/coco-lock` → `coco-lock` |
| Feature id | kebab-case，如 `data-permission` | 定义在 `CocoFeature` 枚举，稳定不变 |
| 配置前缀 | `coco.<feature-id>.*`，属性键 kebab-case | 例如 `coco.rate-limit.enabled` |

`module-layout.md` 规定：Java 包名、公开 FQCN、配置前缀、feature id、插件 goal **不因目录或 artifactId 重命名而改变**（见 [架构总览](Architecture) 的兼容策略）。

### 2.2 包命名现状（含不一致）

| 位置 | 包 | 示例 |
| --- | --- | --- |
| foundation | `io.github.coco.api[.feature]`、`io.github.coco.context[.trace]`、`io.github.coco.exception[.type]`、`io.github.coco.feature.model`、`io.github.coco.i18n`、`io.github.coco.logging.{access,core,lifecycle}` | `coco-foundation/coco-api/src/main/java/io/github/coco/api/feature/CocoFeature.java` |
| autoconfigure（迁移期保留旧 FQCN） | `io.github.coco.common.*`、`io.github.coco.config`、`io.github.coco.feature.registry`、`io.github.coco.feature.runtime.*`、`io.github.coco.spring.boot.*` | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/config/CocoConfigAutoConfiguration.java` |
| Feature（风格 A） | `io.github.coco.feature.<名称>`：web、mybatis-plus（`mybatisplus`）、audit、security、tenant、data-permission（`datapermission`）、openapi、rate-limit（`ratelimit`）、idempotency、lock | `coco-features/coco-lock/src/main/java/io/github/coco/feature/lock/CocoLockAutoConfiguration.java` |
| Feature（风格 B） | `io.github.coco.<名称>`：scheduling、storage、messaging、cache、notification、captcha（共 6 个） | `coco-features/coco-captcha/src/main/java/io/github/coco/captcha/CocoCaptchaAutoConfiguration.java` |
| Maven 插件 | `io.github.coco.maven` | `coco-build/coco-maven-plugin/src/main/java/io/github/coco/maven/CocoFeaturesMojo.java` |
| 兼容门面测试探针 | `io.github.coco.compatibility.<名称>` | `coco-build/coco-compatibility/coco-feature-web/src/test/java/io/github/coco/compatibility/web/CocoFeatureWebCompatibilityCompileProbe.java` |

要点：

- 子包名去掉连字符（`mybatis-plus` → `mybatisplus`，`data-permission` → `datapermission`，`rate-limit` → `ratelimit`）。
- 两种 Feature 包风格并存（`io.github.coco.feature.<名称>` 与 `io.github.coco.<名称>`）。新模块推荐哪一种：设计意图未见文档说明，行为以各模块 `src/main/java` 下的实际包路径为准。实操建议是“与最近的同类模块保持一致”，并在 PR 描述里说明选择。
- `StandardCocoFeatures` 里登记的自动配置 FQCN 必须与实际包名一致（`StandardCocoFeaturesTest` 会逐项断言）。

### 2.3 类名与角色

| 角色 | 命名 | 示例 |
| --- | --- | --- |
| 框架内公开类型 | 以 `Coco` 开头 | `CocoLockManager`、`CocoWebExceptionHandler` |
| 自动配置 | `Coco<X>AutoConfiguration` | `CocoSecurityAutoConfiguration` |
| 属性类 | `Coco<X>Properties`，`@ConfigurationProperties` | `coco-features/coco-lock/.../CocoLockProperties.java`（`@ConfigurationProperties("coco.lock")`） |
| 默认实现 | `Default` + 接口名；也有 `Holder*`、`Properties*` 等按实现方式命名的变体 | `DefaultCocoLockManager`、`DefaultCocoResponseBodyFactory`、`HolderCocoSecurityContextResolver`、`PropertiesCocoSignatureSecretResolver` |
| 内存 / Redis / JDBC 存储实现 | `InMemory<接口>`、`Redis<接口>`、`Jdbc<接口>` | `InMemoryCocoLockStore`、`RedisCocoLockStore`、`JdbcCocoReplayStore` |
| 存储抽象与类型枚举 | `Coco<X>Store` + `Coco<X>StoreType`，属性 `store-type` | rate-limit、idempotency、lock、cache、captcha、web replay |
| 错误码 | `Coco<X>ErrorCode` 枚举，持有 `(int code, String messageCode)` | `CocoLockErrorCode`（实现 `CocoBusinessCode`，消息键形如 `coco.lock.invalid-key`） |
| Feature 占位类 | `Coco<X>Feature`，final + 私有构造 | 仅 web、mybatis-plus、audit、security、tenant、data-permission、openapi 这 7 个模块有（例：`CocoSecurityFeature`） |
| 替换点接口 | 带 `@FunctionalInterface`（如适用） | `CocoAuditRecorder`、`CocoSecurityContextResolver`、`CocoMybatisPlusInterceptorCustomizer` |
| 兼容探针测试 | `<门面名>CompatibilityCompileProbe` | `CocoConfigCompatibilityCompileProbe` |

错误码编号（观察）：`coco-rate-limit`、`coco-idempotency`、`coco-lock` 的错误码都是 5 位数，前 3 位对应 HTTP 状态语义（如 `409`、`503`），后 2 位是模块内编号（rate-limit `00`、idempotency `10`、lock `60/61`）。各模块错误码枚举实现的接口并不统一：`CocoRateLimitErrorCode`、`CocoLockErrorCode` 实现 `CocoBusinessCode`；`CocoDataPermissionErrorCode`、`CocoMybatisPlusErrorCode`、`CocoSecurityErrorCode`、`CocoStorageErrorCode`、`CocoTenantErrorCode` 实现 `CocoErrorCode`；`CocoIdempotencyErrorCode` 没有实现这两者。是否所有模块都遵循同一编号规则，以及这种差异是否有意：设计意图未见文档说明，行为以各模块的 `*ErrorCode` 源码为准。

### 2.4 消息包（i18n）

- 每个模块的自动配置通过一个 `CocoMessageBundleRegistrar` Bean 注册自己的资源包，Bean 名形如 `cocoSecurityMessageBundleRegistrar`，使用 `@ConditionalOnMissingBean(name = ...)`，实现为 lambda：`registry -> registry.add("coco-feature-security-messages")`（见 `CocoSecurityAutoConfiguration`、`CocoLockAutoConfiguration`）。
- `CocoCommonAutoConfiguration` 先加载业务配置的 `coco.common.i18n.basename`，再收集各模块 registrar，最后追加 `coco-messages` 构造 `cocoMessageSource`。
- 资源包命名：较早的 7 个 Feature 为 `coco-feature-<x>-messages`（web、mybatis-plus、audit、security、tenant、data-permission、openapi），其余多为 `coco-<x>-messages`。
- 语言后缀并不统一：web、security、rate-limit 等使用 `_zh_CN` + `_en_US`；idempotency、lock、scheduling、storage 只有 `_zh_CN`；captcha、messaging、notification 只有 `_en`；`coco-cache` 目录下未发现消息资源文件。建议新模块提供“默认包 + `_zh_CN` + `_en_US`”。这种差异是否有意：设计意图未见文档说明，行为以各模块 `src/main/resources` 下的资源文件为准。
- 面向最终用户或 HTTP 响应的文本放进消息包。开发者向的日志和异常信息在代码中多为英文字符串（例如 `CocoFeaturesMojo` 的日志、`CocoSecurityAutoConfiguration` 的 WARN）。这一语言取舍：设计意图未见文档说明，行为以现有源码为准；`AGENTS.md` 明文规定的只有“用户可见文本放进消息包”。

### 2.5 JavaDoc

- 惯例模板：`<p>` 描述 + “项目信息”列表（作者 `patton174`、仓库地址、`模块：{@code <artifactId>}`）+ `@author`、`@since`，见 `CocoFeature.java`、`StandardCocoFeatures.java`、`CocoSecurityFeature.java`。
- `@since` 写该类型预期首次发布的版本：基础类多为 `1.0.0`，`CocoSpringCompositionClasspathTest` 为 `2.0.0`，较新的 `coco-captcha`、`coco-notification` 为 `2.1.0`，兼容探针（如 `CocoFeatureWebCompatibilityCompileProbe`）为 `3.0.0`。仓库的 `revision` 默认值是 `1.0.0-SNAPSHOT`。`@since` 取值：设计意图未见文档说明，请参照同模块最近的类型，并在 PR 描述中说明目标发布版本。
- 一些较新的类使用单行 JavaDoc（例如 `CocoLockProperties`、`CocoLockErrorCode`）。`AGENTS.md` 要求公共类使用标准 JavaDoc HTML 标签，但没有说明是否接受单行 JavaDoc：设计意图未见文档说明，行为以现有源码为准；新代码优先用完整模板。
- 根 `pom.xml` 配置 `maven-javadoc-plugin` 时 `doclint=none`，只有 `release` profile 才会生成 javadoc jar。

## 3. 代码层面的模式（惯例）

### 3.1 自动配置

完整步骤见 [指南：新增或调整自动配置](Guide-Add-Auto-Configuration)。核心约定：

- 使用 `@AutoConfiguration`，并通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册；
- 默认 Bean 一律 `@ConditionalOnMissingBean`，业务方可以自定义 Bean 覆盖；
- 需要 Servlet 时叠加 `@ConditionalOnClass(Filter.class)` + `@ConditionalOnWebApplication(type = SERVLET)`；
- 顺序用 `@AutoConfiguration(before / after / afterName)` 或 `@AutoConfigureBefore` 表达。

### 3.2 属性

- `@ConfigurationProperties("coco.<id>")` 的属性类 + `@EnableConfigurationProperties`；
- 重依赖和需显式启用的能力默认关闭（`coco.lock.enabled` 等为 `false`）；`coco.scheduling.enabled`、`coco.messaging.enabled` 缺省为开启；
- 部分模块提供 `META-INF/additional-spring-configuration-metadata.json`（audit、data-permission、mybatis-plus、openapi、tenant、web），其余依赖 `spring-boot-configuration-processor`（`optional`）生成元数据；
- 测试里可用 `coco-test` 的 `CocoTestSupport.configurationMetadata(Class)` 读取生成的 `META-INF/spring-configuration-metadata.json`。

### 3.3 SPI 与替换点

- 形式：接口（可 `@FunctionalInterface`）+ `@ConditionalOnMissingBean` 的默认实现，例如 `CocoSecurityContextResolver` 默认是 `HolderCocoSecurityContextResolver`；
- 存储可替换：`Coco<X>Store` 接口 + `StoreType` 枚举 + 属性 `store-type`，默认存储为 `IN_MEMORY`（cache 的 `LOCAL` / `two-level`）；
- Redis 存储采用“三件套”：`Coco<X>RedisAutoConfiguration`（条件：`enabled=true`、`store-type=redis`、classpath 有 `StringRedisTemplate`，`before` 主配置）、`Coco<X>RedisMissingDependencyAutoConfiguration`（Redis 类缺失时 **fail-fast** 抛 `BeanCreationException`）、主配置。参考 `coco-features/coco-lock`、`coco-cache`、`coco-captcha`；
- 扩展 MyBatis-Plus 拦截器链统一通过 `CocoMybatisPlusInterceptorCustomizer`（tenant、data-permission 都是这样接入）。

### 3.4 依赖

- 重量级可选依赖（Redis、MyBatis-Plus 等）声明为 `<optional>true</optional>`，运行期用 `@ConditionalOnClass` 保护；
- `spring-boot-configuration-processor` 声明为 `optional`；
- 模块间依赖写 `<groupId>${project.groupId}</groupId>` 且通常不写版本（由根 `pom.xml` 的 `dependencyManagement` 管理）；根 pom 没有登记的新模块，在 starter 中显式写 `<version>${project.version}</version>`（见 [架构总览](Architecture) §5.5）；
- 每个 Maven 模块的 `<parent>` 是 `coco-framework`，`relativePath` 指向根 pom（`coco-features/coco-captcha/pom.xml` 为 `../../pom.xml`）。

### 3.5 构建门禁

根 `pom.xml` 在 `validate` 阶段运行 Enforcer（`enforce-build-gates`）：

- `banDuplicatePomDependencyVersions`；
- `dependencyConvergence`（排除 provided / test）；
- `bannedDependencies`：禁止 `commons-logging:commons-logging`、`log4j:log4j`、`org.slf4j:log4j-over-slf4j`、`spring-boot-starter-jetty`、`spring-boot-starter-undertow`（不检查传递依赖）。

`coco-parent` 设置了 `coco.enforcer.skip=true`，所以业务应用继承它时不会受这些门禁约束；这些门禁只约束框架仓库自身。静态分析（SpotBugs、Checkstyle）**不绑定生命周期**，本地 `mvn install` 不会运行，CI 直接调用目标：Checkstyle 阻塞合并，SpotBugs 目前为软失败（`continue-on-error`，仅出报告）。`maven-checkstyle-plugin` 的 `configLocation` 是 `google_checks.xml`（插件内置的 Google 规则）；仓库中没有被 Git 跟踪的 Checkstyle 或 SpotBugs 自定义配置文件。

### 3.6 安全默认值

- `coco-security` 的请求头解析器信任上游 HTTP 头，默认关闭；开启时会打印 WARN，不得直接暴露公网，需可信网关或自定义 `CocoWebSecurityContextResolver`；
- `coco-web` 的签名与加密密钥映射（`coco.web.signature.secrets`、`coco.web.encryption.keys`）默认为空；文档、示例和测试中只写键名与结构，**不要写入真实密钥值**；
- 绝不提交凭据、生产数据、私钥、构建产物、本地 IDE 状态（`CONTRIBUTING.md`）；`.gitignore` 忽略 `*.log`；
- 发布涉及的机密只以键名出现：`CENTRAL_USERNAME`、`CENTRAL_PASSWORD`、`GPG_PRIVATE_KEY`、`GPG_PASSPHRASE`（GitHub Environment `coco-spring` 的 Secrets），以及 Maven 侧的环境变量名 `MAVEN_GPG_KEY`、`MAVEN_GPG_PASSPHRASE`。

## 4. 测试约定（概要）

详见 [指南：测试约定](Guide-Testing)。与新增代码直接相关的几条：

- 框架：JUnit Jupiter + AssertJ；Spring 相关测试使用 `spring-boot-test` / `spring-boot-starter-test`，日志用 `logback-classic`。
- 命名：单元测试 `*Test`，集成测试 `*IT`（Failsafe 在 `integration-test` 阶段执行）。模块内惯例：`Coco<X>AutoConfigurationTest`、`Default<X>Test`、`InMemory<X>StoreTest`、`Redis<X>StoreTest`（例：`coco-features/coco-lock/src/test/java/io/github/coco/feature/lock/`）。
- 自动配置测试使用 `ApplicationContextRunner` / `WebApplicationContextRunner` + `AutoConfigurations.of(...)`，用 `FilteredClassLoader` 模拟 classpath 缺少某个类（参见 `CocoCaptchaAutoConfigurationTest`、`CocoLockAutoConfigurationTest`、`CocoIdempotencyAutoConfigurationTest`、`CocoRateLimitAutoConfigurationTest`）。
- 新增 Feature 时必须同步更新 `StandardCocoFeaturesTest`（`coco-foundation/coco-feature-model`）；是否进入 starter 要有契约测试（`CocoRateLimitStarterDependencyContractTest`、`CocoMybatisPlusStarterDependencyContractTest`）。
- 仓库只有 2 个 IT：`CocoStarterSmokeIT`、`FeatureArchiveSmokeIT`；业务/HTTP 验收不在本仓库。

## 5. 版本、分支与发布（概要）

详见 [指南：分支、PR、CI 与发布流程](Guide-Release-And-Workflow)；这里只列影响日常开发的约定。

| 主题 | 约定 | 来源 |
| --- | --- | --- |
| 版本号 | 根 `pom.xml` 的 `revision` 默认 `1.0.0-SNAPSHOT`；正式版本由 `-Drevision=X.Y.Z` 注入；`flatten-maven-plugin` 的 `resolveCiFriendliesOnly` 保证发布出去的 pom 里 `${revision}` 已被解析 | 根 `pom.xml` |
| 发布版本格式 | 必须是 SemVer（可带 `v` 前缀输入），不得含 `SNAPSHOT`；留空则按已有标签生成下一个 patch 版本 | `.github/workflows/release.yml` |
| 发布触发 | 只能从受保护 `main` 的最新提交手动触发 `Release` 工作流；作业顺序为 guard → test → central-capacity → publish → tag | `release.yml`、[`release.md`](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/release.md) |
| 发布标签 | `v*` 标签只在该版本于 Maven Central 公开发布后由专用 Release App 创建（默认 `autoPublish=false` 时由维护者在 Portal 发布，工作流轮询确认），创建后不可更新或删除；**不要手工创建或推送发布标签** | `AGENTS.md`、`GOVERNANCE.md` |
| 本地验证 | `mvn -B clean verify`（`CONTRIBUTING.md`）；CI 实际跑 `mvn -B -ntp install`（ubuntu / windows / macos 三平台，Temurin 21，编译目标 17） | `CONTRIBUTING.md`、`reusable-tests.yml` |
| release profile 冒烟（无 GPG） | `mvn -B -Prelease -Drevision=1.0.0 -Dgpg.skip=true -DskipTests verify` | `AGENTS.md`、`release.md` |
| 外部贡献者分支 | 从最新 `dev` 创建 `dev-<你的 GitHub 用户名>`，PR 目标是 `dev`；`main` 仅仓库所有者从 `dev` 晋升，其他分支直接向 `main` 提 PR 会被 `Promotion gate` 拒绝；不接受 fork PR | `CONTRIBUTING.md`、`GOVERNANCE.md`、`.github/workflows/contributor-gate.yml`、`.github/workflows/promotion-gate.yml` |
| 维护者/Agent 分支 | 推到同仓库 agent 分支（默认 `codex/*`，允许前缀由仓库变量 `COCO_AGENT_BRANCH_PREFIXES` 决定），通过受保护的 `Open Agent Pull Request` 工作流由 Coco App 开 PR，PR 的目标分支是 `dev`，人工提供审批 | `AGENTS.md`、`CONTRIBUTING.md`、`.github/workflows/agent-open-pr.yml` |
| 合并门禁 | 到 `dev`：`CI gate`、`Agent issue gate`、`Contributor gate`、`Agent jury gate`，一次当前 CODEOWNER 审批并解决所有评审会话；到 `main`：`CI gate` 与 `Promotion gate`；仅 merge commit；GitHub 原生 auto-merge 禁用 | 仓库分支保护设置、`.github/workflows/`、`GOVERNANCE.md` |

文档与实现存在出入：`AGENTS.md` “Git Workflow” 写的是“聚焦分支 → PR → `main`”，并把 `CI gate`、`Agent jury gate`、`Agent issue gate` 列为 `main` 的必需检查；而工作流与仓库实际设置是“`dev-<login>` 或 agent 分支 → PR 到 `dev` → 所有者把 `dev` 晋升到 `main`”，`main` 的必需检查是 `CI gate` 与 `Promotion gate`。`CONTRIBUTING.md` 与 `GOVERNANCE.md` 的描述与后者一致。分支与合并门禁以工作流、仓库实际设置和 `CONTRIBUTING.md` / `GOVERNANCE.md` 为准。

## 6. README 与文档

- `README.md` 与 `README_CN.md` 是确定性生成的文档，**不要直接编辑**。
- 修改 `.github/readme/fragments/` 下的中英文片段（中文版是结构与视觉参考，两份清单必须保持对齐），然后运行 `node .github/readme/scripts/render.mjs --write`，提交前运行 `node .github/readme/scripts/render.mjs --check`。
- Stars、forks、贡献者数据和时间戳由脚本维护，不得由模型生成。
- 文档放置：面向业务使用者的文档在 `website/docs/`（官网 `https://cocoframework.dev/`）；仓库架构、发布、审计、计划与规格文档在 `coco-support/coco-document/`；本 Wiki 面向框架开发者与 Agent，只做导读并链接上述来源。
- 引用 `module-layout.md`、`release.md` 与设计规格时用链接 + 转述，不复制正文。

## 7. 提交前自检清单

- [ ] 改动是否只覆盖所请求的模块/功能/文档，没有无关重构或格式噪音？
- [ ] 是否匹配现有 Maven 模块边界与包名？是否违反“通用模块不依赖具体 Feature”“starter 不承载行为”？
- [ ] 新的用户可见文本是否放进了消息包？新增 `Coco*Properties` 是否有合理默认值且可替换？
- [ ] 是否运行了 `mvn -B clean verify`（或至少 `mvn -pl :module-artifact-id -am test`）？涉及发布行为时是否跑了 release profile 冒烟？
- [ ] 改了源码后是否执行了 `codegraph sync .`？
- [ ] 改了 README 片段后是否执行了 `render.mjs --write` 与 `--check`？
- [ ] 是否没有提交凭据、JVM 崩溃日志（`hs_err_pid*.log`、`replay_pid*.log`）、`target/` 等产物？`git status -sb` 是否干净？
- [ ] PR 描述是否写明了变更意图、影响模块、兼容性影响和实际执行的验证？

## 相关页面

- [架构总览](Architecture)
- [Feature 机制：构建期与运行期](Feature-System)
- [指南：新增一个 Feature 模块](Guide-Add-Feature-Module)
- [指南：新增或调整自动配置](Guide-Add-Auto-Configuration)
- [指南：测试约定](Guide-Testing)
- [指南：分支、PR、CI 与发布流程](Guide-Release-And-Workflow)
- [开发环境与常见陷阱](Dev-Environment)
- [术语表](Glossary)
- 仓库文档：[`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)、[`CONTRIBUTING.md`](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md)、[`GOVERNANCE.md`](https://github.com/patton174/coco-framework/blob/main/GOVERNANCE.md)、[`SECURITY.md`](https://github.com/patton174/coco-framework/blob/main/SECURITY.md)
