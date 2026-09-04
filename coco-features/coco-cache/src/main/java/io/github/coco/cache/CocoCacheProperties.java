package io.github.coco.cache;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 缓存配置。
 * <p>
 * 默认关闭,需显式打开 {@code coco.cache.enabled}。默认拓扑为 {@link CocoCacheStoreType#LOCAL}。
 * </p>
 */
@ConfigurationProperties("coco.cache")
public class CocoCacheProperties {

    private boolean enabled;

    private CocoCacheStoreType storeType = CocoCacheStoreType.LOCAL;

    /** L1(Caffeine)每缓存最大条目数。 */
    private long maximumSize = 10_000;

    /** 写入后存活时长;{@code null} 表示不按写入过期。 */
    private Duration expireAfterWrite = Duration.ofMinutes(10);

    /**
     * 是否缓存 {@code null} 值(穿透防护)。
     * <p>
     * 打开后,加载器返回 {@code null} 也会以短 TTL 记入缓存,拦住对不存在键的反复穿透查询。
     * </p>
     */
    private boolean cacheNullValues = true;

    /** {@code null} 值的存活时长,通常远短于正常值。 */
    private Duration nullValueTtl = Duration.ofSeconds(30);

    @NestedConfigurationProperty
    private final Redis redis = new Redis();

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public CocoCacheStoreType getStoreType() {
        return this.storeType;
    }

    public void setStoreType(CocoCacheStoreType storeType) {
        this.storeType = storeType == null ? CocoCacheStoreType.LOCAL : storeType;
    }

    public long getMaximumSize() {
        return this.maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
        this.maximumSize = maximumSize;
    }

    public Duration getExpireAfterWrite() {
        return this.expireAfterWrite;
    }

    public void setExpireAfterWrite(Duration expireAfterWrite) {
        this.expireAfterWrite = expireAfterWrite;
    }

    public boolean isCacheNullValues() {
        return this.cacheNullValues;
    }

    public void setCacheNullValues(boolean cacheNullValues) {
        this.cacheNullValues = cacheNullValues;
    }

    public Duration getNullValueTtl() {
        return this.nullValueTtl;
    }

    public void setNullValueTtl(Duration nullValueTtl) {
        this.nullValueTtl = nullValueTtl;
    }

    public Redis getRedis() {
        return this.redis;
    }

    public void setRedis(Redis redis) {
        Redis copy = Redis.copyOf(redis);
        this.redis.setKeyPrefix(copy.getKeyPrefix());
        this.redis.setInvalidationChannel(copy.getInvalidationChannel());
        this.redis.setTemplateBeanName(copy.getTemplateBeanName());
    }

    /** Redis(L2)子配置。 */
    public static class Redis {

        private String keyPrefix = "coco:cache:";

        private String invalidationChannel = "coco:cache:invalidation";

        private String templateBeanName;

        public String getKeyPrefix() {
            return this.keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "coco:cache:" : keyPrefix.trim();
        }

        public String getInvalidationChannel() {
            return this.invalidationChannel;
        }

        public void setInvalidationChannel(String invalidationChannel) {
            this.invalidationChannel = invalidationChannel == null || invalidationChannel.isBlank()
                    ? "coco:cache:invalidation" : invalidationChannel.trim();
        }

        public String getTemplateBeanName() {
            return this.templateBeanName;
        }

        public void setTemplateBeanName(String templateBeanName) {
            this.templateBeanName = templateBeanName == null || templateBeanName.isBlank()
                    ? null : templateBeanName.trim();
        }

        static Redis copyOf(Redis source) {
            Redis copy = new Redis();
            if (source != null) {
                copy.setKeyPrefix(source.getKeyPrefix());
                copy.setInvalidationChannel(source.getInvalidationChannel());
                copy.setTemplateBeanName(source.getTemplateBeanName());
            }
            return copy;
        }
    }
}
