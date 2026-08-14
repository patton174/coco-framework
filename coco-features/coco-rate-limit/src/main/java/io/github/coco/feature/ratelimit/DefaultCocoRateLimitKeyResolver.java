package io.github.coco.feature.ratelimit;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认限流键解析器。
 * <p>
 * 默认键只使用 Servlet 容器报告的远端地址。它绝不读取 {@code Forwarded}、{@code X-Forwarded-For} 或
 * 任何客户端可伪造的请求头；部署在可信代理之后的应用必须显式替换该 SPI，并在替换实现中落实自身的代理
 * 信任边界。
 * </p>
 */
public final class DefaultCocoRateLimitKeyResolver implements CocoRateLimitKeyResolver {

    @Override
    public CocoRateLimitKey resolve(HttpServletRequest request, CocoRateLimitRoute route) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        CocoRateLimitRoute checkedRoute = Objects.requireNonNull(route, "route must not be null");
        String remoteAddress = checkedRequest.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            throw new IllegalArgumentException("Servlet request did not provide a remote address");
        }
        return new CocoRateLimitKey(checkedRoute.getId(), remoteAddress.trim());
    }
}
