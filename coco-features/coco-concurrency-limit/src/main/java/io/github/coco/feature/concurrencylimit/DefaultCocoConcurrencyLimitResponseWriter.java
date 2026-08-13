package io.github.coco.feature.concurrencylimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.exception.CocoException;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * 复用 Coco Web 全局异常处理器的默认并发限制拒绝响应写出器。
 */
public final class DefaultCocoConcurrencyLimitResponseWriter implements CocoConcurrencyLimitResponseWriter {

    private final CocoWebExceptionHandler exceptionHandler;

    private final ObjectMapper objectMapper;

    private final CocoConcurrencyLimitProperties.Response properties;

    /**
     * 创建默认拒绝响应写出器。
     * @param exceptionHandler Coco Web 全局异常处理器
     * @param objectMapper JSON 序列化器
     * @param properties 并发限制配置
     */
    public DefaultCocoConcurrencyLimitResponseWriter(CocoWebExceptionHandler exceptionHandler,
            ObjectMapper objectMapper, CocoConcurrencyLimitProperties properties) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler must not be null");
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.properties = Objects.requireNonNull(properties, "properties must not be null").getResponse();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(CocoConcurrencyLimitErrorCode errorCode, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(response, "response must not be null");
        CocoException exception = new CocoException(errorCode);
        if (response.isCommitted()) {
            throw exception;
        }
        ResponseEntity<Object> entity = this.exceptionHandler.handleCocoException(exception,
                new ServletWebRequest(request, response), resolveRequestLocale(request));
        response.setStatus(this.properties.getStatus());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        this.objectMapper.writeValue(response.getOutputStream(), entity.getBody());
    }

    private static Locale resolveRequestLocale(HttpServletRequest request) {
        String acceptLanguage = request.getHeader(HttpHeaders.ACCEPT_LANGUAGE);
        return acceptLanguage == null || acceptLanguage.isBlank() ? null : request.getLocale();
    }
}
