package io.github.coco.scheduling;

/**
 * Coco 任务的触发方式。
 *
 * @since 1.0.0
 */
public enum CocoTaskScheduleType {

    /** Cron 表达式触发。 */
    CRON,

    /** 固定延迟触发。 */
    FIXED_DELAY,

    /** 固定频率触发。 */
    FIXED_RATE
}
