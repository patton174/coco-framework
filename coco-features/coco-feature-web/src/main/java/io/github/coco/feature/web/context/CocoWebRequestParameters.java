package io.github.coco.feature.web.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Coco Web 请求参数快照。
 * <p>
 * 同时保存查询字符串、合并参数、查询参数和请求体参数。该类型既可承载清洗后的上下文视图，也可承载
 * 安全能力使用的原始参数视图。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param queryString 查询字符串
 * @param parameters 合并后的请求参数
 * @param queryParameters 查询参数
 * @param payloadParameters 请求体参数
 * @param payloadSource 请求体参数来源
 * @author patton174
 * @since 1.0.0
 */
public record CocoWebRequestParameters(String queryString, Map<String, List<String>> parameters,
        Map<String, List<String>> queryParameters, Map<String, List<String>> payloadParameters,
        CocoWebParameterSource payloadSource) {

    /**
     * <p>
     * 创建请求参数快照。
     * </p>
     * @param queryString 查询字符串
     * @param parameters 合并后的请求参数
     * @param queryParameters 查询参数
     * @param payloadParameters 请求体参数
     */
    public CocoWebRequestParameters(String queryString, Map<String, List<String>> parameters,
            Map<String, List<String>> queryParameters, Map<String, List<String>> payloadParameters) {
        this(queryString, parameters, queryParameters, payloadParameters, null);
    }

    /**
     * <p>
     * 创建请求参数快照，并归一化空白字段和集合字段。
     * </p>
     * @param queryString 查询字符串
     * @param parameters 合并后的请求参数
     * @param queryParameters 查询参数
     * @param payloadParameters 请求体参数
     * @param payloadSource 请求体参数来源
     */
    public CocoWebRequestParameters {
        queryString = normalizeOptional(queryString);
        parameters = copyParameters(parameters);
        queryParameters = copyParameters(queryParameters);
        payloadParameters = copyParameters(payloadParameters);
        payloadSource = normalizePayloadSource(payloadSource, payloadParameters);
    }

    /**
     * <p>
     * 返回空参数快照。
     * </p>
     * @return 空参数快照
     */
    public static CocoWebRequestParameters empty() {
        return new CocoWebRequestParameters(null, Map.of(), Map.of(), Map.of(), CocoWebParameterSource.NONE);
    }

    /**
     * <p>
     * 返回指定来源的参数映射。
     * </p>
     * @param source 参数来源
     * @return 参数映射
     */
    public Map<String, List<String>> sourceParameters(CocoWebParameterSource source) {
        CocoWebParameterSource parameterSource = source == null ? CocoWebParameterSource.MERGED : source;
        return switch (parameterSource) {
            case QUERY -> this.queryParameters;
            case PAYLOAD -> this.payloadParameters;
            case FORM -> this.payloadSource == CocoWebParameterSource.FORM ? this.payloadParameters : Map.of();
            case JSON -> this.payloadSource == CocoWebParameterSource.JSON ? this.payloadParameters : Map.of();
            case NONE -> Map.of();
            case MERGED -> this.parameters;
        };
    }

    /**
     * <p>
     * 返回合并参数视图中的指定参数值。
     * </p>
     * @param name 参数名
     * @return 参数值列表；不存在时为空
     */
    public Optional<List<String>> values(String name) {
        return values(name, CocoWebParameterSource.MERGED);
    }

    /**
     * <p>
     * 返回指定来源中的参数值。
     * </p>
     * @param name 参数名
     * @param source 参数来源
     * @return 参数值列表；不存在时为空
     */
    public Optional<List<String>> values(String name, CocoWebParameterSource source) {
        String normalizedName = normalizeOptional(name);
        if (normalizedName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sourceParameters(source).get(normalizedName));
    }

    /**
     * <p>
     * 返回合并参数视图中的第一个参数值。
     * </p>
     * @param name 参数名
     * @return 第一个参数值；不存在时为空
     */
    public Optional<String> firstValue(String name) {
        return firstValue(name, CocoWebParameterSource.MERGED);
    }

    /**
     * <p>
     * 返回指定来源中的第一个参数值。
     * </p>
     * @param name 参数名
     * @param source 参数来源
     * @return 第一个参数值；不存在时为空
     */
    public Optional<String> firstValue(String name, CocoWebParameterSource source) {
        return values(name, source)
                .filter(values -> !values.isEmpty())
                .map(values -> values.get(0));
    }

    /**
     * <p>
     * 判断合并参数视图中是否包含指定参数。
     * </p>
     * @param name 参数名
     * @return 包含时返回 {@code true}
     */
    public boolean contains(String name) {
        return contains(name, CocoWebParameterSource.MERGED);
    }

    /**
     * <p>
     * 判断指定来源中是否包含指定参数。
     * </p>
     * @param name 参数名
     * @param source 参数来源
     * @return 包含时返回 {@code true}
     */
    public boolean contains(String name, CocoWebParameterSource source) {
        return values(name, source).isPresent();
    }

    /**
     * <p>
     * 返回排除指定参数后的参数快照。
     * </p>
     * @param parameterNames 需要排除的参数名集合
     * @return 排除指定参数后的参数快照
     */
    public CocoWebRequestParameters without(Set<String> parameterNames) {
        Set<String> excludedNames = normalizeParameterNames(parameterNames);
        if (excludedNames.isEmpty()) {
            return this;
        }
        return new CocoWebRequestParameters(filterQueryString(this.queryString, excludedNames),
                filterParameters(this.parameters, excludedNames),
                filterParameters(this.queryParameters, excludedNames),
                filterParameters(this.payloadParameters, excludedNames), this.payloadSource);
    }

    private static Map<String, List<String>> filterParameters(Map<String, List<String>> parameters,
            Set<String> excludedNames) {
        if (parameters == null || parameters.isEmpty() || excludedNames.isEmpty()) {
            return parameters == null ? Map.of() : parameters;
        }
        Map<String, List<String>> filtered = new LinkedHashMap<>();
        parameters.forEach((name, values) -> {
            if (!excludedNames.contains(name)) {
                filtered.put(name, values);
            }
        });
        return filtered.isEmpty() ? Map.of() : Collections.unmodifiableMap(filtered);
    }

    private static String filterQueryString(String queryString, Set<String> excludedNames) {
        if (queryString == null || queryString.isBlank() || excludedNames.isEmpty()) {
            return queryString;
        }
        StringBuilder builder = new StringBuilder(queryString.length());
        for (String pair : queryString.split("&", -1)) {
            if (pair == null || pair.isBlank() || excludedNames.contains(parameterName(pair))) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(pair);
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private static String parameterName(String pair) {
        int separatorIndex = pair.indexOf('=');
        return separatorIndex < 0 ? pair : pair.substring(0, separatorIndex);
    }

    private static Set<String> normalizeParameterNames(Set<String> parameterNames) {
        if (parameterNames == null || parameterNames.isEmpty()) {
            return Set.of();
        }
        return parameterNames.stream()
                .map(CocoWebRequestParameters::normalizeOptional)
                .filter(name -> name != null)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static CocoWebParameterSource normalizePayloadSource(CocoWebParameterSource payloadSource,
            Map<String, List<String>> payloadParameters) {
        if (payloadSource != null && payloadSource.payload()) {
            return payloadSource;
        }
        if (payloadParameters != null && !payloadParameters.isEmpty()) {
            return CocoWebParameterSource.PAYLOAD;
        }
        return CocoWebParameterSource.NONE;
    }

    private static Map<String, List<String>> copyParameters(Map<String, List<String>> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copied = new LinkedHashMap<>();
        parameters.forEach((name, values) -> {
            String normalizedName = normalizeOptional(name);
            if (normalizedName != null) {
                copied.put(normalizedName, copyValues(values));
            }
        });
        return copied.isEmpty() ? Map.of() : Collections.unmodifiableMap(copied);
    }

    private static List<String> copyValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of("");
        }
        List<String> copied = new ArrayList<>(values.size());
        for (String value : values) {
            copied.add(value == null ? "" : value);
        }
        return List.copyOf(copied);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
