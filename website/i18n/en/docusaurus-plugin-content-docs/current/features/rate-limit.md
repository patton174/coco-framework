---
title: Rate Limiting
---

# Rate Limiting

Coco rate limiting (`coco-rate-limit`) applies request quota control at the Servlet entry point to explicitly declared routes. It uses the **fixed-window counter** algorithm: each rate-limit key accumulates a count within an aligned time window, requests are rejected once the limit is reached, and the count resets to zero after the window rolls over. It is neither a token bucket nor a sliding window, so a short-lived double burst may occur near the window boundary, which is an inherent characteristic of the fixed-window algorithm.

Rate limiting binds the `coco.rate-limit` namespace and depends on the Web runtime feature (`web`). It is disabled by default; even when enabled, only the routes you explicitly declare in `coco.rate-limit.routes` are intercepted, and it does not apply to all requests.

## Overview

- **Fixed-window counting**: aligns window boundaries with a period of `windowSeconds`, allows `limit` requests within a window, and returns HTTP 429 when exceeded.
- **Two execution paths sharing the same counting semantics**: a path-matching Servlet Filter executes at the frontmost position; the `@CocoRateLimited` annotation goes through an MVC interceptor fallback path. When the Filter has already matched by path and consumed the quota, the annotation interceptor does not deduct again, avoiding counting the same request twice.
- **fail-closed (reject on failure)**: when key resolution or storage throws an exception, or storage capacity is exhausted, it is treated as a rejection and returns HTTP 503 rather than letting the request through.
- **Standard rate-limit response headers**: whether allowed or rejected, quota-related response headers are written out, making it easy for clients to back off adaptively.
- **Replaceable storage and key resolution**: the default in-process storage suits only single-instance deployments; both `CocoRateLimitStore` and `CocoRateLimitKeyResolver` are replaceable.

## How to Enable

Rate limiting is controlled by two layers of switches: the feature toggle `web` (rate limiting depends on the Web runtime) must be on, and `coco.rate-limit.enabled` must be explicitly turned on. The property is disabled by default, avoiding automatically enabling rate limiting after an upgrade.

### 1. Turn on the switch and declare routes

```yaml
coco:
  rate-limit:
    enabled: true
    routes:
      - id: login
        limit: 5
        window-seconds: 60
        matcher:
          methods:
            - POST
          path-patterns:
            - /api/auth/login
      - id: public-read
        limit: 100
        window-seconds: 60
        matcher:
          path-patterns:
            - /api/public/**
```

`matcher.path-patterns` uses Spring Ant-style patterns; an empty `methods` means it matches all HTTP methods. For a route to take effect it must simultaneously satisfy: a non-empty `id`, at least one non-empty `path-pattern`, `limit > 0`, and `windowSeconds` between 1 second and 366 days.

### 2. (Optional) Express business intent with an annotation

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @CocoRateLimited("login")
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        // ...
    }
}
```

`@CocoRateLimited` only expresses the intent that "this handler method is expected to be protected by some route"; it **does not create an implicit route**, nor does it read user, role, or transaction state. The actual interception rules are still explicitly configured by `coco.rate-limit.routes`. `value` and `route` are aliases for each other, and it can be annotated on a type or a method.

## Usage Examples

### Rate-limit response headers

Allowed requests carry the following response headers (the `X-` prefix is a compatibility alias):

```
RateLimit-Limit: 100
RateLimit-Remaining: 87
RateLimit-Reset: 42
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 87
X-RateLimit-Reset: 42
```

`RateLimit-Reset` is the number of seconds remaining until the current window resets. When rejected, it additionally carries `Retry-After` (at least 1 second):

```
HTTP/1.1 429 Too Many Requests
RateLimit-Limit: 5
RateLimit-Remaining: 0
RateLimit-Reset: 18
Retry-After: 18
Content-Type: application/json

