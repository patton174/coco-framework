---
title: Cache
---

# Cache

Coco cache (`coco-cache`) provides two-tier caching: L1 is in-process Caffeine, L2 is shared Redis, and writes/evictions are broadcast over Redis pub/sub so every instance's L1 stays consistent. It implements Spring's `CacheManager`/`Cache`, so `@Cacheable`, `@CacheEvict`, and friends work out of the box.

It binds the `coco.cache` namespace and is **disabled by default**; turn it on with `coco.cache.enabled=true`.

## Overview

- **Two topologies**: `store-type` selects `local` (Caffeine only) or `two-level` (Caffeine L1 + Redis L2), defaulting to `local`.
- **Penetration guard**: with `cache-null-values` on, a `null` returned by the loader is cached with a short TTL, blocking repeated queries for keys that do not exist.
- **Breakdown guard**: `get(key, loader)` uses Caffeine's atomic compute so a given key's loader runs single-flight per instance — no duplicated origin loads under contention.
- **Cross-instance consistency**: in `two-level`, writes/evictions hit both L1 and L2, then broadcast over Redis pub/sub so other instances invalidate their own L1.
- **fail-closed**: if `two-level` is configured but Spring Data Redis is not on the classpath, startup fails rather than silently degrading to local-only (which would let instances quietly read their own stale copies).

## How to Enable

### 1. Turn on the switch

```yaml
coco:
  cache:
    enabled: true
    store-type: local   # local (default) | two-level
    maximum-size: 10000
    expire-after-write: 10m
```

### 2. Cache methods with standard annotations

```java
@Service
public class ProductService {

    @Cacheable(cacheNames = "product", key = "#id", sync = true)
    public Product findById(String id) {
        return productMapper.selectById(id);   // origin hit only the first time
    }

    @CacheEvict(cacheNames = "product", key = "#product.id")
    public void update(Product product) {
        productMapper.updateById(product);
    }
}
```

`sync = true` enables the breakdown guard (single-flight); pair `cache-null-values` with queries that may return `null` for penetration protection.

### 3. Switch to two-level for cluster deployment

```yaml
coco:
  cache:
    enabled: true
    store-type: two-level
    redis:
      key-prefix: "coco:cache:"
      invalidation-channel: "coco:cache:invalidation"
```

Then add Spring Data Redis:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

:::tip[Why two-level fails closed without the dependency]
A silent cache fallback does not corrupt data the way a distributed lock would, but if you configured `two-level` you wanted cross-instance consistency. Quietly reverting to local-only would let instances read their own stale copies with no warning, so a missing Redis dependency fails explicitly — keeping configuration intent and runtime behaviour aligned.
:::

## L1/L2 read and write paths

- **Read**: check L1; on a miss check L2; on an L2 hit, backfill L1 and return.
- **Write/evict**: apply to both L1 and L2, then broadcast an invalidation; other instances invalidate **only** their own L1 (never the shared L2), and the publishing instance ignores its own broadcast via `sourceId`.

## Key Configuration

Prefix `coco.cache`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Whether caching is enabled. |
| `store-type` | enum | `local` | Topology: `local` / `two-level`. |
| `maximum-size` | long | `10000` | Max entries per cache in L1 (Caffeine). |
| `expire-after-write` | Duration | `10m` | Time-to-live after write. |
| `cache-null-values` | boolean | `true` | Whether null values are cached (penetration guard). |
| `null-value-ttl` | Duration | `30s` | TTL for null values, usually far shorter than normal values. |
| `redis.key-prefix` | string | `coco:cache:` | L2 key prefix. |
| `redis.invalidation-channel` | string | `coco:cache:invalidation` | Invalidation broadcast channel. |
| `redis.template-bean-name` | string | empty | Names a StringRedisTemplate bean; empty uses the sole/@Primary candidate. |

## Boundary Notes

- **L2 uses JDK serialization**: the framework has moved to Jackson 3.x, while Spring Data Redis's `GenericJackson2JsonRedisSerializer` still binds Jackson 2.x (not on the classpath), so L2 defaults to JDK serialization and cached values must implement `Serializable`. Supply your own `cocoCacheL2RedisTemplate` bean to override when cross-language readability is needed.
- **Eventually consistent, not strongly consistent**: invalidation is broadcast via pub/sub; before it arrives, another instance's L1 may briefly serve a stale value. Do not rely on L1 for strong-consistency paths.
- **local topology is per-instance**: in a multi-instance deployment each instance's cache is independent and writes/evictions do not propagate. Use `two-level` when you need consistency.
- **Only configured cache names apply**: as with Spring cache annotations, cache names are declared via `@Cacheable(cacheNames=...)` and created lazily.
