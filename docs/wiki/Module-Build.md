# 构建模块：BOM、父 POM、Maven 插件与兼容门面

> 本页回答：`coco-build/` 下的 4 类制品（BOM、父 POM、Maven 插件、兼容门面）各自负责什么、怎样被业务应用使用、有哪些参数和扩展点，以及根 `pom.xml` 提供了哪些构建基础设施。
> 依据：仓库 `main` 分支已提交的 `pom.xml` 与 `src/main/java` 源码；文档与实现不一致的地方以“文档与实现存在出入”开头说明，并给出权威来源。

## 1. 目录与制品一览

| 路径 | artifactId | 打包 | 一句话职责（取自 pom `<description>`） |
| --- | --- | --- | --- |
| `coco-build/coco-dependencies` | `coco-dependencies` | pom | 依赖管理 BOM |
| `coco-build/coco-parent` | `coco-parent` | pom | 业务应用推荐父 POM |
| `coco-build/coco-maven-plugin` | `coco-maven-plugin` | maven-plugin | 构建期 Feature 装配与包裁剪 |
| `coco-build/coco-compatibility/*`（9 个） | `coco-config`、`coco-feature-runtime`、`coco-feature-mybatis-plus`、`coco-feature-audit`、`coco-feature-openapi`、`coco-feature-tenant`、`coco-feature-data-permission`、`coco-feature-security`、`coco-feature-web` | jar | 已发布旧坐标的无源码兼容门面 |

