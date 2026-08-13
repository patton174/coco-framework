package io.github.coco.feature.idempotency.servlet;

final class CocoIdempotencyUnsupportedIoException extends IllegalStateException {

    CocoIdempotencyUnsupportedIoException(String message) {
        super(message);
    }
}
