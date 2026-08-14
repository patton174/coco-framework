package io.github.coco.feature.ratelimit;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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

    private final Set<String> trustedProxyRemoteAddresses;

    /**
     * 创建不信任任何转发头的默认解析器。
     */
    public DefaultCocoRateLimitKeyResolver() {
        this(null);
    }

    /**
     * 创建具有显式可信反向代理边界的解析器。
     * @param trustedProxy 可信代理配置
     */
    public DefaultCocoRateLimitKeyResolver(CocoRateLimitProperties.TrustedProxy trustedProxy) {
        CocoRateLimitProperties.TrustedProxy snapshot = CocoRateLimitProperties.TrustedProxy.copyOf(trustedProxy);
        this.trustedProxyRemoteAddresses = Set.copyOf(new HashSet<>(snapshot.getRemoteAddresses()));
    }

    @Override
    public CocoRateLimitKey resolve(HttpServletRequest request, CocoRateLimitRoute route) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        CocoRateLimitRoute checkedRoute = Objects.requireNonNull(route, "route must not be null");
        String remoteAddress = checkedRequest.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            throw new IllegalArgumentException("Servlet request did not provide a remote address");
        }
        String normalizedRemoteAddress = remoteAddress.trim();
        return new CocoRateLimitKey(checkedRoute.getId(), resolveSubject(checkedRequest, normalizedRemoteAddress));
    }

    private String resolveSubject(HttpServletRequest request, String remoteAddress) {
        if (!this.trustedProxyRemoteAddresses.contains(remoteAddress)) {
            return remoteAddress;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }
        String[] chain = forwardedFor.split(",");
        for (int index = chain.length - 1; index >= 0; index--) {
            String address = chain[index].trim();
            if (!address.isEmpty() && !this.trustedProxyRemoteAddresses.contains(address)) {
                return address;
            }
        }
        return remoteAddress;
    }
}
