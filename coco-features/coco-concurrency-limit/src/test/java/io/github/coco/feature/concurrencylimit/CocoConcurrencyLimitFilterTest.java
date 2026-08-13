package io.github.coco.feature.concurrencylimit;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.coco.feature.web.context.CocoWebRequestMatchRule;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CocoConcurrencyLimitFilterTest {

    private InMemoryCocoConcurrencyLimitStore store;

    @AfterEach
    void closeStore() {
        if (this.store != null) {
            this.store.close();
        }
    }

    @Test
    void releasesAllDimensionsAfterNormalFilterCompletion() throws Exception {
        TestFixture fixture = fixture(CocoConcurrencyLimitAsyncPolicy.TRACK);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        fixture.filter.doFilter(request(), response, (request, currentResponse) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(this.store.activeEntries()).isZero();
        assertThat(response.getHeader("X-Concurrency-Limit-Global")).isEqualTo("2");
        assertThat(response.getHeader("X-Concurrency-Limit-Route")).isEqualTo("1");
        assertThat(response.getHeader("X-Concurrency-Limit-Key")).isEqualTo("1");
    }

    @Test
    void releasesAllDimensionsWhenDownstreamThrows() {
        TestFixture fixture = fixture(CocoConcurrencyLimitAsyncPolicy.TRACK);

        assertThatThrownBy(() -> fixture.filter.doFilter(request(), new MockHttpServletResponse(),
                (request, response) -> {
                    throw new ServletException("boom");
                }))
                .isInstanceOf(ServletException.class)
                .hasMessage("boom");
        assertThat(this.store.activeEntries()).isZero();
    }

    @Test
    void rejectsSecondInFlightRequestAndWritesConfiguredHeaders() throws Exception {
        TestFixture fixture = fixture(CocoConcurrencyLimitAsyncPolicy.TRACK);
        CocoConcurrencyLimitHandle held = fixture.handler.acquire(fixture.route, request(),
                new MockHttpServletResponse());
        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        try {
            fixture.filter.doFilter(request(), rejectedResponse,
                    (request, response) -> invoked.set(true));

            assertThat(invoked).isFalse();
            assertThat(rejectedResponse.getStatus()).isEqualTo(429);
            assertThat(rejectedResponse.getHeader("X-Concurrency-Rejected-Dimension")).isEqualTo("route");
            assertThat(rejectedResponse.getHeader("Retry-After")).isEqualTo("2");
            assertThat(rejectedResponse.getHeader("Cache-Control")).isEqualTo("no-store");
            assertThat(rejectedResponse.getContentAsString()).contains("coco.concurrency-limit.rejected");
        }
        finally {
            held.release();
        }
        assertThat(this.store.activeEntries()).isZero();
    }

    @Test
    void resolverFailureRejectsWithoutAcquiringAnyPermit() throws Exception {
        TestFixture fixture = fixture(CocoConcurrencyLimitAsyncPolicy.TRACK,
                (snapshot, route) -> {
                    throw new IllegalStateException("resolver unavailable");
                });
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(request(), response, (request, currentResponse) -> {
            throw new AssertionError("downstream must not run");
        });

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("coco.concurrency-limit.unavailable");
        assertThat(this.store.activeEntries()).isZero();
    }

    private TestFixture fixture(CocoConcurrencyLimitAsyncPolicy asyncPolicy) {
        return fixture(asyncPolicy, new DefaultCocoConcurrencyLimitKeyResolver());
    }

    private TestFixture fixture(CocoConcurrencyLimitAsyncPolicy asyncPolicy,
            CocoConcurrencyLimitKeyResolver keyResolver) {
        CocoConcurrencyLimitProperties properties = new CocoConcurrencyLimitProperties();
        properties.setEnabled(true);
        properties.setGlobalLimit(2);
        properties.setAsyncPolicy(asyncPolicy);
        properties.getResponse().setRetryAfterSeconds(2);
        properties.getResponse().setHeaders(Map.of("Cache-Control", "no-store"));
        CocoConcurrencyLimitRoute route = route();
        properties.setRoutes(List.of(route));
        this.store = new InMemoryCocoConcurrencyLimitStore(properties);
        CocoConcurrencyLimitResponseWriter writer = (errorCode, request, response) -> {
            response.setStatus(properties.getResponse().getStatus());
            response.getWriter().write(errorCode.messageCode());
        };
        CocoConcurrencyLimitRequestHandler handler = new CocoConcurrencyLimitRequestHandler(properties,
                keyResolver, this.store, (traceId, request) -> snapshot(traceId, request), writer);
        CocoConcurrencyLimitRouteMatcher matcher = new DefaultCocoConcurrencyLimitRouteMatcher(properties,
                new DefaultCocoWebRequestMatcher());
        return new TestFixture(route, handler, new CocoConcurrencyLimitFilter(matcher, handler));
    }

    private static CocoConcurrencyLimitRoute route() {
        CocoConcurrencyLimitRoute route = new CocoConcurrencyLimitRoute();
        route.setId("orders");
        route.setLimit(1);
        route.setKeyLimit(1);
        CocoWebRequestMatchRule matcher = new CocoWebRequestMatchRule();
        matcher.setMethods(Set.of("POST"));
        matcher.setPathPatterns(Set.of("/orders"));
        route.setMatcher(matcher);
        return route;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.setRemoteAddr("198.51.100.10");
        return request;
    }

    private static CocoWebRequestSnapshot snapshot(String traceId, jakarta.servlet.http.HttpServletRequest request) {
        return new CocoWebRequestSnapshot(traceId, request.getMethod(), request.getRequestURI(), null,
                request.getRemoteAddr(), null, null, null, null, null, null, Map.of(), Map.of());
    }

    private record TestFixture(CocoConcurrencyLimitRoute route, CocoConcurrencyLimitRequestHandler handler,
            CocoConcurrencyLimitFilter filter) {
    }
}
