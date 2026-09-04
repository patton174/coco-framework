---
title: Web Runtime
---

# Web Runtime

The Coco Web Runtime (`coco-feature-web`) consolidates the response structure, exception handling, tracing, and request-body reading that would otherwise be scattered across controllers into a stable, configurable set of infrastructure. It is bound to the `coco.web` namespace, and its capabilities are decoupled from one another: trace metadata does not pollute the business response structure, and exception responses follow the same response-body rules as normal responses.

## Unified response wrapping

### Overview

The framework uses `CocoApiResponse<T>` to carry the stable response structure returned to the caller, with normal responses and exception responses sharing the same model. Its fields are as follows:

| Field | Type | Description |
| --- | --- | --- |
| `success` | `boolean` | Whether the request succeeded |
| `code` | `int` | Response code (success code or error code) |
| `message` | `String` | Response message; serialized as an empty string when `null` |
| `data` | `T` | Response data |
| `traceId` | `String` | Request trace identifier; not serialized when response-body metadata is not configured |
| `path` | `String` | Request path; output only in debug mode |

Both `traceId` and `path` are annotated with `@JsonInclude(NON_NULL)`: by default they are `null`, so they do not appear in the JSON. The trace identifier goes through the response header rather than the response body by default.

A typical success response:

```json
{
  "success": true,
  "code": 0,
  "message": "success",
  "data": { "id": 1024, "name": "coco" }
}
```

### metadata-mode: trace metadata output mode

Whether the response body additionally carries `traceId` / `path` is controlled by `coco.web.response.metadata-mode`, corresponding to the enum `CocoResponseMetadataMode`:

| Value | Body traceId | Body path | Description |
| --- | --- | --- | --- |
| `NONE` (default) | No | No | Does not write trace fields into the response body; the trace identifier goes through the response header or Cookie first |
| `COOKIE` | No | No | Outputs the TraceId only via a response Cookie |
| `TRACE` | Yes | No | Outputs the TraceId in the response body |
| `DEBUG` | Yes | Yes | Outputs both the TraceId and the request path in the response body, mainly for integration diagnostics |

```yaml
coco:
  web:
    response:
      metadata-mode: DEBUG   # NONE by default
```

The response body in `DEBUG` mode:

```json
{
  "success": false,
  "code": 400,
  "message": "Invalid request parameter",
  "data": null,
  "traceId": "9f2c1e7b5a3d4f80",
  "path": "/api/users"
}
```

### Normal response wrapping

Normal response wrapping is controlled by `coco.web.response-wrap` (corresponding to `CocoResponseWrapProperties`) and is enabled by default; it automatically wraps a controller's return value into a `CocoApiResponse`.

| Option | Default | Description |
| --- | --- | --- |
| `coco.web.response-wrap.enabled` | `true` | Whether to enable normal response wrapping |
| `coco.web.response-wrap.success-message-code` | `coco.web.response.success` | The i18n code for the success message |
| `coco.web.response-wrap.max-body-bytes` | `-1` | The maximum raw response-body byte count allowed to be wrapped; a negative number means no limit; the decision is based only on the known length and never serializes the business object early just to estimate its size |

## Global exception handling

### Overview

`CocoWebExceptionHandler` is a `@RestControllerAdvice` that uniformly converts exceptions in Web requests into `CocoApiResponse` exception responses and resolves i18n messages. It covers three categories of exceptions:

