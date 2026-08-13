package io.github.coco.feature.idempotency.store;

import java.time.Instant;
import java.util.Objects;

/**
 * 幂等执行租约。
 *
 * @param request 已脱敏请求身份
 * @param ownerToken 本次执行的不透明所有权令牌
 * @param expiresAt 绝对过期时间
 * @author patton174
 * @since 1.0.0
 */
public record CocoIdempotencyLease(CocoIdempotencyRequest request, String ownerToken, Instant expiresAt) {

    /**
     * 创建幂等执行租约。
     */
    public CocoIdempotencyLease {
        request = Objects.requireNonNull(request, "request must not be null");
        ownerToken = Objects.requireNonNull(ownerToken, "ownerToken must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (ownerToken.isBlank()) {
            throw new IllegalArgumentException("ownerToken must not be blank");
        }
    }
}
