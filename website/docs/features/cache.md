---
title: 缓存
---

# 缓存

Coco 缓存（`coco-cache`）提供两层缓存能力：L1 是进程内 Caffeine，L2 是共享 Redis，写入与失效通过 Redis pub/sub 广播，让多实例的 L1 保持一致。它实现 Spring 的 `CacheManager`/`Cache`，因此 `@Cacheable`、`@CacheEvict` 等注解开箱可用。

它绑定 `coco.cache` 命名空间，**默认关闭**，需显式打开 `coco.cache.enabled=true`。

## 功能简介

- **两种拓扑**：`store-type` 选 `local`（仅 Caffeine）或 `two-level`（Caffeine L1 + Redis L2），默认 `local`。
- **穿透防护**：`cache-null-values` 打开时，加载器返回的 `null` 也会以短 TTL 记入缓存，拦住对不存在键的反复穿透查询。
- **击穿防护**：`get(key, loader)` 用 Caffeine 的原子计算保证同一键的加载器在本实例内单飞（single-flight），高并发下不会重复回源。
- **跨实例一致**：`two-level` 拓扑下，写入/失效同时作用于 L1、L2，并通过 Redis pub/sub 广播，让其它实例失效各自 L1。
- **fail-closed**：配了 `two-level` 但 classpath 没有 Spring Data Redis 时，启动即失败，而非静默退回纯本地（避免多实例悄悄读到各自陈旧副本）。

## 如何启用接入

### 1. 打开开关

```yaml
coco:
  cache:
    enabled: true
    store-type: local   # local（默认）| two-level
    maximum-size: 10000
    expire-after-write: 10m
```

### 2. 用标准注解缓存方法

```java
@Service
public class ProductService {

    @Cacheable(cacheNames = "product", key = "#id", sync = true)
    public Product findById(String id) {
        return productMapper.selectById(id);   // 仅首次回源
    }

    @CacheEvict(cacheNames = "product", key = "#product.id")
    public void update(Product product) {
        productMapper.updateById(product);
    }
}
```

`sync = true` 启用击穿防护（单飞）；对可能返回 `null` 的查询，配合 `cache-null-values` 获得穿透防护。

### 3. 集群部署切换到两层

```yaml
coco:
  cache:
    enabled: true
    store-type: two-level
    redis:
      key-prefix: "coco:cache:"
      invalidation-channel: "coco:cache:invalidation"
```

再引入 Spring Data Redis：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

:::tip[为什么 two-level 缺依赖会启动失败]
缓存的静默回落不像分布式锁那样直接导致数据错误，但你既然配了 `two-level` 就是要跨实例一致。悄悄退回纯本地会让多实例读到各自的陈旧副本，且毫无提示。因此缺 Redis 依赖时选择显式失败，让配置意图与运行行为一致。
:::

## L1/L2 读写路径

- **读**：先查 L1；未命中查 L2；L2 命中时回填 L1 并返回。
- **写/失效**：同时作用于 L1 与 L2，再广播失效消息；其它实例收到后**只**失效各自 L1（不动共享 L2），发布实例用 `sourceId` 忽略自己的广播。

## 关键配置项

前缀 `coco.cache`。

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `false` | 是否启用缓存。 |
| `store-type` | enum | `local` | 拓扑：`local` / `two-level`。 |
| `maximum-size` | long | `10000` | L1（Caffeine）每缓存最大条目数。 |
| `expire-after-write` | Duration | `10m` | 写入后存活时长。 |
| `cache-null-values` | boolean | `true` | 是否缓存空值（穿透防护）。 |
| `null-value-ttl` | Duration | `30s` | 空值存活时长，通常远短于正常值。 |
| `redis.key-prefix` | string | `coco:cache:` | L2 键前缀。 |
| `redis.invalidation-channel` | string | `coco:cache:invalidation` | 失效广播频道。 |
| `redis.template-bean-name` | string | 空 | 指定 StringRedisTemplate Bean 名称，空则用唯一/@Primary 候选。 |

## 边界注意事项

- **L2 用 JDK 序列化**：框架已迁移到 Jackson 3.x，而 Spring Data Redis 的 `GenericJackson2JsonRedisSerializer` 仍绑定 Jackson 2.x（类路径不存在），因此 L2 默认用 JDK 序列化，被缓存的值需实现 `Serializable`。需要跨语言可读时，业务可提供同名 `cocoCacheL2RedisTemplate` Bean 覆盖。
- **最终一致，非强一致**：失效通过 pub/sub 广播，广播到达前其它实例的 L1 可能短暂读到旧值。强一致场景不要依赖 L1。
- **local 拓扑不跨实例**：多实例部署下各实例缓存相互独立，写入/失效不传播。需要一致性时用 `two-level`。
- **仅在配置的缓存名生效**：与 Spring 缓存注解一致，缓存名由 `@Cacheable(cacheNames=...)` 声明，惰性创建。
