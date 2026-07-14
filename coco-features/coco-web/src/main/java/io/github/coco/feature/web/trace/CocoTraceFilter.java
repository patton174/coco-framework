package io.github.coco.feature.web.trace;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.context.CocoContextScope;
import io.github.coco.context.CocoContextSnapshot;
import io.github.coco.context.CocoContextSnapshotRegistry;
import io.github.coco.context.CocoRequestContext;
import io.github.coco.context.CocoRequestContextHolder;
import io.github.coco.logging.access.CocoAccessLog;
import io.github.coco.logging.access.CocoAccessLogRecorder;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.CocoWebRequestSnapshotAttributes;
import io.github.coco.feature.web.context.DefaultCocoWebRequestContextResolver;
import io.github.coco.logging.context.CocoMdcContext;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncUtils;
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
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoTraceFilter extends OncePerRequestFilter {

    private static final String ASYNC_CONTEXT_INTERCEPTOR_KEY = CocoTraceFilter.class.getName() + ".context";

    private static final String REQUEST_STATE_ATTRIBUTE = CocoTraceFilter.class.getName() + ".state";

    private static final String ASYNC_CONTEXT_SNAPSHOT_KEY = CocoTraceFilter.class.getName();

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
        Optional<CocoRequestContext> previousRequestContext = CocoRequestContextHolder.current();
        Optional<String> previousTraceId = CocoTraceContext.currentTraceId();
        String previousMdcValue = MDC.get(this.mdcKey);
        RequestState state = requestState(request);
        CocoWebRequestSnapshot requestSnapshot = state.requestSnapshot();
        CocoRequestContext requestContext = requestSnapshot.toRequestContext();
        CocoRequestContextHolder.set(requestContext);
        MDC.put(this.mdcKey, requestSnapshot.traceId());
        if (request.getDispatcherType() == DispatcherType.REQUEST) {
            registerAsyncContextInterceptor(request);
        }
        writeTraceResponse(response, requestSnapshot.traceId());
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        }
        catch (IOException | ServletException | RuntimeException | Error ex) {
            failure = ex;
            state.recordFailure(ex);
            throw ex;
        }
        finally {
            try {
                finishDispatch(request, response, state, failure);
            }
            finally {
                restoreMdcValue(previousMdcValue);
                restoreRequestContext(previousRequestContext, previousTraceId);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        doFilterInternal(request, response, filterChain);
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

    private static void restoreRequestContext(Optional<CocoRequestContext> previousRequestContext,
            Optional<String> previousTraceId) {
        if (previousRequestContext.isPresent()) {
            CocoRequestContextHolder.set(previousRequestContext.get());
            return;
        }
        CocoRequestContextHolder.clear();
        previousTraceId.ifPresent(CocoTraceContext::setTraceId);
    }

    private RequestState requestState(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_STATE_ATTRIBUTE);
        if (existing instanceof RequestState state) {
            return state;
        }
        String traceId = resolveTraceId(request);
        CocoWebRequestSnapshot requestSnapshot = this.requestContextResolver.resolve(traceId, request);
        RequestState state = new RequestState(System.nanoTime(), requestSnapshot);
        request.setAttribute(REQUEST_STATE_ATTRIBUTE, state);
        return state;
    }

    private void finishDispatch(HttpServletRequest request, HttpServletResponse response,
            RequestState state, Throwable dispatchFailure) {
        if (request.isAsyncStarted()) {
            registerAsyncListener(request, response, state);
            return;
        }
        if (dispatchFailure != null && request.getDispatcherType() != DispatcherType.ERROR) {
            state.awaitErrorDispatch();
        }
        completeRequest(request, response, state);
    }

    private void registerAsyncListener(HttpServletRequest request, HttpServletResponse response,
            RequestState state) {
        AsyncContext asyncContext = request.getAsyncContext();
        if (!state.registerAsyncContext(asyncContext)) {
            return;
        }
        try {
            asyncContext.addListener(new TraceAsyncListener(request, response, state, captureAsyncContext(request)));
        }
        catch (IllegalStateException ex) {
            state.recordFailure(ex);
            completeRequest(request, response, state);
        }
    }

    private void completeRequest(HttpServletRequest request, HttpServletResponse response, RequestState state) {
        if (!state.complete()) {
            clearRequestState(request);
            return;
        }
        Throwable failure = state.failure();
        int status = response.getStatus();
        if (failure != null && status < 400) {
            status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        recordAccessLog(latestRequestSnapshot(request, state.requestSnapshot()), status,
                elapsedMillis(state.startNanos()), failure);
        if (!state.isAwaitingErrorDispatch() || request.getDispatcherType() == DispatcherType.ERROR) {
            clearRequestState(request);
        }
    }

    private static void registerAsyncContextInterceptor(HttpServletRequest request) {
        CocoContextSnapshot contextSnapshot = CocoContextSnapshot.compose(
                CocoRequestContextHolder.capture(), CocoMdcContext.capture());
        asyncContextSnapshots(request).register(ASYNC_CONTEXT_SNAPSHOT_KEY, contextSnapshot);
        WebAsyncUtils.getAsyncManager(request).registerCallableInterceptor(
                ASYNC_CONTEXT_INTERCEPTOR_KEY, new ContextCallableProcessingInterceptor(contextSnapshot));
    }

    private static CocoContextSnapshot captureAsyncContext(HttpServletRequest request) {
        return asyncContextSnapshots(request).snapshot();
    }

    private static CocoContextSnapshotRegistry asyncContextSnapshots(HttpServletRequest request) {
        String attributeName = CocoContextSnapshotRegistry.class.getName();
        Object existing = request.getAttribute(attributeName);
        if (existing instanceof CocoContextSnapshotRegistry registry) {
            return registry;
        }
        CocoContextSnapshotRegistry registry = new CocoContextSnapshotRegistry();
        request.setAttribute(attributeName, registry);
        return registry;
    }

    private static void clearRequestState(HttpServletRequest request) {
        request.removeAttribute(REQUEST_STATE_ATTRIBUTE);
        request.removeAttribute(CocoContextSnapshotRegistry.class.getName());
    }

    private static final class ContextCallableProcessingInterceptor implements CallableProcessingInterceptor {

        private final CocoContextSnapshot contextSnapshot;

        private final ThreadLocal<CocoContextScope> activeScope = new ThreadLocal<>();

        private ContextCallableProcessingInterceptor(CocoContextSnapshot contextSnapshot) {
            this.contextSnapshot = contextSnapshot;
        }

        @Override
        public <T> void preProcess(NativeWebRequest request, Callable<T> task) {
            closeActiveScope();
            this.activeScope.set(this.contextSnapshot.restore());
        }

        @Override
        public <T> void postProcess(NativeWebRequest request, Callable<T> task, Object concurrentResult) {
            closeActiveScope();
        }

        @Override
        public <T> void afterCompletion(NativeWebRequest request, Callable<T> task) {
            closeActiveScope();
        }

        private void closeActiveScope() {
            CocoContextScope scope = this.activeScope.get();
            if (scope == null) {
                return;
            }
            this.activeScope.remove();
            scope.close();
        }
    }

    private final class TraceAsyncListener implements AsyncListener {

        private final HttpServletRequest request;

        private final HttpServletResponse response;

        private final RequestState state;

        private final CocoContextSnapshot contextSnapshot;

        private TraceAsyncListener(HttpServletRequest request, HttpServletResponse response, RequestState state,
                CocoContextSnapshot contextSnapshot) {
            this.request = request;
            this.response = response;
            this.state = state;
            this.contextSnapshot = contextSnapshot;
        }

        @Override
        public void onComplete(AsyncEvent event) {
            withRequestContext(() -> completeRequest(eventRequest(event), eventResponse(event), this.state));
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            withRequestContext(() -> this.state.recordFailure(new TimeoutException("Servlet async request timed out")));
        }

        @Override
        public void onError(AsyncEvent event) {
            withRequestContext(() -> this.state.recordFailure(event.getThrowable()));
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            withRequestContext(() -> {
                AsyncContext asyncContext = event.getAsyncContext();
                if (this.state.registerAsyncContext(asyncContext)) {
                    try {
                        asyncContext.addListener(new TraceAsyncListener(this.request, this.response, this.state,
                                this.contextSnapshot));
                    }
                    catch (IllegalStateException ex) {
                        this.state.recordFailure(ex);
                        completeRequest(eventRequest(event), eventResponse(event), this.state);
                    }
                }
            });
        }

        private void withRequestContext(Runnable action) {
            try (CocoContextScope ignored = this.contextSnapshot.restore()) {
                action.run();
            }
        }

        private HttpServletRequest eventRequest(AsyncEvent event) {
            return event.getSuppliedRequest() instanceof HttpServletRequest suppliedRequest
                    ? suppliedRequest
                    : this.request;
        }

        private HttpServletResponse eventResponse(AsyncEvent event) {
            return event.getSuppliedResponse() instanceof HttpServletResponse suppliedResponse
                    ? suppliedResponse
                    : this.response;
        }
    }

    private static final class RequestState {

        private final long startNanos;

        private final CocoWebRequestSnapshot requestSnapshot;

        private final AtomicReference<Throwable> failure = new AtomicReference<>();

        private final AtomicReference<AsyncContext> registeredAsyncContext = new AtomicReference<>();

        private final AtomicBoolean completed = new AtomicBoolean();

        private final AtomicBoolean awaitingErrorDispatch = new AtomicBoolean();

        private RequestState(long startNanos, CocoWebRequestSnapshot requestSnapshot) {
            this.startNanos = startNanos;
            this.requestSnapshot = requestSnapshot;
        }

        private long startNanos() {
            return this.startNanos;
        }

        private CocoWebRequestSnapshot requestSnapshot() {
            return this.requestSnapshot;
        }

        private void recordFailure(Throwable failure) {
            if (failure != null) {
                this.failure.compareAndSet(null, failure);
            }
        }

        private Throwable failure() {
            return this.failure.get();
        }

        private boolean registerAsyncContext(AsyncContext asyncContext) {
            return this.registeredAsyncContext.getAndSet(asyncContext) != asyncContext;
        }

        private void awaitErrorDispatch() {
            this.awaitingErrorDispatch.set(true);
        }

        private boolean isAwaitingErrorDispatch() {
            return this.awaitingErrorDispatch.get();
        }

        private boolean complete() {
            return this.completed.compareAndSet(false, true);
        }
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
