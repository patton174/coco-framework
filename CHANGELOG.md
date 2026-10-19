# Changelog / 更新日志

本文件记录 Coco Framework 各版本的重要变更，面向框架开发者与贡献者。格式遵循
[Keep a Changelog 1.1.0](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循
[语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。

This file records notable changes of Coco Framework for framework developers and
contributors. It follows [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/)
and [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html). Section
headings are English; entries are written in Chinese first.

面向业务使用者的版本说明（升级检查、能力介绍）见
[`website/docs/releases/`](website/docs/releases/index.md)，每个版本的发布记录见
[GitHub Releases](https://github.com/patton174/coco-framework/releases)。两处互相链接，不复制全文。

## 维护规则 / Maintenance

- 提交 PR 时，用户可见或贡献者可见的变更（公开 API、Maven 坐标、配置属性、构建与治理流程、
  文档基础设施）在 `[Unreleased]` 下追加一条，按 `Added`、`Changed`、`Deprecated`、`Removed`、
  `Fixed`、`Security` 分组；破坏性变更以 **BREAKING** 开头。纯内部重构、格式化和无行为变化的
  依赖升级可以不记。
- 发版前由维护者把 `[Unreleased]` 的条目移到新的版本标题下，写明发布日期，并更新文末的比较链接。
- 版本标签只由受保护的 Release 工作流创建，不要手工推送标签；日期取标签创建日（UTC）。
- 条目只写可从提交记录、PR 或发布说明核实的事实，不写计划与猜测。
- 该流程目前由 PR 模板的检查项提醒，没有工作流强制执行。

## [Unreleased]

尚无未发布变更。

## [3.0.0] - 2026-10-19

模块规范化收官版本：规范模块坐标成为实现主体，移除代码生成与 Node 日志渲染器，并新增
MyBatis-Plus 动态多数据源。详细说明与升级检查见
[3.0.0 版本说明](website/docs/releases/3.0.0.md)，发布记录见
[GitHub Release](https://github.com/patton174/coco-framework/releases/tag/v3.0.0)。

### Added

- 治理：agent 分支前缀可通过仓库变量 `COCO_AGENT_BRANCH_PREFIXES` 配置（逗号分隔，未设置时仍为
  `codex/`）；前缀只是标签，Contributor gate 仍要求 Coco App 的登录名与用户 ID。
- 发布：Release 工作流在发布前预检 Maven Central 文件配额，并恢复完整的制品清单预检。
- Agent review：模型请求超时、补全次数、传输重试退避和超时上限可通过 `COCO_AGENT_*` 变量配置；
  各阶段的作业超时可通过仓库变量调整。
- 文档基础设施：新增本更新日志；`docs/wiki/` 作为公开 GitHub Wiki 的源目录，并提供
  `.github/scripts/wiki_sync.py`（校验与镜像发布）及其单元测试；新增 `docs/README.md` 文档索引；
  补充 PR 模板检查项、文档类 Issue 表单和协作文档（CONTRIBUTING、GOVERNANCE、SECURITY、SUPPORT）
  中的 Wiki 与更新日志说明。
- MyBatis-Plus：`coco-mybatis-plus` 新增单 `SqlSessionFactory` 动态多数据源路由能力
  （`CocoMybatisPlusDataSourceAutoConfiguration`、`CocoRoutingDataSource`、`@CocoDataSource`、
  `CocoDataSourceContextHolder`、`CocoDataSourceRegistry`），支持 `coco.mybatis-plus.datasource.*` 配置与
  `CocoDataSourceProvider`、`CocoDataSourceCreator`、`CocoDataSourceLoadBalancer`、
  `CocoDataSourceKeyResolver`、`CocoDataSourceRouter` 扩展点，
  并在 `coco.logging.quiet-spring=true` 时默认关闭 `mybatis-plus.global-config.banner`。

### Changed

- **BREAKING** 启动日志收敛为一行 `ready` 记录，并携带 `java` 与 `pid`；
  `CocoSpringApplicationRunListener#started` 不再输出生命周期记录。
- Feature 模块迁移到规范模块：`coco-audit`、`coco-openapi`、`coco-tenant`、`coco-data-permission`、
  `coco-security`、`coco-web`。原 `coco-feature-*` 坐标作为无源码的兼容门面保留在
  `coco-build/coco-compatibility`；`coco-config` 与 `coco-feature-runtime` 门面也迁到同一目录；
  `coco-tenant`、`coco-data-permission` 改为依赖规范的 `coco-mybatis-plus`。
- Agent review：输入预算提高到 960k 字符，支持 Anthropic thinking 块与流式保活，对瞬时传输错误做
  有界退避重试，并能容忍被代码围栏包裹的 JSON 与不存在的工具调用。
- 治理：Dependabot 的目标分支改为 `dev`，避免被 Promotion gate 长期挂起。
- 治理：README 维护 PR 恢复直接指向 `main`。Promotion gate 为 `automation/readme-maintenance` 增加受限通道，
  要求作者是 `COCO_AGENT_APP_LOGIN` / `COCO_AGENT_APP_BOT_ID` 绑定的 Coco App，且变更文件只能是
  `README.md`、`README_CN.md`、`.github/readme/fragments/**` 与 `.github/readme/state/content-baseline.json`；
  `main` 仅领先 `dev` 这些 README 产物时，`dev → main` 晋升不再要求先把 `main` 合回 `dev`。
- 官网：品牌标识与首页重做（滚动驱动的首页和独立的手机站点），补充 SEO 元数据（Search Console
  标签、sitemap `lastmod`、IndexNow、逐页标题与分享卡片），旧域名 `cocoframwork.dev` 以 301
  跳转到 `cocoframework.dev`。
- 依赖：更新 `maven-plugin-plugin`（3.16.0）、构建插件组、GitHub Actions 固定版本、官网 npm 依赖，
  CI 中的 ruff 升至 0.16.9。

### Removed

- **BREAKING** 移除 Maven 插件的 `coco:generate` 目标及其 `coco.codegen.*` 参数；代码生成由独立项目
  `coco-generate` 的 CLI 负责。
- **BREAKING** 移除 `CocoFeature.CODEGEN` 功能标识及其构建接线。`coco.features.*` 或 `@CocoFeatures`
  中残留的 `codegen` 会得到“已退役标识”的提示并指向 `coco-generate`；FreeMarker 不再出现在依赖图中。
- **BREAKING** 删除 `coco-feature-codegen` 模块及其规格、内部文档和官网页面。
- **BREAKING** 移除 Node 日志渲染器：`CocoNodeLogRendererBootstrap`、`coco.logging.node-renderer.*`、
  `CocoLifecycleLogger#started`、`#startedMessage`、`#startedMessages`，以及
  `coco-support/coco-tools/coco-log-renderer` 工具。运行 `java -jar` 不再需要 `node`。

### Fixed

- 治理：延迟执行的 jury 运行在未发布状态就退出时，`Agent jury gate` 现在会发布明确的失败状态
  （fail closed），不再无限期停在 PENDING。
- 治理：多行的 agent 分支前缀配置现在会被完整读取。
- Agent review：解析内容块前先检查 `tool_use` 的 `stop_reason`；移除 Issue 同步中过时的重复锚点保护。
- 官网：修复手机端代码块标签重叠、中英文切换、滚动流畅度、图示渐显时机以及分享卡片路径解析，
  修复 `robots.txt` 生成与 `/img` 缓存策略，并在各 location 块中保留 `Referrer-Policy`。

## [2.0.3] - 2026-09-07

平台能力扩展。详细说明与升级检查见
[2.0.3 版本说明](website/docs/releases/2.0.3.md)，发布记录见
[GitHub Release](https://github.com/patton174/coco-framework/releases/tag/v2.0.3)。

### Added

- 分页：统一的 `CocoPage` / `CocoSortOrder` 模型，Web 分页参数解析并自动注入 MyBatis-Plus。
- Web 运行时：可配置的 CORS 与安全响应头、字段级校验错误，以及请求上下文和重放清理改进。
- 自动配置：Jackson 约定与可传播请求上下文的异步执行器。
- 存储：可替换的存储 SPI 与安全的本地存储实现（`coco-storage`）。
- 消息与锁：消息模块与内置 Redis 分布式锁存储。
- 限流：滑动窗口与令牌桶算法。
- 缓存：Caffeine 本地缓存与 Caffeine + Redis 两级缓存。
- 通知：可替换的通知渠道模块。
- 验证码：图形、滑块、短信码 SPI，以及适用于多实例的 Redis 答案存储；短信发送由业务通知渠道负责。
- 治理：为 `dev` 集成分支新增 Contributor gate，为 `dev` 晋升 `main` 新增 Promotion gate。

升级提示：新增模块不代表全部默认启用，请按模块文档检查开关、Bean 和外部服务配置。

## [2.0.2] - 2026-08-25

内容依据 [GitHub Release](https://github.com/patton174/coco-framework/releases/tag/v2.0.2) 记录的
合并 PR 整理。

### Added

- 限流模块（可选启用），随后接入为标准 Feature。
- 标准请求幂等能力。
- 分布式锁与调度模块。
- Redis 共享存储。
- Agent jury：模型供应商可配置，支持 OpenAI chat completions 协议并可控制聊天模型的思考模式。
- 治理：受保护的 Release App 在发布成功后创建不可变版本标签。

### Changed

- Spring 组合层实现合并到 `coco-spring-boot-autoconfigure`（2.0 迁移第 7a 批），并补充已发布坐标在迁移期间的保留约定。
- MyBatis-Plus 迁移到规范模块 `coco-mybatis-plus`。
- 框架仓库内的业务样例与代码生成第一阶段迁出（见 `AGENTS.md` 的生态边界）。
- Agent review：评审受保护的 Coco App PR 与 Dependabot PR 时使用完整 jury，上下文预算、
  验证器输出契约、语义共识与 Issue 连续性多处加固。
- 依赖：`maven.version` 3.8.9 → 3.9.16，构建插件组、MyBatis-Plus 组、GitHub Actions 固定版本、
  CI 中的 ruff 与 `cryptography` 升级。

### Fixed

- 关闭 MyBatis-Plus 时同步裁剪其 native-image 支持。
- 恢复完整的 Agent review 回归测试，修复 jury 绑定、密钥路由和 workflow 关联载荷问题。

## [2.0.1] - 2026-07-12

Coco Framework 2.0 的首个版本。依据
[GitHub Release](https://github.com/patton174/coco-framework/releases/tag/v2.0.1) 与 `v1.0.2` 之后的提交整理。

### Added

- 受保护的 Agent Pull Request 工作流、多 Agent 评审 jury 与仓库治理自动化。
- Web：JDBC 重放存储与响应包装大小阈值。
- 审计：默认的结构化审计日志。
- 日志：异步日志溢出遥测。
- Feature 解析诊断；租户与数据权限 SQL 集成；安全基础能力。
- Maven Enforcer 门禁与跨平台（Linux、macOS、Windows）验证矩阵。

### Changed

- **BREAKING** 建立 2.0 所有权布局：`coco-build`、`coco-foundation`、`coco-spring`、`coco-features`、
  `coco-support`。
- **BREAKING** Maven 制品重命名，包括 `coco-bom` → `coco-dependencies`、`coco-api-core` → `coco-api`、
  `coco-feature-registry` → `coco-feature-model`，以及 context、i18n、exception、logging 等基础设施制品。
  升级前请对照 2.0 模块迁移映射（`coco-support/coco-document/architecture/module-layout.md`）。
- 仓库文档合并到 `coco-support/coco-document`，开发工具合并到 `coco-support/coco-tools`。
- Web 重放清理移出请求路径。
- `coco-spring-boot-starter` 仍是业务应用的常规依赖，`coco-parent` 仍是推荐的父 POM；发布字节码目标仍为
  Java 17，仓库构建使用 JDK 21。

### Fixed

- 打包裁剪前保留原始 jar；审计与 Web、MyBatis 解耦；重放请求形态预检。

## [1.0.2] - 2026-07-08

### Added

- 扩展可选 Feature 基础设施：审计发布与错误策略、OpenAPI 元数据 SPI、代码生成 SPI、
  MyBatis-Plus SQL Guard 配置。

发布记录：[GitHub Release](https://github.com/patton174/coco-framework/releases/tag/v1.0.2)。

## [1.0.1] - 2026-07-08

该版本没有 GitHub Release 说明，内容取自 `v1.0.0` 到 `v1.0.1` 之间的提交记录。

### Added

- 扩展 Feature 模块基础。

### Changed

- Release 工作流在发布时等待 Maven Central 完成发布，并防止重复发布到 Central。

### Fixed

- 关闭的 Feature 不再破坏样例运行时。

## [1.0.0] - 2026-07-08

首个 Maven Central 发布版本，详见
[GitHub Release](https://github.com/patton174/coco-framework/releases/tag/v1.0.0)。

### Added

- 发布全部 Coco Framework Maven 模块到 Maven Central。
- Web 请求基础设施：请求体解析与缓存、参数来源与规范化、签名校验、请求加密、重放保护基础、
  Cookie 与浏览器指纹上下文、客户端 IP 解析。
- Feature 模块基础。
- Release 工作流：Maven Central 环境密钥、内存 GPG 签名、等待自动发布，以及未指定版本时基于最新
  `v*` 标签生成下一个补丁版本。

[Unreleased]: https://github.com/patton174/coco-framework/compare/v3.0.0...main
[3.0.0]: https://github.com/patton174/coco-framework/compare/v2.0.3...v3.0.0
[2.0.3]: https://github.com/patton174/coco-framework/compare/v2.0.2...v2.0.3
[2.0.2]: https://github.com/patton174/coco-framework/compare/v2.0.1...v2.0.2
[2.0.1]: https://github.com/patton174/coco-framework/compare/v1.0.2...v2.0.1
[1.0.2]: https://github.com/patton174/coco-framework/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/patton174/coco-framework/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/patton174/coco-framework/releases/tag/v1.0.0
