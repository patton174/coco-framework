---
title: Idempotency
---

# Idempotency

Coco request idempotency (`coco-idempotency`) provides write operations with the guarantee that "the same request key is successfully processed only once." The client carries an `Idempotency-Key` in the request header, and at the MVC interceptor layer the framework acquires a lease for each key: the first request obtains the lease and is allowed through, and after successful processing the key is retained until its TTL expires, after which requests carrying the same key return HTTP 409.

Idempotency binds to the `coco.idempotency` namespace, depends on the Web runtime feature (`web`), and is **disabled by default**. It must be explicitly turned on with `coco.idempotency.enabled=true`. It does not cache or replay the first response, nor does it alter business transaction boundaries.

## Feature Overview

- **Lease-based deduplication**: Each idempotency key corresponds to a lease with a TTL. The key is retained only on successful processing; on failure the key is released to allow retries.
- **Precise retain/release semantics**: When the handler completes normally and the response status is **2xx or 3xx**, the key is retained until the TTL expires, during which the same key returns 409. Whenever an exception is thrown, the response status is **4xx / 5xx**, or the handler does not complete normally, the lease is released, allowing the client to retry with the same key.
- **Only intercepts write methods**: By default it applies only to `POST`, `PUT`, `PATCH`, and `DELETE`, adjustable via `allowed-methods`.
- **Strict key validation**: Key length is limited and only visible ASCII characters (`!` to `~`) are allowed; an illegal or missing key returns HTTP 400.
- **Sensitive header masking**: The `Idempotency-Key` header is registered as a sensitive request header and is not output verbatim in logs.
- **Pluggable storage**: The default in-process store is suitable only for a single instance; both `CocoIdempotencyStore` and `CocoIdempotencyKeyResolver` are replaceable.

## How to Enable and Integrate

Idempotency is controlled by two layers of switches: the feature toggle `web` must be enabled (idempotency depends on the Web runtime), and `coco.idempotency.enabled` must be explicitly turned on. Auto-configuration is only wired up in Servlet applications.

### 1. Turn on the switch

```yaml
coco:
  idempotency:
    enabled: true
    ttl: 24h
    header-name: Idempotency-Key
    allowed-methods:
      - POST
      - PUT
      - PATCH
      - DELETE
```

### 2. Declare it on the handler method

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @CocoIdempotent(namespace = "order-create")
    @PostMapping
    public OrderResponse create(@RequestBody CreateOrderRequest request) {
        // The same Idempotency-Key will succeed only once
        return this.orderService.create(request);
    }
}
```

`@CocoIdempotent` can annotate a type or a method, and the method annotation takes precedence over the class annotation. `namespace` is used for logical isolation; an empty value means the `default` namespace. When `ttlSeconds` is negative, the global `coco.idempotency.ttl` is used.

### 3. Client carries the idempotency key

The client generates a stable and unique key for each logical operation (such as a UUID) and places it in the request header:

```
POST /api/orders HTTP/1.1
Idempotency-Key: 6f9619ff-8b86-d011-b42d-00cf4fc964ff
Content-Type: application/json

{"productId": 42, "quantity": 1}
```

Retries of the same operation must reuse the same key; different operations must use different keys.

## Usage Examples

### Request semantics and status codes

| Scenario | Behavior | Status code / business code |
|------|------|------|
| First request, processed successfully (2xx/3xx) | Obtains the lease, is allowed through, key retained until TTL | The business handler's own status |
| Same key, previous request succeeded and not yet expired | Rejected, treated as a duplicate | HTTP 409 / `40910` |
| Request processing throws an exception or returns 4xx/5xx | Releases the lease, allows retry with the same key | The business handler's own status |
| Missing or illegal `Idempotency-Key` | Rejected | HTTP 400 / `40010` |
| Storage unavailable | fail-closed rejection | HTTP 503 / `50310` |

Key point: **only success (2xx/3xx) locks the key**. This means a failed request will not block subsequent retries with the same key, matching the intuition of "safe retry."

### Replacing shared storage for cluster deployment

The state of the in-process store exists only within the current JVM. In a multi-instance deployment, keys of each instance are invisible to the others, so cross-instance deduplication is impossible. A multi-instance risk warning is emitted when enabled. Production multi-instance deployments must switch to shared storage:

```yaml
coco:
  idempotency:
    enabled: true
    store-type: redis
    redis:
      key-prefix: "coco:idempotency:"
```

Or provide a custom `CocoIdempotencyStore` Bean to override the default implementation.

## Key Configuration Items

Prefix `coco.idempotency`.

| Configuration item | Type | Default | Description |
|--------|------|--------|------|
| `enabled` | boolean | `false` | Whether to enable request idempotency. |
| `header-name` | string | `Idempotency-Key` | Name of the request header carrying the idempotency key. |
| `ttl` | Duration | `24h` | Default retention duration for a successful key; can be overridden by the annotation's `ttlSeconds`. |
| `max-key-length` | int | `128` | Maximum idempotency key length; over-length is treated as an illegal key. |
| `max-entries` | int | `100000` | Maximum number of active keys in the in-process store. |
| `cleanup-interval` | Duration | `1m` | Background cleanup interval for expired keys; zero disables the background cleanup thread. |
| `allowed-methods` | list | `POST`, `PUT`, `PATCH`, `DELETE` | HTTP methods that participate in idempotency protection. |
| `store-type` | enum | `in-memory` | Storage type, either `in-memory` or `redis`. |
| `redis.key-prefix` | string | `coco:idempotency:` | Redis key prefix. |
| `redis.template-bean-name` | string | empty | Specifies the RedisTemplate Bean name; empty uses the default. |

## Boundary Considerations

- **Only effective in Servlet applications**: Idempotency depends on the `web` feature, and the interceptor is only registered in a Servlet environment.
- **Disabled by default**: Unlike rate limiting, even if the `web` feature is enabled, you must still explicitly set `coco.idempotency.enabled=true`.
- **Does not replay the first response**: The framework only guarantees that the same key is not processed repeatedly; it does not cache and replay the body of the first response. A duplicate request gets a 409, not the content of the first response.
- **Does not alter transaction boundaries**: Idempotency is decoupled from business transactions. Whether the transaction inside the business handler commits is still controlled by itself; lease release is determined based on the HTTP response status.
- **The key must be guaranteed stable and unique by the client**: Retries of the same operation reuse the same key, and different operations use different keys; otherwise a request may be wrongly judged as a duplicate or missed.
- **The in-process store cannot be used in a cluster**: In a multi-instance deployment, always replace it with a shared `CocoIdempotencyStore`, otherwise cross-instance deduplication is impossible.
- **fail-closed semantics**: When storage is unavailable it returns 503 rather than allowing through, so you must ensure the availability of the shared storage.
