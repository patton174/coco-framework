package io.github.coco.feature.concurrencylimit;

/**
 * Coco Servlet 异步请求处理策略。
 */
public enum CocoConcurrencyLimitAsyncPolicy {

    /** 通过 Servlet {@code AsyncListener} 持有许可直到异步生命周期结束。 */
    TRACK,

    /** 禁止受保护请求进入 Servlet 异步处理。 */
    REJECT,

    /** 只限制同步调度，异步调度直接跳过。 */
    SKIP
}
