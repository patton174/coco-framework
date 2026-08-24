package io.github.coco.scheduling;

import io.github.coco.i18n.CocoMessageCode;

/**
 * Coco 调度模块消息编码。
 *
 * @since 1.0.0
 */
enum CocoSchedulingMessage implements CocoMessageCode {

    TASK_NAME_REQUIRED("coco.scheduling.task-name-required"),
    TASK_REQUIRED("coco.scheduling.task-required"),
    TASK_EXISTS("coco.scheduling.task-exists"),
    TASK_NOT_FOUND("coco.scheduling.task-not-found"),
    SCHEDULE_EXACTLY_ONE("coco.scheduling.schedule-exactly-one"),
    SCHEDULE_DURATION_POSITIVE("coco.scheduling.schedule-duration-positive"),
    INITIAL_DELAY_NEGATIVE("coco.scheduling.initial-delay-negative"),
    CRON_INVALID("coco.scheduling.cron-invalid"),
    SCHEDULER_CLOSED("coco.scheduling.scheduler-closed"),
    SCHEDULER_REJECTED("coco.scheduling.scheduler-rejected"),
    ANNOTATION_METHOD_ARGUMENTS("coco.scheduling.annotation-method-arguments"),
    ANNOTATION_METHOD_NOT_INVOCABLE("coco.scheduling.annotation-method-not-invocable"),
    ANNOTATION_DURATION_INVALID("coco.scheduling.annotation-duration-invalid"),
    ANNOTATION_ZONE_INVALID("coco.scheduling.annotation-zone-invalid"),
    POOL_SIZE_INVALID("coco.scheduling.pool-size-invalid"),
    AWAIT_TERMINATION_NEGATIVE("coco.scheduling.await-termination-negative"),
    GUARD_EXECUTION_INVALID("coco.scheduling.guard-execution-invalid");

    private final String code;

    CocoSchedulingMessage(String code) {
        this.code = code;
    }

    @Override
    public String code() {
        return this.code;
    }
}
