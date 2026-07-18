package io.github.coco.feature.ratelimit;

import java.time.Instant;
import java.util.Objects;

/**
 * 原子限流占用结果。
 * @param allowed 当前请求是否允许继续处理
 * @param limit 当前窗口总配额
 * @param remaining 当前窗口剩余配额
 * @param resetAt 当前窗口结束时间
 * @param capacityExhausted 是否因为存储容量已耗尽而拒绝
 */
public record CocoRateLimitDecision(boolean allowed, long limit, long remaining, Instant resetAt,
        boolean capacityExhausted) {

    /**
     * 创建原子限流占用结果。
     * @param allowed 当前请求是否允许继续处理
     * @param limit 当前窗口总配额
     * @param remaining 当前窗口剩余配额
     * @param resetAt 当前窗口结束时间
     * @param capacityExhausted 是否因为存储容量已耗尽而拒绝
     */
    public CocoRateLimitDecision {
        resetAt = Objects.requireNonNull(resetAt, "resetAt must not be null");
        if (limit <= 0 || remaining < 0 || remaining > limit) {
            throw new IllegalArgumentException("Rate-limit decision values are invalid");
        }
    }
}
