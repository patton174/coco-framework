# 项目总览

> 本页回答：Coco Framework 是什么、不是什么、技术栈和版本是什么、仓库顶层怎么分、有哪些 Feature、业务应用怎么接入。

## 1. 定位

Coco Framework 是一个高约定的 Spring Boot Web 服务器框架，用来快速搭建可上生产的 Web 服务。

- 它不限于 SaaS 系统。
- 它不是零代码的业务运行时。框架为重复出现的基础设施提供一层强约束的“黑盒”，业务代码、领域设计、生成的 CRUD 代码、自定义查询和事务边界仍由开发者掌控。

来源：[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) “Project Positioning”。

## 2. 核心原则

| 方向 | 做法 |
| --- | --- |
| 顶层封装重复性基础设施 | starter 装配、Feature 启停、构建期裁剪、运行期条件、统一响应、异常、i18n、Trace 上下文、访问日志、请求安全、审计管线、MyBatis-Plus 集成、租户 SQL 隔离、数据权限 SQL 集成 |
| 避免过度封装业务 | 不把 CRUD 藏在运行时魔法里；不自动把实体暴露成 API；不强制所有应用采用同一套用户 / 角色 / 菜单 / 租户模型 |
| CRUD 与标准业务脚手架 | 通过代码生成产出可读源码，之后归业务项目所有 |
| 注解 | 表达明确的业务或框架意图，不做难以调试的不透明开关 |
| 默认值与扩展点 | 默认值有用且安全；每个主要集成点都可以通过属性、Bean 或 SPI 配置或替换 |
| 工程责任 | 业务开发者需要理解 Java、Spring Boot、Maven 与自己保留的生成代码。Coco 减少样板代码，不取代工程责任 |

来源：[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) “Core Principles”（此处为转述）。

## 3. 生态边界

| 项目 | 角色 | 与框架的关系 |
| --- | --- | --- |
| `coco-framework`（本仓库） | 独立的 Web 服务器基础框架 | 不得依赖 `coco-admin` 或 `coco-generate` |
| `coco-admin` | 基于框架构建的 ERP 产品 | ERP 领域、认证选择、组织与权限模型、工作流、报表、事务都属于它；独立仓库 |
| `coco-generate` | 开发期源码生成器与模板平台 | 业务应用运行时不得依赖它；生成出的文件由业务仓库评审、提交并持有；独立仓库 |

框架里没有业务样例。业务和 HTTP 验收由 `coco-admin/framework-acceptance` 承担（[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) “Ecosystem Boundaries”）。

## 4. 技术栈与版本

