# Coco Rate Limit Module Specification

## Scope

`coco-rate-limit` is an opt-in Servlet Web infrastructure module. It is not included by the normal starter, BOM, standard feature set, or generated feature plan. An application enables it only by depending on the module and setting `coco.rate-limit.enabled=true`.

## Configuration and Matching

The module has no implicit global rule. `coco.rate-limit.routes` defines an ordered list of route identifiers, Coco Web method/path matchers, per-window limits, and fixed-window durations. A fixed window must be between one second and 366 days. The first matching route is applied. Invalid configured routes fail application startup rather than silently broadening or disabling protection.

`@CocoRateLimited("route-id")` declares a configured route as a handler fallback. A matching path route always wins and is applied once in the Servlet Filter; only a request with no path-route match reaches the MVC fallback interceptor. On a handler, a method annotation wins over a class annotation. An annotation never creates a route, reads a business identity, or enters a business transaction.

## Identity, Proxy Safety, and Storage

The default `CocoRateLimitKeyResolver` uses the client IP from `CocoWebRequestSnapshot`; it does not parse forwarding headers itself. Coco Web's trusted-proxy configuration remains the sole authority deciding whether forwarding headers are accepted. Applications can replace the resolver with a Bean only when they can derive a verified application, device, or customer key.

`CocoRateLimitStore` is an atomic SPI. It must perform the count, upper-bound decision, and TTL write in one storage operation. `InMemoryCocoRateLimitStore` is the single-JVM reference implementation: it serializes the complete in-memory operation, has bounded active-key capacity, clears expired entries on a scheduled daemon task, and warns whenever instantiated. Multi-instance production deployments must provide a shared store implementation. Runtime failures from key resolution or storage are propagated to the caller; they are not converted into a normal 429 rate-limit decision.

## HTTP Contract

The filter executes before controller invocation and any business transaction. Matching requests receive `RateLimit-Limit`, `RateLimit-Remaining`, and `RateLimit-Reset`; rejected requests additionally receive `Retry-After`, return HTTP 429, and use Coco's existing exception handler for the normal response body, business code `42900`, and message-bundle localization.
