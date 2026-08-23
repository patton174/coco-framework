package io.github.coco.feature.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

class CocoIdempotencyMvcInterceptorTest {
    @Test
    void normalSuccessKeepsLeaseAndSecondRequestIsRejected() throws Exception {
        TestController controller = new TestController();
        CountingStore store = new CountingStore();
        MockMvc mvc = mvc(controller, store);
        mvc.perform(post("/orders/success").header("Idempotency-Key", "success-key"))
                .andExpect(status().isOk());
        mvc.perform(post("/orders/success").header("Idempotency-Key", "success-key"))
                .andExpect(status().isConflict());
        assertThat(controller.successCalls.get()).isOne();
        assertThat(store.acquireCalls.get()).isEqualTo(2);
    }

    @Test
    void resolvedFourHundredControllerExceptionReleasesLease() throws Exception {
        TestController controller = new TestController();
        MockMvc mvc = mvc(controller, new CountingStore());
        mvc.perform(post("/orders/bad-request").header("Idempotency-Key", "retry-400"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/orders/bad-request").header("Idempotency-Key", "retry-400"))
                .andExpect(status().isBadRequest());
        assertThat(controller.badRequestCalls.get()).isEqualTo(2);
    }

    @Test
    void fiveHundredControllerExceptionReleasesLease() throws Exception {
        TestController controller = new TestController();
        MockMvc mvc = mvc(controller, new CountingStore());
        mvc.perform(post("/orders/server-error").header("Idempotency-Key", "retry-500"))
                .andExpect(status().isInternalServerError());
        mvc.perform(post("/orders/server-error").header("Idempotency-Key", "retry-500"))
                .andExpect(status().isInternalServerError());
        assertThat(controller.serverErrorCalls.get()).isEqualTo(2);
    }

    @Test
    void laterInterceptorRejectionReleasesLease() throws Exception {
        TestController controller = new TestController();
        AtomicBoolean reject = new AtomicBoolean(true);
        HandlerInterceptor authorization = new HandlerInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response, Object handler) {
                if (reject.compareAndSet(true, false)) { response.setStatus(401); return false; }
                return true;
            }
        };
        MockMvc mvc = mvc(controller, new CountingStore(), authorization);
        mvc.perform(post("/orders/success").header("Idempotency-Key", "retry-401"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/orders/success").header("Idempotency-Key", "retry-401"))
                .andExpect(status().isOk());
        assertThat(controller.successCalls.get()).isOne();
    }

    @Test
    void asyncRedispatchAcquiresOnceAndKeepsOnlyAfterFinalCompletion() throws Exception {
        TestController controller = new TestController();
        CountingStore store = new CountingStore();
        MockMvc mvc = mvc(controller, store);
        MvcResult initial = mvc.perform(post("/orders/async").header("Idempotency-Key", "async-key"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(initial)).andExpect(status().isOk());
        assertThat(store.acquireCalls.get()).isOne();
        mvc.perform(post("/orders/async").header("Idempotency-Key", "async-key"))
                .andExpect(status().isConflict());
        assertThat(controller.asyncCalls.get()).isOne();
    }

    @Test
    void distinctHeaderMappingConditionsDoNotShareLease() throws Exception {
        ConditionController controller = new ConditionController();
        MockMvc mvc = mvc(controller, new CountingStore());
        mvc.perform(post("/orders/condition").header("X-Mode", "one").header("Idempotency-Key", "shared"))
                .andExpect(status().isOk());
        mvc.perform(post("/orders/condition").header("X-Mode", "two").header("Idempotency-Key", "shared"))
                .andExpect(status().isOk());
        mvc.perform(post("/orders/condition").header("X-Mode", "one").header("Idempotency-Key", "shared"))
                .andExpect(status().isConflict());
        assertThat(controller.oneCalls.get()).isOne();
        assertThat(controller.twoCalls.get()).isOne();
    }

    @Test
    void routeOperationIdentityIsStableAcrossEquivalentHandlersAndSeparatesMappingConditions() {
        DefaultCocoIdempotencyOperationResolver resolver = new DefaultCocoIdempotencyOperationResolver();
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("POST", "/orders/42");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}");

        String first = resolver.resolve(request, new HandlerMethod(new EquivalentControllerOne(),
                method(EquivalentControllerOne.class, "operation")));
        String second = resolver.resolve(request, new HandlerMethod(new EquivalentControllerTwo(),
                method(EquivalentControllerTwo.class, "operation")));
        String headerVariant = resolver.resolve(request, new HandlerMethod(new ConditionController(),
                method(ConditionController.class, "one")));
        String parameterVariant = resolver.resolve(request, new HandlerMethod(new ParameterController(),
                method(ParameterController.class, "one")));

        assertThat(first).isEqualTo(second);
        assertThat(first).doesNotContain(EquivalentControllerOne.class.getName()).doesNotContain("operation()");
        assertThat(headerVariant).isNotEqualTo(resolver.resolve(request, new HandlerMethod(new ConditionController(),
                method(ConditionController.class, "two"))));
        assertThat(parameterVariant).isNotEqualTo(resolver.resolve(request, new HandlerMethod(new ParameterController(),
                method(ParameterController.class, "two"))));
    }

    @Test
    void methodAnnotationOverridesClassNamespace() {
        CocoIdempotencyProperties properties = new CocoIdempotencyProperties();
        DefaultCocoIdempotencyKeyResolver resolver = new DefaultCocoIdempotencyKeyResolver(properties);
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/orders");
        request.addHeader("Idempotency-Key", "method-key");
        request.setAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders");
        HandlerMethod method = new HandlerMethod(new ClassAnnotatedController(), method(ClassAnnotatedController.class, "operation"));
        CocoIdempotent annotation = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                method.getMethod(), CocoIdempotent.class);
        assertThat(resolver.resolve(request, method, annotation).namespace()).isEqualTo("method");
    }

    private static MockMvc mvc(Object controller, CountingStore store, HandlerInterceptor... following) {
        CocoIdempotencyProperties properties = new CocoIdempotencyProperties();
        CocoIdempotencyResponseWriter writer = (code, request, response) -> response.setStatus(switch (code) {
            case INVALID_KEY -> 400;
            case DUPLICATE -> 409;
            case UNAVAILABLE -> 503;
        });
        CocoIdempotencyMvcInterceptor interceptor = new CocoIdempotencyMvcInterceptor(properties,
                new DefaultCocoIdempotencyKeyResolver(properties), store, writer, Clock.systemUTC());
        HandlerInterceptor[] all = new HandlerInterceptor[following.length + 1];
        all[0] = interceptor;
        System.arraycopy(following, 0, all, 1, following.length);
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ErrorAdvice()).addInterceptors(all).build();
    }

