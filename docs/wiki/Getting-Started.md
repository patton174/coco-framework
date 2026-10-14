# 快速开始：构建、测试与验证

> 本页回答：克隆仓库后怎样在本地构建、测试、验证，用到哪些命令，PowerShell 与 bash 写法有什么不同，构建失败时先看哪里。

命令的来源：本页的命令来自 `AGENTS.md`、`CONTRIBUTING.md`、`.github/workflows/*.yml` 与 `coco-support/coco-document/release.md`，其中标为“本页组合”的几条由这些文件和 `pom.xml` 推导而来。

## 1. 前置条件

| 工具 | 要求 | 来源 |
| --- | --- | --- |
| JDK | JDK 21 开发；Maven 编译目标是 Java 17（`<release>` 取自 `java.version=17`） | [AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) “Verification”；`pom.xml` |
| Maven | 业务应用侧要求 3.8.9+；本仓库自身没有 Maven 版本下限：根 `pom.xml` 的 Enforcer 规则不检查 Maven 版本，CI 使用 runner 自带的 Maven。属性 `maven.version`（`3.9.16`）只是 `coco-maven-plugin` 所依赖的 Maven API 版本，不是对开发者的要求 | [`website/docs/getting-started.md`](https://github.com/patton174/coco-framework/blob/main/website/docs/getting-started.md)；`pom.xml`；`coco-build/coco-maven-plugin/pom.xml` |
| Git | 仓库文档未规定版本 | 无 |
| Node.js | 可选。渲染 README、运行 README 测试、构建官网时需要。官网要求 `>=20`，CI 的官网构建用 Node 20，静态分析里的 README 校验用 Node 24 | `website/package.json`；`.github/workflows/docs.yml`；`.github/workflows/reusable-static-analysis.yml` |
| Python | 可选。运行治理脚本测试时需要，CI 使用 3.12，依赖见 `.github/requirements/static-analysis.txt` | `.github/workflows/reusable-static-analysis.yml` |

只改 Java 代码时，JDK 21 与 Maven 就够了。

## 2. 克隆后的五步

```mermaid
flowchart LR
    s1["1 设置 JAVA_HOME 与 Path"] --> s2["2 确认 java 与 mvn 版本"]
    s2 --> s3["3 完整构建与验证"]
    s3 --> s4["4 单模块测试"]
    s4 --> s5["5 提交前检查工作区"]
```

### 第 1 步：设置 JDK 21

下面的 `<JDK21_HOME>` 是占位符，请换成你自己的 JDK 21 安装目录。

PowerShell（写法来自 `AGENTS.md` 与 `CONTRIBUTING.md`）：

```powershell
$env:JAVA_HOME='<JDK21_HOME>'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

bash（对应写法，仓库文档没有给出 bash 版本，这里按等价语义改写）：

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
```

### 第 2 步：确认实际使用的版本

```powershell
java -version   # 应为 21.x
mvn -v          # 输出里的 Java version 应是 JDK 21
```

终端默认的 `JAVA_HOME` 可能指向别的 JDK，此时直接运行 `mvn` 会用错版本，详见 [开发环境与常见陷阱](Dev-Environment)。

### 第 3 步：完整构建与验证

CI 在 ubuntu、windows、macos 三个平台上运行的是：

```powershell
mvn -B -ntp install
```

它会构建整个 Reactor，并执行 Failsafe 集成测试和 Boot 包裁剪冒烟。因为是 `install`，各模块的 `1.0.0-SNAPSHOT` 产物会被安装到本地 Maven 仓库，`coco-feature-archive-smoke` 例外，它设置了 `maven.install.skip=true`。

贡献指南给出的“正常完整验证”是：

```powershell
mvn -B clean verify
```

`AGENTS.md` 里也列出了更短的 `mvn verify`。三种写法执行的测试范围相同：Maven 生命周期中 `install` 在 `verify` 之后，只多出“把产物安装到本地仓库”这一步；`clean` 先清理 `target/`，避免旧产物干扰。提交 PR 前建议使用 `CONTRIBUTING.md` 的 `mvn -B clean verify`，需要与 CI 完全一致时使用 `mvn -B -ntp install`。

### 第 4 步：只验证改动的模块

```powershell
mvn -pl :module-artifact-id -am test
```

例如 `mvn -pl :coco-web -am test` 会构建并测试 `coco-web` 及其上游模块。涉及 Maven 插件时：

```powershell
mvn -B -pl :coco-maven-plugin -am verify
```

### 第 5 步：提交前检查工作区

```powershell
git status -sb
```

`AGENTS.md` 要求在报告完成前检查它，并且只在有意义时才提到未跟踪或被忽略的输出。

## 3. 命令速查

| 目的 | 命令 | 来源 |
| --- | --- | --- |
| 快速检查 POM 与 Enforcer 门禁，不编译 | `mvn -B -ntp -q validate` | 本页组合（Enforcer 绑定在 `validate` 阶段，见 `pom.xml`） |
| CI 同款完整验证 | `mvn -B -ntp install` | `.github/workflows/reusable-tests.yml`；[release.md](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/release.md) “本地验证” |
| 贡献前的完整验证 | `mvn -B clean verify` | [CONTRIBUTING.md](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md) |
| 单模块加上游依赖测试 | `mvn -pl :module-artifact-id -am test` | [AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) |
| 只编译某模块及其上游 | `mvn -B -ntp -pl :coco-spring-boot-autoconfigure -am -DskipTests clean compile` | 本页组合 |
| Maven 插件相关 Reactor 检查 | `mvn -B -pl :coco-maven-plugin -am verify` | [AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) “Reactor checks” |
| Boot 归档裁剪冒烟 | `mvn -B -pl :coco-feature-archive-smoke -am verify` | 本页组合：根 `pom.xml` 把 Failsafe 的 `integration-test` 与 `verify` 目标绑定到默认生命周期，`coco-support/coco-feature-archive-smoke/pom.xml` 给它传入 `coco.feature.archive.path`，所以运行到 `verify` 阶段即可触发；[release.md](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/release.md) 也说明它在 `mvn -B install` 的 Failsafe 阶段执行 |
| release profile 冒烟（无 GPG） | `mvn -B -Prelease -Drevision=1.0.0 -Dgpg.skip=true -DskipTests verify` | [AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)；[release.md](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/release.md) |
| 仅编译（CI 静态分析前置） | `mvn -B -ntp -DskipTests test-compile` | `.github/workflows/reusable-static-analysis.yml` |
| SpotBugs | `mvn -B -ntp spotbugs:check` | 同上；CI 中为 `continue-on-error`，只发布报告 |
| Checkstyle | `mvn -B -ntp checkstyle:check` | 同上；CI 中会阻塞 |
| 治理或工作流变更验证 | 见下方代码块 | [CONTRIBUTING.md](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md) |
| 重新生成 README | `node .github/readme/scripts/render.mjs --write`，再运行 `--check` | [AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) “README Maintenance” |
| 官网本地预览与构建 | 在 `website/` 下：`npm ci`、`npm start`、`npm run build`、`npm run typecheck` | `website/package.json`；`.github/workflows/docs.yml` |
| 同步代码图谱 | `codegraph sync .` | [AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)；[CONTRIBUTING.md](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md) |

治理或工作流变更时，`CONTRIBUTING.md` 列出的检查是：

```powershell
python .github/scripts/test_agent_review.py
python .github/scripts/test_auto_merge.py
python -m ruff check .github/scripts
python -m ruff format --check .github/scripts
node --test .github/readme/tests/readme.test.mjs
node .github/readme/scripts/render.mjs --check
```

CI 另外还会运行 `test_contributor_gate.py`、`test_verify_boot_archive_feature_coordinates.py`、`test_release_file_count_preflight.py` 以及 `actionlint`。

不要直接编辑 `README.md` 与 `README_CN.md`，它们由 `.github/readme/fragments/` 下的片段生成。

## 4. PowerShell 与 bash 对照

| 事项 | PowerShell | bash |
| --- | --- | --- |
| 设置环境变量 | `$env:JAVA_HOME='...'` | `export JAVA_HOME=...` |
| 修改 PATH | `$env:Path="$env:JAVA_HOME\bin;$env:Path"` | `export PATH="$JAVA_HOME/bin:$PATH"` |
| 路径分隔符 | `\`（多数命令也接受 `/`） | `/` |
| 带点号的 `-D` 参数 | 建议加单引号，如 `'-Drevision=2.0.1' '-Dgpg.skip=true' '-DskipTests'`（这是 `CONTRIBUTING.md` 的写法） | 通常不需要引号 |
| 命令连接 | 依赖关系用 `;` 或分步执行；`&&` 是否可用取决于 PowerShell 版本 | `&&` |
| 续行 | 反引号 `` ` `` | 反斜杠 `\` |
| 路径含空格 | 用引号包住整个路径 | 同左 |

关于 `-D` 参数：`AGENTS.md` 的示例没有加引号，`CONTRIBUTING.md` 加了单引号。在 PowerShell 里，未加引号且带点号的 `-Dxxx.yyy=zzz` 可能被拆开传给 Maven，所以加单引号更稳妥，两种写法在 bash 里等价。

## 5. Reactor 顺序与构建会生成什么

以 `-pl :coco-spring-boot-autoconfigure -am` 为例，Maven 会先构建它在 Reactor 内依赖的上游模块：`coco-api`、`coco-feature-model`、`coco-i18n`、`coco-context`、`coco-logging`（依赖见 `coco-spring/coco-spring-boot-autoconfigure/pom.xml`）。根 `pom.xml` 的 `<modules>` 里列出的是模块集合，实际构建顺序由依赖关系决定，以 Maven 输出的 Reactor Build Order 为准。模块清单见 [模块参考总览](Module-Overview)。

构建会在本地产生这些内容，它们都不应提交：

- 各模块的 `target/` 目录（`.gitignore` 忽略 `target/`）。
- 各模块的 `.flattened-pom.xml`（`.gitignore` 忽略；由 `flatten-maven-plugin` 在 `process-resources` 阶段生成，`clean` 阶段清理）。
- `coco-feature-archive-smoke` 会产出一个 Spring Boot 包，随后被 `coco-maven-plugin:prune-package` 处理。它的 Failsafe 集成测试读取系统属性 `coco.feature.archive.path`，校验包内的 Feature 清单与被裁剪的库。

静态分析工具 SpotBugs 与 Checkstyle 不绑定 Maven 生命周期，所以本地 `install` 和 `verify` 都不会运行它们。

## 6. 构建失败时先看哪里

下面是两类常见的本地构建失败，更完整的排查见 [FAQ 与故障排查](FAQ-Troubleshooting)。它们多由本地构建残留或工作区改动引起，不代表仓库 `main` 分支的状态。

| 现象 | 可能原因 | 先做什么 |
| --- | --- | --- |
| 某模块报 `InvalidConfigurationMetadataException`，提示 `META-INF/spring-configuration-metadata.json` 的 `End of input at character 0` | 该模块 `target/classes/META-INF/` 下的 `spring-configuration-metadata.json` 是 0 字节的残缺文件，常见于构建被中断之后 | 运行 `mvn clean`（或删除对应模块的 `target/`）后重新构建 |
| `javac` 报 `package ... does not match the expected package`、`class ... should be declared in a file named`，或出现重复类、找不到同模块中的类 | 工作区里有本地改动，使某个类的 `package` 声明与其所在目录不一致 | 先运行 `git status -sb` 与 `git diff` 检查本地改动，确认类的 `package` 声明与所在目录一致；确认是自己的临时改动后，用 `git stash` 暂存（或在不再需要时用 `git restore` 丢弃）再重试 |

另一个常见的环境问题是 shell 里的 `JAVA_HOME` 不是 JDK 21。构建前务必先完成第 2 步。

## 7. 运行：没有现成的 demo

框架本身不是可运行的应用，仓库里也没有可独立运行的 demo。

- 业务应用使用 `coco-parent` 加 `coco-spring-boot-starter` 之后，按普通 Spring Boot 方式运行，见官网的 [快速开始](https://github.com/patton174/coco-framework/blob/main/website/docs/getting-started.md)。
- 框架里“能启动”的只有两个集成测试夹具：`CocoStarterSmokeIT`（以 starter 默认值启动一个 Servlet 应用）和 `FeatureArchiveSmokeIT`（校验归档与裁剪），用 `mvn` 的 Failsafe 阶段执行。
- 业务与 HTTP 验收属于独立仓库 `coco-admin` 的 `framework-acceptance`。

## 相关页面

- [开发环境与常见陷阱（Windows / IDE / CodeGraph）](Dev-Environment)
- [FAQ 与故障排查](FAQ-Troubleshooting)
- [指南：测试约定](Guide-Testing)
- [指南：分支、PR、CI 与发布流程](Guide-Release-And-Workflow)
- [项目总览](Project-Overview)
- [Wiki 首页](Home)
