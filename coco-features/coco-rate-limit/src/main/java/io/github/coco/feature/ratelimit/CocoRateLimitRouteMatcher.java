package io.github.coco.feature.ratelimit;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco 限流路由匹配器。
 */
@FunctionalInterface
public interface CocoRateLimitRouteMatcher {

    /**
     * 解析当前请求命中的第一条限流路由。
     * @param request 当前 Servlet 请求
     * @return 命中的路由；未命中时为空
     */
    Optional<CocoRateLimitRoute> resolve(HttpServletRequest request);

    /**
     * 按标识返回已配置路由，供显式注解意图后备使用。
     * @param routeId 路由标识
     * @return 已配置路由；不存在时为空
     */
    default Optional<CocoRateLimitRoute> resolve(String routeId) {
        return Optional.empty();
    }
}
