---
title: 基础设施约定
---

# 基础设施约定

Coco 在 Spring Boot 之上收敛了一批“默认就该这么配”的基础设施约定：Jackson 序列化行为、异步线程池与上下文传播、国际化消息、日志机制。它们分散在 `coco-spring-boot-autoconfigure`、`coco-i18n`、`coco-logging` 等模块，通过各自的 `coco.*` 命名空间配置，目标是让业务项目开箱即得到安全、一致的默认行为。

## Jackson 约定

### 功能简介

`CocoJacksonAutoConfiguration` 在 Spring Boot 的 `JacksonAutoConfiguration` **之前**注册一个 `JsonMapperBuilderCustomizer`，统一定制序列化行为。它面向 Jackson 3.x（包 `tools.jackson.*`），核心约定有两条：

- **`Long` 序列化为字符串**：默认开启（`long-to-string=true`）。JavaScript 的 `Number` 无法安全表示超过 53 位的整数，雪花 ID、大自增主键直接以数字返回前端会丢精度。开启后 `Long`、`long` 都通过 `ToStringSerializer` 输出为字符串，从根源规避前端精度丢失。
- **未知属性策略**：默认关闭 `FAIL_ON_UNKNOWN_PROPERTIES`（`fail-on-unknown-properties=false`），反序列化遇到未知字段不报错，提升前后端字段演进时的兼容性。

Jackson 3.x 内置 `java.time` 支持，默认以 ISO 格式输出日期时间，无需额外注册模块。

### 关键配置项

绑定前缀 `coco.jackson`（对应 `CocoJacksonProperties`）：

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `coco.jackson.long-to-string` | `boolean` | `true` | 是否将 `Long` / `long` 序列化为字符串，防前端精度丢失 |
| `coco.jackson.fail-on-unknown-properties` | `boolean` | `false` | 反序列化遇到未知属性是否抛异常 |
| `coco.jackson.write-dates-as-timestamps` | `boolean` | `false` | 是否将日期类型序列化为时间戳 |

## 异步线程池与上下文传播

### 功能简介

`CocoAsyncAutoConfiguration` 自动创建名为 `cocoTaskExecutor` 的 `ThreadPoolTaskExecutor`，并为它装上 `CocoContextTaskDecorator`，让异步任务能访问提交线程的请求上下文与 Trace 上下文。

`CocoContextTaskDecorator` 在**提交任务时**捕获当前线程的请求上下文（`CocoRequestContextHolder`）、Trace 上下文（`CocoTraceContext`）和分页上下文（`CocoPageContextHolder`）快照，在**工作线程执行任务前**恢复，任务结束后再还原工作线程原有上下文。这样 `traceId`、请求上下文、分页参数等能透传过线程边界，异步日志与主线程日志能串到同一条链路上。

### 使用示例

把 `cocoTaskExecutor` 指定为 `@Async` 的执行器，异步方法即自动获得上下文传播：

```java
@Service
public class ReportService {

    @Async("cocoTaskExecutor")
    public void generateAsync(String reportId) {
        // 此处仍可读取到提交线程的 traceId 与请求上下文
    }
}
```

需要自定义线程池行为时，声明自己的名为 `cocoTaskExecutor` 的 Bean 即可覆盖默认实现（`@ConditionalOnMissingBean(name = "cocoTaskExecutor")`）。

### 关键配置项

绑定前缀 `coco.async`（对应 `CocoAsyncProperties`）：

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `coco.async.enabled` | `boolean` | `true` | 是否启用框架内置异步线程池 |
| `coco.async.core-pool-size` | `int` | `8` | 核心线程数 |
| `coco.async.max-pool-size` | `int` | `32` | 最大线程数 |
| `coco.async.queue-capacity` | `int` | `1000` | 任务队列容量 |
| `coco.async.thread-name-prefix` | `String` | `coco-async-` | 线程名称前缀 |

## 国际化消息

### 功能简介

