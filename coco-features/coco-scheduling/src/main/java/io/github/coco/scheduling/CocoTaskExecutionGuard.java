package io.github.coco.scheduling;

/**
 * Coco 任务执行互斥 SPI。
 * <p>
 * 默认实现仅在本进程内互斥。需要跨实例互斥时，应用可提供分布式锁适配实现。
 * </p>
 *
 * @since 1.0.0
 */
public interface CocoTaskExecutionGuard {

    /**
     * 尝试取得指定任务的执行权。
     *
     * @param taskName 稳定任务名称
     * @return 取得成功时返回 {@code true}
     */
    boolean tryAcquire(String taskName);

    /**
     * 释放指定任务的执行权。
     *
     * @param taskName 稳定任务名称
     */
    void release(String taskName);
}
