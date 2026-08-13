package io.github.coco.feature.concurrencylimit;

import java.io.IOException;
import java.util.Objects;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 在 Controller 和业务事务边界前申请在途请求并发许可的 Servlet 过滤器。
 */
public final class CocoConcurrencyLimitFilter extends OncePerRequestFilter {

    static final String FILTER_HANDLE_ATTRIBUTE = CocoConcurrencyLimitFilter.class.getName() + ".handle";

    private final CocoConcurrencyLimitRouteMatcher routeMatcher;

    private final CocoConcurrencyLimitRequestHandler requestHandler;

    /**
     * 创建并发限制过滤器。
     * @param routeMatcher 有序路由匹配器
     * @param requestHandler 并发许可请求执行器
     */
    public CocoConcurrencyLimitFilter(CocoConcurrencyLimitRouteMatcher routeMatcher,
            CocoConcurrencyLimitRequestHandler requestHandler) {
        this.routeMatcher = Objects.requireNonNull(routeMatcher, "routeMatcher must not be null");
        this.requestHandler = Objects.requireNonNull(requestHandler, "requestHandler must not be null");
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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        boolean asyncDispatch = asyncDispatch(request);
        if (asyncDispatch && CocoConcurrencyLimitHandle.asyncTrackingFailed(request)) {
            this.requestHandler.rejectAsyncDispatch(request, response);
            return;
        }
        Object existing = request.getAttribute(FILTER_HANDLE_ATTRIBUTE);
        if (existing instanceof CocoConcurrencyLimitHandle handle && handle.acquired() && !handle.released()) {
            if (asyncDispatch && handle.asyncTrackingFailed()) {
                this.requestHandler.rejectAsyncDispatch(request, response);
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        CocoConcurrencyLimitRoute route = this.routeMatcher.resolve(request).orElse(null);
        if (route == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (asyncDispatch) {
            CocoConcurrencyLimitAsyncPolicy asyncPolicy = this.requestHandler.asyncPolicy();
            if (asyncPolicy == CocoConcurrencyLimitAsyncPolicy.SKIP) {
                filterChain.doFilter(request, response);
                return;
            }
            if (asyncPolicy == CocoConcurrencyLimitAsyncPolicy.REJECT
                    && !this.requestHandler.handleAsyncDispatch(request, response)) {
                return;
            }
        }

        CocoConcurrencyLimitHandle handle = this.requestHandler.acquire(route, request, response);
        if (!handle.acquired()) {
            return;
        }

        boolean completed = false;
        boolean asyncBound = false;
        try {
            request.setAttribute(FILTER_HANDLE_ATTRIBUTE, handle);
            filterChain.doFilter(request, response);
            completed = true;
        }
        finally {
            if (completed && this.requestHandler.asyncPolicy() == CocoConcurrencyLimitAsyncPolicy.TRACK
                    && request.isAsyncStarted()) {
                asyncBound = handle.bindAsync(request, FILTER_HANDLE_ATTRIBUTE);
            }
            if (!asyncBound) {
                try {
                    handle.release();
                }
                finally {
                    CocoConcurrencyLimitHandle.removeAttribute(request, FILTER_HANDLE_ATTRIBUTE, handle);
                }
            }
        }
    }

    private static boolean asyncDispatch(HttpServletRequest request) {
        return request.getDispatcherType() == DispatcherType.ASYNC || request.isAsyncStarted();
    }
}
