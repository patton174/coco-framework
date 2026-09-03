---
title: 安全上下文与安全响应头
---

# 安全上下文与安全响应头

本章覆盖两块相关但独立的能力：一是安全功能模块（`coco-feature-security`）提供的安全上下文桥接，把可信上游写入的主体信息桥接进当前线程；二是 Web 模块（`coco-feature-web`）提供的安全响应头与 CORS 跨域配置。

## 安全上下文桥接

### 功能简介

`CocoSecurityContext` 保存当前调用方的安全主体（`CocoSecurityPrincipal`：主体标识、显示名、角色集合、权限集合、附加属性）和认证状态，供鉴权、审计、租户、数据权限等模块读取。它保存在 `CocoSecurityContextHolder` 的 `ThreadLocal` 中，入口适配器负责在请求进入时设置、结束时清理，业务代码只读取。

```java
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityContext;

// 读取当前上下文，不存在时返回 Optional.empty()
CocoSecurityContextHolder.current()
        .map(CocoSecurityContext::principal)
        .ifPresent(principal -> log(principal.id()));

// 要求已有上下文，缺失时抛出未认证异常
CocoSecurityContext context = CocoSecurityContextHolder.requireCurrent();
```

Web 场景下，桥接由 `CocoSecurityWebFilter` 完成，它调用 `CocoWebSecurityContextResolver` 从请求解析出上下文。框架默认注册的解析器是 `HeaderCocoWebSecurityContextResolver`。

### 从可信 HTTP 头构建上下文

`HeaderCocoWebSecurityContextResolver` **不做认证**，它只消费可信上游（网关、认证过滤器、业务基础设施）已经写入的请求头，把它们组装成已认证的 `CocoSecurityContext`：

| 请求头 | 默认名称 | 映射到 |
| --- | --- | --- |
| 主体标识 | `X-Coco-Principal-Id` | `principal.id`（缺失则不构建上下文） |
| 主体显示名 | `X-Coco-Principal-Name` | `principal.name`（缺失时回退为主体标识） |
| 角色集合 | `X-Coco-Roles` | `principal.roles`（按分隔符拆分） |
| 权限集合 | `X-Coco-Permissions` | `principal.permissions`（按分隔符拆分） |

角色与权限默认用逗号 `,` 分隔。该解析器默认**关闭**（`coco.security.web.header.enabled=false`），避免直接信任外部客户端输入。

### 必须部署在可信网关之后

这是本能力最关键的边界：一旦开启可信请求头解析，**任何能直达应用的请求都可以伪造 `X-Coco-Principal-Id` 等请求头来冒充任意主体**。因此该解析器**必须部署在可信网关之后**，由网关负责认证并覆盖/剥离这些请求头，绝不能把应用直接暴露到公网。

为提醒这一风险，当 `coco.security.web.header.enabled=true` 时，`CocoSecurityAutoConfiguration` 会在启动时打印告警日志，提示该解析器信任上游请求头、必须置于可信网关之后，并建议提供自定义 `CocoWebSecurityContextResolver` bean 来消除告警。

### 用自定义解析器替换

