# 速查：开发任务到文件/目录

本页回答：我想做某件开发任务（加 Feature、改属性、跑单模块测试、发版……），应该去哪个文件或目录。

> 路径都相对仓库根目录 `coco-framework/`，写入前已逐一核对存在。内容对应仓库 `main` 分支。文档与实现不一致的条目以“文档与实现存在出入”开头说明，并给出权威来源。AI Agent 的速读顺序在文末。

## 一、最常见的 5 件事

| 我想…… | 步骤与文件 | 详细页面 |
| --- | --- | --- |
| 新增一个 Feature 模块 | ① 在 `coco-features/<新模块>/` 建模块，参照 `coco-features/coco-captcha/pom.xml`；② 在 `coco-features/pom.xml` 的 `<modules>` 追加；③ 在 `coco-build/coco-dependencies/pom.xml` 登记；④ 在 `coco-foundation/coco-api/src/main/java/io/github/coco/api/feature/CocoFeature.java` 加枚举常量；⑤ 在 `coco-foundation/coco-feature-model/src/main/java/io/github/coco/feature/model/StandardCocoFeatures.java` 登记元数据，并更新 `StandardCocoFeaturesTest`；⑥ 决定 starter 是否直接依赖：`coco-spring/coco-spring-boot-starter/pom.xml`；⑦ 在 `.github/release/release-artifact-expectations.json` 登记发布条目（文档与实现存在出入：该清单目前登记 34 项，而 Reactor 有 40 个项目，`release_file_count_preflight.py` 的预检会按路径比对两者，以 `.github/scripts/release_file_count_preflight.py` 的校验结果为准）；⑧ 在 `website/docs/features/<name>.md` 与英文 `website/i18n/en/docusaurus-plugin-content-docs/current/features/<name>.md` 补用户文档。这份清单对照 `coco-captcha` 在仓库中出现的位置核对过 | [新增一个 Feature 模块](Guide-Add-Feature-Module) |
| 新增或调整自动配置 | 类放在对应模块的 `src/main/java`；在该模块 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 里每行登记一个 FQCN（例：`coco-features/coco-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）；只有早期扩展点才放 `coco-spring/coco-spring-boot-autoconfigure/src/main/resources/META-INF/spring.factories`；条件模板可参考 `coco-features/coco-idempotency/src/main/java/io/github/coco/feature/idempotency/CocoIdempotencyAutoConfiguration.java` | [新增或调整自动配置](Guide-Add-Auto-Configuration) |
| 修改一个配置属性 | 找到该前缀对应的 `Coco<X>Properties.java`（见下面第四节），改字段与默认值；有 `META-INF/additional-spring-configuration-metadata.json` 的模块（audit、data-permission、mybatis-plus、openapi、tenant、web）也要同步元数据；同步官网文档 `website/docs/features/` 里对应页面 | [约定与规范](Conventions) |
| 只跑某个模块的测试 | `mvn -pl :module-artifact-id -am test`，例如 `mvn -pl :coco-web -am test`（命令来自 `AGENTS.md`）；测试源码在同模块的 `src/test/java`；跑集成测试需要走到 `verify`，见测试指南 | [测试约定](Guide-Testing) |
| 发一个正式版本 | 在 GitHub Actions 里从**最新受保护的 `main`** 手动触发 `.github/workflows/release.yml`（`Release`），版本号留空则自动取下一个 patch；本地预演：`mvn -B -Prelease -Drevision=1.0.0 -Dgpg.skip=true -DskipTests verify`；不要手工创建或推送 `v*` 标签；流程说明见 `coco-support/coco-document/release.md` | [分支、PR、CI 与发布流程](Guide-Release-And-Workflow) |

## 二、Feature 机制

| 我想…… | 去哪里 | 备注 |
| --- | --- | --- |
| 看有哪些 Feature 及其稳定 id | `coco-foundation/coco-api/src/main/java/io/github/coco/api/feature/CocoFeature.java` | 枚举 16 项，`id()` 返回 kebab-case 标识 |
| 看某个 Feature 的 artifactId、自动配置 FQCN、依赖、裁剪集合 | `coco-foundation/coco-feature-model/src/main/java/io/github/coco/feature/model/StandardCocoFeatures.java` | 新增或改名后，同步 `coco-foundation/coco-feature-model/src/test/java/io/github/coco/feature/model/StandardCocoFeaturesTest.java` |
| 看 Feature 计划是怎么解析出来的 | `StandardCocoFeatures.java` 中的 `resolve`；数据模型见同目录 `CocoFeatureSelection.java`、`CocoFeaturePlan.java`、`CocoFeatureDefinition.java` | 依赖传播也在 `resolve` 里 |
| 看 manifest（`META-INF/coco/features.json`）的读写和版本 | `coco-foundation/coco-feature-model/src/main/java/io/github/coco/feature/model/CocoFeatureManifest.java`、`CocoFeatureManifestLoader.java` | 当前 schema `1.1`，兼容 `1.0` |
| 看运行期 Feature 计划 Bean | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/config/CocoConfigAutoConfiguration.java` | 有 manifest 就直接还原，否则合并属性与代码声明 |
| 看 `@ConditionalOnCocoFeature` 怎么判定 | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/feature/runtime/condition/`（`ConditionalOnCocoFeature.java`、`OnCocoFeatureCondition.java`、`CocoRuntimeFeatureResolver.java`） | |
| 看自动配置被过滤的原因 | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/feature/runtime/autoconfigure/CocoFeatureAutoConfigurationImportFilter.java` | 注册在 `META-INF/spring.factories` |
| 看 `coco.features.*` 属性类 | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/config/CocoProperties.java`、`CocoFeatureProperties.java` | 编译报错见 [FAQ-Troubleshooting](FAQ-Troubleshooting) 中关于 `package` 声明与目录不一致的条目 |
| 看 `@CocoFeatures` 注解 | `coco-foundation/coco-api/src/main/java/io/github/coco/api/feature/CocoFeatures.java` | 同一配置源里禁用优先 |
| 看已废弃的 Java 配置入口 | `coco-foundation/coco-api/src/main/java/io/github/coco/api/CocoConfigurer.java` | 仅为兼容保留 |

## 三、构建期与模块布局

| 我想…… | 去哪里 | 备注 |
| --- | --- | --- |
| 改构建期装配（写 manifest、补依赖、剔除 artifact） | `coco-build/coco-maven-plugin/src/main/java/io/github/coco/maven/CocoFeaturesMojo.java` | goal `features`，默认阶段 `process-classes` |
| 改 Boot 归档裁剪 | `coco-build/coco-maven-plugin/src/main/java/io/github/coco/maven/CocoPackagePruneMojo.java` | goal `prune-package`，默认阶段 `package` |
| 改构建期读取 `application.yml` 的逻辑 | `coco-build/coco-maven-plugin/src/main/java/io/github/coco/maven/CocoBuildFeatureConfigurationLoader.java` | |
| 改构建期的 `@CocoFeatures` 扫描 | `coco-build/coco-maven-plugin/src/main/java/io/github/coco/maven/CocoAnnotatedFeatureScanner.java` | |
| 改插件的测试 | `coco-build/coco-maven-plugin/src/test/java/io/github/coco/maven/` | 5 个测试类 |
| 改业务应用推荐的父 POM | `coco-build/coco-parent/pom.xml` | 绑定 `spring-boot:repackage`、`coco:features`、`coco:prune-package` |
| 改 BOM 管理的版本或新增坐标 | `coco-build/coco-dependencies/pom.xml` | 对外的权威版本清单；新增模块必须登记 |
| 改根构建规则（Enforcer、Surefire / Failsafe、release profile、插件版本） | `pom.xml` | 根 `dependencyManagement` 缺少 8 个较新模块（`coco-rate-limit`、`coco-scheduling`、`coco-lock`、`coco-storage`、`coco-messaging`、`coco-cache`、`coco-captcha`、`coco-notification`），是否也登记没有统一做法，设计意图未见文档说明，以根 `pom.xml` 与 BOM 的实际内容为准 |
| 改 starter 的依赖组合 | `coco-spring/coco-spring-boot-starter/pom.xml` | 改后跑 starter 的契约测试 |
| 查模块依赖方向与迁移映射 | `coco-support/coco-document/architecture/module-layout.md` | 2.0 模块布局与兼容基线的权威来源 |
| 处理旧坐标兼容门面 | `coco-build/coco-compatibility/` | 无源码；每个门面只依赖规范制品 |
| 看归档裁剪的冒烟夹具 | `coco-support/coco-feature-archive-smoke/` | 父 POM 是 `coco-parent` |

## 四、配置属性（改默认值、加键时）

| 前缀 | 属性类 | 备注 |
| --- | --- | --- |
| `coco.banner` / `coco.async` / `coco.jackson` | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/spring/boot/` 下的 `banner/`、`async/`、`jackson/` | 各自有 `Coco*Properties` |
| `coco.common.i18n` | `coco-foundation/coco-i18n/src/main/java/io/github/coco/CocoCommonProperties.java` | 绑定在 autoconfigure 的 `CocoCommonAutoConfiguration` |
| `coco.logging` | `coco-foundation/coco-logging/src/main/java/io/github/coco/logging/core/CocoLoggingProperties.java` | 绑定在 `CocoCommonLoggingAutoConfiguration` |
| `coco.web` | `coco-features/coco-web/src/main/java/io/github/coco/feature/web/CocoWebProperties.java` | 子树另有 `response`、`trace`、`signature`、`encryption`、`replay`、`cors`、`headers`、`page` 各自的 Properties |
| `coco.mybatis-plus` | `coco-features/coco-mybatis-plus/src/main/java/io/github/coco/feature/mybatisplus/CocoMybatisPlusProperties.java` | 分页与 SQL Guard 属性在 `pagination/`、`sqlguard/` |
| `coco.tenant` | `coco-features/coco-tenant/src/main/java/io/github/coco/feature/tenant/CocoTenantProperties.java` | SQL 属性在 `sql/` |
| `coco.data-permission` | `coco-features/coco-data-permission/src/main/java/io/github/coco/feature/datapermission/CocoDataPermissionProperties.java` | SQL 属性在 `sql/` |
| `coco.security` | `coco-features/coco-security/src/main/java/io/github/coco/feature/security/CocoSecurityProperties.java` | Web 与 Header 属性在 `web/` |
| `coco.audit` | `coco-features/coco-audit/src/main/java/io/github/coco/feature/audit/CocoAuditProperties.java` | |
| `coco.openapi` | `coco-features/coco-openapi/src/main/java/io/github/coco/feature/openapi/CocoOpenApiProperties.java` | |
| `coco.rate-limit` | `coco-features/coco-rate-limit/src/main/java/io/github/coco/feature/ratelimit/CocoRateLimitProperties.java` | |
| `coco.idempotency` | `coco-features/coco-idempotency/src/main/java/io/github/coco/feature/idempotency/CocoIdempotencyProperties.java` | |
| `coco.lock` | `coco-features/coco-lock/src/main/java/io/github/coco/feature/lock/CocoLockProperties.java` | |
| `coco.cache` / `coco.captcha` / `coco.notification` | `coco-features/coco-cache/`、`coco-features/coco-captcha/`、`coco-features/coco-notification/` 下的 `Coco<X>Properties.java` | 包名为 `io.github.coco.<name>`，不带 `feature` |
| `coco.scheduling` / `coco.storage` / `coco.messaging` | `coco-features/coco-scheduling/`、`coco-features/coco-storage/`、`coco-features/coco-messaging/` 下的 `Coco<X>Properties.java` | 同上 |
| 配置元数据补充 | `coco-features/coco-web/src/main/resources/META-INF/additional-spring-configuration-metadata.json` 等 | 其余模块由 `spring-boot-configuration-processor` 生成 |

