package io.github.coco.feature.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.context.CocoRequestContextHolder;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.exception.CocoException;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalForm;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationProperties;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.CocoWebRequestSnapshotAttributes;
import io.github.coco.feature.web.context.DefaultCocoWebRequestCanonicalizer;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.trace.CocoTraceIdValidator;
import io.github.coco.feature.web.trace.CocoTraceProperties;
import io.github.coco.logging.access.CocoAccessLog;
import io.github.coco.logging.access.CocoAccessLogRecorder;
import jakarta.servlet.Filter;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Coco TraceId 边界集成测试。
 * <p>
 * 验证独立请求规范化调用和按 Spring 注册顺序执行的完整过滤器链共享同一 TraceId 校验语义。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
class CocoTraceIdBoundaryIntegrationTest {

    private static final List<String> REGISTERED_FILTER_ORDER = List.of(
            "cocoRequestBodyCachingFilterRegistration",
            "cocoTraceFilterRegistration",
            "cocoReplayRequestShapeFilterRegistration",
            "cocoEncryptionFilterRegistration",
            "cocoSignatureFilterRegistration",
            "cocoReplayFilterRegistration");

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoWebAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @AfterEach
    void clearContext() {
        CocoRequestContextHolder.clear();
        CocoTraceContext.clear();
        MDC.clear();
    }

    @Test
    void independentCanonicalizerRejectsUnsafeTraceIdsBeforeBuildingCanonicalForm() {
        DefaultCocoWebRequestCanonicalizer canonicalizer = new DefaultCocoWebRequestCanonicalizer();

        for (String traceId : List.of(
                "\u0001polluted", "polluted\u0085value", "polluted\u2028value", "polluted\u2029value")) {
            CocoException exception = assertThrows(CocoException.class,
                    () -> canonicalizer.canonicalize(securityInput("X-Trace-Id", List.of(traceId))));

            assertEquals("coco.web.trace.invalid-trace-id", exception.messageCode(), traceId);
        }
    }

    @Test
    void independentCanonicalizerUsesConfiguredHeaderAndCustomValidator() {
        CocoTraceProperties traceProperties = new CocoTraceProperties();
        traceProperties.setHeaderName("X-Correlation-Id");
        CocoTraceIdValidator validator = traceId -> traceId.startsWith("custom_");
        DefaultCocoWebRequestCanonicalizer canonicalizer = new DefaultCocoWebRequestCanonicalizer(
                new CocoWebRequestCanonicalizationProperties(), traceProperties, validator);

        CocoWebRequestCanonicalForm canonicalForm = canonicalizer.canonicalize(
                securityInput("X-Correlation-Id", List.of(" custom_trace ", "\tcustom_trace\t")));
        CocoException exception = assertThrows(CocoException.class, () -> canonicalizer.canonicalize(
                securityInput("X-Correlation-Id", List.of("legacy-trace"))));

        assertTrue(canonicalForm.text().contains("x-correlation-id#2"));
        assertEquals("coco.web.trace.invalid-trace-id", exception.messageCode());
    }

    @Test
    void canonicalizerIgnoresMaliciousLegacyHeaderResolutionOverride() {
        OverrideBypassingTraceIdValidator validator = new OverrideBypassingTraceIdValidator();
        DefaultCocoWebRequestCanonicalizer canonicalizer = new DefaultCocoWebRequestCanonicalizer(
                new CocoWebRequestCanonicalizationProperties(), new CocoTraceProperties(), validator);

        CocoException exception = assertThrows(CocoException.class, () -> canonicalizer.canonicalize(
                securityInput("X-Trace-Id", List.of("first-trace", "second-trace"))));

        assertEquals("coco.web.trace.invalid-trace-id", exception.messageCode());
        assertEquals(0, validator.legacyResolutionCalls());
    }

