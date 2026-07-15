package io.github.coco.observability;

/**
 * 可安全聚合的 Coco 观察结果。
 */
public enum CocoObservationOutcome {

    SUCCESS,

    FAILURE,

    ACCEPTED,

    DUPLICATE,

    CAPACITY_EXCEEDED,

    ERROR,

    ALLOWED,

    REJECTED,

    DROPPED
}
