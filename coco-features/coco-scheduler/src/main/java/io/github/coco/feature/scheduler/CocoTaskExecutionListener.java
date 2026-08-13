package io.github.coco.feature.scheduler;

import java.time.Duration;

/**
 * Coco 任务执行观测 SPI。
 * <p>
 * 监听器异常会被隔离，不会改变任务成功、失败或重试结果。
 * </p>
 */
public interface CocoTaskExecutionListener {

    /**
     * 任务开始执行时调用。
     * @param event 执行事件
     */
    default void onStarted(CocoTaskExecutionEvent event) {
    }

    /**
     * 任务一次尝试成功时调用。
     * @param event 执行事件
     * @param duration 执行耗时
     */
    default void onSucceeded(CocoTaskExecutionEvent event, Duration duration) {
    }

    /**
     * 任务一次尝试失败时调用。
     * @param event 执行事件
     * @param failure 失败原因
     */
    default void onFailed(CocoTaskExecutionEvent event, Throwable failure) {
    }

    /**
     * 将安排重试时调用。
     * @param event 执行事件
     * @param delay 重试延迟
     * @param failure 失败原因
     */
    default void onRetryScheduled(CocoTaskExecutionEvent event, Duration delay, Throwable failure) {
    }

    /**
     * 因重叠策略跳过触发时调用。
     * @param taskId 任务标识
     */
    default void onSkipped(String taskId) {
    }
}
