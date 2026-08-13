package io.github.coco.feature.idempotency.store;

import java.util.Objects;
import java.util.Optional;

/**
 * 幂等请求原子获取结果。
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoIdempotencyAcquireResult {

    private final CocoIdempotencyAcquireStatus status;

    private final CocoIdempotencyLease lease;

    private final CocoIdempotencyStoredResponse response;

    private CocoIdempotencyAcquireResult(CocoIdempotencyAcquireStatus status, CocoIdempotencyLease lease,
            CocoIdempotencyStoredResponse response) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.lease = lease;
        this.response = response;
    }

    /**
     * 创建取得租约的结果。
     * @param lease 执行租约
     * @return 获取结果
     */
    public static CocoIdempotencyAcquireResult acquired(CocoIdempotencyLease lease) {
        return new CocoIdempotencyAcquireResult(CocoIdempotencyAcquireStatus.ACQUIRED,
                Objects.requireNonNull(lease, "lease must not be null"), null);
    }

    /**
     * 创建正在执行的结果。
     * @return 获取结果
     */
    public static CocoIdempotencyAcquireResult inProgress() {
        return new CocoIdempotencyAcquireResult(CocoIdempotencyAcquireStatus.IN_PROGRESS, null, null);
    }

    /**
     * 创建响应重放结果。
     * @param response 已完成响应
     * @return 获取结果
     */
    public static CocoIdempotencyAcquireResult replay(CocoIdempotencyStoredResponse response) {
        return new CocoIdempotencyAcquireResult(CocoIdempotencyAcquireStatus.REPLAY, null,
                Objects.requireNonNull(response, "response must not be null"));
    }

    /**
     * 创建请求不匹配结果。
     * @return 获取结果
     */
    public static CocoIdempotencyAcquireResult payloadMismatch() {
        return new CocoIdempotencyAcquireResult(CocoIdempotencyAcquireStatus.PAYLOAD_MISMATCH, null, null);
    }

    /**
     * 创建容量不足结果。
     * @return 获取结果
     */
    public static CocoIdempotencyAcquireResult capacityExceeded() {
        return new CocoIdempotencyAcquireResult(CocoIdempotencyAcquireStatus.CAPACITY_EXCEEDED, null, null);
    }

    /**
     * 返回结果状态。
     * @return 结果状态
     */
    public CocoIdempotencyAcquireStatus status() {
        return this.status;
    }

    /**
     * 返回执行租约。
     * @return 仅 `ACQUIRED` 状态存在的执行租约
     */
    public Optional<CocoIdempotencyLease> lease() {
        return Optional.ofNullable(this.lease);
    }

    /**
     * 返回已完成响应。
     * @return 仅 `REPLAY` 状态存在的响应
     */
    public Optional<CocoIdempotencyStoredResponse> response() {
        return Optional.ofNullable(this.response);
    }
}
