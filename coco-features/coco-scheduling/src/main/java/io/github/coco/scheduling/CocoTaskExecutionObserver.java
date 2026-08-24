package io.github.coco.scheduling;

/**
 * Coco 任务执行事件观察器 SPI。
 * <p>
 * 观察器异常会被调度器隔离，不会终止后续任务调度。
 * </p>
 *
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoTaskExecutionObserver {

    /**
     * 接收任务执行事件。
     *
     * @param event 执行事件
     */
    void onExecution(CocoTaskExecutionEvent event);
}
