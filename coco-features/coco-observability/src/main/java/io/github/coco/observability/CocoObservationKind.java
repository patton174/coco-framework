package io.github.coco.observability;

/**
 * 可安全聚合的 Coco 观察事件种类。
 */
public enum CocoObservationKind {

    AUDIT,

    REPLAY,

    RATE_LIMIT,

    LOG_OVERFLOW
}
