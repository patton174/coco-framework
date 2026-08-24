package io.github.coco.feature.ratelimit;

/** Supported rate-limit store selections. */
public enum CocoRateLimitStoreType {

    /** Uses the current application's process-local reference store. */
    IN_MEMORY,

    /** Uses Spring Data Redis as a shared store. */
    REDIS
}
