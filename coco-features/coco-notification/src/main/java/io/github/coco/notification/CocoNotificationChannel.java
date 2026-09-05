package io.github.coco.notification;

/**
 * 通知渠道 SPI。
 * <p>
 * 一个渠道声明它支持的 {@link CocoNotificationChannelType},并实现实际发送。框架只提供
 * 本地参考实现(日志/内存);对接阿里云/腾讯云短信、SMTP 邮件等由业务自行引入 SDK 后
 * 实现本接口并注册为 Bean——与对象存储一致的纯 SPI 策略,框架不绑定任何云厂商依赖。
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
public interface CocoNotificationChannel {

    /**
     * 本渠道支持的类型。
     * @return 渠道类型
     */
    CocoNotificationChannelType supportedType();

    /**
     * 发送一条通知。
     * <p>
     * 实现应捕获自身异常并回 {@link CocoNotificationResult#failure} 而非抛出,让上层能按渠道
     * 聚合成败。
     * </p>
     * @param notification 待发送通知(其 {@code channelType} 必与 {@link #supportedType()} 一致)
     * @return 发送结果
     */
    CocoNotificationResult send(CocoNotification notification);
}
