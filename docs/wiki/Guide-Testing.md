# 指南：测试约定

本页回答：Coco Framework 仓库里有哪些测试、怎么命名、怎么运行，以及新增或修改 Feature 时需要同步改哪些测试。

> 内容对应仓库 `main` 分支；以源码为准，发现出入请提 Issue 或 PR。命令取自 [`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)、[`CONTRIBUTING.md`](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md)、[`release.md`](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/release.md) 与 `.github/workflows/*.yml`；执行前请先确认它们适用于你的环境和改动面。

## 1. 测试栈一览

| 项 | 现状 | 来源 |
| --- | --- | --- |
| 单元测试框架 | JUnit Jupiter + AssertJ | 各模块 `pom.xml` 的 test 依赖 |
| Spring 相关测试 | `spring-boot-test` / `spring-boot-starter-test`；自动配置测试常用 `ApplicationContextRunner` | 例：`coco-features/coco-lock/src/test/java/io/github/coco/feature/lock/CocoLockAutoConfigurationTest.java` |
| 日志实现 | `logback-classic`（test 依赖） | 各 Feature 模块 `pom.xml` |
| 覆盖率 / 容器化测试 | 全仓库 `pom.xml` 中**未发现** JaCoCo、Testcontainers 的配置 | 对 `git ls-files '*pom.xml'` 检索 `jacoco`/`testcontainers`/`mockito` 无匹配 |
| 单元测试规模 | `src/test` 下 `*Test.java` 共 114 个（量级参考，随提交变化） | 以 `git ls-files` 的已跟踪文件统计 |
| 集成测试规模 | 全仓库仅 **2 个** `*IT`（见第 3 节） | `CocoStarterSmokeIT`、`FeatureArchiveSmokeIT` |

框架**不维护业务样例应用**。业务 / HTTP 验收由独立仓库 `coco-admin` 的 `framework-acceptance` 负责（`AGENTS.md` 与 `coco-support/coco-document/release.md` 均有说明）。因此框架内“能跑起来”的只有上述两个 IT 夹具，没有可独立运行的 demo。

## 2. 命名与目录约定

| 类型 | 约定 | 示例 |
| --- | --- | --- |
| 单元测试 | 类名以 `Test` 结尾，放在模块自己的 `src/test/java`，包名与被测类一致 | `coco-foundation/coco-feature-model/src/test/java/io/github/coco/feature/model/StandardCocoFeaturesTest.java` |
| 集成测试 | 类名以 `IT` 结尾，由 Failsafe 在 `integration-test` 阶段执行 | `coco-spring/coco-spring-boot-starter/src/test/java/io/github/coco/spring/boot/CocoStarterSmokeIT.java` |
| 自动配置测试 | `Coco<X>AutoConfigurationTest` | `CocoLockAutoConfigurationTest` |
| 默认实现测试 | `Default<X>Test` | `DefaultCocoLockManagerTest` |
| 存储实现测试 | `InMemory<X>StoreTest`、`Redis<X>StoreTest` | `InMemoryCocoLockStoreTest`、`RedisCocoLockStoreTest` |
| 兼容门面探针 | `<Facade>CompatibilityCompileProbe`，位于门面模块 `src/test` | `coco-build/coco-compatibility/coco-config/src/test/java/io/github/coco/compatibility/config/CocoConfigCompatibilityCompileProbe.java` |

说明：`coco-lock` 的 `src/test/java/io/github/coco/feature/lock/` 下正好是上表前四类的一套样板（4 个测试类），新增同类模块时可以对照。自动配置测试统一使用 `ApplicationContextRunner`：仓库里全部 27 个 `*AutoConfigurationTest` 都使用它（或其 Servlet 变体 `WebApplicationContextRunner`），共有 31 个测试类引用了 `ApplicationContextRunner`。

## 3. Surefire、Failsafe 与生命周期

根 `pom.xml` 对两个插件的配置很简单：

| 插件 | 版本属性 | 配置 |
| --- | --- | --- |
| `maven-surefire-plugin` | `3.6.0` | `useModulePath=false` |
| `maven-failsafe-plugin` | `3.6.0` | `useModulePath=false`、`failIfNoTests=false`；在根 `pom.xml` 的 `build/plugins` 中绑定 execution `integration-tests`（继承根 pom 的模块都会带上），目标为 `integration-test` 与 `verify` |

由此得到的行为：

- `mvn test` 只跑 Surefire（`*Test`），**不会**执行 `*IT`。
- `mvn verify` / `mvn install` 会走到 `integration-test` 与 `verify` 阶段，Failsafe 才执行 `*IT`。CI 跑的是 `mvn -B -ntp install`。
- 因为 `failIfNoTests=false`，没有 IT 的模块不会因此失败。

### 两个集成测试

| 测试 | 做什么 |
| --- | --- |
| `coco-spring/coco-spring-boot-starter/src/test/java/io/github/coco/spring/boot/CocoStarterSmokeIT.java` | 方法 `startsServletApplicationWithStarterDefaults`：以 starter 默认值启动一个 Servlet 应用，断言 `CocoMessageService`、`CocoWebRequestContextResolver` 两个 Bean 存在 |
| `coco-support/coco-feature-archive-smoke/src/test/java/io/github/coco/fixture/archive/FeatureArchiveSmokeIT.java` | 方法 `verifiesCurrentReactorArchiveManifestAndPrunedLibraries`：读取系统属性 `coco.feature.archive.path`（由该模块 `pom.xml` 的 Failsafe 配置设为 `${project.build.directory}/${project.build.finalName}.jar`），校验 Boot 归档里的 Feature 清单与被裁剪的库 |

`coco-feature-archive-smoke` 的父 POM 是 `coco-parent`，所以它会真实执行 `coco:features` 与 `coco:prune-package`；夹具应用类是 `coco-support/coco-feature-archive-smoke/src/main/java/io/github/coco/fixture/archive/FeatureArchiveSmokeApplication.java`。它的 `maven.deploy.skip` / `maven.install.skip` 为 true，不会发布。`release.md` 说明该冒烟测试在 `mvn -B install` 的 Failsafe 阶段执行。

## 4. 契约与回归类测试（改相关代码前先看）

| 测试 | 位置 | 守护什么 |
| --- | --- | --- |
| `StandardCocoFeaturesTest` | `coco-foundation/coco-feature-model/src/test/java/io/github/coco/feature/model/` | 逐 Feature 断言 id、artifactId、自动配置 FQCN、Feature 依赖。**新增 / 改名 Feature 必须同步更新** |
| `CocoRateLimitStarterDependencyContractTest` | `coco-spring/coco-spring-boot-starter/src/test/java/io/github/coco/spring/boot/` | starter 的直接依赖里**不得**出现 `coco-rate-limit` 与 `coco-idempotency`；二者由 `coco:features` 在构建期按 Feature 计划装配 |
| `CocoMybatisPlusStarterDependencyContractTest` | 同上目录 | starter 与 MyBatis-Plus 相关的依赖契约（具体断言以源码为准） |
| `CocoSpringCompositionClasspathTest` | 同上目录 | starter 与兼容门面同在 classpath 时，已迁移的自动配置与 `spring.factories` 扩展点只注册一次 |
| `*CompatibilityCompileProbe`（9 个） | `coco-build/coco-compatibility/*/src/test/...` | 以编译期引用的方式钉住旧坐标曾对外暴露的公开类型（例：`CocoConfigCompatibilityCompileProbe` 引用 `io.github.coco.config.CocoProperties` 等）；这些类型被移动、改名或降低可见性时，该模块的测试编译会失败 |
| `CocoFeaturesMojoTest`、`CocoPackagePruneMojoTest`、`CocoAnnotatedFeatureScannerTest`、`CocoBuildFeatureConfigurationLoaderTest`、`CocoMavenPluginDescriptorTest` | `coco-build/coco-maven-plugin/src/test/java/io/github/coco/maven/` | 构建期装配、包裁剪、`@CocoFeatures` 扫描、`application.yml` 读取、插件描述符 |

starter 模块的 `src/test` 共 7 个类：上表中的 3 个（两个依赖契约测试和 `CocoSpringCompositionClasspathTest`），加 `CocoAutoConfigurationTest`、`CocoLifecycleLoggerTest`、`CocoSpringApplicationRunListenerTest`，再加 1 个 IT（`CocoStarterSmokeIT`）。

## 5. 命令速查

先按 [开发环境与常见陷阱](Dev-Environment) 配好 JDK 21（编译目标是 17）：

```powershell
$env:JAVA_HOME='<JDK21_HOME>'   # 换成你自己的 JDK 21 安装目录
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -v
```

| 目的 | 命令 | 来源 |
| --- | --- | --- |
| CI 同款完整验证 | `mvn -B -ntp install` | `.github/workflows/reusable-tests.yml` |
| 贡献前的完整验证 | `mvn -B clean verify` | `CONTRIBUTING.md` |
| 快速 `verify` | `mvn verify` | `AGENTS.md` |
| 单模块及其上游依赖的测试 | `mvn -pl :module-artifact-id -am test`，例如 `mvn -pl :coco-web -am test` | `AGENTS.md` |
| 插件相关反应堆检查 | `mvn -B -pl :coco-maven-plugin -am verify` | `AGENTS.md` |
| 归档裁剪冒烟（含 IT） | `mvn -B -pl :coco-feature-archive-smoke -am verify` | 由根 `pom.xml` 的 Failsafe 绑定与该模块 `pom.xml`（artifactId `coco-feature-archive-smoke`）推导得出 |
| 只编译测试代码（静态分析前置） | `mvn -B -ntp -DskipTests test-compile` | `.github/workflows/reusable-static-analysis.yml` |
| release profile 冒烟（无 GPG） | `mvn -B -Prelease '-Drevision=1.0.0' '-Dgpg.skip=true' '-DskipTests' verify` | `AGENTS.md`、`release.md` |

PowerShell 提示：`CONTRIBUTING.md` 在 release 冒烟命令里给 `-D` 参数加了单引号（`'-Drevision=1.0.0' '-Dgpg.skip=true' '-DskipTests'`），因为 PowerShell 可能把带点的参数拆开；上表沿用同样的写法。

### 只跑某一个测试类（通用 Maven 知识）

`-Dtest=ClassName` 是 Surefire 的标准用法，本仓库没有为它做专门配置（根 `pom.xml` 只设置了 `useModulePath=false`）：

```powershell
# 只在目标模块里跑一个测试类
mvn -pl :coco-feature-model -Dtest=StandardCocoFeaturesTest test

# 带 -am 时，上游模块里没有同名测试会报 “No tests matching”，可以加上这个标准开关
mvn -pl :coco-feature-model -am -Dtest=StandardCocoFeaturesTest -Dsurefire.failIfNoSpecifiedTests=false test
```

IDE 里直接运行单个 JUnit 类同样可行；如果 IDE 导入时报 `${revision}` 相关问题，见 [开发环境与常见陷阱](Dev-Environment)。

## 6. 新增或修改 Feature 时要动的测试

1. 在 `StandardCocoFeaturesTest` 补上新 Feature 的 id、artifactId、自动配置 FQCN 与依赖断言。
2. 决定 starter 是否直接依赖该模块：
   - 要直接依赖：改 `coco-spring/coco-spring-boot-starter/pom.xml`，并确认现有契约测试没有被破坏。
   - 不直接依赖（和 `coco-rate-limit`、`coco-idempotency` 一样由构建期装配）：参照 `CocoRateLimitStarterDependencyContractTest` 加一个契约测试。取舍理由见 `AGENTS.md` 的“starter 只组合能力”；哪些模块应走这条路径，设计意图未见文档说明，行为以 `coco-spring/coco-spring-boot-starter/pom.xml` 与现有契约测试为准。
3. 在新模块里按第 2 节的命名补一套：`Coco<X>AutoConfigurationTest`、`Default<X>Test`、（有存储抽象时）`InMemory<X>StoreTest` / `Redis<X>StoreTest`。
4. 如果改了旧坐标门面或重命名了模块，对应的 `*CompatibilityCompileProbe` 需要保持可编译。
5. 提交前至少跑一次 `mvn -B -ntp install`，因为 Failsafe 的两个 IT 只在 `verify` 之后的阶段执行。

完整步骤见 [指南：新增一个 Feature 模块](Guide-Add-Feature-Module)。

## 7. 共享测试工具：CocoTestSupport

`coco-support/coco-test/src/main/java/io/github/coco/test/CocoTestSupport.java` 是该模块唯一的类，提供：

- `configurationMetadata(Class)`：在测试里读取 `spring-boot-configuration-processor` 生成的 `META-INF/spring-configuration-metadata.json`，用来断言配置键、默认值是否真的出现在元数据里。
- `jsonResource(Class, String)`：读取测试资源里的 JSON。
- `findNamedNode`、`requiredNamedNode`：在 JSON 树里按名称查找节点。

当前只有 `coco-security` 以 test 作用域依赖 `coco-test`。

文档与实现存在出入：`module-layout.md` 的迁移映射把它的新主制品名写作 `coco-test-support`，而当前 Reactor 的实际模块是 `coco-support/coco-test`（artifactId `coco-test`），以根 `pom.xml` 的 `<modules>` 为准。更多说明见 [支持模块与仓库工具](Module-Support)。

## 8. 治理脚本与 README 的测试（非 Maven）

改 `.github/` 下的脚本、工作流或 README 片段时，CI 会额外运行这些检查，本地可以直接复用：

```powershell
python .github/scripts/test_agent_review.py
python .github/scripts/test_auto_merge.py
python .github/scripts/test_contributor_gate.py
python .github/scripts/test_verify_boot_archive_feature_coordinates.py
python .github/scripts/test_release_file_count_preflight.py
python -m ruff check .github/scripts
python -m ruff format --check .github/scripts
node --test .github/readme/tests/readme.test.mjs
node .github/readme/scripts/render.mjs --check
```

- `CONTRIBUTING.md` 只列出了前两条 Python 测试；其余三条来自 `.github/workflows/reusable-static-analysis.yml`。
- Python 依赖清单在 `.github/requirements/static-analysis.txt`；CI 使用 Python 3.12。
- `README.md` 与 `README_CN.md` 是生成文件，不要直接改；改 `.github/readme/fragments/` 后运行 `node .github/readme/scripts/render.mjs --write`，再用 `--check` 验证。

改 `docs/wiki/` 下的页面时，用 Wiki 工具自检（详见 [指南：维护与发布本 Wiki](Guide-Contribute-To-Wiki)）：

```powershell
python .github/scripts/test_wiki_sync.py
python .github/scripts/wiki_sync.py validate
```

这两条命令需要自行在本地运行：它们目前没有列入 `reusable-static-analysis.yml` 的显式命令清单，但 `.github/scripts/` 下的新脚本仍会被上面的 `ruff check` 与 `ruff format --check` 覆盖。

## 9. 静态分析与测试的关系

`SpotBugs` 与 `Checkstyle` **没有**绑定到 Maven 生命周期，所以本地 `mvn install` 不会运行它们。CI 里单独调用：

```powershell
mvn -B -ntp spotbugs:check     # 目前 continue-on-error，只出报告
mvn -B -ntp checkstyle:check   # 失败会阻塞 CI gate
```

根 `pom.xml` 里 Checkstyle 的 `configLocation` 是 `google_checks.xml`，即 Checkstyle 内置的 Google 规则集；仓库内没有被跟踪的自定义 checkstyle / spotbugs 配置文件，所以实际生效的就是这套内置规则与 SpotBugs 的 `effort=Max`、`threshold=Low` 设置。

## 10. 测试时的常见坑

- 编译或测试阶段报 `InvalidConfigurationMetadataException ... End of input at character 0`：多半是残缺的 `target/` 产物。构建被中断后，`target/classes/META-INF/spring-configuration-metadata.json` 可能留下 0 字节文件；执行 `mvn clean` 后重新构建即可，详见 [FAQ 与故障排查](FAQ-Troubleshooting)。
- 某个模块编译时报 `package ... does not match`、`class ... should be declared in a file named` 或重复类：先用 `git status -sb` 与 `git diff` 检查是否有尚未收尾的本地改动，确认类的 `package` 声明与所在目录一致，再修正这些改动，或先 `git stash` 暂存后重试（`git restore` 会丢弃改动，使用前请确认）。
- 默认 `JAVA_HOME` 可能不是 JDK 21，先用 `java -version` 与 `mvn -v` 自检。

## 相关页面

- [快速开始：构建、测试与验证](Getting-Started)
- [开发环境与常见陷阱](Dev-Environment)
- [指南：新增一个 Feature 模块](Guide-Add-Feature-Module)
- [指南：分支、PR、CI 与发布流程](Guide-Release-And-Workflow)
- [指南：维护与发布本 Wiki](Guide-Contribute-To-Wiki)
- [Feature 机制：构建期与运行期](Feature-System)
- [支持模块与仓库工具](Module-Support)
- [FAQ 与故障排查](FAQ-Troubleshooting)
- [速查：开发任务到文件/目录](Where-To-Find-Things)
- 仓库文档：[AGENTS.md](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)、[CONTRIBUTING.md](https://github.com/patton174/coco-framework/blob/main/CONTRIBUTING.md)、[发布说明 release.md](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/release.md)
