package io.github.coco.scheduling;

import java.util.List;
import java.util.Optional;

/**
 * Coco 动态任务注册表 API。
 *
 * @since 1.0.0
 */
public interface CocoTaskRegistry {

    /**
     * 注册新任务。同名任务已存在时拒绝注册。
     *
     * @param definition 任务定义
     */
    void register(CocoTaskDefinition definition);

    /**
     * 原子替换已注册任务，并取消旧任务的 future。
     *
     * @param definition 新任务定义
     */
    void replace(CocoTaskDefinition definition);

    /**
     * 取消并移除任务。
     *
     * @param name 稳定任务名称
     * @return 存在任务并成功移除时返回 {@code true}
     */
    boolean cancel(String name);

    /**
     * 返回当前全部任务状态。
     *
     * @return 状态列表
     */
    List<CocoTaskStatus> list();

    /**
     * 查询指定任务状态。
     *
     * @param name 稳定任务名称
     * @return 状态；不存在时为空
     */
    Optional<CocoTaskStatus> status(String name);
}
