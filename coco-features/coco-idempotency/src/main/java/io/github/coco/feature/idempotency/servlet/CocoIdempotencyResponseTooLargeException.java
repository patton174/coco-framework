package io.github.coco.feature.idempotency.servlet;

import java.io.IOException;

final class CocoIdempotencyResponseTooLargeException extends IOException {

    CocoIdempotencyResponseTooLargeException() {
        super("Coco idempotency response body exceeded the configured limit");
    }
}
