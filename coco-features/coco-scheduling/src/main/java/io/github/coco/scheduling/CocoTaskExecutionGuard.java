package io.github.coco.scheduling;

/**
 * Coco 任务执行互斥 SPI。
 * <p>
 * 默认实现仅在本进程内互斥。需要跨实例互斥时，应用可提供分布式锁适配实现。
 * </p>
 * <p>
 * 分布式 guard 应在执行权失效时返回 {@code false}，使调度器在任务正常返回后失败关闭。该检查不能撤销
 * 已经发生的业务副作用；需要业务幂等或 fencing 的场景必须由业务资源协作实现。
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

    /**
     * 检查当前线程持有的指定任务执行权是否仍然有效。
     * <p>
     * 默认返回 {@code true}，以保持既有 guard 实现的源和二进制兼容性。
     * </p>
     * @param taskName 稳定任务名称
     * @return 执行权仍有效时返回 {@code true}
     */
    default boolean isExecutionValid(String taskName) {
        return true;
    }
}
