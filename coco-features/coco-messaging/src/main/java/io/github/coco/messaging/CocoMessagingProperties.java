package io.github.coco.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 消息模块配置。
 */
@ConfigurationProperties(prefix = "coco.messaging")
public class CocoMessagingProperties {

    private boolean enabled = true;

    private CocoMessageDeliveryMode deliveryMode = CocoMessageDeliveryMode.SYNC;

    private CocoMessageFailurePolicy failurePolicy = CocoMessageFailurePolicy.FAIL_FAST;

    private CocoMessageNoSubscriberPolicy noSubscriberPolicy = CocoMessageNoSubscriberPolicy.IGNORE;

    @NestedConfigurationProperty
    private AsyncProperties async = new AsyncProperties();

    /** @return 是否启用消息基础设施 */
    public boolean isEnabled() {
        return this.enabled;
    }

    /** @param enabled 是否启用消息基础设施 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return 投递模式 */
    public CocoMessageDeliveryMode getDeliveryMode() {
        return this.deliveryMode;
    }

    /** @param deliveryMode 投递模式；为空时使用同步模式 */
    public void setDeliveryMode(CocoMessageDeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode == null ? CocoMessageDeliveryMode.SYNC : deliveryMode;
    }

    /** @return 处理器异常策略 */
    public CocoMessageFailurePolicy getFailurePolicy() {
        return this.failurePolicy;
    }

    /** @param failurePolicy 处理器异常策略；为空时快速失败 */
    public void setFailurePolicy(CocoMessageFailurePolicy failurePolicy) {
        this.failurePolicy = failurePolicy == null ? CocoMessageFailurePolicy.FAIL_FAST : failurePolicy;
    }

    /** @return 无订阅者策略 */
    public CocoMessageNoSubscriberPolicy getNoSubscriberPolicy() {
        return this.noSubscriberPolicy;
    }

    /** @param noSubscriberPolicy 无订阅者策略；为空时忽略 */
    public void setNoSubscriberPolicy(CocoMessageNoSubscriberPolicy noSubscriberPolicy) {
        this.noSubscriberPolicy = noSubscriberPolicy == null ? CocoMessageNoSubscriberPolicy.IGNORE : noSubscriberPolicy;
    }

    /** @return 异步投递配置 */
    public AsyncProperties getAsync() {
        return this.async;
    }

    /** @param async 异步投递配置 */
    public void setAsync(AsyncProperties async) {
        this.async = async == null ? new AsyncProperties() : async;
    }

    /**
     * 异步投递配置。
     * <p>
     * 默认仅使用一个工作线程，保证同一传输实例的投递顺序。队列满时使用拒绝策略，不在调用线程回退执行。
     * </p>
     */
    public static class AsyncProperties {

        private int queueCapacity = 256;

        private Duration shutdownAwait = Duration.ofSeconds(30);

        private CocoMessageAsyncShutdownPolicy shutdownPolicy = CocoMessageAsyncShutdownPolicy.DRAIN;

        /** @return 有界队列容量 */
        public int getQueueCapacity() {
            return this.queueCapacity;
        }

        /** @param queueCapacity 有界队列容量，必须大于零 */
        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        /** @return 关闭时等待排空的最长时间 */
        public Duration getShutdownAwait() {
            return this.shutdownAwait;
        }

        /** @param shutdownAwait 关闭时等待排空的最长时间 */
        public void setShutdownAwait(Duration shutdownAwait) {
            this.shutdownAwait = shutdownAwait;
        }

        /** @return 异步关闭策略 */
        public CocoMessageAsyncShutdownPolicy getShutdownPolicy() {
            return this.shutdownPolicy;
        }

        /** @param shutdownPolicy 异步关闭策略；为空时排空 */
        public void setShutdownPolicy(CocoMessageAsyncShutdownPolicy shutdownPolicy) {
            this.shutdownPolicy = shutdownPolicy == null ? CocoMessageAsyncShutdownPolicy.DRAIN : shutdownPolicy;
        }
    }
}
