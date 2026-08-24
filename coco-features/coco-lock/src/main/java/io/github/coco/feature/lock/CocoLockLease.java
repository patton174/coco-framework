package io.github.coco.feature.lock;

import java.time.Instant;
import java.util.Objects;

/**
 * 已申请的锁租约。
 * @param key 业务锁键
 * @param ownerToken 不可预测的所有者令牌
 * @param expiresAt 租约到期时间
 */
public record CocoLockLease(String key, String ownerToken, Instant expiresAt) {
    /** 校验租约的必要字段。 */
    public CocoLockLease {
        if (key == null || key.isBlank()) { throw new IllegalArgumentException("key must not be blank"); }
        if (ownerToken == null || ownerToken.isBlank()) { throw new IllegalArgumentException("ownerToken must not be blank"); }
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
