package io.github.coco.feature.web.trace;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.github.coco.common.context.CocoRequestContext;
import io.github.coco.common.context.CocoRequestContextHolder;
import io.github.coco.common.logging.access.CocoAccessLog;
import io.github.coco.common.logging.access.CocoAccessLogRecorder;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Coco Web Trace 过滤器。
 * <p>
 * 从请求头读取 TraceId；请求头缺失时自动生成 TraceId；将 TraceId 写入 {@link CocoTraceContext} 和响应头，并在请求结束后清理上下文。
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
public final class CocoTraceFilter extends OncePerRequestFilter {

    private final String headerName;

    private final String mdcKey;

    private final List<CocoAccessLogRecorder> accessLogRecorders;

    private final CocoAccessLogCaptureProperties accessLogProperties;

    /**
     * <p>
     * 创建 Coco Web Trace 过滤器。
     * </p>
     * @param properties Trace 配置属性
     */
    public CocoTraceFilter(CocoTraceProperties properties) {
        this(properties, List.of());
    }

    /**
     * <p>
     * 创建 Coco Web Trace 过滤器。
     * </p>
     * @param properties Trace 配置属性
     * @param accessLogRecorders 接口访问日志记录器集合
     */
    public CocoTraceFilter(CocoTraceProperties properties,
            Collection<CocoAccessLogRecorder> accessLogRecorders) {
        this(properties, accessLogRecorders, new CocoAccessLogCaptureProperties());
    }

