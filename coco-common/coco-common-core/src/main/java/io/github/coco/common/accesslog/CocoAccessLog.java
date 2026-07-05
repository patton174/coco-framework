package io.github.coco.common.accesslog;

import java.util.Locale;
import java.util.Optional;

/**
 * Coco 接口访问日志。
 * <p>
 * 表示一次接口请求完成后的访问日志事件，保存 TraceId、HTTP 方法、请求路径、响应状态、耗时、成功标记和异常类型。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-core}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoAccessLog {

    private final String traceId;

    private final String method;

    private final String path;

    private final int status;

    private final long durationMillis;

    private final boolean success;

    private final String exceptionType;

    private CocoAccessLog(String traceId, String method, String path, int status, long durationMillis,
            boolean success, String exceptionType) {
        this.traceId = requireTraceId(traceId);
        this.method = normalizeMethod(method);
        this.path = normalizeOptional(path);
        this.status = status;
        this.durationMillis = requireDurationMillis(durationMillis);
        this.success = success;
        this.exceptionType = normalizeOptional(exceptionType);
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
        return new CocoAccessLog(traceId, method, path, status, durationMillis, success, exceptionType);
    }

    /**
     * <p>
     * 返回 TraceId。
     * </p>
     * @return TraceId
     */
    public String traceId() {
        return this.traceId;
    }

    /**
     * <p>
     * 返回 HTTP 方法。
     * </p>
     * @return HTTP 方法；未设置时为空
     */
    public Optional<String> method() {
        return Optional.ofNullable(this.method);
    }

    /**
     * <p>
     * 返回请求路径。
     * </p>
     * @return 请求路径；未设置时为空
     */
    public Optional<String> path() {
        return Optional.ofNullable(this.path);
    }

    /**
     * <p>
     * 返回响应状态码。
     * </p>
     * @return 响应状态码
     */
    public int status() {
        return this.status;
    }

    /**
     * <p>
     * 返回请求耗时，单位毫秒。
     * </p>
     * @return 请求耗时
     */
    public long durationMillis() {
        return this.durationMillis;
    }

    /**
     * <p>
     * 返回请求是否成功。
     * </p>
     * @return 成功时返回 {@code true}
     */
    public boolean success() {
        return this.success;
    }

    /**
     * <p>
     * 返回请求异常类型。
     * </p>
     * @return 异常类型；无异常时为空
     */
    public Optional<String> exceptionType() {
        return Optional.ofNullable(this.exceptionType);
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
}