- **Coco framework exceptions** (`CocoException` and its subclasses): the HTTP status and business response code are resolved by exception type, e.g. `CocoUnauthorizedException` → 401, `CocoForbiddenException` → 403, `CocoNotFoundException` → 404, `CocoConflictException` → 409, `CocoRequestException` → 400, `CocoSystemException` → 500.
- **Spring MVC request-parameter exceptions**: `BindException`, `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, `MethodArgumentTypeMismatchException`, `MissingServletRequestParameterException` — all uniformly return 400.
- **Others**: `NoHandlerFoundException` → 404, `HttpRequestMethodNotSupportedException` → 405, uncaught exception → 500 (an exception from a client that actively disconnects is rethrown as-is and no longer wrapped).

### Field-level validation errors: CocoFieldError

When parameter validation fails and field errors can be extracted, the exception response's `data` carries a list of `CocoFieldError`. `CocoFieldError` is a record with only two fields:

```java
public record CocoFieldError(String field, String message) {
}
```

The corresponding JSON form (`data` is an array of field errors):

```json
{
  "success": false,
  "code": 400,
  "message": "Invalid request parameter",
  "data": [
    { "field": "name", "message": "must not be empty" },
    { "field": "age", "message": "must be greater than 0" }
  ]
}
```

Note: field errors are currently only extracted from `BindException`; if the exception has no extractable field errors, `data` remains `null`.

## TraceId tracing

### Overview

The trace filter maintains a TraceId for each request, used to correlate logs and cross-system traces. The TraceId's input, output, and storage locations are all configurable:

- **Inbound**: reads the TraceId passed from upstream from the request header (default `X-Trace-Id`), and generates one automatically when it is missing. A TraceId that is read is validated against a length and character whitelist, and is discarded and regenerated if invalid.
- **MDC**: written into the logging MDC (default key `traceId`), which the log template can reference directly.
- **Outbound response header**: by default the TraceId is written back into the response header (default `X-Trace-Id`).
- **Outbound Cookie**: optional; writes the TraceId into a Cookie (default `COCO_TRACE_ID`), supporting attributes such as Path, Max-Age, HttpOnly, Secure, and SameSite.

### How to enable and configure

The trace filter is controlled by `coco.web.trace` and is enabled by default.

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

### Key configuration options

| Option | Default | Description |
| --- | --- | --- |
| `coco.web.trace.enabled` | `true` | Whether to enable the trace filter |
| `coco.web.trace.header-name` | `X-Trace-Id` | The HTTP header name for reading and writing back the TraceId |
| `coco.web.trace.mdc-key` | `traceId` | The key name written into the logging MDC |
| `coco.web.trace.response-header-enabled` | `true` | Whether to write the TraceId into the response header |
| `coco.web.trace.response-cookie-enabled` | `false` | Whether to write the TraceId into a Cookie |
| `coco.web.trace.cookie-name` | `COCO_TRACE_ID` | The TraceId Cookie name |
| `coco.web.trace.cookie-path` | `/` | The Path of the TraceId Cookie |
| `coco.web.trace.cookie-max-age` | `-1` | The Cookie Max-Age; a negative number means a session-level Cookie |
| `coco.web.trace.cookie-http-only` | `false` | Whether the Cookie is HttpOnly |
| `coco.web.trace.cookie-secure` | `false` | Whether the Cookie is Secure |
| `coco.web.trace.cookie-same-site` | `Lax` | The Cookie SameSite policy |
| `coco.web.trace.max-length` | `128` | The maximum length of an accepted TraceId; restores the default when less than or equal to zero |
| `coco.web.trace.allowed-pattern` | `[A-Za-z0-9._:-]+` | The regular expression for an accepted TraceId |

To obtain the current TraceId in business code or logs:

```java
import io.github.coco.context.trace.CocoTraceContext;

String traceId = CocoTraceContext.getOrCreateTraceId();
```

## Request-body caching

### Overview

A servlet's request input stream can be read only once by default, whereas capabilities such as signature verification and AES decryption all need to read the raw request body repeatedly. The request-body caching filter reads qualifying request bodies into memory and reuses them, providing stable input for subsequent filters.

Caching has two trigger modes (`CocoRequestBodyCachingMode`):

- `SECURITY_HEADERS` (default): caches only when the request carries a security trigger header (default `content-md5`, `x-coco-sign`, `x-coco-signature`, `x-coco-encrypted`), avoiding the memory cost for ordinary requests.
- `ALWAYS`: always caches for requests that meet the method and content-type conditions.

### How to enable and configure

Controlled by `coco.web.request-body` and enabled by default.

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

### Key configuration options

| Option | Default | Description |
| --- | --- | --- |
| `coco.web.request-body.enabled` | `true` | Whether to enable the request-body caching facility |
| `coco.web.request-body.mode` | `SECURITY_HEADERS` | The trigger mode, see above |
| `coco.web.request-body.max-cache-bytes` | `1048576` (1 MB) | The maximum cached byte count; restores the default when less than or equal to zero |
| `coco.web.request-body.cache-methods` | `POST, PUT, PATCH, DELETE` | The HTTP methods for which caching the request body is allowed |
| `coco.web.request-body.trigger-header-names` | `content-md5, x-coco-sign, x-coco-signature, x-coco-encrypted` | The request headers that trigger caching in `SECURITY_HEADERS` mode |
| `coco.web.request-body.included-content-types` | `application/json, application/*+json, text/plain, application/xml, text/xml` | The content types allowed to be cached |
| `coco.web.request-body.excluded-content-type-prefixes` | `multipart/, application/octet-stream` | Content-type prefixes excluded from caching |

### Considerations and boundaries

- **multipart is excluded**: `multipart/` (file upload) and `application/octet-stream` are in the excluded prefixes by default and are not cached. This is a deliberately designed, important boundary — file uploads are usually large and cannot safely be read into memory in their entirety, so they do not participate in request-body caching, nor do they enter the signature-verification or AES-decryption chains that depend on a cached request body. If a business needs to perform integrity validation on upload requests, it should adopt a separate solution.
- **Memory ceiling**: when a request body exceeds `max-cache-bytes` it is not read into memory without limit; set this threshold sensibly according to your business's maximum message size.
- **Content-type matching**: before matching, the content type has the parameters after `;` (such as charset) stripped and is lowercased, so `application/json;charset=UTF-8` and `application/json` are treated as equivalent.

