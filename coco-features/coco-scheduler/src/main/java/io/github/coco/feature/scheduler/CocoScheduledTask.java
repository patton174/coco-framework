package io.github.coco.feature.scheduler;

/**
 * Coco 调度任务 SPI。
 * <p>
 * 任务标识在应用内必须稳定且唯一；业务方通过配置清单选择要注册的任务，而非扫描任意注解方法。
 * </p>
 */
public interface CocoScheduledTask {

    /**
     * 返回稳定的任务标识。
     * @return 任务标识
     */
    String taskId();

    /**
     * 执行一次任务。
     * @param context 本次执行上下文
     * @throws Exception 允许重试的普通失败
     */
    void execute(CocoTaskExecutionContext context) throws Exception;
}