`CocoI18nProperties` 绑定 `coco.common.i18n` 命名空间，控制消息资源包、默认语言和缺省消息策略。框架各模块把自己的消息资源通过 `CocoMessageBundleRegistrar` 注册进来（如存储模块的 `coco-storage-messages`），业务方也可以追加自己的 basename。默认语言为简体中文，消息缺失时默认用编码作为兜底消息，避免直接抛错。

### 关键配置项

绑定前缀 `coco.common.i18n`（对应 `CocoI18nProperties`）：

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `coco.common.i18n.basename` | `List<String>` | `[coco-messages]` | 消息资源 basename 列表；传空会回退默认值 |
| `coco.common.i18n.default-locale` | `Locale` | `zh-CN`（简体中文） | 默认语言 |
| `coco.common.i18n.fallback-to-system-locale` | `boolean` | `false` | 找不到消息时是否回退到系统语言 |
| `coco.common.i18n.use-code-as-default-message` | `boolean` | `true` | 消息资源缺失时是否用编码作为默认消息 |

## 日志机制

### 功能简介

`CocoLoggingProperties` 绑定 `coco.logging` 命名空间，控制 Coco 默认控制台日志格式、Spring 原始启动日志降噪、异步日志输出、Node 终端渲染器以及访问日志。其中：

- **控制台格式**：默认使用带颜色高亮、包含时间、级别、`COCO` 标记、logger、线程名的模式串。
- **Spring 降噪**：默认降低 Spring 与容器的原始启动日志噪音。
- **异步日志**：默认开启，框架日志通过进程内有界队列异步写出，减少日志 I/O 对业务线程的阻塞；队列默认容量 1024，配置为非正数时回退默认值。
- **Node 终端渲染器**：默认仅在 `java -jar` 启动场景启用，用一个 Node.js 子进程接管控制台输出做美化渲染。

### 关键配置项

绑定前缀 `coco.logging`（对应 `CocoLoggingProperties`）：

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `coco.logging.enabled` | `boolean` | `true` | 是否启用 Coco 默认日志机制 |
| `coco.logging.quiet-spring` | `boolean` | `true` | 是否降低 Spring 与容器原始启动日志噪音 |
| `coco.logging.console-pattern` | `String` | 见下 | 默认控制台日志格式；传空回退默认值 |
| `coco.logging.async.enabled` | `boolean` | `true` | 是否启用异步日志输出 |
| `coco.logging.async.queue-capacity` | `int` | `1024` | 异步日志队列容量；非正数回退默认值 |
| `coco.logging.node-renderer.enabled` | `boolean` | `true` | 是否启用 Node 终端日志渲染器 |
| `coco.logging.node-renderer.jar-only` | `boolean` | `true` | 是否仅在 `java -jar` 场景接管日志输出 |
| `coco.logging.node-renderer.command` | `String` | `node` | Node.js 命令；为空回退 `node` |
| `coco.logging.node-renderer.color` | `String` | `always` | 颜色模式，支持 `always` / `auto` / `never` |

默认控制台格式：

```text
%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} %highlight(%-5level) %clr(COCO){cyan} %clr(%logger{32}){magenta} %clr([%thread]){faint} : %msg%n%wEx
```

## 边界注意事项

- Jackson 定制仅在类路径存在 Jackson 3.x 的 `JsonMapper` 时生效，且面向 `tools.jackson.*` 包；沿用 Jackson 2.x（`com.fasterxml.jackson.*`）的项目不适用本约定。
- `long-to-string` 默认开启会让所有 `Long` 字段以字符串返回，前端与接口契约需按字符串处理；确需数字类型的场景可关闭该项。
- 上下文传播在**任务提交时**捕获快照，因此提交动作必须发生在持有上下文的线程（如 Web 请求线程）中；脱离请求上下文提交的任务不会凭空获得上下文。
- Node 终端渲染器依赖运行环境具备可用的 `node` 命令，默认只在打包成 jar 运行时接管输出；开发期 IDE 运行不受影响。