    @Test
    void springRequestContextResolverRejectsRawTraceBeforeCanonicalizationWhenTraceFilterIsSkipped() {
        this.webContextRunner
                .withPropertyValues("coco.web.context.canonical-header-names=X-Trace-Id")
                .run(context -> {
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
                    request.addHeader("X-Trace-Id", "\u0001polluted");

                    CocoException exception = assertThrows(CocoException.class,
                            () -> context.getBean(CocoWebRequestContextResolver.class)
                                    .resolve("server-generated-trace", request));

                    assertEquals("coco.web.trace.invalid-trace-id", exception.messageCode());
                    assertTrue(CocoWebRequestSnapshotAttributes.get(request).isEmpty());
                });
    }

    @Test
    void securityInputResolverIgnoresMaliciousLegacyHeaderResolutionOverride() {
        OverrideBypassingTraceIdValidator validator = new OverrideBypassingTraceIdValidator();
        this.webContextRunner
                .withBean(CocoTraceIdValidator.class, () -> validator)
                .run(context -> {
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
                    request.addHeader("X-Trace-Id", "x".repeat(CocoTraceProperties.DEFAULT_MAX_LENGTH + 1));

                    CocoException exception = assertThrows(CocoException.class,
                            () -> context.getBean(CocoWebRequestContextResolver.class)
                                    .resolve("server-generated-trace", request));

                    assertEquals("coco.web.trace.invalid-trace-id", exception.messageCode());
                    assertTrue(CocoWebRequestSnapshotAttributes.get(request).isEmpty());
                    assertEquals(0, validator.legacyResolutionCalls());
                });
    }

