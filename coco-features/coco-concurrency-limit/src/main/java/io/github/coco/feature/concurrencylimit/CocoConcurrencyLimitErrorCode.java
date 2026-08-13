package io.github.coco.feature.concurrencylimit;

import io.github.coco.exception.CocoBusinessCode;

/**
 * Coco 在途请求并发限制业务码。
 */
public enum CocoConcurrencyLimitErrorCode implements CocoBusinessCode {

    /** 当前并发维度已经达到上限。 */
    REJECTED(42910, "coco.concurrency-limit.rejected"),

    /** 键解析或并发存储不可用。 */
    UNAVAILABLE(42911, "coco.concurrency-limit.unavailable"),

    /** 当前策略拒绝 Servlet 异步调度。 */
    ASYNC_REJECTED(42912, "coco.concurrency-limit.async-rejected");

    private final int code;

    private final String messageCode;

    CocoConcurrencyLimitErrorCode(int code, String messageCode) {
        this.code = code;
        this.messageCode = messageCode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int code() {
        return this.code;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String messageCode() {
        return this.messageCode;
    }
}