    /**
     * <p>
     * 创建 Coco Web Trace 过滤器。
     * </p>
     * @param properties Trace 配置属性
     * @param accessLogRecorders 接口访问日志记录器集合
     * @param accessLogProperties 接口访问日志配置属性
     */
    public CocoTraceFilter(CocoTraceProperties properties,
            Collection<CocoAccessLogRecorder> accessLogRecorders,
            CocoAccessLogCaptureProperties accessLogProperties) {
        CocoTraceProperties checkedProperties = Objects.requireNonNull(properties, "properties must not be null");
        this.headerName = checkedProperties.getHeaderName();
        this.mdcKey = checkedProperties.getMdcKey();
        this.accessLogRecorders = accessLogRecorders == null ? List.of() : List.copyOf(accessLogRecorders);
        this.accessLogProperties = accessLogProperties == null
                ? new CocoAccessLogCaptureProperties()
                : accessLogProperties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        String traceId = resolveTraceId(request);
        String previousMdcValue = MDC.get(this.mdcKey);
        CocoRequestContext requestContext = CocoRequestContext.of(traceId, request.getMethod(), resolvePath(request));
        CocoRequestContextHolder.set(requestContext);
        MDC.put(this.mdcKey, traceId);
        response.setHeader(this.headerName, traceId);
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        }
        catch (IOException | ServletException | RuntimeException | Error ex) {
            failure = ex;
            throw ex;
        }
        finally {
            recordAccessLog(requestContext, request, response.getStatus(), elapsedMillis(startNanos), failure);
            restoreMdcValue(previousMdcValue);
            CocoRequestContextHolder.clear();
        }
    }

    /**
     * <p>
     * 解析当前请求的 TraceId。
     * </p>
     * <p>
     * 优先读取配置的 HTTP 头；当请求头缺失或为空白时，创建新的 TraceId。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 可写入上下文和响应头的 TraceId
     */
    private String resolveTraceId(HttpServletRequest request) {
        String requestTraceId = request.getHeader(this.headerName);
        if (requestTraceId == null || requestTraceId.isBlank()) {
            return CocoTraceContext.getOrCreateTraceId();
        }
        return requestTraceId.trim();
    }

    private static String resolvePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri == null || requestUri.isBlank() ? null : requestUri;
    }

    private void restoreMdcValue(String previousMdcValue) {
        if (previousMdcValue == null) {
            MDC.remove(this.mdcKey);
            return;
        }
        MDC.put(this.mdcKey, previousMdcValue);
    }

    /**
     * <p>
     * 记录当前请求的接口访问日志。
     * </p>
     * <p>
     * 访问日志是旁路基础设施能力，记录器异常不会中断业务请求的收尾流程。
     * </p>
     * @param requestContext 请求上下文
     * @param status 响应状态码
     * @param durationMillis 请求耗时，单位毫秒
     * @param failure 请求处理异常；正常完成时为空
     */
    private void recordAccessLog(CocoRequestContext requestContext, HttpServletRequest request, int status,
            long durationMillis, Throwable failure) {
        if (!this.accessLogProperties.isEnabled() || this.accessLogRecorders.isEmpty()) {
            return;
        }
        CocoAccessLog accessLog = CocoAccessLog.of(requestContext.traceId(),
                requestContext.method().orElse(null),
                requestContext.path().orElse(null),
                status,
                durationMillis,
                failure == null && status < 400,
                failure == null ? null : failure.getClass().getName(),
                resolveClientIp(request),
                request.getHeader("User-Agent"),
                resolveQueryString(request),
                resolveRequestParameters(request));
        for (CocoAccessLogRecorder recorder : this.accessLogRecorders) {
            try {
                recorder.record(accessLog);
            }
            catch (RuntimeException ex) {
                // 访问日志属于旁路能力，记录失败不能中断业务请求收尾。
            }
        }
    }

    /**
     * <p>
     * 计算从开始时间到当前时间的毫秒耗时。
     * </p>
     * @param startNanos 开始时间，来自 {@link System#nanoTime()}
     * @return 非负毫秒耗时
     */
    private static long elapsedMillis(long startNanos) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
    }

    /**
     * <p>
     * 解析客户端 IP。
     * </p>
     * <p>
     * 优先读取反向代理常用头，缺失时回退到 Servlet 容器提供的远端地址。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 客户端 IP
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = firstHeaderValue(request, "X-Forwarded-For");
        if (forwardedFor != null) {
            return forwardedFor;
        }
        String realIp = firstHeaderValue(request, "X-Real-IP");
        if (realIp != null) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    /**
     * <p>
     * 返回逗号分隔请求头中的第一个有效值。
     * </p>
     * @param request 当前 HTTP 请求
     * @param headerName 请求头名称
     * @return 第一个有效值；请求头缺失时为空
     */
    private static String firstHeaderValue(HttpServletRequest request, String headerName) {
        String headerValue = request.getHeader(headerName);
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        return Arrays.stream(headerValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * <p>
     * 解析并清洗查询字符串。
     * </p>
     * <p>
     * 当访问日志关闭参数采集时不记录查询字符串；敏感参数会在输出前完成掩码。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 清洗后的查询字符串；无查询字符串时为空
     */
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

    /**
     * <p>
     * 清洗查询字符串中的单个键值对。
     * </p>
     * @param pair 查询字符串键值对
     * @return 清洗后的键值对
     */
    private String sanitizeQueryPair(String pair) {
        int separatorIndex = pair.indexOf('=');
        String name = separatorIndex < 0 ? pair : pair.substring(0, separatorIndex);
        String value = separatorIndex < 0 ? "" : pair.substring(separatorIndex + 1);
        if (isMaskedParameterName(name)) {
            return name + "=******";
        }
        return separatorIndex < 0 ? trimValue(name) : name + "=" + trimValue(value);
    }

    /**
     * <p>
     * 解析并清洗 Servlet 请求参数。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 清洗后的请求参数快照
     */
    private Map<String, List<String>> resolveRequestParameters(HttpServletRequest request) {
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

    /**
     * <p>
     * 清洗单个请求参数值。
     * </p>
     * @param name 参数名
     * @param value 参数值
     * @return 清洗后的参数值
     */
    private String sanitizeParameterValue(String name, String value) {
        if (isMaskedParameterName(name)) {
            return "******";
        }
        return trimValue(value);
    }

    /**
     * <p>
     * 判断请求参数名是否需要掩码。
     * </p>
     * @param name 参数名
     * @return 需要掩码时返回 {@code true}
     */
    private boolean isMaskedParameterName(String name) {
        return name != null && this.accessLogProperties.getMaskedParameterNames()
                .contains(name.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * <p>
     * 裁剪请求参数值。
     * </p>
     * @param value 参数值
     * @return 裁剪后的参数值
     */
    private String trimValue(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        int maxLength = this.accessLogProperties.getMaxParameterValueLength();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength) + "...";
    }
}
