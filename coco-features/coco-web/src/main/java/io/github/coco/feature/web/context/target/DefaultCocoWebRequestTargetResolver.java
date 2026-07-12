package io.github.coco.feature.web.context.target;

import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import io.github.coco.feature.web.context.CocoIpAddressSupport;
import io.github.coco.feature.web.context.CocoWebContextProperties;
import jakarta.servlet.http.HttpServletRequest;

/**
 * <p>
 * 默认 Coco Web 请求目标解析器。
 * </p>
 * <p>
 * 在远端地址命中可信代理时优先读取标准 {@code Forwarded} 与常见 {@code X-Forwarded-*} 请求头，
 * 否则回退到 Servlet 容器提供的目标地址。
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
public final class DefaultCocoWebRequestTargetResolver implements CocoWebRequestTargetResolver {

    private final CocoWebContextProperties properties;

    /**
     * <p>
     * 创建默认 Coco Web 请求目标解析器。
     * </p>
     * @param properties Web 请求上下文配置
     */
    public DefaultCocoWebRequestTargetResolver(CocoWebContextProperties properties) {
        this.properties = properties == null ? new CocoWebContextProperties() : properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebRequestTarget resolve(HttpServletRequest request) {
        return resolveResolution(request).target();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebRequestTargetResolution resolveResolution(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        String remoteAddress = normalizeOptional(checkedRequest.getRemoteAddr());
        String servletScheme = normalizeScheme(checkedRequest.getScheme());
        HostPort servletHostPort = servletHostPort(checkedRequest);
        String servletPath = normalizePath(checkedRequest.getRequestURI());
        CocoWebRequestTarget servletTarget = new CocoWebRequestTarget(servletScheme, servletHostPort.host(),
                servletHostPort.port(), servletPath);
        if (!CocoIpAddressSupport.isTrustedProxy(checkedRequest.getRemoteAddr(), this.properties.getTrustedProxyCidrs())) {
            return CocoWebRequestTargetResolution.servlet(servletTarget, remoteAddress);
        }
        ForwardedTarget forwardedTarget = forwardedTarget(checkedRequest);
        HeaderCandidate<String> forwardedScheme = configuredForwardedSchemeCandidate(checkedRequest);
        HeaderCandidate<HostPort> forwardedHost = configuredForwardedHostCandidate(checkedRequest);
        HeaderCandidate<Integer> forwardedPort = configuredForwardedPortCandidate(checkedRequest);
        HeaderCandidate<String> forwardedPrefix = configuredForwardedPrefixCandidate(checkedRequest);
        String scheme = firstNonBlank(forwardedTarget.scheme(), forwardedScheme.value(), servletScheme);
        HostPort hostPort = firstHostPort(forwardedTarget.hostPort(), forwardedHost.value(), servletHostPort);
        Integer port = firstPort(hostPort.port(), forwardedPort.value(), servletHostPort.port());
        String path = applyPrefix(servletPath, forwardedPrefix.value());
        return CocoWebRequestTargetResolution.forwarded(
                new CocoWebRequestTarget(scheme, hostPort.host(), port, path),
                resolveSource(forwardedTarget.present(), forwardedScheme.present(), forwardedHost.present(),
                        forwardedPort.present(), forwardedPrefix.present()),
                remoteAddress,
                sourceHeaders(forwardedTarget.present(), forwardedScheme, forwardedHost, forwardedPort,
                        forwardedPrefix),
                forwardedPrefix.value());
    }

    private HostPort servletHostPort(HttpServletRequest request) {
        HostPort hostHeader = parseHostPort(existingHeaderValue(request, "Host"));
        String host = firstNonBlank(hostHeader.host(), request.getServerName());
        Integer port = firstPort(hostHeader.port(), normalizePort(request.getServerPort()));
        return new HostPort(host, port);
    }

    private ForwardedTarget forwardedTarget(HttpServletRequest request) {
        Enumeration<String> headerValues = request.getHeaders("Forwarded");
        if (headerValues == null) {
            return ForwardedTarget.empty();
        }
        String scheme = null;
        HostPort hostPort = null;
        while (headerValues.hasMoreElements()) {
            String headerValue = headerValues.nextElement();
            for (String segment : splitCommaSeparatedValues(headerValue)) {
                if (scheme == null) {
                    scheme = forwardedParameter(segment, "proto");
                }
                if (hostPort == null) {
                    hostPort = parseHostPort(forwardedParameter(segment, "host"));
                }
                if (scheme != null && hostPort != null) {
                    return new ForwardedTarget(normalizeScheme(scheme), hostPort);
                }
            }
        }
        return new ForwardedTarget(normalizeScheme(scheme), hostPort == null ? HostPort.empty() : hostPort);
    }

    private HeaderCandidate<String> configuredForwardedSchemeCandidate(HttpServletRequest request) {
        for (String headerName : this.properties.getTarget().getProtoHeaderNames()) {
            for (String value : existingHeaderValues(request, headerName)) {
                for (String token : splitCommaSeparatedValues(value)) {
                    String scheme = normalizeForwardedScheme(headerName, token);
                    if (scheme != null) {
                        return new HeaderCandidate<>(headerName, scheme);
                    }
                }
            }
        }
        return HeaderCandidate.empty();
    }

    private HeaderCandidate<HostPort> configuredForwardedHostCandidate(HttpServletRequest request) {
        for (String headerName : this.properties.getTarget().getHostHeaderNames()) {
            for (String value : existingHeaderValues(request, headerName)) {
                for (String token : splitCommaSeparatedValues(value)) {
                    HostPort hostPort = parseHostPort(token);
                    if (hostPort.present()) {
                        return new HeaderCandidate<>(headerName, hostPort);
                    }
                }
            }
        }
        return HeaderCandidate.empty();
    }

    private HeaderCandidate<Integer> configuredForwardedPortCandidate(HttpServletRequest request) {
        for (String headerName : this.properties.getTarget().getPortHeaderNames()) {
            for (String value : existingHeaderValues(request, headerName)) {
                for (String token : splitCommaSeparatedValues(value)) {
                    Integer port = parsePort(token);
                    if (port != null) {
                        return new HeaderCandidate<>(headerName, port);
                    }
                }
            }
        }
        return HeaderCandidate.empty();
    }

    private HeaderCandidate<String> configuredForwardedPrefixCandidate(HttpServletRequest request) {
        if (!this.properties.getTarget().isApplyForwardedPrefix()) {
            return HeaderCandidate.empty();
        }
        for (String headerName : this.properties.getTarget().getPrefixHeaderNames()) {
            for (String value : existingHeaderValues(request, headerName)) {
                for (String token : splitCommaSeparatedValues(value)) {
                    String prefix = normalizePrefix(token);
                    if (prefix != null) {
                        return new HeaderCandidate<>(headerName, prefix);
                    }
                }
            }
        }
        return HeaderCandidate.empty();
    }

    private static String applyPrefix(String path, String prefix) {
        String normalizedPath = normalizePath(path);
        String normalizedPrefix = normalizePrefix(prefix);
        if (normalizedPrefix == null) {
            return normalizedPath;
        }
        if (normalizedPath == null || "/".equals(normalizedPath)) {
            return normalizedPrefix;
        }
        if (normalizedPath.equals(normalizedPrefix) || normalizedPath.startsWith(normalizedPrefix + "/")) {
            return normalizedPath;
        }
        return normalizedPrefix.equals("/") ? normalizedPath : normalizedPrefix + normalizedPath;
    }

    private static HostPort firstHostPort(HostPort first, HostPort second, HostPort fallback) {
        if (first != null && first.present()) {
            return first;
        }
        if (second != null && second.present()) {
            return second;
        }
        return fallback == null ? HostPort.empty() : fallback;
    }

    private static Integer firstPort(Integer... ports) {
        if (ports == null) {
            return null;
        }
        for (Integer port : ports) {
            if (port != null && port > 0 && port <= 65535) {
                return port;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeOptional(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static CocoWebRequestTargetSource resolveSource(boolean forwardedUsed, boolean forwardedHeadersUsed) {
        if (forwardedUsed && forwardedHeadersUsed) {
            return CocoWebRequestTargetSource.MIXED;
        }
        if (forwardedUsed) {
            return CocoWebRequestTargetSource.FORWARDED;
        }
        if (forwardedHeadersUsed) {
            return CocoWebRequestTargetSource.FORWARDED_HEADERS;
        }
        return CocoWebRequestTargetSource.SERVLET;
    }

    private static CocoWebRequestTargetSource resolveSource(boolean forwardedHeaderUsed,
            boolean forwardedSchemeUsed, boolean forwardedHostUsed, boolean forwardedPortUsed,
            boolean forwardedPrefixUsed) {
        return resolveSource(forwardedHeaderUsed, forwardedSchemeUsed || forwardedHostUsed
                || forwardedPortUsed || forwardedPrefixUsed);
    }

    @SafeVarargs
    private static List<String> sourceHeaders(boolean forwardedUsed, HeaderCandidate<?>... candidates) {
        LinkedHashSet<String> headerNames = new LinkedHashSet<>();
        if (forwardedUsed) {
            headerNames.add("forwarded");
        }
        if (candidates != null) {
            for (HeaderCandidate<?> candidate : candidates) {
                if (candidate != null && candidate.present()) {
                    headerNames.add(candidate.name().toLowerCase(Locale.ROOT));
                }
            }
        }
        return List.copyOf(headerNames);
    }

    private static String forwardedParameter(String segment, String name) {
        if (segment == null || segment.isBlank() || name == null || name.isBlank()) {
            return null;
        }
        return Arrays.stream(segment.split(";"))
                .map(String::trim)
                .filter(part -> part.regionMatches(true, 0, name + "=", 0, name.length() + 1))
                .map(part -> cleanQuotedValue(part.substring(name.length() + 1)))
                .map(DefaultCocoWebRequestTargetResolver::normalizeOptional)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String cleanQuotedValue(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            return normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private static String normalizeForwardedScheme(String headerName, String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        String loweredHeaderName = normalizeOptional(headerName);
        if (loweredHeaderName != null) {
            String headerKey = loweredHeaderName.toLowerCase(Locale.ROOT);
            if ("x-forwarded-ssl".equals(headerKey) || "front-end-https".equals(headerKey)) {
                if ("on".equalsIgnoreCase(normalized) || "true".equalsIgnoreCase(normalized)
                        || "1".equals(normalized) || "https".equalsIgnoreCase(normalized)) {
                    return "https";
                }
                if ("off".equalsIgnoreCase(normalized) || "false".equalsIgnoreCase(normalized)
                        || "0".equals(normalized) || "http".equalsIgnoreCase(normalized)) {
                    return "http";
                }
            }
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static HostPort parseHostPort(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return HostPort.empty();
        }
        if (normalized.startsWith("[") && normalized.contains("]")) {
            int closingIndex = normalized.indexOf(']');
            String host = normalized.substring(1, closingIndex);
            String suffix = normalized.substring(closingIndex + 1).trim();
            Integer port = suffix.startsWith(":") ? parsePort(suffix.substring(1)) : null;
            return new HostPort(host, port);
        }
        int separatorIndex = normalized.lastIndexOf(':');
        if (separatorIndex > 0 && normalized.indexOf(':') == separatorIndex) {
            Integer port = parsePort(normalized.substring(separatorIndex + 1));
            if (port != null) {
                return new HostPort(normalized.substring(0, separatorIndex), port);
            }
        }
        return new HostPort(normalized, null);
    }

    private static Integer parsePort(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        try {
            int port = Integer.parseInt(normalized);
            return normalizePort(port);
        }
        catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer normalizePort(int port) {
        return port <= 0 || port > 65535 ? null : port;
    }

    private static String normalizeScheme(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizePrefix(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null || normalized.contains("://") || normalized.contains("?") || normalized.contains("#")) {
            return null;
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizePath(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<String> existingHeaderValues(HttpServletRequest request, String headerName) {
        Enumeration<String> values = request.getHeaders(headerName);
        if (values == null) {
            return List.of();
        }
        return Collections.list(values).stream()
                .map(DefaultCocoWebRequestTargetResolver::normalizeOptional)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String existingHeaderValue(HttpServletRequest request, String headerName) {
        return existingHeaderValues(request, headerName).stream().findFirst().orElse(null);
    }

    private static List<String> splitCommaSeparatedValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private record ForwardedTarget(String scheme, HostPort hostPort) {

        private static ForwardedTarget empty() {
            return new ForwardedTarget(null, HostPort.empty());
        }

        private boolean present() {
            return this.scheme != null || (this.hostPort != null && this.hostPort.present());
        }
    }

    private record HostPort(String host, Integer port) {

        private HostPort {
            host = normalizeOptional(host);
        }

        private static HostPort empty() {
            return new HostPort(null, null);
        }

        private boolean present() {
            return this.host != null || this.port != null;
        }
    }

    private record HeaderCandidate<T>(String name, T value) {

        private static <T> HeaderCandidate<T> empty() {
            return new HeaderCandidate<>(null, null);
        }

        private boolean present() {
            return this.name != null && this.value != null;
        }
    }
}
