---
title: Distributed Lock
---

# Distributed Lock

Coco distributed lock (`coco-lock`) declares mutually exclusive execution for synchronous business methods via the `@CocoLock` annotation. It wraps "acquire lock → execute method → release lock" into an AOP aspect: it acquires a lease by lock key before entering the method, releases it in `finally` after the method returns or throws, and meanwhile a background watchdog automatically renews the lease during method execution, preventing a long-running task from being preempted by others because its lease expires.

The lock binds to the `coco.lock` namespace and is **disabled by default**. It must be explicitly turned on with `coco.lock.enabled=true` (no `matchIfMissing`). The lock does not alter transaction boundaries, nor does it provide an exactly-once guarantee.

## Feature Overview

- **Annotation-based mutual exclusion**: `@CocoLock` is placed on a synchronous method or type, and a method declaration overrides a type declaration.
- **SpEL lock key**: `key` can be a fixed string or a Spring expression (`#p0`, `#{#order.id}`, etc.), dynamically evaluated from method arguments.
- **Bounded wait + polling**: The wait duration and polling interval are configurable; if the wait times out without acquiring the lock, a conflict error is thrown.
- **Same-thread reentrancy**: A nested acquisition of the same lock key by the same thread reuses the already-held lease, managed via a reentrancy count, and the lock is only truly released when the outermost frame releases it.
- **Lease and watchdog renewal**: While the lock is held, a background thread automatically renews it at roughly 1/3 of the lock lease's period; a renewal failure (not the owner, storage unavailable, or an exception thrown) marks that holding as "lost", and subsequent operations are handled as unavailable.
- **owner token protection**: Renewal and release take effect only while the owner token still matches, preventing accidental release of someone else's lock.
- **SPI-pluggable storage**: `CocoLockStore` is an atomic-storage SPI; the default in-process implementation is suitable only for a single instance. A cluster must replace it with a distributed implementation (such as Redis-based).

## How to Enable and Integrate

The lock is controlled by a single switch: explicitly turn on `coco.lock.enabled`. Once enabled, an application-provided `CocoLockStore` Bean takes precedence over the in-process reference implementation.

### 1. Turn on the switch

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

### 2. Declare the lock on a method

```java
@Service
public class InventoryService {

    // Fixed key: the entire method is globally mutually exclusive
    @CocoLock(key = "inventory:rebuild")
    public void rebuildIndex() {
        // ...
    }

    // SpEL key: mutually exclusive per order, wait up to 2 seconds
    @CocoLock(key = "#order.id", waitMillis = 2000)
    public void settle(Order order) {
        // ...
    }
}
```

A `key` starting with `#` is treated as a SpEL expression and supports the `#{...}` wrapping form; it can reference method parameter names, the `#p0` positional parameter, and `#target`. When `leaseMillis`, `waitMillis`, or `pollIntervalMillis` are negative, they fall back to the global configuration. When the lock key is empty, cannot be evaluated, or exceeds `max-key-length`, an invalid-key error is thrown.

### 3. Replace CocoLockStore for cluster deployment

The state of the in-process `InMemoryCocoLockStore` exists only within the current JVM. In a multi-instance deployment the instances are unaware of one another, so cross-instance mutual exclusion is impossible; a multi-instance risk warning is emitted on construction. A cluster environment requires implementing and registering a custom `CocoLockStore` (for example, Redis-based), which must: acquire atomically by key, and allow only the current owner token to renew or release:

```java
@Bean
public CocoLockStore cocoLockStore() {
    return new RedisCocoLockStore(/* ... */);
}
```

When a custom Bean is present, it automatically overrides the default in-process implementation.

## Usage Examples

### Error Codes

On acquisition failure or runtime exception, the aspect throws a unified business code:

| Business code | Constant | Trigger scenario |
|--------|------|----------|
| `40060` | `INVALID_KEY` | The lock key is missing, invalid, or the expression cannot be evaluated. |
| `40960` | `TIMED_OUT` | The lock was not acquired within the bounded wait time (contention). |
| `50360` | `UNAVAILABLE` | The lock storage is unavailable, or the lease was lost while the lock was held. |
| `50060` | `ASYNCHRONOUS_RETURN` | The annotated method returns an async or reactive type and is rejected. |
| `50361` | `INTERRUPTED` | The thread was interrupted while waiting for the lock. |

### Reentrancy Example

```java
@Service
public class ReportService {

    @CocoLock(key = "report:daily")
    public void generate() {
        aggregate(); // Re-entering the same-key lock on the same thread reuses the lease and does not self-block
    }

    @CocoLock(key = "report:daily")
    public void aggregate() {
        // ...
    }
}
```

Nested calls on the same key by the same thread reuse the lease via a reentrancy count, and the lock is released only when the outermost call returns.

## Key Configuration Items

Prefix `coco.lock`.

| Configuration item | Type | Default | Description |
|--------|------|--------|------|
| `enabled` | boolean | `false` | Whether to enable the distributed lock. |
| `lease` | Duration | `30s` | Default lease duration; can be overridden by the annotation's `leaseMillis`. |
| `wait` | Duration | `0s` | Default maximum wait duration to acquire the lock; 0 means no waiting. |
| `poll-interval` | Duration | `50ms` | Retry polling interval during the wait. |
| `watchdog-enabled` | boolean | `true` | Whether to enable the background lease-renewal watchdog. |
| `watchdog-interval` | Duration | `10s` | Upper bound of the watchdog renewal interval (the actual period is the smaller of this value and about 1/3 of the lease). |
| `max-entries` | int | `100000` | Maximum number of active lock keys in the in-process store. |
| `cleanup-interval` | Duration | `1m` | Background cleanup interval for expired leases; zero disables the background cleanup thread. |
| `max-key-length` | int | `256` | Maximum lock key length; over-length is treated as an invalid key. |
| `aspect-order` | int | `Ordered.LOWEST_PRECEDENCE - 100` | Order of the lock aspect in the AOP chain. |

## Boundary Considerations

- **Disabled by default**: You must explicitly set `coco.lock.enabled=true`. This property has no `matchIfMissing`, so it is not wired up when unconfigured.
- **The in-process store cannot be used in a cluster**: `InMemoryCocoLockStore` is suitable only for a single instance or for testing. Multi-instance mutual exclusion must replace it with a shared `CocoLockStore` implementation.
- **Async/reactive returns are not supported**: An annotated method returning types such as `CompletionStage` or `Publisher` is directly rejected (`50060`), because the aspect relies on the synchronous method boundary to release the lock.
- **No exactly-once, and no transaction change**: The lease may be lost due to watchdog renewal failure (network partition, storage jitter, etc.), at which point the holding is marked lost. The business must handle consistency itself after the critical section is preempted; a lock is not a transaction.
- **The handle is released by the acquiring thread**: The underlying holding handle requires being closed by the same thread that acquired it; releasing it across threads throws a state exception.
- **Renewal depends on storage availability**: A watchdog renewal failure is treated as losing the lock; scenarios with high strong-consistency requirements should combine business idempotency with conflict detection.
