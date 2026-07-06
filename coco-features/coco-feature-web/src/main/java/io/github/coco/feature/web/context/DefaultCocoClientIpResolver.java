package io.github.coco.feature.web.context;

import java.util.Arrays;
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
 *   <li>模块：{@code coco-feature-web}</li>
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
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        for (String headerName : this.properties.getClientIpHeaderNames()) {
            String headerValue = checkedRequest.getHeader(headerName);
            String clientIp = "Forwarded".equalsIgnoreCase(headerName)
                    ? firstForwardedForValue(headerValue)
                    : firstHeaderValue(headerValue);
            if (isUsableClientIp(clientIp)) {
                return clientIp;
            }
        }
        return normalizeString(checkedRequest.getRemoteAddr());
    }

    private static String firstForwardedForValue(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        return Arrays.stream(headerValue.split(","))
                .map(DefaultCocoClientIpResolver::forwardedForValue)
                .filter(DefaultCocoClientIpResolver::isUsableClientIp)
                .findFirst()
                .orElse(null);
    }

    private static String forwardedForValue(String segment) {
        if (segment == null || segment.isBlank()) {
            return null;
        }
        return Arrays.stream(segment.split(";"))
                .map(String::trim)
                .filter(part -> part.regionMatches(true, 0, "for=", 0, 4))
                .map(part -> cleanClientIpToken(part.substring(4)))
                .findFirst()
                .orElse(null);
    }

    private static String firstHeaderValue(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        return Arrays.stream(headerValue.split(","))
                .map(DefaultCocoClientIpResolver::cleanClientIpToken)
                .filter(DefaultCocoClientIpResolver::isUsableClientIp)
                .findFirst()
                .orElse(null);
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
            return normalized.substring(1, normalized.indexOf(']'));
        }
        int portIndex = normalized.lastIndexOf(':');
        if (portIndex > 0 && normalized.indexOf(':') == portIndex) {
            return normalized.substring(0, portIndex);
        }
        return normalized;
    }

    private static boolean isUsableClientIp(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value.trim());
    }

    private static String normalizeString(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