| 项 | 值 | 来源 |
| --- | --- | --- |
| Maven 坐标 groupId | `io.github.patton174`（注意：Java 包根是 `io.github.coco`，二者不同） | `pom.xml`；各模块 `src/main/java` |
| 默认版本 `revision` | `1.0.0-SNAPSHOT`（本地默认值）；正式版本通过 `-Drevision=X.Y.Z` 注入 | `pom.xml`；`.github/workflows/release.yml` |
| 编译目标 | `java.version=17`；`maven-compiler-plugin` 使用 `<release>` 与 `<parameters>true</parameters>` | `pom.xml` |
| 开发与 CI 使用的 JDK | JDK 21（CI 使用 Temurin；环境变量 `JAVA_TOOLCHAIN_VERSION=21`、`MAVEN_COMPILE_RELEASE=17`） | [AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) “Verification”；`.github/workflows/reusable-tests.yml` |
| Spring Boot | `4.1.1` | `pom.xml` 属性 `spring-boot.version` |
| MyBatis-Plus | `3.5.17` | `pom.xml` 属性 `mybatis-plus.version` |
| 业务应用侧要求 | JDK 17+、Maven 3.8.9+ | [`website/docs/getting-started.md`](https://github.com/patton174/coco-framework/blob/main/website/docs/getting-started.md) |
| 许可证 | Apache-2.0 | `pom.xml`；[LICENSE](https://github.com/patton174/coco-framework/blob/main/LICENSE) |
| 维护者 | `@patton174`（`.github/CODEOWNERS` 中所有路径均指向该账号） | [GOVERNANCE.md](https://github.com/patton174/coco-framework/blob/main/GOVERNANCE.md)；`.github/CODEOWNERS` |
| 仓库 | `https://github.com/patton174/coco-framework` | `pom.xml` |
| 官网与用户文档 | [https://cocoframework.dev/](https://cocoframework.dev/)（Docusaurus，源码在 `website/`，默认中文，另有 `website/i18n/en`） | [README_CN.md](https://github.com/patton174/coco-framework/blob/main/README_CN.md)；`.github/workflows/docs.yml` |

构建行为要点（`pom.xml`）：

- Enforcer 在 `validate` 阶段执行 `enforce-build-gates`：禁止重复的 POM 依赖版本、检查依赖收敛（排除 `provided` 与 `test`）、禁止 `commons-logging:commons-logging`、`log4j:log4j`、`org.slf4j:log4j-over-slf4j`、`spring-boot-starter-jetty`、`spring-boot-starter-undertow`。
- Surefire 与 Failsafe 均设 `useModulePath=false`。
- `flatten-maven-plugin` 使用 `resolveCiFriendliesOnly`，各模块会生成 `.flattened-pom.xml`（已被 `.gitignore` 忽略）。
- SpotBugs 与 Checkstyle 不绑定生命周期，本地 `mvn install` 不会运行它们，CI 才会直接调用对应目标。
- `release` profile 额外启用 `requireReleaseVersion`、源码包与 Javadoc 包、GPG 签名和 Central 发布，细节见 [指南：分支、PR、CI 与发布流程](Guide-Release-And-Workflow)。

### 版本说明

最近发布的稳定版本是 `v2.0.3`，以 [GitHub Releases](https://github.com/patton174/coco-framework/releases) 与 [CHANGELOG.md](https://github.com/patton174/coco-framework/blob/main/CHANGELOG.md) 为准；面向业务使用者的发布说明在 [`website/docs/releases/`](https://github.com/patton174/coco-framework/tree/main/website/docs/releases)。

根 `pom.xml` 中的 `revision=1.0.0-SNAPSHOT` 只是本地构建的默认值，不代表发布版本；正式版本由发布工作流通过 `-Drevision=X.Y.Z` 注入。本 Wiki 不在其他页面写死“最新版本”。

## 5. 仓库顶层目录地图

| 路径 | 职责 | 备注 |
| --- | --- | --- |
| `coco-build/` | BOM、父 POM、Maven 插件、发布期构建行为、2.x 旧坐标兼容门面 | 见 [构建模块](Module-Build) |
| `coco-foundation/` | 稳定契约与不依赖 Spring 的基础设施 | 见 [基础模块](Module-Foundation) |
| `coco-spring/` | Spring Boot 集成与 starter 组合 | 见 [Spring 组合层](Module-Spring) |
| `coco-features/` | 可独立启停的 Web 服务器能力（16 个 Feature 模块，聚合 pom 为 `coco-features`） | 见 [模块参考总览](Module-Overview) |
| `coco-support/` | 文档、开发工具、测试支持，不进入普通业务运行时 | 见 [支持模块与仓库工具](Module-Support) |
| `website/` | Docusaurus 官网与面向业务使用者的用户文档 | 由 `.github/workflows/docs.yml` 发布，只监听 `website/**` |
| `docs/` | 本 Wiki 的事实源（`docs/wiki/`）与官网文档 UI 的设计与计划文档（`docs/superpowers/`） | Wiki 维护方式见 [指南：维护与发布本 Wiki](Guide-Contribute-To-Wiki) |
| `.github/` | CI 工作流、治理脚本、README 渲染、Agent 评审配置、Issue 与 PR 模板 | README 由 `.github/readme/fragments` 生成 |

要点：

- 目录职责来自 [GOVERNANCE.md](https://github.com/patton174/coco-framework/blob/main/GOVERNANCE.md) 的 “Module ownership” 与 [2.0 模块布局](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/architecture/module-layout.md)。
- 根 `pom.xml` 的 `<modules>` 是 Reactor 的唯一入口，包含 2 个构建 POM、6 个 foundation 模块、2 个 spring 模块、`coco-features` 聚合、`coco-maven-plugin`、9 个兼容门面，以及 `coco-test` 与 `coco-feature-archive-smoke` 两个支持模块。逐模块清单见 [模块参考总览](Module-Overview)。
- `README.md` 与 `README_CN.md` 是脚本生成的文件，不要直接编辑（见 [约定与规范](Conventions)）。

## 6. 16 个 Feature 一览

Feature 的稳定标识来自枚举 `io.github.coco.api.feature.CocoFeature`（`coco-foundation/coco-api`），元数据（模块 artifactId、自动配置类、依赖）来自 `StandardCocoFeatures`（`coco-foundation/coco-feature-model`）。当前枚举有 16 项，且 16 项的 `defaultEnabled` 都是 `true`。

| id | 枚举 | 模块 | 依赖的其他 Feature | 模块页 |
| --- | --- | --- | --- | --- |
| `web` | `WEB` | `coco-web` | 无 | [coco-web](Module-Web) |
| `mybatis-plus` | `MYBATIS_PLUS` | `coco-mybatis-plus` | 无 | [数据与安全类](Module-Features-Data-And-Security) |
| `audit` | `AUDIT` | `coco-audit` | 无 | [数据与安全类](Module-Features-Data-And-Security) |
| `security` | `SECURITY` | `coco-security` | 无 | [数据与安全类](Module-Features-Data-And-Security) |
| `tenant` | `TENANT` | `coco-tenant` | `mybatis-plus`、`security` | [数据与安全类](Module-Features-Data-And-Security) |
| `data-permission` | `DATA_PERMISSION` | `coco-data-permission` | `mybatis-plus`、`security` | [数据与安全类](Module-Features-Data-And-Security) |
| `openapi` | `OPENAPI` | `coco-openapi` | `web`、`security` | [数据与安全类](Module-Features-Data-And-Security) |
| `rate-limit` | `RATE_LIMIT` | `coco-rate-limit` | `web` | [基础设施类](Module-Features-Infrastructure) |
| `idempotency` | `IDEMPOTENCY` | `coco-idempotency` | `web` | [基础设施类](Module-Features-Infrastructure) |
| `scheduling` | `SCHEDULING` | `coco-scheduling` | 无 | [基础设施类](Module-Features-Infrastructure) |
| `lock` | `LOCK` | `coco-lock` | 无 | [基础设施类](Module-Features-Infrastructure) |
| `storage` | `STORAGE` | `coco-storage` | 无 | [基础设施类](Module-Features-Infrastructure) |
| `messaging` | `MESSAGING` | `coco-messaging` | 无 | [基础设施类](Module-Features-Infrastructure) |
| `cache` | `CACHE` | `coco-cache` | 无 | [基础设施类](Module-Features-Infrastructure) |
| `notification` | `NOTIFICATION` | `coco-notification` | 无 | [基础设施类](Module-Features-Infrastructure) |
| `captcha` | `CAPTCHA` | `coco-captcha` | 无 | [基础设施类](Module-Features-Infrastructure) |

阅读这张表时请注意三点：

- “Feature 默认启用”不等于“模块功能默认生效”。很多模块还有自己的 `coco.<feature>.enabled` 属性，默认值各不相同。例如 `coco.rate-limit.enabled`、`coco.idempotency.enabled`、`coco.lock.enabled`、`coco.cache.enabled`、`coco.captcha.enabled`、`coco.notification.enabled`、`coco.storage.enabled` 默认为 `false`，而 `coco.scheduling.enabled`、`coco.messaging.enabled`、`coco.audit.enabled` 默认为 `true`。这两层开关的关系见 [Feature 机制](Feature-System)。
- 依赖会向下传播：禁用某个 Feature 时，依赖它的 Feature 也会被连带禁用，例如禁用 `mybatis-plus` 会连带禁用 `tenant` 与 `data-permission`。
- 功能标识以 `CocoFeature` 枚举为准，当前为 16 项；内置的代码生成（Codegen）功能及其 feature id 已从框架中移除。

## 7. 业务应用如何接入

业务应用的接入面向“一个 starter 加一个父 POM（或 BOM）”。下面是业务侧写法的摘要，完整说明见用户文档 [`website/docs/getting-started.md`](https://github.com/patton174/coco-framework/blob/main/website/docs/getting-started.md)。

方式一：继承 `coco-parent` 并依赖唯一的 starter。

```xml
<parent>
    <groupId>io.github.patton174</groupId>
    <artifactId>coco-parent</artifactId>
    <version>${coco.version}</version>
    <relativePath/>
</parent>

<dependencies>
    <dependency>
        <groupId>io.github.patton174</groupId>
        <artifactId>coco-spring-boot-starter</artifactId>
    </dependency>
</dependencies>
```

方式二：不继承父 POM，仅导入 BOM `coco-dependencies` 管理版本。

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.patton174</groupId>
            <artifactId>coco-dependencies</artifactId>
            <version>${coco.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

`coco-parent` 在构建中绑定三个插件目标：`spring-boot-maven-plugin:repackage`、`coco-maven-plugin:features`（装配启用的 Feature 依赖并写入清单）、`coco-maven-plugin:prune-package`（从 Spring Boot 包中裁剪被禁用 Feature 的 jar）。这一整套机制见 [Feature 机制](Feature-System) 与 [构建模块](Module-Build)。

两个要点：

- `coco-spring-boot-starter` 是业务应用的唯一常规 starter，只负责组合能力，不承载行为（[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) “Development Rules”）。
- 在 starter 的直接依赖中不包含 `coco-rate-limit` 与 `coco-idempotency`，二者由构建期的 `coco:features` 按 Feature 计划加入。依赖关系见 [架构总览](Architecture)。

## 8. 本仓库“能跑”的是什么

框架本身不是可直接运行的应用，仓库里也没有可独立运行的 demo。能“跑起来”的只有两个集成测试夹具，业务与 HTTP 验收属于 `coco-admin/framework-acceptance`。构建、测试与验证命令见 [快速开始](Getting-Started)，测试约定见 [指南：测试约定](Guide-Testing)。

## 相关页面

- [快速开始：构建、测试与验证](Getting-Started)
- [开发环境与常见陷阱（Windows / IDE / CodeGraph）](Dev-Environment)
- [架构总览](Architecture)
- [Feature 机制：构建期与运行期](Feature-System)
- [模块参考总览](Module-Overview)
- [约定与规范](Conventions)
- [术语表](Glossary)
- [Wiki 首页](Home)
