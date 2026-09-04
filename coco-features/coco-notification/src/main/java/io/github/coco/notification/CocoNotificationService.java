package io.github.coco.notification;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按渠道类型路由通知到对应 {@link CocoNotificationChannel}。
 * <p>
 * 构造时把注入的渠道按 {@link CocoNotificationChannel#supportedType()} 建索引;同一类型出现
 * 多个渠道时,后者覆盖前者并告警(业务通常每类型只装一个)。发送到未注册类型时回失败结果而非抛异常。
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
public final class CocoNotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoNotificationService.class);

    private final Map<CocoNotificationChannelType, CocoNotificationChannel> channels =
            new EnumMap<>(CocoNotificationChannelType.class);

    /**
     * 创建通知服务。
     * @param channels 已注册的渠道
     */
    public CocoNotificationService(List<CocoNotificationChannel> channels) {
        for (CocoNotificationChannel channel : Objects.requireNonNull(channels, "channels must not be null")) {
            CocoNotificationChannel previous = this.channels.put(channel.supportedType(), channel);
            if (previous != null) {
                LOGGER.warn("Multiple Coco notification channels for type {}; {} overrides {}",
                        channel.supportedType(), channel.getClass().getName(), previous.getClass().getName());
            }
        }
    }

    /**
     * 发送一条通知。
     * @param notification 待发送通知
     * @return 发送结果;无对应渠道时为失败结果
     */
    public CocoNotificationResult send(CocoNotification notification) {
        Objects.requireNonNull(notification, "notification must not be null");
        CocoNotificationChannel channel = this.channels.get(notification.channelType());
        if (channel == null) {
            return CocoNotificationResult.failure(notification.channelType(),
                    "no channel registered for type " + notification.channelType());
        }
        return channel.send(notification);
    }

    /**
     * 是否存在支持某类型的渠道。
     * @param type 渠道类型
     * @return 存在返回 {@code true}
     */
    public boolean supports(CocoNotificationChannelType type) {
        return this.channels.containsKey(type);
    }
}
