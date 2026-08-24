package io.github.coco.scheduling;

/**
 * Coco 任务重叠执行策略。
 *
 * @since 1.0.0
 */
public enum CocoTaskOverlapPolicy {

    /** 允许同名任务的多个执行重叠。 */
    ALLOW,

    /** 当前一次执行尚未结束时跳过新的触发。 */
    SKIP
}