如果主体信息来自 JWT、Session 或其它认证机制，直接提供一个自定义 `CocoWebSecurityContextResolver` bean 即可替换默认实现（默认解析器带 `@ConditionalOnMissingBean`，同时也会抑制上面的启动告警）：

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
        // 校验 JWT 后构建主体
        CocoSecurityPrincipal principal = /* ... */ null;
        return Optional.ofNullable(principal).map(CocoSecurityContext::authenticated);
    }
}
```

### 关键配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `coco.security.web.enabled` | `true` | 是否注册 Web 安全上下文桥接过滤器 |
| `coco.security.web.header.enabled` | `false` | 是否启用可信请求头解析（开启即信任上游头，需置于可信网关后） |
| `coco.security.web.header.principal-id-header-name` | `X-Coco-Principal-Id` | 主体标识请求头 |
| `coco.security.web.header.principal-name-header-name` | `X-Coco-Principal-Name` | 主体显示名请求头 |
| `coco.security.web.header.roles-header-name` | `X-Coco-Roles` | 角色集合请求头 |
| `coco.security.web.header.permissions-header-name` | `X-Coco-Permissions` | 权限集合请求头 |
| `coco.security.web.header.authority-delimiter` | `,` | 角色和权限请求头的分隔符 |

## 安全响应头

### 功能简介

`CocoSecurityHeadersFilter` 在过滤器链最前端写入一组安全响应头，使后续过滤器、业务代码以及下游产生的错误响应（签名 401、限流 429、未处理异常 500 等）都携带这些头。写在最前端是刻意的：响应一旦提交再写入就太晚，同时后续代码仍可用 `setHeader` 覆盖框架默认值。任一响应头取值为 `null` 或空白时不写入。

分两类：

- **默认开启**（提供安全默认值）：`X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`。
- **默认不写入**（需应用显式配置）：`Content-Security-Policy`、`Permissions-Policy`、`Strict-Transport-Security`。这三者要么没有普适安全默认值、错误配置会直接破坏应用，要么与具体应用强相关，因此必须由应用自行编写。

### 如何启用与配置

由 `coco.web.security-headers` 控制，默认启用。

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

### 关键配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `coco.web.security-headers.enabled` | `true` | 是否启用安全响应头过滤器 |
| `coco.web.security-headers.order` | 最高优先级 | 过滤器执行顺序 |
| `coco.web.security-headers.content-type-options` | `nosniff` | `X-Content-Type-Options`，空白时回退默认 |
| `coco.web.security-headers.frame-options` | `DENY` | `X-Frame-Options`，空白时回退默认 |
| `coco.web.security-headers.referrer-policy` | `strict-origin-when-cross-origin` | `Referrer-Policy`，空白时回退默认 |
| `coco.web.security-headers.content-security-policy` | 空（不写入） | `Content-Security-Policy` |
| `coco.web.security-headers.permissions-policy` | 空（不写入） | `Permissions-Policy` |
| `coco.web.security-headers.strict-transport-security` | 空（不写入） | `Strict-Transport-Security` |

### HSTS 仅在 HTTPS 上写入

`Strict-Transport-Security` 只在 `HttpServletRequest.isSecure()` 为 `true`（安全连接）时才写入；明文 HTTP 上浏览器会忽略它，写入只会掩盖部署配置问题。

需要特别注意：在 TLS 由前置代理终止的部署中，只有应用设置了 `server.forward-headers-strategy=framework`（或 `native`）时 `isSecure()` 才会反映客户端的原始协议，否则 HSTS 会被静默跳过。

## CORS 跨域

### 功能简介

`CocoCorsProperties` 配置全局 CORS 过滤器的允许来源、方法、请求头、暴露响应头、凭证和预检缓存。CORS 过滤器**默认关闭**，仅在 `coco.web.cors.enabled=true` 时才注册（自动配置带 `@ConditionalOnProperty(name = "enabled", havingValue = "true")`）。

### 如何启用与配置

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

### 关键配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `coco.web.cors.enabled` | `false` | 是否启用 CORS 跨域过滤器 |
| `coco.web.cors.allowed-origins` | `["*"]` | 允许的跨域来源 |
| `coco.web.cors.allowed-methods` | `GET, POST, PUT, DELETE, OPTIONS` | 允许的 HTTP 方法 |
| `coco.web.cors.allowed-headers` | `["*"]` | 允许的请求头 |
| `coco.web.cors.exposed-headers` | 空 | 暴露给客户端的响应头 |
| `coco.web.cors.allow-credentials` | `false` | 是否允许发送凭证（Cookie 等） |
| `coco.web.cors.max-age` | `1800` | 预检请求缓存时间（秒） |

### 注意事项

按 CORS 规范，`allow-credentials: true` 与 `allowed-origins: ["*"]` 不能同时使用。开启凭证时请把 `allowed-origins` 收敛为明确的来源列表。

