---
title: Security Context and Security Response Headers
---

# Security Context and Security Response Headers

This chapter covers two related but independent capabilities: first, the security context bridging provided by the security feature module (`coco-feature-security`), which bridges principal information written by a trusted upstream into the current thread; and second, the security response headers and CORS cross-origin configuration provided by the Web module (`coco-feature-web`).

## Security Context Bridging

### Overview

`CocoSecurityContext` holds the security principal of the current caller (`CocoSecurityPrincipal`: principal identifier, display name, role set, permission set, additional attributes) and the authentication state, for authorization, audit, tenant, data permission, and other modules to read. It is stored in the `ThreadLocal` of `CocoSecurityContextHolder`; the entry adapter is responsible for setting it when a request enters and clearing it when the request ends, while business code only reads it.

```java
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityContext;

// Read the current context; returns Optional.empty() when absent
CocoSecurityContextHolder.current()
        .map(CocoSecurityContext::principal)
        .ifPresent(principal -> log(principal.id()));

// Require an existing context; throws an unauthenticated exception when absent
CocoSecurityContext context = CocoSecurityContextHolder.requireCurrent();
```

In the Web scenario, bridging is performed by `CocoSecurityWebFilter`, which calls `CocoWebSecurityContextResolver` to resolve the context from the request. The resolver registered by the framework by default is `HeaderCocoWebSecurityContextResolver`.

### Building the Context from Trusted HTTP Headers

`HeaderCocoWebSecurityContextResolver` **does not authenticate**; it only consumes the request headers already written by a trusted upstream (gateway, authentication filter, business infrastructure) and assembles them into an authenticated `CocoSecurityContext`:

| Request Header | Default Name | Maps To |
| --- | --- | --- |
| Principal identifier | `X-Coco-Principal-Id` | `principal.id` (the context is not built if absent) |
| Principal display name | `X-Coco-Principal-Name` | `principal.name` (falls back to the principal identifier when absent) |
| Role set | `X-Coco-Roles` | `principal.roles` (split by delimiter) |
| Permission set | `X-Coco-Permissions` | `principal.permissions` (split by delimiter) |

Roles and permissions are separated by a comma `,` by default. This resolver is **disabled** by default (`coco.security.web.header.enabled=false`) to avoid directly trusting external client input.

### Must Be Deployed Behind a Trusted Gateway

This is the most critical boundary of this capability: once trusted request-header resolution is enabled, **any request that can reach the application directly can forge headers such as `X-Coco-Principal-Id` to impersonate any principal**. Therefore this resolver **must be deployed behind a trusted gateway**, with the gateway responsible for authentication and for overwriting/stripping these headers; the application must never be exposed directly to the public internet.

To highlight this risk, when `coco.security.web.header.enabled=true`, `CocoSecurityAutoConfiguration` prints a warning log at startup, noting that this resolver trusts upstream request headers and must be placed behind a trusted gateway, and recommends providing a custom `CocoWebSecurityContextResolver` bean to eliminate the warning.

### Replacing with a Custom Resolver

If the principal information comes from a JWT, session, or another authentication mechanism, simply provide a custom `CocoWebSecurityContextResolver` bean to replace the default implementation (the default resolver is annotated with `@ConditionalOnMissingBean` and also suppresses the startup warning above):

```java
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class MySecurityConfig {

    @Bean
    public CocoWebSecurityContextResolver jwtSecurityContextResolver() {
        return request -> resolveFromJwt(request);
    }

    private Optional<CocoSecurityContext> resolveFromJwt(HttpServletRequest request) {
        // Build the principal after validating the JWT
        CocoSecurityPrincipal principal = /* ... */ null;
        return Optional.ofNullable(principal).map(CocoSecurityContext::authenticated);
    }
}
```

### Key Configuration Items

| Configuration Item | Default | Description |
| --- | --- | --- |
| `coco.security.web.enabled` | `true` | Whether to register the Web security context bridging filter |
| `coco.security.web.header.enabled` | `false` | Whether to enable trusted request-header resolution (enabling it trusts upstream headers; must be placed behind a trusted gateway) |
| `coco.security.web.header.principal-id-header-name` | `X-Coco-Principal-Id` | Principal identifier request header |
| `coco.security.web.header.principal-name-header-name` | `X-Coco-Principal-Name` | Principal display name request header |
| `coco.security.web.header.roles-header-name` | `X-Coco-Roles` | Role set request header |
| `coco.security.web.header.permissions-header-name` | `X-Coco-Permissions` | Permission set request header |
| `coco.security.web.header.authority-delimiter` | `,` | Delimiter for the roles and permissions request headers |

