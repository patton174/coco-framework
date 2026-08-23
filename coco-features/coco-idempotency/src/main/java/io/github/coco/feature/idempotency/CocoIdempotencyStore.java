package io.github.coco.feature.idempotency;

/**
 * Coco 请求幂等原子租约存储 SPI。
 * <p>实现必须原子获得租约，并且只允许相同 owner token 释放当前租约。它与安全防重放存储的生命周期不同。</p>
 */
public interface CocoIdempotencyStore {

    /** 原子获取一个租约。 */
    AcquireResult acquire(CocoIdempotencyLease lease);

    /** 仅当租约 owner 仍匹配时释放该租约。 */
    void release(CocoIdempotencyLease lease);

    /** 获取结果。 */
    enum AcquireResult {
        ACQUIRED, DUPLICATE, UNAVAILABLE
    }
}
