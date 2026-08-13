package io.github.coco.feature.lock;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco 锁模块配置。
 */
@ConfigurationProperties("coco.lock")
public class CocoLockProperties {

    private boolean enabled = true;

    private Type type = Type.LOCAL;

    private Duration defaultWait = Duration.ZERO;

    private Duration defaultLease = Duration.ofSeconds(30);

    private String keyPrefix = "coco:lock:";

    /**
     * 返回是否启用自动配置。
     * @return 是否启用
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * 设置是否启用自动配置。
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回默认锁实现类型。
     * @return 锁实现类型
     */
    public Type getType() {
        return this.type;
    }

    /**
     * 设置默认锁实现类型。
     * @param type 锁实现类型
     */
    public void setType(Type type) {
        this.type = type;
    }

    /**
     * 返回默认最长等待时间。
     * @return 默认等待时间
     */
    public Duration getDefaultWait() {
        return this.defaultWait;
    }

    /**
     * 设置默认最长等待时间。
     * @param defaultWait 默认等待时间
     */
    public void setDefaultWait(Duration defaultWait) {
        this.defaultWait = defaultWait;
    }

    /**
     * 返回默认锁租期。
     * @return 默认租期
     */
    public Duration getDefaultLease() {
        return this.defaultLease;
    }

    /**
     * 设置默认锁租期。
     * @param defaultLease 默认租期
     */
    public void setDefaultLease(Duration defaultLease) {
        this.defaultLease = defaultLease;
    }

    /**
     * 返回 Redis 锁键前缀。
     * @return 锁键前缀
     */
    public String getKeyPrefix() {
        return this.keyPrefix;
    }

    /**
     * 设置 Redis 锁键前缀。
     * @param keyPrefix 锁键前缀
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    /**
     * 校验绑定后的配置边界。
     */
    public void validate() {
        Objects.requireNonNull(this.type, "coco.lock.type must not be null");
        Objects.requireNonNull(this.defaultWait, "coco.lock.default-wait must not be null");
        Objects.requireNonNull(this.defaultLease, "coco.lock.default-lease must not be null");
        if (this.defaultWait.isNegative()) {
            throw new IllegalStateException("coco.lock.default-wait must not be negative");
        }
        if (this.defaultLease.isZero() || this.defaultLease.isNegative()) {
            throw new IllegalStateException("coco.lock.default-lease must be positive");
        }
        if (this.type == Type.REDIS) {
            validateRedisLease(this.defaultLease);
            if (this.keyPrefix == null || this.keyPrefix.isBlank()) {
                throw new IllegalStateException("coco.lock.key-prefix must not be blank when type is redis");
            }
        }
    }

    private static void validateRedisLease(Duration lease) {
        try {
            if (lease.toMillis() <= 0) {
                throw new IllegalStateException("coco.lock.default-lease must be at least one millisecond for redis");
            }
        }
        catch (ArithmeticException ex) {
            throw new IllegalStateException("coco.lock.default-lease is too large for redis", ex);
        }
    }

    /**
     * 可选的默认锁实现类型。
     */
    public enum Type {
        /** 本地 JVM 锁。 */
        LOCAL,
        /** Redis 分布式锁。 */
        REDIS
    }
}
