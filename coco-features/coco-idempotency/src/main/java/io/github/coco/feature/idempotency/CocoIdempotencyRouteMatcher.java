package io.github.coco.feature.idempotency;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;

/**
 * 显式幂等路由匹配器。
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoIdempotencyRouteMatcher {

    private final List<CocoIdempotencyProperties.Route> routes;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 创建路由匹配器。
     * @param routes 已校验路由规则
     */
    public CocoIdempotencyRouteMatcher(List<CocoIdempotencyProperties.Route> routes) {
        this.routes = List.copyOf(Objects.requireNonNull(routes, "routes must not be null"));
    }

    /**
     * 判断请求是否命中显式幂等路由。
     * @param request Servlet 请求
     * @return 命中时返回 {@code true}
     */
    public boolean matches(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = servletPath(request);
        return this.routes.stream().anyMatch(route -> route.getMethods().contains(method)
                && route.getPathPatterns().stream().anyMatch(pattern -> this.pathMatcher.match(pattern, path)));
    }

    private static String servletPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            String path = uri.substring(contextPath.length());
            return path.isEmpty() ? "/" : path;
        }
        return uri == null || uri.isEmpty() ? "/" : uri;
    }
}
