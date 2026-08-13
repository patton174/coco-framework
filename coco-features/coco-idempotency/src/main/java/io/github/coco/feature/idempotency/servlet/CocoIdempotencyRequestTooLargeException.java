package io.github.coco.feature.idempotency.servlet;

import java.io.IOException;

final class CocoIdempotencyRequestTooLargeException extends IOException {

    CocoIdempotencyRequestTooLargeException() {
        super("Coco idempotency request body exceeded the configured limit");
    }
}
