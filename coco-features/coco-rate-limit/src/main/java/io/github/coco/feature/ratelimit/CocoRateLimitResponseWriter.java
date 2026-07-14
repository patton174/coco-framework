package io.github.coco.feature.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.exception.CocoException;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * 限流拒绝响应写出器。
 * <p>
 * 复用 Coco Web 全局异常处理器生成业务码和国际化消息，仅将 HTTP 状态覆盖为 429，避免过滤器错误响应与
 * Controller 错误响应格式分叉。
 * </p>
 */
public final class CocoRateLimitResponseWriter {

    private final CocoWebExceptionHandler exceptionHandler;

    private final ObjectMapper objectMapper;

    /**
     * 创建限流拒绝响应写出器。
     * @param exceptionHandler Coco Web 全局异常处理器
     * @param objectMapper JSON 序列化器
     */
    public CocoRateLimitResponseWriter(CocoWebExceptionHandler exceptionHandler, ObjectMapper objectMapper) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 写出 HTTP 429 的 Coco 统一异常响应。
     * @param exception 限流异常
     * @param request 当前请求
     * @param response 当前响应
     * @throws IOException 响应写出失败时抛出
     */
    public void write(CocoException exception, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Objects.requireNonNull(exception, "exception must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(response, "response must not be null");
        if (response.isCommitted()) {
            throw exception;
        }
        ResponseEntity<Object> entity = this.exceptionHandler.handleCocoException(exception,
                new ServletWebRequest(request, response), request.getLocale());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        this.objectMapper.writeValue(response.getOutputStream(), entity.getBody());
    }
}
