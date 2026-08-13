package io.github.coco.security.jwt;

import java.io.IOException;
import java.util.Objects;

import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 将 Bearer 访问拒绝写为 Coco 统一响应的处理器适配器。

 * @author patton174
 * @since 1.0.0
 */
public final class CocoJwtAccessDeniedHandler implements AccessDeniedHandler {

    private final CocoFilterExceptionResponseWriter responseWriter;

    /**
     * 创建 Bearer 访问拒绝处理器。
     * @param responseWriter Coco 过滤器异常响应写出器
     */
    public CocoJwtAccessDeniedHandler(CocoFilterExceptionResponseWriter responseWriter) {
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setHeader("WWW-Authenticate", "Bearer");
        this.responseWriter.write(CocoSecurityJwtErrorCode.ACCESS_DENIED
                .forbidden(), request, response);
    }
}
