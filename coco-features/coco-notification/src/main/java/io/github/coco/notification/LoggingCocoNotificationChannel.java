package io.github.coco.notification;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把通知写到日志的参考渠道。
 * <p>
 * 面向开发/测试:不真正外发,只记录一条日志并回成功,方便本地验证发送链路而不引入任何
 * 云厂商 SDK。生产环境应换成实现了 {@link CocoNotificationChannel} 的真实渠道。为避免泄露,
 * 只记录收件人与标题,不打印正文。
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
public final class LoggingCocoNotificationChannel implements CocoNotificationChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingCocoNotificationChannel.class);

    private final CocoNotificationChannelType type;

    /**
     * 创建日志渠道。
     * @param type 声明支持的渠道类型
     */
    public LoggingCocoNotificationChannel(CocoNotificationChannelType type) {
        this.type = Objects.requireNonNull(type, "type must not be null");
    }

    @Override
    public CocoNotificationChannelType supportedType() {
        return this.type;
    }

    @Override
    public CocoNotificationResult send(CocoNotification notification) {
        // Body intentionally omitted to avoid logging message content.
        LOGGER.info("[coco-notification:{}] to={} subject={}", this.type, notification.recipient(),
                notification.subject());
        return CocoNotificationResult.success(this.type, "logged");
    }
}
