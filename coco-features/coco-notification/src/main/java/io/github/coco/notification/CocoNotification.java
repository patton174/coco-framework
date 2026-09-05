package io.github.coco.notification;

import java.util.Map;
import java.util.Objects;

/**
 * 一条待发送的通知。
 * <p>
 * 与渠道无关的最小载荷:目标类型、收件人、标题、正文,外加一组自由属性(如短信模板号、
 * 邮件抄送、站内信业务标签),由具体渠道自行解释。
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
public record CocoNotification(CocoNotificationChannelType channelType, String recipient, String subject,
        String content, Map<String, String> attributes) {

    /**
     * 创建通知;规范化 null 属性为空 Map 并做基本非空校验。
     * @param channelType 目标渠道类型
     * @param recipient 收件人
     * @param subject 标题
     * @param content 正文
     * @param attributes 渠道特定属性
     */
    public CocoNotification {
        channelType = Objects.requireNonNull(channelType, "channelType must not be null");
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient must not be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("content must not be null");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 便捷构造:无标题、无附加属性。
     * @param channelType 渠道类型
     * @param recipient 收件人
     * @param content 正文
     * @return 通知
     */
    public static CocoNotification of(CocoNotificationChannelType channelType, String recipient, String content) {
        return new CocoNotification(channelType, recipient, null, content, Map.of());
    }
}
