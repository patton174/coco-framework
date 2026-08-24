package io.github.coco.scheduling;

/**
 * Coco 任务调度编程 API。
 * <p>
 * 关闭调度器会取消全部已注册任务；该接口由容器自动配置的默认实现负责生命周期管理。
 * </p>
 *
 * @since 1.0.0
 */
public interface CocoTaskScheduler extends CocoTaskRegistry, AutoCloseable {

    /**
     * 关闭调度器并取消已注册任务。
     */
    @Override
    void close();
}
