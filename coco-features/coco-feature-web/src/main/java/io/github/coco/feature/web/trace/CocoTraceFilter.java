package io.github.coco.feature.web.trace;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.github.coco.common.context.CocoRequestContext;
import io.github.coco.common.context.CocoRequestContextHolder;
import io.github.coco.common.logging.access.CocoAccessLog;
import io.github.coco.common.logging.access.CocoAccessLogRecorder;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.CocoWebRequestSnapshotAttributes;
import io.github.coco.feature.web.context.DefaultCocoWebRequestContextResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
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

    private final boolean responseHeaderEnabled;

    private final boolean responseCookieEnabled;

    private final String cookieName;

    private final String cookiePath;

    private final int cookieMaxAge;

    private final boolean cookieHttpOnly;

    private final boolean cookieSecure;

    private final String cookieSameSite;

    private final List<CocoAccessLogRecorder> accessLogRecorders;

    private final CocoAccessLogCaptureProperties accessLogProperties;

    private final CocoWebRequestContextResolver requestContextResolver;

    private final CocoTraceIdValidator traceIdValidator;

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
        this(properties, accessLogRecorders, accessLogProperties,
                new DefaultCocoWebRequestContextResolver(null));
    }

    /**
     * <p>
     * 创建 Coco Web Trace 过滤器。
     * </p>
     * @param properties Trace 配置属性
     * @param accessLogRecorders 接口访问日志记录器集合
     * @param accessLogProperties 接口访问日志配置属性
     * @param requestContextResolver Web 请求上下文解析器
     */
    public CocoTraceFilter(CocoTraceProperties properties,
            Collection<CocoAccessLogRecorder> accessLogRecorders,
            CocoAccessLogCaptureProperties accessLogProperties,
            CocoWebRequestContextResolver requestContextResolver) {
        this(properties, accessLogRecorders, accessLogProperties, requestContextResolver, null);
    }

    /**
     * <p>
     * 创建 Coco Web Trace 过滤器。
     * </p>
     * @param properties Trace 配置属性
     * @param accessLogRecorders 接口访问日志记录器集合
     * @param accessLogProperties 接口访问日志配置属性
     * @param requestContextResolver Web 请求上下文解析器
     * @param traceIdValidator TraceId 校验器
     */
    public CocoTraceFilter(CocoTraceProperties properties,
            Collection<CocoAccessLogRecorder> accessLogRecorders,
            CocoAccessLogCaptureProperties accessLogProperties,
            CocoWebRequestContextResolver requestContextResolver,
            CocoTraceIdValidator traceIdValidator) {
        CocoTraceProperties checkedProperties = Objects.requireNonNull(properties, "properties must not be null");
        this.headerName = checkedProperties.getHeaderName();
        this.mdcKey = checkedProperties.getMdcKey();
        this.responseHeaderEnabled = checkedProperties.isResponseHeaderEnabled();
        this.responseCookieEnabled = checkedProperties.isResponseCookieEnabled();
        this.cookieName = checkedProperties.getCookieName();
        this.cookiePath = checkedProperties.getCookiePath();
        this.cookieMaxAge = checkedProperties.getCookieMaxAge();
        this.cookieHttpOnly = checkedProperties.isCookieHttpOnly();
        this.cookieSecure = checkedProperties.isCookieSecure();
        this.cookieSameSite = checkedProperties.getCookieSameSite();
        this.accessLogRecorders = accessLogRecorders == null ? List.of() : List.copyOf(accessLogRecorders);
        this.accessLogProperties = accessLogProperties == null
                ? new CocoAccessLogCaptureProperties()
                : accessLogProperties;
        this.requestContextResolver = Objects.requireNonNull(requestContextResolver,
                "requestContextResolver must not be null");
        this.traceIdValidator = traceIdValidator == null
                ? new DefaultCocoTraceIdValidator(checkedProperties)
                : traceIdValidator;
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
        CocoWebRequestSnapshot requestSnapshot = this.requestContextResolver.resolve(traceId, request);
        CocoRequestContext requestContext = requestSnapshot.toRequestContext();
        CocoRequestContextHolder.set(requestContext);
        MDC.put(this.mdcKey, requestSnapshot.traceId());
        writeTraceResponse(response, requestSnapshot.traceId());
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        }
        catch (IOException | ServletException | RuntimeException | Error ex) {
            failure = ex;
            throw ex;
        }
        finally {
            recordAccessLog(latestRequestSnapshot(request, requestSnapshot), response.getStatus(),
                    elapsedMillis(startNanos), failure);
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
        String candidateTraceId = requestTraceId.trim();
        if (this.traceIdValidator.isValid(candidateTraceId)) {
            return candidateTraceId;
        }
        return CocoTraceContext.getOrCreateTraceId();
    }

    /**
     * <p>
     * 按配置将 TraceId 写入响应通道。
     * </p>
     * @param response 当前 HTTP 响应
     * @param traceId 当前请求 TraceId
     */
    private void writeTraceResponse(HttpServletResponse response, String traceId) {
        if (this.responseHeaderEnabled) {
            response.setHeader(this.headerName, traceId);
        }
        if (this.responseCookieEnabled) {
            response.addHeader(HttpHeaders.SET_COOKIE, buildTraceCookie(traceId));
        }
    }

    private String buildTraceCookie(String traceId) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(this.cookieName, traceId)
                .path(this.cookiePath)
                .httpOnly(this.cookieHttpOnly)
                .secure(this.cookieSecure);
        if (this.cookieMaxAge >= 0) {
            builder.maxAge(this.cookieMaxAge);
        }
        if (this.cookieSameSite != null && !this.cookieSameSite.isBlank()) {
            builder.sameSite(this.cookieSameSite);
        }
        return builder.build().toString();
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
     * @param requestSnapshot 请求快照
     * @param status 响应状态码
     * @param durationMillis 请求耗时，单位毫秒
     * @param failure 请求处理异常；正常完成时为空
     */
    private void recordAccessLog(CocoWebRequestSnapshot requestSnapshot, int status,
            long durationMillis, Throwable failure) {
        if (!this.accessLogProperties.isEnabled() || this.accessLogRecorders.isEmpty()) {
            return;
        }
        CocoAccessLog accessLog = CocoAccessLog.of(requestSnapshot.traceId(),
                requestSnapshot.method(),
                requestSnapshot.path(),
                status,
                durationMillis,
                failure == null && status < 400,
                failure == null ? null : failure.getClass().getName(),
                requestSnapshot.clientIp(),
                requestSnapshot.clientIpResolution().source().name(),
                requestSnapshot.userAgent(),
                requestSnapshot.contentType(),
                requestSnapshot.queryString(),
                requestSnapshot.headers(),
                requestSnapshot.requestBody().effectiveSha256(),
                requestSnapshot.requestBody().effectiveLength(),
                requestSnapshot.requestBody().stage().id(),
                requestSnapshot.browserFingerprint().value(),
                requestSnapshot.payloadParseStatus().id(),
                requestSnapshot.targetResolution().source().name(),
                requestSnapshot.parameters())
                .withFailure(failure);
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
     * 返回当前请求上的最新请求快照。
     * </p>
     * <p>
     * 后续过滤器可能会刷新请求快照，例如 AES 解密后会将请求体从密文切换为业务可见的明文，因此访问日志应优先使用最新快照。
     * </p>
     * @param request 当前 HTTP 请求
     * @param fallbackSnapshot 兜底请求快照
     * @return 最新请求快照；不存在时返回兜底快照
     */
    private static CocoWebRequestSnapshot latestRequestSnapshot(HttpServletRequest request,
            CocoWebRequestSnapshot fallbackSnapshot) {
        return CocoWebRequestSnapshotAttributes.get(request).orElse(fallbackSnapshot);
    }
}
