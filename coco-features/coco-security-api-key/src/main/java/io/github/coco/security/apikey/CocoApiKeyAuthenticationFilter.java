package io.github.coco.security.apikey;

import java.io.IOException;
import java.util.Objects;

import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * API Key 认证失败统一响应过滤器。
 * <p>
 * 该过滤器只转换模块内部认证异常，不绑定安全上下文；上下文生命周期完全复用 Coco 核心安全过滤器。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoApiKeyAuthenticationFilter implements Filter {

    private final CocoFilterExceptionResponseWriter responseWriter;

    /**
     * 创建 API Key 认证失败过滤器。
     * @param responseWriter Coco 统一过滤器异常响应写出器
     */
    public CocoApiKeyAuthenticationFilter(CocoFilterExceptionResponseWriter responseWriter) {
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        }
        catch (CocoApiKeyAuthenticationException exception) {
            if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
                this.responseWriter.write(exception, httpRequest, httpResponse);
                return;
            }
            throw exception;
        }
    }
}
