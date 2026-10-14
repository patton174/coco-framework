# FAQ 与故障排查

本页回答：构建、运行、Feature 开关和协作流程里最常遇到的问题，每条按“现象 → 原因 → 处理”给出，并附可复制的命令。

> 内容对应仓库 `main` 分支。命令来自 `AGENTS.md`、`CONTRIBUTING.md`、CI 配置或源码；没有证据支持的原因不下结论，只给出现象和处理办法。文档与实现不一致的地方以“文档与实现存在出入”开头说明，并给出权威来源。

## 目录

| 类别 | 问题 |
| --- | --- |
| 环境与构建 | 1 默认 JDK 不是 21；2 `InvalidConfigurationMetadataException`；3 `package` 声明与目录不一致、重复类；4 根目录出现 `hs_err_pid*.log`；5 中文乱码；6 IDE 导入 `${revision}` 问题；7 `target/` 与 `.flattened-pom.xml` 要不要提交 |
| Feature 与装配 | 8 如何禁用某个 Feature；9 Feature 已启用却没效果；10 starter 里找不到 `coco-rate-limit` / `coco-idempotency`；11 旧坐标 `coco-config` 等是什么；12 Redis 缺依赖启动失败；13 `coco.security.web.header` 的 WARN；14 自动配置不生效怎么排查 |
| 流程与文档 | 15 README 为什么不能直接改；16 PR 被 `Promotion gate` / `Contributor gate` 拒绝；17 三类文档放什么、在哪改；18 框架里怎么“运行”一个应用 |

---

## 一、环境与构建

### 1. 默认 JDK 不是 21

- **现象**：在新开的终端里直接 `mvn ...`，编译报与 Java 版本相关的错误，或 `mvn -v` 显示的不是 JDK 21。具体报错文案因 JDK 与插件版本而异。
- **原因**：项目要求用 JDK 21 构建，编译目标是 Java 17（`pom.xml` 的 `java.version=17`，`<release>17</release>`；`AGENTS.md`“Verification”）。系统默认的 `JAVA_HOME` 指向的不是 JDK 21 时，直接 `mvn` 就会用错 JDK。这是环境配置问题，不是项目缺陷。
- **处理**：先自检，再按需设置（把 `<JDK21_HOME>` 换成你自己的 JDK 21 安装目录）：

```powershell
$env:JAVA_HOME='<JDK21_HOME>'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version   # 应为 21.x
mvn -v          # 确认 "Java version" 与 "runtime" 都是 JDK 21
```

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
java -version && mvn -v
```

### 2. `InvalidConfigurationMetadataException ... End of input at character 0`

- **现象**：构建时注解处理阶段报
  `Invalid additional meta-data in 'META-INF/spring-configuration-metadata.json': End of input at character 0`。
- **原因**：`target/classes/META-INF/spring-configuration-metadata.json` 是 0 字节的残缺文件。构建被中断后可能留下这类产物，导致后续构建或运行期读取它时失败。
- **处理**：先找出 0 字节文件（只读检查），再对相关模块 `clean` 后重建；对其中一个模块执行 `clean compile` 后即可恢复：

```powershell
# 只读：列出 target 下 0 字节的配置元数据文件
Get-ChildItem -Recurse -File -Filter spring-configuration-metadata.json |
  Where-Object { $_.Length -eq 0 -and $_.FullName -match '\\target\\' } |
  ForEach-Object { $_.FullName }

