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
 * @author patton174
 * @since 1.0.0
 */
public record CocoWebRequestSecurityInput(String method, String path, String queryString,
        Map<String, List<String>> parameters, Map<String, String> securityHeaders,
        Map<String, String> canonicalHeaders, String bodySha256, Long bodyLength, boolean bodyCached) {

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
     */
    public CocoWebRequestSecurityInput {
        method = normalizeMethod(method);
        path = normalizeOptional(path);
        queryString = normalizeOptional(queryString);
        parameters = copyParameters(parameters);
        securityHeaders = copyHeaders(securityHeaders);
        canonicalHeaders = copyHeaders(canonicalHeaders);
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
     * 返回指定规范化请求头。
     * </p>
     * @param name 请求头名称
     * @return 请求头值；未设置时为空
     */
    public Optional<String> canonicalHeader(String name) {
        return header(this.canonicalHeaders, name);
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
