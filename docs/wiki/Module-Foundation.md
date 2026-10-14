# 基础模块：coco-foundation

> 本页回答：`coco-foundation/` 下 6 个模块（`coco-api`、`coco-feature-model`、`coco-i18n`、`coco-exception`、`coco-context`、`coco-logging`）各自提供哪些公开类型、扩展点和配置属性。
> 依据：各模块 `pom.xml` 与 `src/main/java` 实读。Spring 绑定（自动配置）不在本层，见 [Spring 组合层](Module-Spring)。

## 1. 层级定位

`coco-foundation` 放稳定公共契约与不依赖 Spring Web 的基础设施（职责定义见 [`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 与 [`module-layout.md`](https://github.com/patton174/coco-framework/blob/main/coco-support/coco-document/architecture/module-layout.md)）。约束：foundation 不得反向依赖 `coco-spring` 或具体 Feature。

| 模块 | 对 Coco 模块的依赖 | 其他主要依赖 | 测试类数量 |
| --- | --- | --- | --- |
| `coco-api` | 无 | 无 | 2 |
| `coco-context` | 无 | 无 | 4 |
| `coco-i18n` | 无 | `spring-context`、`spring-boot`、`spring-boot-configuration-processor`（optional） | 5 |
| `coco-logging` | 无 | `spring-boot`、`slf4j-api`、`spring-boot-configuration-processor`（optional） | 6 |
| `coco-exception` | `coco-i18n` | 无 | 2 |
| `coco-feature-model` | `coco-api` | `jackson-databind` | 1 |

推荐阅读顺序：`coco-api` → `coco-feature-model` → `coco-i18n` / `coco-exception` / `coco-context` / `coco-logging`。

## 2. coco-api

**坐标与路径**：`io.github.patton174:coco-api`，`coco-foundation/coco-api`。**职责**：业务应用可依赖的稳定公共 API 契约。

| 类型 | 路径（`src/main/java/io/github/coco/api/...`） | 说明 |
| --- | --- | --- |
| `CocoFeature`（枚举） | `feature/CocoFeature.java` | 16 个标准 Feature 标识，`id()` 返回稳定字符串，`fromId(String)` 返回 `Optional`。成员：`WEB`、`MYBATIS_PLUS`、`AUDIT`、`SECURITY`、`TENANT`、`DATA_PERMISSION`、`OPENAPI`、`RATE_LIMIT`、`IDEMPOTENCY`、`SCHEDULING`、`LOCK`、`STORAGE`、`MESSAGING`、`CACHE`、`NOTIFICATION`、`CAPTCHA` |
| `CocoFeatures`（注解） | `feature/CocoFeatures.java` | `@Target(TYPE)`、`@Retention(RUNTIME)`，属性 `enabled()`、`disabled()`（默认空）；同一配置源同时启用与禁用同一功能时，禁用优先 |
| `CocoPage<T>`（record） | `CocoPage.java` | 与持久层无关的分页结果：`items`、`total`、`page`、`size`；`items` 会做防御性拷贝；`totalPages()`、`hasNext()` |
| `CocoPageRequest`（record） | `CocoPageRequest.java` | `page`、`size`（均须 ≥ 1，否则抛 `IllegalArgumentException`）；`of(page, size)` |
| `CocoConfigurer`（接口，`@Deprecated(since="1.0.0")`） | `CocoConfigurer.java` | 早期 Java 配置入口，默认方法 `configureFeatures(CocoFeatureRegistry)`；仅为兼容保留 |
| `CocoFeatureRegistry`（接口，`@Deprecated`）/ `DefaultCocoFeatureRegistry`（`@Deprecated(since="1.0.0")`） | `feature/` | 旧式 Feature 注册器及其内存实现 |

**扩展点**：推荐用 `coco.features.*` 配置或 `@CocoFeatures`。`CocoConfigurer` 与 `CocoFeatureRegistry` 已废弃，JavaDoc 明确建议不要在新代码使用。

**配置 / 自动配置**：无。`CocoFeature` 的启用规则在 `coco-feature-model` 与运行期模块里实现。

> `CocoFeature` 枚举当前是 16 项，`StandardCocoFeatures` 的元数据同为 16 项；内置 Codegen 功能及其 feature id 已从框架移除。Feature 的个数与成员以 `CocoFeature` 枚举为准。

## 3. coco-feature-model

**坐标与路径**：`coco-foundation/coco-feature-model`。**职责**：标准 Feature 元数据、选择、清单、依赖解析，与 Spring 无关（构建期插件和运行期都复用它）。包 `io.github.coco.feature.model`。

| 类型 | 说明 |
| --- | --- |
| `StandardCocoFeatures` | 16 个 Feature 的元数据来源，全部 `defaultEnabled=true`。静态方法：`all()`、`allByFeature()`、`resolve(CocoFeatureSelection)`、`resolveEnabledFeatures(Set<CocoFeature>)`、`toManifest(CocoFeaturePlan, String)`、`fromManifest(CocoFeatureManifest)` |
| `CocoFeatureDefinition`（record） | `feature`、`artifactId`、`autoConfigurationClassName`、`defaultEnabled`、`dependencies`、`pruneArtifactIds`；有一个不带裁剪集合的构造器（裁剪集合默认为自身 artifactId） |
| `CocoFeatureSelection`（record） | `enabled` / `disabled` 两个集合；`empty()`、`of(...)`、`ofEnabled(...)`、`ofDisabled(...)`、`merge(...)`（后者优先） |
| `CocoFeaturePlan`（record） | `enabledFeatures`、`disabledFeatures`、`definitions`；`isEnabled(feature)`、`disabledByDependencyFeatures()` |
| `CocoFeatureManifest` / `CocoFeatureManifestEntry`（record） | 清单模型。`CURRENT_SCHEMA_VERSION="1.1"`，`SUPPORTED_SCHEMA_VERSIONS=["1.0","1.1"]`；`enabledFeatureIds()` |
| `CocoFeatureManifestLoader` | `MANIFEST_LOCATION="META-INF/coco/features.json"`；`load(ClassLoader)`、`read(InputStream)`、`write(manifest)` |

**依赖解析**（`StandardCocoFeatures.resolve`）：先启用全部 `defaultEnabled` 的 Feature → 并入 `selection.enabled` → 移除 `selection.disabled` → 反复移除“依赖未全部启用”的 Feature 直到稳定。例如禁用 `mybatis-plus` 会连带禁用 `tenant` 与 `data-permission`。完整流程见 [Feature 机制：构建期与运行期](Feature-System)。

**资源**：`coco-feature-registry-messages.properties` 与 `_en_US`、`_zh_CN`。注意资源包名仍叫 `coco-feature-registry-messages`（历史名），artifactId 已是 `coco-feature-model`。

**扩展点 / 自动配置**：模块本身无 SPI、无自动配置。它的消息包由 `coco-spring-boot-autoconfigure` 里的 `CocoFeatureRegistryAutoConfiguration` 注册（见 [Spring 组合层](Module-Spring)）。

**测试**：`StandardCocoFeaturesTest` 逐 Feature 断言 id、artifactId、自动配置 FQCN 与依赖。新增 Feature 必须同步更新（见 [指南：新增一个 Feature 模块](Guide-Add-Feature-Module)）。

## 4. coco-i18n

**坐标与路径**：`coco-foundation/coco-i18n`。**职责**：国际化消息基础设施。包 `io.github.coco`（仅 `CocoCommonProperties`）、`io.github.coco.i18n`、`io.github.coco.i18n.internal`。

| 类型 | 说明 |
| --- | --- |
| `CocoMessageService` | 统一消息解析入口：`getMessage(code, args...)`、`getMessage(code, locale, args...)`、`getMessage(CocoMessageCode, ...)`、`getMessageOrDefault(code, default, args...)` 等 |
| `CocoMessageCode`（接口） | `code()`、`defaultMessage()`（默认返回 `code()`）、`message(args...)` |
| `CocoMessage`（record） | `code`、`defaultMessage`、`args`；编码为空会抛 `IllegalArgumentException` |
| `CocoLocaleResolver`（接口） | `resolveLocale()`；默认实现 `internal.DefaultCocoLocaleResolver` |
| `CocoMessageBundleRegistry`（接口） | `add(String basename)`；默认实现 `internal.DefaultCocoMessageBundleRegistry` |
| `CocoMessageBundleRegistrar`（函数式接口） | `registerBundles(CocoMessageBundleRegistry)`；**各模块注册自己消息资源包的扩展点** |
| `CocoCommonProperties` / `CocoI18nProperties` | 配置绑定 |
| `internal.DefaultCocoMessageService` | 默认消息服务实现 |

**配置属性**（前缀 `coco.common`，子树 `i18n`，即 `coco.common.i18n.*`）：

| 键 | 默认值 |
| --- | --- |
| `basename` | `["coco-messages"]` |
| `default-locale` | `Locale.SIMPLIFIED_CHINESE`（`zh_CN`） |
| `fallback-to-system-locale` | `false` |
| `use-code-as-default-message` | `true` |

**资源**：`coco-messages.properties`、`coco-messages_en_US.properties`、`coco-messages_zh_CN.properties`；异常相关的 `coco.error.*` 文本也在这里（例如 `coco.error.unknown`、`coco.error.not-found`）。

**装配方式**（实现在 `CocoCommonAutoConfiguration`）：Bean `cocoMessageSource`（`ResourceBundleMessageSource`）先加入用户配置的 `basename`（排除 `coco-messages`），再依次收集所有 `CocoMessageBundleRegistrar` Bean，最后追加 `coco-messages`。编码固定为 UTF-8。因此业务自定义的 basename 优先于框架消息。

## 5. coco-exception

**坐标与路径**：`coco-foundation/coco-exception`；依赖 `coco-i18n`。**职责**：异常契约与工具。包 `io.github.coco.exception`、`io.github.coco.exception.type`。

| 类型 | 说明 |
| --- | --- |
| `CocoException`（`RuntimeException`） | 框架异常基类。构造器接受 `CocoErrorCode` 或 `CocoBusinessCode`（可带 cause 与消息参数），或直接传字符串 code 与 defaultMessage；访问器 `code()`、`messageCode()`、`businessCode()`（`OptionalInt`）、`args()`、`defaultMessage()`、`message()` |
| `CocoErrorCode`（接口，继承 `CocoMessageCode`） | 异常编码契约；默认方法 `exception(...)`、`request(...)`、`unauthorized(...)` 等直接创建对应异常 |
| `CocoBusinessCode`（接口） | 业务码契约：`int code()`、`String messageCode()`、`defaultMessage()`；同样带 `request(...)` / `unauthorized(...)` / `forbidden(...)` / `notFound(...)` / `conflict(...)` / `system(...)` 默认方法 |
| `CocoCommonErrorCode`（枚举） | 内置编码：`UNKNOWN`、`INVALID_ARGUMENT`、`MISSING_MESSAGE_CODE`、`MISSING_ERROR_CODE`、`UNAUTHORIZED`、`FORBIDDEN`、`NOT_FOUND`、`CONFLICT`、`INTERNAL_ERROR`（消息 key 形如 `coco.error.unknown`） |
| `CocoAssert` | 静态断言：`isTrue`、`state`、`notNull`、`hasText`、`notEmpty`（集合 / Map / 数组），失败时抛 `CocoErrorCode` 对应的异常 |
| `CocoExceptions` / `CocoBusinessExceptions` | 静态工厂：`request` / `unauthorized` / `forbidden` / `notFound` / `conflict` / `system`，分别接 `CocoErrorCode` 与 `CocoBusinessCode`（字符串 code 版本在 `CocoBusinessExceptions`） |
| `type.*` | `CocoRequestException`、`CocoUnauthorizedException`、`CocoForbiddenException`、`CocoNotFoundException`、`CocoConflictException`、`CocoSystemException`，均继承 `CocoException` |

**扩展点**：业务实现自己的 `CocoErrorCode` / `CocoBusinessCode` 枚举即可，HTTP 状态映射在 `coco-web` 的 `CocoExceptionHttpStatusResolver` 里（见 [coco-web](Module-Web)）。各 Feature 模块都有自己的 `*ErrorCode` 枚举（例如 `CocoTenantErrorCode`、`CocoMybatisPlusErrorCode`、`CocoRateLimitErrorCode`）。

**配置 / 自动配置 / 资源**：无。

## 6. coco-context

**坐标与路径**：`coco-foundation/coco-context`，无任何依赖。**职责**：请求上下文与 Trace 基础设施，全部基于 `ThreadLocal`，不依赖 Servlet。包 `io.github.coco.context`、`io.github.coco.context.trace`。

| 类型 | 说明 |
| --- | --- |
| `CocoRequestContext` | 不可变请求上下文：`traceId`、`method`、`path` 与字符串属性 Map；工厂 `of(traceId)` / `of(traceId, method, path)` / `of(traceId, method, path, attributes)`；提供 `clientIp()`、`userAgent()`、`signatureAppId()`、`requestBodySha256()` 等大量按属性键取值的便捷访问器，以及 `clientIpInfo()`、`signatureInfo()`、`encryptionInfo()`、`replayInfo()`、`bodyInfo()`、`browserFingerprintInfo()` 视图 |
| `CocoRequestContextHolder` | `ThreadLocal` 持有器：`current()`（`Optional`）、`set(...)`（同时设置 TraceId）、`clear()`、`capture()`、`restore(snapshot)`、`wrap(Runnable|Callable)`、`wrapSupplier`、`runWithContext(...)` |
| `CocoRequestContextAttributes` | 标准属性键常量（`clientIp`、`userAgent`、`signatureAppId`、`requestBodySha256`、`replayNonce` 等）与前缀构造方法（`header(name)`、`cookie(name)`、`parameter(name)`、`queryParameter(name)`、`payloadParameter(name)`） |
| `CocoRequestBody` / `CocoRequestBrowserFingerprint` / `CocoRequestClientIp` / `CocoRequestEncryption` / `CocoRequestReplay` / `CocoRequestSignature` | 请求体、浏览器指纹、客户端 IP、加密、重放、签名的只读视图（由 `CocoRequestContext` 的 `*Info()` 方法返回） |
| `CocoRequestContextValueCodec` | 属性值编码工具（公开类） |
| `CocoPageContext`（record）/ `CocoPageContextHolder` | 分页上下文：`page`、`size`、`orders`；Holder 提供 `set` / `current` / `clear` / `capture` |
| `CocoSortOrder`（record） | `field`、`ascending` |
| `CocoContextSnapshot` / `CocoContextScope` | 跨线程传播的快照与作用域：`snapshot.restore()` 返回可关闭的 `CocoContextScope`；`wrap(...)`、`compose(...)`、`noop()` |
| `CocoStrings` | 通用字符串工具 |
| `trace.CocoTraceContext` | TraceId 的 `ThreadLocal` 容器：`currentTraceId()`、`getOrCreateTraceId()`、`setTraceId(...)`、`clear()`、`capture()` 等 |
| `trace.CocoTraceIdGenerator` | `generate()` 返回 32 位小写十六进制（UUID 去掉连字符） |

`CocoRequestContextAttributeParser` 是包私有类，不属于公开 API。

**扩展点**：`CocoContextSnapshot` 是线程池、异步任务传播上下文的通用机制；`coco-spring-boot-autoconfigure` 的 `CocoContextTaskDecorator` 用 `CocoContextSnapshot.compose(...)` 组合请求上下文、TraceId 与分页上下文三个快照后包装任务（见 [Spring 组合层](Module-Spring)）。`coco-tenant`、`coco-security`、`coco-data-permission` 的 Holder 也提供同样的 `capture` / `restore` / `wrap` 方法，但 `CocoContextTaskDecorator` 目前不会自动传播它们，需要业务在异步边界自行包装。

**配置 / 自动配置**：无。请求上下文由 `coco-web` 在过滤器里填充。

## 7. coco-logging

**坐标与路径**：`coco-foundation/coco-logging`。**职责**：日志契约、句柄、格式化与默认输出。包 `io.github.coco.logging.{core,access,lifecycle}`。

| 类型 | 说明 |
| --- | --- |
| `core.CocoLogHandle` | 日志句柄：`of(name, loggerName, defaultLevel)`；访问器 `name()`、`loggerName()`、`defaultLevel()` |
| `core.CocoLogHandles` | 内置句柄名常量与注册：`ACCESS`（logger `io.github.coco.access`，INFO）、`EXCEPTION`（`io.github.coco.exception`，WARN）、`LIFECYCLE`（`io.github.coco.lifecycle`，INFO）；`registerDefaults(registry)` |
| `core.CocoLogHandleRegistry` | 句柄注册表：`register`、`find`、`handles` |
| `core.CocoLogHandleRegistrar`（函数式接口） | `register(CocoLogHandleRegistry)`；**Feature 注册自己日志句柄的扩展点**（例如 `coco-audit` 的 `cocoAuditLogHandleRegistrar`） |
| `core.CocoLogManager` | 统一日志入口：`log(handle, level, message, failure)`、`info`、`error`；`defaults()` |
| `core.CocoLogSink`（函数式接口） | `log(CocoLogRecord)`；实现 `Slf4jCocoLogSink`、`AsyncCocoLogSink` |
| `core.CocoAsyncLogDropListener`（函数式接口） | `onDropped(level, handleName, totalDropped)`；默认 `Slf4jCocoAsyncLogDropListener` |
| `core.CocoLogLevel`（枚举） | `OFF`、`ERROR`、`WARN`、`INFO`、`DEBUG`、`TRACE` |
| `core.CocoLogRecord` | 日志记录模型 |
| `access.CocoAccessLog` | 访问日志模型：`traceId`、`method`、`path`、`clientIp`、`clientIpSource`、`userAgent`、`contentType`、`queryString`、`headers`、请求体摘要 / 长度 / 阶段、`browserFingerprint`、`payloadParseStatus`、`requestTargetSource`、`requestParameters`、`status`、`durationMillis`、`success`、`exceptionType`、`failure` |
| `access.CocoAccessLogFormatter`（函数式接口） | `format(accessLog, properties)`；默认 `DefaultCocoAccessLogFormatter` |
| `access.CocoAccessLogRecorder`（函数式接口） | `record(CocoAccessLog)`；默认 `Slf4jCocoAccessLogRecorder`。`coco-audit` 会额外注册一个把访问日志转成审计事件的 recorder |
| `access.CocoAccessLogStyle`（枚举） | `TEXT`、`JSON` |
| `lifecycle.CocoLifecycleLogger` | 应用就绪 / 失败日志：`ready(context, duration)`、`failed(context, exception)` |

**配置属性**：

| 前缀 / 键 | 默认值 |
| --- | --- |
| `coco.logging.enabled` | `true` |
| `coco.logging.quiet-spring` | `true` |
| `coco.logging.console-pattern` | 常量 `CocoLoggingProperties.DEFAULT_CONSOLE_PATTERN` |
| `coco.logging.async.enabled` | `true` |
| `coco.logging.async.queue-capacity` | `1024` |
| `coco.logging.access-log.enabled` | `true` |
| `coco.logging.access-log.level` | `INFO` |
| `coco.logging.access-log.style` | `TEXT` |
| `coco.logging.access-log.logger-name` | 常量 `CocoAccessLogProperties.DEFAULT_LOGGER_NAME` = `io.github.coco.access` |

`coco.logging.enabled` 与 `quiet-spring` 的实际效果由 `coco-spring-boot-autoconfigure` 的 `CocoLoggingEnvironmentPostProcessor` 实现。异步日志溢出的可观测性设计见规格 `coco-support/coco-document/superpowers/specs/2026-07-10-coco-async-log-overflow-observability.md`。

**自动配置**：本模块没有 `AutoConfiguration.imports`；Bean 由 `coco-spring-boot-autoconfigure` 的 `CocoCommonLoggingAutoConfiguration` 创建（见 [Spring 组合层](Module-Spring)）。

## 8. 自动配置 FQCN 说明

按 [`AGENTS.md`](https://github.com/patton174/coco-framework/blob/main/AGENTS.md) 的 Current Architecture 末条，分阶段 2.0 迁移期间 I18n、Logging、Feature Model 保留既有自动配置 FQCN，Spring 绑定已迁到 `coco-spring-boot-autoconfigure`。因此 `io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration`、`io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration`、`io.github.coco.feature.registry.CocoFeatureRegistryAutoConfiguration` 虽然包名带有旧模块痕迹，物理文件都在 autoconfigure 模块。这一点不要凭包名判断源码在哪个 jar。

## 9. 相关页面

- [模块参考总览](Module-Overview)
- [Spring 组合层：autoconfigure 与 starter](Module-Spring)
- [coco-web：Web 运行时](Module-Web)
- [Feature 机制：构建期与运行期](Feature-System)
- [约定与规范](Conventions)
- 官网用户文档：[功能开关](https://github.com/patton174/coco-framework/blob/main/website/docs/feature-toggles.md)、[分页](https://github.com/patton174/coco-framework/blob/main/website/docs/features/pagination.md)