# 清理并重新编译某模块及其上游（-am）
mvn -B -ntp -pl :coco-spring-boot-autoconfigure -am -DskipTests clean compile
```

`target/` 已被 `.gitignore` 忽略，`clean` 只影响构建产物。

### 3. `package` 声明与目录不一致、重复类

- **现象**：`coco-spring-boot-autoconfigure` 等模块编译失败，`javac` 报 `package ... does not match the expected package`、`class ... should be declared in a file named`、重复类，或某个类无法访问同包里的类型（例如 `CocoConfigAutoConfiguration` 找不到 `io.github.coco.config.CocoProperties`、`CocoFeatureProperties`）。
- **原因**：源文件的 `package` 声明与它所在的目录不一致。`coco-spring-boot-autoconfigure` 里 `io/github/coco/config/` 目录下的类应声明 `package io.github.coco.config;`，兼容探针 `CocoConfigCompatibilityCompileProbe` 也依赖这个包名。常见来源是你工作区里尚未合并的本地改动或冲突解决。
- **处理**：先判断是不是你工作区里的本地改动：

```powershell
git status -sb
git diff -- <出错源文件的路径>
```

确认每个类的 `package` 声明与所在目录一致；如果是你自己的改动造成的，把 `package` 改回与目录一致，或用 `git stash` 暂存改动后重试。只有确认不需要保留这些改动时才使用 `git restore`。**别在不相关的 PR 里顺手改**（`AGENTS.md`：保持改动范围、不引入无关重构）。

### 4. 仓库根目录出现 `hs_err_pid*.log` / `replay_pid*.log`

- **它们是什么**：HotSpot 在 JVM 崩溃时写到**当前工作目录**的诊断文件。`hs_err_pid<pid>.log` 是致命错误报告；`replay_pid<pid>.log` 是 JIT 编译器崩溃时写出的编译重放文件。所以，从仓库根目录启动、随后崩溃的 JVM，会把它们留在根目录。
- **它们不会被提交**：`.gitignore` 里有 `*.log`。可以用下面的只读命令确认：

```powershell
git check-ignore -v hs_err_pid<pid>.log        # 显示命中的 .gitignore 规则
Get-ChildItem hs_err_pid*.log, replay_pid*.log | Select-Object Name, Length, LastWriteTime
```

- **怎么办**：
  1. 不要提交它们；保持原样，由持有该工作区的人决定如何处理。
  2. 先确认你用的是 JDK 21（第 1 条），再对相关模块 `clean` 后重跑（第 2 条的命令）。
  3. 如果问题可以复现，保留**第一份非空**的 `hs_err_pid*.log`，连同 JDK 版本、操作系统、复现命令一起开 Issue（Bug Report 表单，见 `SUPPORT.md`）。
  4. 提交 Issue 前先检查日志里是否含有本地路径、用户名、环境变量等敏感信息，必要时脱敏。

### 5. Windows 控制台里的中文乱码

- **现象**：Maven 输出或 `git` 输出里的中文显示成乱码，例如编译错误信息读不懂。
- **原因**：控制台编码（常见为 GBK）与 UTF-8 不一致，这是**显示问题**；源码编码是 UTF-8（`project.build.sourceEncoding=UTF-8`）。
- **处理**：改用 UTF-8 终端，或在会话里切换代码页：

```powershell
chcp 65001
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
```

- **区分“显示乱码”和“文件本身有损坏字符”**：用 UTF-8 编辑器打开文件仍然看到方块或问号形状的替换字符（Unicode U+FFFD），说明字节本身就带有替换字符。`main` 分支上有 27 个已提交的 `.java` 文件含这类字符，命中的行全部是注释行（JavaDoc 或行注释），例如 `coco-features/coco-web/src/main/java/io/github/coco/feature/web/replay/CocoReplayFilter.java`。不要在无关 PR 里批量“修复”这些字符。检查命令：

```powershell
git grep -l -P '(*UTF8)\x{FFFD}' HEAD -- '*.java'
```

- **行尾**：`.gitattributes` 为 `* text=auto eol=lf`、`*.bat text eol=crlf`，仓库文本文件以 LF 为准。Windows 上不要让编辑器批量转成 CRLF，以免产生格式噪音。

### 6. IDE 导入后提示 `${revision}` 无法解析

- **现象**：导入根 `pom.xml` 后，模块版本显示为未解析的 `${revision}` 或依赖红线。
- **背景**：本地默认版本是 `1.0.0-SNAPSHOT`，各模块的 `.flattened-pom.xml` 由 `flatten-maven-plugin` 在 `process-resources` 阶段生成。
- **处理**：如果 IDE 仍然无法解析，先在命令行构建一次：

```powershell
mvn -B -ntp install -DskipTests
```

### 7. `target/`、`.flattened-pom.xml` 要不要提交

不要。`.gitignore` 已忽略 `target/`、`.flattened-pom.xml`、`.codegraph/` 和 `*.log`。`AGENTS.md` 要求不把无关重构、格式化和生成的构建产物带进提交。提交前运行 `git status -sb` 自检。

---

## 二、Feature 与装配

### 8. 如何单独禁用某个 Feature

三种声明方式（来源：`website/docs/feature-toggles.md`、`CocoFeaturesMojo` 的参数）：

```yaml
# application.yml：业务应用
coco:
  features:
    disabled:
      - mybatis-plus
      - tenant
      - data-permission
