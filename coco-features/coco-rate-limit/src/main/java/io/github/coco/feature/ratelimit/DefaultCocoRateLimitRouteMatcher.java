package io.github.coco.feature.ratelimit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.AntPathMatcher;

/**
 * 默认限流路由匹配器。
 */
public final class DefaultCocoRateLimitRouteMatcher implements CocoRateLimitRouteMatcher {

    private final List<CocoRateLimitRoute> routes;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 创建默认限流路由匹配器。
     * @param properties 限流配置
     * @param requestMatcher Coco Web 请求匹配器
     */
    public DefaultCocoRateLimitRouteMatcher(CocoRateLimitProperties properties) {
        CocoRateLimitProperties checkedProperties = properties == null ? new CocoRateLimitProperties() : properties;
        this.routes = snapshotRoutes(checkedProperties.getRoutes());
        this.routes.forEach(DefaultCocoRateLimitRouteMatcher::validate);
    }

    @Override
    public Optional<CocoRateLimitRoute> resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return this.routes.stream()
                .filter(route -> matches(route, request))
                .findFirst();
    }

    @Override
    public Optional<CocoRateLimitRoute> resolve(String routeId) {
        if (routeId == null || routeId.isBlank()) {
            return Optional.empty();
        }
        return this.routes.stream().filter(route -> routeId.trim().equals(route.getId())).findFirst();
    }

    private static void validate(CocoRateLimitRoute route) {
        if (route == null || !route.valid()) {
            throw new IllegalStateException("Each coco.rate-limit.routes entry needs id, matcher, positive limit and window-seconds");
        }
    }

    private static List<CocoRateLimitRoute> snapshotRoutes(List<CocoRateLimitRoute> routes) {
        List<CocoRateLimitRoute> snapshot = new ArrayList<>();
        for (CocoRateLimitRoute route : routes) {
            snapshot.add(CocoRateLimitRoute.copyOf(route));
        }
        return List.copyOf(snapshot);
    }

    private boolean matches(CocoRateLimitRoute route, HttpServletRequest request) {
        CocoRateLimitRequestMatchRule matcher = route.getMatcher();
        String method = request.getMethod();
        if (!matcher.getMethods().isEmpty()
                && matcher.getMethods().stream().noneMatch(candidate -> candidate.equalsIgnoreCase(method))) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        String requestPath = path;
        return matcher.getPathPatterns().stream().anyMatch(pattern -> this.pathMatcher.match(pattern, requestPath));
    }
}
