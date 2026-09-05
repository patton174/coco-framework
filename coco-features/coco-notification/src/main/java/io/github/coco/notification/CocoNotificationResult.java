package io.github.coco.notification;

import java.util.Objects;

/**
 * 单条通知的发送结果。
 * <p>
 * 发送失败不抛异常而是回一个 {@code success=false} 的结果,让调用方能按渠道聚合处理
 * (例如多渠道并发发送时统计成败),而不是被第一个异常打断。
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
public record CocoNotificationResult(boolean success, CocoNotificationChannelType channelType,
        String providerMessageId, String detail) {

    /**
     * 创建结果。
     * @param success 是否成功
     * @param channelType 渠道类型
     * @param providerMessageId 回执标识
     * @param detail 补充信息
     */
    public CocoNotificationResult {
        channelType = Objects.requireNonNull(channelType, "channelType must not be null");
    }

    /**
     * 成功结果。
     * @param channelType 渠道类型
     * @param providerMessageId 回执标识
     * @return 成功结果
     */
    public static CocoNotificationResult success(CocoNotificationChannelType channelType, String providerMessageId) {
        return new CocoNotificationResult(true, channelType, providerMessageId, null);
    }

    /**
     * 失败结果。
     * @param channelType 渠道类型
     * @param detail 失败原因
     * @return 失败结果
     */
    public static CocoNotificationResult failure(CocoNotificationChannelType channelType, String detail) {
        return new CocoNotificationResult(false, channelType, null, detail);
    }
}