{"code":42900,"message":"..."}
```

Quota exhaustion returns business code `42900` (HTTP 429); key resolution or storage being unavailable, or capacity exhaustion, returns business code `50300` (HTTP 503).

### Replacing shared storage for cluster deployment

The state of in-process storage only exists in the current JVM. Under a multi-instance deployment, each instance's quota is independent, which is equivalent to inflating the total quota. A multi-instance risk warning is emitted when enabled. Production multi-instance deployments need to switch to shared storage:

```yaml
coco:
  rate-limit:
    enabled: true
    store-type: redis
    redis:
      key-prefix: "coco:rate-limit:"
```

Or provide a custom `CocoRateLimitStore` Bean to override the default implementation.

### Client identification behind a reverse proxy

The default key resolver (`DefaultCocoRateLimitKeyResolver`) **uses only the remote address reported by the Servlet container** and never trusts client-spoofable request headers such as `X-Forwarded-For`. When deployed behind a trusted reverse proxy, you need to explicitly declare the trusted proxy boundary, and only then will the resolver take the first non-proxy address from the forwarding chain, scanning from right to left along the trust boundary:

```yaml
coco:
  rate-limit:
    trusted-proxy:
      remote-addresses:
        - 10.0.0.1
        - 10.0.0.2
```

## Key Configuration Items

Prefix `coco.rate-limit`.

| Configuration item | Type | Default | Description |
|--------|------|--------|------|
| `enabled` | boolean | `false` | Whether to enable rate limiting. |
| `routes` | list | empty | The list of explicit rate-limit routes; only routes in the list are intercepted. |
| `routes[].id` | string | — | Route identifier, corresponding to the `route` of `@CocoRateLimited`. |
| `routes[].limit` | long | `100` | The number of requests allowed within a single window. |
| `routes[].window-seconds` | long | `60` | Fixed-window duration (seconds), ranging from 1 to 366 days. |
| `routes[].matcher.methods` | list | empty (all methods) | The HTTP methods to match. |
| `routes[].matcher.path-patterns` | list | empty | Ant-style path patterns; at least one must be non-empty to be valid. |
| `store-type` | enum | `in-memory` | Storage type, either `in-memory` or `redis`. |
| `in-memory.max-entries` | int | `10000` | The maximum number of active rate-limit keys in in-process storage. |
| `in-memory.cleanup-interval-seconds` | int | `60` | The background cleanup interval for expired keys (seconds). |
| `redis.key-prefix` | string | `coco:rate-limit:` | The Redis key prefix. |
| `redis.template-bean-name` | string | empty | Specifies the RedisTemplate Bean name; the default is used when empty. |
| `filter.excluded-path-patterns` | list | `/actuator`, `/actuator/**`, `/health`, `/health/**` | Paths the Filter skips, to avoid monitoring requests consuming the business quota. |
| `trusted-proxy.remote-addresses` | list | empty | Trusted reverse proxy addresses; empty is a secure default that parses no forwarding headers. |

## Boundary Considerations

- **Takes effect only in Servlet applications**: rate limiting depends on the `web` feature, and both the Filter and the MVC interceptor are registered only in a Servlet environment.
- **Boundary bursts of fixed windows**: because windows are aligned rather than sliding, close to `2 × limit` requests can theoretically pass at the junction of two adjacent windows; scenarios requiring strict smooth rate limiting need to evaluate this themselves.
- **An annotation is not equivalent to configuration**: `@CocoRateLimited` does not generate a route. When you forget to declare the corresponding `id` in `coco.rate-limit.routes`, the annotation produces no interception effect.
- **In-process storage cannot be used for clusters**: under multiple instances, be sure to replace it with a shared `CocoRateLimitStore`, otherwise the total quota is inflated.
- **fail-closed semantics**: when storage throws an exception or capacity is exhausted, it returns 503 rather than letting the request through. You need to ensure the availability of the shared storage.
- **Forwarding headers are not trusted by default**: when `trusted-proxy.remote-addresses` is not configured, all clients behind a proxy are identified as the same remote address (the proxy address), which may cause false rate limiting. In production, be sure to configure it according to the actual topology or replace the key resolver.
