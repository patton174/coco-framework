# 开发环境与常见陷阱（Windows / IDE / CodeGraph）

> 本页回答：本地开发环境要怎么配、在 Windows 与 IDE 上有哪些容易踩的坑、CodeGraph 怎么用、仓库里哪些本地目录不属于框架源码。

构建与测试命令见 [快速开始](Getting-Started)。排障条目见 [FAQ 与故障排查](FAQ-Troubleshooting)。

## 1. JDK 与 Maven 自检

项目要求 JDK 21 开发，Maven 编译目标是 Java 17（[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) “Verification”）。每次开新终端，先做这份清单：

| 检查项 | 命令 | 期望 |
| --- | --- | --- |
| 当前 shell 的 JDK | `java -version` | 21.x |
| Maven 实际使用的 JDK | `mvn -v` | 输出中的 `Java version` 是 21.x，`runtime` 指向你的 JDK 21 目录 |
| `JAVA_HOME` | PowerShell：`$env:JAVA_HOME`；bash：`echo "$JAVA_HOME"` | 指向 JDK 21 |
| POM 与 Enforcer 门禁 | `mvn -B -ntp -q validate` | 退出码 0 |

设置方式（`<JDK21_HOME>` 是占位符，请换成你自己的 JDK 21 安装目录）：

```powershell
$env:JAVA_HOME='<JDK21_HOME>'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -v
```

两点提醒：

- 终端默认的 `JAVA_HOME` 可能指向别的 JDK（例如系统环境变量里仍是 JDK 8），新开的 shell 里直接运行 `mvn` 会用错 JDK。请养成先看 `mvn -v` 的习惯。
- 在 Windows PowerShell 里，`java -version 2>&1` 会把 `java` 写到 stderr 的版本信息显示成红色的 `NativeCommandError`。这只是 PowerShell 对 stderr 的呈现方式，不表示命令失败；直接运行 `java -version` 即可。

Maven 版本：业务应用侧要求 3.8.9+。本仓库自身没有 Maven 版本下限：根 `pom.xml` 没有 Maven 版本的 Enforcer 规则。属性 `maven.version=3.9.16` 只用于 `coco-build/coco-maven-plugin/pom.xml` 中对 `maven-plugin-api` 与 `maven-core` 的 `provided` 依赖版本，用来编译 Maven 插件，不是对开发者所用 Maven 版本的要求。

## 2. Windows 注意点

### PowerShell 引号与参数

