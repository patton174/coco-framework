---
title: Web 运行时
---

# Web 运行时

Coco Web 运行时（`coco-feature-web`）负责把散落在控制器里的响应结构、异常处理、链路追踪和请求体读取收敛成一套稳定、可配置的基础设施。它绑定 `coco.web` 命名空间，各能力互相解耦：链路元数据不会污染业务响应结构，异常响应与正常响应遵守同一套响应体规则。

## 统一响应封装

### 功能简介

框架用 `CocoApiResponse<T>` 承载返回给调用方的稳定响应结构，正常响应和异常响应共用同一个模型。字段如下：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `boolean` | 请求是否成功 |
| `code` | `int` | 响应编码（成功码或异常码） |
| `message` | `String` | 响应消息，`null` 时序列化为空串 |
| `data` | `T` | 响应数据 |
| `traceId` | `String` | 请求链路标识，未配置响应体元数据时不序列化 |
| `path` | `String` | 请求路径，仅调试模式输出 |

`traceId` 与 `path` 都标注了 `@JsonInclude(NON_NULL)`：默认情况下它们为 `null`，因此不会出现在 JSON 里。链路标识默认走响应头而非响应体。

一个典型的成功响应：

```json
{
  "success": true,
  "code": 0,
  "message": "success",
  "data": { "id": 1024, "name": "coco" }
}
```

### metadata-mode：链路元数据输出模式

响应体是否额外携带 `traceId` / `path`，由 `coco.web.response.metadata-mode` 控制，对应枚举 `CocoResponseMetadataMode`：

| 取值 | 响应体 traceId | 响应体 path | 说明 |
| --- | --- | --- | --- |
| `NONE`（默认） | 否 | 否 | 不向响应体写入链路字段，链路标识优先走响应头或 Cookie |
| `COOKIE` | 否 | 否 | 仅通过响应 Cookie 输出 TraceId |
| `TRACE` | 是 | 否 | 在响应体中输出 TraceId |
| `DEBUG` | 是 | 是 | 在响应体中同时输出 TraceId 与请求路径，主要用于联调诊断 |

```yaml
coco:
  web:
    response:
      metadata-mode: DEBUG   # 默认 NONE
```

`DEBUG` 模式下的响应体：

```json
{
  "success": false,
  "code": 400,
  "message": "请求参数不合法",
  "data": null,
  "traceId": "9f2c1e7b5a3d4f80",
  "path": "/api/users"
}
```

### 正常响应包装

正常响应包装由 `coco.web.response-wrap` 控制（对应 `CocoResponseWrapProperties`），默认开启，会把控制器返回值自动包装成 `CocoApiResponse`。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `coco.web.response-wrap.enabled` | `true` | 是否启用正常响应包装 |
| `coco.web.response-wrap.success-message-code` | `coco.web.response.success` | 成功消息国际化编码 |
| `coco.web.response-wrap.max-body-bytes` | `-1` | 允许包装的最大原始响应体字节数，负数表示不限制；仅基于已知长度判断，不会为估算大小而提前序列化业务对象 |

## 全局异常处理

### 功能简介

`CocoWebExceptionHandler` 是一个 `@RestControllerAdvice`，把 Web 请求中的异常统一转换成 `CocoApiResponse` 异常响应，并解析国际化消息。它覆盖三类异常：

- **Coco 框架异常**（`CocoException` 及其子类）：按异常类型解析 HTTP 状态和业务响应码，如 `CocoUnauthorizedException` → 401、`CocoForbiddenException` → 403、`CocoNotFoundException` → 404、`CocoConflictException` → 409、`CocoRequestException` → 400、`CocoSystemException` → 500。
- **Spring MVC 请求参数异常**：`BindException`、`MethodArgumentNotValidException`、`HttpMessageNotReadableException`、`MethodArgumentTypeMismatchException`、`MissingServletRequestParameterException`，统一返回 400。
- **其他**：`NoHandlerFoundException` → 404、`HttpRequestMethodNotSupportedException` → 405、未捕获异常 → 500（客户端主动断开的异常会原样抛出，不再包装）。

### 字段级校验错误 CocoFieldError

当参数校验失败并且能提取出字段错误时，异常响应的 `data` 会带上一个 `CocoFieldError` 列表。`CocoFieldError` 是一个只含两个字段的 record：

```java
public record CocoFieldError(String field, String message) {
}
```

对应的 JSON 形态（`data` 为字段错误数组）：

```json
{
  "success": false,
  "code": 400,
  "message": "请求参数不合法",
  "data": [
    { "field": "name", "message": "不能为空" },
    { "field": "age", "message": "必须大于 0" }
  ]
}
```

注意：字段错误当前只从 `BindException` 中提取；若异常没有可提取的字段错误，`data` 保持为 `null`。

## TraceId 链路追踪

### 功能简介

Trace 过滤器为每个请求维护一个 TraceId，用于串联日志和跨系统链路。TraceId 的输入、输出和落地位置都可配置：

