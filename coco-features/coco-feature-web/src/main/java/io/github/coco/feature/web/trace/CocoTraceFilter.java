package io.github.coco.feature.web.trace;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import io.github.coco.common.accesslog.CocoAccessLog;
import io.github.coco.common.accesslog.CocoAccessLogRecorder;
import io.github.coco.common.context.CocoRequestContext;
import io.github.coco.common.context.CocoRequestContextHolder;
import io.github.coco.common.trace.CocoTraceContext;
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
        CocoTraceProperties checkedProperties = Objects.requireNonNull(properties, "properties must not be null");
        this.headerName = checkedProperties.getHeaderName();
        this.mdcKey = checkedProperties.getMdcKey();
        this.accessLogRecorders = accessLogRecorders == null ? List.of() : List.copyOf(accessLogRecorders);
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
            recordAccessLog(requestContext, response.getStatus(), elapsedMillis(startNanos), failure);
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
    private void recordAccessLog(CocoRequestContext requestContext, int status, long durationMillis,
            Throwable failure) {
        if (this.accessLogRecorders.isEmpty()) {
            return;
        }
        CocoAccessLog accessLog = CocoAccessLog.of(requestContext.traceId(),
                requestContext.method().orElse(null),
                requestContext.path().orElse(null),
                status,
                durationMillis,
                failure == null && status < 400,
                failure == null ? null : failure.getClass().getName());
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
}
