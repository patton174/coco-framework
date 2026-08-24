package io.github.coco.feature.lock;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
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
}
