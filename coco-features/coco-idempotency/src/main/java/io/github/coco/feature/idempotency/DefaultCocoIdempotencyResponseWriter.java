package io.github.coco.feature.idempotency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.feature.web.response.CocoApiResponse;
import io.github.coco.i18n.CocoMessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** 使用 {@link CocoApiResponse} 结构的默认拒绝响应写出器。 */
public final class DefaultCocoIdempotencyResponseWriter implements CocoIdempotencyResponseWriter {
    private final CocoMessageService messages;
    private final ObjectMapper objectMapper;
    /** 创建默认写出器。 */
    public DefaultCocoIdempotencyResponseWriter(CocoMessageService messages, ObjectMapper objectMapper) {
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }
    @Override
    public void write(CocoIdempotencyErrorCode errorCode, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (response.isCommitted()) { throw new IllegalStateException("Cannot write an idempotency response after the response has been committed"); }
        response.setStatus(status(errorCode).value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        this.objectMapper.writeValue(response.getOutputStream(), CocoApiResponse.error(errorCode.code(),
                this.messages.getMessage(errorCode.messageCode(), request.getLocale())));
    }
    private static HttpStatus status(CocoIdempotencyErrorCode errorCode) {
        return switch (errorCode) {
            case INVALID_KEY -> HttpStatus.BAD_REQUEST;
            case DUPLICATE -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
