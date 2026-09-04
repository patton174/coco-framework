---
title: Infrastructure Conventions
---

# Infrastructure Conventions

On top of Spring Boot, Coco consolidates a set of infrastructure conventions that "should just be configured this way by default": Jackson serialization behavior, async thread pools and context propagation, internationalized messages, and the logging mechanism. They are spread across modules such as `coco-spring-boot-autoconfigure`, `coco-i18n`, and `coco-logging`, and are configured through their respective `coco.*` namespaces. The goal is to give business projects safe, consistent default behavior out of the box.

## Jackson Conventions

### Feature Overview

`CocoJacksonAutoConfiguration` registers a `JsonMapperBuilderCustomizer` **before** Spring Boot's `JacksonAutoConfiguration`, uniformly customizing serialization behavior. It targets Jackson 3.x (the `tools.jackson.*` package) and has two core conventions:

- **`Long` serialized as a string**: Enabled by default (`long-to-string=true`). JavaScript's `Number` cannot safely represent integers exceeding 53 bits; returning snowflake IDs or large auto-increment primary keys directly as numbers to the frontend loses precision. When enabled, both `Long` and `long` are output as strings via `ToStringSerializer`, avoiding frontend precision loss at the source.
- **Unknown property policy**: `FAIL_ON_UNKNOWN_PROPERTIES` is disabled by default (`fail-on-unknown-properties=false`), so deserialization does not error on unknown fields, improving compatibility as frontend and backend fields evolve.

Jackson 3.x has built-in `java.time` support and outputs date-times in ISO format by default, with no need to register additional modules.

### Key Configuration Items

Bound to the prefix `coco.jackson` (corresponding to `CocoJacksonProperties`):

| Configuration item | Type | Default | Description |
| --- | --- | --- | --- |
| `coco.jackson.long-to-string` | `boolean` | `true` | Whether to serialize `Long` / `long` as strings to prevent frontend precision loss |
| `coco.jackson.fail-on-unknown-properties` | `boolean` | `false` | Whether to throw an exception when an unknown property is encountered during deserialization |
| `coco.jackson.write-dates-as-timestamps` | `boolean` | `false` | Whether to serialize date types as timestamps |

## Async Thread Pool and Context Propagation

### Feature Overview

`CocoAsyncAutoConfiguration` automatically creates a `ThreadPoolTaskExecutor` named `cocoTaskExecutor` and equips it with `CocoContextTaskDecorator`, letting async tasks access the request context and Trace context of the submitting thread.

`CocoContextTaskDecorator` captures a snapshot of the current thread's request context (`CocoRequestContextHolder`), Trace context (`CocoTraceContext`), and pagination context (`CocoPageContextHolder`) **when the task is submitted**, restores them **before the worker thread executes the task**, and then reverts the worker thread's original context after the task finishes. This way `traceId`, request context, pagination parameters, and so on can pass through thread boundaries, allowing async logs and main-thread logs to be strung onto the same trace.

### Usage Example

Designate `cocoTaskExecutor` as the executor for `@Async`, and the async method automatically gains context propagation:

```java
@Service
public class ReportService {

    @Async("cocoTaskExecutor")
    public void generateAsync(String reportId) {
        // The submitting thread's traceId and request context can still be read here
    }
}
```

When you need custom thread pool behavior, declare your own Bean named `cocoTaskExecutor` to override the default implementation (`@ConditionalOnMissingBean(name = "cocoTaskExecutor")`).

### Key Configuration Items

Bound to the prefix `coco.async` (corresponding to `CocoAsyncProperties`):

| Configuration item | Type | Default | Description |
| --- | --- | --- | --- |
| `coco.async.enabled` | `boolean` | `true` | Whether to enable the framework's built-in async thread pool |
| `coco.async.core-pool-size` | `int` | `8` | Core thread count |
| `coco.async.max-pool-size` | `int` | `32` | Maximum thread count |
| `coco.async.queue-capacity` | `int` | `1000` | Task queue capacity |
| `coco.async.thread-name-prefix` | `String` | `coco-async-` | Thread name prefix |

## Internationalized Messages

### Feature Overview

