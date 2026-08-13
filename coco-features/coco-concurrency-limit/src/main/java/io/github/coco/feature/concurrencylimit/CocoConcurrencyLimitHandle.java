package io.github.coco.feature.concurrencylimit;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 模块内部的 release-once 许可生命周期句柄。
 */
public final class CocoConcurrencyLimitHandle {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoConcurrencyLimitHandle.class);

    private static final String ASYNC_TRACKING_FAILED_ATTRIBUTE = CocoConcurrencyLimitHandle.class.getName()
            + ".asyncTrackingFailed";

    private final CocoConcurrencyLimitStore store;

    private final CocoConcurrencyLimitPermit permit;

    private final AtomicBoolean released = new AtomicBoolean();

    private final AtomicBoolean asyncTrackingFailed = new AtomicBoolean();

    private final AtomicBoolean asyncListenerBound = new AtomicBoolean();

    private CocoConcurrencyLimitHandle(CocoConcurrencyLimitStore store, CocoConcurrencyLimitPermit permit) {
        this.store = store;
        this.permit = permit;
    }

    static CocoConcurrencyLimitHandle acquired(CocoConcurrencyLimitStore store,
            CocoConcurrencyLimitPermit permit) {
        return new CocoConcurrencyLimitHandle(Objects.requireNonNull(store, "store must not be null"),
                Objects.requireNonNull(permit, "permit must not be null"));
    }

    static CocoConcurrencyLimitHandle rejected() {
        return new CocoConcurrencyLimitHandle(null, null);
    }

    /**
     * 返回当前句柄是否持有成功申请的许可。
     * @return 是否持有许可
     */
    public boolean acquired() {
        return this.permit != null;
    }

    /**
     * 返回当前句柄是否已经释放。
     * @return 是否已经释放
     */
    public boolean released() {
        return this.released.get();
    }

    boolean asyncTrackingFailed() {
        return this.asyncTrackingFailed.get();
    }

    static boolean asyncTrackingFailed(HttpServletRequest request) {
        return Boolean.TRUE.equals(request.getAttribute(ASYNC_TRACKING_FAILED_ATTRIBUTE));
    }

    boolean asyncListenerBound() {
        return this.asyncListenerBound.get();
    }

    /**
     * 释放句柄持有的许可；重复调用不会重复释放存储计数。
     */
    public void release() {
        if (!acquired() || !this.released.compareAndSet(false, true)) {
            return;
        }
        this.store.release(this.permit);
    }

    boolean bindAsync(HttpServletRequest request, String attributeName) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(attributeName, "attributeName must not be null");
        if (!acquired() || released() || !request.isAsyncStarted()) {
            return false;
        }
        try {
            AsyncContext asyncContext = request.getAsyncContext();
            asyncContext.addListener(new PermitAsyncListener(this, request, attributeName));
            this.asyncListenerBound.set(true);
            return true;
        }
        catch (IllegalStateException | UnsupportedOperationException exception) {
            LOGGER.warn("Coco concurrency-limit could not bind AsyncListener; releasing permit at dispatch return",
                    exception);
            return false;
        }
    }

    private static final class PermitAsyncListener implements AsyncListener {

        private final CocoConcurrencyLimitHandle handle;

        private final HttpServletRequest request;

        private final String attributeName;

        private PermitAsyncListener(CocoConcurrencyLimitHandle handle, HttpServletRequest request,
                String attributeName) {
            this.handle = handle;
            this.request = request;
            this.attributeName = attributeName;
        }

        @Override
        public void onComplete(AsyncEvent event) {
            release(event);
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            // A timeout can be followed by dispatch() or startAsync(); ownership ends only at onComplete().
        }

        @Override
        public void onError(AsyncEvent event) {
            // An error can be followed by dispatch() or startAsync(); ownership ends only at onComplete().
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            if (this.handle.released()) {
                return;
            }
            try {
                event.getAsyncContext().addListener(this);
            }
            catch (IllegalStateException | UnsupportedOperationException exception) {
                failAsyncTracking(event, exception);
            }
        }

        private void failAsyncTracking(AsyncEvent event, RuntimeException rebindFailure) {
            HttpServletRequest effectiveRequest = request(event);
            this.handle.asyncTrackingFailed.set(true);
            markAsyncTrackingFailed(effectiveRequest);
            if (effectiveRequest != this.request) {
                markAsyncTrackingFailed(this.request);
            }
            LOGGER.error("Coco concurrency-limit could not rebind AsyncListener; completing the async context and "
                    + "releasing its permit", rebindFailure);
            try {
                event.getAsyncContext().complete();
            }
            catch (RuntimeException completeFailure) {
                LOGGER.error("Coco concurrency-limit could not complete an untrackable async context", completeFailure);
            }
            finally {
                release(event);
            }
        }

        private void release(AsyncEvent event) {
            HttpServletRequest effectiveRequest = request(event);
            try {
                this.handle.release();
            }
            catch (RuntimeException exception) {
                LOGGER.error("Coco concurrency-limit failed to release an async permit", exception);
            }
            finally {
                removeAttribute(effectiveRequest, this.attributeName, this.handle);
                if (effectiveRequest != this.request) {
                    removeAttribute(this.request, this.attributeName, this.handle);
                }
            }
        }

        private static void markAsyncTrackingFailed(HttpServletRequest request) {
            request.setAttribute(ASYNC_TRACKING_FAILED_ATTRIBUTE, Boolean.TRUE);
        }

        private HttpServletRequest request(AsyncEvent event) {
            ServletRequest suppliedRequest = event == null ? null : event.getSuppliedRequest();
            return suppliedRequest instanceof HttpServletRequest httpRequest ? httpRequest : this.request;
        }
    }

    static void removeAttribute(HttpServletRequest request, String attributeName,
            CocoConcurrencyLimitHandle handle) {
        if (request.getAttribute(attributeName) == handle) {
            request.removeAttribute(attributeName);
        }
    }
}
