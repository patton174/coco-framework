# 指南：新增一个 Feature 模块

> 本页回答：要在 `coco-features/` 下新增一个可独立启停的 Feature（例如假想的 `example`），需要创建哪些文件、登记到哪些位置、补哪些测试、怎么验证。
> 本清单由已有模块 `coco-captcha`、`coco-lock` 在仓库中出现的位置反推而来，并对照了校验脚本和测试的实际行为。清单未必覆盖所有登记点；动手前请先用 `rg` 或 CodeGraph 搜索一个现有同类模块（如 `coco-captcha`）的全部出现位置，逐项对照。
> 文中 `example` / `coco-example` / `Example` 是占位符，请替换为真实名称。

## 0. 开始之前

- 先确认这项能力属于框架。[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 规定：框架是独立的 Web 服务器基础，业务领域、认证选择、组织权限模型属于下游；CRUD 与业务脚手架通过代码生成产出，不做运行时魔法。
- 新增 Feature 属于 “feature contract” 变更：[GOVERNANCE.md](https://github.com/patton174/coco-framework/blob/main/GOVERNANCE.md) 的决策流程要求这类变更在实现前先有 Issue 或设计讨论（[CONTRIBUTING.md](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md) 同样要求破坏性 API、Maven 坐标、feature 契约变更先开 Issue）。
- 决定命名：**Feature id** 用 kebab-case（如 `example`）；模块 artifactId 取 `coco-<id>`（现有 16 个 Feature 全部如此，[`verify_boot_archive_feature_coordinates.py`](https://github.com/patton174/coco-framework/blob/main/.github/scripts/verify_boot_archive_feature_coordinates.py) 也按 `coco-<id>-` 前缀匹配归档中的 jar）；配置前缀 `coco.<id>`；Java 包见下一条。
- 包名在 `io.github.coco.feature.<名称>` 与 `io.github.coco.<名称>` 之间二选一，两种风格并存：现有 16 个 Feature 模块中，10 个使用前者（audit、data-permission、idempotency、lock、mybatis-plus、openapi、rate-limit、security、tenant、web），6 个使用后者（cache、captcha、messaging、notification、scheduling、storage）。设计意图未见文档说明，行为以各模块 `src/main/java` 下的实际包路径为准（另见 [约定与规范](Conventions)）。实操建议：与最近的同类模块保持一致，并在 PR 描述里说明所选风格。

## 1. 登记点总览

| # | 位置 | 动作 | 必须？ | 依据 |
| --- | --- | --- | --- | --- |
| 1 | `coco-features/coco-example/` | 新建模块（pom、代码、资源、测试） | 是 | 参照 `coco-features/coco-captcha/pom.xml` |
| 2 | `coco-features/pom.xml` | 在 `<modules>` 追加 `<module>coco-example</module>` | 是 | 现有 16 个模块均列于此 |
| 3 | `coco-build/coco-dependencies/pom.xml` | 在 `dependencyManagement` 登记，版本 `${project.version}` | 是 | 业务侧使用的 BOM 已登记全部 Feature 模块 |
| 4 | 根 `pom.xml` 的 `dependencyManagement` | 是否也要登记 | 做法不统一 | 根 pom 目前缺少 rate-limit、scheduling、lock、storage、messaging、cache、notification、captcha 共 8 个模块，它们只登记在 BOM；设计意图未见文档说明，行为以 `coco-build/coco-dependencies/pom.xml` 为准，BOM 必须登记 |
| 5 | `coco-foundation/coco-api/.../feature/CocoFeature.java` | 增加枚举常量 `EXAMPLE("example")` | 是 | Feature id 的稳定来源 |
| 6 | `coco-foundation/coco-feature-model/.../StandardCocoFeatures.java` | 在 `FEATURES` 追加 `feature(...)` | 是 | Feature 计划、manifest、裁剪的来源 |
| 7 | `coco-foundation/coco-feature-model/src/test/.../StandardCocoFeaturesTest.java` | 补断言 | 是 | `registersAllStandardFeatures` 要求枚举与登记一一对应，否则测试失败 |
| 8 | `coco-spring/coco-spring-boot-starter/pom.xml` | 是否让 starter 直接依赖 | 视情况 | 见 §4 |
| 9 | starter 契约测试 | 若不加入 starter，补一个契约测试 | 视情况 | `CocoRateLimitStarterDependencyContractTest` 的做法 |
| 10 | `.github/release/release-artifact-expectations.json` | 在 `modules[]` 追加条目 | 是（见 §7 的出入说明） | `release_file_count_preflight.py` 会比对反应堆与该清单 |
| 11 | `website/docs/features/example.md`、`website/sidebars.ts`、英文版、`feature-toggles.md` | 用户文档 | 建议 | 官网侧边栏是显式列表，不会自动收录 |
| 12 | `coco-support/coco-tools/coco-agent-skills` | 重建文档索引 | 视情况 | 索引由 `website/docs` 生成 |

## 2. 创建模块

### 2.1 目录与 pom

```text
coco-features/coco-example/
  pom.xml
  src/main/java/io/github/coco/example/            # 或 io/github/coco/feature/example/，见 §0
  src/main/resources/
    coco-example-messages.properties
    coco-example-messages_zh_CN.properties
    coco-example-messages_en_US.properties
    META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  src/test/java/io/github/coco/example/
```

pom 骨架（依据 [`coco-features/coco-captcha/pom.xml`](https://github.com/patton174/coco-framework/blob/main/coco-features/coco-captcha/pom.xml)；`<description>` 在现有 pom 中是英文一句话）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.patton174</groupId>
        <artifactId>coco-framework</artifactId>
        <version>${revision}</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <artifactId>coco-example</artifactId>
    <name>Coco Example</name>
    <description>One-sentence description of the capability.</description>

    <dependencies>
        <dependency><groupId>${project.groupId}</groupId><artifactId>coco-exception</artifactId></dependency>
        <dependency><groupId>${project.groupId}</groupId><artifactId>coco-i18n</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-autoconfigure</artifactId></dependency>
        <dependency><groupId>org.slf4j</groupId><artifactId>slf4j-api</artifactId></dependency>
        <!-- 重依赖设为 optional，运行期用 @ConditionalOnClass 保护 -->
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-redis</artifactId><optional>true</optional></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-configuration-processor</artifactId><optional>true</optional></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
        <dependency><groupId>ch.qos.logback</groupId><artifactId>logback-classic</artifactId><scope>test</scope></dependency>
    </dependencies>
</project>
```

依赖取舍：

- 不需要 Redis 就去掉 Redis 依赖；`coco-captcha`、`coco-lock` 的 Redis 为 `optional`。
- 若要使用 `@ConditionalOnCocoFeature`，模块必须能看到注解所在的 `coco-spring-boot-autoconfigure`，需要增加该依赖（`coco-web`、`coco-rate-limit` 等就是这样做的，见 [架构总览](Architecture)）。没有使用该注解的 7 个模块（scheduling、lock、storage、messaging、cache、notification、captcha）的 pom 也都没有依赖 `coco-spring-boot-autoconfigure`，两者吻合。这些模块是否有意不使用该注解，设计意图未见文档说明，行为以各模块的自动配置类与 `pom.xml` 为准。是否应该使用 `@ConditionalOnCocoFeature` 的取舍见 [Feature 机制](Feature-System)。
- 依赖版本不要在子模块里重复声明：`banDuplicatePomDependencyVersions` 与 `dependencyConvergence` 会在 `validate` 阶段把关。

### 2.2 代码

最小集合，细节见 [指南：新增或调整自动配置](Guide-Add-Auto-Configuration)：

- `Coco<Example>Properties`：`@ConfigurationProperties("coco.example")`，至少一个 `enabled`；
- `Coco<Example>AutoConfiguration`：`@AutoConfiguration`，注册消息包 Bean、默认实现 Bean（`@ConditionalOnMissingBean`）；
- 可替换点（SPI）：接口 + `Default*` 实现，只有存在真实替换点才加；
- 错误码（如有）：`Coco<Example>ErrorCode`，参照 `CocoLockErrorCode`；
- JavaDoc 使用项目模板（作者、仓库、`模块：{@code coco-example}`、`@since`），中文描述。

### 2.3 资源文件

- 消息包 `coco-example-messages.properties`（及 `_zh_CN`、`_en_US`）；Bean 里 `registry.add("coco-example-messages")` 要与文件基名一致。
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：每行一个自动配置 FQCN（Redis 三件套时按 `Redis`、`RedisMissingDependency`、主配置列出，参照 `coco-captcha` 的同名文件）。
- 需要在 IDE 里提示属性时，可参照 audit / tenant / web 等模块提供 `META-INF/additional-spring-configuration-metadata.json`；其余模块靠 `spring-boot-configuration-processor` 生成。

## 3. 登记 Feature（这是最容易漏的一步）

### 3.1 `CocoFeature`

在 `CocoFeature` 枚举末尾追加常量，id 一旦发布就不能改：

```java
EXAMPLE("example");
```

### 3.2 `StandardCocoFeatures`

在 `FEATURES` 列表追加一项：

```java
feature(CocoFeature.EXAMPLE, "coco-example",
        "io.github.coco.example.CocoExampleAutoConfiguration"),
```

- 有 Feature 依赖时作为可变参数追加，例如 `CocoFeature.WEB`（`rate-limit`、`idempotency` 就是 `feature(..., CocoFeature.WEB)`）；
- 需要额外裁剪的 artifact（例如同时裁剪第三方库或旧坐标）时，使用带 `Set<String> pruneArtifactIds` 的重载；否则裁剪集合默认就是 `Set.of(artifactId)`；
- 新 Feature 的 `defaultEnabled` 由辅助方法写死为 `true`；这意味着继承 `coco-parent` 的应用在构建期会通过 `coco:features` 得到该模块（除非被禁用），详见 §4。

### 3.3 `StandardCocoFeaturesTest`

按已有写法补断言（id、artifactId、自动配置 FQCN、`defaultEnabled`、依赖），例如：

```java
assertEquals("example", CocoFeature.EXAMPLE.id());
assertEquals("coco-example", definitions.get(CocoFeature.EXAMPLE).artifactId());
assertEquals("io.github.coco.example.CocoExampleAutoConfiguration",
        definitions.get(CocoFeature.EXAMPLE).autoConfigurationClassName());
assertTrue(definitions.get(CocoFeature.EXAMPLE).defaultEnabled());
assertEquals(Set.of(), definitions.get(CocoFeature.EXAMPLE).dependencies());
```

只要枚举与 `FEATURES` 不一致，`registersAllStandardFeatures` 就会失败，可以用它兜底。

## 4. 要不要让 starter 直接依赖

[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 要求 starter 只组合能力、不承载行为，但没有写“哪些模块应该进 starter”。现状可以作为参照：

| 做法 | 现有例子 | 说明 |
| --- | --- | --- |
| 加入 `coco-spring-boot-starter` | 14 个 Feature（web、mybatis-plus、audit、security、tenant、data-permission、openapi、scheduling、lock、storage、messaging、cache、notification、captcha） | 在 starter pom 追加依赖；若根 pom 的 `dependencyManagement` 没有登记该模块，需要显式写 `<version>${project.version}</version>`（scheduling … captcha 就是这样） |
| 不加入 starter，由 `coco:features` 在构建期装配 | `coco-rate-limit`、`coco-idempotency`（二者的 Feature 依赖 `web`） | `CocoRateLimitStarterDependencyContractTest` 断言 starter 直接依赖中不能出现它们 |

`CocoFeaturesMojo.applyFeatureDependencies` 会为 Feature 计划中已启用、且项目尚未声明的每个 Feature 追加一个 `runtime` 作用域依赖。因为所有 Feature 都是 `defaultEnabled=true`，所以继承 `coco-parent` 的应用即使 starter 不含该模块，也会在构建期得到它；差别主要出现在“只依赖 starter、不使用 `coco-parent`”的场景。哪些模块应进入 starter，设计意图未见文档说明，行为以 `coco-spring/coco-spring-boot-starter/pom.xml` 与 `coco-build/coco-maven-plugin` 中的 `CocoFeaturesMojo` 为准；建议在 Issue 或 PR 里与维护者沟通你的选择。

如果选择“不加入”，请仿照 `CocoRateLimitStarterDependencyContractTest` 加一个契约测试（读取 starter 的 `pom.xml`，断言直接依赖集合不含新模块），防止日后被无意加入。

## 5. 测试

模块内至少包含（参照 `coco-features/coco-lock/src/test/java/io/github/coco/feature/lock/` 与 `coco-captcha`）：

- `Coco<Example>AutoConfigurationTest`：用 `ApplicationContextRunner`（Servlet 相关用 `WebApplicationContextRunner`）+ `AutoConfigurations.of(...)`，验证默认 Bean、`enabled` 开关、`@ConditionalOnMissingBean` 覆盖；需要模拟“classpath 缺类”时用 `FilteredClassLoader`；
- `Default<X>Test`、`InMemory<X>StoreTest`、`Redis<X>StoreTest` 等功能单测；
- 若有 Redis 三件套，补 “store-type=redis 但缺少 Redis 类时启动失败” 的断言（`CocoLockAutoConfigurationTest` 中有 `coco.lock.store-type=redis requires` 的断言）。

约定与命名详见 [约定与规范](Conventions)，更多细节见 [指南：测试约定](Guide-Testing)。

## 6. 文档

- 用户文档：`website/docs/features/example.md`，英文版 `website/i18n/en/docusaurus-plugin-content-docs/current/features/example.md`。
- 官网侧边栏是显式列表（[`website/sidebars.ts`](https://github.com/patton174/coco-framework/blob/main/website/sidebars.ts) 的 `docsSidebar`），新增页面必须手工加入，如 `'features/example'`。
- 官网 [特性开关](https://github.com/patton174/coco-framework/blob/main/website/docs/feature-toggles.md) 中的 Feature 一览表需要增加一行（英文版同步）。
- agent-skills 的文档索引由 `website/docs` 与英文目录生成，更新用户文档后如需刷新，在 [`coco-support/coco-tools/coco-agent-skills`](https://github.com/patton174/coco-framework/tree/main/coco-support/coco-tools/coco-agent-skills) 运行 `npm run build-index`（脚本定义见该包 `package.json`）。
- README：`README.md` / `README_CN.md` 是生成物，**不要直接编辑**；能力清单若要体现新 Feature，修改 [`.github/readme/fragments/`](https://github.com/patton174/coco-framework/tree/main/.github/readme/fragments) 下 `en` 与 `zh-CN` 成对的 `40-capabilities-and-boundaries.md`（能力分组）和 `50-samples-and-architecture.md`（架构图），然后运行 `node .github/readme/scripts/render.mjs --write` 与 `--check`。
- 架构类说明放 [`coco-support/coco-document/`](https://github.com/patton174/coco-framework/tree/main/coco-support/coco-document)；本 Wiki 的相关页面（`Module-*` 模块参考页、[Feature 机制](Feature-System)、[速查表](Where-To-Find-Things)）需要同步更新，做法见 [指南：维护与发布本 Wiki](Guide-Contribute-To-Wiki)。
- 用户可见的新能力还应在 [`CHANGELOG.md`](https://github.com/patton174/coco-framework/blob/main/CHANGELOG.md) 的 `[Unreleased]` 下记一条。

## 7. 发布清单

`.github/release/release-artifact-expectations.json` 的 `modules[]` 需要追加一项（字段含义见该文件顶部的 `schema_version`、`group_id`、`checksum_suffixes`）：

```json
{
  "path": "coco-features/coco-example",
  "artifact_id": "coco-example",
  "packaging": "jar",
  "artifacts": ["pom", "main", "sources", "javadoc"]
}
```

原因：[`release_file_count_preflight.py`](https://github.com/patton174/coco-framework/blob/main/.github/scripts/release_file_count_preflight.py) 的 `validate_expected_reactor` 会比较反应堆里的模块路径与该清单的路径集合，不一致会抛出 `Reactor differs from release expectations; missing=..., extra=...`。该检查位于发布工作流的 `central-capacity` 作业。

文档与实现存在出入：该清单记录 34 个模块，当前反应堆共有 40 个项目，二者并不一致，以 `release_file_count_preflight.py` 的比对输出为准。差异集中在两类：清单缺少 `coco-features` 下 6 个新布局模块（`coco-web`、`coco-audit`、`coco-security`、`coco-tenant`、`coco-data-permission`、`coco-openapi`）以及 `coco-build/coco-compatibility/` 下 8 个兼容门面；清单里仍保留旧布局的 `coco-features/coco-feature-*`（7 项）和 `coco-spring/coco-config`（1 项）。这与你新增的模块无关，本页只做陈述，未改动清单；如果你的改动会触及发布清单，请在 PR 描述里说明，并向维护者确认是否已有进行中的同步。

## 8. 验证

命令来自 `AGENTS.md`、`CONTRIBUTING.md` 与 CI 配置，请以你的环境为准。先按 `AGENTS.md` 设置 JDK 21（见 [快速开始](Getting-Started)）：

```powershell
# 单模块 + 上游依赖
mvn -pl :coco-example -am test

# StandardCocoFeatures 与 starter 相关测试一并验证
mvn -pl :coco-spring-boot-starter -am test

# CI 同款完整验证
mvn -B -ntp install

# 静态分析（Checkstyle 阻塞 CI；SpotBugs 目前软失败）
mvn -B -ntp checkstyle:check
```

涉及发布行为（pom、插件、BOM）时再跑 release profile 冒烟：

```powershell
mvn -B -Prelease '-Drevision=1.0.0' '-Dgpg.skip=true' '-DskipTests' verify
```

完成后：修改了源码就执行 `codegraph sync .`；提交前 `git status -sb`。

## 9. 附：重命名一个已发布的 Feature 模块

如果不是新增而是把已发布的 `coco-feature-x` 改名为 `coco-x`，必须遵守 [`module-layout.md`](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/architecture/module-layout.md) 的兼容基线：

- 在 `coco-build/coco-compatibility/<旧 artifactId>/` 保留一个**无源码**门面，参照 [`coco-build/coco-compatibility/coco-feature-web/pom.xml`](https://github.com/patton174/coco-framework/blob/main/coco-build/coco-compatibility/coco-feature-web/pom.xml)：`<parent>` 为 `coco-framework`（`relativePath` 为 `../../../pom.xml`），`packaging` 为 `jar`，唯一依赖是规范制品 `coco-x`（`${project.version}`）；
- 门面不得包含实现类、自动配置导入、`spring.factories`、消息资源；
- 在根 `pom.xml` 的 `<modules>` 加入门面；BOM 同时管理新旧两个坐标；starter 只组合新坐标（`CocoMybatisPlusStarterDependencyContractTest` 断言了这一点）；
- 增加编译探针测试 `<门面名>CompatibilityCompileProbe`（位于 `src/test/java/io/github/coco/compatibility/<名称>/`），把旧坐标曾对外暴露的公开类型以 `Class<?>[]` 的形式引用起来；
- Java 包名、公开 FQCN、配置前缀、feature id、插件 goal 保持不变；
- `StandardCocoFeatures` 里该 Feature 的裁剪集合要同时包含新旧 artifactId（如 `Set.of("coco-web", "coco-feature-web")`）。

## 相关页面

- [架构总览](Architecture)
- [Feature 机制：构建期与运行期](Feature-System)
- [约定与规范](Conventions)
- [指南：新增或调整自动配置](Guide-Add-Auto-Configuration)
- [指南：测试约定](Guide-Testing)
- [指南：分支、PR、CI 与发布流程](Guide-Release-And-Workflow)
- [指南：维护与发布本 Wiki](Guide-Contribute-To-Wiki)
- [构建模块：BOM、父 POM、Maven 插件与兼容门面](Module-Build)
- [模块参考总览](Module-Overview)
- [速查：开发任务到文件/目录](Where-To-Find-Things)
- 仓库文档：[`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)、[`GOVERNANCE.md`](https://github.com/patton174/coco-framework/blob/main/GOVERNANCE.md)、[`CONTRIBUTING.md`](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md)
