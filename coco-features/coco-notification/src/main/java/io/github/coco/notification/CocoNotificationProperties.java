package io.github.coco.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco 通知配置。
 * <p>
 * 默认关闭,需显式打开 {@code coco.notification.enabled}。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-notification}</li>
 * </ul>
 * @author patton174
 * @since 2.1.0
 */
@ConfigurationProperties("coco.notification")
public class CocoNotificationProperties {

    private boolean enabled;

    /**
     * 是否为未被业务渠道覆盖的 SMS/EMAIL 类型注册日志参考渠道。
     * <p>
     * 面向开发/测试:打开后,没有业务实现的 SMS/EMAIL 也能"发送成功"(仅记日志),便于跑通链路。
     * 生产建议关闭,以免误以为真的发出去了。
     * </p>
     */
    private boolean loggingFallback = true;

    /**
     * 是否注册进程内站内信参考渠道(IN_APP)。
     * <p>
     * 该实现真能用但不跨实例、不持久化;多实例生产应关闭并提供自己的实现。
     * </p>
     */
    private boolean inMemoryInApp = true;

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isLoggingFallback() {
        return this.loggingFallback;
    }

    public void setLoggingFallback(boolean loggingFallback) {
        this.loggingFallback = loggingFallback;
    }

    public boolean isInMemoryInApp() {
        return this.inMemoryInApp;
    }

    public void setInMemoryInApp(boolean inMemoryInApp) {
        this.inMemoryInApp = inMemoryInApp;
    }
}
