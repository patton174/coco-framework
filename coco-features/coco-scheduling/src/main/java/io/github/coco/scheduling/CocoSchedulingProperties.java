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
}
