package io.github.coco.common.logging.access;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Coco 接口访问日志。
 * <p>
 * 表示一次接口请求完成后的访问日志事件，保存 TraceId、HTTP 方法、请求路径、请求头、客户端信息、请求参数、
 * 响应状态、耗时、成功标记、异常类型、异常对象，以及请求体和安全快照的摘要字段。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoAccessLog {

    private final String traceId;

    private final String method;

    private final String path;

    private final String clientIp;

    private final String clientIpSource;

    private final String userAgent;

    private final String contentType;

    private final String queryString;

    private final Map<String, String> headers;

    private final String requestBodySha256;

    private final Long requestBodyLength;

    private final String requestBodyStage;

    private final String browserFingerprint;

    private final String payloadParseStatus;

    private final String requestTargetSource;

    private final Map<String, List<String>> requestParameters;

    private final int status;

    private final long durationMillis;

    private final boolean success;

    private final String exceptionType;

    private final Throwable failure;

    private CocoAccessLog(String traceId, String method, String path, int status, long durationMillis,
            boolean success, String exceptionType, String clientIp, String clientIpSource, String userAgent,
            String contentType, String queryString, Map<String, String> headers, String requestBodySha256,
            Long requestBodyLength, String requestBodyStage, String browserFingerprint,
            String payloadParseStatus, String requestTargetSource, Map<String, List<String>> requestParameters,
            Throwable failure) {
        this.traceId = requireTraceId(traceId);
        this.method = normalizeMethod(method);
        this.path = normalizeOptional(path);
        this.clientIp = normalizeOptional(clientIp);
        this.clientIpSource = normalizeOptional(clientIpSource);
        this.userAgent = normalizeOptional(userAgent);
        this.contentType = normalizeOptional(contentType);
        this.queryString = normalizeOptional(queryString);
        this.headers = normalizeHeaders(headers);
        this.requestBodySha256 = normalizeOptional(requestBodySha256);
        this.requestBodyLength = requestBodyLength == null || requestBodyLength < 0L ? null : requestBodyLength;
        this.requestBodyStage = normalizeOptional(requestBodyStage);
        this.browserFingerprint = normalizeOptional(browserFingerprint);
        this.payloadParseStatus = normalizeOptional(payloadParseStatus);
        this.requestTargetSource = normalizeOptional(requestTargetSource);
        this.requestParameters = normalizeParameters(requestParameters);
        this.status = status;
        this.durationMillis = requireDurationMillis(durationMillis);
        this.success = success;
        this.exceptionType = normalizeExceptionType(exceptionType, failure);
        this.failure = failure;
    }

    /**
     * <p>
     * 创建接口访问日志事件。
     * </p>
     * @param traceId TraceId
     * @param method HTTP 方法
     * @param path 请求路径
     * @param status 响应状态码
     * @param durationMillis 请求耗时，单位毫秒
     * @param success 是否成功
     * @param exceptionType 异常类型
     * @return 接口访问日志事件
     */
    public static CocoAccessLog of(String traceId, String method, String path, int status,
            long durationMillis, boolean success, String exceptionType) {
        return new CocoAccessLog(traceId, method, path, status, durationMillis, success, exceptionType,
                null, null, null, null, null, Map.of(), null, null, null, null, null, null, Map.of(), null);
    }

    /**
     * <p>
     * 创建携带请求明细的接口访问日志事件。
     * </p>
     * @param traceId TraceId
     * @param method HTTP 方法
     * @param path 请求路径
     * @param status 响应状态码
     * @param durationMillis 请求耗时，单位毫秒
     * @param success 是否成功
     * @param exceptionType 异常类型
     * @param clientIp 客户端 IP
     * @param userAgent User-Agent
     * @param queryString 原始查询字符串
     * @param requestParameters 请求参数
     * @return 接口访问日志事件
     */
    public static CocoAccessLog of(String traceId, String method, String path, int status,
            long durationMillis, boolean success, String exceptionType, String clientIp, String userAgent,
            String queryString, Map<String, List<String>> requestParameters) {
        return new CocoAccessLog(traceId, method, path, status, durationMillis, success, exceptionType,
                clientIp, null, userAgent, null, queryString, Map.of(), null, null, null, null, null, null,
                requestParameters, null);
    }

    /**
     * <p>
     * 创建携带完整结构化快照的接口访问日志事件。
     * </p>
     * @param traceId TraceId
     * @param method HTTP 方法
     * @param path 请求路径
     * @param status 响应状态码
     * @param durationMillis 请求耗时，单位毫秒
     * @param success 是否成功
     * @param exceptionType 异常类型
     * @param clientIp 客户端 IP
     * @param clientIpSource 客户端 IP 来源
     * @param userAgent User-Agent
     * @param contentType 请求内容类型
     * @param queryString 原始查询字符串
     * @param headers 请求头快照
     * @param requestBodySha256 请求体 SHA-256
     * @param requestBodyLength 请求体长度
     * @param requestBodyStage 请求体阶段
     * @param browserFingerprint 浏览器指纹
     * @param payloadParseStatus 请求体参数解析状态
     * @param requestTargetSource 请求目标来源
     * @param requestParameters 请求参数
     * @return 接口访问日志事件
     */
    public static CocoAccessLog of(String traceId, String method, String path, int status,
            long durationMillis, boolean success, String exceptionType, String clientIp, String clientIpSource,
            String userAgent, String contentType, String queryString, Map<String, String> headers,
            String requestBodySha256, Long requestBodyLength, String requestBodyStage, String browserFingerprint,
            String payloadParseStatus, String requestTargetSource, Map<String, List<String>> requestParameters) {
        return new CocoAccessLog(traceId, method, path, status, durationMillis, success, exceptionType,
                clientIp, clientIpSource, userAgent, contentType, queryString, headers, requestBodySha256,
                requestBodyLength, requestBodyStage, browserFingerprint, payloadParseStatus, requestTargetSource,
                requestParameters, null);
    }

    /**
     * <p>
     * 返回携带异常对象的新访问日志事件。
     * </p>
     * <p>
     * 访问日志对象保持不可变；需要把真实异常交给日志输出器时，通过该方法生成带异常的新事件。
     * </p>
     * @param failure 请求处理异常；没有异常时为空
     * @return 携带异常对象的新访问日志事件
     */
    public CocoAccessLog withFailure(Throwable failure) {
        return new CocoAccessLog(this.traceId, this.method, this.path, this.status, this.durationMillis, this.success,
                this.exceptionType, this.clientIp, this.clientIpSource, this.userAgent, this.contentType,
                this.queryString, this.headers, this.requestBodySha256, this.requestBodyLength,
                this.requestBodyStage, this.browserFingerprint, this.payloadParseStatus, this.requestTargetSource,
                this.requestParameters, failure);
    }

    public String traceId() {
        return this.traceId;
    }

    public Optional<String> method() {
        return Optional.ofNullable(this.method);
    }

    public Optional<String> path() {
        return Optional.ofNullable(this.path);
    }

    public Optional<String> clientIp() {
        return Optional.ofNullable(this.clientIp);
    }

    public Optional<String> clientIpSource() {
        return Optional.ofNullable(this.clientIpSource);
    }

    public Optional<String> userAgent() {
        return Optional.ofNullable(this.userAgent);
    }

    public Optional<String> contentType() {
        return Optional.ofNullable(this.contentType);
    }

    public Optional<String> queryString() {
        return Optional.ofNullable(this.queryString);
    }

    public Map<String, String> headers() {
        return this.headers;
    }

    public Optional<String> requestBodySha256() {
        return Optional.ofNullable(this.requestBodySha256);
    }

    public Optional<Long> requestBodyLength() {
        return Optional.ofNullable(this.requestBodyLength);
    }

    public Optional<String> requestBodyStage() {
        return Optional.ofNullable(this.requestBodyStage);
    }

    public Optional<String> browserFingerprint() {
        return Optional.ofNullable(this.browserFingerprint);
    }

    public Optional<String> payloadParseStatus() {
        return Optional.ofNullable(this.payloadParseStatus);
    }

    public Optional<String> requestTargetSource() {
        return Optional.ofNullable(this.requestTargetSource);
    }

    public Map<String, List<String>> requestParameters() {
        return this.requestParameters;
    }

    public int status() {
        return this.status;
    }

    public long durationMillis() {
        return this.durationMillis;
    }

    public boolean success() {
        return this.success;
    }

    public Optional<String> exceptionType() {
        return Optional.ofNullable(this.exceptionType);
    }

    /**
     * <p>
     * 返回请求处理异常。
     * </p>
     * @return 请求处理异常；没有异常时为空
     */
    public Optional<Throwable> failure() {
        return Optional.ofNullable(this.failure);
    }

    private static String requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        return traceId.trim();
    }

    private static long requireDurationMillis(long durationMillis) {
        if (durationMillis < 0L) {
            throw new IllegalArgumentException("durationMillis must not be negative");
        }
        return durationMillis;
    }

    private static String normalizeMethod(String method) {
        String normalized = normalizeOptional(method);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeExceptionType(String exceptionType, Throwable failure) {
        String normalizedExceptionType = normalizeOptional(exceptionType);
        if (normalizedExceptionType != null) {
            return normalizedExceptionType;
        }
        return failure == null ? null : failure.getClass().getName();
    }

    private static Map<String, String> normalizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        headers.forEach((name, value) -> {
            String normalizedName = normalizeOptional(name);
            String normalizedValue = normalizeOptional(value);
            if (normalizedName != null && normalizedValue != null) {
                normalized.put(normalizedName.toLowerCase(Locale.ROOT), normalizedValue);
            }
        });
        return Collections.unmodifiableMap(normalized);
    }

    private static Map<String, List<String>> normalizeParameters(Map<String, List<String>> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        parameters.forEach((name, values) -> {
            String normalizedName = normalizeOptional(name);
            if (normalizedName == null) {
                return;
            }
            normalized.put(normalizedName, normalizeParameterValues(values));
        });
        return Collections.unmodifiableMap(normalized);
    }

    private static List<String> normalizeParameterValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of("");
        }
        List<String> normalized = new ArrayList<>(values.size());
        for (String value : values) {
            normalized.add(value == null || value.isBlank() ? "" : value.trim());
        }
        return List.copyOf(normalized);
    }
}
