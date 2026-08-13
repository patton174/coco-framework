package io.github.coco.feature.lock.redis;

import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis 分布式锁适配器配置。 */
@ConfigurationProperties(CocoLockRedisProperties.PROPERTY_PREFIX)
public class CocoLockRedisProperties {

    /** Redis 适配器配置前缀。 */
    public static final String PROPERTY_PREFIX = "coco.lock.redis";

    private static final Pattern SAFE_PREFIX = Pattern.compile("[A-Za-z0-9:_-]{1,64}");

    private boolean enabled;

    private String keyPrefix;

    /** @return 是否启用 Redis 适配器 */
    public boolean isEnabled() { return this.enabled; }

    /** @param enabled 是否启用 Redis 适配器 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** @return 显式 Redis key 前缀 */
    public String getKeyPrefix() { return this.keyPrefix; }

    /** @param keyPrefix 显式 Redis key 前缀 */
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    /**
     * 解析安全的应用隔离前缀。
     * @param applicationName Spring 应用名
     * @return 安全的 Redis key 前缀
     */
    public String resolveKeyPrefix(String applicationName) {
        String candidate = this.keyPrefix == null || this.keyPrefix.isBlank() ? applicationName : this.keyPrefix;
        if (candidate == null || candidate.isBlank() || !SAFE_PREFIX.matcher(candidate).matches()) {
            throw new IllegalArgumentException("coco.lock.redis.key-prefix or spring.application.name must be safe");
        }
        return "coco:lock:" + candidate + ":";
    }
}
