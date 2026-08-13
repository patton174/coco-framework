package io.github.coco.feature.idempotency.servlet;

final class CocoIdempotencyStoreCompletionException extends IllegalStateException {

    CocoIdempotencyStoreCompletionException() {
        super("Coco idempotency lease was no longer active during completion");
    }
}
