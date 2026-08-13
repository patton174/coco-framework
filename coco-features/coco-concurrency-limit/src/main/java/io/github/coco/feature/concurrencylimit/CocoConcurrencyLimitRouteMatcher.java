package io.github.coco.feature.concurrencylimit;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco 并发限制显式路由匹配器。
 */
public interface CocoConcurrencyLimitRouteMatcher {

    /**
     * 按配置顺序解析首个匹配当前 Servlet 请求的路由。
     * @param request 当前 Servlet 请求
     * @return 匹配路由
     */
    Optional<CocoConcurrencyLimitRoute> resolve(HttpServletRequest request);

    /**
     * 按唯一标识解析注解引用的路由。
     * @param routeId 路由标识
     * @return 匹配路由
     */
    Optional<CocoConcurrencyLimitRoute> resolve(String routeId);
}
