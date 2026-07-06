package io.github.coco.feature.web.context;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认 Coco 请求头解析器。
 * <p>
 * 基于 Web 上下文配置采集请求头，对普通上下文请求头执行裁剪和脱敏，对安全输入请求头保留原始值。
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
public final class DefaultCocoRequestHeaderResolver implements CocoRequestHeaderResolver {

    private static final String MASKED_VALUE = "******";

    private final CocoWebContextProperties properties;

    /**
     * <p>
     * 创建默认 Coco 请求头解析器。
     * </p>
     * @param properties Web 请求上下文配置属性
     */
    public DefaultCocoRequestHeaderResolver(CocoWebContextProperties properties) {
        this.properties = properties == null ? new CocoWebContextProperties() : properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> resolveIncludedHeaders(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        if (!this.properties.isIncludeHeaders()) {
            return Map.of();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (String headerName : this.properties.getIncludedHeaderNames()) {
            String value = existingHeaderValue(checkedRequest, headerName);
            if (value != null) {
                headers.put(headerName, sanitizeHeaderValue(headerName, value));
            }
        }
        return headers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> resolveSelectedHeaders(HttpServletRequest request, Iterable<String> headerNames,
            boolean trimValue) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        Map<String, String> headers = new LinkedHashMap<>();
        if (headerNames == null) {
            return headers;
        }
        for (String headerName : headerNames) {
            if (headerName == null || headerName.isBlank()) {
                continue;
            }
            String value = existingHeaderValue(checkedRequest, headerName);
            if (value != null) {
                headers.put(headerName.trim().toLowerCase(Locale.ROOT),
                        trimValue ? trimValue(value, this.properties.getMaxHeaderValueLength()) : value);
            }
        }
        return headers;
    }

    private static String existingHeaderValue(HttpServletRequest request, String headerName) {
        Enumeration<String> values = request.getHeaders(headerName);
        if (values == null) {
            return null;
        }
        List<String> normalizedValues = enumerationAsStream(values)
                .map(DefaultCocoRequestHeaderResolver::normalizeString)
                .filter(Objects::nonNull)
                .toList();
        return normalizedValues.isEmpty() ? null : String.join(",", normalizedValues);
    }

    private String sanitizeHeaderValue(String name, String value) {
        if (name != null && this.properties.getMaskedHeaderNames().contains(name.trim().toLowerCase(Locale.ROOT))) {
            return MASKED_VALUE;
        }
        return trimValue(value, this.properties.getMaxHeaderValueLength());
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
}
