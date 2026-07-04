package io.github.coco.feature.web.trace;

import java.io.IOException;
import java.util.Objects;

import io.github.coco.common.trace.CocoTraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Coco Web Trace 过滤器。
 * <p>
 * 从请求头读取 TraceId；请求头缺失时自动生成 TraceId；将 TraceId 写入 {@link CocoTraceContext} 和响应头，并在请求结束后清理上下文。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoTraceFilter extends OncePerRequestFilter {

    private final String headerName;

    /**
     * <p>
     * 创建 Coco Web Trace 过滤器。
     * </p>
     * @param properties Trace 配置属性
     */
    public CocoTraceFilter(CocoTraceProperties properties) {
        CocoTraceProperties checkedProperties = Objects.requireNonNull(properties, "properties must not be null");
        this.headerName = checkedProperties.getHeaderName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        CocoTraceContext.setTraceId(traceId);
        response.setHeader(this.headerName, traceId);
        try {
            filterChain.doFilter(request, response);
        }
        finally {
            CocoTraceContext.clear();
        }
    }

    /**
     * <p>
     * 解析当前请求的 TraceId。
     * </p>
     * <p>
     * 优先读取配置的 HTTP 头；当请求头缺失或为空白时，创建新的 TraceId。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 可写入上下文和响应头的 TraceId
     */
    private String resolveTraceId(HttpServletRequest request) {
        String requestTraceId = request.getHeader(this.headerName);
        if (requestTraceId == null || requestTraceId.isBlank()) {
            return CocoTraceContext.getOrCreateTraceId();
        }
        return requestTraceId.trim();
    }
}