`CocoI18nProperties` binds to the `coco.common.i18n` namespace, controlling message resource bundles, the default language, and the missing-message policy. Each framework module registers its own message resources through `CocoMessageBundleRegistrar` (such as the storage module's `coco-storage-messages`), and business teams can also append their own basename. The default language is Simplified Chinese, and when a message is missing the code is used as the fallback message by default, avoiding throwing errors directly.

### Key Configuration Items

Bound to the prefix `coco.common.i18n` (corresponding to `CocoI18nProperties`):

| Configuration item | Type | Default | Description |
| --- | --- | --- | --- |
| `coco.common.i18n.basename` | `List<String>` | `[coco-messages]` | List of message resource basenames; passing empty falls back to the default |
| `coco.common.i18n.default-locale` | `Locale` | `zh-CN` (Simplified Chinese) | Default language |
| `coco.common.i18n.fallback-to-system-locale` | `boolean` | `false` | Whether to fall back to the system language when a message is not found |
| `coco.common.i18n.use-code-as-default-message` | `boolean` | `true` | Whether to use the code as the default message when the message resource is missing |

## Logging Mechanism

### Feature Overview

`CocoLoggingProperties` binds to the `coco.logging` namespace, controlling Coco's default console log format, noise reduction of Spring's original startup logs, async log output, the Node terminal renderer, and access logs. Among them:

- **Console format**: By default it uses a pattern string with color highlighting that includes the time, level, the `COCO` marker, logger, and thread name.
- **Spring noise reduction**: By default it reduces the noise of Spring's and the container's original startup logs.
- **Async logging**: Enabled by default; framework logs are written out asynchronously through an in-process bounded queue, reducing the blocking of business threads by log I/O. The queue defaults to a capacity of 1024, falling back to the default when configured as a non-positive value.
- **Node terminal renderer**: Enabled by default only in the `java -jar` startup scenario, using a Node.js subprocess to take over console output for polished rendering.

### Key Configuration Items

Bound to the prefix `coco.logging` (corresponding to `CocoLoggingProperties`):

| Configuration item | Type | Default | Description |
| --- | --- | --- | --- |
| `coco.logging.enabled` | `boolean` | `true` | Whether to enable Coco's default logging mechanism |
| `coco.logging.quiet-spring` | `boolean` | `true` | Whether to reduce the noise of Spring's and the container's original startup logs |
| `coco.logging.console-pattern` | `String` | see below | Default console log format; passing empty falls back to the default |
| `coco.logging.async.enabled` | `boolean` | `true` | Whether to enable async log output |
| `coco.logging.async.queue-capacity` | `int` | `1024` | Async log queue capacity; a non-positive value falls back to the default |
| `coco.logging.node-renderer.enabled` | `boolean` | `true` | Whether to enable the Node terminal log renderer |
| `coco.logging.node-renderer.jar-only` | `boolean` | `true` | Whether to take over log output only in the `java -jar` scenario |
| `coco.logging.node-renderer.command` | `String` | `node` | Node.js command; empty falls back to `node` |
| `coco.logging.node-renderer.color` | `String` | `always` | Color mode, supporting `always` / `auto` / `never` |

Default console format:

```text
%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %highlight(%-5level) %clr(COCO){cyan} %clr(%logger{32}){magenta} %clr([%thread]){faint} : %msg%n%wEx
```

## Boundary Considerations

- Jackson customization takes effect only when Jackson 3.x's `JsonMapper` is present on the classpath, and it targets the `tools.jackson.*` package; projects still using Jackson 2.x (`com.fasterxml.jackson.*`) are not covered by this convention.
- `long-to-string` being enabled by default causes all `Long` fields to be returned as strings, so the frontend and interface contract must handle them as strings; you can disable this option for scenarios that genuinely require a numeric type.
- Context propagation captures the snapshot **when the task is submitted**, so the submission action must happen on a thread holding the context (such as a Web request thread); a task submitted outside the request context will not gain context out of nowhere.
- The Node terminal renderer relies on an available `node` command in the runtime environment and by default only takes over output when running as a packaged jar; running from an IDE during development is not affected.
