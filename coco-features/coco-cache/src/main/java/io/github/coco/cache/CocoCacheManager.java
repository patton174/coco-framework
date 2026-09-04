package io.github.coco.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;

/**
 * 按缓存名惰性创建 {@link CocoTwoLevelCache} 的 {@link CacheManager}。
 * <p>
 * 首次访问某缓存名时创建对应的 L1(Caffeine)并组装两层缓存。{@link CocoCacheStoreType#LOCAL}
 * 拓扑下 {@code l2} 与 {@code publisher} 为 {@code null},退化为纯本地缓存。收到失效广播时,
 * 通过 {@link #onRemoteInvalidation} 只失效对应缓存的本地 L1。
 * </p>
 */
public final class CocoCacheManager implements CacheManager {

    private final ConcurrentMap<String, CocoTwoLevelCache> caches = new ConcurrentHashMap<>();
    private final long maximumSize;
    private final long ttlMillis;
    private final Duration expireAfterWrite;
    private final boolean allowNullValues;
    private final long nullValueTtlMillis;
    @Nullable
    private final CocoCacheL2Store l2;
    @Nullable
    private final CocoCacheInvalidationPublisher publisher;

    /**
     * 创建缓存管理器。
     * @param properties 缓存配置
     * @param l2 共享 L2 存储;{@code null} 为纯本地拓扑
     * @param publisher L1 失效广播发布器;{@code null} 为纯本地拓扑
     */
    public CocoCacheManager(CocoCacheProperties properties, @Nullable CocoCacheL2Store l2,
            @Nullable CocoCacheInvalidationPublisher publisher) {
        this.maximumSize = properties.getMaximumSize();
        this.expireAfterWrite = properties.getExpireAfterWrite();
        this.ttlMillis = properties.getExpireAfterWrite() == null ? Long.MAX_VALUE
                : properties.getExpireAfterWrite().toMillis();
        this.allowNullValues = properties.isCacheNullValues();
        this.nullValueTtlMillis = properties.getNullValueTtl() == null ? 30_000L
                : properties.getNullValueTtl().toMillis();
        this.l2 = l2;
        this.publisher = publisher;
    }

    @Override
    @Nullable
    public Cache getCache(String name) {
        return this.caches.computeIfAbsent(name, this::create);
    }

    @Override
    public Collection<String> getCacheNames() {
        return Set.copyOf(this.caches.keySet());
    }

    private CocoTwoLevelCache create(String name) {
        Caffeine<Object, Object> builder = Caffeine.newBuilder().maximumSize(this.maximumSize);
        if (this.expireAfterWrite != null) {
            builder = builder.expireAfterWrite(this.expireAfterWrite);
        }
        return new CocoTwoLevelCache(name, builder.build(), this.l2, this.publisher, this.allowNullValues,
                this.ttlMillis, this.nullValueTtlMillis);
    }

    /**
     * 分发一条来自其它实例的失效广播到对应缓存的本地 L1。
     * <p>
     * 只对已存在的缓存生效;未被本实例访问过的缓存名无需处理。
     * </p>
     * @param message 失效消息
     */
    public void onRemoteInvalidation(CocoCacheInvalidationMessage message) {
        if (this.publisher != null && this.publisher.sourceId().equals(message.sourceId())) {
            return; // Ignore our own broadcast: our L1 is already correct.
        }
        CocoTwoLevelCache cache = this.caches.get(message.cacheName());
        if (cache != null) {
            cache.onRemoteInvalidation(message.key());
        }
    }
}