- **入站**：从请求头（默认 `X-Trace-Id`）读取上游传入的 TraceId，缺失时自动生成。读取到的 TraceId 会经过长度和字符白名单校验，不合法则丢弃并重新生成。
- **MDC**：写入日志 MDC（默认键 `traceId`），日志模板可直接引用。
- **出站响应头**：默认把 TraceId 回写到响应头（默认 `X-Trace-Id`）。
- **出站 Cookie**：可选，把 TraceId 写入 Cookie（默认 `COCO_TRACE_ID`），支持 Path、Max-Age、HttpOnly、Secure、SameSite 等属性。

### 如何启用与配置

Trace 过滤器由 `coco.web.trace` 控制，默认启用。

```yaml
coco:
  web:
    trace:
      enabled: true
      header-name: X-Trace-Id
      mdc-key: traceId
      response-header-enabled: true
      response-cookie-enabled: false
      cookie-name: COCO_TRACE_ID
      cookie-same-site: Lax
      max-length: 128
      allowed-pattern: "[A-Za-z0-9._:-]+"
```

### 关键配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `coco.web.trace.enabled` | `true` | 是否启用 Trace 过滤器 |
| `coco.web.trace.header-name` | `X-Trace-Id` | 读取和回写 TraceId 的 HTTP 头名称 |
| `coco.web.trace.mdc-key` | `traceId` | 写入日志 MDC 的键名 |
| `coco.web.trace.response-header-enabled` | `true` | 是否把 TraceId 写入响应头 |
| `coco.web.trace.response-cookie-enabled` | `false` | 是否把 TraceId 写入 Cookie |
| `coco.web.trace.cookie-name` | `COCO_TRACE_ID` | TraceId Cookie 名称 |
| `coco.web.trace.cookie-path` | `/` | TraceId Cookie 的 Path |
| `coco.web.trace.cookie-max-age` | `-1` | Cookie Max-Age，负数表示会话级 Cookie |
| `coco.web.trace.cookie-http-only` | `false` | Cookie 是否 HttpOnly |
| `coco.web.trace.cookie-secure` | `false` | Cookie 是否 Secure |
| `coco.web.trace.cookie-same-site` | `Lax` | Cookie SameSite 策略 |
| `coco.web.trace.max-length` | `128` | 允许接收的 TraceId 最大长度，小于等于零时恢复默认 |
| `coco.web.trace.allowed-pattern` | `[A-Za-z0-9._:-]+` | 允许接收的 TraceId 正则表达式 |

在业务代码或日志里获取当前 TraceId：

```java
import io.github.coco.context.trace.CocoTraceContext;

String traceId = CocoTraceContext.getOrCreateTraceId();
```

## 请求体缓存

### 功能简介

Servlet 的请求输入流默认只能读取一次，而签名验签、AES 解密等能力都需要重复读取原始请求体。请求体缓存过滤器把符合条件的请求体读入内存并复用，为后续过滤器提供稳定输入。

缓存有两种触发模式（`CocoRequestBodyCachingMode`）：

- `SECURITY_HEADERS`（默认）：仅当请求携带安全触发头（默认 `content-md5`、`x-coco-sign`、`x-coco-signature`、`x-coco-encrypted`）时才缓存，避免为普通请求付出内存代价。
- `ALWAYS`：对符合方法和内容类型条件的请求始终缓存。

### 如何启用与配置

由 `coco.web.request-body` 控制，默认启用。

```yaml
coco:
  web:
    request-body:
      enabled: true
      mode: SECURITY_HEADERS
      max-cache-bytes: 1048576
      cache-methods: [POST, PUT, PATCH, DELETE]
      included-content-types:
        - application/json
        - application/*+json
        - text/plain
        - application/xml
        - text/xml
```

### 关键配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `coco.web.request-body.enabled` | `true` | 是否启用请求体缓存设施 |
| `coco.web.request-body.mode` | `SECURITY_HEADERS` | 触发模式，见上文 |
| `coco.web.request-body.max-cache-bytes` | `1048576`（1 MB） | 最大缓存字节数，小于等于零时恢复默认 |
| `coco.web.request-body.cache-methods` | `POST, PUT, PATCH, DELETE` | 允许缓存请求体的 HTTP 方法 |
| `coco.web.request-body.trigger-header-names` | `content-md5, x-coco-sign, x-coco-signature, x-coco-encrypted` | `SECURITY_HEADERS` 模式下触发缓存的请求头 |
| `coco.web.request-body.included-content-types` | `application/json, application/*+json, text/plain, application/xml, text/xml` | 允许缓存的内容类型 |
| `coco.web.request-body.excluded-content-type-prefixes` | `multipart/, application/octet-stream` | 排除缓存的内容类型前缀 |

### 注意事项与边界

- **multipart 被排除**：`multipart/`（文件上传）和 `application/octet-stream` 默认在排除前缀里，不会被缓存。这是刻意设计的重要边界——文件上传通常体积大、无法安全地整体读入内存，因此不参与请求体缓存，也不会进入依赖缓存请求体的签名验签、AES 解密链路。若业务需要对上传请求做完整性校验，应采用独立方案。
- **内存上限**：请求体超过 `max-cache-bytes` 时不会被无限读入内存，请根据业务最大报文合理设置该阈值。
- **内容类型匹配**：内容类型在匹配前会去掉 `;` 后的参数（如 charset）并转小写，因此 `application/json;charset=UTF-8` 与 `application/json` 视为一致。

