package io.github.coco.feature.security.web;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco Web 安全上下文桥接过滤器。
 * <p>
 * 在 Servlet 请求进入业务代码前解析安全上下文，并在请求结束后恢复线程原有上下文，避免线程池复用造成上下文泄漏。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-security}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoSecurityWebFilter implements Filter {

    private final CocoWebSecurityContextResolver resolver;

    /**
     * <p>
     * 创建 Web 安全上下文桥接过滤器。
     * </p>
     * @param resolver Web 安全上下文解析器
     */
    public CocoSecurityWebFilter(CocoWebSecurityContextResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
        Optional<CocoSecurityContext> previous = CocoSecurityContextHolder.current();
        try {
            Optional<CocoSecurityContext> securityContext = this.resolver.resolve(httpRequest);
            if (securityContext.isPresent()) {
                CocoSecurityContextHolder.set(securityContext.get());
            }
            else {
                CocoSecurityContextHolder.clear();
            }
            chain.doFilter(request, response);
        }
        finally {
            restore(previous);
        }
    }

    private static void restore(Optional<CocoSecurityContext> previous) {
        if (previous.isPresent()) {
            CocoSecurityContextHolder.set(previous.get());
        }
        else {
            CocoSecurityContextHolder.clear();
        }
    }
}
