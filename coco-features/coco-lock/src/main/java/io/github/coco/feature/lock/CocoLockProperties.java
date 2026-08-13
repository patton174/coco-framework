package io.github.coco.feature.lock;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco 锁模块配置。
 */
@ConfigurationProperties("coco.lock")
public class CocoLockProperties {

    private boolean enabled = true;

    private Duration defaultWait = Duration.ZERO;

    private Duration defaultLease = Duration.ofSeconds(30);

    /** @return 是否启用锁模块 */
    public boolean isEnabled() {
        return this.enabled;
    }

    /** @param enabled 是否启用锁模块 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 默认最长等待时间 */
    public Duration getDefaultWait() {
        return this.defaultWait;
    }

    /** @param defaultWait 默认最长等待时间 */
    public void setDefaultWait(Duration defaultWait) {
        this.defaultWait = defaultWait;
    }

    /** @return 默认锁租期 */
    public Duration getDefaultLease() {
        return this.defaultLease;
    }

    /** @param defaultLease 默认锁租期 */
    public void setDefaultLease(Duration defaultLease) {
        this.defaultLease = defaultLease;
    }

    /**
     * 校验绑定后的配置边界。
     */
    public void validate() {
        if (this.defaultWait == null || this.defaultLease == null) {
            throw new IllegalStateException("Coco lock durations must not be null");
        }
        if (this.defaultWait.isNegative()) {
            throw new IllegalStateException("coco.lock.default-wait must not be negative");
        }
        if (this.defaultLease.isZero() || this.defaultLease.isNegative()) {
            throw new IllegalStateException("coco.lock.default-lease must be positive");
        }
    }
}
