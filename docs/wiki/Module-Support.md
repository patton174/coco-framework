# 支持模块与仓库工具

> 本页回答：`coco-support/` 下有什么（`coco-test`、`coco-feature-archive-smoke`、`coco-document`、`coco-tools`），哪些是 Maven 模块、哪些只是文档或工具目录，以及仓库里其它非框架代码的目录各管什么。
> 依据：`pom.xml`、源码、目录实测与 `git ls-files`，内容对应仓库 `main` 分支。[`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 对 `coco-support` 的定位是：文档、开发工具、测试支持，不进入普通业务运行时。

## 1. 目录一览

| 目录 | 是 Maven 模块？ | 内容 |
| --- | --- | --- |
| `coco-support/coco-test` | 是（`coco-test`） | 共享测试支持，仅一个类 `CocoTestSupport` |
| `coco-support/coco-feature-archive-smoke` | 是（`coco-feature-archive-smoke`） | 仅用于测试的 Spring Boot 归档夹具 + Failsafe IT |
| `coco-support/coco-document` | 否（无 pom） | 仓库架构、发布、审计、计划与规格文档 |
| `coco-support/coco-tools` | 否（无 pom） | 开发期工具；实际有内容的只有 `coco-agent-skills/` |
| `coco-support/coco-test-support` | 否 | 本地构建残留：没有被 Git 跟踪的文件，只有被 `.gitignore` 忽略的 `target/` 与 `.flattened-pom.xml`，无 pom / 源码，不在根 `<modules>` 中；`mvn clean` 或手动删除即可 |

## 2. coco-test

| 项 | 内容 |
| --- | --- |
| 坐标 / 路径 | `io.github.patton174:coco-test`，`coco-support/coco-test` |
| 描述 | Shared test support utilities for Coco Framework |
| 依赖 | `jackson-databind`（编译）、`junit-jupiter`（测试） |
| 主类 | `io.github.coco.test.CocoTestSupport`（唯一类，`public final class`） |
| 测试 | `CocoTestSupportTest`，测试资源 `src/test/resources/test-support-metadata.json` |
| 受管版本 | 已在 `coco-dependencies`（BOM）登记 |
| 使用者 | 目前只有 `coco-security` 以 `test` 作用域依赖它 |

`CocoTestSupport` 的静态方法：

| 方法 | 作用 |
| --- | --- |
| `configurationMetadata(Class<?> anchor)` | 读取 classpath 上 `/META-INF/spring-configuration-metadata.json`，用于在测试里断言配置属性元数据 |
| `jsonResource(Class<?> anchor, String resourcePath)` | 读取任意 classpath JSON 资源 |
| `findNamedNode(JsonNode nodes, String name)` / `requiredNamedNode(JsonNode nodes, String name)` | 在元数据数组里按 `name` 查找节点（后者找不到时抛错） |

**与 `module-layout.md` 的差异**：文档与实现存在出入：[`module-layout.md`](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/architecture/module-layout.md) 的迁移映射把 `coco-test` 的新主制品写成 `coco-test-support`，并规划 `coco-test` 作为兼容门面保留；当前 Reactor 的实际模块是 `coco-support/coco-test`（artifactId `coco-test`），以根 `pom.xml` 为准。`coco-support/coco-test-support/` 与 `coco-build/coco-compatibility/coco-test/` 两个目录没有被 Git 跟踪的文件，只有被 `.gitignore` 忽略的构建产物，属于本地残留，不是仓库内容。写代码和依赖时以 `coco-test` 为准。

## 3. coco-feature-archive-smoke

| 项 | 内容 |
| --- | --- |
| 路径 | `coco-support/coco-feature-archive-smoke` |
| 父 POM | `coco-parent`（`relativePath=../../coco-build/coco-parent/pom.xml`），所以会真实执行 `spring-boot:repackage`、`coco:features`、`coco:prune-package` |
| 依赖 | `coco-spring-boot-starter`；测试：`jackson-databind`、`junit-jupiter`、`assertj-core` |
| 发布 | `maven.deploy.skip=true`、`maven.install.skip=true`，不会发布也不会安装 |
| 描述 | Test-only Spring Boot archive fixture for Coco feature assembly and pruning |

**夹具应用**：`io.github.coco.fixture.archive.FeatureArchiveSmokeApplication`（`@SpringBootApplication`），并用 `@CocoFeatures(disabled = {MYBATIS_PLUS, TENANT, DATA_PERMISSION, RATE_LIMIT, IDEMPOTENCY})` 禁用 5 个 Feature，仅为了生成一个带清单、能被裁剪的 Boot 归档。

**验证测试**：`FeatureArchiveSmokeIT#verifiesCurrentReactorArchiveManifestAndPrunedLibraries`。Failsafe 配置把系统属性 `coco.feature.archive.path` 设为 `${project.build.directory}/${project.build.finalName}.jar`，测试据此检查：

- `META-INF/coco/features.json` 中 `web`、`audit`、`security`、`openapi` 为 enabled，上述 5 个被禁用的 Feature 为 disabled，并且没有 `codegen` 条目。
- `BOOT-INF/lib/` 中存在 `coco-web-`、`coco-audit-`、`coco-security-`、`coco-openapi-` 前缀的 jar，不存在旧坐标 `coco-feature-web-` 等；也不存在 `coco-feature-codegen-` 与 `freemarker-`。
- 被禁用 Feature 对应的 jar（`coco-mybatis-plus-`、`coco-tenant-`、`coco-data-permission-`、`coco-rate-limit-`、`coco-idempotency-` 及其旧 `coco-feature-*` 名）不在归档、`BOOT-INF/classpath.idx`、`BOOT-INF/layers.idx` 中，且两个索引与归档里的 jar 集合完全一致。

执行时机：[`release.md`](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/release.md) 称它在 `mvn -B install` 的 Failsafe 阶段执行。根 `pom.xml` 把 `maven-failsafe-plugin` 的 `integration-test` 与 `verify` 目标绑定到所有模块，因此单独运行该模块可用 `mvn -B -pl :coco-feature-archive-smoke -am verify`（命令取自上述 Failsafe 绑定）。

**验收所有权**（同上 `release.md`）：框架仓库负责模块级回归、Feature 装配与 `prune-package` 的 Boot archive / index 验证；跨模块的 Basic / Full HTTP 验收属于独立仓库 `coco-admin/framework-acceptance`，不随 Maven Central 发布。框架里没有业务样例，也没有可独立运行的 demo。

## 4. coco-document（无 pom）

| 子目录 / 文件 | 内容 |
| --- | --- |
| `architecture/module-layout.md` | 2.0 模块布局、所有权、已发布兼容基线 6 条规则、依赖方向图、迁移规则与迁移映射表 |
| `release.md` | 发布流程、本地验证、验收所有权 |
| `superpowers/specs/` | 设计规格存档（目前 12 份），含框架边界、Spring 组合层、JDBC 重放存储、审计独立性、默认审计日志、异步日志溢出可观测性、i18n 与响应包装等 |
| `superpowers/plans/` | 早期实施计划（6 份，2026-07-04 / 05） |
| `superpowers/audits/` | 审计报告（含 `README.md` 及 3 份 2026-07-09 审计） |
| `website/` | 官网外观重设计的计划与状态存档（`2026-09-07-facade-plan-v1.md`、`2026-09-07-facade-status.md`、`2026-09-07-lighthouse-live.json`） |

[`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 规定：多 Agent 评审与治理自动化的规范性规格在这里（`2026-07-10-multi-agent-review-jury.md`、`2026-07-11-agent-governance-automation.md`），改动评审路由协议前要先更新规格。

## 5. coco-tools（无 pom）

Git 只跟踪 `coco-agent-skills/`。其余目录（`artifact-ownership/tests`、`compatibility-consumers/{fixtures,tests}`、`docs/`、`tests/`）没有被 Git 跟踪的文件，在工作区里是空目录或只含 `target/` 构建产物（`fixtures` 下有 `canonical`、`feature-api`、`legacy-2x` 三个夹具目录的产物），属于本地残留，不是仓库内容。

### coco-agent-skills

| 项 | 内容 |
| --- | --- |
| 路径 | `coco-support/coco-tools/coco-agent-skills` |
| 包名 / 版本 | `@patton174/coco-agent-skills`，`0.2.1`（`package.json`） |
| 运行环境 | Node.js ≥ 20，ES Module |
| 入口 | `bin/cli.mjs`（命令名 `coco-agent-skills`）；MCP 服务器 `src/mcp-server.mjs` |
| 依赖 | `@huggingface/transformers`、`@modelcontextprotocol/sdk` |
| 脚本 | `npm run build-index`（重建文档索引）、`npm run mcp`（启动 MCP 服务器）、`npm test`（`node --test`） |
| 数据 | `data/doc-index.json`、`data/doc-index.meta.json`（文档索引及元信息） |
| 技能文件 | `skills/agents`、`skills/claude`、`skills/cursor` |
| 测试 | `test/extended.test.mjs`、`test/search.test.mjs`、`test/version.test.mjs` |
| 发布 | 工作流 `.github/workflows/npm-publish-agent-skills.yml` |

MCP 服务器注册的 7 个工具（`mcp-server.mjs` 实读）：`coco_search_docs`（检索文档）、`coco_get_doc`（取整页文档）、`coco_list_docs`（列出文档页）、`coco_dependency_snippet`（生成依赖片段）、`coco_get_latest_version`（取 Maven Central 最新版本）、`coco_check_version`（比较版本）、`coco_index_status`（索引新鲜度）。注意文件头部注释只写了前三个工具，已落后于代码。

官网对应用户文档：[skills/overview](https://github.com/patton174/coco-framework/blob/main/website/docs/skills/overview.md)、[install](https://github.com/patton174/coco-framework/blob/main/website/docs/skills/install.md)、[usage](https://github.com/patton174/coco-framework/blob/main/website/docs/skills/usage.md)。

## 6. 其它仓库级目录（简述）

| 目录 | 内容 |
| --- | --- |
| `website/` | Docusaurus 用户文档站点（`npm`，CI 使用 Node 20）；用户文档源在 `website/docs/`，英文在 `website/i18n/en/` |
| `.github/` | 工作流、治理脚本、README 生成器（`.github/readme/`）、Agent 评审配置（`.github/agent-review/`） |
| `.codegraph/` | CodeGraph 本地索引，已被 `.gitignore` 忽略；用法见 [开发环境与常见陷阱](Dev-Environment) |

## 7. 相关页面

- [模块参考总览](Module-Overview)
- [构建模块](Module-Build)
- [Spring 组合层](Module-Spring)
- [指南：测试约定](Guide-Testing)
- [指南：分支、PR、CI 与发布流程](Guide-Release-And-Workflow)
- [速查：开发任务到文件 / 目录](Where-To-Find-Things)
- [FAQ 与故障排查](FAQ-Troubleshooting)
