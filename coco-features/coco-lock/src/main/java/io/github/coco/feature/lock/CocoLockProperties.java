package io.github.coco.feature.lock;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.core.Ordered;

/** Coco 分布式锁配置。 */
@ConfigurationProperties("coco.lock")
public class CocoLockProperties {
    private boolean enabled;
    private Duration lease = Duration.ofSeconds(30);
    private Duration wait = Duration.ZERO;
    private Duration pollInterval = Duration.ofMillis(50);
    private boolean watchdogEnabled = true;
    private Duration watchdogInterval = Duration.ofSeconds(10);
    private int maxEntries = 100_000;
    private Duration cleanupInterval = Duration.ofMinutes(1);
    private int maxKeyLength = 256;
    private int aspectOrder = Ordered.LOWEST_PRECEDENCE - 100;
    private CocoLockStoreType storeType = CocoLockStoreType.IN_MEMORY;
    @NestedConfigurationProperty
    private final Redis redis = new Redis();
    public boolean isEnabled() { return this.enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Duration getLease() { return this.lease; }
    public void setLease(Duration lease) { this.lease = lease; }
    public Duration getWait() { return this.wait; }
    public void setWait(Duration wait) { this.wait = wait; }
    public Duration getPollInterval() { return this.pollInterval; }
    public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
    public boolean isWatchdogEnabled() { return this.watchdogEnabled; }
    public void setWatchdogEnabled(boolean watchdogEnabled) { this.watchdogEnabled = watchdogEnabled; }
    public Duration getWatchdogInterval() { return this.watchdogInterval; }
    public void setWatchdogInterval(Duration watchdogInterval) { this.watchdogInterval = watchdogInterval; }
    public int getMaxEntries() { return this.maxEntries; }
    public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    public Duration getCleanupInterval() { return this.cleanupInterval; }
    public void setCleanupInterval(Duration cleanupInterval) { this.cleanupInterval = cleanupInterval; }
    public int getMaxKeyLength() { return this.maxKeyLength; }
    public void setMaxKeyLength(int maxKeyLength) { this.maxKeyLength = maxKeyLength; }
    public int getAspectOrder() { return this.aspectOrder; }
    public void setAspectOrder(int aspectOrder) { this.aspectOrder = aspectOrder; }
    public CocoLockStoreType getStoreType() { return this.storeType; }
    public void setStoreType(CocoLockStoreType storeType) {
        this.storeType = storeType == null ? CocoLockStoreType.IN_MEMORY : storeType;
    }
    public Redis getRedis() { return this.redis; }
    public void setRedis(Redis redis) {
        Redis copy = Redis.copyOf(redis);
        this.redis.setKeyPrefix(copy.getKeyPrefix());
        this.redis.setTemplateBeanName(copy.getTemplateBeanName());
    }

    /** Redis 共享存储配置。 */
    public static class Redis {
        private String keyPrefix = "coco:lock:";
        private String templateBeanName;
        public String getKeyPrefix() { return this.keyPrefix; }
        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix == null || keyPrefix.isBlank() ? "coco:lock:" : keyPrefix.trim();
        }
        public String getTemplateBeanName() { return this.templateBeanName; }
        public void setTemplateBeanName(String templateBeanName) {
            this.templateBeanName = templateBeanName == null || templateBeanName.isBlank() ? null : templateBeanName.trim();
        }
        static Redis copyOf(Redis source) {
            Redis copy = new Redis();
            if (source != null) {
                copy.setKeyPrefix(source.getKeyPrefix());
                copy.setTemplateBeanName(source.getTemplateBeanName());
            }
            return copy;
        }
    }
}
