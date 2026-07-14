package io.github.coco.feature.ratelimit;

import io.github.coco.exception.CocoBusinessCode;

/**
 * Coco 限流模块对外业务码。
 */
public enum CocoRateLimitErrorCode implements CocoBusinessCode {

    /** 请求超过已配置配额。 */
    EXCEEDED(42900, "coco.rate-limit.exceeded");

    private final int code;

    private final String messageCode;

    CocoRateLimitErrorCode(int code, String messageCode) {
        this.code = code;
        this.messageCode = messageCode;
    }

    @Override
    public int code() {
        return this.code;
    }

    @Override
    public String messageCode() {
        return this.messageCode;
    }
}
