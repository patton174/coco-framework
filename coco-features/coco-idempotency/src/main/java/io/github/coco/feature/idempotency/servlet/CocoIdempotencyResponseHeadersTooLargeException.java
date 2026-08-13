package io.github.coco.feature.idempotency.servlet;

final class CocoIdempotencyResponseHeadersTooLargeException extends IllegalStateException {

    CocoIdempotencyResponseHeadersTooLargeException(String message) {
        super(message);
    }
}
