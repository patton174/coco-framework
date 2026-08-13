package io.github.coco.feature.concurrencylimit;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.feature.web.context.CocoWebRequestMatchRule;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockAsyncContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CocoConcurrencyLimitAsyncTest {

    private CountingStore store;

    @AfterEach
    void closeStore() {
        if (this.store != null) {
            this.store.close();
        }
    }

    @Test
    void trackKeepsPermitUntilAsyncCompletion() throws Exception {
        TestFixture fixture = fixture(CocoConcurrencyLimitAsyncPolicy.TRACK);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAsyncSupported(true);

        fixture.filter.doFilter(request, response,
                (currentRequest, currentResponse) -> currentRequest.startAsync(currentRequest, currentResponse));

        assertThat(this.store.currentCount(routeConstraint())).isOne();
        request.getAsyncContext().complete();
        assertThat(this.store.activeEntries()).isZero();
    }

    @Test
    void trackRetainsPermitThroughTimeoutAndNewAsyncCycleUntilCompletion() throws Exception {
        TestFixture fixture = fixture(CocoConcurrencyLimitAsyncPolicy.TRACK);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAsyncSupported(true);

        TestAsyncContext asyncContext = startTrackedAsync(fixture, request, response);
        asyncContext.timeoutLifecycle();

        assertThat(this.store.currentCount(routeConstraint())).isOne();
        assertThat(this.store.releaseCalls()).isZero();
        TestAsyncContext nextContext = asyncContext.startNewAsyncCycle();
        assertThat(nextContext.listenerCount()).isOne();

        nextContext.completeLifecycle();

        assertThat(this.store.activeEntries()).isZero();
        assertThat(this.store.releaseCalls()).isOne();
    }

    @Test
    void trackRetainsPermitThroughErrorAndNewAsyncCycleUntilCompletion() throws Exception {
        TestFixture fixture = fixture(CocoConcurrencyLimitAsyncPolicy.TRACK);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAsyncSupported(true);

        TestAsyncContext asyncContext = startTrackedAsync(fixture, request, response);
        asyncContext.errorLifecycle(new IllegalStateException("application async error"));

        assertThat(this.store.currentCount(routeConstraint())).isOne();
        assertThat(this.store.releaseCalls()).isZero();
        TestAsyncContext nextContext = asyncContext.startNewAsyncCycle();
        assertThat(nextContext.listenerCount()).isOne();

        nextContext.completeLifecycle();

        assertThat(this.store.activeEntries()).isZero();
        assertThat(this.store.releaseCalls()).isOne();
    }

    @Test
    void trackRebindFailureCompletesAndReleasesBeforeRejectingAsyncRedispatch() throws Exception {
        TestFixture fixture = fixture(CocoConcurrencyLimitAsyncPolicy.TRACK);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAsyncSupported(true);

        TestAsyncContext asyncContext = startTrackedAsync(fixture, request, response);
        TestAsyncContext failedContext = asyncContext.startNewAsyncCycleWithBindingFailure();

        assertThat(failedContext.completeCalls()).isOne();
        assertThat(this.store.activeEntries()).isZero();
        assertThat(this.store.releaseCalls()).isOne();
        assertThat(request.getAttribute(CocoConcurrencyLimitFilter.FILTER_HANDLE_ATTRIBUTE)).isNull();
        assertThat(CocoConcurrencyLimitHandle.asyncTrackingFailed(request)).isTrue();

        request.setAsyncContext(failedContext);
        request.setDispatcherType(DispatcherType.ASYNC);
        AtomicBoolean invoked = new AtomicBoolean();

        fixture.filter.doFilter(request, response,
                (currentRequest, currentResponse) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(this.store.currentCount(routeConstraint())).isZero();
        assertThat(this.store.releaseCalls()).isOne();
    }

    @Test
    void trackRebindFailureStillReleasesWhenAsyncCompletionThrows() throws Exception {
        TestFixture fixture = fixture(CocoConcurrencyLimitAsyncPolicy.TRACK);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAsyncSupported(true);

        TestAsyncContext asyncContext = startTrackedAsync(fixture, request, response);
        TestAsyncContext failedContext = asyncContext.startNewAsyncCycleWithBindingAndCompletionFailure();

        assertThat(failedContext.completeCalls()).isOne();
        assertThat(this.store.activeEntries()).isZero();
        assertThat(this.store.releaseCalls()).isOne();
        assertThat(request.getAttribute(CocoConcurrencyLimitFilter.FILTER_HANDLE_ATTRIBUTE)).isNull();
        assertThat(CocoConcurrencyLimitHandle.asyncTrackingFailed(request)).isTrue();

        request.setAsyncContext(failedContext);
        request.setDispatcherType(DispatcherType.ASYNC);
        AtomicBoolean invoked = new AtomicBoolean();
        fixture.filter.doFilter(request, response,
                (currentRequest, currentResponse) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(this.store.releaseCalls()).isOne();
    }

    @Test
    void skipReleasesAtInitialDispatchReturnAndSkipsAsyncDispatch() throws Exception {
        TestFixture fixture = fixture(CocoConcurrencyLimitAsyncPolicy.SKIP);
        MockHttpServletRequest initialRequest = request();
        MockHttpServletResponse initialResponse = new MockHttpServletResponse();
        initialRequest.setAsyncSupported(true);

        fixture.filter.doFilter(initialRequest, initialResponse,
                (currentRequest, currentResponse) -> currentRequest.startAsync(currentRequest, currentResponse));

        assertThat(this.store.activeEntries()).isZero();

        MockHttpServletRequest asyncRequest = request();
        MockHttpServletResponse asyncResponse = new MockHttpServletResponse();
        MockAsyncContext asyncContext = new MockAsyncContext(asyncRequest, asyncResponse);
        asyncRequest.setAsyncContext(asyncContext);
        asyncRequest.setAsyncStarted(true);
        asyncRequest.setDispatcherType(DispatcherType.ASYNC);
        AtomicBoolean invoked = new AtomicBoolean();

        fixture.filter.doFilter(asyncRequest, asyncResponse,
                (currentRequest, currentResponse) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(this.store.activeEntries()).isZero();
    }

    @Test
    void rejectStopsExistingAsyncDispatchWithoutAcquiringPermit() throws Exception {
        TestFixture fixture = fixture(CocoConcurrencyLimitAsyncPolicy.REJECT);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAsyncContext(new MockAsyncContext(request, response));
        request.setAsyncStarted(true);
        request.setDispatcherType(DispatcherType.ASYNC);
        AtomicBoolean invoked = new AtomicBoolean();

        fixture.filter.doFilter(request, response,
                (currentRequest, currentResponse) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("coco.concurrency-limit.async-rejected");
        assertThat(this.store.activeEntries()).isZero();
    }

    @Test
    void trackBindingFailureDegradesToSynchronousRelease() throws Exception {
        TestFixture fixture = fixture(CocoConcurrencyLimitAsyncPolicy.TRACK);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders") {
            @Override
            public jakarta.servlet.AsyncContext getAsyncContext() {
                throw new IllegalStateException("async completed before listener binding");
            }
        };
        request.setRemoteAddr("198.51.100.10");
        request.setAsyncStarted(true);

        fixture.filter.doFilter(request, new MockHttpServletResponse(),
                (currentRequest, currentResponse) -> {
                });

        assertThat(this.store.activeEntries()).isZero();
    }

    private TestFixture fixture(CocoConcurrencyLimitAsyncPolicy policy) {
        CocoConcurrencyLimitProperties properties = new CocoConcurrencyLimitProperties();
        properties.setEnabled(true);
        properties.setAsyncPolicy(policy);
        CocoConcurrencyLimitRoute route = route();
        properties.setRoutes(List.of(route));
        this.store = new CountingStore(new InMemoryCocoConcurrencyLimitStore(properties));
        CocoConcurrencyLimitResponseWriter writer = (errorCode, request, response) -> {
            response.setStatus(properties.getResponse().getStatus());
            response.getWriter().write(errorCode.messageCode());
        };
        CocoConcurrencyLimitRequestHandler handler = new CocoConcurrencyLimitRequestHandler(properties,
                new DefaultCocoConcurrencyLimitKeyResolver(), this.store,
                (traceId, request) -> snapshot(traceId, request), writer);
        CocoConcurrencyLimitRouteMatcher matcher = new DefaultCocoConcurrencyLimitRouteMatcher(properties,
                new DefaultCocoWebRequestMatcher());
        return new TestFixture(new CocoConcurrencyLimitFilter(matcher, handler));
    }

    private static CocoConcurrencyLimitRoute route() {
        CocoConcurrencyLimitRoute route = new CocoConcurrencyLimitRoute();
        route.setId("orders");
        route.setLimit(1);
        CocoWebRequestMatchRule matcher = new CocoWebRequestMatchRule();
        matcher.setMethods(Set.of("POST"));
        matcher.setPathPatterns(Set.of("/orders"));
        route.setMatcher(matcher);
        return route;
    }

    private static CocoConcurrencyLimitConstraint routeConstraint() {
        return new CocoConcurrencyLimitConstraint(CocoConcurrencyLimitDimension.ROUTE, "orders", 1);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.setRemoteAddr("198.51.100.10");
        return request;
    }

    private static TestAsyncContext startTrackedAsync(TestFixture fixture, MockHttpServletRequest request,
            MockHttpServletResponse response) throws Exception {
        AtomicReference<TestAsyncContext> asyncContext = new AtomicReference<>();
        fixture.filter.doFilter(request, response, (currentRequest, currentResponse) -> {
            TestAsyncContext context = new TestAsyncContext(currentRequest, currentResponse);
            MockHttpServletRequest currentHttpRequest = (MockHttpServletRequest) currentRequest;
            currentHttpRequest.setAsyncContext(context);
            currentHttpRequest.setAsyncStarted(true);
            asyncContext.set(context);
        });
        return asyncContext.get();
    }

    private static CocoWebRequestSnapshot snapshot(String traceId, jakarta.servlet.http.HttpServletRequest request) {
        return new CocoWebRequestSnapshot(traceId, request.getMethod(), request.getRequestURI(), null,
                request.getRemoteAddr(), null, null, null, null, null, null, Map.of(), Map.of());
    }

    private record TestFixture(CocoConcurrencyLimitFilter filter) {
    }

    private static final class CountingStore implements CocoConcurrencyLimitStore {

        private final InMemoryCocoConcurrencyLimitStore delegate;

        private int releaseCalls;

        private CountingStore(InMemoryCocoConcurrencyLimitStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public CocoConcurrencyLimitAcquisition acquire(CocoConcurrencyLimitRequest request) {
            return this.delegate.acquire(request);
        }

        @Override
        public void release(CocoConcurrencyLimitPermit permit) {
            this.releaseCalls++;
            this.delegate.release(permit);
        }

        private int currentCount(CocoConcurrencyLimitConstraint constraint) {
            return this.delegate.currentCount(constraint);
        }

        private int activeEntries() {
            return this.delegate.activeEntries();
        }

        private int releaseCalls() {
            return this.releaseCalls;
        }

        @Override
        public void close() {
            this.delegate.close();
        }
    }
}
