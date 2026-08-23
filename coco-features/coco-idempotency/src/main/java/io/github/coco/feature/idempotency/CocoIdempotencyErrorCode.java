package io.github.coco.feature.idempotency;

/** 幂等拒绝响应语义。 */
public enum CocoIdempotencyErrorCode {
    INVALID_KEY(40010, "coco.idempotency.invalid-key"),
    DUPLICATE(40910, "coco.idempotency.duplicate"),
    UNAVAILABLE(50310, "coco.idempotency.unavailable");
    private final int code;
    private final String messageCode;
    CocoIdempotencyErrorCode(int code, String messageCode) { this.code = code; this.messageCode = messageCode; }
    public int code() { return this.code; }
    public String messageCode() { return this.messageCode; }
}
