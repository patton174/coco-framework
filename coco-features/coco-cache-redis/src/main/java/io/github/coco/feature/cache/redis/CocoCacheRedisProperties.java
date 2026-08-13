package io.github.coco.feature.cache.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco Redis 缓存配置属性。
 * <p>
 * 该适配器默认关闭。默认配置需要安全的显式 {@code key-prefix}，或者非空的
 * {@code spring.application.name} 以生成应用隔离前缀。
 * </p>
 */
@ConfigurationProperties(CocoCacheRedisProperties.PROPERTY_PREFIX)
public class CocoCacheRedisProperties {

    /** Redis 缓存适配器配置前缀。 */
    public static final String PROPERTY_PREFIX = "coco.cache.redis";

    private boolean enabled;

    private String keyPrefix;

    private Duration timeToLive = Duration.ofMinutes(30);

    private List<String> cacheNames = new ArrayList<>();

    private boolean allowNullValues;

    private boolean useKeyPrefix = true;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return this.keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Duration getTimeToLive() {
        return this.timeToLive;
    }

    public void setTimeToLive(Duration timeToLive) {
        this.timeToLive = timeToLive;
    }

    public List<String> getCacheNames() {
        return new ArrayList<>(this.cacheNames);
    }

    public void setCacheNames(List<String> cacheNames) {
        this.cacheNames = cacheNames == null ? new ArrayList<>() : new ArrayList<>(cacheNames);
    }

    public boolean isAllowNullValues() {
        return this.allowNullValues;
    }

    public void setAllowNullValues(boolean allowNullValues) {
        this.allowNullValues = allowNullValues;
    }

    public boolean isUseKeyPrefix() {
        return this.useKeyPrefix;
    }

    public void setUseKeyPrefix(boolean useKeyPrefix) {
        this.useKeyPrefix = useKeyPrefix;
    }
}
