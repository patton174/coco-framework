package io.github.coco.feature.web.exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.i18n.CocoLocaleResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;

/** 使用 Coco Web 统一异常语义写出非 Controller 错误响应。 */
public final class CocoWebErrorResponseWriter {
    private final CocoWebExceptionHandler exceptionHandler;
    private final ObjectMapper objectMapper;
    private final CocoLocaleResolver localeResolver;

    /** 创建统一错误响应写出器。 */
    public CocoWebErrorResponseWriter(CocoWebExceptionHandler exceptionHandler, ObjectMapper objectMapper) {
        this(exceptionHandler, objectMapper, () -> null);
    }

    /** 创建使用 Coco 语言解析器的统一错误响应写出器。 */
    public CocoWebErrorResponseWriter(CocoWebExceptionHandler exceptionHandler, ObjectMapper objectMapper,
            CocoLocaleResolver localeResolver) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler must not be null");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.localeResolver = Objects.requireNonNull(localeResolver, "localeResolver must not be null");
    }

    /**
     * <p>按既有 Web locale、metadata、trace 和 cookie 语义写出错误响应。</p>
     * @param status HTTP 状态
     * @param code 业务响应码
     * @param messageCode 国际化消息码
     * @param request 当前请求
     * @param response 当前响应
     * @throws IOException 写出失败时抛出
     */
    public void write(HttpStatusCode status, int code, String messageCode, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(messageCode, "messageCode must not be null");
        if (response.isCommitted()) { throw new IllegalStateException("Cannot write an error response after commit"); }
        ResponseEntity<Object> entity = this.exceptionHandler.handleError(status, code, messageCode,
                new ServletWebRequest(request, response), this.localeResolver.resolveLocale());
        response.setStatus(entity.getStatusCode().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        entity.getHeaders().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
        this.objectMapper.writeValue(response.getOutputStream(), entity.getBody());
    }
}