```

```java
// 代码声明：同一配置源里同时启用和禁用同一个 Feature 时，禁用优先
@CocoFeatures(disabled = {CocoFeature.TENANT, CocoFeature.DATA_PERMISSION})
@Configuration(proxyBeanMethods = false)
class ApplicationCocoConfiguration {
}
```

```powershell
# Maven 命令行覆盖（逗号分隔的 id）
mvn -B -ntp '-Dcoco.features.disabled=tenant,data-permission' package
```

要点：

- **依赖传播**：禁用被依赖的 Feature，依赖它的会被一并禁用。例如禁用 `mybatis-plus` 会连带禁用 `tenant`、`data-permission`；`tenant`、`data-permission` 还依赖 `security`，`openapi` 依赖 `web` 与 `security`，`rate-limit`、`idempotency` 依赖 `web`。
- **构建期优先**：`coco-maven-plugin` 的 `features` goal 会写出 `META-INF/coco/features.json`；运行期只要 classpath 上有它，就直接据此还原计划，不再合并业务配置。所以**修改 YAML 后要重新构建**，只改运行时配置可能不生效。
- **构建期的选择来源优先级（低 → 高）**：`src/main/resources` 下的 `application.yml` / `application.yaml` / `application.properties` → Maven 参数 → 带 `@CocoFeatures` 的类。
- **跳过装配**：`-Dcoco.features.skip=true` 同时影响 `features` 与 `prune-package` 两个 goal。

细节见 [Feature 机制：构建期与运行期](Feature-System)。

### 9. Feature 已启用，但看起来没效果

- **原因**：有“两层开关”。`coco.features.*` 决定是否装配；每个模块还有自己的 `enabled` 属性，决定装配后是否生效，且默认值不同：
  - 默认**关闭**：`coco.rate-limit.enabled`、`coco.idempotency.enabled`、`coco.lock.enabled`、`coco.storage.enabled`、`coco.cache.enabled`、`coco.notification.enabled`、`coco.captcha.enabled`，以及 `coco.data-permission.sql.enabled`。
  - 默认**开启**：`coco.audit.enabled`、`coco.openapi.enabled`、`coco.scheduling.enabled`、`coco.messaging.enabled`、`coco.security.web.enabled`、`coco.tenant.sql.enabled`、`coco.mybatis-plus.pagination.enabled`。
- **处理**：打开对应模块的 `*Properties.java` 核对键名与默认值（路径见 [速查](Where-To-Find-Things)）。
- **与 `@ConditionalOnCocoFeature` 的关系**：`lock`、`storage`、`messaging`、`cache`、`notification`、`captcha`、`scheduling` 这 7 个模块的自动配置**没有** `@ConditionalOnCocoFeature`，因此 `coco.features.disabled` 对它们在构建期影响依赖装配、裁剪与清单，运行期开关靠各自的 `coco.<feature>.enabled`。设计意图未见文档说明，行为以各模块自动配置类源码为准。

### 10. 为什么 starter 里找不到 `coco-rate-limit` / `coco-idempotency`

- **事实**：`coco-spring-boot-starter` 的 `pom.xml` 没有直接依赖这两个模块，测试 `CocoRateLimitStarterDependencyContractTest` 明确断言了这一点。它们由 `coco:features` 在构建期按 Feature 计划补加（`CocoFeaturesMojo.applyFeatureDependencies` 为每个启用的 Feature 补 `runtime` 依赖，已存在则跳过）。
- **处理**：业务应用使用 `coco-parent` 即可，构建会自动装配。`coco:features` 与 `prune-package` 的执行绑定在 `coco-parent` 里，BOM（`coco-dependencies`）只管理依赖版本、不包含 `coco-maven-plugin`（见 [构建模块](Module-Build)），所以只导入 BOM 而不使用 `coco-parent` 时，`coco:features` 不会自动运行，需要自己显式依赖这两个模块（BOM 管理它们的版本）。
- **原则**：starter 只组合能力、不承载行为（`AGENTS.md`）。

### 11. `coco-config`、`coco-feature-runtime`、`coco-feature-*` 旧坐标是什么

- **是什么**：它们是 2.x 已发布的旧 Maven 坐标。现在这些实现已并入 `coco-spring-boot-autoconfigure` 或改名为 `coco-web`、`coco-mybatis-plus` 等规范制品；旧坐标只以**无源码兼容门面**的形式留在 `coco-build/coco-compatibility/` 下，每个门面只依赖对应的规范制品。
- **为什么框架模块不能依赖它们**：门面的存在是为了让下游业务项目的旧依赖继续可解析，不是新的依赖入口；`AGENTS.md` 明确“no framework module may depend on them”。目前只有 starter 的 test 作用域会用到 `coco-config`、`coco-feature-runtime`（用于验证兼容）。
- **改动时注意**：不要往门面里放实现、自动配置导入、`spring.factories` 或消息资源；类型被移动或改名时，`*CompatibilityCompileProbe` 会让构建失败。
- **包名不等于所在 jar**：`coco-spring-boot-autoconfigure` 里仍保留 `io.github.coco.config`、`io.github.coco.feature.runtime.*` 等旧包名（迁移期保留 FQCN），这不代表它们还在旧 jar 里。

### 12. 配置了 Redis 存储却启动失败

- **现象**：启动时抛 `BeanCreationException`（cache 为 `IllegalStateException`），消息形如 `coco.lock.store-type=redis requires org.springframework.data.redis.core.StringRedisTemplate on the runtime classpath`。
- **原因**：这是**有意的 fail-fast**。`coco-lock`、`coco-cache`、`coco-captcha` 各有一个 `Coco<X>RedisMissingDependencyAutoConfiguration`，当模块已启用（`enabled=true`）、`store-type` 指向 Redis（cache 为 `two-level`）、classpath 上没有 `StringRedisTemplate` 时，就让应用启动失败，而不是静默退回内存实现。lock 与 captcha 的该配置还带有 `@ConditionalOnMissingBean`（业务自己提供 Store Bean 时不触发）；cache 的配置类上未见这个条件。
- **处理**（按情况选择）：
  1. 给应用加上 Spring Data Redis 依赖（`spring-boot-starter-data-redis`）；
  2. lock / captcha 可以改为提供自己的 `Coco<X>Store` Bean；
  3. 把 `store-type` 改回默认值（lock / captcha 为 `IN_MEMORY`，cache 为 `LOCAL`）。
- **例外**：`coco-rate-limit`、`coco-idempotency` 的 Redis 存储走 `Coco<X>RedisAutoConfiguration`，没有 `*RedisMissingDependency*` 变体。缺 `StringRedisTemplate` 类时，该 Redis 自动配置因 `@ConditionalOnClass(StringRedisTemplate.class)` 被跳过；而内存存储 Bean 只在 `store-type` 缺省或为 `in-memory` 时创建，所以 `store-type=redis` 时容器里没有对应的 Store Bean。按源码推导，依赖它的 Bean（rate-limit 的 `CocoRateLimitRequestHandler`、idempotency 的 `cocoIdempotencyMvcConfigurer`）会因缺少依赖而无法创建，报错信息不同于上面的 fail-fast 文案。
- **`store-type` 的写法**：条件注解按小写连字符字面量比较（`in-memory`、`redis`、`two-level`），`IN_MEMORY` 这类枚举常量写法不会匹配，详见 [Module-Features-Infrastructure](Module-Features-Infrastructure)。

### 13. 开启 `coco.security.web.header` 后出现 WARN

- **含义**：`coco-security` 的 `HeaderCocoWebSecurityContextResolver` 从上游 HTTP 头（默认 `X-Coco-Principal-Id`、`X-Coco-Principal-Name`、`X-Coco-Roles`、`X-Coco-Permissions`）读取身份，也就是**信任上游**。日志 WARN 在提醒：不要把它直接暴露到公网，必须放在可信网关之后。
- **处理**：要么确保只有可信网关能写这些头；要么提供自己的 `CocoWebSecurityContextResolver` Bean，WARN 随之消失。该 Header 解析默认关闭（`coco.security.web.header.enabled=false`）。
- **注意**：`coco-security` 是**安全上下文**，不是认证实现；认证选择属于下游应用（`AGENTS.md`）。

### 14. 自动配置“没生效”，按这个顺序排查

1. **Feature 是否被禁用**：先看产物里的 `META-INF/coco/features.json`；它存在时优先于运行期属性。
2. **是否被 `AutoConfigurationImportFilter` 过滤**：`CocoFeatureAutoConfigurationImportFilter` 在 `mybatis-plus` 未启用时会过滤 MyBatis-Plus 的自动配置，并在满足条件时过滤 Spring Boot 的数据源自动配置。
3. **模块级条件属性**：`coco.<feature>.enabled` 等（见第 9 条）。
4. **classpath 与条件注解**：`@ConditionalOnClass`、`@ConditionalOnWebApplication(type = SERVLET)`、`@ConditionalOnMissingBean` 是否满足；`tenant`、`data-permission` 对 `coco-mybatis-plus` 是 optional 依赖。
5. **注册文件**：对应模块的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 是否列出了该自动配置。
6. **条件评估报告**：用 Spring Boot 通用手段（`--debug` 或 `debug=true`）查看条件评估，这是通用 Spring Boot 知识，不是 Coco 专有功能。

更多见 [指南：新增或调整自动配置](Guide-Add-Auto-Configuration)。

---

## 三、流程与文档

### 15. README 为什么不能直接改

`README.md` 与 `README_CN.md` 是由 `.github/readme/fragments/` 下的片段确定性生成的文档（`AGENTS.md`“README Maintenance”）。直接编辑会在 `render.mjs --check` 与 CI 的 README 漂移校验里失败。正确做法：

```powershell
# 1) 修改成对的中英文片段：.github/readme/fragments/en 与 .github/readme/fragments/zh-CN
# 2) 重新生成
node .github/readme/scripts/render.mjs --write
# 3) 校验
node .github/readme/scripts/render.mjs --check
```

Stars、forks、贡献者数据和时间戳由脚本维护，不要由模型生成。

### 16. PR 被 `Promotion gate` / `Contributor gate` 拒绝

| 现象 | 常见原因 | 处理 |
| --- | --- | --- |
| `Promotion gate` 失败 | PR 的目标是 `main`，但来源不是 `dev` / 发起者不是所有者 / `dev` 落后于 `main` | 普通改动请向 `dev` 提 PR；`main` 只由所有者晋升 |
| `Contributor gate` 失败 | 分支名不是 `dev-<你的 GitHub 用户名>`（后缀必须是本人登录名）/ 作者没有写权限 / 来自 fork | 从最新 `dev` 重建正确命名的分支；不接受 fork PR |
| `Agent issue gate` 失败 | 存在带 `coco-agent-review` 标记的未关闭绑定 Issue | 处理该发现，等待后续绑定评审或维护者关闭 |

维护者 / Agent 的改动走 agent 分支和 `Open Agent Pull Request` 工作流，规则与两份文档之间的文字出入见 [分支、PR、CI 与发布流程](Guide-Release-And-Workflow)。

### 17. 三类文档分别放什么、在哪改

| 文档 | 位置 | 面向谁 / 放什么 |
| --- | --- | --- |
| 官网用户文档 | `website/docs/`（中文），英文版在 `website/i18n/en/docusaurus-plugin-content-docs/current/`；由 `.github/workflows/docs.yml` 发布到 `https://cocoframework.dev/` | 业务使用者：快速开始、特性开关、各 Feature 用法 |
| 仓库架构 / 发布 / 审计 / 规格文档 | `coco-support/coco-document/`（`architecture/module-layout.md`、`release.md`、`superpowers/{audits,plans,specs}`） | 治理与设计权威来源，`AGENTS.md` 指定放这类文档 |
| 开发者 Wiki | 主仓库 `docs/wiki/`，发布到 GitHub Wiki | 框架开发者 / 贡献者 / AI Agent 的导读，**链接**上面两类权威文档而不复制 |

