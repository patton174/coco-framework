package io.github.coco.feature.idempotency;

import java.time.Instant;
import java.util.Objects;

/**
 * 已获租的幂等键。
 * @param key 非敏感逻辑键
 * @param ownerToken 不可预测的租约所有者令牌
 * @param expiresAt 租约到期时间
 */
public record CocoIdempotencyLease(CocoIdempotencyKey key, String ownerToken, Instant expiresAt) {

    /** 创建租约。 */
    public CocoIdempotencyLease {
        key = Objects.requireNonNull(key, "key must not be null");
        ownerToken = Objects.requireNonNull(ownerToken, "ownerToken must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (ownerToken.isBlank()) {
            throw new IllegalArgumentException("ownerToken must not be blank");
        }
    }
}
