package io.github.coco.feature.ratelimit;

import java.util.Objects;

import io.github.coco.feature.web.context.CocoClientIpResolution;
import io.github.coco.feature.web.context.CocoClientIpSource;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;

/**
 * 默认限流键解析器。
 * <p>
 * 默认键只使用 Coco Web 已解析且可证明可信的客户端 IP：直接 Servlet 远端地址，或由可信代理解析的
 * 转发请求头。它不直接读取 {@code Forwarded} 或 {@code X-Forwarded-For}，因此转发头信任边界始终由
 * Coco Web 的可信代理配置控制。
 * </p>
 */
public final class DefaultCocoRateLimitKeyResolver implements CocoRateLimitKeyResolver {

    @Override
    public CocoRateLimitKey resolve(CocoWebRequestSnapshot request, CocoRateLimitRoute route) {
        CocoWebRequestSnapshot checkedRequest = Objects.requireNonNull(request, "request must not be null");
        CocoRateLimitRoute checkedRoute = Objects.requireNonNull(route, "route must not be null");
        CocoClientIpResolution resolution = checkedRequest.clientIpResolution();
        if (!trusted(resolution)) {
            throw new IllegalArgumentException("Coco Web request context did not resolve a trusted client IP");
        }
        return new CocoRateLimitKey(checkedRoute.getId(), resolution.clientIp());
    }

    private static boolean trusted(CocoClientIpResolution resolution) {
        if (resolution == null || resolution.clientIp() == null || resolution.clientIp().isBlank()) {
            return false;
        }
        boolean directRemoteAddress = resolution.source() == CocoClientIpSource.REMOTE_ADDRESS
                && resolution.clientIp().equals(resolution.remoteAddress());
        boolean trustedForwardedHeader = resolution.source() == CocoClientIpSource.FORWARDED_HEADER
                && resolution.trustedProxy();
        return directRemoteAddress || trustedForwardedHeader;
    }
}
