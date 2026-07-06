package io.github.coco.feature.web.context;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Coco Web 默认请求上下文解析器。
 * <p>
 * 解析客户端 IP、请求头、请求参数、查询字符串、语言、协议、主机和内容类型，并对敏感字段做统一清洗。
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
public final class DefaultCocoWebRequestContextResolver implements CocoWebRequestContextResolver {

    private static final String MASKED_VALUE = "******";

    private final CocoWebContextProperties properties;

    private final CocoAccessLogCaptureProperties accessLogProperties;

    /**
     * <p>
     * 创建默认请求上下文解析器。
     * </p>
     * @param properties Web 请求上下文配置
     * @param accessLogProperties 访问日志采集配置
     */
    public DefaultCocoWebRequestContextResolver(CocoWebContextProperties properties,
            CocoAccessLogCaptureProperties accessLogProperties) {
        this.properties = properties == null ? new CocoWebContextProperties() : properties;
        this.accessLogProperties = accessLogProperties == null
                ? new CocoAccessLogCaptureProperties()
                : accessLogProperties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebRequestSnapshot resolve(String traceId, HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        String method = checkedRequest.getMethod();
        String path = resolvePath(checkedRequest);
        String rawQueryString = normalizeString(checkedRequest.getQueryString());
        Map<String, List<String>> rawParameters = resolveRawParameters(checkedRequest);
        Map<String, String> securityHeaders = resolveSelectedHeaders(checkedRequest,
                this.properties.getSecurityHeaderNames(), false);
        Map<String, String> canonicalHeaders = resolveSelectedHeaders(checkedRequest,
                this.properties.getCanonicalHeaderNames(), false);
        CocoBrowserFingerprint browserFingerprint = CocoBrowserFingerprint.from(resolveSelectedHeaders(checkedRequest,
                this.properties.getFingerprintHeaderNames(), true));
        CocoWebRequestSecurityInput securityInput = new CocoWebRequestSecurityInput(method, path, rawQueryString,
                rawParameters, securityHeaders, canonicalHeaders, null);
        return new CocoWebRequestSnapshot(traceId, method, path, resolveQueryString(checkedRequest),
                resolveClientIp(checkedRequest), checkedRequest.getHeader("User-Agent"), resolveLocale(checkedRequest),
                checkedRequest.getScheme(), checkedRequest.getServerName(), checkedRequest.getServerPort(),
                checkedRequest.getContentType(), resolveHeaders(checkedRequest), resolveParameters(checkedRequest),
                securityInput, browserFingerprint);
    }

    private static String resolvePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri == null || requestUri.isBlank() ? null : requestUri;
    }

    private String resolveClientIp(HttpServletRequest request) {
        for (String headerName : this.properties.getClientIpHeaderNames()) {
            String headerValue = request.getHeader(headerName);
            String clientIp = "Forwarded".equalsIgnoreCase(headerName)
                    ? firstForwardedForValue(headerValue)
                    : firstHeaderValue(headerValue);
            if (isUsableClientIp(clientIp)) {
                return clientIp;
            }
        }
        return normalizeString(request.getRemoteAddr());
    }

    private static String firstForwardedForValue(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        return Arrays.stream(headerValue.split(","))
                .map(DefaultCocoWebRequestContextResolver::forwardedForValue)
                .filter(DefaultCocoWebRequestContextResolver::isUsableClientIp)
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
                .map(DefaultCocoWebRequestContextResolver::cleanClientIpToken)
                .filter(DefaultCocoWebRequestContextResolver::isUsableClientIp)
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

    private String resolveQueryString(HttpServletRequest request) {
        if (!this.accessLogProperties.isIncludeParameters()) {
            return null;
        }
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return null;
        }
        return Arrays.stream(queryString.split("&"))
                .map(this::sanitizeQueryPair)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + "&" + right)
                .orElse(null);
    }

    private String sanitizeQueryPair(String pair) {
        int separatorIndex = pair.indexOf('=');
        String name = separatorIndex < 0 ? pair : pair.substring(0, separatorIndex);
        String value = separatorIndex < 0 ? "" : pair.substring(separatorIndex + 1);
        if (isMaskedParameterName(decodeQueryComponent(name))) {
            return name + "=" + MASKED_VALUE;
        }
        return separatorIndex < 0 ? trimParameterValue(name) : name + "=" + trimParameterValue(value);
    }

    private Map<String, List<String>> resolveParameters(HttpServletRequest request) {
        if (!this.accessLogProperties.isIncludeParameters()) {
            return Map.of();
        }
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> parameters.put(name,
                Arrays.stream(values == null ? new String[0] : values)
                        .map(value -> sanitizeParameterValue(name, value))
                        .toList()));
        return parameters;
    }

    private static Map<String, List<String>> resolveRawParameters(HttpServletRequest request) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((name, values) -> parameters.put(name,
                Arrays.stream(values == null ? new String[0] : values)
                        .map(value -> value == null ? "" : value)
                        .toList()));
        return parameters;
    }

    private String sanitizeParameterValue(String name, String value) {
        if (isMaskedParameterName(name)) {
            return MASKED_VALUE;
        }
        return trimParameterValue(value);
    }

    private boolean isMaskedParameterName(String name) {
        return name != null && this.accessLogProperties.getMaskedParameterNames()
                .contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private String trimParameterValue(String value) {
        return trimValue(value, this.accessLogProperties.getMaxParameterValueLength());
    }

    private Map<String, String> resolveHeaders(HttpServletRequest request) {
        if (!this.properties.isIncludeHeaders()) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (String headerName : this.properties.getIncludedHeaderNames()) {
            String value = firstExistingHeaderValue(request, headerName);
            if (value != null) {
                headers.put(headerName, sanitizeHeaderValue(headerName, value));
            }
        }
        return headers;
    }

    private Map<String, String> resolveSelectedHeaders(HttpServletRequest request, Iterable<String> headerNames,
            boolean trimValue) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (headerNames == null) {
            return headers;
        }
        for (String headerName : headerNames) {
            if (headerName == null || headerName.isBlank()) {
                continue;
            }
            String value = firstExistingHeaderValue(request, headerName);
            if (value != null) {
                headers.put(headerName.trim().toLowerCase(Locale.ROOT),
                        trimValue ? trimValue(value, this.properties.getMaxHeaderValueLength()) : value);
            }
        }
        return headers;
    }

    private static String firstExistingHeaderValue(HttpServletRequest request, String headerName) {
        Enumeration<String> values = request.getHeaders(headerName);
        if (values == null) {
            return null;
        }
        return enumerationAsStream(values)
                .map(DefaultCocoWebRequestContextResolver::normalizeString)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String sanitizeHeaderValue(String name, String value) {
        if (name != null && this.properties.getMaskedHeaderNames().contains(name.trim().toLowerCase(Locale.ROOT))) {
            return MASKED_VALUE;
        }
        return trimValue(value, this.properties.getMaxHeaderValueLength());
    }

    private static String resolveLocale(HttpServletRequest request) {
        Locale locale = request.getLocale();
        return locale == null ? null : locale.toLanguageTag();
    }

    private static Stream<String> enumerationAsStream(Enumeration<String> values) {
        if (values == null) {
            return Stream.empty();
        }
        List<String> copied = new ArrayList<>();
        while (values.hasMoreElements()) {
            copied.add(values.nextElement());
        }
        return copied.stream();
    }

    private static String normalizeString(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimValue(String value, int maxLength) {
        String normalized = normalizeString(value);
        if (normalized == null) {
            return "";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    private static String decodeQueryComponent(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        }
        catch (IllegalArgumentException ex) {
            return value;
        }
    }
}
