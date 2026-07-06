package io.github.coco.feature.web.context;

import java.net.InetAddress;
import java.net.UnknownHostException;
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
                String clientIp = "Forwarded".equalsIgnoreCase(headerName)
                        ? firstForwardedForValue(headerValue)
                        : firstHeaderValue(headerValue);
                if (clientIp != null) {
                    return CocoClientIpResolution.forwardedHeader(clientIp, headerName, headerValue, remoteAddr);
                }
            }
        }
        return remoteAddr == null ? CocoClientIpResolution.unresolved() : CocoClientIpResolution.remoteAddress(remoteAddr);
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null || this.properties.getTrustedProxyCidrs().isEmpty()) {
            return false;
        }
        byte[] remoteAddress = parseIpAddress(remoteAddr);
        if (remoteAddress == null) {
            return false;
        }
        return this.properties.getTrustedProxyCidrs().stream()
                .anyMatch(trustedProxy -> matchesTrustedProxy(remoteAddress, trustedProxy));
    }

    private static boolean matchesTrustedProxy(byte[] remoteAddress, String trustedProxy) {
        String normalizedProxy = normalizeString(trustedProxy);
        if (normalizedProxy == null) {
            return false;
        }
        int separatorIndex = normalizedProxy.indexOf('/');
        String addressPart = separatorIndex < 0 ? normalizedProxy : normalizedProxy.substring(0, separatorIndex);
        byte[] trustedAddress = parseIpAddress(addressPart);
        if (trustedAddress == null || trustedAddress.length != remoteAddress.length) {
            return false;
        }
        int prefixLength = separatorIndex < 0
                ? remoteAddress.length * Byte.SIZE
                : parsePrefixLength(normalizedProxy.substring(separatorIndex + 1), remoteAddress.length * Byte.SIZE);
        return prefixLength >= 0 && matchesPrefix(remoteAddress, trustedAddress, prefixLength);
    }

    private static boolean matchesPrefix(byte[] remoteAddress, byte[] trustedAddress, int prefixLength) {
        int fullBytes = prefixLength / Byte.SIZE;
        int remainingBits = prefixLength % Byte.SIZE;
        for (int i = 0; i < fullBytes; i++) {
            if (remoteAddress[i] != trustedAddress[i]) {
                return false;
            }
        }
        if (remainingBits == 0) {
            return true;
        }
        int mask = 0xFF << (Byte.SIZE - remainingBits);
        return (remoteAddress[fullBytes] & mask) == (trustedAddress[fullBytes] & mask);
    }

    private static int parsePrefixLength(String value, int maxPrefixLength) {
        try {
            int prefixLength = Integer.parseInt(value);
            return prefixLength >= 0 && prefixLength <= maxPrefixLength ? prefixLength : -1;
        }
        catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String firstForwardedForValue(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        return Arrays.stream(headerValue.split(","))
                .map(DefaultCocoClientIpResolver::forwardedForValue)
                .map(DefaultCocoClientIpResolver::normalizeClientIp)
                .filter(Objects::nonNull)
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
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String firstHeaderValue(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        return Arrays.stream(headerValue.split(","))
                .map(DefaultCocoClientIpResolver::cleanClientIpToken)
                .map(DefaultCocoClientIpResolver::normalizeClientIp)
                .filter(Objects::nonNull)
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
        return parseIpAddress(normalized) == null ? null : normalized;
    }

    private static byte[] parseIpAddress(String value) {
        String normalized = normalizeString(value);
        if (normalized == null) {
            return null;
        }
        if (isIpv4Literal(normalized)) {
            return parseIpv4Address(normalized);
        }
        if (!normalized.contains(":") || !isIpv6LiteralCandidate(normalized)) {
            return null;
        }
        try {
            byte[] address = InetAddress.getByName(normalized).getAddress();
            return address.length == 16 ? address : null;
        }
        catch (UnknownHostException ex) {
            return null;
        }
    }

    private static boolean isIpv4Literal(String value) {
        return value.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    private static byte[] parseIpv4Address(String value) {
        String[] parts = value.split("\\.");
        byte[] address = new byte[4];
        for (int i = 0; i < parts.length; i++) {
            int part = Integer.parseInt(parts[i]);
            if (part < 0 || part > 255) {
                return null;
            }
            address[i] = (byte) part;
        }
        return address;
    }

    private static boolean isIpv6LiteralCandidate(String value) {
        return value.chars().allMatch(character ->
                Character.digit(character, 16) >= 0 || character == ':' || character == '.');
    }

    private static String normalizeString(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
