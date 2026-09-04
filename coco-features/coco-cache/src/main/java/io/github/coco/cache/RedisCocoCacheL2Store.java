package io.github.coco.cache;

import java.time.Duration;
import java.util.Objects;

import org.springframework.data.redis.core.RedisTemplate;

/**
 * 基于 Spring Data Redis 的 L2 存储。
 * <p>
 * 键形如 {@code <keyPrefix><cacheName>:<key>}。值直接交给注入的 {@link RedisTemplate} 的
 * 值序列化器处理;{@code null} 值用 {@link NullMarker} 哨兵写入,以便与"键不存在"区分
 * (穿透防护需要缓存空值)。{@code clear} 用 {@code SCAN} 逐个删除该缓存名下的键,避免
 * {@code KEYS} 在大库上阻塞。
 * </p>
 */
public final class RedisCocoCacheL2Store implements CocoCacheL2Store {

    /** null 值哨兵:序列化后写入 Redis,读回时还原为 {@code null}。 */
    public enum NullMarker {
        /** 唯一实例。 */
        INSTANCE
    }

    private final RedisTemplate<String, Object> redisTemplate;
    private final String keyPrefix;

    /**
     * 创建 Redis L2 存储。
     * @param redisTemplate 值可序列化任意对象的 RedisTemplate
     * @param keyPrefix 键前缀
     */
    public RedisCocoCacheL2Store(RedisTemplate<String, Object> redisTemplate, String keyPrefix) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
    }

    @Override
    public Entry get(String cacheName, String key) {
        Object raw = this.redisTemplate.opsForValue().get(redisKey(cacheName, key));
        if (raw == null) {
            return Entry.miss();
        }
        return raw == NullMarker.INSTANCE ? Entry.hit(null) : Entry.hit(raw);
    }

    @Override
    public void put(String cacheName, String key, Object value, long ttlMillis) {
        Object stored = value == null ? NullMarker.INSTANCE : value;
        this.redisTemplate.opsForValue().set(redisKey(cacheName, key), stored, Duration.ofMillis(Math.max(1, ttlMillis)));
    }

    @Override
    public void evict(String cacheName, String key) {
        this.redisTemplate.delete(redisKey(cacheName, key));
    }

    @Override
    public void clear(String cacheName) {
        String pattern = this.keyPrefix + cacheName + ":*";
        var keys = this.redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            this.redisTemplate.delete(keys);
        }
    }

    private String redisKey(String cacheName, String key) {
        return this.keyPrefix + cacheName + ":" + key;
    }
}