- 带点号的 `-D` 参数建议加单引号，如 `'-Drevision=2.0.1' '-Dgpg.skip=true' '-DskipTests'`，这是 [CONTRIBUTING.md](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md) 的写法。[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 的示例没有加引号；加单引号在 PowerShell 中更稳妥，在 bash 中等价。
- 路径含空格时要用引号包住整个路径，例如 Windows 的 `Program Files` 目录。
- PowerShell 用 `$env:NAME=...` 设置环境变量，bash 用 `export NAME=...`。完整对照表见 [快速开始](Getting-Started)。

### UTF-8 与控制台乱码

- 项目源码编码是 UTF-8（根 `pom.xml` 的 `project.build.sourceEncoding=UTF-8`）。
- 中文 Windows 的控制台常用 GBK。Maven 的中文输出（包括编译错误信息）以及部分工具的输出，在这样的控制台里可能显示成乱码。这是显示问题，不表示文件损坏。
- 可以改用 UTF-8 终端，例如在 PowerShell 里先执行 `chcp 65001`。
- 用编辑器直接打开文件，可以判断文件本身有没有乱码。

### 行尾

`.gitattributes` 内容是：

```text
* text=auto eol=lf
*.bat text eol=crlf
```

也就是说，仓库里的文本文件以 LF 为准，只有 `.bat` 使用 CRLF。在 Windows 上请不要让编辑器批量把文件转成 CRLF，那会制造大量格式噪音，而 [AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 要求不引入无关的格式化改动。

## 3. IDE

| 事项 | 说明 | 来源 |
| --- | --- | --- |
| 导入方式 | 按根 `pom.xml` 以 Maven 项目导入 | 根 `pom.xml` 的 `<modules>` |
| 被忽略的本地状态 | `.idea/`、`.vscode/`、`*.iml` 被 `.gitignore` 忽略，不要提交 | `.gitignore` |
| 注解处理器 | 父 POM 与各模块里没有配置 Lombok 或 MapStruct，也没有 `annotationProcessorPaths`；根 `pom.xml` 中提到 Lombok、MapStruct 的只是一段关于 SpotBugs 后续阶段的注释 | `pom.xml` |
| 配置元数据 | `coco-features` 下除 `coco-scheduling` 外的 Feature 模块，以及 `coco-i18n`、`coco-logging`、`coco-spring-boot-autoconfigure`，依赖 `spring-boot-configuration-processor` 生成 `META-INF/spring-configuration-metadata.json`，IDE 的属性提示依赖它 | 各模块 `pom.xml` |
| 编译参数 | `maven-compiler-plugin` 开启 `<parameters>true</parameters>` | `pom.xml` |

### 版本号 `${revision}` 与 flatten

本地默认版本是 `1.0.0-SNAPSHOT`，由根 `pom.xml` 的属性 `revision` 提供，各模块用 `flatten-maven-plugin`（`resolveCiFriendliesOnly`）在 `process-resources` 阶段生成 `.flattened-pom.xml`。

如果 IDE 导入后提示版本无法解析，可以先在命令行执行一次 `mvn -B -ntp install -DskipTests`（PowerShell 下建议写成 `mvn -B -ntp install '-DskipTests'`）。这只是遇到该问题时可选的排障做法，正常导入不需要预先执行。

### 全仓库搜索时的干扰目录

`.worktrees/` 与 `worktrees/` 下可能放着 Git worktree，也就是完整的仓库副本，里面也有 `pom.xml` 与源码。它们被 `.gitignore` 忽略，但 IDE 或全局文本搜索会重复命中其中的文件。做搜索和重构时请把这两个目录排除在外。

## 4. CodeGraph

CodeGraph 的索引放在仓库根目录的 `.codegraph/`（被 `.gitignore` 忽略，内含索引数据库）。相关规则来自 [AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 与 [CONTRIBUTING.md](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md)：

- 存在 `.codegraph/` 时，先用 CodeGraph 理解和定位代码，再考虑用 grep、find 或大范围读文件。
- 改完源码后执行 `codegraph sync .`，让本地代码图谱保持最新，再继续后续探索。

`codegraph --help` 列出的常用子命令：

| 子命令 | 用途 |
| --- | --- |
| `codegraph sync .` | 同步自上次索引以来的改动 |
| `codegraph status` | 查看索引状态与统计 |
| `codegraph query <search>` | 按名称搜索符号 |
| `codegraph explore <query...>` | 一次返回相关符号源码与调用路径 |
| `codegraph node <name>` | 查看单个符号的源码与调用者、被调用者 |
| `codegraph files` | 查看索引中的项目文件结构 |
| `codegraph callers <symbol>` / `codegraph callees <symbol>` | 查找调用某符号的位置 / 某符号调用的位置 |

CodeGraph CLI 通过 Node 全局安装。如果命令找不到，先检查 Node 的全局目录是否在 `Path` 里。

给 AI Agent 的补充：

- 先读 [AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)，再读 [约定与规范](Conventions)。
- 想知道“改某件事该去哪个文件”，先看 [速查：开发任务到文件/目录](Where-To-Find-Things)。
- 保持改动范围与请求一致，不夹带无关重构，也不提交生成的构建产物。

## 5. 仓库里的本地杂项目录

这些目录可能出现在你的工作区里，但**不属于框架源码**，Wiki 不展开介绍它们的内容：

| 路径 | 状态 | 说明 |
| --- | --- | --- |
| `.worktrees/`、`worktrees/` | 被 `.gitignore` 忽略 | 本地 Git worktree |
| `.idea/`、`.vscode/` | 被 `.gitignore` 忽略 | IDE 本地状态 |
| `.codegraph/` | 被 `.gitignore` 忽略 | CodeGraph 索引 |
| 编辑器、AI 工具或其他本地工具创建的目录与脚本 | 可能未被 `.gitignore` 忽略，在 `git status` 中显示为未跟踪 | 与框架源码无关，不要提交 |

提交时用 `git add <具体文件>` 而不是 `git add .`，避免把这些杂项带进提交。

## 6. 根目录里的 `hs_err_pid*.log` 与 `replay_pid*.log`

`hs_err_pid*.log` 与 `replay_pid*.log` 是 JVM 崩溃时写到**当前工作目录**的诊断文件，所以它们会出现在启动 JVM 的目录里，包括仓库根目录。

- 它们被 `.gitignore` 的 `*.log` 忽略，不会被提交，也不要把它们加进提交。
- 如果问题可复现，保留第一份非空的 `hs_err_pid*.log`，连同 JDK 版本、操作系统和复现命令提 Issue；提交前请脱敏其中的路径与用户名。
- 排查建议见 [FAQ 与故障排查](FAQ-Troubleshooting)。

## 相关页面

- [快速开始：构建、测试与验证](Getting-Started)
- [FAQ 与故障排查](FAQ-Troubleshooting)
- [约定与规范](Conventions)
- [速查：开发任务到文件/目录](Where-To-Find-Things)
- [项目总览](Project-Overview)
- [Wiki 首页](Home)
