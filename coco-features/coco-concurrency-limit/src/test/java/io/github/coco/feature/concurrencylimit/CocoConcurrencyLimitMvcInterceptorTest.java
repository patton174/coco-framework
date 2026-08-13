package io.github.coco.feature.concurrencylimit;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CocoConcurrencyLimitMvcInterceptorTest {

    private CountingStore store;

    @AfterEach
    void closeStore() {
        if (this.store != null) {
            this.store.close();
        }
    }

    @Test
    void methodAnnotationOverridesClassAndAfterCompletionReleases() throws Exception {
        TestFixture fixture = fixture();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/annotation");
        HandlerMethod handlerMethod = handlerMethod("methodRoute");

        assertThat(fixture.interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod)).isTrue();
        assertThat(this.store.currentCount(routeConstraint("method-route"))).isOne();
        assertThat(this.store.currentCount(routeConstraint("class-route"))).isZero();

        fixture.interceptor.afterCompletion(request, new MockHttpServletResponse(), handlerMethod,
                new IllegalStateException("controller failure"));

        assertThat(this.store.activeEntries()).isZero();
    }

    @Test
    void unknownAnnotationRouteFailsBeforeAnyAcquire() throws Exception {
        TestFixture fixture = fixture();

        assertThatThrownBy(() -> fixture.interceptor.preHandle(
                new MockHttpServletRequest("GET", "/annotation"), new MockHttpServletResponse(),
                handlerMethod("unknownRoute")))
                .isInstanceOf(ServletException.class)
                .hasMessageContaining("unknown-route");
        assertThat(this.store.activeEntries()).isZero();
    }

    @Test
    void annotationOnlyTrackTimeoutRedispatchReusesPermitAndReleasesOnceAtCompletion() throws Exception {
        assertAnnotationOnlyTrackRedispatchRetainsPermit(TestAsyncContext::timeoutLifecycle);
    }

    @Test
    void annotationOnlyTrackErrorRedispatchReusesPermitAndReleasesOnceAtCompletion() throws Exception {
        assertAnnotationOnlyTrackRedispatchRetainsPermit(
                context -> context.errorLifecycle(new IllegalStateException("application async error")));
    }

    @Test
    void annotationOnlyTrackRebindFailureReleasesAndRejectsRedispatchWithoutDoubleRelease() throws Exception {
        TestFixture fixture = fixture();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/annotation");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = handlerMethod("methodRoute");

        assertThat(fixture.interceptor.preHandle(request, response, handlerMethod)).isTrue();
        TestAsyncContext asyncContext = new TestAsyncContext(request, response);
        request.setAsyncSupported(true);
        request.setAsyncContext(asyncContext);
        request.setAsyncStarted(true);
        fixture.interceptor.afterConcurrentHandlingStarted(request, response, handlerMethod);

        TestAsyncContext failedContext = asyncContext.startNewAsyncCycleWithBindingFailure();
        assertThat(failedContext.completeCalls()).isOne();
        assertThat(this.store.currentCount(routeConstraint("method-route"))).isZero();
        assertThat(this.store.releaseCalls()).isOne();
        assertThat(request.getAttribute(CocoConcurrencyLimitMvcInterceptor.MVC_HANDLE_ATTRIBUTE)).isNull();
        assertThat(CocoConcurrencyLimitHandle.asyncTrackingFailed(request)).isTrue();

        fixture.interceptor.afterCompletion(request, response, handlerMethod, null);
        assertThat(this.store.releaseCalls()).isOne();

        request.setAsyncContext(failedContext);
        request.setDispatcherType(DispatcherType.ASYNC);

        assertThat(fixture.interceptor.preHandle(request, response, handlerMethod)).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(this.store.currentCount(routeConstraint("method-route"))).isZero();
        assertThat(this.store.releaseCalls()).isOne();

        fixture.interceptor.afterCompletion(request, response, handlerMethod, null);
        assertThat(this.store.releaseCalls()).isOne();
    }

    private void assertAnnotationOnlyTrackRedispatchRetainsPermit(AsyncLifecycleSignal signal) throws Exception {
        TestFixture fixture = fixture();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/annotation");
        MockHttpServletResponse response = new MockHttpServletResponse();
        HandlerMethod handlerMethod = handlerMethod("methodRoute");

        assertThat(fixture.interceptor.preHandle(request, response, handlerMethod)).isTrue();
        assertThat(this.store.currentCount(routeConstraint("method-route"))).isOne();

        TestAsyncContext asyncContext = new TestAsyncContext(request, response);
        request.setAsyncSupported(true);
        request.setAsyncContext(asyncContext);
        request.setAsyncStarted(true);
        fixture.interceptor.afterConcurrentHandlingStarted(request, response, handlerMethod);
        assertThat(asyncContext.listenerCount()).isOne();

        signal.fire(asyncContext);
        assertThat(this.store.currentCount(routeConstraint("method-route"))).isOne();
        assertThat(this.store.releaseCalls()).isZero();

        request.setDispatcherType(DispatcherType.ASYNC);
        assertThat(fixture.interceptor.preHandle(request, response, handlerMethod)).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(this.store.currentCount(routeConstraint("method-route"))).isOne();
        assertThat(this.store.releaseCalls()).isZero();

        TestAsyncContext nextContext = asyncContext.startNewAsyncCycle();
        assertThat(nextContext.listenerCount()).isOne();
        fixture.interceptor.afterCompletion(request, response, handlerMethod, null);
        assertThat(this.store.currentCount(routeConstraint("method-route"))).isOne();
        assertThat(this.store.releaseCalls()).isZero();

        nextContext.completeLifecycle();

        assertThat(this.store.activeEntries()).isZero();
        assertThat(this.store.releaseCalls()).isOne();
    }

    private TestFixture fixture() {
        CocoConcurrencyLimitProperties properties = new CocoConcurrencyLimitProperties();
        properties.setRoutes(List.of(route("class-route"), route("method-route")));
        this.store = new CountingStore(new InMemoryCocoConcurrencyLimitStore(properties));
        CocoConcurrencyLimitResponseWriter writer = (errorCode, request, response) -> response.setStatus(429);
        CocoConcurrencyLimitRequestHandler handler = new CocoConcurrencyLimitRequestHandler(properties,
                new DefaultCocoConcurrencyLimitKeyResolver(), this.store,
                (traceId, request) -> snapshot(traceId, request), writer);
        CocoConcurrencyLimitRouteMatcher matcher = new DefaultCocoConcurrencyLimitRouteMatcher(properties,
                new DefaultCocoWebRequestMatcher());
        return new TestFixture(new CocoConcurrencyLimitMvcInterceptor(matcher, handler));
    }

    private static HandlerMethod handlerMethod(String name) throws Exception {
        AnnotationController controller = new AnnotationController();
        Method method = AnnotationController.class.getDeclaredMethod(name);
        return new HandlerMethod(controller, method);
    }

    private static CocoConcurrencyLimitRoute route(String id) {
        CocoConcurrencyLimitRoute route = new CocoConcurrencyLimitRoute();
        route.setId(id);
        route.setLimit(1);
        return route;
    }

    private static CocoConcurrencyLimitConstraint routeConstraint(String id) {
        return new CocoConcurrencyLimitConstraint(CocoConcurrencyLimitDimension.ROUTE, id, 1);
    }

    private static CocoWebRequestSnapshot snapshot(String traceId, jakarta.servlet.http.HttpServletRequest request) {
        return new CocoWebRequestSnapshot(traceId, request.getMethod(), request.getRequestURI(), null,
                request.getRemoteAddr(), null, null, null, null, null, null, Map.of(), Map.of());
    }

    private record TestFixture(CocoConcurrencyLimitMvcInterceptor interceptor) {
    }

    @FunctionalInterface
    private interface AsyncLifecycleSignal {

        void fire(TestAsyncContext context) throws IOException;
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

    @CocoConcurrencyLimited("class-route")
    static class AnnotationController {

        @CocoConcurrencyLimited("method-route")
        void methodRoute() {
        }

        @CocoConcurrencyLimited("unknown-route")
        void unknownRoute() {
        }
    }
}
