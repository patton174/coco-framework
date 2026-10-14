# 指南：新增或调整自动配置

> 本页回答：一个自动配置类该放在哪个模块、怎么注册、怎么写条件与顺序、怎样保证默认实现可被业务替换，以及自动配置“不生效”时按什么顺序排查。
> 所有模式都来自仓库里的真实类，括号中给出类名或路径。`example` / `Example` 是占位符。与源码不一致之处会以“文档与实现存在出入：…”标出，请以句末给出的权威来源为准。

## 1. 先判断放在哪里

| 场景 | 放置位置 | 依据 |
| --- | --- | --- |
| 某个 Feature 自己的装配（属性、默认 Bean、过滤器、拦截器） | 该 Feature 模块（`coco-features/coco-<x>`） | `AGENTS.md`：starter 不承载行为；通用模块不依赖具体 Feature |
| 跨 Feature 的公共装配：国际化、日志、Feature 计划、异步、Jackson、启动横幅 | `coco-spring/coco-spring-boot-autoconfigure` | 该模块现有 8 个自动配置（见 [架构总览](Architecture)） |
| 需要在 Spring Boot 很早期介入：`SpringApplicationRunListener`、`EnvironmentPostProcessor`、`AutoConfigurationImportFilter` | `coco-spring-boot-autoconfigure` 的 `META-INF/spring.factories` | 现有三个扩展点 |
| 只组合依赖、不含行为 | `coco-spring-boot-starter`（仅 pom，无主代码） | `AGENTS.md` |

调整 autoconfigure 中**既有**自动配置时要特别小心：迁移期保留了旧的包名与 FQCN（`io.github.coco.config`、`io.github.coco.feature.runtime.*`、`io.github.coco.feature.registry`、`io.github.coco.common.*`），[`module-layout.md`](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/architecture/module-layout.md) 规定公开 FQCN、配置前缀、feature id、插件 goal 不得因重命名而改变。包名保留不等于模块仍在旧 jar 里。

## 2. 注册方式

### 2.1 `AutoConfiguration.imports`

每个模块在 `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 里每行写一个自动配置 FQCN。例（`coco-captcha`）：

```text
io.github.coco.captcha.CocoCaptchaRedisAutoConfiguration
io.github.coco.captcha.CocoCaptchaRedisMissingDependencyAutoConfiguration
io.github.coco.captcha.CocoCaptchaAutoConfiguration
```

`coco-web` 比较特殊：`CocoWebAutoConfiguration` 用 `@Import` 汇入 9 个内部配置（`CocoWebI18nAutoConfiguration`、`CocoWebResponseAutoConfiguration`、`CocoWebExceptionAutoConfiguration`、`CocoWebRequestBodyAutoConfiguration`、`CocoWebContextAutoConfiguration`、`CocoWebTraceAutoConfiguration`、`CocoWebSignatureAutoConfiguration`、`CocoWebEncryptionAutoConfiguration`、`CocoWebReplayAutoConfiguration`），这些内部配置不在 `.imports` 里单独登记。

### 2.2 `spring.factories`

仅用于早期扩展点。`coco-spring-boot-autoconfigure` 当前注册：

| 键 | 实现 |
| --- | --- |
| `org.springframework.boot.SpringApplicationRunListener` | `io.github.coco.spring.boot.banner.CocoSpringApplicationRunListener` |
| `org.springframework.boot.EnvironmentPostProcessor` | `io.github.coco.spring.boot.logging.CocoLoggingEnvironmentPostProcessor` |
| `org.springframework.boot.autoconfigure.AutoConfigurationImportFilter` | `io.github.coco.feature.runtime.autoconfigure.CocoFeatureAutoConfigurationImportFilter` |

### 2.3 不要重复注册

同一个自动配置不能在多个 jar 里各登记一次。兼容门面（`coco-config`、`coco-feature-runtime` 等）严禁复制 `.imports` 或 `spring.factories`。[`CocoSpringCompositionClasspathTest`](https://github.com/patton174/coco-framework/blob/main/coco-spring/coco-spring-boot-starter/src/test/java/io/github/coco/spring/boot/CocoSpringCompositionClasspathTest.java)（`coco-spring/coco-spring-boot-starter`）在 starter 与兼容门面同在 classpath 时，断言迁入 autoconfigure 的 5 个自动配置（`CocoCommonAutoConfiguration`、`CocoCommonLoggingAutoConfiguration`、`CocoFeatureRegistryAutoConfiguration`、`CocoConfigAutoConfiguration`、`CocoFeatureRuntimeAutoConfiguration`）以及上述 3 个 `spring.factories` 扩展点都**只注册一次**。该测试没有覆盖 `CocoAutoConfiguration`、`CocoAsyncAutoConfiguration`、`CocoJacksonAutoConfiguration` 三个；是否有意不覆盖，设计意图未见文档说明，行为以 `CocoSpringCompositionClasspathTest.java` 为准。

## 3. 一个典型的 Feature 自动配置骨架

依据 `CocoLockAutoConfiguration`、`CocoIdempotencyAutoConfiguration`、`CocoSecurityAutoConfiguration` 总结（示意代码，不能直接复制编译，需按真实类型调整）：

```java
@AutoConfiguration
@EnableConfigurationProperties(CocoExampleProperties.class)
@ConditionalOnProperty(prefix = "coco.example", name = "enabled", havingValue = "true")
public class CocoExampleAutoConfiguration {