Wiki 与权威文档冲突时，以 `AGENTS.md`、`CONTRIBUTING.md`、`GOVERNANCE.md`、源码和 `pom.xml` 为准，先改源文档再同步 Wiki。本 Wiki 的事实源是主仓库的 `docs/wiki/`，发布到 GitHub Wiki 由维护者手动完成，详见 [Guide-Contribute-To-Wiki](Guide-Contribute-To-Wiki)。

### 18. 框架里怎么“运行”一个应用

框架本身不是可运行应用，仓库里也没有可独立运行的 demo。能“跑起来”的只有两个测试夹具：`CocoStarterSmokeIT`（以 starter 默认值启动 Servlet 应用）和 `coco-feature-archive-smoke`（校验 Boot 归档裁剪）。业务 / HTTP 验收由独立仓库 `coco-admin` 的 `framework-acceptance` 负责。

要体验框架，请按 [官网快速开始](https://github.com/patton174/coco-framework/blob/main/website/docs/getting-started.md) 新建业务应用：`coco-parent` 作父 POM、依赖 `coco-spring-boot-starter`，然后在**那个业务应用**里 `mvn spring-boot:run`。不要在框架仓库根目录找 `spring-boot:run`。

---

## 相关页面

- [快速开始：构建、测试与验证](Getting-Started)
- [开发环境与常见陷阱](Dev-Environment)
- [指南：测试约定](Guide-Testing)
- [指南：分支、PR、CI 与发布流程](Guide-Release-And-Workflow)
- [Feature 机制：构建期与运行期](Feature-System)
- [术语表](Glossary)
- [速查：开发任务到文件/目录](Where-To-Find-Things)
- 仓库文档：[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)、[CONTRIBUTING.md](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md)、[SUPPORT.md](https://github.com/patton174/coco-framework/blob/main/SUPPORT.md)
