package io.github.coco.cache;

import java.util.concurrent.Callable;

import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.lang.Nullable;

/**
 * 两层缓存:L1(Caffeine)+ 可选 L2(共享存储)+ 可选 L1 失效广播。
 * <p>
 * 继承 {@link AbstractValueAdaptingCache} 以复用 Spring 的 {@code NullValue} 空值哨兵
 * 与 {@code @Cacheable} 契约。L2 与广播发布器可为 {@code null}——{@link CocoCacheStoreType#LOCAL}
 * 拓扑下二者缺省,退化为纯本地缓存。
 * </p>
 * <ul>
 *   <li><b>穿透防护</b>:{@code allowNullValues} 打开时,加载器返回的 {@code null} 也会被缓存
 *   (以 {@code NullValue} 哨兵形式),拦住对不存在键的反复查询。</li>
 *   <li><b>击穿防护</b>:{@link #get(Object, Callable)} 用 Caffeine 的原子计算保证同一键的加载器
 *   在本实例内单飞(single-flight),不会并发重复回源。</li>
 *   <li><b>一致性</b>:{@code put}/{@code evict}/{@code clear} 同时作用于 L1、L2,并广播失效,
 *   使其它实例失效各自 L1。</li>
 * </ul>
 */
public final class CocoTwoLevelCache extends AbstractValueAdaptingCache {

    private final String name;
    private final Cache<Object, Object> l1;
    @Nullable
    private final CocoCacheL2Store l2;
    @Nullable
    private final CocoCacheInvalidationPublisher publisher;
    private final long ttlMillis;
    private final long nullValueTtlMillis;

    /**
     * 创建两层缓存。
     * @param name 缓存名
     * @param l1 本地 Caffeine 缓存
     * @param l2 共享 L2 存储;{@code null} 为纯本地拓扑
     * @param publisher L1 失效广播发布器;{@code null} 为纯本地拓扑
     * @param allowNullValues 是否缓存空值(穿透防护)
     * @param ttlMillis 正常值的 L2 存活毫秒
     * @param nullValueTtlMillis 空值的 L2 存活毫秒
     */
    public CocoTwoLevelCache(String name, Cache<Object, Object> l1, @Nullable CocoCacheL2Store l2,
            @Nullable CocoCacheInvalidationPublisher publisher, boolean allowNullValues, long ttlMillis,
            long nullValueTtlMillis) {
        super(allowNullValues);
        this.name = name;
        this.l1 = l1;
        this.l2 = l2;
        this.publisher = publisher;
        this.ttlMillis = ttlMillis;
        this.nullValueTtlMillis = nullValueTtlMillis;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Object getNativeCache() {
        return this.l1;
    }

    @Override
    @Nullable
    protected Object lookup(Object key) {
        Object l1Value = this.l1.getIfPresent(key);
        if (l1Value != null) {
            return l1Value;
        }
        if (this.l2 == null) {
            return null;
        }
        CocoCacheL2Store.Entry entry = this.l2.get(this.name, stringKey(key));
        if (!entry.present()) {
            return null;
        }
        // Backfill L1 with the store-form value (NullValue sentinel already applied on write).
        Object storeValue = entry.value() == null ? toStoreValue(null) : entry.value();
        this.l1.put(key, storeValue);
        return storeValue;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        // Single-flight: Caffeine runs the mapping function at most once per key under contention.
        Object storeValue = this.l1.get(key, ignored -> loadThrough(key, valueLoader));
        return (T) fromStoreValue(storeValue);
    }

    private Object loadThrough(Object key, Callable<?> valueLoader) {
        if (this.l2 != null) {
            CocoCacheL2Store.Entry entry = this.l2.get(this.name, stringKey(key));
            if (entry.present()) {
                return entry.value() == null ? toStoreValue(null) : entry.value();
            }
        }
        Object loaded;
        try {
            loaded = valueLoader.call();
        }
        catch (Exception exception) {
            throw new ValueRetrievalException(key, valueLoader, exception);
        }
        writeThrough(key, loaded);
        return toStoreValue(loaded);
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        Object storeValue = toStoreValue(value);
        this.l1.put(key, storeValue);
        writeL2(key, value);
        broadcast(stringKey(key));
    }

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        Object existing = lookup(key);
        if (existing != null) {
            return toValueWrapper(existing);
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        this.l1.invalidate(key);
        if (this.l2 != null) {
            this.l2.evict(this.name, stringKey(key));
        }
        broadcast(stringKey(key));
    }

    @Override
    public void clear() {
        this.l1.invalidateAll();
        if (this.l2 != null) {
            this.l2.clear(this.name);
        }
        broadcast(null);
    }

    /**
     * 收到来自其它实例的失效广播时,仅失效本地 L1(不动共享 L2)。
     * @param key 被失效的键;{@code null} 表示清空整个 L1
     */
    void onRemoteInvalidation(@Nullable String key) {
        if (key == null) {
            this.l1.invalidateAll();
            return;
        }
        // L1 keys are the original objects; match by their string form to the broadcast key.
        this.l1.asMap().keySet().removeIf(candidate -> stringKey(candidate).equals(key));
    }

    private void writeThrough(Object key, @Nullable Object loaded) {
        writeL2(key, loaded);
        broadcast(stringKey(key));
    }

    private void writeL2(Object key, @Nullable Object value) {
        if (this.l2 == null) {
            return;
        }
        if (value == null) {
            if (isAllowNullValues()) {
                this.l2.put(this.name, stringKey(key), null, this.nullValueTtlMillis);
            }
            return;
        }
        this.l2.put(this.name, stringKey(key), value, this.ttlMillis);
    }

    private void broadcast(@Nullable String key) {
        if (this.publisher != null) {
            this.publisher.publish(new CocoCacheInvalidationMessage(this.name, key, this.publisher.sourceId()));
        }
    }

    private static String stringKey(Object key) {
        return String.valueOf(key);
    }
}
