package io.github.coco.feature.ratelimit;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 基于 Coco Web 请求匹配器的默认限流路由匹配器。
 */
public final class DefaultCocoRateLimitRouteMatcher implements CocoRateLimitRouteMatcher {

    private final List<CocoRateLimitRoute> routes;

    private final CocoWebRequestMatcher requestMatcher;

    /**
     * 创建默认限流路由匹配器。
     * @param properties 限流配置
     * @param requestMatcher Coco Web 请求匹配器
     */
    public DefaultCocoRateLimitRouteMatcher(CocoRateLimitProperties properties,
            CocoWebRequestMatcher requestMatcher) {
        CocoRateLimitProperties checkedProperties = properties == null ? new CocoRateLimitProperties() : properties;
        this.routes = List.copyOf(checkedProperties.getRoutes());
        this.requestMatcher = Objects.requireNonNull(requestMatcher, "requestMatcher must not be null");
        this.routes.forEach(DefaultCocoRateLimitRouteMatcher::validate);
    }

    @Override
    public Optional<CocoRateLimitRoute> resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        return this.routes.stream()
                .filter(route -> this.requestMatcher.matches(request, List.of(route.getMatcher())))
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
}
