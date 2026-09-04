---
title: 幂等
---

# 幂等

Coco 请求幂等（`coco-idempotency`）为写操作提供"同一个请求键只成功处理一次"的保护。客户端在请求头携带 `Idempotency-Key`，框架在 MVC 拦截器层为每个键申请一个租约（lease）：首个请求获得租约并放行，处理成功后键会被保留到 TTL 到期，此后携带相同键的请求返回 HTTP 409。

幂等绑定 `coco.idempotency` 命名空间，依赖 Web 运行时特性（`web`），且**默认关闭**，需显式打开 `coco.idempotency.enabled=true`。它不缓存或回放首次响应，也不改变业务事务边界。

## 功能简介

- **基于租约的去重**：每个幂等键对应一个带 TTL 的租约。仅在处理成功时保留键，失败时释放键允许重试。
- **精确的保留/释放语义**：处理器正常完成且响应状态为 **2xx 或 3xx** 时，键被保留至 TTL 到期，其间相同键返回 409；只要抛出异常，或响应状态为 **4xx / 5xx**，或处理器未正常完成，租约会被释放，允许客户端用相同键重试。
- **仅拦截写方法**：默认只对 `POST`、`PUT`、`PATCH`、`DELETE` 生效，可通过 `allowed-methods` 调整。
- **键强校验**：键长度受限、只允许可见 ASCII 字符（`!` 至 `~`），非法或缺失键返回 HTTP 400。
- **敏感头脱敏**：`Idempotency-Key` 头被登记为敏感请求头，不会在日志中原样输出。
- **可替换存储**：默认进程内存储仅适合单实例，`CocoIdempotencyStore` 与 `CocoIdempotencyKeyResolver` 均可替换。

## 如何启用接入

幂等受两层开关控制：特性开关 `web` 必须开启（幂等依赖 Web 运行时），同时需显式打开 `coco.idempotency.enabled`。自动配置仅在 Servlet 应用中装配。

### 1. 打开开关

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

### 2. 在处理方法上声明

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @CocoIdempotent(namespace = "order-create")
    @PostMapping
    public OrderResponse create(@RequestBody CreateOrderRequest request) {
        // 同一 Idempotency-Key 只会成功执行一次
        return this.orderService.create(request);
    }
}
```

`@CocoIdempotent` 可标注在类型或方法上，方法注解优先于类注解。`namespace` 用于逻辑隔离，空值表示 `default` 命名空间；`ttlSeconds` 为负数时使用全局 `coco.idempotency.ttl`。

### 3. 客户端携带幂等键

客户端为每次逻辑操作生成稳定且唯一的键（如 UUID），放入请求头：

```
POST /api/orders HTTP/1.1
Idempotency-Key: 6f9619ff-8b86-d011-b42d-00cf4fc964ff
Content-Type: application/json

{"productId": 42, "quantity": 1}
```

同一操作的重试必须复用同一个键；不同操作必须使用不同的键。

## 使用示例

### 请求语义与状态码

| 场景 | 行为 | 状态码 / 业务码 |
|------|------|------|
| 首个请求，处理成功（2xx/3xx） | 获得租约、放行，键保留至 TTL | 业务处理器自身状态 |
| 相同键，前一请求成功且未过期 | 拒绝，视为重复 | HTTP 409 / `40910` |
| 请求处理抛异常或返回 4xx/5xx | 释放租约，允许相同键重试 | 业务处理器自身状态 |
| 缺失或非法 `Idempotency-Key` | 拒绝 | HTTP 400 / `40010` |
| 存储不可用 | fail-closed 拒绝 | HTTP 503 / `50310` |

关键点：**只有成功（2xx/3xx）才会锁定键**。这意味着失败的请求不会阻塞后续同键重试，符合"安全重试"的直觉。

### 集群部署替换共享存储

进程内存储的状态只存在于当前 JVM，多实例部署下各实例的键互不可见，无法跨实例去重。启用时会输出多实例风险警告。生产多实例需切换到共享存储：

```yaml
coco:
  idempotency:
    enabled: true
    store-type: redis
    redis:
      key-prefix: "coco:idempotency:"
```

或提供自定义 `CocoIdempotencyStore` Bean 覆盖默认实现。

## 关键配置项

前缀 `coco.idempotency`。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 是否启用请求幂等。 |
| `header-name` | string | `Idempotency-Key` | 携带幂等键的请求头名称。 |
| `ttl` | Duration | `24h` | 成功键的默认保留时长，可被注解 `ttlSeconds` 覆盖。 |
| `max-key-length` | int | `128` | 幂等键最大长度，超长视为非法键。 |
| `max-entries` | int | `100000` | 进程内存储最大活动键数。 |
| `cleanup-interval` | Duration | `1m` | 过期键后台清理间隔；为零则关闭后台清理线程。 |
| `allowed-methods` | list | `POST`, `PUT`, `PATCH`, `DELETE` | 参与幂等保护的 HTTP 方法。 |
| `store-type` | enum | `in-memory` | 存储类型，可选 `in-memory` / `redis`。 |
| `redis.key-prefix` | string | `coco:idempotency:` | Redis 键前缀。 |
| `redis.template-bean-name` | string | 空 | 指定 RedisTemplate Bean 名称，空则使用默认。 |

## 边界注意事项

- **仅在 Servlet 应用生效**：幂等依赖 `web` 特性，且拦截器只在 Servlet 环境注册。
- **默认关闭**：与限流不同，即使 `web` 特性开启，也必须显式设置 `coco.idempotency.enabled=true`。
- **不回放首次响应**：框架只保证同键不重复处理，不会缓存并重放第一次的响应体。重复请求得到的是 409，而非首次响应内容。
- **不改变事务边界**：幂等与业务事务解耦。业务处理器内部的事务是否提交仍由自身控制；租约的释放依据 HTTP 响应状态判断。
- **键必须由客户端保证稳定唯一**：同一操作重试复用同键，不同操作使用不同键，否则会误判为重复或漏判。
- **进程内存储不可用于集群**：多实例下务必替换为共享 `CocoIdempotencyStore`，否则无法跨实例去重。
- **fail-closed 语义**：存储不可用时返回 503 而非放行，需为共享存储做好可用性保障。