    // 1) 每个模块一个消息包注册器 Bean
    @Bean
    @ConditionalOnMissingBean(name = "cocoExampleMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoExampleMessageBundleRegistrar() {
        return registry -> registry.add("coco-example-messages");
    }

    // 2) 默认实现，业务方可通过自定义 Bean 替换
    @Bean
    @ConditionalOnMissingBean
    public CocoExampleKeyResolver cocoExampleKeyResolver(CocoExampleProperties properties) {
        return new DefaultCocoExampleKeyResolver(properties);
    }

    // 3) 默认存储：in-memory（属性缺省或显式 in-memory 时生效）
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoExampleStore.class)
    @ConditionalOnProperty(prefix = "coco.example", name = "store-type",
            havingValue = "in-memory", matchIfMissing = true)
    public CocoExampleStore cocoExampleStore(CocoExampleProperties properties) {
        return new InMemoryCocoExampleStore(properties);
    }
}
```

若该 Feature 要由 Feature 计划门控，再加 `@ConditionalOnCocoFeature(CocoFeature.EXAMPLE)`（需要模块依赖 `coco-spring-boot-autoconfigure`，是否使用的取舍见 [Feature 机制](Feature-System)）。

## 4. 条件与顺序：现有模式一览

| 目的 | 写法 | 真实例子 |
| --- | --- | --- |
| 按 Feature 计划门控 | `@ConditionalOnCocoFeature(CocoFeature.X)` | `CocoSecurityAutoConfiguration`、`CocoIdempotencyAutoConfiguration` 等 9 个 Feature |
| 模块级开关（默认关闭） | `@ConditionalOnProperty(prefix = "coco.x", name = "enabled", havingValue = "true")` | `CocoLockAutoConfiguration`、`CocoIdempotencyAutoConfiguration` |
| 模块级开关（缺省开启） | 同上 + `matchIfMissing = true` | `CocoSchedulingAutoConfiguration`、`CocoMessagingAutoConfiguration` |
| 仅 Servlet Web 应用 | `@ConditionalOnWebApplication(type = SERVLET)`，必要时叠加 `@ConditionalOnClass(Filter.class)` | `CocoIdempotencyAutoConfiguration`；`CocoSecurityAutoConfiguration` 中的 Bean 方法 |
| 可选依赖存在才装配 | `@ConditionalOnClass(...)` | `CocoTenantMybatisPlusAutoConfiguration`（`TenantLineInnerInterceptor`、`CocoMybatisPlusInterceptorCustomizer`） |
| 按 Bean 是否存在装配 | `@ConditionalOnBean` | `CocoOpenApiAutoConfiguration` 的 `cocoSpringDocOpenApiCustomizer` |
| 在某个自动配置之后 | `@AutoConfiguration(after = X.class)` | `CocoAuditAutoConfiguration`（after `CocoCommonLoggingAutoConfiguration`） |
| 在某个自动配置之前 | `@AutoConfiguration(before = X.class)` 或 `@AutoConfigureBefore` | `CocoWebAutoConfiguration`（before `CocoCommonAutoConfiguration`）；`CocoIdempotencyAutoConfiguration`（`@AutoConfigureBefore(CocoWebContextAutoConfiguration.class)`） |
| 引用本模块依赖中没有的类 | `afterName = {"全限定类名"}` 字符串形式 | `CocoOpenApiAutoConfiguration`（after `CocoWebAutoConfiguration`、`CocoSecurityAutoConfiguration`；pom 中并没有依赖 `coco-web` / `coco-security`） |
| 夹在第三方自动配置之间 | `after = ...` + `before = ...` | `CocoMybatisPlusAutoConfiguration`（after `MybatisPlusAutoConfiguration`，before `MybatisPlusInnerInterceptorAutoConfiguration`） |
| 多个自动配置有先后 | `after = {A.class, B.class}` | `CocoTenantMybatisPlusAutoConfiguration`（after `CocoTenantAutoConfiguration`、`CocoMybatisPlusAutoConfiguration`） |

## 5. 默认 Bean 必须可替换

- 默认实现一律 `@ConditionalOnMissingBean`（按类型）；有固定名称语义的用 `@ConditionalOnMissingBean(name = "...")`，如 `cocoExampleClock`。
- 消息包注册器 Bean 的名字形如 `coco<Module>MessageBundleRegistrar`，并用 `@ConditionalOnMissingBean(name = ...)`。`CocoCommonAutoConfiguration` 通过 `ObjectProvider<CocoMessageBundleRegistrar>` 收集全部注册器，再追加基础包 `coco-messages` 构造 `cocoMessageSource`。
- 具有“策略”性质的组件（解析器、存储、写出器、验证器）都应是接口 + `Default*`/`InMemory*`/`Redis*` 实现；业务方提供同类型 Bean 即可替换。
- 面向业务的 SPI 保持小而稳（`AGENTS.md`）：没有真实替换点就不要新增接口。
- 安全相关默认值要保守，并在启用危险能力时给出日志：`CocoSecurityAutoConfiguration` 在 `coco.security.web.header.enabled=true` 时打印 WARN，说明该解析器信任上游 HTTP 头，不得直接暴露公网。

## 6. 属性类

- `@ConfigurationProperties("coco.<x>")`（或 `prefix = "coco.<x>"`），配合 `@EnableConfigurationProperties`；
- 属性键 kebab-case；默认值合理、危险能力默认关闭；
- 属性类放哪个模块有两种现状：Feature 的属性类放在 Feature 模块里；`CocoCommonProperties`（`coco-i18n`）与 `CocoLoggingProperties`（`coco-logging`）在 foundation 模块，却在 autoconfigure 的 `CocoCommonAutoConfiguration` / `CocoCommonLoggingAutoConfiguration` 里用 `@EnableConfigurationProperties` 启用。新代码请与目标模块的现有做法一致；
- IDE 提示：有的模块提供 `META-INF/additional-spring-configuration-metadata.json`，其余依赖 `spring-boot-configuration-processor`（`optional`）。

## 7. 可替换存储与 Redis “三件套”

适用于 rate-limit、idempotency、lock、cache、captcha 与 web replay。以 `coco-lock` 为准：

| 类 | 作用 | 关键条件 |
| --- | --- | --- |
| `CocoLockRedisAutoConfiguration` | 提供 `RedisCocoLockStore` | `@AutoConfiguration(before = CocoLockAutoConfiguration.class)`；`coco.lock.enabled=true`；`coco.lock.store-type=redis`；`@ConditionalOnClass(StringRedisTemplate.class)`；`@ConditionalOnMissingBean(CocoLockStore.class)` |
| `CocoLockRedisMissingDependencyAutoConfiguration` | Redis 类缺失时 **fail-fast**：Bean 方法直接抛 `BeanCreationException`，信息为 `coco.lock.store-type=redis requires ... StringRedisTemplate on the runtime classpath` | `@ConditionalOnMissingClass("org.springframework.data.redis.core.StringRedisTemplate")`；`enabled=true`；`store-type=redis` |
| `CocoLockAutoConfiguration` | 主配置，in-memory 默认存储 | `store-type=in-memory` 且 `matchIfMissing = true` |

要点：

- Redis 配置通过 `before` 排在主配置之前，主配置的默认存储再用 `@ConditionalOnMissingBean(CocoLockStore.class)` 让位；
- `StringRedisTemplate` 的选择：优先用 `coco.lock.redis.template-bean-name` 指定的 Bean；否则要求只有一个候选或恰好一个 `@Primary`，否则抛 `BeanCreationException`；
- `coco-cache` 的条件是 `store-type=two-level`（不是 `redis`），默认 `LOCAL`；
- 三个类都要写进 `.imports`，顺序按 Redis、MissingDependency、主配置（见 `coco-captcha`）；
- 对应测试见 `CocoLockAutoConfigurationTest`、`CocoCaptchaAutoConfigurationTest`、`CocoCacheAutoConfigurationTest`。

## 8. 与 MyBatis-Plus 的交互

需要给 MyBatis-Plus 拦截器链追加内部拦截器的 Feature（tenant、data-permission），不直接创建 `MybatisPlusInterceptor`，而是提供一个 `CocoMybatisPlusInterceptorCustomizer` Bean（`@FunctionalInterface`，位于 `coco-mybatis-plus`）：

```java
@Bean
@ConditionalOnMissingBean(name = "cocoTenantMybatisPlusInterceptorCustomizer")
public CocoMybatisPlusInterceptorCustomizer cocoTenantMybatisPlusInterceptorCustomizer(...) {
    return interceptor -> {
        interceptor.addInnerInterceptor(interceptorIgnoreGuard);
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
    };
}
```

该 Bean 所在的自动配置要：`after` 排在 `CocoMybatisPlusAutoConfiguration` 之后；用 `@ConditionalOnClass` 保护对 `coco-mybatis-plus`（`optional` 依赖）和 MyBatis-Plus 类的引用；仍然受 `@ConditionalOnCocoFeature(TENANT / DATA_PERMISSION)` 与 SQL 开关属性（`coco.tenant.sql.enabled` 缺省开启，`coco.data-permission.sql.enabled` 缺省关闭）控制。参考 `CocoTenantMybatisPlusAutoConfiguration`、`CocoDataPermissionMybatisPlusAutoConfiguration`。

## 9. 过滤器与拦截器的注册

- Servlet 过滤器：用 `FilterRegistrationBean` 显式设置名称与顺序，例如 `CocoSecurityAutoConfiguration` 注册 `cocoSecurityWebFilter`，`order = Ordered.HIGHEST_PRECEDENCE + 5`，Bean 名 `cocoSecurityWebFilterRegistration`（`@ConditionalOnMissingBean(name = ...)`）。
- MVC 拦截器：提供 `WebMvcConfigurer` Bean 并在 `addInterceptors` 中设置顺序，例如 `CocoIdempotencyAutoConfiguration` 的 `MVC_INTERCEPTOR_ORDER = Ordered.HIGHEST_PRECEDENCE + 10`。
- 顺序常量请集中定义并写注释，避免与 `coco-web` 的 Trace、上下文、签名、重放等过滤器产生隐含依赖。

## 10. 涉及 starter 时

`coco-spring-boot-starter` 是业务应用唯一的常规 starter 依赖，它**只组合依赖，没有主代码**（`src/main` 下没有任何文件）。因此“给 starter 加东西”实际上只有两类改动：

| 目的 | 做法 | 注意 |
| --- | --- | --- |
| 让新 Feature 模块随 starter 进入业务应用 | 在 `coco-spring/coco-spring-boot-starter/pom.xml` 追加 `<dependency>` | 根 pom 没有登记的模块要显式写 `<version>${project.version}</version>`（现状：scheduling、lock、storage、messaging、cache、notification、captcha 就是这样）；是否应进入 starter 的取舍见 [新增一个 Feature 模块](Guide-Add-Feature-Module) |
| 保证某个模块**不会**进入 starter | 不加依赖，并补契约测试 | 仿照 `CocoRateLimitStarterDependencyContractTest`（断言 starter 直接依赖不含 `coco-rate-limit`、`coco-idempotency`）；规范制品与兼容门面的关系用 `CocoMybatisPlusStarterDependencyContractTest` 钉住（starter 依赖 `coco-mybatis-plus`，而不是 `coco-feature-mybatis-plus`） |

不要做的事：

- 不要在 starter 里新增 `@Configuration`、Bean 或 `META-INF/spring/*.imports`（`AGENTS.md`：starter 应组合能力、不承载行为）；装配代码放进 autoconfigure 或对应 Feature 模块；
- 不要通过兼容门面（`coco-config`、`coco-feature-runtime`、`coco-feature-*`）间接获得能力；starter 对这两个旧坐标只以 test 作用域引用，用来证明共存时自动配置只注册一次。

验证 starter 改动：

- `CocoStarterSmokeIT`（`coco-spring/coco-spring-boot-starter/src/test/java/io/github/coco/spring/boot/`）以 starter 默认值启动一个 Servlet 应用，断言 `cocoStartupBanner` Bean 存在，且能取到 `CocoMessageService` 与 `CocoWebRequestContextResolver`；
- `CocoSpringCompositionClasspathTest`（见 §2.3）与上述两个依赖契约测试；
- 命令（来自 `AGENTS.md` 的聚焦验证写法）：`mvn -pl :coco-spring-boot-starter -am verify`（`verify` 才会运行 Failsafe 的 IT）。

## 11. 测试

使用 `ApplicationContextRunner`（Servlet 场景 `WebApplicationContextRunner`）：

```java
private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CocoExampleAutoConfiguration.class));
```

- 断言默认 Bean 存在、`enabled=false` 时不装配、自定义 Bean 覆盖默认实现；
- 用 `FilteredClassLoader` 模拟 classpath 缺类（Redis、MyBatis-Plus 等），断言 fail-fast 或降级行为；
- 涉及消息包时，在 runner 中加入 `CocoCommonAutoConfiguration`，参考 `CocoMybatisPlusAutoConfigurationTest`、`CocoDataPermissionAutoConfigurationTest`；
- 修改了 `.imports`/`spring.factories` 时，同时检查 `CocoSpringCompositionClasspathTest` 是否仍通过。

更多约定见 [指南：测试约定](Guide-Testing)。

## 12. 自动配置没有生效时的排查顺序

1. **Feature 是否被禁用**：查看启动日志 `Coco features resolved from ...` 中的 `enabled` / `disabled` / `disabledByDependency`；注意 `features.json`（构建期产出）**优先于**运行期属性。详见 [Feature 机制](Feature-System)。
2. **`AutoConfigurationImportFilter` 是否过滤**：`mybatis-plus` 未启用时会过滤 MyBatis-Plus 的 5 个自动配置，以及在无数据源配置、无嵌入式驱动时过滤 Spring Boot 的 3 个数据源自动配置。
3. **模块级属性**：`coco.<x>.enabled` 的默认值（默认关闭的模块必须显式开启）；Redis 相关还要看 `store-type`。
4. **条件注解**：`@ConditionalOnWebApplication`、`@ConditionalOnClass`、`@ConditionalOnBean` 是否满足；可以打开 Spring Boot 的条件评估报告（通用做法：启动参数 `--debug` 或属性 `debug=true`，本仓库未专门配置）。
5. **是否注册**：模块的 `.imports` 是否包含该 FQCN；`META-INF` 是否被打进 jar；模块是否被 `coco:prune-package` 裁剪掉（查看 `BOOT-INF/lib`）。
6. **顺序**：`before` / `after` 是否导致默认 Bean 先于自定义 Bean 注册，或 `@ConditionalOnMissingBean` 判定提前。
7. **是否有重复注册**：兼容门面或其他 jar 是否复制了 `.imports`。

## 相关页面

- [架构总览](Architecture)
- [Feature 机制：构建期与运行期](Feature-System)
- [约定与规范](Conventions)
- [指南：新增一个 Feature 模块](Guide-Add-Feature-Module)
- [指南：测试约定](Guide-Testing)
- [Spring 组合层：autoconfigure 与 starter](Module-Spring)
- [coco-web：Web 运行时](Module-Web)
- [FAQ 与故障排查](FAQ-Troubleshooting)
- 仓库文档：[`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md)、[`module-layout.md`](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/architecture/module-layout.md)
