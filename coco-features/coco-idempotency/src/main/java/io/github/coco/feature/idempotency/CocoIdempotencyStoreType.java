package io.github.coco.feature.idempotency;

/** Supported idempotency store selections. */
public enum CocoIdempotencyStoreType {

    /** Uses the current application's process-local reference store. */
    IN_MEMORY,

    /** Uses Spring Data Redis as a shared store. */
    REDIS
}
