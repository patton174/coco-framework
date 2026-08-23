package io.github.coco.feature.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

class CocoIdempotencyMvcInterceptorTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC);
    @Test
    void methodIntentOverridesClassIntentAndSuccessKeepsLease() throws Exception {
        RecordingStore store = new RecordingStore();
        CocoIdempotencyMvcInterceptor interceptor = interceptor(store);
        MockHttpServletRequest request = request("POST", "valid-key");
        HandlerMethod handler = new HandlerMethod(new ClassController(), ClassController.class.getMethod("method"));
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
        assertThat(store.leases).singleElement().satisfies(lease -> assertThat(lease.key().namespace()).isEqualTo("method"));
        interceptor.afterCompletion(request, new MockHttpServletResponse(), handler, null);
        assertThat(store.released).isEmpty();
    }
    @Test
    void rejectsMissingInvalidDuplicateAndUnavailableKeys() throws Exception {
        HandlerMethod handler = new HandlerMethod(new ClassController(), ClassController.class.getMethod("method"));
        assertStatus(interceptor(new RecordingStore()), request("POST", null), handler, 400);
        assertStatus(interceptor(new RecordingStore()), request("POST", "has space"), handler, 400);
        RecordingStore duplicate = new RecordingStore(); duplicate.result = CocoIdempotencyStore.AcquireResult.DUPLICATE;
        assertStatus(interceptor(duplicate), request("POST", "valid"), handler, 409);
        RecordingStore unavailable = new RecordingStore(); unavailable.result = CocoIdempotencyStore.AcquireResult.UNAVAILABLE;
        assertStatus(interceptor(unavailable), request("POST", "valid"), handler, 503);
    }
    @Test
    void failuresReleaseOnceAndAsyncRedispatchDoesNotAcquireTwice() throws Exception {
        RecordingStore store = new RecordingStore();
        CocoIdempotencyMvcInterceptor interceptor = interceptor(store);
        MockHttpServletRequest request = request("POST", "valid");
        HandlerMethod handler = new HandlerMethod(new ClassController(), ClassController.class.getMethod("method"));
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
        assertThat(store.leases).hasSize(1);
        MockHttpServletResponse response = new MockHttpServletResponse(); response.setStatus(500);
        interceptor.afterCompletion(request, response, handler, null);
        interceptor.afterCompletion(request, response, handler, null);
        assertThat(store.released).hasSize(1);
    }
    @Test
    void ignoresUnannotatedAndDisallowedMethods() throws Exception {
        RecordingStore store = new RecordingStore(); CocoIdempotencyMvcInterceptor interceptor = interceptor(store);
        assertThat(interceptor.preHandle(request("GET", "valid"), new MockHttpServletResponse(), new HandlerMethod(new ClassController(), ClassController.class.getMethod("method")))).isTrue();
        assertThat(interceptor.preHandle(request("POST", "valid"), new MockHttpServletResponse(), new HandlerMethod(new PlainController(), PlainController.class.getMethod("plain")))).isTrue();
        assertThat(store.leases).isEmpty();
    }
    @Test
    void executesAfterTheRateLimitInterceptorOrder() {
        assertThat(CocoIdempotencyAutoConfiguration.MVC_INTERCEPTOR_ORDER)
                .isGreaterThan(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
    }
    private void assertStatus(CocoIdempotencyMvcInterceptor interceptor, MockHttpServletRequest request, HandlerMethod handler, int status) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(request, response, handler)).isFalse(); assertThat(response.getStatus()).isEqualTo(status);
    }
    private static CocoIdempotencyMvcInterceptor interceptor(RecordingStore store) {
        CocoIdempotencyProperties properties = new CocoIdempotencyProperties();
        CocoIdempotencyResponseWriter writer = (code, request, response) -> response.setStatus(switch (code) { case INVALID_KEY -> 400; case DUPLICATE -> 409; case UNAVAILABLE -> 503; });
        return new CocoIdempotencyMvcInterceptor(properties, new DefaultCocoIdempotencyKeyResolver(properties), store, writer, CLOCK);
    }
    private static MockHttpServletRequest request(String method, String key) { MockHttpServletRequest request = new MockHttpServletRequest(method, "/orders/42"); request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}"); if (key != null) { request.addHeader("Idempotency-Key", key); } return request; }
    @CocoIdempotent(namespace = "class") static class ClassController { @CocoIdempotent(namespace = "method") public void method() { } }
    static class PlainController { public void plain() { } }
    private static final class RecordingStore implements CocoIdempotencyStore {
        private final List<CocoIdempotencyLease> leases = new ArrayList<>(); private final List<CocoIdempotencyLease> released = new ArrayList<>(); private CocoIdempotencyStore.AcquireResult result = CocoIdempotencyStore.AcquireResult.ACQUIRED;
        @Override public CocoIdempotencyStore.AcquireResult acquire(CocoIdempotencyLease lease) { this.leases.add(lease); return this.result; }
        @Override public void release(CocoIdempotencyLease lease) { this.released.add(lease); }
    }
}
