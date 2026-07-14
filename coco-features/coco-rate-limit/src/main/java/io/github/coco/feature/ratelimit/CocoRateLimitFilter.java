package io.github.coco.feature.ratelimit;

import java.io.IOException;
import java.util.Objects;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Coco Servlet 限流过滤器。
 * <p>
 * 过滤器在业务 Controller 和事务边界前执行，使用 Coco Web 请求快照获取受可信代理策略保护的客户端信息；
 * 本身不解析用户、角色或租户模型。
 * </p>
 */
public final class CocoRateLimitFilter extends OncePerRequestFilter {

    static final String APPLIED_ROUTE_ATTRIBUTE = CocoRateLimitFilter.class.getName() + ".appliedRoute";

    private final CocoRateLimitRouteMatcher routeMatcher;

    /**
     * 创建 Coco 限流过滤器。
     * @param routeMatcher 限流路由匹配器
     * @param requestHandler 限流请求执行器
     */
    public CocoRateLimitFilter(CocoRateLimitRouteMatcher routeMatcher, CocoRateLimitRequestHandler requestHandler) {
        this.routeMatcher = Objects.requireNonNull(routeMatcher, "routeMatcher must not be null");
        this.requestHandler = Objects.requireNonNull(requestHandler, "requestHandler must not be null");
    }

    private final CocoRateLimitRequestHandler requestHandler;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        CocoRateLimitRoute route = this.routeMatcher.resolve(request).orElse(null);
        if (route == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (this.requestHandler.handle(route, request, response)) {
            request.setAttribute(APPLIED_ROUTE_ATTRIBUTE, route.getId());
            filterChain.doFilter(request, response);
        }
    }
}