## 五、Web 运行时（`coco-web`）

根目录：`coco-features/coco-web/src/main/java/io/github/coco/feature/web/`。

| 我想改…… | 子目录 | 代表类 |
| --- | --- | --- |
| 统一响应、响应封装 | `response/` | `CocoApiResponse`、`CocoResponseWrapAdvice`、`CocoIgnoreResponseWrap` |
| 异常到响应的转换 | `exception/` | `CocoWebExceptionHandler` |
| Trace / TraceId | `trace/` | `CocoTraceFilter` |
| 请求体缓存 | `body/` | `CocoRequestBodyCachingFilter` |
| 请求上下文解析（IP、头、Cookie、参数） | `context/` | `DefaultCocoWebRequestContextResolver` |
| 请求签名 | `signature/` | `CocoSignatureFilter` |
| 请求加密 | `encryption/` | `CocoEncryptionFilter` |
| 防重放 | `replay/` | `CocoReplayFilter`、`CocoReplayStore` |
| CORS | `cors/` | `CocoCorsAutoConfiguration` |
| 安全响应头 | `headers/` | `CocoSecurityHeadersFilter` |
| 分页参数入口 | `page/` | `CocoPageInterceptor` |
| 访问日志抓取 | `accesslog/` | `CocoAccessLogCaptureProperties` |

