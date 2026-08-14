package io.github.coco.feature.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

import io.github.coco.i18n.CocoMessageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * 限流拒绝响应写出器。
 * <p>
 * 使用基础国际化契约写出稳定的限流错误体。模块不依赖具体 Coco Web 实现；应用可替换该 Bean 以对接自身的
 * 响应体约定。
 * </p>
 */
public final class CocoRateLimitResponseWriter {

    private final CocoMessageService messageService;

    private final ObjectMapper objectMapper;

    /**
     * 创建限流拒绝响应写出器。
     * @param messageService 国际化消息服务
     * @param objectMapper JSON 序列化器
     */
    public CocoRateLimitResponseWriter(CocoMessageService messageService, ObjectMapper objectMapper) {
        this.messageService = Objects.requireNonNull(messageService, "messageService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 写出 HTTP 429 的 Coco 统一异常响应。
     * @param exception 限流异常
     * @param request 当前请求
     * @param response 当前响应
     * @throws IOException 响应写出失败时抛出
     */
    public void write(CocoRateLimitErrorCode errorCode, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(response, "response must not be null");
        if (response.isCommitted()) {
            throw new IllegalStateException("Cannot write a rate-limit response after the response has been committed");
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String message = this.messageService.getMessage(errorCode.messageCode(), request.getLocale());
        this.objectMapper.writeValue(response.getOutputStream(), Map.of("code", errorCode.code(), "message", message));
    }
}
