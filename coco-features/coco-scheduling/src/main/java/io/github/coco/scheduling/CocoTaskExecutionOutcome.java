package io.github.coco.scheduling;

/**
 * Coco 任务最近一次执行结果。
 *
 * @since 1.0.0
 */
public enum CocoTaskExecutionOutcome {

    /** 尚未执行。 */
    NONE,

    /** 已开始执行。 */
    STARTED,

    /** 已成功完成。 */
    SUCCEEDED,

    /** 执行失败。 */
    FAILED,

    /** 因重叠策略跳过。 */
    SKIPPED
}
