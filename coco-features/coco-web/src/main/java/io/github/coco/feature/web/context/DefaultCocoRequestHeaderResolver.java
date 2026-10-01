package io.github.coco.feature.web.context;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final Set<String> contributedSensitiveHeaderNames;

    /**
     * <p>
     * 创建默认 Coco 请求头解析器。
     * </p>
     * @param properties Web 请求上下文配置属性
     */
    public DefaultCocoRequestHeaderResolver(CocoWebContextProperties properties) {
        this(properties, List.of());
    }

    /**
     * <p>创建默认解析器，并纳入各 Web 功能贡献的敏感请求头。</p>
     * @param properties Web 请求上下文配置属性
     * @param contributors 敏感请求头贡献者
     */
    public DefaultCocoRequestHeaderResolver(CocoWebContextProperties properties,
            Iterable<CocoSensitiveRequestHeaderContributor> contributors) {
        this.properties = properties == null ? new CocoWebContextProperties() : properties;
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        if (contributors != null) {
            for (CocoSensitiveRequestHeaderContributor contributor : contributors) {
                if (contributor == null || contributor.headerNames() == null) { continue; }
                contributor.headerNames().stream().filter(Objects::nonNull).map(String::trim)
                        .filter(name -> !name.isEmpty()).map(name -> name.toLowerCase(Locale.ROOT)).forEach(names::add);
            }
        }
        this.contributedSensitiveHeaderNames = Set.copyOf(names);
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
        java.util.LinkedHashSet<String> included = new java.util.LinkedHashSet<>(this.properties.getIncludedHeaderNames());
        included.addAll(this.contributedSensitiveHeaderNames);
        for (String headerName : included) {
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> resolveSelectedHeaderValues(HttpServletRequest request,
            Iterable<String> headerNames, boolean trimValue) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        Map<String, List<String>> headers = new LinkedHashMap<>();
        if (headerNames == null) {
            return headers;
        }
        for (String headerName : headerNames) {
            if (headerName == null || headerName.isBlank()) {
                continue;
            }
            List<String> values = existingHeaderValues(checkedRequest, headerName);
            if (!values.isEmpty()) {
                headers.put(headerName.trim().toLowerCase(Locale.ROOT), trimValue
                        ? values.stream()
                                .map(value -> trimValue(value, this.properties.getMaxHeaderValueLength()))
                                .toList()
                        : values);
            }
        }
        return headers;
    }

    private static String existingHeaderValue(HttpServletRequest request, String headerName) {
        List<String> normalizedValues = existingHeaderValues(request, headerName);
        return normalizedValues.isEmpty() ? null : String.join(",", normalizedValues);
    }

    private static List<String> existingHeaderValues(HttpServletRequest request, String headerName) {
        Enumeration<String> values = request.getHeaders(headerName);
        if (values == null) {
            return List.of();
        }
        return enumerationAsStream(values)
                .map(DefaultCocoRequestHeaderResolver::normalizeString)
                .filter(Objects::nonNull)
                .toList();
    }

    private String sanitizeHeaderValue(String name, String value) {
        if (name != null && (this.properties.getMaskedHeaderNames().contains(name.trim().toLowerCase(Locale.ROOT))
                || this.contributedSensitiveHeaderNames.contains(name.trim().toLowerCase(Locale.ROOT)))) {
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
        return io.github.coco.context.CocoStrings.blankToNull(value);
    }

    private static String trimValue(String value, int maxLength) {
        String normalized = normalizeString(value);
        if (normalized == null) {
            return "";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
