package io.github.coco.feature.web.trace;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.context.CocoRequestContextHolder;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import io.github.coco.logging.access.CocoAccessLog;
import io.github.coco.logging.access.CocoAccessLogRecorder;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CocoTraceFilterServletAsyncLifecycleTest {

    private static final String TRACE_ID = "abcdef0123456789abcdef0123456789";

    private static final String WORKER_MDC_KEY = "worker-context";

    private static final String WORKER_MDC_VALUE = "preserved";

    @AfterEach
    void clearThreadContext() {
        CocoRequestContextHolder.clear();
        MDC.clear();
    }

    @Test
    void completeClearsOwnedContextAndRecordsFinalStatusOnce() throws Exception {
        Fixture fixture = startAsyncRequest();
        fixture.response().setStatus(204);

        ContextView callbackThreadContext = completeOnWorkerThread(fixture.asyncContext(), fixture.properties());
        fixture.asyncContext().fireCompleteAgain();

        assertEmpty(callbackThreadContext);
        assertEquals(1, fixture.accessLogs().size());
        assertEquals(204, fixture.accessLogs().get(0).status());
        assertEquals(new ContextView(TRACE_ID, TRACE_ID, TRACE_ID), fixture.callbackContexts().get(0));
    }

    @Test
    void errorClearsOwnedContextAndRecordsFailureOnFinalComplete() throws Exception {
        Fixture fixture = startAsyncRequest();
        IllegalStateException failure = new IllegalStateException("async failed");

        fixture.asyncContext().fireError(failure);

        assertThreadContextCleared(fixture.properties());
        assertTrue(fixture.accessLogs().isEmpty());
        fixture.response().setStatus(502);
        fixture.asyncContext().complete();

        assertEquals(1, fixture.accessLogs().size());
        assertEquals(502, fixture.accessLogs().get(0).status());
        assertSame(failure, fixture.accessLogs().get(0).failure().orElseThrow());
        assertEquals(new ContextView(TRACE_ID, TRACE_ID, TRACE_ID), fixture.callbackContexts().get(0));
    }

    @Test
    void timeoutClearsOwnedContextAndRecordsTimeoutOnFinalComplete() throws Exception {
        Fixture fixture = startAsyncRequest();

        fixture.asyncContext().fireTimeout();

        assertThreadContextCleared(fixture.properties());
        assertTrue(fixture.accessLogs().isEmpty());
        fixture.response().setStatus(503);
        fixture.asyncContext().complete();

        assertEquals(1, fixture.accessLogs().size());
        CocoAccessLog accessLog = fixture.accessLogs().get(0);
        assertEquals(503, accessLog.status());
        assertEquals(TimeoutException.class, accessLog.failure().orElseThrow().getClass());
        assertEquals(new ContextView(TRACE_ID, TRACE_ID, TRACE_ID), fixture.callbackContexts().get(0));
    }

    @Test
    void restartedAsyncCyclePropagatesContextAndLeavesReusedWorkerClean() throws Exception {
        Fixture fixture = startAsyncRequest();
        ControllableAsyncContext restartedContext = fixture.asyncContext().startNextAsyncCycle();
        fixture.response().setStatus(206);

        ContextView callbackThreadContext = completeOnWorkerThread(restartedContext, fixture.properties());

        assertEmpty(callbackThreadContext);
        assertEquals(1, fixture.accessLogs().size());
        assertEquals(206, fixture.accessLogs().get(0).status());
        assertEquals(new ContextView(TRACE_ID, TRACE_ID, TRACE_ID), fixture.callbackContexts().get(0));
    }

    @Test
    void generatedTraceIdDoesNotLeakAcrossReusedRequestThread() throws Exception {
        CocoTraceProperties properties = new CocoTraceProperties();
        CocoTraceFilter filter = new CocoTraceFilter(properties);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            GeneratedTraceResult result = worker.submit(() -> {
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/generated-trace");
                MockHttpServletResponse response = new MockHttpServletResponse();
                AtomicReference<String> traceInChain = new AtomicReference<>();

                filter.doFilter(request, response, (servletRequest, servletResponse) ->
                        traceInChain.set(CocoTraceContext.currentTraceId().orElseThrow()));

                return new GeneratedTraceResult(traceInChain.get(), response.getHeader(properties.getHeaderName()),
                        currentContext(properties));
            }).get();

            assertNotNull(result.traceId());
            assertEquals(result.traceId(), result.responseTraceId());
            assertEmpty(result.threadContext());
            assertEmpty(worker.submit(() -> currentContext(properties)).get());
        }
        finally {
            worker.shutdownNow();
        }
    }

    @Test
    void listenerRegistrationRaceRecordsFailureAndCompletesExactlyOnce() throws Exception {
        Fixture fixture = startAsyncRequest();

        ControllableAsyncContext rejectedContext = fixture.asyncContext()
                .startNextAsyncCycleWithRejectedListenerRegistration();

        assertThreadContextCleared(fixture.properties());
        assertEquals(1, fixture.accessLogs().size());
        CocoAccessLog accessLog = fixture.accessLogs().get(0);
        assertEquals(500, accessLog.status());
        assertEquals(IllegalStateException.class, accessLog.failure().orElseThrow().getClass());
        assertEquals(new ContextView(TRACE_ID, TRACE_ID, TRACE_ID), fixture.callbackContexts().get(0));

        rejectedContext.complete();
        assertEquals(1, fixture.accessLogs().size());
    }

    private static Fixture startAsyncRequest() throws Exception {
        CocoTraceProperties properties = new CocoTraceProperties();
        CopyOnWriteArrayList<CocoAccessLog> accessLogs = new CopyOnWriteArrayList<>();
        CopyOnWriteArrayList<ContextView> callbackContexts = new CopyOnWriteArrayList<>();
        CocoAccessLogRecorder recorder = accessLog -> {
            callbackContexts.add(currentContext(properties));
            accessLogs.add(accessLog);
        };
        CocoTraceFilter filter = new CocoTraceFilter(properties, List.of(recorder),
                new CocoAccessLogCaptureProperties());
        MockHttpServletResponse response = new MockHttpServletResponse();
        ControllableAsyncRequest request = new ControllableAsyncRequest(response);
        request.addHeader(properties.getHeaderName(), TRACE_ID);

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                servletRequest.startAsync(servletRequest, servletResponse));

        assertThreadContextCleared(properties);
        assertTrue(accessLogs.isEmpty());
        return new Fixture(properties, response, request.controllableAsyncContext(), accessLogs, callbackContexts);
    }

    private static ContextView completeOnWorkerThread(ControllableAsyncContext asyncContext,
            CocoTraceProperties properties) throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            ContextView callbackThreadContext = worker.submit(() -> {
                MDC.put(WORKER_MDC_KEY, WORKER_MDC_VALUE);
                asyncContext.complete();
                assertEquals(WORKER_MDC_VALUE, MDC.get(WORKER_MDC_KEY));
                return currentContext(properties);
            }).get();
            ContextView reusedThreadContext = worker.submit(() -> {
                assertEquals(WORKER_MDC_VALUE, MDC.get(WORKER_MDC_KEY));
                return currentContext(properties);
            }).get();
            assertEmpty(reusedThreadContext);
            return callbackThreadContext;
        }
        finally {
            worker.shutdownNow();
        }
    }

    private static ContextView currentContext(CocoTraceProperties properties) {
        return new ContextView(CocoTraceContext.currentTraceId().orElse(null),
                CocoRequestContextHolder.current().map(context -> context.traceId()).orElse(null),
                MDC.get(properties.getMdcKey()));
    }

    private static void assertEmpty(ContextView context) {
        assertEquals(new ContextView(null, null, null), context);
    }

    private static void assertThreadContextCleared(CocoTraceProperties properties) {
        assertEmpty(currentContext(properties));
    }

    private record Fixture(CocoTraceProperties properties, MockHttpServletResponse response,
            ControllableAsyncContext asyncContext, CopyOnWriteArrayList<CocoAccessLog> accessLogs,
            CopyOnWriteArrayList<ContextView> callbackContexts) {
    }

    private record ContextView(String traceId, String requestTraceId, String mdcTraceId) {
    }

    private record GeneratedTraceResult(String traceId, String responseTraceId, ContextView threadContext) {
    }

    private static final class ControllableAsyncRequest extends MockHttpServletRequest {

        private ControllableAsyncContext asyncContext;

        private boolean asyncStarted;

        private ControllableAsyncRequest(MockHttpServletResponse response) {
            super("GET", "/async-lifecycle");
            setAsyncSupported(true);
            this.asyncContext = new ControllableAsyncContext(this, response);
        }

        @Override
        public AsyncContext startAsync() {
            return startAsync(this, this.asyncContext.response());
        }

        @Override
        public AsyncContext startAsync(ServletRequest request, ServletResponse response) {
            this.asyncStarted = true;
            this.asyncContext.setSuppliedRequestAndResponse(request, response);
            return this.asyncContext;
        }

        @Override
        public boolean isAsyncStarted() {
            return this.asyncStarted;
        }

        @Override
        public AsyncContext getAsyncContext() {
            if (!this.asyncStarted) {
                throw new IllegalStateException("Async processing has not started");
            }
            return this.asyncContext;
        }

        private ControllableAsyncContext controllableAsyncContext() {
            return this.asyncContext;
        }

        private void useAsyncContext(ControllableAsyncContext asyncContext) {
            this.asyncContext = asyncContext;
            this.asyncStarted = true;
        }

        private void markComplete() {
            this.asyncStarted = false;
        }
    }

    private static final class ControllableAsyncContext implements AsyncContext {

        private final ControllableAsyncRequest request;

        private final MockHttpServletResponse response;

        private final CopyOnWriteArrayList<ListenerRegistration> listeners = new CopyOnWriteArrayList<>();

        private ServletRequest suppliedRequest;

        private ServletResponse suppliedResponse;

        private long timeout;

        private boolean rejectListenerRegistration;

        private ControllableAsyncContext(ControllableAsyncRequest request, MockHttpServletResponse response) {
            this.request = request;
            this.response = response;
            this.suppliedRequest = request;
            this.suppliedResponse = response;
        }

        @Override
        public ServletRequest getRequest() {
            return this.suppliedRequest;
        }

        @Override
        public ServletResponse getResponse() {
            return this.suppliedResponse;
        }

        @Override
        public boolean hasOriginalRequestAndResponse() {
            return this.suppliedRequest == this.request && this.suppliedResponse == this.response;
        }

        @Override
        public void dispatch() {
            throw new UnsupportedOperationException("dispatch is not used by this fixture");
        }

        @Override
        public void dispatch(String path) {
            throw new UnsupportedOperationException("dispatch is not used by this fixture");
        }

        @Override
        public void dispatch(ServletContext context, String path) {
            throw new UnsupportedOperationException("dispatch is not used by this fixture");
        }

        @Override
        public void complete() {
            this.request.markComplete();
            notifyListeners(AsyncListener::onComplete, null);
        }

        private void fireCompleteAgain() {
            notifyListeners(AsyncListener::onComplete, null);
        }

        private void fireError(Throwable failure) {
            notifyListeners(AsyncListener::onError, failure);
        }

        private void fireTimeout() {
            notifyListeners(AsyncListener::onTimeout, null);
        }

        private ControllableAsyncContext startNextAsyncCycle() {
            return startNextAsyncCycle(false);
        }

        private ControllableAsyncContext startNextAsyncCycleWithRejectedListenerRegistration() {
            return startNextAsyncCycle(true);
        }

        private ControllableAsyncContext startNextAsyncCycle(boolean rejectListenerRegistration) {
            ControllableAsyncContext next = new ControllableAsyncContext(this.request, this.response);
            next.rejectListenerRegistration = rejectListenerRegistration;
            this.request.useAsyncContext(next);
            notifyListeners((listener, event) -> listener.onStartAsync(event), null,
                    new AsyncEvent(next, this.suppliedRequest, this.suppliedResponse));
            return next;
        }

        @Override
        public void start(Runnable runnable) {
            runnable.run();
        }

        @Override
        public void addListener(AsyncListener listener) {
            addListener(listener, this.suppliedRequest, this.suppliedResponse);
        }

        @Override
        public void addListener(AsyncListener listener, ServletRequest request, ServletResponse response) {
            if (this.rejectListenerRegistration) {
                throw new IllegalStateException("async listener registration is no longer allowed");
            }
            this.listeners.add(new ListenerRegistration(listener, request, response));
        }

        @Override
        public <T extends AsyncListener> T createListener(Class<T> listenerClass) throws ServletException {
            try {
                return listenerClass.getDeclaredConstructor().newInstance();
            }
            catch (ReflectiveOperationException ex) {
                throw new ServletException(ex);
            }
        }

        @Override
        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }

        @Override
        public long getTimeout() {
            return this.timeout;
        }

        private MockHttpServletResponse response() {
            return this.response;
        }

        private void setSuppliedRequestAndResponse(ServletRequest request, ServletResponse response) {
            this.suppliedRequest = request;
            this.suppliedResponse = response;
        }

        private void notifyListeners(ListenerCallback callback, Throwable failure) {
            notifyListeners(callback, failure, new AsyncEvent(this, this.suppliedRequest, this.suppliedResponse, failure));
        }

        private void notifyListeners(ListenerCallback callback, Throwable failure, AsyncEvent event) {
            for (ListenerRegistration registration : List.copyOf(this.listeners)) {
                try {
                    callback.invoke(registration.listener(), new AsyncEvent(event.getAsyncContext(), registration.request(),
                            registration.response(), failure));
                }
                catch (java.io.IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }
    }

    @FunctionalInterface
    private interface ListenerCallback {

        void invoke(AsyncListener listener, AsyncEvent event) throws java.io.IOException;
    }

    private record ListenerRegistration(AsyncListener listener, ServletRequest request, ServletResponse response) {
    }
}
