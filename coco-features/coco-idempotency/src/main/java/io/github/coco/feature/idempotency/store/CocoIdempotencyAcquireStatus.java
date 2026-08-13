package io.github.coco.feature.idempotency.store;

/**
 * 幂等获取结果状态。
 *
 * @author patton174
 * @since 1.0.0
 */
public enum CocoIdempotencyAcquireStatus {

    /** 当前请求取得执行租约。 */
    ACQUIRED,

    /** 同一请求正在执行。 */
    IN_PROGRESS,

    /** 同一请求已有可重放响应。 */
    REPLAY,

    /** 相同幂等键绑定了不同请求。 */
    PAYLOAD_MISMATCH,

    /** 存储容量已满。 */
    CAPACITY_EXCEEDED
}