`coco-build` 的职责边界见 [`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 与 [`module-layout.md`](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/architecture/module-layout.md)：依赖管理、推荐父 POM、构建期 Feature 清单、打包裁剪、以及 2.x 旧坐标的发布兼容。

## 2. coco-dependencies（BOM）

**路径**：`coco-build/coco-dependencies/pom.xml`，打包 `pom`，父 POM 是根 `coco-framework`。

**作用**：业务应用通过 `dependencyManagement` 导入后即可省略所有 Coco 模块的版本号。

**管理的内容**（`<dependencyManagement>` 实读）：

| 分组 | 条目 |
| --- | --- |
| 第三方 BOM | `org.springframework.boot:spring-boot-dependencies:${spring-boot.version}`（`type=pom`、`scope=import`） |
| foundation | `coco-api`、`coco-context`、`coco-exception`、`coco-i18n`、`coco-logging`、`coco-feature-model` |
| spring | `coco-spring-boot-starter`、`coco-spring-boot-autoconfigure` |
| features（规范坐标） | `coco-web`、`coco-mybatis-plus`、`coco-audit`、`coco-security`、`coco-tenant`、`coco-data-permission`、`coco-openapi`、`coco-rate-limit`、`coco-idempotency`、`coco-scheduling`、`coco-lock`、`coco-storage`、`coco-messaging`、`coco-cache`、`coco-captcha`、`coco-notification` |
| 旧坐标（兼容） | `coco-config`、`coco-feature-runtime`、`coco-feature-web`、`coco-feature-mybatis-plus`、`coco-feature-audit`、`coco-feature-security`、`coco-feature-tenant`、`coco-feature-data-permission`、`coco-feature-openapi` |
| 测试支持 | `coco-test` |
| 第三方 | `com.baomidou:mybatis-plus-spring-boot4-starter`、`com.baomidou:mybatis-plus-jsqlparser-4.9`（版本均为 `${mybatis-plus.version}`） |

版本号统一为 `${project.version}`。BOM **不包含** `coco-maven-plugin`、`coco-parent`、`coco-dependencies` 自身；插件版本由 `coco-parent` 的 `pluginManagement` 管理（见下节）。

> 与根 `pom.xml` 的差异：根 `pom.xml` 的 `dependencyManagement` 只登记了部分 Coco 模块，缺少 `coco-rate-limit`、`coco-scheduling`、`coco-lock`、`coco-storage`、`coco-messaging`、`coco-cache`、`coco-captcha`、`coco-notification`；BOM 则管理全部。业务侧使用的是 BOM，所以新增 Feature 模块必须登记到 BOM（见 [指南：新增一个 Feature 模块](Guide-Add-Feature-Module)）。根 `pom.xml` 是否同样登记，现有模块没有形成统一做法，设计意图未见文档说明，行为以根 `pom.xml` 与 BOM 的实际内容为准。

## 3. coco-parent（业务应用父 POM）

**路径**：`coco-build/coco-parent/pom.xml`。父 POM 是根 `coco-framework`（`relativePath=../../pom.xml`），所以业务应用继承它也就继承了根 pom 的构建配置，但可通过属性关闭 Enforcer。

| 配置 | 内容 |
| --- | --- |
| `<properties>` | `coco.version=${revision}`、`coco.enforcer.skip=true`（业务应用不继承仓库的 Enforcer 门禁） |
| `dependencyManagement` | 导入 `coco-dependencies:${coco.version}`（`type=pom`、`scope=import`） |
| `pluginManagement` | 固定 `coco-maven-plugin:${coco.version}`（`featureVersion=${coco.version}`）与 `spring-boot-maven-plugin:${spring-boot.version}` |
| `build/plugins` | 三个 execution（见下表） |

| execution id | 插件 / goal | 默认阶段（来自 Mojo 或插件） |
| --- | --- | --- |
| `spring-boot-repackage` | `spring-boot-maven-plugin:repackage` | `package` |
| `coco-feature-assembly` | `coco-maven-plugin:features` | `process-classes`（`@Mojo.defaultPhase`） |
| `coco-package-prune` | `coco-maven-plugin:prune-package` | `package`（`@Mojo.defaultPhase`） |

`repackage` 与 `prune-package` 同在 `package` 阶段，按 POM 中声明顺序执行：`spring-boot-maven-plugin` 先于 `coco-maven-plugin`，所以裁剪发生在 Boot 重打包之后。业务应用写法（`<parent>` + 单个 starter 依赖）见官网 [`getting-started.md`](https://github.com/patton174/coco-framework/blob/main/website/docs/getting-started.md)，Wiki 不重复。

`coco-support/coco-feature-archive-smoke` 就是以 `coco-parent` 为父的真实消费者，见 [支持模块与仓库工具](Module-Support)。

## 4. coco-maven-plugin

**路径**：`coco-build/coco-maven-plugin`，打包 `maven-plugin`，源码包 `io.github.coco.maven`。

**依赖**：`coco-api`、`coco-feature-model`、`jackson-databind`、`snakeyaml`（编译）；`maven-plugin-api`、`maven-plugin-annotations`、`maven-core`（`provided`）；测试用 `coco-exception`、`spring-boot-starter-web`、`spring-boot-starter-validation`、`mybatis-plus-spring-boot4-starter`、`assertj-core`、`junit-jupiter`。

### 4.1 主要类型

| 类 | 作用 |
| --- | --- |
| `CocoFeaturesMojo` | goal `features`：解析 Feature 计划、写清单、补依赖、剪裁 Maven artifact 集合 |
| `CocoPackagePruneMojo` | goal `prune-package`：改写 Spring Boot 可执行 jar，移除被禁用 Feature 的嵌套依赖 |
| `CocoBuildFeatureConfigurationLoader` | 从 `src/main/resources` 读取 `application.yml` / `application.yaml` / `application.properties` 里的 `coco.features.enabled|disabled` |
| `CocoAnnotatedFeatureScanner` | 扫描项目编译输出目录里的 class，读取类上的 `@CocoFeatures` |
| `CocoFeatureIdParser` | 解析逗号分隔的 Feature id（Maven 参数使用） |

### 4.2 `features` goal

`@Mojo(name = "features", defaultPhase = PROCESS_CLASSES, requiresDependencyResolution = RUNTIME, threadSafe = true)`

| 参数（`property`） | 默认值 | 含义 |
| --- | --- | --- |
| `coco.features.enabled` | 无 | 逗号分隔的 Feature id，显式启用 |
| `coco.features.disabled` | 无 | 逗号分隔的 Feature id，显式禁用 |
| `coco.features.featureGroupId` | `io.github.patton174` | 补加依赖时使用的 groupId，也是 `coco-feature-*` 剪裁时的限定 groupId |
| `coco.features.featureVersion` | `${project.version}` | 补加依赖时使用的版本；`coco-parent` 把它设为 `${coco.version}` |
| `coco.features.skip` | `false` | 跳过本 goal |

`pom` 打包会直接跳过（日志 `Coco feature assembly skipped for pom packaging.`）。

**选择来源与优先级（低 → 高）**，按 `execute()` 内的 `merge` 顺序：

1. 业务项目 `src/main/resources` 下的配置文件：按 `application.yml`、`application.yaml`、`application.properties` 顺序加载，后加载者优先级更高。
2. Maven 参数 `coco.features.enabled|disabled`。
3. `@CocoFeatures` 注解：`CocoAnnotatedFeatureScanner` 遍历 `${project.build.outputDirectory}` 下的 `.class`，用依赖 classpath 构造 `URLClassLoader` 加载并读取注解；classpath 仅用于类加载，不是被扫描对象。

合并结果交给 `StandardCocoFeatures.resolve`（依赖传播规则见 [Feature 机制](Feature-System)）。

**随后的动作**（`execute()` 顺序）：

1. `writeManifest`：写入 `target/classes/META-INF/coco/features.json`（`generatedBy="coco-maven-plugin"`）。
2. `applyFeatureDependencies`：对每个启用的 Feature，如果项目依赖里还没有 `featureGroupId:artifactId`，就加一条 `runtime` 作用域依赖（版本 `featureVersion`），并尝试通过 Maven Resolver 解析后加入项目 artifact 集合。这就是 `coco-rate-limit`、`coco-idempotency` 不在 starter 里也能被带入的原因（见 [Spring 组合层](Module-Spring)）。
3. `pruneDisabledFeatureArtifacts`：从项目 artifact 集合里移除被禁用 Feature 的 `pruneArtifactIds`。`isPrunableArtifact` 只对三类 artifact 命中：artifactId 以 `coco-feature-` 开头且 groupId 等于 `featureGroupId`；artifactId 以 `mybatis-plus` 开头且 groupId 为 `com.baomidou`；`mybatis` / `mybatis-spring` 且 groupId 为 `org.mybatis`。规范坐标（如 `coco-web`）在这一步不会命中，Boot 归档里的最终清理由 `prune-package` 完成。这种分工的设计意图未见文档说明，行为以 `CocoFeaturesMojo#isPrunableArtifact` 源码为准。

### 4.3 `prune-package` goal

`@Mojo(name = "prune-package", defaultPhase = PACKAGE, threadSafe = true)`；参数只有 `coco.features.skip`（默认 `false`）和只读的 `project`、`classesDirectory`、`buildDirectory`、`finalName`。

执行流程：

- 读取 `target/classes/META-INF/coco/features.json`；没有清单，或没有被禁用的 Feature，直接跳过。
- 定位主 jar（项目 artifact 文件，缺省为 `${buildDirectory}/${finalName}.jar`）；文件不存在则跳过。
- 重写归档：删除 `BOOT-INF/lib/` 下文件名匹配 `<pruneArtifactId>-<像版本号的后缀>.jar` 的条目，并同步过滤 `BOOT-INF/classpath.idx` 与 `BOOT-INF/layers.idx` 中对应的行。匹配时 artifactId 按长度倒序，并要求 `-` 之后是像版本号的片段，用来避免短 artifactId 误伤业务自己的扩展 jar（`isArtifactJar` / `isVersionLike`）。
- 内容发生变化时，先把原归档备份为 `target/coco-prune.original.jar`，再用新文件替换。

### 4.4 测试

`coco-build/coco-maven-plugin/src/test/java/io/github/coco/maven/` 下 5 个：`CocoFeaturesMojoTest`、`CocoPackagePruneMojoTest`、`CocoAnnotatedFeatureScannerTest`、`CocoBuildFeatureConfigurationLoaderTest`、`CocoMavenPluginDescriptorTest`。端到端归档验证由 `coco-feature-archive-smoke` 的 `FeatureArchiveSmokeIT` 承担。

### 4.5 扩展点

插件本身没有 SPI。可调整的只有 Mojo 参数和 `featureGroupId` / `featureVersion` 的覆盖。新增 Feature 时需要同步 `StandardCocoFeatures`（它是插件、运行期和测试共用的元数据来源），见 [指南：新增一个 Feature 模块](Guide-Add-Feature-Module)。

## 5. coco-compatibility（无源码兼容门面）

**规则来源**：[`module-layout.md`](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/architecture/module-layout.md)“已发布兼容基线”6 条。要点是：旧坐标在 2.x 内必须继续可解析；门面不得复制实现类、自动配置导入、`spring.factories`、消息资源或模板；Java 包名、FQCN、配置前缀、Feature id 和插件 goal 不因 artifactId 重命名而改变；BOM 同时管理新旧坐标，starter 只组合主坐标。

| 门面（旧坐标） | 唯一依赖（规范制品） | 编译探针（`src/test/java`） |
| --- | --- | --- |
| `coco-config` | `coco-spring-boot-autoconfigure` | `io.github.coco.compatibility.config.CocoConfigCompatibilityCompileProbe` |
| `coco-feature-runtime` | `coco-spring-boot-autoconfigure` | `...compatibility.runtime.CocoFeatureRuntimeCompatibilityCompileProbe` |
| `coco-feature-web` | `coco-web` | `...compatibility.web.CocoFeatureWebCompatibilityCompileProbe` |
| `coco-feature-mybatis-plus` | `coco-mybatis-plus` | `...compatibility.mybatisplus.CocoFeatureMybatisPlusCompatibilityCompileProbe` |
| `coco-feature-audit` | `coco-audit` | `...compatibility.audit.CocoFeatureAuditCompatibilityCompileProbe` |
| `coco-feature-openapi` | `coco-openapi` | `...compatibility.openapi.CocoFeatureOpenAPICompatibilityCompileProbe` |
| `coco-feature-tenant` | `coco-tenant` | `...compatibility.tenant.CocoFeatureTenantCompatibilityCompileProbe` |
| `coco-feature-data-permission` | `coco-data-permission` | `...compatibility.datapermission.CocoFeatureDataPermissionCompatibilityCompileProbe` |
| `coco-feature-security` | `coco-security` | `...compatibility.security.CocoFeatureSecurityCompatibilityCompileProbe` |

- 每个门面只有 `pom.xml` 与一个测试探针，没有 `src/main`。探针是不含测试方法的 `final class`，用一个 `PUBLIC_TYPES` 数组以 `.class` 字面量引用旧坐标曾经对外暴露、现由规范制品承载的公开类型；任何一个类型被移动、改名或降低可见性，该模块的测试编译就会失败，从而在发布前暴露兼容面的破坏。探针只做编译期校验，运行期不会被加载，也没有运行期断言。
- `coco-config` 与 `coco-feature-runtime` 的实现已并入 `coco-spring-boot-autoconfigure`，包名保留（见 [Spring 组合层](Module-Spring)）。
- **不要把门面当作新依赖入口**。框架内没有任何模块依赖它们；唯一的使用者是 `coco-spring-boot-starter` 的测试作用域（`coco-feature-runtime`、`coco-config`），用来验证两者与迁移后的自动配置共存时扩展点只注册一次（`CocoSpringCompositionClasspathTest`）。
- 目录里还有一个 `coco-build/coco-compatibility/coco-test/`，只含 `target/` 与 `.flattened-pom.xml`，没有 `pom.xml`，也不在根 `<modules>` 中，没有被 Git 跟踪的文件，属于本地构建残留，不是仓库内容；`mvn clean` 或手动删除即可。详见 [支持模块与仓库工具](Module-Support)。

## 6. 根 pom.xml 的构建基础设施

这些配置在根 `pom.xml`，由所有模块继承（`coco-parent` 通过 `coco.enforcer.skip=true` 关闭 Enforcer）。

| 主题 | 配置 |
| --- | --- |
| 版本 | `revision=1.0.0-SNAPSHOT`（本地默认值，发布时用 `-Drevision=X.Y.Z`）；`spring-boot.version=4.1.1`；`java.version=17`；`project.build.sourceEncoding=UTF-8` |
| 编译 | `maven-compiler-plugin`：`<release>${java.version}</release>`、`<parameters>true</parameters>`。开发 / CI 用 JDK 21，编译目标是 17 |
| Enforcer | `enforce-build-gates`（`validate` 阶段）：`banDuplicatePomDependencyVersions`；`dependencyConvergence`（排除 `provided` / `test`）；`bannedDependencies`（`searchTransitive=false`，禁 `commons-logging:commons-logging`、`log4j:log4j`、`org.slf4j:log4j-over-slf4j`、`spring-boot-starter-jetty`、`spring-boot-starter-undertow`）。受 `coco.enforcer.skip` 控制，根默认 `false` |
| Surefire / Failsafe | 都设 `useModulePath=false`；Failsafe 另设 `failIfNoTests=false`，仓库只有 2 个 `*IT`（见 [指南：测试约定](Guide-Testing)） |
| flatten-maven-plugin | `flattenMode=resolveCiFriendliesOnly`；`process-resources` 阶段生成 `.flattened-pom.xml`，`clean` 阶段清理（该文件已被 `.gitignore`） |
| `release` profile | `enforce-release-version`（`requireReleaseVersion`）、源码包与 javadoc 包、GPG 签名（`verify` 阶段，环境变量键名 `MAVEN_GPG_KEY`、`MAVEN_GPG_PASSPHRASE`）、`central-publishing-maven-plugin`（`publishingServerId=central`，`central.auto-publish=false`，`central.wait-until=VALIDATED`） |
| 静态分析 | SpotBugs、Checkstyle 的版本在根 pom 里声明，但**不绑定默认生命周期**，本地 `mvn install` 不会运行；CI 直接调用目标：`spotbugs:check` 带 `continue-on-error`（只报告），`checkstyle:check` 会阻塞。Checkstyle 配置 `configLocation=google_checks.xml`（内置的 Google 规则），SpotBugs 设 `effort=Max`、`threshold=Low`；仓库没有被 Git 跟踪的自定义 checkstyle / spotbugs 规则文件 |

发布流程、凭据键名与触发规则见 [指南：分支、PR、CI 与发布流程](Guide-Release-And-Workflow) 和 [`release.md`](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/release.md)，本页不重复。

## 7. 相关页面

- [模块参考总览](Module-Overview)
- [Spring 组合层：autoconfigure 与 starter](Module-Spring)
- [基础模块：coco-foundation](Module-Foundation)
- [支持模块与仓库工具](Module-Support)
- [Feature 机制：构建期与运行期](Feature-System)
- [架构总览](Architecture)
- [指南：新增一个 Feature 模块](Guide-Add-Feature-Module)
