package io.github.coco.logging.core;

/**
 * Coco 异步日志丢弃监听器。
 * <p>
 * 当异步日志队列拒绝低等级日志时接收非重入通知，可用于桥接指标、告警等观测能力。
 * 同一线程中的监听器重入会被抑制，但嵌套丢弃仍计入输出器累计数。
 * 回调在日志提交线程执行，实现应保持快速且避免主动阻塞。并发回调的累计序号唯一，
 * 但不同线程之间不保证调用或完成顺序。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoAsyncLogDropListener {

    /**
     * <p>
     * 处理异步日志丢弃通知。
     * </p>
     *
     * @param level 被丢弃日志的级别
     * @param handleName 被丢弃日志的句柄名称
     * @param totalDropped 当前异步输出器的唯一累计丢弃序号
     */
    void onDropped(CocoLogLevel level, String handleName, long totalDropped);
}
