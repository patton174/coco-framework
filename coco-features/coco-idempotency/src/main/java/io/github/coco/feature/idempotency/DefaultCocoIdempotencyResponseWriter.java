package io.github.coco.feature.idempotency;

import java.io.IOException;
import java.util.Objects;

import io.github.coco.feature.web.exception.CocoWebErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

/** 使用 Coco Web 统一响应路径的默认幂等拒绝响应写出器。 */
public final class DefaultCocoIdempotencyResponseWriter implements CocoIdempotencyResponseWriter {
    private final CocoWebErrorResponseWriter responseWriter;

    /** 创建默认写出器。 */
    public DefaultCocoIdempotencyResponseWriter(CocoWebErrorResponseWriter responseWriter) {
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
    }

    @Override
    public void write(CocoIdempotencyErrorCode errorCode, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        this.responseWriter.write(status(errorCode), errorCode.code(), errorCode.messageCode(), request, response);
    }

    private static HttpStatus status(CocoIdempotencyErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_KEY -> HttpStatus.BAD_REQUEST;
            case DUPLICATE -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
