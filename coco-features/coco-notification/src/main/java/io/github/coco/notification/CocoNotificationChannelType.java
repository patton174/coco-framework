package io.github.coco.notification;

/**
 * 通知渠道类型。
 * <p>
 * 一个 {@link CocoNotificationChannel} 声明自己支持的类型;发送时按类型路由到对应渠道。
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
public enum CocoNotificationChannelType {

    /** 短信。收件人为手机号。 */
    SMS,

    /** 邮件。收件人为邮箱地址。 */
    EMAIL,

    /** 站内信。收件人为用户标识,消息落到应用自己的收件箱存储。 */
    IN_APP
}
