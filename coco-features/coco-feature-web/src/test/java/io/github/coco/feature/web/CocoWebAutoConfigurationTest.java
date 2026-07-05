package io.github.coco.feature.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.accesslog.CocoAccessLog;
import io.github.coco.common.accesslog.CocoAccessLogRecorder;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.context.CocoRequestContext;
import io.github.coco.common.context.CocoRequestContextHolder;
import io.github.coco.common.exception.CocoCommonErrorCode;
import io.github.coco.common.exception.CocoExceptions;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.response.CocoApiResponse;
import io.github.coco.feature.web.response.CocoIgnoreResponseWrap;
import io.github.coco.feature.web.response.CocoResponseWrapAdvice;
import io.github.coco.feature.web.response.CocoResponseWrapProperties;
import io.github.coco.feature.web.trace.CocoTraceFilter;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Coco Web 功能自动配置测试。
 * <p>
 * 验证 Web 功能模块可以通过 Coco 国际化基础设施注册自己的消息资源。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoWebAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoWebAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoWebAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @AfterEach
    void clearTraceContext() {
        CocoRequestContextHolder.clear();
        CocoTraceContext.clear();
        MDC.clear();
    }

    @Test
    void registersWebMessageBundle() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertTrue(context.containsBean("cocoWebMessageBundleRegistrar"));
            assertEquals("Coco Web 功能消息资源已就绪。", messageService.getMessage("coco.feature.web.ready"));
        });
    }

    @Test
    void createsExceptionHandlerInServletApplication() {
        this.webContextRunner.run(context -> {
            assertTrue(context.containsBean("cocoExceptionHttpStatusResolver"));
            assertTrue(context.containsBean("cocoWebExceptionHandler"));
            assertTrue(context.containsBean("cocoTraceFilterRegistration"));
        });
    }

    @Test
    void createsResponseWrapAdviceByDefault() {
        this.webContextRunner.run(context -> {
            assertTrue(context.containsBean("cocoResponseWrapAdvice"));
            assertNotNull(context.getBean(CocoResponseWrapAdvice.class));
        });
    }

    @Test
    void disablesResponseWrapAdviceByProperty() {
        this.webContextRunner
                .withPropertyValues("coco.web.response-wrap.enabled=false")
                .run(context -> assertFalse(context.containsBean("cocoResponseWrapAdvice")));
    }

    @Test
    void registersLocalizedSuccessResponseMessage() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertEquals("操作成功", messageService.getMessage("coco.web.response.success"));
        });
    }

    @Test
    void disablesTraceFilterRegistrationByProperty() {
        this.webContextRunner
                .withPropertyValues("coco.web.trace.enabled=false")
                .run(context -> assertFalse(context.containsBean("cocoTraceFilterRegistration")));
    }

    @Test
    void returnsLocalizedErrorResponseForCocoException() {
        CocoTraceContext.setTraceId("trace-test");
        this.webContextRunner.run(context -> {
            CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");

            ResponseEntity<CocoApiResponse<Void>> response = handler.handleCocoException(
                    CocoCommonErrorCode.INVALID_ARGUMENT.exception("name"),
                    new ServletWebRequest(request));

            CocoApiResponse<Void> body = response.getBody();
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(body);
            assertFalse(body.success());
            assertEquals("coco.error.invalid-argument", body.code());
            assertEquals("参数不合法：name", body.message());
            assertEquals("trace-test", body.traceId());
            assertEquals("/api/users", body.path());
        });
    }

    @Test
    void returnsLocalizedErrorResponseForTypedCocoException() {
        this.webContextRunner.run(context -> {
            CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/404");

            ResponseEntity<CocoApiResponse<Void>> response = handler.handleCocoException(
                    CocoCommonErrorCode.NOT_FOUND.notFound("user"),
                    new ServletWebRequest(request));

            CocoApiResponse<Void> body = response.getBody();
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(body);
            assertFalse(body.success());
            assertEquals("coco.error.not-found", body.code());
            assertEquals("资源不存在：user", body.message());
            assertEquals("/api/users/404", body.path());
        });
    }

    @Test
    void rejectsBlankResponseCode() {
        assertThrows(IllegalArgumentException.class,
                () -> new CocoApiResponse<>(false, " ", "message", null, "trace", "/api/users"));
    }

    @Test
    void createsSuccessResponseModel() {
        CocoApiResponse<String> response = CocoApiResponse.success(
                "coco.success", "操作成功", "payload", "trace-id", "/api/users");

        assertTrue(response.success());
        assertEquals("coco.success", response.code());
        assertEquals("操作成功", response.message());
        assertEquals("payload", response.data());
        assertEquals("trace-id", response.traceId());
        assertEquals("/api/users", response.path());
    }

    @Test
    void responseWrapPropertiesUseDefaultsAndResetNullNestedValue() {
        CocoWebProperties properties = new CocoWebProperties();

        assertTrue(properties.getResponseWrap().isEnabled());
        assertEquals("coco.success", properties.getResponseWrap().getSuccessCode());
        assertEquals("coco.web.response.success", properties.getResponseWrap().getSuccessMessageCode());

        properties.setResponseWrap(null);

        assertTrue(properties.getResponseWrap().isEnabled());
        assertEquals("coco.success", properties.getResponseWrap().getSuccessCode());
    }

    @Test
    void wrapsObjectResponseBody() throws Exception {
        CocoTraceContext.setTraceId("trace-wrap");
        this.webContextRunner.run(context -> {
            CocoResponseWrapAdvice advice = responseWrapAdvice(context.getBean(CocoMessageService.class));
            MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/users");
            ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);

            Object body = advice.beforeBodyWrite(Map.of("name", "Coco"), methodParameter("objectBody"),
                    MediaType.APPLICATION_JSON, TestHttpMessageConverter.class, request,
                    new ServletServerHttpResponse(new MockHttpServletResponse()));

            assertTrue(body instanceof CocoApiResponse<?>);
            CocoApiResponse<?> response = (CocoApiResponse<?>) body;
            assertTrue(response.success());
            assertEquals("coco.success", response.code());
            assertEquals("未知错误", response.message());
            assertEquals(Map.of("name", "Coco"), response.data());
            assertEquals("trace-wrap", response.traceId());
            assertEquals("/api/users", response.path());
        });
    }

    @Test
    void wrapsStringResponseBodyAsJsonString() throws Exception {
        this.webContextRunner.run(context -> {
            CocoResponseWrapAdvice advice = responseWrapAdvice(context.getBean(CocoMessageService.class));
            MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/text");
            ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);

            Object body = advice.beforeBodyWrite("hello", methodParameter("stringBody"),
                    MediaType.TEXT_PLAIN, StringHttpMessageConverter.class, request,
                    new ServletServerHttpResponse(new MockHttpServletResponse()));

            assertTrue(body instanceof String);
            assertTrue(((String) body).contains("\"success\":true"));
            assertTrue(((String) body).contains("\"data\":\"hello\""));
            assertTrue(((String) body).contains("\"path\":\"/api/text\""));
        });
    }

    @Test
    void skipsAlreadyWrappedBodyIgnoredMethodsIgnoredClassesAndResponseEntity() throws Exception {
        this.webContextRunner.run(context -> {
            CocoResponseWrapAdvice advice = responseWrapAdvice(context.getBean(CocoMessageService.class));

            assertFalse(advice.supports(methodParameter("wrappedBody"),
                    TestHttpMessageConverter.class));
            assertFalse(advice.supports(methodParameter("ignoredBody"),
                    TestHttpMessageConverter.class));
            assertFalse(advice.supports(methodParameter(IgnoredController.class, "classIgnoredBody"),
                    TestHttpMessageConverter.class));
            assertFalse(advice.supports(methodParameter("responseEntityBody"),
                    TestHttpMessageConverter.class));
        });
    }

    @Test
    void usesCustomExceptionHttpStatusResolver() {
        this.webContextRunner
                .withBean(CocoExceptionHttpStatusResolver.class,
                        () -> exception -> HttpStatus.CONFLICT)
                .run(context -> {
                    CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");

                    ResponseEntity<CocoApiResponse<Void>> response = handler.handleCocoException(
                            CocoCommonErrorCode.INVALID_ARGUMENT.exception("name"),
                            new ServletWebRequest(request));

                    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
                });
    }

    @Test
    void mapsTypedCocoExceptionsToHttpStatuses() {
        this.webContextRunner.run(context -> {
            CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            ServletWebRequest webRequest = new ServletWebRequest(request);

            assertEquals(HttpStatus.BAD_REQUEST,
                    handler.handleCocoException(CocoExceptions.request(CocoCommonErrorCode.INVALID_ARGUMENT, "name"),
                            webRequest).getStatusCode());
            assertEquals(HttpStatus.UNAUTHORIZED,
                    handler.handleCocoException(CocoCommonErrorCode.UNAUTHORIZED.unauthorized(),
                            webRequest).getStatusCode());
            assertEquals(HttpStatus.FORBIDDEN,
                    handler.handleCocoException(CocoCommonErrorCode.FORBIDDEN.forbidden(),
                            webRequest).getStatusCode());
            assertEquals(HttpStatus.NOT_FOUND,
                    handler.handleCocoException(CocoCommonErrorCode.NOT_FOUND.notFound("user"),
                            webRequest).getStatusCode());
            assertEquals(HttpStatus.CONFLICT,
                    handler.handleCocoException(CocoCommonErrorCode.CONFLICT.conflict("username"),
                            webRequest).getStatusCode());
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                    handler.handleCocoException(CocoCommonErrorCode.INTERNAL_ERROR.system(),
                            webRequest).getStatusCode());
        });
    }

    @Test
    void readsTraceIdFromRequestHeaderAndClearsContext() throws Exception {
        this.webContextRunner.run(context -> {
            CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                    FilterRegistrationBean.class));
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.addHeader("X-Trace-Id", " incoming-trace ");

            filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() ->
                    assertEquals("incoming-trace", CocoTraceContext.currentTraceId().orElseThrow()))));

            assertEquals("incoming-trace", response.getHeader("X-Trace-Id"));
            assertTrue(CocoRequestContextHolder.current().isEmpty());
            assertTrue(CocoTraceContext.currentTraceId().isEmpty());
        });
    }

    @Test
    void bindsRequestContextAndMdcDuringTraceFilter() throws Exception {
        this.webContextRunner.run(context -> {
            CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                    FilterRegistrationBean.class));
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.addHeader("X-Trace-Id", " incoming-trace ");

            filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() -> {
                CocoRequestContext requestContext = CocoRequestContextHolder.current().orElseThrow();
                assertEquals("incoming-trace", requestContext.traceId());
                assertEquals("POST", requestContext.method().orElseThrow());
                assertEquals("/api/users", requestContext.path().orElseThrow());
                assertEquals("incoming-trace", MDC.get("traceId"));
            })));

            assertTrue(CocoRequestContextHolder.current().isEmpty());
            assertNull(MDC.get("traceId"));
        });
    }

    @Test
    void generatesTraceIdWhenRequestHeaderIsMissing() throws Exception {
        this.webContextRunner.run(context -> {
            CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                    FilterRegistrationBean.class));
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String[] traceId = new String[1];

            filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() ->
                    traceId[0] = CocoTraceContext.currentTraceId().orElseThrow())));

            assertNotNull(traceId[0]);
            assertFalse(traceId[0].isBlank());
            assertEquals(traceId[0], response.getHeader("X-Trace-Id"));
            assertTrue(CocoTraceContext.currentTraceId().isEmpty());
        });
    }

    @Test
    void usesConfiguredTraceHeaderName() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.trace.header-name=X-Request-Id")
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Request-Id", "request-trace");

                    filter.doFilter(request, response, new MockFilterChain());

                    assertEquals("request-trace", response.getHeader("X-Request-Id"));
                    assertTrue(CocoTraceContext.currentTraceId().isEmpty());
                });
    }

    @Test
    void usesConfiguredMdcKey() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.trace.mdc-key=cocoTraceId")
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "mdc-trace");

                    filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() -> {
                        assertEquals("mdc-trace", MDC.get("cocoTraceId"));
                        assertNull(MDC.get("traceId"));
                    })));

                    assertNull(MDC.get("cocoTraceId"));
                });
    }

    @Test
    void restoresPreviousMdcValueAfterTraceFilter() throws Exception {
        MDC.put("traceId", "outer-trace");
        this.webContextRunner.run(context -> {
            CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                    FilterRegistrationBean.class));
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.addHeader("X-Trace-Id", "inner-trace");

            filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() ->
                    assertEquals("inner-trace", MDC.get("traceId")))));

            assertEquals("outer-trace", MDC.get("traceId"));
        });
    }

    @Test
    void recordsAccessLogAfterTraceFilterCompletes() throws Exception {
        CapturingAccessLogRecorder recorder = new CapturingAccessLogRecorder();
        this.webContextRunner
                .withBean(CocoAccessLogRecorder.class, () -> recorder)
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "access-trace");

                    filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() ->
                            response.setStatus(201))));

                    CocoAccessLog accessLog = recorder.lastAccessLog();
                    assertEquals("access-trace", accessLog.traceId());
                    assertEquals("POST", accessLog.method().orElseThrow());
                    assertEquals("/api/users", accessLog.path().orElseThrow());
                    assertEquals(201, accessLog.status());
                    assertTrue(accessLog.success());
                    assertTrue(accessLog.durationMillis() >= 0L);
                    assertTrue(accessLog.exceptionType().isEmpty());
                });
    }

    @Test
    void recordsFailedAccessLogWhenTraceFilterThrows() {
        CapturingAccessLogRecorder recorder = new CapturingAccessLogRecorder();
        this.webContextRunner
                .withBean(CocoAccessLogRecorder.class, () -> recorder)
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/fail");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "failed-trace");

                    assertThrows(ServletException.class, () -> filter.doFilter(request, response,
                            new MockFilterChain(new FailingServlet())));

                    CocoAccessLog accessLog = recorder.lastAccessLog();
                    assertEquals("failed-trace", accessLog.traceId());
                    assertEquals("GET", accessLog.method().orElseThrow());
                    assertEquals("/api/fail", accessLog.path().orElseThrow());
                    assertFalse(accessLog.success());
                    assertEquals(ServletException.class.getName(), accessLog.exceptionType().orElseThrow());
                });
    }

    @Test
    void ignoresAccessLogRecorderFailureAndStillClearsContext() {
        this.webContextRunner
                .withBean(CocoAccessLogRecorder.class, ThrowingAccessLogRecorder::new)
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "access-trace");

                    filter.doFilter(request, response, new MockFilterChain());

                    assertTrue(CocoRequestContextHolder.current().isEmpty());
                    assertTrue(CocoTraceContext.currentTraceId().isEmpty());
                    assertNull(MDC.get("traceId"));
                });
    }

    private static CocoTraceFilter traceFilter(FilterRegistrationBean<?> registrationBean) {
        return (CocoTraceFilter) registrationBean.getFilter();
    }

    private static CocoResponseWrapAdvice responseWrapAdvice(CocoMessageService messageService) {
        CocoResponseWrapProperties properties = new CocoResponseWrapProperties();
        properties.setSuccessMessageCode("coco.error.unknown");
        return new CocoResponseWrapAdvice(messageService, properties, new ObjectMapper());
    }

    private static MethodParameter methodParameter(String methodName) throws NoSuchMethodException {
        return methodParameter(CocoWebAutoConfigurationTest.class, methodName);
    }

    private static MethodParameter methodParameter(Class<?> declaringClass, String methodName)
            throws NoSuchMethodException {
        Method method = declaringClass.getDeclaredMethod(methodName);
        return new MethodParameter(method, -1);
    }

    private Object objectBody() {
        return Map.of("name", "Coco");
    }

    private String stringBody() {
        return "hello";
    }

    private CocoApiResponse<String> wrappedBody() {
        return CocoApiResponse.success("coco.success", "操作成功", "hello", null, null);
    }

    @CocoIgnoreResponseWrap
    private Object ignoredBody() {
        return Map.of("ignored", true);
    }

    private ResponseEntity<String> responseEntityBody() {
        return ResponseEntity.ok("hello");
    }

    @CocoIgnoreResponseWrap
    private static final class IgnoredController {

        private Object classIgnoredBody() {
            return Map.of("ignored", true);
        }
    }

    private static final class TestHttpMessageConverter implements HttpMessageConverter<Object> {

        @Override
        public boolean canRead(Class<?> clazz, MediaType mediaType) {
            return false;
        }

        @Override
        public boolean canWrite(Class<?> clazz, MediaType mediaType) {
            return true;
        }

        @Override
        public List<MediaType> getSupportedMediaTypes() {
            return List.of(MediaType.APPLICATION_JSON);
        }

        @Override
        public Object read(Class<? extends Object> clazz, HttpInputMessage inputMessage) {
            return null;
        }

        @Override
        public void write(Object body, MediaType contentType, HttpOutputMessage outputMessage) {
        }
    }

    private static final class TraceCapturingServlet implements Servlet {

        private final Runnable assertion;

        private TraceCapturingServlet(Runnable assertion) {
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
        public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
            this.assertion.run();
        }

        @Override
        public String getServletInfo() {
            return "trace-capturing-servlet";
        }

        @Override
        public void destroy() {
        }
    }

    private static final class FailingServlet implements Servlet {

        @Override
        public void init(ServletConfig config) {
        }

        @Override
        public ServletConfig getServletConfig() {
            return null;
        }

        @Override
        public void service(ServletRequest request, ServletResponse response) throws ServletException {
            throw new ServletException("boom");
        }

        @Override
        public String getServletInfo() {
            return "failing-servlet";
        }

        @Override
        public void destroy() {
        }
    }

    private static final class CapturingAccessLogRecorder implements CocoAccessLogRecorder {

        private final AtomicReference<CocoAccessLog> lastAccessLog = new AtomicReference<>();

        @Override
        public void record(CocoAccessLog accessLog) {
            this.lastAccessLog.set(accessLog);
        }

        private CocoAccessLog lastAccessLog() {
            return this.lastAccessLog.get();
        }
    }

    private static final class ThrowingAccessLogRecorder implements CocoAccessLogRecorder {

        @Override
        public void record(CocoAccessLog accessLog) {
            throw new IllegalStateException("recorder failed");
        }
    }
}