    private static java.lang.reflect.Method method(Class<?> type, String name) {
        try { return type.getDeclaredMethod(name); }
        catch (NoSuchMethodException exception) { throw new AssertionError(exception); }
    }

    @RestController
    static class TestController {
        private final AtomicInteger successCalls = new AtomicInteger();
        private final AtomicInteger badRequestCalls = new AtomicInteger();
        private final AtomicInteger serverErrorCalls = new AtomicInteger();
        private final AtomicInteger asyncCalls = new AtomicInteger();
        @PostMapping("/orders/success") @CocoIdempotent String success() { this.successCalls.incrementAndGet(); return "ok"; }
        @PostMapping("/orders/bad-request") @CocoIdempotent String badRequest() { this.badRequestCalls.incrementAndGet(); throw new IllegalArgumentException(); }
        @PostMapping("/orders/server-error") @CocoIdempotent String serverError() { this.serverErrorCalls.incrementAndGet(); throw new IllegalStateException(); }
        @PostMapping("/orders/async") @CocoIdempotent Callable<String> async() {
            return () -> { this.asyncCalls.incrementAndGet(); return "ok"; };
        }
    }

    @RestController
    static class ConditionController {
        private final AtomicInteger oneCalls = new AtomicInteger();
        private final AtomicInteger twoCalls = new AtomicInteger();
        @PostMapping(value = "/orders/condition", headers = "X-Mode=one") @CocoIdempotent String one() { this.oneCalls.incrementAndGet(); return "one"; }
        @PostMapping(value = "/orders/condition", headers = "X-Mode=two") @CocoIdempotent String two() { this.twoCalls.incrementAndGet(); return "two"; }
    }

    @RestController
    static class ParameterController {
        @PostMapping(value = "/orders/{id}", params = "version=1") @CocoIdempotent String one() { return "one"; }
        @PostMapping(value = "/orders/{id}", params = "version=2") @CocoIdempotent String two() { return "two"; }
    }

    @RestController
    static class EquivalentControllerOne {
        @PostMapping(value = "/orders/{id}", params = "version=1", headers = "X-Mode=one",
                consumes = "application/json", produces = "application/json")
        String operation() { return "one"; }
    }

    @RestController
    static class EquivalentControllerTwo {
        @PostMapping(value = "/orders/{id}", params = "version=1", headers = "X-Mode=one",
                consumes = "application/json", produces = "application/json")
        String operation() { return "two"; }
    }

    @CocoIdempotent(namespace = "class")
    static class ClassAnnotatedController { @CocoIdempotent(namespace = "method") void operation() { } }

    @RestControllerAdvice
    static class ErrorAdvice {
        @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Void> badRequest() { return ResponseEntity.badRequest().build(); }
        @ExceptionHandler(IllegalStateException.class) ResponseEntity<Void> serverError() { return ResponseEntity.internalServerError().build(); }
    }

    static final class CountingStore implements CocoIdempotencyStore, AutoCloseable {
        private final InMemoryCocoIdempotencyStore delegate = new InMemoryCocoIdempotencyStore(new CocoIdempotencyProperties());
        private final AtomicInteger acquireCalls = new AtomicInteger();
        @Override public AcquireResult acquire(CocoIdempotencyLease lease) { this.acquireCalls.incrementAndGet(); return this.delegate.acquire(lease); }
        @Override public void release(CocoIdempotencyLease lease) { this.delegate.release(lease); }
        @Override public void close() { this.delegate.close(); }
    }
}
