package io.github.coco.feature.web.context;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认 Coco 请求参数解析器。
 * <p>
 * 基于访问日志采集配置解析查询字符串和请求参数，普通上下文视图会执行脱敏和裁剪，安全输入视图保留原始参数。
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
public final class DefaultCocoRequestParameterResolver implements CocoRequestParameterResolver {

    private static final String MASKED_VALUE = "******";

    private final CocoAccessLogCaptureProperties properties;

    /**
     * <p>
     * 创建默认 Coco 请求参数解析器。
     * </p>
     * @param properties 访问日志采集配置属性
     */
    public DefaultCocoRequestParameterResolver(CocoAccessLogCaptureProperties properties) {
        this.properties = properties == null ? new CocoAccessLogCaptureProperties() : properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveQueryString(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        if (!this.properties.isIncludeParameters()) {
            return null;
        }
        String queryString = checkedRequest.getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return null;
        }
        return Arrays.stream(queryString.split("&"))
                .map(this::sanitizeQueryPair)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + "&" + right)
                .orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String resolveRawQueryString(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        String queryString = checkedRequest.getQueryString();
        return queryString == null || queryString.isBlank() ? null : queryString.trim();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> resolveParameters(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        if (!this.properties.isIncludeParameters()) {
            return Map.of();
        }
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        checkedRequest.getParameterMap().forEach((name, values) -> parameters.put(name,
                Arrays.stream(values == null ? new String[0] : values)
                        .map(value -> sanitizeParameterValue(name, value))
                        .toList()));
        return parameters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> resolveRawParameters(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        checkedRequest.getParameterMap().forEach((name, values) -> parameters.put(name,
                Arrays.stream(values == null ? new String[0] : values)
                        .map(value -> value == null ? "" : value)
                        .toList()));
        return parameters;
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

    private String sanitizeParameterValue(String name, String value) {
        if (isMaskedParameterName(name)) {
            return MASKED_VALUE;
        }
        return trimParameterValue(value);
    }

    private boolean isMaskedParameterName(String name) {
        return name != null && this.properties.getMaskedParameterNames()
                .contains(name.trim().toLowerCase(Locale.ROOT));
    }

    private String trimParameterValue(String value) {
        return trimValue(value, this.properties.getMaxParameterValueLength());
    }

    private static String trimValue(String value, int maxLength) {
        String normalized = value == null || value.isBlank() ? "" : value.trim();
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
