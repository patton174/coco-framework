package io.github.coco.feature.concurrencylimit;

/**
 * Coco 并发许可拒绝原因。
 */
public enum CocoConcurrencyLimitRejectionReason {

    /** 某个并发维度已经达到限制。 */
    LIMIT_REACHED,

    /** 存储的活动键容量已经耗尽。 */
    CAPACITY_EXHAUSTED,

    /** 存储不可用或已经关闭。 */
    UNAVAILABLE
}
