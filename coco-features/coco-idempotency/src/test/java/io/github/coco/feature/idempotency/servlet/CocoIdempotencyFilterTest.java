package io.github.coco.feature.idempotency.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.feature.idempotency.CocoIdempotencyProperties;
import io.github.coco.feature.idempotency.CocoIdempotencyRouteMatcher;
import io.github.coco.feature.idempotency.CocoIdempotencyScopeResolver;
import io.github.coco.feature.idempotency.CocoIdempotencyExceptionHttpStatusResolver;
import io.github.coco.feature.idempotency.store.CocoIdempotencyAcquireResult;
import io.github.coco.feature.idempotency.store.CocoIdempotencyLease;
import io.github.coco.feature.idempotency.store.CocoIdempotencyRequest;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStore;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStoredResponse;
import io.github.coco.feature.idempotency.store.InMemoryCocoIdempotencyStore;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.exception.DefaultCocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.response.CocoSystemCodes;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CocoIdempotencyFilterTest {

    private static final Instant NOW = Instant.parse("2026-08-08T08:00:00Z");

    @AfterEach
    void clearSecurityContext() {
        CocoSecurityContextHolder.clear();
    }

    @Test
    void completedResponseIsReplayedWithoutExecutingBusinessTwice() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        try (TestFixture fixture = fixture(properties -> { }, request -> "caller-a")) {
            MockHttpServletResponse firstResponse = new MockHttpServletResponse();
            fixture.filter.doFilter(request("same-key", "{\"order\":1}"), firstResponse,
                    (request, response) -> {
                        executions.incrementAndGet();
                        assertThat(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                                .isEqualTo("{\"order\":1}");
                        HttpServletResponse servletResponse = (HttpServletResponse) response;
                        servletResponse.setStatus(201);
                        servletResponse.setHeader("X-Result", "created");
                        servletResponse.getWriter().write("{\"id\":42}");
                    });

            MockHttpServletResponse replayResponse = new MockHttpServletResponse();
            fixture.filter.doFilter(request("same-key", "{\"order\":1}"), replayResponse,
                    (request, response) -> executions.incrementAndGet());

            assertThat(executions).hasValue(1);
            assertThat(firstResponse.getStatus()).isEqualTo(201);
            assertThat(replayResponse.getStatus()).isEqualTo(201);
            assertThat(replayResponse.getHeader("X-Result")).isEqualTo("created");
            assertThat(replayResponse.getContentAsByteArray()).isEqualTo(firstResponse.getContentAsByteArray());
        }
    }

    @Test
    void sameKeyIsIsolatedAcrossAuthenticatedPrincipals() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        CocoIdempotencyScopeResolver verifiedContext = request -> CocoSecurityContextHolder.current()
                .filter(CocoSecurityContext::authenticated)
                .map(context -> context.principal().principalId())
                .orElse(null);
        try (TestFixture fixture = fixture(properties -> { }, verifiedContext)) {
            authenticate("principal-a");
            MockHttpServletResponse first = new MockHttpServletResponse();
            fixture.filter.doFilter(request("shared-key", "same"), first,
                    (request, response) -> {
                        executions.incrementAndGet();
                        response.getWriter().write("response-a");
                    });

            authenticate("principal-b");
            MockHttpServletResponse second = new MockHttpServletResponse();
            fixture.filter.doFilter(request("shared-key", "same"), second,
                    (request, response) -> {
                        executions.incrementAndGet();
                        response.getWriter().write("response-b");
                    });

            authenticate("principal-a");
            MockHttpServletResponse replay = new MockHttpServletResponse();
            fixture.filter.doFilter(request("shared-key", "same"), replay,
                    (request, response) -> executions.incrementAndGet());

            assertThat(executions).hasValue(2);
            assertThat(first.getContentAsString()).isEqualTo("response-a");
            assertThat(second.getContentAsString()).isEqualTo("response-b");
            assertThat(replay.getContentAsString()).isEqualTo("response-a");
        }
    }

    @Test
    void missingScopeFailsClosedWithUnifiedCocoError() throws Exception {
        try (TestFixture fixture = fixture(properties -> { }, request -> null)) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicInteger executions = new AtomicInteger();

            fixture.filter.doFilter(request("scope-key", "same"), response,
                    (request, servletResponse) -> executions.incrementAndGet());

            assertThat(executions).hasValue(0);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentType()).startsWith("application/json");
            assertThat(response.getContentAsString())
                    .contains("COCO_IDEMPOTENCY_SCOPE_REQUIRED")
                    .doesNotContain("scope-key");
        }
    }

    @Test
    void sameKeyWithDifferentPayloadIsRejectedByUnifiedWriter() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        try (TestFixture fixture = fixture(properties -> { }, request -> "caller-a")) {
            fixture.filter.doFilter(request("bound-key", "one"), new MockHttpServletResponse(),
                    (request, response) -> {
                        executions.incrementAndGet();
                        response.getWriter().write("done");
                    });

            MockHttpServletResponse mismatch = new MockHttpServletResponse();
            fixture.filter.doFilter(request("bound-key", "two"), mismatch,
                    (request, response) -> executions.incrementAndGet());

            assertThat(mismatch.getStatus()).isEqualTo(422);
            assertThat(mismatch.getContentType()).startsWith("application/json");
            assertThat(mismatch.getContentAsString())
                    .contains("COCO_IDEMPOTENCY_PAYLOAD_MISMATCH")
                    .doesNotContain("bound-key")
                    .doesNotContain("two");
            assertThat(executions).hasValue(1);
        }
    }

    @Test
    void dangerousAndDynamicHopByHopHeadersFailClosed() throws Exception {
        try (TestFixture fixture = fixture(properties -> { }, request -> "caller-a")) {
            assertUnsafeHeader(fixture, "set-cookie", response -> response.setHeader("Set-Cookie", "sid=secret"));
            assertUnsafeHeader(fixture, "set-cookie2",
                    response -> response.setHeader("Set-Cookie2", "sid=secret"));
            assertUnsafeHeader(fixture, "authorization",
                    response -> response.setHeader("Authorization", "Bearer secret"));
            assertUnsafeHeader(fixture, "dynamic-hop", response -> {
                response.setHeader("X-Private-Hop", "secret");
                response.setHeader("Connection", "X-Private-Hop");
            });
        }
    }

    @Test
    void unsafeStoredHeadersAreRejectedBeforeReplay() throws Exception {
        CocoIdempotencyStoredResponse unsafe = new CocoIdempotencyStoredResponse(200,
                Map.of("Set-Cookie", List.of("sid=secret")), "stored".getBytes(StandardCharsets.UTF_8));
        CocoIdempotencyStore store = new ReplayOnlyStore(unsafe);
        TestFixture fixture = fixture(properties -> { }, request -> "caller-a", store);
        MockHttpServletResponse response = new MockHttpServletResponse();

        fixture.filter.doFilter(request("unsafe-replay", "same"), response,
                (request, servletResponse) -> servletResponse.getWriter().write("unexpected"));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(response.getContentAsString())
                .contains("COCO_IDEMPOTENCY_UNSAFE_RESPONSE_HEADER")
                .doesNotContain("sid=secret")
                .doesNotContain("stored");
    }

    @Test
    void unsupportedServletLifecycleCallsFailClosedAndReleaseLease() throws Exception {
        try (TestFixture fixture = fixture(properties -> { }, request -> "caller-a")) {
            assertUnsupported(fixture, "start-async", (request, response) -> request.startAsync());
            assertUnsupported(fixture, "flush-buffer", (request, response) -> response.flushBuffer());
            assertUnsupported(fixture, "send-error",
                    (request, response) -> ((HttpServletResponse) response).sendError(409));
            assertUnsupported(fixture, "send-redirect",
                    (request, response) -> ((HttpServletResponse) response).sendRedirect("/other"));
            assertUnsupported(fixture, "set-trailers",
                    (request, response) -> ((HttpServletResponse) response).setTrailerFields(Map::of));
            assertUnsupported(fixture, "get-trailers",
                    (request, response) -> ((HttpServletResponse) response).getTrailerFields());
            assertUnsupported(fixture, "write-listener",
                    (request, response) -> response.getOutputStream().setWriteListener(null));
            assertUnsupported(fixture, "add-cookie",
                    (request, response) -> ((HttpServletResponse) response)
                            .addCookie(new Cookie("sid", "secret")));
        }
    }

    @Test
    void responseOutputHandlesAreStableAndResetAfterWriterFailsClosedWithoutCachingSecret() throws Exception {
        try (TestFixture fixture = fixture(properties -> { }, request -> "caller-a")) {
            MockHttpServletResponse stableWriterResponse = new MockHttpServletResponse();
            fixture.filter.doFilter(request("stable-writer", "same"), stableWriterResponse,
                    (request, response) -> {
                        PrintWriter first = response.getWriter();
                        assertThat(response.getWriter()).isSameAs(first);
                        first.write("stable");
                    });

            MockHttpServletResponse stableStreamResponse = new MockHttpServletResponse();
            fixture.filter.doFilter(request("stable-stream", "same"), stableStreamResponse,
                    (request, response) -> {
                        ServletOutputStream first = response.getOutputStream();
                        assertThat(response.getOutputStream()).isSameAs(first);
                        first.write("stable".getBytes(StandardCharsets.UTF_8));
                    });

            assertResetAfterWriter(fixture, "writer-reset-buffer", false);
            assertResetAfterWriter(fixture, "writer-reset", true);
        }
    }

    @Test
    void writerAndStreamFlushOrCloseFailClosedWithOneLeaseRelease() throws Exception {
        assertUnsupportedWithSingleLeaseFailure((request, response) -> response.getWriter().flush());
        assertUnsupportedWithSingleLeaseFailure((request, response) -> response.getWriter().close());
        assertUnsupportedWithSingleLeaseFailure((request, response) -> response.getOutputStream().flush());
        assertUnsupportedWithSingleLeaseFailure((request, response) -> response.getOutputStream().close());
    }

    @Test
    void responseHeaderCountValueAndTotalByteBoundsFailClosed() throws Exception {
        try (TestFixture countFixture = fixture(properties -> properties.setMaxResponseHeaderCount(1),
                request -> "caller-a")) {
            assertHeaderLimit(countFixture, "header-count", response -> {
                response.setHeader("X-One", "one");
                response.setHeader("X-Two", "two");
            });
        }
        try (TestFixture valueFixture = fixture(properties -> {
            properties.setMaxResponseHeaderValueBytes(4);
            properties.setMaxResponseHeaderBytes(100);
        }, request -> "caller-a")) {
            assertHeaderLimit(valueFixture, "header-value", response -> response.setHeader("X-One", "12345"));
        }
        try (TestFixture totalFixture = fixture(properties -> {
            properties.setMaxResponseHeaderCount(10);
            properties.setMaxResponseHeaderValueBytes(20);
            properties.setMaxResponseHeaderBytes(20);
        }, request -> "caller-a")) {
            assertHeaderLimit(totalFixture, "header-total", response -> {
                response.setHeader("X-One", "12345");
                response.setHeader("X-Two", "12345");
            });
        }
    }

    @Test
    void downstreamFailureReleasesLeaseAndKeepsOriginalException() throws Exception {
        try (TestFixture fixture = fixture(properties -> { }, request -> "caller-a")) {
            assertThatThrownBy(() -> fixture.filter.doFilter(
                    request("retry-key", "same"), new MockHttpServletResponse(),
                    (request, response) -> {
                        throw new ServletException("business failed");
                    })).isInstanceOf(ServletException.class).hasMessageContaining("business failed");

            MockHttpServletResponse retry = new MockHttpServletResponse();
            fixture.filter.doFilter(request("retry-key", "same"), retry,
                    (request, response) -> response.getWriter().write("retried"));

            assertThat(retry.getContentAsString()).isEqualTo("retried");
        }
    }

    @Test
    void requestAndResponseBodyLimitsFailClosed() throws Exception {
        try (TestFixture fixture = fixture(properties -> {
            properties.setMaxRequestBodyBytes(4);
            properties.setMaxResponseBodyBytes(4);
        }, request -> "caller-a")) {
            MockHttpServletResponse requestTooLarge = new MockHttpServletResponse();
            fixture.filter.doFilter(request("large-request", "12345"), requestTooLarge,
                    (request, response) -> response.getWriter().write("no"));
            assertThat(requestTooLarge.getStatus()).isEqualTo(413);

            MockHttpServletResponse responseTooLarge = new MockHttpServletResponse();
            fixture.filter.doFilter(request("large-response", "1234"), responseTooLarge,
                    (request, response) -> response.getWriter().write("12345"));
            assertThat(responseTooLarge.getStatus()).isEqualTo(500);
            assertThat(responseTooLarge.getContentAsString()).contains("COCO_IDEMPOTENCY_RESPONSE_TOO_LARGE");

            MockHttpServletResponse retry = new MockHttpServletResponse();
            fixture.filter.doFilter(request("large-response", "1234"), retry,
                    (request, response) -> response.getWriter().write("okay"));
            assertThat(retry.getContentAsString()).isEqualTo("okay");
        }
    }

    @Test
    void unmatchedRoutePassesThroughWithoutScopeOrKey() throws Exception {
        try (TestFixture fixture = fixture(properties -> { }, request -> null)) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
            MockHttpServletResponse response = new MockHttpServletResponse();

            fixture.filter.doFilter(request, response,
                    (servletRequest, servletResponse) -> servletResponse.getWriter().write("ok"));

            assertThat(response.getContentAsString()).isEqualTo("ok");
        }
    }

    private static void assertUnsafeHeader(TestFixture fixture, String key,
            Consumer<HttpServletResponse> headerAction) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        fixture.filter.doFilter(request(key, "same"), response,
                (request, servletResponse) -> headerAction.accept((HttpServletResponse) servletResponse));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getHeaderNames()).doesNotContain("Set-Cookie", "Set-Cookie2", "Authorization",
                "Connection", "X-Private-Hop");
        assertThat(response.getContentAsString())
                .contains("COCO_IDEMPOTENCY_UNSAFE_RESPONSE_HEADER")
                .doesNotContain("secret");

        MockHttpServletResponse retry = new MockHttpServletResponse();
        fixture.filter.doFilter(request(key, "same"), retry,
                (request, servletResponse) -> servletResponse.getWriter().write("retry-ok"));
        assertThat(retry.getContentAsString()).isEqualTo("retry-ok");
    }

    private static void assertUnsupported(TestFixture fixture, String key, FilterChain unsupportedAction)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        fixture.filter.doFilter(request(key, "same"), response, unsupportedAction);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString())
                .contains("COCO_IDEMPOTENCY_UNSUPPORTED_IO")
                .doesNotContain("secret");

        MockHttpServletResponse retry = new MockHttpServletResponse();
        fixture.filter.doFilter(request(key, "same"), retry,
                (request, servletResponse) -> servletResponse.getWriter().write("retry-ok"));
        assertThat(retry.getContentAsString()).isEqualTo("retry-ok");
    }

    private static void assertResetAfterWriter(TestFixture fixture, String key, boolean fullReset) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        fixture.filter.doFilter(request(key, "same"), response, (request, servletResponse) -> {
            HttpServletResponse wrapped = (HttpServletResponse) servletResponse;
            wrapped.getWriter().write("secret");
            if (fullReset) {
                wrapped.reset();
            }
            else {
                wrapped.resetBuffer();
            }
            wrapped.getWriter().write("ok");
        });

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString())
                .contains("COCO_IDEMPOTENCY_UNSUPPORTED_IO")
                .doesNotContain("secret")
                .doesNotContain("ok");

        MockHttpServletResponse retry = new MockHttpServletResponse();
        fixture.filter.doFilter(request(key, "same"), retry,
                (request, servletResponse) -> servletResponse.getWriter().write("retry-ok"));
        assertThat(retry.getContentAsString()).isEqualTo("retry-ok");
    }

    private static void assertUnsupportedWithSingleLeaseFailure(FilterChain unsupportedAction) throws Exception {
        CountingStore store = new CountingStore();
        try (TestFixture fixture = fixture(properties -> { }, request -> "caller-a", store)) {
            assertUnsupported(fixture, "unsupported-" + store.nextKey(), unsupportedAction);
            assertThat(store.failedLeases()).isEqualTo(1);
        }
    }

    private static void assertHeaderLimit(TestFixture fixture, String key,
            Consumer<HttpServletResponse> headerAction) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        fixture.filter.doFilter(request(key, "same"), response,
                (request, servletResponse) -> headerAction.accept((HttpServletResponse) servletResponse));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("COCO_IDEMPOTENCY_RESPONSE_HEADERS_TOO_LARGE");
    }

    private static TestFixture fixture(Consumer<CocoIdempotencyProperties> customizer,
            CocoIdempotencyScopeResolver scopeResolver) {
        InMemoryCocoIdempotencyStore store = new InMemoryCocoIdempotencyStore(
                100, Duration.ofHours(1), Clock.fixed(NOW, ZoneOffset.UTC));
        return fixture(customizer, scopeResolver, store);
    }

    private static TestFixture fixture(Consumer<CocoIdempotencyProperties> customizer,
            CocoIdempotencyScopeResolver scopeResolver, CocoIdempotencyStore store) {
        CocoIdempotencyProperties properties = new CocoIdempotencyProperties();
        properties.setEnabled(true);
        CocoIdempotencyProperties.Route route = new CocoIdempotencyProperties.Route();
        route.setMethods(Set.of("POST"));
        route.setPathPatterns(Set.of("/orders/**"));
        properties.setRoutes(List.of(route));
        customizer.accept(properties);
        properties.validate();
        CocoIdempotencyFilter filter = new CocoIdempotencyFilter(properties,
                new CocoIdempotencyRouteMatcher(properties.getRoutes()), store, scopeResolver,
                exceptionWriter(), Clock.fixed(NOW, ZoneOffset.UTC));
        return new TestFixture(filter, store);
    }

    private static MockHttpServletRequest request(String key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders/create");
        request.addHeader("Idempotency-Key", key);
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static void authenticate(String principalId) {
        CocoSecurityContextHolder.set(CocoSecurityContext.authenticated(
                CocoSecurityPrincipal.of(principalId, principalId)));
    }

    private static CocoFilterExceptionResponseWriter exceptionWriter() {
        return new CocoFilterExceptionResponseWriter(new CocoWebExceptionHandler(new StaticMessageService(),
                new CocoIdempotencyExceptionHttpStatusResolver(new DefaultCocoExceptionHttpStatusResolver()),
                CocoSystemCodes.defaults()), new ObjectMapper());
    }

    private record TestFixture(CocoIdempotencyFilter filter, CocoIdempotencyStore store) implements AutoCloseable {

        @Override
        public void close() {
            this.store.close();
        }
    }

    private static final class ReplayOnlyStore implements CocoIdempotencyStore {

        private final CocoIdempotencyStoredResponse response;

        private ReplayOnlyStore(CocoIdempotencyStoredResponse response) {
            this.response = response;
        }

        @Override
        public CocoIdempotencyAcquireResult acquire(CocoIdempotencyRequest request, Instant now, Instant expiresAt) {
            return CocoIdempotencyAcquireResult.replay(this.response);
        }

        @Override
        public boolean complete(CocoIdempotencyLease lease, CocoIdempotencyStoredResponse response, Instant now) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public boolean fail(CocoIdempotencyLease lease, Instant now) {
            throw new UnsupportedOperationException("not used");
        }
    }

    private static final class CountingStore implements CocoIdempotencyStore {

        private final InMemoryCocoIdempotencyStore delegate = new InMemoryCocoIdempotencyStore(
                100, Duration.ofHours(1), Clock.fixed(NOW, ZoneOffset.UTC));

        private final AtomicInteger failedLeases = new AtomicInteger();

        private final AtomicInteger keys = new AtomicInteger();

        @Override
        public CocoIdempotencyAcquireResult acquire(CocoIdempotencyRequest request, Instant now, Instant expiresAt) {
            return this.delegate.acquire(request, now, expiresAt);
        }

        @Override
        public boolean complete(CocoIdempotencyLease lease, CocoIdempotencyStoredResponse response, Instant now) {
            return this.delegate.complete(lease, response, now);
        }

        @Override
        public boolean fail(CocoIdempotencyLease lease, Instant now) {
            this.failedLeases.incrementAndGet();
            return this.delegate.fail(lease, now);
        }

        @Override
        public void close() {
            this.delegate.close();
        }

        private int failedLeases() {
            return this.failedLeases.get();
        }

        private int nextKey() {
            return this.keys.incrementAndGet();
        }
    }

    private static final class StaticMessageService implements CocoMessageService {

        @Override
        public String getMessage(String code, Object... args) {
            return code;
        }

        @Override
        public String getMessage(String code, Locale locale, Object... args) {
            return code;
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Object... args) {
            return code;
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) {
            return code;
        }

        @Override
        public String resolve(CocoMessage message) {
            return message == null ? "" : message.code();
        }

        @Override
        public String resolve(CocoMessage message, Locale locale) {
            return resolve(message);
        }
    }
}
