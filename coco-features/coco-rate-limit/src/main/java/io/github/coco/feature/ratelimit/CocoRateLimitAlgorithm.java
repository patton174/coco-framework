package io.github.coco.feature.ratelimit;

/**
 * 限流算法。
 * <p>
 * 三种算法共用同一个心智模型：在 {@code windowSeconds} 秒内允许 {@code limit} 个请求。
 * 区别只在如何在时间上分摊这个额度。
 * </p>
 */
public enum CocoRateLimitAlgorithm {

    /**
     * 固定窗口。
     * <p>
     * 按 {@code windowSeconds} 对齐时间轴，每个窗口独立计数、到边界清零。实现最简、开销最低，
     * 但两个窗口交界处最多可放行 2×{@code limit} 个请求（前窗末尾 + 后窗开头），
     * 秒杀/支付这类对瞬时峰值敏感的场景不适用。
     * </p>
     */
    FIXED_WINDOW,

    /**
     * 滑动窗口。
     * <p>
     * 用当前窗口计数加上一窗口计数的时间加权值近似一个连续滑动的窗口，消除固定窗口交界处的
     * 2× 突发。开销略高于固定窗口（需保留上一窗口计数），是通用限流的稳妥默认。
     * </p>
     */
    SLIDING_WINDOW,

    /**
     * 令牌桶。
     * <p>
     * 桶容量为 {@code limit}，以 {@code limit/windowSeconds} 个/秒的速率匀速补充令牌，
     * 每个请求消耗一个。允许在容量范围内的突发，又把长期平均速率限制在配置值，
     * 适合"平时低峰、偶尔成组请求"的场景。
     * </p>
     */
    TOKEN_BUCKET
}
