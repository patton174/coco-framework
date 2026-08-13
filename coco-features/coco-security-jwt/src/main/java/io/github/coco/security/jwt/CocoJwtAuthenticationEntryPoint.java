package io.github.coco.security.jwt;

import java.io.IOException;
import java.util.Objects;

import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 将 Bearer 认证失败写为 Coco 统一响应的入口适配器。

 * @author patton174
 * @since 1.0.0
 */
public final class CocoJwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final CocoFilterExceptionResponseWriter responseWriter;

    /**
     * 创建 Bearer 认证失败入口适配器。
     * @param responseWriter Coco 过滤器异常响应写出器
     */
    public CocoJwtAuthenticationEntryPoint(CocoFilterExceptionResponseWriter responseWriter) {
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException, ServletException {
        response.setHeader("WWW-Authenticate", "Bearer");
        this.responseWriter.write(CocoSecurityJwtErrorCode.AUTHENTICATION_FAILED
                .unauthorized(), request, response);
    }
}
