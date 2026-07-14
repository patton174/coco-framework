package io.github.coco.feature.ratelimit;

import java.time.Instant;
import java.util.Objects;

/**
 * 一次原子限流占用请求。
 * @param key 限流逻辑键
 * @param limit 当前窗口允许的最大请求数
 * @param resetAt 当前固定窗口结束时间
 */
public record CocoRateLimitPermit(CocoRateLimitKey key, long limit, Instant resetAt) {

    /**
     * 创建原子限流占用请求。
     * @param key 限流逻辑键
     * @param limit 当前窗口允许的最大请求数
     * @param resetAt 当前固定窗口结束时间
     */
    public CocoRateLimitPermit {
        key = Objects.requireNonNull(key, "key must not be null");
        resetAt = Objects.requireNonNull(resetAt, "resetAt must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