## Security Response Headers

### Overview

`CocoSecurityHeadersFilter` writes a set of security response headers at the very front of the filter chain, so that subsequent filters, business code, and downstream-generated error responses (signature 401, rate-limiting 429, unhandled exception 500, etc.) all carry these headers. Writing them at the very front is deliberate: once a response is committed it is too late to write them, and subsequent code can still override the framework's defaults with `setHeader`. Any response header whose value is `null` or blank is not written.

They fall into two categories:

- **Enabled by default** (providing secure defaults): `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`.
- **Not written by default** (requiring explicit application configuration): `Content-Security-Policy`, `Permissions-Policy`, `Strict-Transport-Security`. These three either have no universally safe default (a misconfiguration would directly break the application) or are strongly application-specific, so they must be written by the application itself.

### How to Enable and Configure

Controlled by `coco.web.security-headers`, enabled by default.

```yaml
coco:
  web:
    security-headers:
      enabled: true
      content-type-options: nosniff
      frame-options: DENY
      referrer-policy: strict-origin-when-cross-origin
      content-security-policy: "default-src 'self'"
      permissions-policy: "geolocation=(), camera=()"
      strict-transport-security: "max-age=31536000; includeSubDomains"
```

### Key Configuration Items

| Configuration Item | Default | Description |
| --- | --- | --- |
| `coco.web.security-headers.enabled` | `true` | Whether to enable the security response headers filter |
| `coco.web.security-headers.order` | highest priority | Filter execution order |
| `coco.web.security-headers.content-type-options` | `nosniff` | `X-Content-Type-Options`; reverts to default when blank |
| `coco.web.security-headers.frame-options` | `DENY` | `X-Frame-Options`; reverts to default when blank |
| `coco.web.security-headers.referrer-policy` | `strict-origin-when-cross-origin` | `Referrer-Policy`; reverts to default when blank |
| `coco.web.security-headers.content-security-policy` | empty (not written) | `Content-Security-Policy` |
| `coco.web.security-headers.permissions-policy` | empty (not written) | `Permissions-Policy` |
| `coco.web.security-headers.strict-transport-security` | empty (not written) | `Strict-Transport-Security` |

### HSTS Is Written Only over HTTPS

`Strict-Transport-Security` is written only when `HttpServletRequest.isSecure()` is `true` (a secure connection); over plaintext HTTP, browsers ignore it, and writing it would only mask deployment configuration issues.

Note in particular: in deployments where TLS is terminated by a front-end proxy, `isSecure()` reflects the client's original protocol only if the application has set `server.forward-headers-strategy=framework` (or `native`); otherwise HSTS is silently skipped.

## CORS Cross-Origin

### Overview

`CocoCorsProperties` configures the allowed origins, methods, request headers, exposed response headers, credentials, and preflight cache of the global CORS filter. The CORS filter is **disabled by default** and is only registered when `coco.web.cors.enabled=true` (the auto-configuration is annotated with `@ConditionalOnProperty(name = "enabled", havingValue = "true")`).

### How to Enable and Configure

```yaml
coco:
  web:
    cors:
      enabled: true
      allowed-origins: ["https://app.example.com"]
      allowed-methods: [GET, POST, PUT, DELETE, OPTIONS]
      allowed-headers: ["*"]
      exposed-headers: [X-Trace-Id]
      allow-credentials: false
      max-age: 1800
```

### Key Configuration Items

| Configuration Item | Default | Description |
| --- | --- | --- |
| `coco.web.cors.enabled` | `false` | Whether to enable the CORS cross-origin filter |
| `coco.web.cors.allowed-origins` | `["*"]` | Allowed cross-origin origins |
| `coco.web.cors.allowed-methods` | `GET, POST, PUT, DELETE, OPTIONS` | Allowed HTTP methods |
| `coco.web.cors.allowed-headers` | `["*"]` | Allowed request headers |
| `coco.web.cors.exposed-headers` | empty | Response headers exposed to the client |
| `coco.web.cors.allow-credentials` | `false` | Whether to allow sending credentials (cookies, etc.) |
| `coco.web.cors.max-age` | `1800` | Preflight request cache time (seconds) |

### Notes

Per the CORS specification, `allow-credentials: true` and `allowed-origins: ["*"]` cannot be used together. When enabling credentials, narrow `allowed-origins` down to an explicit list of origins.
