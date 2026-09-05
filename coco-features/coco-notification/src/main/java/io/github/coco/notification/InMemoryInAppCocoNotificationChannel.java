package io.github.coco.notification;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 进程内站内信参考渠道。
 * <p>
 * 与日志渠道不同,这个是真能用的:把站内信按收件人存进进程内收件箱,应用可读取。仅适合单实例
 * 或开发环境——状态不跨实例、不持久化。生产多实例应换成落库/落 Redis 的实现。
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
public final class InMemoryInAppCocoNotificationChannel implements CocoNotificationChannel {

    private final Map<String, List<CocoNotification>> inboxes = new ConcurrentHashMap<>();

    @Override
    public CocoNotificationChannelType supportedType() {
        return CocoNotificationChannelType.IN_APP;
    }

    @Override
    public CocoNotificationResult send(CocoNotification notification) {
        this.inboxes.computeIfAbsent(notification.recipient(), key -> new CopyOnWriteArrayList<>())
                .add(notification);
        return CocoNotificationResult.success(CocoNotificationChannelType.IN_APP, null);
    }

    /**
     * 读取某收件人的站内信(按到达顺序)。
     * @param recipient 收件人标识
     * @return 不可变快照;无信时为空列表
     */
    public List<CocoNotification> inbox(String recipient) {
        return List.copyOf(this.inboxes.getOrDefault(recipient, List.of()));
    }
}
