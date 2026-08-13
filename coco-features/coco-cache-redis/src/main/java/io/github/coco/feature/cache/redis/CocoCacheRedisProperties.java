package io.github.coco.feature.cache.redis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Coco Redis 缓存配置属性。
 * <p>
 * 该适配器默认关闭。开启后仅在应用没有自行声明 {@code CacheManager} 或名为
 * {@code cacheResolver} 的 Bean 时提供 Redis {@code CacheManager}。
 * </p>
 */
@Validated
@ConfigurationProperties(CocoCacheRedisProperties.PROPERTY_PREFIX)
public class CocoCacheRedisProperties {

    /** Redis 缓存适配器配置前缀。 */
    public static final String PROPERTY_PREFIX = "coco.cache.redis";

    private boolean enabled;

    private String keyPrefix = "coco:";

    @NotNull
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

    /**
     * 校验缓存生存时间为正数。
     * @return 生存时间有效时返回 {@code true}
     */
    @AssertTrue(message = "coco.cache.redis.time-to-live must be positive")
    public boolean isTimeToLivePositive() {
        return this.timeToLive != null && !this.timeToLive.isZero() && !this.timeToLive.isNegative();
    }

    /**
     * 校验 Redis 缓存键前缀。
     * @return 前缀非空且不含控制字符时返回 {@code true}
     */
    @AssertTrue(message = "coco.cache.redis.key-prefix must not be blank or contain control characters")
    public boolean isKeyPrefixValid() {
        return !isBlank(this.keyPrefix) && !containsControlCharacter(this.keyPrefix);
    }

    /**
     * 校验显式缓存名称。
     * @return 缓存名称均安全且唯一时返回 {@code true}
     */
    @AssertTrue(message = "coco.cache.redis.cache-names must be nonblank, unique, and contain no control characters")
    public boolean isCacheNamesValid() {
        Set<String> names = new LinkedHashSet<>();
        for (String cacheName : this.cacheNames) {
            if (isBlank(cacheName) || containsControlCharacter(cacheName) || !names.add(cacheName)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean containsControlCharacter(String value) {
        return value != null && value.chars().anyMatch(Character::isISOControl);
    }
}
