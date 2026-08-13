package io.github.coco.feature.idempotency.servlet;

final class CocoIdempotencyUnsafeResponseHeaderException extends IllegalStateException {

    CocoIdempotencyUnsafeResponseHeaderException(String message) {
        super(message);
    }
}
