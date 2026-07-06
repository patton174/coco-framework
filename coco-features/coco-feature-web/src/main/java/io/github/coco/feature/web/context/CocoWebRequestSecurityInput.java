package io.github.coco.feature.web.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Coco Web 请求安全输入。
 * <p>
 * 保存后续 AES 加密、Sign 签名和防重放能力需要的原始或规范化请求输入，不使用访问日志的脱敏值。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param method HTTP 方法
 * @param path 请求路径
 * @param queryString 原始查询字符串
 * @param parameters 原始请求参数
 * @param securityHeaders 安全能力相关请求头
 * @param canonicalHeaders 默认参与签名规范化的请求头
 * @param bodySha256 请求体 SHA-256 摘要；未解析请求体时为空
 * @param bodyLength 请求体长度；未解析请求体时为空
 * @param bodyCached 请求体是否已缓存
 * @param canonicalHeaderValues 默认参与签名规范化的多值请求头
 * @author patton174
 * @since 1.0.0
 */
public record CocoWebRequestSecurityInput(String method, String path, String queryString,
        Map<String, List<String>> parameters, Map<String, List<String>> queryParameters,
        Map<String, List<String>> payloadParameters, Map<String, String> securityHeaders,
        Map<String, String> canonicalHeaders, String bodySha256, Long bodyLength, boolean bodyCached,
        Map<String, List<String>> canonicalHeaderValues) {

    /**
     * <p>
     * 创建请求安全输入。
     * </p>
     * @param method HTTP 方法
     * @param path 请求路径
     * @param queryString 原始查询字符串
     * @param parameters 原始请求参数
     * @param securityHeaders 安全能力相关请求头
     * @param canonicalHeaders 默认参与签名规范化的请求头
     * @param bodySha256 请求体 SHA-256 摘要
     */
    public CocoWebRequestSecurityInput(String method, String path, String queryString,
            Map<String, List<String>> parameters, Map<String, String> securityHeaders,
            Map<String, String> canonicalHeaders, String bodySha256) {
        this(method, path, queryString, parameters, securityHeaders, canonicalHeaders, bodySha256, null, false);
    }

    /**
     * <p>
     * 创建请求安全输入。
     * </p>
     * @param method HTTP 方法
     * @param path 请求路径
     * @param queryString 原始查询字符串
     * @param parameters 原始请求参数
     * @param securityHeaders 安全能力相关请求头
     * @param canonicalHeaders 默认参与签名规范化的请求头
     * @param bodySha256 请求体 SHA-256 摘要
     * @param bodyLength 请求体长度
     * @param bodyCached 请求体是否已缓存
     */
    public CocoWebRequestSecurityInput(String method, String path, String queryString,
            Map<String, List<String>> parameters, Map<String, String> securityHeaders,
            Map<String, String> canonicalHeaders, String bodySha256, Long bodyLength, boolean bodyCached) {
        this(method, path, queryString, parameters, securityHeaders, canonicalHeaders, bodySha256, bodyLength,
                bodyCached, headerValuesFromHeaders(canonicalHeaders));
    }

    /**
     * <p>
     * 创建请求安全输入。
     * </p>
     * @param method HTTP 方法
     * @param path 请求路径
     * @param queryString 原始查询字符串
     * @param parameters 原始请求参数
     * @param securityHeaders 安全能力相关请求头
     * @param canonicalHeaders 默认参与签名规范化的请求头
     * @param bodySha256 请求体 SHA-256 摘要
     * @param bodyLength 请求体长度
     * @param bodyCached 请求体是否已缓存
     * @param canonicalHeaderValues 默认参与签名规范化的多值请求头
     */
    public CocoWebRequestSecurityInput(String method, String path, String queryString,
            Map<String, List<String>> parameters, Map<String, String> securityHeaders,
            Map<String, String> canonicalHeaders, String bodySha256, Long bodyLength, boolean bodyCached,
            Map<String, List<String>> canonicalHeaderValues) {
        this(method, path, queryString, parameters, Map.of(), Map.of(), securityHeaders, canonicalHeaders,
                bodySha256, bodyLength, bodyCached, canonicalHeaderValues);
    }

    /**
     * <p>
     * 创建请求安全输入，并归一化字段和集合。
     * </p>
     * @param method HTTP 方法
     * @param path 请求路径
     * @param queryString 原始查询字符串
     * @param parameters 原始请求参数
     * @param securityHeaders 安全能力相关请求头
     * @param canonicalHeaders 默认参与签名规范化的请求头
     * @param bodySha256 请求体 SHA-256 摘要
     * @param bodyLength 请求体长度
     * @param bodyCached 请求体是否已缓存
     * @param canonicalHeaderValues 默认参与签名规范化的多值请求头
     */
    public CocoWebRequestSecurityInput {
        method = normalizeMethod(method);
        path = normalizeOptional(path);
        queryString = normalizeOptional(queryString);
        queryParameters = copyParameters(queryParameters);
        payloadParameters = copyParameters(payloadParameters);
        parameters = copyParameters(parameters);
        if (parameters.isEmpty() && (!queryParameters.isEmpty() || !payloadParameters.isEmpty())) {
            parameters = mergeParameters(queryParameters, payloadParameters);
        }
        securityHeaders = copyHeaders(securityHeaders);
        canonicalHeaders = copyHeaders(canonicalHeaders);
        canonicalHeaderValues = copyHeaderValues(canonicalHeaderValues);
        bodySha256 = normalizeOptional(bodySha256);
        bodyLength = bodyLength == null || bodyLength < 0 ? null : bodyLength;
        bodyCached = bodyCached && bodySha256 != null;
    }

    /**
     * <p>
     * 创建空请求安全输入。
     * </p>
     * @return 空请求安全输入
     */
    public static CocoWebRequestSecurityInput empty() {
        return new CocoWebRequestSecurityInput(null, null, null, Map.of(), Map.of(), Map.of(), null, null, false);
    }

    /**
     * <p>
     * 返回指定安全请求头。
     * </p>
     * @param name 请求头名称
     * @return 请求头值；未设置时为空
     */
    public Optional<String> securityHeader(String name) {
        return header(this.securityHeaders, name);
    }

    /**
     * <p>
     * 返回指定规范化请求头的合并值。
     * </p>
     * @param name 请求头名称
     * @return 请求头值；未设置时为空
     */
    public Optional<String> canonicalHeader(String name) {
        return header(this.canonicalHeaders, name);
    }

    /**
     * <p>
     * 返回指定规范化请求头的多值快照。
     * </p>
     * @param name 请求头名称
     * @return 请求头值列表；未设置时为空
     */
    public Optional<List<String>> canonicalHeaderValues(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.canonicalHeaderValues.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * <p>
     * 返回指定原始请求参数。
     * </p>
     * @param name 请求参数名称
     * @return 请求参数值；未设置时为空
     */
    public Optional<List<String>> parameter(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.parameters.get(name.trim()));
    }

    /**
     * <p>
     * 返回指定原始查询参数。
     * </p>
     * @param name 请求参数名称
     * @return 查询参数值；未设置时为空
     */
    public Optional<List<String>> queryParameter(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.queryParameters.get(name.trim()));
    }

    /**
     * <p>
     * 返回指定原始请求体参数。
     * </p>
     * @param name 请求参数名称
     * @return 请求体参数值；未设置时为空
     */
    public Optional<List<String>> payloadParameter(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.payloadParameters.get(name.trim()));
    }

    private static Optional<String> header(Map<String, String> headers, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(headers.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    private static String normalizeMethod(String method) {
        String normalized = normalizeOptional(method);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, String> copyHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copied = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            String normalizedName = normalizeOptional(name);
            String normalizedValue = normalizeOptional(value);
            if (normalizedName != null && normalizedValue != null) {
                copied.put(normalizedName.toLowerCase(Locale.ROOT), normalizedValue);
            }
        });
        return copied.isEmpty() ? Map.of() : Collections.unmodifiableMap(copied);
    }

    private static Map<String, List<String>> copyHeaderValues(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copied = new LinkedHashMap<>();
        headers.forEach((name, values) -> {
            String normalizedName = normalizeOptional(name);
            if (normalizedName != null) {
                List<String> copiedValues = copyHeaderValueList(values);
                if (!copiedValues.isEmpty()) {
                    copied.put(normalizedName.toLowerCase(Locale.ROOT), copiedValues);
                }
            }
        });
        return copied.isEmpty() ? Map.of() : Collections.unmodifiableMap(copied);
    }

    private static Map<String, List<String>> headerValuesFromHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> values = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            String normalizedName = normalizeOptional(name);
            String normalizedValue = normalizeOptional(value);
            if (normalizedName != null && normalizedValue != null) {
                values.put(normalizedName.toLowerCase(Locale.ROOT), List.of(normalizedValue));
            }
        });
        return values;
    }

    private static List<String> copyHeaderValueList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> copied = new ArrayList<>(values.size());
        for (String value : values) {
            String normalizedValue = normalizeOptional(value);
            if (normalizedValue != null) {
                copied.add(normalizedValue);
            }
        }
        return List.copyOf(copied);
    }

    private static Map<String, List<String>> copyParameters(Map<String, List<String>> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> copied = new LinkedHashMap<>();
        parameters.forEach((name, values) -> {
            String normalizedName = normalizeOptional(name);
            if (normalizedName != null) {
                copied.put(normalizedName, copyParameterValues(values));
            }
        });
        return copied.isEmpty() ? Map.of() : Collections.unmodifiableMap(copied);
    }

    private static Map<String, List<String>> mergeParameters(Map<String, List<String>> first,
            Map<String, List<String>> second) {
        Map<String, List<String>> merged = new LinkedHashMap<>();
        mergeInto(merged, first);
        mergeInto(merged, second);
        return copyParameters(merged);
    }

    private static void mergeInto(Map<String, List<String>> target, Map<String, List<String>> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((name, values) -> {
            String normalizedName = normalizeOptional(name);
            if (normalizedName == null) {
                return;
            }
            List<String> targetValues = new ArrayList<>(target.getOrDefault(normalizedName, List.of()));
            List<String> coveredValues = new ArrayList<>(targetValues);
            for (String value : values == null || values.isEmpty() ? List.of("") : values) {
                String normalizedValue = value == null ? "" : value;
                int coveredIndex = coveredValues.indexOf(normalizedValue);
                if (coveredIndex >= 0) {
                    coveredValues.remove(coveredIndex);
                }
                else {
                    targetValues.add(normalizedValue);
                }
            }
            target.put(normalizedName, targetValues);
        });
    }

    private static List<String> copyParameterValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of("");
        }
        List<String> copied = new ArrayList<>(values.size());
        for (String value : values) {
            copied.add(value == null ? "" : value);
        }
        return List.copyOf(copied);
    }
}
