package io.github.coco.feature.web.context;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.context.payload.CocoPayloadParameterResolver;
import io.github.coco.feature.web.context.payload.DefaultCocoPayloadParameterResolver;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 默认 Coco 请求参数解析器。
 * <p>
 * 基于 Web 请求参数配置解析查询字符串和请求参数，普通上下文视图会执行脱敏和裁剪，安全输入视图保留原始参数。
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

    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";

    private final CocoWebParameterProperties properties;

    private final CocoPayloadParameterResolver payloadParameterResolver;

    /**
     * <p>
     * 使用旧访问日志参数配置创建默认 Coco 请求参数解析器。
     * </p>
     * @param properties 访问日志采集配置属性
     */
    public DefaultCocoRequestParameterResolver(CocoAccessLogCaptureProperties properties) {
        this(CocoWebParameterProperties.fromAccessLog(properties));
    }

    /**
     * <p>
     * 创建默认 Coco 请求参数解析器。
     * </p>
     * @param properties Web 请求参数配置属性
     */
    public DefaultCocoRequestParameterResolver(CocoWebParameterProperties properties) {
        this(properties, null);
    }

    /**
     * <p>
     * 创建默认 Coco 请求参数解析器。
     * </p>
     * @param properties Web 请求参数配置属性
     * @param payloadParameterResolver 请求体参数解析器
     */
    public DefaultCocoRequestParameterResolver(CocoWebParameterProperties properties,
            CocoPayloadParameterResolver payloadParameterResolver) {
        this.properties = properties == null ? new CocoWebParameterProperties() : properties;
        this.payloadParameterResolver = payloadParameterResolver == null
                ? new DefaultCocoPayloadParameterResolver(this.properties)
                : payloadParameterResolver;
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
        Map<String, List<String>> payloadParameters = resolvePayloadParameters(checkedRequest);
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        if (isCachedFormPayload(checkedRequest) && !payloadParameters.isEmpty()) {
            merge(parameters, resolveQueryParameters(checkedRequest));
        }
        else {
            checkedRequest.getParameterMap().forEach((name, values) -> parameters.put(name,
                    new ArrayList<>(Arrays.stream(values == null ? new String[0] : values)
                            .map(value -> sanitizeParameterValue(name, value))
                            .toList())));
        }
        merge(parameters, payloadParameters);
        return copy(parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> resolveQueryParameters(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        if (!this.properties.isIncludeParameters()) {
            return Map.of();
        }
        return parseSanitizedQueryString(resolveRawQueryString(checkedRequest));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> resolvePayloadParameters(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        if (!this.properties.isIncludeParameters()) {
            return Map.of();
        }
        return this.payloadParameterResolver.resolvePayloadParameters(checkedRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> resolveRawParameters(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        Map<String, List<String>> parameters = new LinkedHashMap<>(resolveRawQueryParameters(checkedRequest));
        merge(parameters, resolveRawPayloadParameters(checkedRequest));
        return copy(parameters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> resolveRawQueryParameters(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        return parseRawQueryString(resolveRawQueryString(checkedRequest));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, List<String>> resolveRawPayloadParameters(HttpServletRequest request) {
        HttpServletRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        return this.payloadParameterResolver.resolveRawPayloadParameters(checkedRequest);
    }

    private static void merge(Map<String, List<String>> target, Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((name, values) -> {
            if (name == null || name.isBlank()) {
                return;
            }
            List<String> targetValues = new ArrayList<>(target.getOrDefault(name, List.of()));
            List<String> coveredValues = new ArrayList<>(targetValues);
            for (String value : values == null || values.isEmpty() ? List.of("") : values) {
                String normalizedValue = value == null ? "" : value;
                // 保留单一来源内的重复值，同时避免同一参数经多个解析入口被双算。
                int coveredIndex = coveredValues.indexOf(normalizedValue);
                if (coveredIndex >= 0) {
                    coveredValues.remove(coveredIndex);
                }
                else {
                    targetValues.add(normalizedValue);
                }
            }
            target.put(name, targetValues);
        });
    }

    private Map<String, List<String>> parseSanitizedQueryString(String queryString) {
        Map<String, List<String>> rawParameters = parseRawQueryString(queryString);
        if (rawParameters.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        rawParameters.forEach((name, values) -> {
            String decodedName = decodeQueryComponent(name);
            if (decodedName.isBlank()) {
                return;
            }
            List<String> sanitizedValues = new ArrayList<>();
            for (String value : values == null || values.isEmpty() ? List.of("") : values) {
                sanitizedValues.add(sanitizeParameterValue(decodedName, decodeQueryComponent(value)));
            }
            parameters.computeIfAbsent(decodedName, ignored -> new ArrayList<>()).addAll(sanitizedValues);
        });
        return copy(parameters);
    }

    private static Map<String, List<String>> parseRawQueryString(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return Map.of();
        }
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        for (String pair : queryString.split("&", -1)) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int separatorIndex = pair.indexOf('=');
            String name = separatorIndex < 0 ? pair : pair.substring(0, separatorIndex);
            if (name.isBlank()) {
                continue;
            }
            String value = separatorIndex < 0 ? "" : pair.substring(separatorIndex + 1);
            parameters.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
        if (parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copied = new LinkedHashMap<>();
        parameters.forEach((name, values) -> copied.put(name, List.copyOf(values)));
        return Collections.unmodifiableMap(copied);
    }

    private static Map<String, List<String>> copy(Map<String, List<String>> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copied = new LinkedHashMap<>();
        parameters.forEach((name, values) -> copied.put(name, List.copyOf(values)));
        return Collections.unmodifiableMap(copied);
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

    private static boolean isCachedFormPayload(HttpServletRequest request) {
        return FORM_URLENCODED.equals(normalizeMediaType(request.getContentType()))
                && CocoCachedBodyHttpServletRequest.cachedBody(request)
                        .filter(cachedBody -> cachedBody.cached() && cachedBody.content().length > 0)
                        .isPresent();
    }

    private static String normalizeMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int parameterIndex = contentType.indexOf(';');
        String value = parameterIndex < 0 ? contentType : contentType.substring(0, parameterIndex);
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
