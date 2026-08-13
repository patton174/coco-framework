package io.github.coco.feature.idempotency.redis;

import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 幂等共享存储配置。
 *
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(CocoIdempotencyRedisProperties.PROPERTY_PREFIX)
public class CocoIdempotencyRedisProperties {

    /** Redis 适配器配置前缀。 */
    public static final String PROPERTY_PREFIX = "coco.idempotency.redis";

    private static final Pattern KEY_PREFIX = Pattern.compile("[A-Za-z0-9:_-]{1,64}");

    private boolean enabled;

    private String keyPrefix = "coco:idempotency:";

    private int maxResponseBytes = 1_048_576;

    private int maxHeaderBytes = 65_536;

    /** @return 是否启用 Redis 幂等存储。 */
    public boolean isEnabled() {
        return this.enabled;
    }

    /** @param enabled 是否启用 Redis 幂等存储。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return Redis key 固定前缀。 */
    public String getKeyPrefix() {
        return this.keyPrefix;
    }

    /** @param keyPrefix Redis key 固定前缀。 */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /** @return 响应结构及响应体的最大字节数。 */
    public int getMaxResponseBytes() {
        return this.maxResponseBytes;
    }

    /** @param maxResponseBytes 响应结构及响应体的最大字节数。 */
    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    /** @return 结构化响应头的最大字节数。 */
    public int getMaxHeaderBytes() {
        return this.maxHeaderBytes;
    }

    /** @param maxHeaderBytes 结构化响应头的最大字节数。 */
    public void setMaxHeaderBytes(int maxHeaderBytes) {
        this.maxHeaderBytes = maxHeaderBytes;
    }

    /** 校验 Redis key 和序列化限制。 */
    public void validate() {
        String prefix = Objects.requireNonNull(this.keyPrefix, "coco.idempotency.redis.key-prefix must not be null");
        if (!KEY_PREFIX.matcher(prefix).matches() || !prefix.endsWith(":")) {
            throw new IllegalArgumentException("coco.idempotency.redis.key-prefix must be a safe prefix ending in ':'");
        }
        requireRange(this.maxResponseBytes, 0, 16_777_216, "max-response-bytes");
        requireRange(this.maxHeaderBytes, 1, 1_048_576, "max-header-bytes");
    }

    private static void requireRange(int value, int minimum, int maximum, String property) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("coco.idempotency.redis." + property + " must be between "
                    + minimum + " and " + maximum);
        }
    }
}
