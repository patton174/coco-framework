package io.github.coco.feature.concurrencylimit;

import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

/**
 * 仅在路径过滤器未命中时处理 {@link CocoConcurrencyLimited} 的 Spring MVC 后备拦截器。
 */
public final class CocoConcurrencyLimitMvcInterceptor implements AsyncHandlerInterceptor {

    static final String MVC_HANDLE_ATTRIBUTE = CocoConcurrencyLimitMvcInterceptor.class.getName() + ".handle";

    private final CocoConcurrencyLimitRouteMatcher routeMatcher;

    private final CocoConcurrencyLimitRequestHandler requestHandler;

    /**
     * 创建 MVC 注解后备拦截器。
     * @param routeMatcher 有序路由匹配器
     * @param requestHandler 并发许可请求执行器
     */
    public CocoConcurrencyLimitMvcInterceptor(CocoConcurrencyLimitRouteMatcher routeMatcher,
            CocoConcurrencyLimitRequestHandler requestHandler) {
        this.routeMatcher = Objects.requireNonNull(routeMatcher, "routeMatcher must not be null");
        this.requestHandler = Objects.requireNonNull(requestHandler, "requestHandler must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressFBWarnings(value = "THROWS_METHOD_THROWS_RUNTIMEEXCEPTION",
            justification = "Attribute binding failures release the acquired permit before preserving the failure")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        boolean asyncDispatch = asyncDispatch(request);
        if (asyncDispatch && CocoConcurrencyLimitHandle.asyncTrackingFailed(request)) {
            return this.requestHandler.rejectAsyncDispatch(request, response);
        }
        CocoConcurrencyLimitHandle mvcHandle = activeHandle(request.getAttribute(MVC_HANDLE_ATTRIBUTE));
        if (asyncDispatch && mvcHandle != null) {
            return !mvcHandle.asyncTrackingFailed() || this.requestHandler.rejectAsyncDispatch(request, response);
        }
        CocoConcurrencyLimitHandle filterHandle = activeHandle(
                request.getAttribute(CocoConcurrencyLimitFilter.FILTER_HANDLE_ATTRIBUTE));
        if (filterHandle != null) {
            return !asyncDispatch || !filterHandle.asyncTrackingFailed()
                    || this.requestHandler.rejectAsyncDispatch(request, response);
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        CocoConcurrencyLimited annotation = resolveAnnotation(handlerMethod);
        if (annotation == null) {
            return true;
        }
        CocoConcurrencyLimitRoute route = this.routeMatcher.resolve(annotation.route()).orElse(null);
        if (route == null) {
            throw new ServletException("@CocoConcurrencyLimited references an unknown route: "
                    + annotation.route());
        }
        if (asyncDispatch) {
            CocoConcurrencyLimitAsyncPolicy asyncPolicy = this.requestHandler.asyncPolicy();
            if (asyncPolicy == CocoConcurrencyLimitAsyncPolicy.SKIP) {
                return true;
            }
            if (asyncPolicy == CocoConcurrencyLimitAsyncPolicy.REJECT) {
                return this.requestHandler.handleAsyncDispatch(request, response);
            }
        }
        CocoConcurrencyLimitHandle handle = this.requestHandler.acquire(route, request, response);
        if (!handle.acquired()) {
            return false;
        }
        try {
            request.setAttribute(MVC_HANDLE_ATTRIBUTE, handle);
            return true;
        }
        catch (RuntimeException exception) {
            handle.release();
            throw exception;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception exception) {
        Object value = request.getAttribute(MVC_HANDLE_ATTRIBUTE);
        if (value instanceof CocoConcurrencyLimitHandle handle && handle.asyncListenerBound()) {
            return;
        }
        release(request);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response,
            Object handler) {
        Object value = request.getAttribute(MVC_HANDLE_ATTRIBUTE);
        if (!(value instanceof CocoConcurrencyLimitHandle handle) || !handle.acquired()) {
            return;
        }
        boolean asyncBound = this.requestHandler.asyncPolicy() == CocoConcurrencyLimitAsyncPolicy.TRACK
                && handle.bindAsync(request, MVC_HANDLE_ATTRIBUTE);
        if (!asyncBound) {
            release(request);
        }
    }

    private static CocoConcurrencyLimited resolveAnnotation(HandlerMethod handlerMethod) {
        CocoConcurrencyLimited methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), CocoConcurrencyLimited.class);
        return methodAnnotation != null ? methodAnnotation
                : AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(),
                        CocoConcurrencyLimited.class);
    }

    private static void release(HttpServletRequest request) {
        Object value = request.getAttribute(MVC_HANDLE_ATTRIBUTE);
        if (!(value instanceof CocoConcurrencyLimitHandle handle)) {
            return;
        }
        try {
            handle.release();
        }
        finally {
            CocoConcurrencyLimitHandle.removeAttribute(request, MVC_HANDLE_ATTRIBUTE, handle);
        }
    }

    private static CocoConcurrencyLimitHandle activeHandle(Object value) {
        return value instanceof CocoConcurrencyLimitHandle handle && handle.acquired() && !handle.released()
                ? handle : null;
    }

    private static boolean asyncDispatch(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ASYNC || request.isAsyncStarted();
    }
}
