package io.github.coco.feature.ratelimit;

import java.util.Objects;

import io.github.coco.feature.web.context.CocoWebRequestSnapshot;

/**
 * 默认限流键解析器。
 * <p>
 * 默认键只使用 Coco Web 已解析的客户端 IP。它不直接读取 {@code Forwarded} 或
 * {@code X-Forwarded-For}，因此转发头信任边界始终由 Coco Web 的可信代理配置控制。
 * </p>
 */
public final class DefaultCocoRateLimitKeyResolver implements CocoRateLimitKeyResolver {

    @Override
    public CocoRateLimitKey resolve(CocoWebRequestSnapshot request, CocoRateLimitRoute route) {
        CocoWebRequestSnapshot checkedRequest = Objects.requireNonNull(request, "request must not be null");
        CocoRateLimitRoute checkedRoute = Objects.requireNonNull(route, "route must not be null");
        String clientIp = checkedRequest.clientIp();
        if (clientIp == null || clientIp.isBlank()) {
            throw new IllegalArgumentException("Coco Web request context did not resolve a client IP");
        }
        return new CocoRateLimitKey(checkedRoute.getId(), clientIp);
    }
}
