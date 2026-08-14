package io.github.coco.feature.ratelimit;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Coco 限流注解后备拦截器。
 * <p>
 * 当 Filter 已按路径匹配到路由时，本拦截器不重复占用配额。只有路径未命中时，才按方法优先、类次之的
 * {@link CocoRateLimited} 意图寻找已配置路由并执行限流。
 * </p>
 */
public final class CocoRateLimitMvcInterceptor implements HandlerInterceptor {

    private final CocoRateLimitRouteMatcher routeMatcher;

    private final CocoRateLimitRequestHandler requestHandler;

    /**
     * 创建注解后备拦截器。
     * @param routeMatcher 限流路由匹配器
     * @param requestHandler 限流请求执行器
     */
    public CocoRateLimitMvcInterceptor(CocoRateLimitRouteMatcher routeMatcher,
            CocoRateLimitRequestHandler requestHandler) {
        this.routeMatcher = Objects.requireNonNull(routeMatcher, "routeMatcher must not be null");
        this.requestHandler = Objects.requireNonNull(requestHandler, "requestHandler must not be null");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (request.getAttribute(CocoRateLimitFilter.APPLIED_ROUTE_ATTRIBUTE) != null
                || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        CocoRateLimited intent = resolveIntent(handlerMethod);
        if (intent == null) {
            return true;
        }
        CocoRateLimitRoute route = this.routeMatcher.resolve(intent.route())
                .orElseThrow(() -> new IllegalStateException("@CocoRateLimited references an unknown route"));
        boolean allowed = this.requestHandler.handle(route, request, response);
        if (allowed) {
            request.setAttribute(CocoRateLimitFilter.APPLIED_ROUTE_ATTRIBUTE, route.getId());
        }
        return allowed;
    }

    private static CocoRateLimited resolveIntent(HandlerMethod handlerMethod) {
        CocoRateLimited methodIntent = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(),
                CocoRateLimited.class);
        return methodIntent != null ? methodIntent
                : AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), CocoRateLimited.class);
    }
}