用户文档：`website/docs/features/web-runtime.md`、`website/docs/features/request-security.md`、`website/docs/features/pagination.md`。模块说明见 [coco-web：Web 运行时](Module-Web)。

## 六、基础能力与其余 Feature

| 我想改…… | 去哪里 | 备注 |
| --- | --- | --- |
| 请求上下文、分页上下文、Trace 上下文 | `coco-foundation/coco-context/src/main/java/io/github/coco/context/` | `CocoRequestContext`、`CocoPageContext`、`trace/CocoTraceContext` |
| 异常类型与错误码 | `coco-foundation/coco-exception/src/main/java/io/github/coco/exception/` | `CocoException`、`CocoErrorCode` |
| 国际化消息服务与消息包注册 | `coco-foundation/coco-i18n/src/main/java/io/github/coco/i18n/` | `CocoMessageService`、`CocoMessageBundleRegistrar` |
| 某模块的用户可见文本 | 该模块 `src/main/resources/*-messages*.properties`，例：`coco-features/coco-security/src/main/resources/coco-feature-security-messages.properties` | 不要硬编码文本；`coco-cache` 没有消息资源文件，自动配置里也没有 `CocoMessageBundleRegistrar` |
| 日志契约、句柄、访问日志 | `coco-foundation/coco-logging/src/main/java/io/github/coco/logging/` | 装配在 `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/common/logging/autoconfigure/CocoCommonLoggingAutoConfiguration.java` |
| 启动期日志默认值 | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/spring/boot/logging/CocoLoggingEnvironmentPostProcessor.java` | |
| 异步线程池与上下文传播 | `coco-spring/coco-spring-boot-autoconfigure/src/main/java/io/github/coco/spring/boot/async/` | `CocoContextTaskDecorator` |
| MyBatis-Plus 拦截器、分页、SQL Guard | `coco-features/coco-mybatis-plus/src/main/java/io/github/coco/feature/mybatisplus/`（`interceptor/`、`pagination/`、`sqlguard/`） | 扩展点 `CocoMybatisPlusInterceptorCustomizer` |
| 租户 SQL 隔离 | `coco-features/coco-tenant/src/main/java/io/github/coco/feature/tenant/sql/` | `CocoTenantLineHandler` |
| 租户上下文 | `coco-features/coco-tenant/src/main/java/io/github/coco/feature/tenant/context/` | |
| 数据权限 SQL | `coco-features/coco-data-permission/src/main/java/io/github/coco/feature/datapermission/sql/` | `CocoDataPermissionSqlPredicateProvider` |
| 安全上下文与 Header 解析 | `coco-features/coco-security/src/main/java/io/github/coco/feature/security/` | 不是认证实现 |
| 审计事件管线 | `coco-features/coco-audit/src/main/java/io/github/coco/feature/audit/core/` | `CocoAuditRecorder` |
| OpenAPI 元数据 | `coco-features/coco-openapi/` | 与 SpringDoc 适配 |
| 限流 / 幂等 | `coco-features/coco-rate-limit/`、`coco-features/coco-idempotency/` | 不在 starter 直接依赖里 |
| 分布式锁 / 缓存 / 验证码 | `coco-features/coco-lock/`、`coco-features/coco-cache/`、`coco-features/coco-captcha/` | 带 Redis 缺依赖 fail-fast 配置 |
| 动态定时任务 / 对象存储 / 消息 / 通知 | `coco-features/coco-scheduling/`、`coco-features/coco-storage/`、`coco-features/coco-messaging/`、`coco-features/coco-notification/` | |

模块级说明：[模块参考总览](Module-Overview)、[数据与安全类 Feature](Module-Features-Data-And-Security)、[基础设施类 Feature](Module-Features-Infrastructure)。

## 七、测试

| 我想…… | 去哪里 | 备注 |
| --- | --- | --- |
| 看某个类的测试 | 同模块 `src/test/java`，包名与被测类一致 | 单元测试 `*Test`，集成测试 `*IT` |
| 找一套测试样板 | `coco-features/coco-lock/src/test/java/io/github/coco/feature/lock/` | 自动配置、默认实现、内存与 Redis 存储各一个 |
| 看 starter 默认启动的冒烟 IT | `coco-spring/coco-spring-boot-starter/src/test/java/io/github/coco/spring/boot/CocoStarterSmokeIT.java` | |
| 看归档裁剪的 IT | `coco-support/coco-feature-archive-smoke/src/test/java/io/github/coco/fixture/archive/FeatureArchiveSmokeIT.java` | |
| 看 starter 依赖契约 | `coco-spring/coco-spring-boot-starter/src/test/java/io/github/coco/spring/boot/CocoRateLimitStarterDependencyContractTest.java`、`CocoMybatisPlusStarterDependencyContractTest.java` | |
| 看自动配置只注册一次的检查 | `coco-spring/coco-spring-boot-starter/src/test/java/io/github/coco/spring/boot/CocoSpringCompositionClasspathTest.java` | |
| 看旧坐标兼容探针 | `coco-build/coco-compatibility/coco-config/src/test/java/io/github/coco/compatibility/config/CocoConfigCompatibilityCompileProbe.java` | 另外 8 个门面各有一个 |
| 用共享测试工具读配置元数据 | `coco-support/coco-test/src/main/java/io/github/coco/test/CocoTestSupport.java` | 目前只有 `coco-security` 使用 |

## 八、CI、发布与治理

| 我想…… | 去哪里 | 备注 |
| --- | --- | --- |
| 改主 CI 流水线 | `.github/workflows/ci.yml`、`.github/workflows/reusable-tests.yml`、`.github/workflows/reusable-static-analysis.yml`、`.github/workflows/reusable-codeql.yml` | 改名要保留稳定的 required check 上下文 |
| 改发布流程 | `.github/workflows/release.yml` | 发布说明同步改 `coco-support/coco-document/release.md`；`v*` 标签只由专用 Release App 创建 |
| 改发布容量预检 / 产物期望 | `.github/scripts/release_file_count_preflight.py`、`.github/release/release-artifact-expectations.json` | 新增会发布的模块要登记 |
| 改 Boot 归档坐标校验 | `.github/scripts/verify_boot_archive_feature_coordinates.py` | 同目录有对应测试 |
| 改官网发布流水线 | `.github/workflows/docs.yml` | 只监听 `website/**` 与自身 |
| 改 Agent 评审配置与策略 | `.github/agent-review/`（`config.json`、`policy.md`） | 规格在 `coco-support/coco-document/superpowers/specs/`；`AGENTS.md` 要求改路由协议前先更新规格 |
| 改门禁 / 自动合并 | `.github/workflows/contributor-gate.yml`、`promotion-gate.yml`、`agent-issue-gate.yml`、`agent-review.yml`、`agent-open-pr.yml`、`auto-merge.yml` | 脚本在 `.github/scripts/` |
| 看代码所有者 | `.github/CODEOWNERS` | 默认 `@patton174` |
| 看 PR / Issue 模板 | `.github/PULL_REQUEST_TEMPLATE.md`、`.github/ISSUE_TEMPLATE/` | |
| 看工作流治理说明 | `.github/workflow-governance.md` | |
| 看贡献、治理、安全、支持规则 | `CONTRIBUTING.md`、`GOVERNANCE.md`、`SECURITY.md`、`SUPPORT.md` | |
| 看 Agent 规则 | `AGENTS.md` | 与其他文档冲突时的优先级见 [Home](Home) |

## 九、README、文档与工具

| 我想…… | 去哪里 | 备注 |
| --- | --- | --- |
| 改 README 内容 | `.github/readme/fragments/en/` 与 `.github/readme/fragments/zh-CN/`，然后 `node .github/readme/scripts/render.mjs --write` 与 `--check` | **不要**直接改 `README.md` / `README_CN.md` |
| 看 README 生成规则 | `.github/readme/README.md`、`.github/readme/scripts/render.mjs`、`.github/readme/tests/readme.test.mjs` | |
| 改面向业务的用户文档 | `website/docs/`（中文）、`website/i18n/en/docusaurus-plugin-content-docs/current/`（英文） | 本地预览在 `website/` 下 `npm start` |
| 看某个 Feature 的用户文档 | `website/docs/features/` | 如 `lock.md`、`cache.md`、`tenant.md` |
| 看特性开关的用户说明 | `website/docs/feature-toggles.md` | |
| 看业务接入方式 | `website/docs/getting-started.md` | `coco-parent` + starter，或导入 BOM |
| 看设计规格 / 计划 / 审计 | `coco-support/coco-document/superpowers/`（`specs/`、`plans/`、`audits/`） | |
| 看发布说明 | `coco-support/coco-document/release.md` | |
| 看 MCP 文档检索工具 | `coco-support/coco-tools/coco-agent-skills/` | Node 包，索引来自官网文档 |
| 使用代码图谱 | 仓库根的 `.codegraph/`（已被 `.gitignore` 忽略） | 改完源码执行 `codegraph sync .` |

## Agent 速读顺序

1. `AGENTS.md`：项目定位、边界和开发规则。
2. [Wiki 首页](Home)：目录与读者路径。
3. [架构总览](Architecture)：分层、依赖方向、运行期装配。
4. [Feature 机制](Feature-System)：构建期与运行期的 Feature 计划。
5. 目标模块页：从 [模块参考总览](Module-Overview) 进入。
6. 动手前，如果存在 `.codegraph/`，先用 CodeGraph 定位代码；改完源码执行 `codegraph sync .`。

## 相关页面

- [Coco Framework 开发者 Wiki](Home)
- [项目总览](Project-Overview)
- [术语表](Glossary)
- [FAQ 与故障排查](FAQ-Troubleshooting)
- [约定与规范](Conventions)
- [指南：测试约定](Guide-Testing)
- [指南：分支、PR、CI 与发布流程](Guide-Release-And-Workflow)
- 仓库文档：[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)、[模块布局 module-layout.md](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/architecture/module-layout.md)
