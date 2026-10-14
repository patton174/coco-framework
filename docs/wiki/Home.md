# Coco Framework 开发者 Wiki

> 本页回答：这个 Wiki 讲什么、给谁看、每一页在哪里、第一次来该先读哪几页。

## 项目一句话

Coco Framework 是一个高约定的 Spring Boot Web 服务器框架，目标是快速搭建可上生产的 Web 服务。它不限于 SaaS 场景，也不是零代码的业务运行时：框架负责重复出现的基础设施，业务代码、领域设计、生成的 CRUD 代码、自定义查询和事务边界仍由开发者掌控。CRUD 与标准业务脚手架应通过代码生成产出可读源码，再由业务项目自己持有（见 [AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 的 “Project Positioning” 与 “Core Principles”）。

## 这个 Wiki 面向谁

| 读者 | 应该去哪里 |
| --- | --- |
| 框架开发者、贡献者、AI Agent | 本 Wiki：模块结构、Feature 机制、构建与测试命令、约定、发布流程 |
| 业务应用的使用者（只想接入框架） | 官网 [https://cocoframework.dev/](https://cocoframework.dev/) 与仓库内的用户文档目录 [`website/docs/`](https://github.com/patton174/coco-framework/tree/main/website/docs) |

本 Wiki 是对代码与既有权威文档的导读，不是规范源。用户文档、治理规则与设计规格已经存在，Wiki 只做链接，不复制正文。

最近发布的版本、变更历史与升级提示见仓库的 [CHANGELOG.md](https://github.com/patton174/coco-framework/blob/main/CHANGELOG.md) 与 [GitHub Releases](https://github.com/patton174/coco-framework/releases)。

## 目录

### 入门

| 页面 | 内容 |
| --- | --- |
| [项目总览](Project-Overview) | 定位与核心原则、生态边界、技术栈与版本、顶层目录地图、16 个 Feature 一览 |
| [快速开始：构建、测试与验证](Getting-Started) | 前置条件、克隆后的构建与验证步骤、PowerShell 与 bash 命令对照、构建失败时先看哪里 |
| [开发环境与常见陷阱（Windows / IDE / CodeGraph）](Dev-Environment) | JDK 与 Maven 自检、Windows 注意点、IDE 导入、CodeGraph、本地杂项目录 |

### 架构

| 页面 | 内容 |
| --- | --- |
| [架构总览](Architecture) | 分层与所有权、依赖方向规则、依赖图、运行期装配总览、兼容策略 |
| [Feature 机制：构建期与运行期](Feature-System) | Feature 枚举与元数据、选择来源与优先级、计划解析、manifest、Maven 插件 goal、运行期条件 |

### 模块参考

| 页面 | 内容 |
| --- | --- |
| [模块参考总览](Module-Overview) | 全部 Reactor 模块一览与阅读入口 |
| [构建模块：BOM、父 POM、Maven 插件与兼容门面](Module-Build) | `coco-dependencies`、`coco-parent`、`coco-maven-plugin`、`coco-compatibility/*` |
| [基础模块：coco-foundation](Module-Foundation) | `coco-api`、`coco-context`、`coco-exception`、`coco-feature-model`、`coco-i18n`、`coco-logging` |
| [Spring 组合层：autoconfigure 与 starter](Module-Spring) | `coco-spring-boot-autoconfigure`、`coco-spring-boot-starter` |
| [coco-web：Web 运行时](Module-Web) | 统一响应、异常、Trace、访问日志、签名、加密、重放保护等 |
| [数据与安全类 Feature：mybatis-plus / tenant / data-permission / security / audit / openapi](Module-Features-Data-And-Security) | 这六个 Feature 的职责、配置与扩展点 |
| [基础设施类 Feature：rate-limit / idempotency / lock / cache / captcha / scheduling / storage / messaging / notification](Module-Features-Infrastructure) | 这九个 Feature 的职责、配置与存储实现 |
| [支持模块与仓库工具](Module-Support) | `coco-test`、`coco-feature-archive-smoke`、`coco-support/coco-document`、`coco-support/coco-tools` |

### 约定与指南

| 页面 | 内容 |
| --- | --- |
| [约定与规范](Conventions) | `AGENTS.md` 规则转述与来自真实代码的命名、JavaDoc、SPI、配置约定 |
| [指南：新增一个 Feature 模块](Guide-Add-Feature-Module) | 新模块需要登记的位置清单 |
| [指南：新增或调整自动配置](Guide-Add-Auto-Configuration) | 自动配置的注册、条件、顺序与消息资源包 |
| [指南：测试约定](Guide-Testing) | 单元测试、集成测试（`*IT`）、契约测试与共享测试支持 |
| [指南：分支、PR、CI 与发布流程](Guide-Release-And-Workflow) | 分支命名、PR 门禁、CI 流水线、发布步骤、更新日志维护 |
| [指南：维护与发布本 Wiki](Guide-Contribute-To-Wiki) | `docs/wiki` 的目录与命名规则、编写规范、校验命令、手动发布到 GitHub Wiki 的步骤 |

### 速查与排障

| 页面 | 内容 |
| --- | --- |
| [速查：开发任务到文件/目录](Where-To-Find-Things) | “我要改 X，应该去哪个文件” |
| [术语表](Glossary) | Feature、manifest、门面、BOM 等术语 |
| [FAQ 与故障排查](FAQ-Troubleshooting) | 常见构建失败、本地环境问题与处理办法 |

页面左侧的导航栏（`_Sidebar`）按同样的分组列出了全部页面，并附有 CHANGELOG、CONTRIBUTING、GOVERNANCE、SECURITY、SUPPORT 的链接。

## 读者路径

| 你是谁 | 建议阅读顺序 |
| --- | --- |
| 新贡献者 | [项目总览](Project-Overview) → [快速开始](Getting-Started) → [开发环境](Dev-Environment) → [架构总览](Architecture) → [约定与规范](Conventions) → [分支、PR、CI 与发布流程](Guide-Release-And-Workflow) → [维护与发布本 Wiki](Guide-Contribute-To-Wiki)，同时阅读 [CONTRIBUTING.md](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md)、[GOVERNANCE.md](https://github.com/patton174/coco-framework/blob/main/GOVERNANCE.md) 与 [CHANGELOG.md](https://github.com/patton174/coco-framework/blob/main/CHANGELOG.md) |
| 业务集成者 | 先看官网 [https://cocoframework.dev/](https://cocoframework.dev/)，再看 [`website/docs/getting-started.md`](https://github.com/patton174/coco-framework/blob/main/website/docs/getting-started.md) 与 [`website/docs/feature-toggles.md`](https://github.com/patton174/coco-framework/blob/main/website/docs/feature-toggles.md)；想了解内部机制再读 [Feature 机制](Feature-System) |
| AI Agent | 先读 [AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)；仓库存在 `.codegraph/` 时先用 CodeGraph 定位代码（见 [开发环境](Dev-Environment)）；定位文件用 [速查](Where-To-Find-Things)；动手前读 [约定与规范](Conventions) |

## 权威来源与优先级

- 事实以源码、`pom.xml`、[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)、[CONTRIBUTING.md](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md)、[GOVERNANCE.md](https://github.com/patton174/coco-framework/blob/main/GOVERNANCE.md) 和 `.github/workflows/` 下的工作流为准。本 Wiki 与它们不一致时，以它们为准，并请修正 Wiki。
- 这几份文档彼此也有出入。文档与实现存在出入：`AGENTS.md` 的 “Git Workflow” 与 `CONTRIBUTING.md` / `GOVERNANCE.md` 对分支流程的描述不同，分支与合并门禁以工作流、仓库实际设置和 `CONTRIBUTING.md` / `GOVERNANCE.md` 为准，详见 [约定与规范](Conventions) 与 [指南：分支、PR、CI 与发布流程](Guide-Release-And-Workflow)。
- 其他可链接的既有文档：
  - [2.0 模块布局](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/architecture/module-layout.md)（模块边界、兼容基线、迁移映射）
  - [发布说明与本地验证](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/release.md)
  - [SECURITY.md](https://github.com/patton174/coco-framework/blob/main/SECURITY.md)、[SUPPORT.md](https://github.com/patton174/coco-framework/blob/main/SUPPORT.md)
  - 仓库根的 [README_CN.md](https://github.com/patton174/coco-framework/blob/main/README_CN.md) 与 [README.md](https://github.com/patton174/coco-framework/blob/main/README.md)（由脚本生成，不要直接编辑）

## 阅读说明

| 写法 | 含义 |
| --- | --- |
| 以“文档与实现存在出入：…”开头的段落 | 已知的文档不一致。处理办法就是该句末尾给出的权威来源 |
| 以“设计意图未见文档说明，…”开头的句子 | 现有文档没有解释这样设计的原因。行为以句中给出的源码为准，本 Wiki 不替维护者推测动机 |
| `代码标识符` / 路径 / 命令 | 保持原样，不翻译 |

本 Wiki 是导读而不是规范源。发现新的出入或过时内容，请提 Issue 或 PR。

## 关于本 Wiki

- 内容对应仓库 `main` 分支。以源码为准，发现出入请提 Issue 或 PR。
- 本 Wiki 由主仓库的 [`docs/wiki/`](https://github.com/patton174/coco-framework/tree/main/docs/wiki) 目录镜像而来，该目录是唯一事实源。请通过 PR 修改，不要直接在网页上编辑页面，否则下次同步会被覆盖。维护与发布步骤见 [指南：维护与发布本 Wiki](Guide-Contribute-To-Wiki)。
- 本地现象与 Wiki 不符时，先执行 `git status -sb` 检查工作区，再看 [FAQ 与故障排查](FAQ-Troubleshooting)。
- 维护约定：Wiki 是对代码与既有权威文档的导读，发现不一致时先改源文档或在 Issue 中说明，再同步 Wiki。新增或重命名模块、Feature、配置前缀或 CI 命令时，同步更新各 `Module-*` 页、[Feature 机制](Feature-System)、[速查](Where-To-Find-Things) 与（如有）[新增一个 Feature 模块](Guide-Add-Feature-Module)；新增页面时同时更新 `_Sidebar` 与本页的目录。
