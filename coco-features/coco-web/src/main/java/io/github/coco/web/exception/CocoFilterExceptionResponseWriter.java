package io.github.coco.web.exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.exception.CocoException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Coco 过滤器异常响应写出器。
 * <p>
 * 在 Servlet 过滤器阶段复用 Coco 全局异常处理器生成统一响应，避免过滤器链和 Controller 层错误响应格式分叉。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoFilterExceptionResponseWriter {

    private final CocoWebExceptionHandler exceptionHandler;

    private final ObjectMapper objectMapper;

    /**
     * <p>
     * 创建 Coco 过滤器异常响应写出器。
     * </p>
     * @param exceptionHandler Coco Web 全局异常处理器
     * @param objectMapper JSON 序列化器
     */
    public CocoFilterExceptionResponseWriter(CocoWebExceptionHandler exceptionHandler, ObjectMapper objectMapper) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler must not be null");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /**
     * <p>
     * 写出 Coco 统一异常响应。
     * </p>
     * @param exception Coco 异常
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
                new ServletWebRequest(request, response), resolveRequestLocale(request));
        response.setStatus(entity.getStatusCode().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        this.objectMapper.writeValue(response.getOutputStream(), entity.getBody());
    }

    private static Locale resolveRequestLocale(HttpServletRequest request) {
        String acceptLanguage = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        return acceptLanguage == null || acceptLanguage.isBlank() ? null : request.getLocale();
    }
}
