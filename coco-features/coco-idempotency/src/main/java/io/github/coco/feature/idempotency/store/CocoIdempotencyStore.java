package io.github.coco.feature.idempotency.store;

import java.time.Instant;

/**
 * 幂等状态存储 SPI。
 * <p>实现必须保证每个方法内部的状态判断与转换是原子的。</p>
 *
 * @author patton174
 * @since 1.0.0
 */
public interface CocoIdempotencyStore extends AutoCloseable {

    /**
     * 原子获取幂等请求执行权。
     * @param request 已脱敏请求身份
     * @param now 当前时间
     * @param expiresAt 绝对过期时间
     * @return 确定的获取结果
     */
    CocoIdempotencyAcquireResult acquire(CocoIdempotencyRequest request, Instant now, Instant expiresAt);

    /**
     * 原子完成当前租约。
     * @param lease 当前执行租约
     * @param response 可重放响应
     * @param now 当前时间
     * @return 租约仍有效且完成成功时返回 {@code true}
     */
    boolean complete(CocoIdempotencyLease lease, CocoIdempotencyStoredResponse response, Instant now);

    /**
     * 原子释放当前执行租约。
     * @param lease 当前执行租约
     * @param now 当前时间
     * @return 删除匹配的活动租约时返回 {@code true}
     */
    boolean fail(CocoIdempotencyLease lease, Instant now);

    /**
     * 关闭存储资源。
     */
    @Override
    default void close() {
    }
}
