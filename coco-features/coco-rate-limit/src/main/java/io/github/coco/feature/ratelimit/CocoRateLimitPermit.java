package io.github.coco.feature.ratelimit;

import java.util.Objects;

/**
 * 一次原子限流占用请求。
 * <p>
 * 不再携带预先算好的窗口边界，改为携带算法与窗口时长，由具体 {@link CocoRateLimitStore}
 * 按自身时钟解释窗口语义并回填 {@link CocoRateLimitDecision#resetAt()}。这样同一个 SPI
 * 能同时承载固定窗口、滑动窗口与令牌桶，而不必把某种算法的窗口模型烧进契约。
 * </p>
 * @param key 限流逻辑键
 * @param algorithm 限流算法
 * @param limit 窗口内允许的最大请求数（令牌桶下即桶容量）
 * @param windowSeconds 窗口时长秒数（令牌桶下即补满一整桶所需秒数）
 */
public record CocoRateLimitPermit(CocoRateLimitKey key, CocoRateLimitAlgorithm algorithm, long limit,
        long windowSeconds) {

    /**
     * 创建原子限流占用请求。
     * @param key 限流逻辑键
     * @param algorithm 限流算法
     * @param limit 窗口内允许的最大请求数
     * @param windowSeconds 窗口时长秒数
     */
    public CocoRateLimitPermit {
        key = Objects.requireNonNull(key, "key must not be null");
        algorithm = Objects.requireNonNull(algorithm, "algorithm must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be positive");
        }
    }
}
