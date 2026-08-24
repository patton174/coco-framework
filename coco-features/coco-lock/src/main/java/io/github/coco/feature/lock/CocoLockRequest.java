package io.github.coco.feature.lock;

import java.time.Duration;
import java.util.Objects;

/**
 * 编程式申请锁的参数。
 * @param key 非空业务锁键
 * @param lease 租约时长
 * @param waitDuration 最长等待时长
 * @param pollInterval 等待期间轮询间隔
 */
public record CocoLockRequest(String key, Duration lease, Duration waitDuration, Duration pollInterval) {
    /** 校验锁请求。 */
    public CocoLockRequest {
        if (key == null || key.isBlank()) { throw new IllegalArgumentException("key must not be blank"); }
        lease = positive(lease, "lease");
        waitDuration = nonNegative(waitDuration, "waitDuration");
        pollInterval = positive(pollInterval, "pollInterval");
    }

    private static Duration positive(Duration value, String name) {
        value = Objects.requireNonNull(value, name + " must not be null");
        if (value.isZero() || value.isNegative()) { throw new IllegalArgumentException(name + " must be positive"); }
        return value;
    }

    private static Duration nonNegative(Duration value, String name) {
        value = Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) { throw new IllegalArgumentException(name + " must not be negative"); }
        return value;
    }
}
