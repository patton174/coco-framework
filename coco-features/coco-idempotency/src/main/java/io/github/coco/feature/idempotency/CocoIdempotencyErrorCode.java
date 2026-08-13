package io.github.coco.feature.idempotency;

import io.github.coco.exception.CocoBusinessCode;

/**
 * Error codes emitted by the idempotency Servlet feature.
 *
 * @author patton174
 * @since 1.0.0
 */
public enum CocoIdempotencyErrorCode implements CocoBusinessCode {

    /** Missing caller scope. */
    SCOPE_REQUIRED(401, "COCO_IDEMPOTENCY_SCOPE_REQUIRED"),

    /** Missing idempotency key. */
    KEY_REQUIRED(400, "COCO_IDEMPOTENCY_KEY_REQUIRED"),

    /** Invalid idempotency key. */
    KEY_INVALID(400, "COCO_IDEMPOTENCY_KEY_INVALID"),

    /** Matching request is still in progress. */
    IN_PROGRESS(409, "COCO_IDEMPOTENCY_IN_PROGRESS"),

    /** Key is bound to another request. */
    PAYLOAD_MISMATCH(422, "COCO_IDEMPOTENCY_PAYLOAD_MISMATCH"),

    /** Request body exceeds the configured limit. */
    REQUEST_TOO_LARGE(413, "COCO_IDEMPOTENCY_REQUEST_TOO_LARGE"),

    /** Response body exceeds the configured limit. */
    RESPONSE_TOO_LARGE(500, "COCO_IDEMPOTENCY_RESPONSE_TOO_LARGE"),

    /** Response headers exceed a configured limit. */
    RESPONSE_HEADERS_TOO_LARGE(500, "COCO_IDEMPOTENCY_RESPONSE_HEADERS_TOO_LARGE"),

    /** Response headers are unsafe to cache or replay. */
    UNSAFE_RESPONSE_HEADER(500, "COCO_IDEMPOTENCY_UNSAFE_RESPONSE_HEADER"),

    /** Servlet lifecycle cannot be cached safely. */
    UNSUPPORTED_IO(500, "COCO_IDEMPOTENCY_UNSUPPORTED_IO"),

    /** The in-memory reference store is full. */
    CAPACITY_EXCEEDED(503, "COCO_IDEMPOTENCY_CAPACITY_EXCEEDED"),

    /** Store operation cannot be completed safely. */
    STORE_UNAVAILABLE(503, "COCO_IDEMPOTENCY_STORE_UNAVAILABLE");

    private final int code;

    private final String messageCode;

    CocoIdempotencyErrorCode(int code, String messageCode) {
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