    @Test
    void registeredSpringFilterChainRejectsUnsafeTraceIdsWithoutPropagation() throws Exception {
        CapturingAccessLogRecorder recorder = new CapturingAccessLogRecorder();
        AtomicInteger canonicalizations = new AtomicInteger();
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.context.canonical-header-names=X-Trace-Id",
                        "coco.web.response.metadata-mode=debug",
                        "coco.web.trace.response-cookie-enabled=true",
                        "coco.web.signature.required=true")
                .withBean(CocoAccessLogRecorder.class, () -> recorder)
                .withBean(CocoWebRequestCanonicalizer.class, () -> context -> {
                    canonicalizations.incrementAndGet();
                    return new DefaultCocoWebRequestCanonicalizer().canonicalize(context);
                })
                .run(context -> {
                    assertRegisteredFilterOrder(context);
                    for (String traceId : List.of(
                            "polluted\u0001value", "polluted\u0085value",
                            "polluted\u2028value", "polluted\u2029value")) {
                        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
                        MockHttpServletResponse response = new MockHttpServletResponse();
                        AtomicBoolean terminalInvoked = new AtomicBoolean();
                        request.addHeader("X-Trace-Id", traceId);
                        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US");

                        registeredFilterChain(context, () -> terminalInvoked.set(true))
                                .doFilter(request, response);

                        assertEquals(400, response.getStatus(), traceId);
                        assertFalse(terminalInvoked.get(), traceId);
                        assertEquals(0, canonicalizations.get(), traceId);
                        assertNull(response.getHeader("X-Trace-Id"), traceId);
                        assertNull(response.getHeader(HttpHeaders.SET_COOKIE), traceId);
                        assertNull(recorder.lastAccessLog(), traceId);
                        Map<?, ?> responseBody = assertUnifiedInvalidTraceResponse(response, traceId);
                        assertNotNull(responseBody.get("traceId"), traceId);
                        assertTrue(CocoRequestContextHolder.current().isEmpty(), traceId);
                        assertTrue(CocoTraceContext.currentTraceId().isEmpty(), traceId);
                        assertNull(MDC.get("traceId"), traceId);
                    }
                });
    }

    @Test
    void registeredFilterChainIgnoresMaliciousLegacyHeaderResolutionOverride() throws Exception {
        OverrideBypassingTraceIdValidator validator = new OverrideBypassingTraceIdValidator();
        CapturingAccessLogRecorder recorder = new CapturingAccessLogRecorder();
        AtomicInteger canonicalizations = new AtomicInteger();
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.context.canonical-header-names=X-Trace-Id",
                        "coco.web.response.metadata-mode=debug",
                        "coco.web.trace.response-cookie-enabled=true",
                        "coco.web.signature.required=true")
                .withBean(CocoTraceIdValidator.class, () -> validator)
                .withBean(CocoAccessLogRecorder.class, () -> recorder)
                .withBean(CocoWebRequestCanonicalizer.class, () -> context -> {
                    canonicalizations.incrementAndGet();
                    return new DefaultCocoWebRequestCanonicalizer().canonicalize(context);
                })
                .run(context -> {
                    assertRegisteredFilterOrder(context);
                    for (String traceId : List.of("polluted\r\nInjected: true", "polluted\u2028value")) {
                        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
                        MockHttpServletResponse response = new MockHttpServletResponse();
                        AtomicBoolean terminalInvoked = new AtomicBoolean();
                        request.addHeader("X-Trace-Id", traceId);
                        request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US");

                        registeredFilterChain(context, () -> terminalInvoked.set(true))
                                .doFilter(request, response);

                        assertEquals(400, response.getStatus(), traceId);
                        assertFalse(terminalInvoked.get(), traceId);
                        assertEquals(0, canonicalizations.get(), traceId);
                        assertNull(response.getHeader("X-Trace-Id"), traceId);
                        assertNull(response.getHeader(HttpHeaders.SET_COOKIE), traceId);
                        assertNull(recorder.lastAccessLog(), traceId);
                        assertNotNull(assertUnifiedInvalidTraceResponse(response, traceId).get("traceId"), traceId);
                        assertTrue(CocoRequestContextHolder.current().isEmpty(), traceId);
                        assertTrue(CocoTraceContext.currentTraceId().isEmpty(), traceId);
                        assertNull(MDC.get("traceId"), traceId);
                    }
                    assertEquals(0, validator.legacyResolutionCalls());
                });
    }

    @Test
    void registeredChainWithoutTraceFilterStillReturnsUnifiedBadRequestBeforeCanonicalization() throws Exception {
        CapturingAccessLogRecorder recorder = new CapturingAccessLogRecorder();
        AtomicInteger canonicalizations = new AtomicInteger();
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.trace.enabled=false",
                        "coco.web.context.canonical-header-names=X-Trace-Id",
                        "coco.web.response.metadata-mode=debug",
                        "coco.web.signature.required=true")
                .withBean(CocoAccessLogRecorder.class, () -> recorder)
                .withBean(CocoWebRequestCanonicalizer.class, () -> context -> {
                    canonicalizations.incrementAndGet();
                    return new DefaultCocoWebRequestCanonicalizer().canonicalize(context);
                })
                .run(context -> {
                    assertRegisteredFilterOrder(context, REGISTERED_FILTER_ORDER.stream()
                            .filter(name -> !"cocoTraceFilterRegistration".equals(name))
                            .toList());
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicBoolean terminalInvoked = new AtomicBoolean();
                    String traceId = "polluted\u2029value";
                    request.addHeader("X-Trace-Id", traceId);
                    request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US");

                    registeredFilterChain(context, () -> terminalInvoked.set(true)).doFilter(request, response);

                    assertEquals(400, response.getStatus());
                    assertFalse(terminalInvoked.get());
                    assertEquals(0, canonicalizations.get());
                    assertNull(response.getHeader("X-Trace-Id"));
                    assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
                    assertNull(recorder.lastAccessLog());
                    assertNotNull(assertUnifiedInvalidTraceResponse(response, traceId).get("traceId"));
                });
    }

    @Test
    void registeredSpringFilterChainAcceptsRepeatedIdenticalTraceIds() throws Exception {
        CapturingAccessLogRecorder recorder = new CapturingAccessLogRecorder();
        AtomicReference<CocoWebRequestCanonicalForm> canonicalForm = new AtomicReference<>();
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.context.canonical-header-names=X-Trace-Id",
                        "coco.web.trace.response-cookie-enabled=true")
                .withBean(CocoAccessLogRecorder.class, () -> recorder)
                .run(context -> {
                    assertRegisteredFilterOrder(context);
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "repeated-trace");
                    request.addHeader("X-Trace-Id", "repeated-trace");

                    registeredFilterChain(context, () -> {
                        CocoWebRequestSnapshot snapshot = CocoWebRequestSnapshotAttributes.get(request).orElseThrow();
                        canonicalForm.set(context.getBean(CocoWebRequestCanonicalizer.class)
                                .canonicalize(snapshot.securityInput()));
                    }).doFilter(request, response);

                    assertEquals(200, response.getStatus());
                    assertEquals("repeated-trace", response.getHeader("X-Trace-Id"));
                    assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains("COCO_TRACE_ID=repeated-trace"));
                    assertTrue(canonicalForm.get().text().contains("x-trace-id#2"));
                    assertTrue(canonicalForm.get().text().contains("x-trace-id[0]=14:repeated-trace"));
                    assertTrue(canonicalForm.get().text().contains("x-trace-id[1]=14:repeated-trace"));
                    assertEquals("repeated-trace", recorder.lastAccessLog().traceId());
                });
    }

    @Test
    void springCanonicalizerAndFilterChainShareCustomHeaderValidator() throws Exception {
        CapturingAccessLogRecorder recorder = new CapturingAccessLogRecorder();
        AtomicReference<CocoWebRequestCanonicalForm> canonicalForm = new AtomicReference<>();
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.trace.header-name=X-Correlation-Id",
                        "coco.web.context.canonical-header-names=X-Correlation-Id",
                        "coco.web.trace.response-cookie-enabled=true")
                .withBean(CocoTraceIdValidator.class, () -> traceId -> traceId.startsWith("custom_"))
                .withBean(CocoAccessLogRecorder.class, () -> recorder)
                .run(context -> {
                    MockHttpServletRequest validRequest = new MockHttpServletRequest("GET", "/api/orders");
                    MockHttpServletResponse validResponse = new MockHttpServletResponse();
                    validRequest.addHeader("X-Correlation-Id", "custom_trace");

                    registeredFilterChain(context, () -> {
                        CocoWebRequestSnapshot snapshot = CocoWebRequestSnapshotAttributes.get(validRequest)
                                .orElseThrow();
                        canonicalForm.set(context.getBean(CocoWebRequestCanonicalizer.class)
                                .canonicalize(snapshot.securityInput()));
                    }).doFilter(validRequest, validResponse);

                    assertEquals("custom_trace", validResponse.getHeader("X-Correlation-Id"));
                    assertTrue(canonicalForm.get().text().contains("x-correlation-id[0]=12:custom_trace"));
                    assertEquals("custom_trace", recorder.lastAccessLog().traceId());

                    CocoException canonicalizationException = assertThrows(CocoException.class,
                            () -> context.getBean(CocoWebRequestCanonicalizer.class).canonicalize(
                                    securityInput("X-Correlation-Id", List.of("legacy-trace"))));
                    assertEquals("coco.web.trace.invalid-trace-id", canonicalizationException.messageCode());

                    recorder.clear();
                    MockHttpServletRequest invalidRequest = new MockHttpServletRequest("GET", "/api/orders");
                    MockHttpServletResponse invalidResponse = new MockHttpServletResponse();
                    invalidRequest.addHeader("X-Correlation-Id", "legacy-trace");
                    invalidRequest.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US");

                    registeredFilterChain(context, () -> {
                        throw new AssertionError("invalid custom TraceId reached the terminal servlet");
                    }).doFilter(invalidRequest, invalidResponse);

                    assertEquals(400, invalidResponse.getStatus());
                    assertNull(invalidResponse.getHeader("X-Correlation-Id"));
                    assertNull(invalidResponse.getHeader(HttpHeaders.SET_COOKIE));
                    assertNull(recorder.lastAccessLog());
                    assertUnifiedInvalidTraceResponse(invalidResponse, "legacy-trace");
                });
    }

    private static CocoWebRequestSecurityInput securityInput(String headerName, List<String> headerValues) {
        return new CocoWebRequestSecurityInput("GET", "/api/orders", null,
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), null, null, false,
                Map.of(headerName, headerValues), Map.of());
    }

    private static MockFilterChain registeredFilterChain(ApplicationContext context, Runnable terminalAssertion) {
        List<RegisteredFilter> registrations = registeredFilters(context);
        Servlet terminal = new TerminalServlet(terminalAssertion);
        Filter[] filters = registrations.stream().map(RegisteredFilter::filter).toArray(Filter[]::new);
        return new MockFilterChain(terminal, filters);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static List<RegisteredFilter> registeredFilters(ApplicationContext context) {
        List<RegisteredFilter> registrations = new ArrayList<>();
        context.getBeansOfType(FilterRegistrationBean.class).forEach((name, registration) -> {
            if (registration.isEnabled()) {
                registrations.add(new RegisteredFilter(name, registration.getOrder(), registration.getFilter()));
            }
        });
        registrations.sort(Comparator.comparingInt(RegisteredFilter::order).thenComparing(RegisteredFilter::name));
        return List.copyOf(registrations);
    }

    private static void assertRegisteredFilterOrder(ApplicationContext context) {
        assertRegisteredFilterOrder(context, REGISTERED_FILTER_ORDER);
    }

    private static void assertRegisteredFilterOrder(ApplicationContext context, List<String> expectedOrder) {
        assertEquals(expectedOrder,
                registeredFilters(context).stream().map(RegisteredFilter::name).toList());
    }

    private static Map<?, ?> assertUnifiedInvalidTraceResponse(MockHttpServletResponse response,
            String rejectedTraceId)
            throws IOException {
        String content = response.getContentAsString();
        Map<?, ?> body = new ObjectMapper().readValue(content, Map.class);
        assertEquals(Boolean.FALSE, body.get("success"));
        assertEquals(400, body.get("code"));
        assertEquals("Request TraceId is invalid.", body.get("message"));
        assertFalse(content.contains("polluted"));
        assertFalse(content.contains(rejectedTraceId));
        Object responseTraceId = body.get("traceId");
        if (responseTraceId != null) {
            assertTrue(CocoTraceIdValidator.isTransportSafe(responseTraceId.toString()));
            assertFalse(rejectedTraceId.equals(responseTraceId));
        }
        return body;
    }

    @SuppressWarnings("deprecation")
    private static final class OverrideBypassingTraceIdValidator implements CocoTraceIdValidator {

        private final AtomicInteger legacyResolutionCalls = new AtomicInteger();

        @Override
        public boolean isValid(String traceId) {
            return true;
        }

        @Override
        public Optional<String> resolveHeaderValues(List<String> headerValues, int maxLength) {
            this.legacyResolutionCalls.incrementAndGet();
            return headerValues == null || headerValues.isEmpty()
                    ? Optional.empty()
                    : Optional.ofNullable(headerValues.get(0));
        }

        private int legacyResolutionCalls() {
            return this.legacyResolutionCalls.get();
        }
    }

    private record RegisteredFilter(String name, int order, Filter filter) {
    }

    private static final class TerminalServlet implements Servlet {

        private final Runnable assertion;

        private TerminalServlet(Runnable assertion) {
            this.assertion = assertion;
        }

        @Override
        public void init(ServletConfig config) {
        }

        @Override
        public ServletConfig getServletConfig() {
            return null;
        }

        @Override
        public void service(ServletRequest request, ServletResponse response) {
            this.assertion.run();
        }

        @Override
        public String getServletInfo() {
            return "trace-boundary-terminal";
        }

        @Override
        public void destroy() {
        }
    }

    private static final class CapturingAccessLogRecorder implements CocoAccessLogRecorder {

        private final AtomicReference<CocoAccessLog> accessLog = new AtomicReference<>();

        @Override
        public void record(CocoAccessLog accessLog) {
            this.accessLog.set(accessLog);
        }

        private CocoAccessLog lastAccessLog() {
            return this.accessLog.get();
        }

        private void clear() {
            this.accessLog.set(null);
        }
    }
}
