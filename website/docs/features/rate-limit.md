---
title: 限流
---

# 限流

Coco 限流（`coco-rate-limit`）在 Servlet 入口处对显式声明的路由做请求配额控制。它使用**固定窗口计数器（fixed-window counter）**算法：每个限流键在一个对齐的时间窗口内累加计数，达到上限即拒绝，窗口滚动后计数归零。它不是令牌桶，也不是滑动窗口，因此在窗口边界附近可能出现短时的双倍突发，这是固定窗口算法的固有特性。

限流绑定 `coco.rate-limit` 命名空间，依赖 Web 运行时特性（`web`）。默认关闭；启用后也只有你在 `coco.rate-limit.routes` 中显式声明的路由会被拦截，不会对全部请求生效。

## 功能简介

- **固定窗口计数**：以 `windowSeconds` 为周期对齐窗口边界，窗口内允许 `limit` 次请求，超出返回 HTTP 429。
- **两条执行路径共享同一计数语义**：路径匹配的 Servlet 过滤器（Filter）在最靠前的位置执行；`@CocoRateLimited` 注解走 MVC 拦截器后备路径。当 Filter 已按路径匹配并占用了配额时，注解拦截器不会重复扣减，避免同一请求被计两次。
- **fail-closed（失败即拒绝）**：键解析或存储发生异常、存储容量耗尽时按拒绝处理，返回 HTTP 503，而不是放行。
- **标准限流响应头**：无论放行还是拒绝，都会写出配额相关响应头，便于客户端自适应退避。
- **可替换存储与键解析**：默认进程内存储仅适合单实例；`CocoRateLimitStore` 与 `CocoRateLimitKeyResolver` 均可替换。

## 如何启用接入

限流受两层开关控制：特性开关 `web`（限流依赖 Web 运行时）必须开启，同时需要显式打开 `coco.rate-limit.enabled`。属性默认关闭，避免升级后自动启用限流。

### 1. 打开开关并声明路由

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

`matcher.path-patterns` 使用 Spring Ant 风格模式；`methods` 为空表示匹配所有 HTTP 方法。路由要生效必须同时满足：`id` 非空、至少一个非空 `path-pattern`、`limit > 0`、`windowSeconds` 在 1 至 366 天之间。

### 2. （可选）用注解表达业务意图

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

`@CocoRateLimited` 只表达"该处理方法预期由某条路由保护"的意图，它**不会创建隐式路由**，也不读取用户、角色或事务状态。实际拦截规则仍由 `coco.rate-limit.routes` 显式配置。`value` 与 `route` 互为别名，可标注在类型或方法上。

## 使用示例

### 限流响应头

放行请求携带以下响应头（`X-` 前缀为兼容别名）：

```
RateLimit-Limit: 100
RateLimit-Remaining: 87
RateLimit-Reset: 42
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 87
X-RateLimit-Reset: 42
```

`RateLimit-Reset` 是距离当前窗口重置的剩余秒数。被拒绝时额外携带 `Retry-After`（至少为 1 秒）：

```
HTTP/1.1 429 Too Many Requests
RateLimit-Limit: 5
RateLimit-Remaining: 0
RateLimit-Reset: 18
Retry-After: 18
Content-Type: application/json

{"code":42900,"message":"..."}
```

配额耗尽返回业务码 `42900`（HTTP 429）；键解析或存储不可用、容量耗尽返回业务码 `50300`（HTTP 503）。

### 集群部署替换共享存储

进程内存储的状态只存在于当前 JVM，多实例部署下各实例配额相互独立，等效于放大了总配额。启用时会输出多实例风险警告。生产多实例需切换到共享存储：

```yaml
coco:
  rate-limit:
    enabled: true
    store-type: redis
    redis:
      key-prefix: "coco:rate-limit:"
```

或提供自定义 `CocoRateLimitStore` Bean 覆盖默认实现。

### 反向代理下的客户端识别

默认键解析器（`DefaultCocoRateLimitKeyResolver`）**只使用 Servlet 容器上报的远端地址**，绝不信任 `X-Forwarded-For` 等客户端可伪造的请求头。部署在可信反向代理之后时，需显式声明可信代理边界，解析器才会从转发链中按右向左信任边界取第一个非代理地址：

```yaml
coco:
  rate-limit:
    trusted-proxy:
      remote-addresses:
        - 10.0.0.1
        - 10.0.0.2
```

## 关键配置项

前缀 `coco.rate-limit`。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 是否启用限流。 |
| `routes` | list | 空 | 显式限流路由列表；只有列表内路由被拦截。 |
| `routes[].id` | string | — | 路由标识，与 `@CocoRateLimited` 的 `route` 对应。 |
| `routes[].limit` | long | `100` | 单个窗口内允许的请求数。 |
| `routes[].window-seconds` | long | `60` | 固定窗口时长（秒），范围 1 至 366 天。 |
| `routes[].matcher.methods` | list | 空（全部方法） | 匹配的 HTTP 方法。 |
| `routes[].matcher.path-patterns` | list | 空 | Ant 风格路径模式，至少一个非空才有效。 |
| `store-type` | enum | `in-memory` | 存储类型，可选 `in-memory` / `redis`。 |
| `in-memory.max-entries` | int | `10000` | 进程内存储最大活动限流键数。 |
| `in-memory.cleanup-interval-seconds` | int | `60` | 过期键后台清理间隔（秒）。 |
| `redis.key-prefix` | string | `coco:rate-limit:` | Redis 键前缀。 |
| `redis.template-bean-name` | string | 空 | 指定 RedisTemplate Bean 名称，空则使用默认。 |
| `filter.excluded-path-patterns` | list | `/actuator`, `/actuator/**`, `/health`, `/health/**` | Filter 跳过的路径，避免监控请求占用业务配额。 |
| `trusted-proxy.remote-addresses` | list | 空 | 可信反向代理地址；空为安全默认值，不解析任何转发头。 |

## 边界注意事项

- **仅在 Servlet 应用生效**：限流依赖 `web` 特性，Filter 与 MVC 拦截器均只在 Servlet 环境注册。
- **固定窗口的边界突发**：由于窗口对齐而非滑动，相邻两个窗口交界处理论上可通过接近 `2 × limit` 的请求，对严格平滑限流的场景需自行评估。
- **注解不等于配置**：`@CocoRateLimited` 不生成路由。忘记在 `coco.rate-limit.routes` 中声明对应 `id` 时，注解不会产生任何拦截效果。
- **进程内存储不可用于集群**：多实例下务必替换为共享 `CocoRateLimitStore`，否则总配额被放大。
- **fail-closed 语义**：存储异常或容量耗尽时返回 503 而非放行。需要为共享存储做好可用性保障。
- **默认不信任转发头**：未配置 `trusted-proxy.remote-addresses` 时，代理后的所有客户端会被识别为同一个远端地址（代理地址），可能导致误限流。生产环境务必按实际拓扑配置或替换键解析器。

