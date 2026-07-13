package io.github.coco.feature.web.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认 Coco 客户端 IP 解析器。
 * <p>
 * 按配置顺序读取代理请求头，支持标准 {@code Forwarded} 和常见 {@code X-Forwarded-For} 类请求头。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoClientIpResolver implements CocoClientIpResolver {

    private final CocoWebContextProperties properties;

    /**
     * <p>
     * 创建默认 Coco 客户端 IP 解析器。
     * </p>
     * @param properties Web 请求上下文配置属性
     */
    public DefaultCocoClientIpResolver(CocoWebContextProperties properties) {
        this.properties = properties == null ? new CocoWebContextProperties() : properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolve(HttpServletRequest request) {
        return resolveResolution(request).clientIp();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoClientIpResolution resolveResolution(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        String remoteAddr = normalizeClientIp(checkedRequest.getRemoteAddr());
        if (isTrustedProxy(remoteAddr)) {
            for (String headerName : this.properties.getClientIpHeaderNames()) {
                String headerValue = checkedRequest.getHeader(headerName);
                ResolvedClientIp clientIp = clientIpFromTrustedProxyHeader(headerName, headerValue);
                if (clientIp != null) {
                    return CocoClientIpResolution.forwardedHeader(clientIp.clientIp(), headerName, headerValue,
                            remoteAddr, clientIp.sourceChain(), clientIp.resolvedChainIndex());
                }
            }
        }
        return remoteAddr == null ? CocoClientIpResolution.unresolved() : CocoClientIpResolution.remoteAddress(remoteAddr);
    }

    private ResolvedClientIp clientIpFromTrustedProxyHeader(String headerName, String headerValue) {
        List<String> candidates = "Forwarded".equalsIgnoreCase(headerName)
                ? forwardedForValues(headerValue)
                : headerValues(headerValue);
        if (candidates.isEmpty()) {
            return null;
        }
        for (int index = candidates.size() - 1; index >= 0; index--) {
            String candidate = candidates.get(index);
            if (!isTrustedProxy(candidate)) {
                return new ResolvedClientIp(candidate, candidates, index);
            }
        }
        return new ResolvedClientIp(candidates.get(0), candidates, 0);
    }

    private boolean isTrustedProxy(String remoteAddr) {
        return CocoIpAddressSupport.isTrustedProxy(remoteAddr, this.properties.getTrustedProxyCidrs());
    }

    private static List<String> forwardedForValues(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Arrays.stream(headerValue.split(","))
                .map(DefaultCocoClientIpResolver::forwardedForValue)
                .map(DefaultCocoClientIpResolver::normalizeClientIp)
                .filter(Objects::nonNull)
                .forEach(values::add);
        return values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private static String forwardedForValue(String segment) {
        if (segment == null || segment.isBlank()) {
            return null;
        }
        return Arrays.stream(segment.split(";"))
                .map(String::trim)
                .filter(part -> part.regionMatches(true, 0, "for=", 0, 4))
                .map(part -> cleanClientIpToken(part.substring(4)))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static List<String> headerValues(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Arrays.stream(headerValue.split(","))
                .map(DefaultCocoClientIpResolver::cleanClientIpToken)
                .map(DefaultCocoClientIpResolver::normalizeClientIp)
                .filter(Objects::nonNull)
                .forEach(values::add);
        return values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private static String cleanClientIpToken(String value) {
        String normalized = normalizeString(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.startsWith("[") && normalized.contains("]")) {
            int closingIndex = normalized.indexOf(']');
            String suffix = normalized.substring(closingIndex + 1).trim();
            if (suffix.isEmpty()) {
                return normalized.substring(1, closingIndex);
            }
            if (suffix.startsWith(":") && isValidPort(suffix.substring(1))) {
                return normalized.substring(1, closingIndex);
            }
            return null;
        }
        int portIndex = normalized.lastIndexOf(':');
        if (portIndex > 0 && normalized.indexOf(':') == portIndex) {
            String port = normalized.substring(portIndex + 1);
            return isValidPort(port) ? normalized.substring(0, portIndex) : null;
        }
        return normalized;
    }

    private static boolean isValidPort(String value) {
        if (value == null || value.isBlank() || value.length() > 5) {
            return false;
        }
        try {
            int port = Integer.parseInt(value);
            return port >= 0 && port <= 65535;
        }
        catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String normalizeClientIp(String value) {
        String normalized = normalizeString(value);
        if (normalized == null || "unknown".equalsIgnoreCase(normalized) || normalized.startsWith("_")) {
            return null;
        }
        return CocoIpAddressSupport.parseIpAddress(normalized) == null ? null : normalized;
    }

    private static String normalizeString(String value) {
        return CocoIpAddressSupport.normalizeString(value);
    }

    private record ResolvedClientIp(String clientIp, List<String> sourceChain, int resolvedChainIndex) {
    }
}
