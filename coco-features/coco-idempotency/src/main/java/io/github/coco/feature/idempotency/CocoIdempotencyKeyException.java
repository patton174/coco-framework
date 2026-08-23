package io.github.coco.feature.idempotency;

/** 请求幂等键校验失败。 */
public final class CocoIdempotencyKeyException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** 创建不包含原始键的校验异常。 */
    public CocoIdempotencyKeyException(String message) { super(message); }
}
