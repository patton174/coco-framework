---
title: 分布式锁
---

# 分布式锁

Coco 分布式锁（`coco-lock`）通过 `@CocoLock` 注解为同步业务方法声明互斥执行。它把"获取锁 → 执行方法 → 释放锁"封装成 AOP 切面：进入方法前按锁键申请租约，方法返回或抛异常后在 `finally` 中释放，同时后台看门狗（watchdog）在方法执行期间自动为租约续期，避免长任务因租约到期被他人抢占。

锁绑定 `coco.lock` 命名空间，**默认关闭**，需显式打开 `coco.lock.enabled=true`（无 `matchIfMissing`）。锁不改变事务边界，也不提供 exactly-once 保证。

## 功能简介

- **注解式互斥**：`@CocoLock` 标注在同步方法或类型上，方法声明覆盖类型声明。
- **SpEL 锁键**：`key` 既可以是固定字符串，也可以是 Spring 表达式（`#p0`、`#{#order.id}` 等），从方法参数动态求值。
- **有限等待 + 轮询**：可配置等待时长与轮询间隔；等待超时未获得锁抛出冲突错误。
- **同线程可重入**：同一线程对同一锁键的嵌套获取会复用已持有的租约，通过重入计数管理，最外层释放时才真正释放锁。
- **租约与看门狗续期**：持锁期间后台线程按锁租约的约 1/3 周期自动续期；续期失败（非本 owner、存储不可用、抛异常）会将该持有标记为"丢失"（lost），后续操作按不可用处理。
- **owner token 保护**：续期与释放仅在 owner token 仍匹配时生效，防止误释放他人的锁。
- **SPI 可替换存储**：`CocoLockStore` 是原子存储 SPI，默认进程内实现仅适合单实例；集群必须替换为分布式实现（如基于 Redis）。

## 如何启用接入

锁只受单个开关控制：显式打开 `coco.lock.enabled`。启用后，应用提供的 `CocoLockStore` Bean 优先于进程内参考实现。

### 1. 打开开关

```yaml
coco:
  lock:
    enabled: true
    lease: 30s
    wait: 0s
    poll-interval: 50ms
    watchdog-enabled: true
    watchdog-interval: 10s
```

### 2. 在方法上声明锁

```java
@Service
public class InventoryService {

    // 固定键：整个方法全局互斥
    @CocoLock(key = "inventory:rebuild")
    public void rebuildIndex() {
        // ...
    }

    // SpEL 键：按订单维度互斥，等待最多 2 秒
    @CocoLock(key = "#order.id", waitMillis = 2000)
    public void settle(Order order) {
        // ...
    }
}
```

`key` 以 `#` 开头视为 SpEL 表达式，支持 `#{...}` 包裹形式；可引用方法参数名、`#p0` 位置参数以及 `#target`。`leaseMillis`、`waitMillis`、`pollIntervalMillis` 为负数时回退到全局配置。锁键为空、无法求值或超过 `max-key-length` 时抛出无效键错误。

### 3. 集群部署替换 CocoLockStore

进程内 `InMemoryCocoLockStore` 的状态只存在于当前 JVM，多实例部署下各实例互不感知，无法实现跨实例互斥；构造时会输出多实例风险警告。集群环境需实现并注册自定义 `CocoLockStore`（例如基于 Redis），要求：按 key 原子获取，且只允许当前 owner token 续期或释放：

```java
@Bean
public CocoLockStore cocoLockStore() {
    return new RedisCocoLockStore(/* ... */);
}
```

自定义 Bean 存在时会自动覆盖默认进程内实现。

## 使用示例

### 错误码

获取失败或运行异常时，切面抛出统一业务码：

| 业务码 | 常量 | 触发场景 |
|--------|------|----------|
| `40060` | `INVALID_KEY` | 锁键缺失、无效或表达式无法求值。 |
| `40960` | `TIMED_OUT` | 在有限等待时间内未获得锁（竞争）。 |
| `50360` | `UNAVAILABLE` | 锁存储不可用，或持锁期间租约丢失。 |
| `50060` | `ASYNCHRONOUS_RETURN` | 注解方法返回异步或响应式类型，被拒绝。 |
| `50361` | `INTERRUPTED` | 等待锁时线程被中断。 |

### 重入示例

```java
@Service
public class ReportService {

    @CocoLock(key = "report:daily")
    public void generate() {
        aggregate(); // 同线程再次进入同键锁，复用租约，不会自阻塞
    }

    @CocoLock(key = "report:daily")
    public void aggregate() {
        // ...
    }
}
```

同线程对同键的嵌套调用通过重入计数复用租约，仅在最外层调用返回时释放锁。

## 关键配置项

前缀 `coco.lock`。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 是否启用分布式锁。 |
| `lease` | Duration | `30s` | 默认租约时长，可被注解 `leaseMillis` 覆盖。 |
| `wait` | Duration | `0s` | 默认获取锁的最大等待时长，0 表示不等待。 |
| `poll-interval` | Duration | `50ms` | 等待期间的重试轮询间隔。 |
| `watchdog-enabled` | boolean | `true` | 是否启用后台租约续期看门狗。 |
| `watchdog-interval` | Duration | `10s` | 看门狗续期间隔上限（实际周期取该值与租约约 1/3 的较小者）。 |
| `max-entries` | int | `100000` | 进程内存储最大活动锁键数。 |
| `cleanup-interval` | Duration | `1m` | 过期租约后台清理间隔；为零则关闭后台清理线程。 |
| `max-key-length` | int | `256` | 锁键最大长度，超长视为无效键。 |
| `aspect-order` | int | `Ordered.LOWEST_PRECEDENCE - 100` | 锁切面在 AOP 链中的顺序。 |

## 边界注意事项

- **默认关闭**：必须显式设置 `coco.lock.enabled=true`，该属性无 `matchIfMissing`，未配置即不装配。
- **进程内存储不可用于集群**：`InMemoryCocoLockStore` 仅适合单实例或测试。多实例互斥必须替换为共享 `CocoLockStore` 实现。
- **不支持异步/响应式返回**：注解方法返回 `CompletionStage`、`Publisher` 等类型会被直接拒绝（`50060`），因为切面依赖同步方法边界释放锁。
- **不提供 exactly-once，也不改变事务**：租约可能因看门狗续期失败而丢失（网络分区、存储抖动等），此时持有会被标记 lost。业务需自行处理临界区被抢占后的一致性，锁不等于事务。
- **句柄由获取线程释放**：底层持有句柄要求由获取它的同一线程关闭，跨线程释放会抛出状态异常。
- **续期依赖存储可用性**：看门狗续期失败即视为丢失锁；对强一致要求高的场景应结合业务幂等与冲突检测。

