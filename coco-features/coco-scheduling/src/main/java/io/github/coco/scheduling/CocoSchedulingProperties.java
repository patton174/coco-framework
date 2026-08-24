package io.github.coco.scheduling;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 调度模块配置属性。
 *
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.scheduling")
public class CocoSchedulingProperties {

    private boolean enabled = true;
    private int poolSize = 1;
    private String threadNamePrefix = "coco-scheduling-";
    private CocoSchedulingGuardType guardType = CocoSchedulingGuardType.IN_MEMORY;
    @NestedConfigurationProperty
    private final GuardProperties guard = new GuardProperties();
    @NestedConfigurationProperty
    private ShutdownProperties shutdown = new ShutdownProperties();

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPoolSize() {
        return this.poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public String getThreadNamePrefix() {
        return this.threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public CocoSchedulingGuardType getGuardType() {
        return this.guardType;
    }

    public void setGuardType(CocoSchedulingGuardType guardType) {
        this.guardType = guardType == null ? CocoSchedulingGuardType.IN_MEMORY : guardType;
    }

    public GuardProperties getGuard() {
        return this.guard;
    }

    public void setGuard(GuardProperties guard) {
        GuardProperties copy = GuardProperties.copyOf(guard);
        this.guard.setLease(copy.getLease());
        this.guard.setWait(copy.getWait());
        this.guard.setPollInterval(copy.getPollInterval());
    }

    public ShutdownProperties getShutdown() {
        return this.shutdown;
    }

    public void setShutdown(ShutdownProperties shutdown) {
        this.shutdown = shutdown == null ? new ShutdownProperties() : shutdown;
    }

    /**
     * 关闭期间的取消和等待配置。
     *
     * @since 1.0.0
     */
    public static class ShutdownProperties {

        private Duration awaitTermination = Duration.ofSeconds(30);
        private boolean interrupt;

        public Duration getAwaitTermination() {
            return this.awaitTermination;
        }

        public void setAwaitTermination(Duration awaitTermination) {
            this.awaitTermination = awaitTermination;
        }

        public boolean isInterrupt() {
            return this.interrupt;
        }

        public void setInterrupt(boolean interrupt) {
            this.interrupt = interrupt;
        }
    }

    /** CocoLock 任务 guard 的锁请求配置。 */
    public static class GuardProperties {

        private Duration lease = Duration.ofSeconds(30);
        private Duration wait = Duration.ZERO;
        private Duration pollInterval = Duration.ofMillis(50);

        public Duration getLease() {
            return this.lease;
        }

        public void setLease(Duration lease) {
            this.lease = lease;
        }

        public Duration getWait() {
            return this.wait;
        }

        public void setWait(Duration wait) {
            this.wait = wait;
        }

        public Duration getPollInterval() {
            return this.pollInterval;
        }

        public void setPollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
        }

        static GuardProperties copyOf(GuardProperties source) {
            GuardProperties copy = new GuardProperties();
            if (source != null) {
                copy.setLease(source.getLease());
                copy.setWait(source.getWait());
                copy.setPollInterval(source.getPollInterval());
            }
            return copy;
        }
    }
}
