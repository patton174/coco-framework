package io.github.coco.feature.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.context.CocoRequestContext;
import io.github.coco.common.context.CocoRequestContextHolder;
import io.github.coco.common.exception.CocoBusinessCode;
import io.github.coco.common.exception.CocoBusinessExceptions;
import io.github.coco.common.exception.CocoCommonErrorCode;
import io.github.coco.common.exception.CocoExceptions;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.common.logging.access.CocoAccessLog;
import io.github.coco.common.logging.access.CocoAccessLogFormatter;
import io.github.coco.common.logging.access.CocoAccessLogProperties;
import io.github.coco.common.logging.access.CocoAccessLogRecorder;
import io.github.coco.common.logging.access.CocoAccessLogStyle;
import io.github.coco.common.logging.access.DefaultCocoAccessLogFormatter;
import io.github.coco.common.trace.CocoTraceContext;
import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.body.CocoRequestBodyCachingFilter;
import io.github.coco.feature.web.body.CocoRequestBodyCachingMode;
import io.github.coco.feature.web.context.CocoBrowserFingerprint;
import io.github.coco.feature.web.context.CocoBrowserFingerprintResolver;
import io.github.coco.feature.web.context.CocoClientIpResolver;
import io.github.coco.feature.web.context.CocoRequestHeaderResolver;
import io.github.coco.feature.web.context.CocoRequestParameterResolver;
import io.github.coco.feature.web.context.CocoWebContextProperties;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalForm;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationContext;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationProperties;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationPurpose;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.context.CocoWebRequestSecurityInputResolver;
import io.github.coco.feature.web.context.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.context.CocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.DefaultCocoBrowserFingerprintResolver;
import io.github.coco.feature.web.context.DefaultCocoClientIpResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestHeaderResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestParameterResolver;
import io.github.coco.feature.web.context.DefaultCocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.DefaultCocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.encryption.CocoCryptoTextEncoding;
import io.github.coco.feature.web.encryption.CocoEncryptionFilter;
import io.github.coco.feature.web.encryption.CocoEncryptionProperties;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.replay.CocoReplayFilter;
import io.github.coco.feature.web.response.CocoApiResponse;
import io.github.coco.feature.web.response.CocoIgnoreResponseWrap;
import io.github.coco.feature.web.response.CocoResponseBodyFactory;
import io.github.coco.feature.web.response.CocoResponseMetadataMode;
import io.github.coco.feature.web.response.CocoResponsePayload;
import io.github.coco.feature.web.response.CocoResponseProperties;
import io.github.coco.feature.web.response.CocoResponseWrapAdvice;
import io.github.coco.feature.web.response.CocoResponseWrapProperties;
import io.github.coco.feature.web.response.CocoSystemCodeProvider;
import io.github.coco.feature.web.response.CocoSystemCodes;
import io.github.coco.feature.web.signature.CocoSignatureFilter;
import io.github.coco.feature.web.signature.CocoSignatureProperties;
import io.github.coco.feature.web.trace.CocoTraceFilter;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.i18n.LocaleContextHolder;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
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
        LocaleContextHolder.resetLocaleContext();
        RequestContextHolder.resetRequestAttributes();
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
            assertTrue(context.containsBean("cocoClientIpResolver"));
            assertTrue(context.containsBean("cocoBrowserFingerprintResolver"));
            assertTrue(context.containsBean("cocoRequestHeaderResolver"));
            assertTrue(context.containsBean("cocoRequestParameterResolver"));
            assertTrue(context.containsBean("cocoWebRequestSecurityInputResolver"));
            assertTrue(context.containsBean("cocoWebRequestSecurityMetadataResolver"));
            assertTrue(context.containsBean("cocoWebRequestCanonicalizer"));
            assertTrue(context.containsBean("cocoWebRequestContextResolver"));
            assertTrue(context.containsBean("cocoRequestBodyCachingFilterRegistration"));
            assertTrue(context.containsBean("cocoTraceFilterRegistration"));
            assertTrue(context.containsBean("cocoSignatureSecretResolver"));
            assertTrue(context.containsBean("cocoSignatureVerifier"));
            assertTrue(context.containsBean("cocoSignatureFilterRegistration"));
            assertTrue(context.containsBean("cocoEncryptionKeyResolver"));
            assertTrue(context.containsBean("cocoRequestDecryptor"));
            assertTrue(context.containsBean("cocoReplayKeyResolver"));
            assertTrue(context.containsBean("cocoReplayStore"));
            assertTrue(context.containsBean("cocoReplayFilterRegistration"));
            assertTrue(context.containsBean("cocoFilterExceptionResponseWriter"));
            assertTrue(context.containsBean("cocoEncryptionFilterRegistration"));
        });
    }

    @Test
    void createsResponseWrapAdviceByDefault() {
        this.webContextRunner.run(context -> {
            assertTrue(context.containsBean("cocoResponseBodyFactory"));
            assertTrue(context.containsBean("cocoResponseWrapAdvice"));
            assertNotNull(context.getBean(CocoResponseBodyFactory.class));
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
    void webModuleDoesNotCreateAccessLogPrinterByDefault() {
        this.webContextRunner.run(context -> {
            assertFalse(context.containsBean("cocoSlf4jAccessLogRecorder"));
            assertFalse(context.getBeansOfType(CocoAccessLogFormatter.class).containsKey("cocoAccessLogFormatter"));
        });
    }

    @Test
    void disablesAccessLogEventPublicationByProperty() throws Exception {
        CapturingAccessLogRecorder recorder = new CapturingAccessLogRecorder();
        this.webContextRunner
                .withPropertyValues("coco.web.access-log.enabled=false")
                .withBean(CocoAccessLogRecorder.class, () -> recorder)
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "access-disabled");

                    filter.doFilter(request, response, new MockFilterChain());

                    assertNull(recorder.lastAccessLog());
                });
    }

    @Test
    void registersLocalizedSuccessResponseMessage() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertEquals("操作成功", messageService.getMessage("coco.web.response.success"));
            assertEquals("检测到重复请求。", messageService.getMessage("coco.web.replay.detected"));
        });
    }

    @Test
    void resolvesWebMessageFromAcceptLanguageHeader() {
        this.webContextRunner.run(context -> {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            request.addHeader("Accept-Language", "en-US");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);

            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertEquals("Operation succeeded.", messageService.getMessage("coco.web.response.success"));
        });
    }

    @Test
    void fallsBackToCocoDefaultLocaleWhenAcceptLanguageHeaderIsMissing() {
        this.webContextRunner.run(context -> {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
            LocaleContextHolder.setLocale(Locale.US);

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

            ResponseEntity<Object> response = handler.handleCocoException(
                    CocoCommonErrorCode.INVALID_ARGUMENT.exception("name"),
                    new ServletWebRequest(request));

            CocoApiResponse<?> body = apiBody(response);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertFalse(body.success());
            assertEquals(400, body.code());
            assertEquals("参数不合法：name", body.message());
            assertNull(body.traceId());
            assertNull(body.path());
        });
    }

    @Test
    void returnsTraceMetadataInErrorResponseWhenDebugModeIsEnabled() {
        CocoTraceContext.setTraceId("trace-test");
        this.webContextRunner
                .withPropertyValues("coco.web.response.metadata-mode=debug")
                .run(context -> {
                    CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");

                    ResponseEntity<Object> response = handler.handleCocoException(
                            CocoCommonErrorCode.INVALID_ARGUMENT.exception("name"),
                            new ServletWebRequest(request));

                    CocoApiResponse<?> body = apiBody(response);
                    assertEquals("trace-test", body.traceId());
                    assertEquals("/api/users", body.path());
                });
    }

    @Test
    void returnsLocalizedErrorResponseForTypedCocoException() {
        this.webContextRunner.run(context -> {
            CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/404");

            ResponseEntity<Object> response = handler.handleCocoException(
                    CocoCommonErrorCode.NOT_FOUND.notFound("user"),
                    new ServletWebRequest(request));

            CocoApiResponse<?> body = apiBody(response);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertFalse(body.success());
            assertEquals(404, body.code());
            assertEquals("资源不存在：user", body.message());
            assertNull(body.traceId());
            assertNull(body.path());
        });
    }

    @Test
    void acceptsZeroResponseCode() {
        CocoApiResponse<Void> response = new CocoApiResponse<>(true, 0, "message", null, "trace", "/api/users");

        assertEquals(0, response.code());
    }

    @Test
    void createsSuccessResponseModel() {
        CocoApiResponse<String> response = CocoApiResponse.success(200, "操作成功", "payload");

        assertTrue(response.success());
        assertEquals(200, response.code());
        assertEquals("操作成功", response.message());
        assertEquals("payload", response.data());
        assertNull(response.traceId());
        assertNull(response.path());
    }

    @Test
    void responsePropertiesUseDefaultsAndResetNullNestedValue() {
        CocoWebProperties properties = new CocoWebProperties();

        assertEquals(CocoResponseMetadataMode.NONE, properties.getResponse().getMetadataMode());
        assertTrue(properties.getResponseWrap().isEnabled());
        assertEquals("coco.web.response.success", properties.getResponseWrap().getSuccessMessageCode());

        properties.setResponse(null);
        properties.setResponseWrap(null);

        assertEquals(CocoResponseMetadataMode.NONE, properties.getResponse().getMetadataMode());
        assertTrue(properties.getResponseWrap().isEnabled());
    }

    @Test
    void accessLogPropertiesUseDefaultsAndResetNullNestedValue() {
        CocoWebProperties properties = new CocoWebProperties();

        assertTrue(properties.getAccessLog().isEnabled());
        assertTrue(properties.getAccessLog().isIncludeParameters());
        assertEquals(256, properties.getAccessLog().getMaxParameterValueLength());
        assertTrue(properties.getAccessLog().getMaskedParameterNames().contains("token"));
        assertTrue(properties.getRequestBody().isEnabled());
        assertEquals(CocoRequestBodyCachingMode.SECURITY_HEADERS, properties.getRequestBody().getMode());
        assertEquals(1024 * 1024, properties.getRequestBody().getMaxCacheBytes());
        assertTrue(properties.getRequestBody().getCacheMethods().contains("POST"));
        assertTrue(properties.getRequestBody().getTriggerHeaderNames().contains("x-coco-sign"));
        assertTrue(properties.getRequestBody().getIncludedContentTypes().contains("application/json"));
        assertTrue(properties.getRequestBody().getExcludedContentTypePrefixes().contains("multipart/"));
        assertTrue(properties.getSignature().isEnabled());
        assertFalse(properties.getSignature().isRequired());
        assertEquals("X-Coco-App-Id", properties.getSignature().getAppIdHeaderName());
        assertEquals("X-Coco-Sign", properties.getSignature().getSignatureHeaderName());
        assertEquals("X-Coco-Sign-Algorithm", properties.getSignature().getAlgorithmHeaderName());
        assertEquals("HMAC-SHA256", properties.getSignature().getDefaultAlgorithm());
        assertEquals(300L, properties.getSignature().getMaxClockSkewSeconds());
        assertTrue(properties.getEncryption().isEnabled());
        assertFalse(properties.getEncryption().isRequired());
        assertEquals("X-Coco-Encrypted", properties.getEncryption().getEncryptedHeaderName());
        assertEquals("X-Coco-App-Id", properties.getEncryption().getAppIdHeaderName());
        assertEquals("X-Coco-Key-Id", properties.getEncryption().getKeyIdHeaderName());
        assertEquals("X-Coco-IV", properties.getEncryption().getIvHeaderName());
        assertEquals("X-Coco-Algorithm", properties.getEncryption().getAlgorithmHeaderName());
        assertEquals("AES-GCM", properties.getEncryption().getDefaultAlgorithm());
        assertEquals(CocoCryptoTextEncoding.BASE64, properties.getEncryption().getKeyEncoding());
        assertEquals(CocoCryptoTextEncoding.BASE64, properties.getEncryption().getIvEncoding());
        assertEquals(CocoCryptoTextEncoding.BASE64, properties.getEncryption().getPayloadEncoding());
        assertEquals(128, properties.getEncryption().getGcmTagLengthBits());
        assertTrue(properties.getReplay().isEnabled());
        assertFalse(properties.getReplay().isRequired());
        assertTrue(properties.getReplay().isProtectSignedRequests());
        assertFalse(properties.getReplay().isProtectEncryptedRequests());
        assertTrue(properties.getReplay().isIncludeMethod());
        assertTrue(properties.getReplay().isIncludePath());
        assertEquals(300L, properties.getReplay().getTtlSeconds());
        assertEquals(60L, properties.getReplay().getCleanupIntervalSeconds());
        assertTrue(properties.getContext().isIncludeHeaders());
        assertTrue(properties.getContext().getClientIpHeaderNames().contains("X-Forwarded-For"));
        assertTrue(properties.getContext().getIncludedHeaderNames().contains("user-agent"));
        assertTrue(properties.getContext().getMaskedHeaderNames().contains("authorization"));
        assertTrue(properties.getContext().getSecurityHeaderNames().contains("x-coco-sign"));
        assertTrue(properties.getContext().getSecurityHeaderNames().contains("x-coco-sign-algorithm"));
        assertTrue(properties.getContext().getCanonicalHeaderNames().contains("x-coco-timestamp"));
        assertTrue(properties.getContext().getCanonicalHeaderNames().contains("x-coco-sign-algorithm"));
        assertTrue(properties.getContext().getFingerprintHeaderNames().contains("sec-ch-ua"));
        assertEquals(256, properties.getContext().getMaxHeaderValueLength());
        assertEquals("coco-v1", properties.getContext().getCanonicalization().getVersion());
        assertFalse(properties.getContext().getCanonicalization().isIncludeVersion());
        assertFalse(properties.getContext().getCanonicalization().isIncludePurpose());
        assertTrue(properties.getContext().getCanonicalization().isIncludeMethod());
        assertTrue(properties.getContext().getCanonicalization().isIncludePath());
        assertTrue(properties.getContext().getCanonicalization().isIncludeQueryString());
        assertTrue(properties.getContext().getCanonicalization().isIncludeHeaders());
        assertTrue(properties.getContext().getCanonicalization().isIncludeParameters());
        assertTrue(properties.getContext().getCanonicalization().isIncludeBodySha256());
        assertTrue(properties.getContext().getCanonicalization().isIncludeBodyLength());
        assertTrue(properties.getContext().getCanonicalization().isSortParameterValues());
        assertEquals(",", properties.getContext().getCanonicalization().getParameterValueSeparator());

        properties.setAccessLog(null);
        properties.setRequestBody(null);
        properties.setSignature(null);
        properties.setEncryption(null);
        properties.setReplay(null);
        properties.setContext(null);

        assertTrue(properties.getAccessLog().isEnabled());
        assertTrue(properties.getRequestBody().isEnabled());
        assertTrue(properties.getSignature().isEnabled());
        assertTrue(properties.getEncryption().isEnabled());
        assertTrue(properties.getReplay().isEnabled());
        assertTrue(properties.getContext().isIncludeHeaders());
    }

    @Test
    void formatsAccessLogAsTextAndJson() {
        CocoAccessLog accessLog = CocoAccessLog.of("trace-1001", "post", "/sample/orders",
                201, 42L, true, null, "10.0.0.8", "PostmanRuntime/7.37",
                "sku=COCO-STARTER&token=******",
                Map.of("sku", List.of("COCO-STARTER"), "token", List.of("******")));
        CocoAccessLogProperties properties = new CocoAccessLogProperties();
        DefaultCocoAccessLogFormatter formatter = new DefaultCocoAccessLogFormatter();

        assertEquals("▸ request  POST /sample/orders?sku=COCO-STARTER&token=****** | trace=trace-1001 "
                        + "ip=10.0.0.8 ua=\"PostmanRuntime/7.37\" "
                        + "params=\"sku=COCO-STARTER&token=******\" ◂ response 201 42ms success=true",
                formatter.format(accessLog, properties));

        properties.setStyle(CocoAccessLogStyle.JSON);

        assertEquals("{\"traceId\":\"trace-1001\",\"method\":\"POST\",\"path\":\"/sample/orders\","
                        + "\"clientIp\":\"10.0.0.8\",\"queryString\":\"sku=COCO-STARTER&token=******\","
                        + "\"parameters\":{\"sku\":[\"COCO-STARTER\"],\"token\":[\"******\"]},"
                        + "\"userAgent\":\"PostmanRuntime/7.37\",\"status\":201,\"durationMs\":42,\"success\":true}",
                formatter.format(accessLog, properties));
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
            assertEquals(200, response.code());
            assertEquals("未知错误", response.message());
            assertEquals(Map.of("name", "Coco"), response.data());
            assertNull(response.traceId());
            assertNull(response.path());
        });
    }

    @Test
    void wrapsObjectResponseBodyWithDebugMetadata() throws Exception {
        CocoTraceContext.setTraceId("trace-wrap");
        this.webContextRunner
                .withPropertyValues("coco.web.response.metadata-mode=debug")
                .run(context -> {
                    CocoResponseWrapAdvice advice = context.getBean(CocoResponseWrapAdvice.class);
                    MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/users");
                    ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);

                    Object body = advice.beforeBodyWrite(Map.of("name", "Coco"), methodParameter("objectBody"),
                            MediaType.APPLICATION_JSON, TestHttpMessageConverter.class, request,
                            new ServletServerHttpResponse(new MockHttpServletResponse()));

                    assertTrue(body instanceof CocoApiResponse<?>);
                    CocoApiResponse<?> response = (CocoApiResponse<?>) body;
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
            assertFalse(((String) body).contains("\"traceId\""));
            assertFalse(((String) body).contains("\"path\""));
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

                    ResponseEntity<Object> response = handler.handleCocoException(
                            CocoCommonErrorCode.INVALID_ARGUMENT.exception("name"),
                            new ServletWebRequest(request));

                    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
                });
    }

    @Test
    void usesCustomSystemCodeProviderForDefaultSuccessAndExceptionCodes() throws Exception {
        CocoSystemCodeProvider codeProvider = CocoSystemCodes.builder()
                .success(0)
                .invalidArgument(100400)
                .notFound(100404)
                .build();
        this.webContextRunner
                .withBean(CocoSystemCodeProvider.class, () -> codeProvider)
                .run(context -> {
                    CocoResponseWrapAdvice advice = responseWrapAdvice(context.getBean(CocoMessageService.class),
                            context.getBean(CocoSystemCodeProvider.class));
                    MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/users");
                    ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);

                    Object body = advice.beforeBodyWrite(Map.of("name", "Coco"), methodParameter("objectBody"),
                            MediaType.APPLICATION_JSON, TestHttpMessageConverter.class, request,
                            new ServletServerHttpResponse(new MockHttpServletResponse()));

                    assertTrue(body instanceof CocoApiResponse<?>);
                    assertEquals(0, ((CocoApiResponse<?>) body).code());

                    CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
                    ResponseEntity<Object> response = handler.handleCocoException(
                            CocoCommonErrorCode.NOT_FOUND.notFound("user"),
                            new ServletWebRequest(new MockHttpServletRequest("GET", "/api/users/404")));

                    assertEquals(100404, apiBody(response).code());
                });
    }

    @Test
    void usesCustomResponseBodyFactoryForSuccessAndErrorBodies() throws Exception {
        CocoResponseBodyFactory responseBodyFactory = new CocoResponseBodyFactory() {

            @Override
            public Object success(CocoResponsePayload<?> payload) {
                return Map.of("ok", payload.success(), "status", payload.code(), "result", payload.data());
            }

            @Override
            public Object error(CocoResponsePayload<?> payload) {
                return Map.of("ok", payload.success(), "status", payload.code(), "error", payload.message());
            }
        };
        this.webContextRunner
                .withBean(CocoResponseBodyFactory.class, () -> responseBodyFactory)
                .run(context -> {
                    CocoResponseWrapAdvice advice = context.getBean(CocoResponseWrapAdvice.class);
                    MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/users");
                    ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);

                    Object body = advice.beforeBodyWrite(Map.of("name", "Coco"), methodParameter("objectBody"),
                            MediaType.APPLICATION_JSON, TestHttpMessageConverter.class, request,
                            new ServletServerHttpResponse(new MockHttpServletResponse()));

                    assertEquals(Map.of("ok", true, "status", 200, "result", Map.of("name", "Coco")), body);

                    CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
                    ResponseEntity<Object> error = handler.handleCocoException(
                            CocoCommonErrorCode.INVALID_ARGUMENT.exception("name"),
                            new ServletWebRequest(new MockHttpServletRequest("GET", "/api/users")));

                    assertEquals(Map.of("ok", false, "status", 400, "error", "参数不合法：name"), error.getBody());
                });
    }

    @Test
    void businessCodeOverridesCustomSystemCodeProvider() {
        CocoSystemCodeProvider codeProvider = CocoSystemCodes.builder()
                .notFound(100404)
                .build();
        this.webContextRunner
                .withBean(CocoSystemCodeProvider.class, () -> codeProvider)
                .run(context -> {
                    CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
                    ResponseEntity<Object> response = handler.handleCocoException(
                            CocoBusinessExceptions.notFound(TestBusinessCode.ORDER_NOT_FOUND, "ORD-1001"),
                            new ServletWebRequest(new MockHttpServletRequest("GET", "/api/orders/ORD-1001")));

                    CocoApiResponse<?> body = apiBody(response);
                    assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
                    assertEquals(200001, body.code());
                    assertEquals("资源不存在：ORD-1001", body.message());
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
    void skipsTraceResponseHeaderWhenConfigured() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.trace.response-header-enabled=false")
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "incoming-trace");

                    filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() ->
                            assertEquals("incoming-trace", CocoTraceContext.currentTraceId().orElseThrow()))));

                    assertNull(response.getHeader("X-Trace-Id"));
                    assertTrue(CocoTraceContext.currentTraceId().isEmpty());
                });
    }

    @Test
    void writesTraceIdToCookieWhenConfigured() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.trace.response-cookie-enabled=true",
                        "coco.web.trace.cookie-name=COCO_TRACE",
                        "coco.web.trace.cookie-path=/sample",
                        "coco.web.trace.cookie-max-age=60")
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "cookie-trace");

                    filter.doFilter(request, response, new MockFilterChain());

                    String cookie = response.getHeader("Set-Cookie");
                    assertNotNull(cookie);
                    assertTrue(cookie.contains("COCO_TRACE=cookie-trace"));
                    assertTrue(cookie.contains("Path=/sample"));
                    assertTrue(cookie.contains("Max-Age=60"));
                    assertTrue(cookie.contains("SameSite=Lax"));
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
            request.addHeader("X-Forwarded-For", " 10.0.0.8, 10.0.0.9 ");
            request.addHeader("User-Agent", "PostmanRuntime/7.37");
            request.addHeader("Accept-Language", "zh-CN");
            request.setContentType("application/json");
            request.setScheme("https");
            request.setServerName("api.example.test");
            request.setServerPort(8443);
            request.setQueryString("name=Coco&token=abc");
            request.addParameter("name", "Coco");
            request.addParameter("token", "abc");
            request.addPreferredLocale(Locale.SIMPLIFIED_CHINESE);

            filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() -> {
                CocoRequestContext requestContext = CocoRequestContextHolder.current().orElseThrow();
                assertEquals("incoming-trace", requestContext.traceId());
                assertEquals("POST", requestContext.method().orElseThrow());
                assertEquals("/api/users", requestContext.path().orElseThrow());
                assertEquals("10.0.0.8", requestContext.clientIp().orElseThrow());
                assertEquals("PostmanRuntime/7.37", requestContext.userAgent().orElseThrow());
                assertEquals("name=Coco&token=******", requestContext.queryString().orElseThrow());
                assertEquals("zh-CN", requestContext.locale().orElseThrow());
                assertEquals("https", requestContext.attribute("scheme").orElseThrow());
                assertEquals("api.example.test", requestContext.attribute("host").orElseThrow());
                assertEquals("8443", requestContext.attribute("port").orElseThrow());
                assertEquals("application/json", requestContext.attribute("contentType").orElseThrow());
                assertTrue(requestContext.browserFingerprint().orElseThrow().length() == 64);
                assertTrue(requestContext.header("accept-language").orElseThrow().contains("zh-cn"));
                assertEquals("Coco", requestContext.parameter("name").orElseThrow());
                assertEquals("******", requestContext.parameter("token").orElseThrow());
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
    void sanitizesForwardedIpHeadersAndSensitiveContextValues() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.context.included-header-names=authorization,accept-language",
                        "coco.web.context.max-header-value-length=8",
                        "coco.web.access-log.max-parameter-value-length=4")
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "context-trace");
                    request.addHeader("Forwarded", "for=\"[2001:db8:cafe::17]:4711\";proto=https");
                    request.addHeader("Authorization", "Bearer secret-token");
                    request.addHeader("Accept-Language", "zh-CN,en;q=0.8");
                    request.setQueryString("password=abcdef&name=Coconut");
                    request.addParameter("password", "abcdef");
                    request.addParameter("name", "Coconut");

                    filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() -> {
                        CocoRequestContext requestContext = CocoRequestContextHolder.current().orElseThrow();
                        assertEquals("2001:db8:cafe::17", requestContext.clientIp().orElseThrow());
                        assertEquals("password=******&name=Coco...", requestContext.queryString().orElseThrow());
                        assertEquals("******", requestContext.header("authorization").orElseThrow());
                        assertEquals("zh-CN,en...", requestContext.header("accept-language").orElseThrow());
                        assertEquals("******", requestContext.parameter("password").orElseThrow());
                        assertEquals("Coco...", requestContext.parameter("name").orElseThrow());
                    })));
                });
    }

    @Test
    void resolvesSecurityInputFingerprintAndCanonicalForm() {
        this.webContextRunner.run(context -> {
            CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
            CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
            byte[] body = "{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8);
            request.setQueryString("sku=COCO-STARTER&token=abc");
            request.addParameter("sku", "COCO-STARTER");
            request.addParameter("token", "abc");
            request.addHeader("Content-Type", "application/json");
            request.addHeader("X-Coco-App-Id", "sample-app");
            request.addHeader("X-Coco-Timestamp", "1783300000000");
            request.addHeader("X-Coco-Nonce", "nonce-1001");
            request.addHeader("X-Coco-Sign", "sign-value");
            request.addHeader("X-Coco-Encrypted", "true");
            request.addHeader("X-Coco-Key-Id", "key-1");
            request.addHeader("X-Coco-IV", "iv-1");
            request.addHeader("X-Coco-Algorithm", "AES/GCM/NoPadding");
            request.addHeader("User-Agent", "Chrome/126");
            request.addHeader("Sec-CH-UA", "\"Chromium\";v=\"126\"");
            request.addHeader("Sec-CH-UA-Platform", "\"Windows\"");

            CocoWebRequestSnapshot snapshot = resolver.resolve("security-trace",
                    new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
            CocoWebRequestCanonicalForm canonicalForm = canonicalizer.canonicalize(snapshot.securityInput());

            assertEquals("sign-value", snapshot.securityInput().securityHeader("x-coco-sign").orElseThrow());
            assertEquals("true", snapshot.securityInput().securityHeader("x-coco-encrypted").orElseThrow());
            assertEquals("1783300000000",
                    snapshot.securityInput().canonicalHeader("x-coco-timestamp").orElseThrow());
            assertEquals(List.of("COCO-STARTER"), snapshot.securityInput().parameter("sku").orElseThrow());
            assertEquals("sku=COCO-STARTER&token=abc", snapshot.securityInput().queryString());
            assertEquals(sha256(body), snapshot.securityInput().bodySha256());
            assertEquals(body.length, snapshot.securityInput().bodyLength());
            assertTrue(snapshot.securityInput().bodyCached());
            assertTrue(snapshot.browserFingerprint().value().length() == 64);
            assertEquals(snapshot.browserFingerprint().value(),
                    snapshot.toRequestContext().browserFingerprint().orElseThrow());
            assertEquals(sha256(body), snapshot.toRequestContext().requestBodySha256().orElseThrow());
            assertTrue(canonicalForm.text().contains("method=POST"));
            assertTrue(canonicalForm.text().contains("path=/api/orders"));
            assertTrue(canonicalForm.text().contains("x-coco-timestamp:1783300000000"));
            assertTrue(canonicalForm.text().contains("bodySha256=" + sha256(body)));
            assertTrue(canonicalForm.text().contains("bodyLength=" + body.length));
            assertTrue(canonicalForm.sha256().length() == 64);
        });
    }

    @Test
    void defaultRequestCanonicalizerBuildsStableTextAndDigest() {
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("post", "/api/orders",
                "tag=b&tag=a&sku=COCO-STARTER",
                Map.of("tag", List.of("b", "a"), "sku", List.of("COCO-STARTER")),
                Map.of(), Map.of("x-coco-timestamp", "1783300000000", "content-type", "application/json"),
                "body-digest", 12L, true);

        CocoWebRequestCanonicalForm canonicalForm = new DefaultCocoWebRequestCanonicalizer().canonicalize(input);

        String expectedText = "method=POST\n"
                + "path=/api/orders\n"
                + "query=tag=b&tag=a&sku=COCO-STARTER\n"
                + "headers\n"
                + "content-type:application/json\n"
                + "x-coco-timestamp:1783300000000\n"
                + "parameters\n"
                + "sku=COCO-STARTER\n"
                + "tag=a,b\n"
                + "bodySha256=body-digest\n"
                + "bodyLength=12\n";
        assertEquals(expectedText, canonicalForm.text());
        assertEquals(sha256(expectedText), canonicalForm.sha256());
    }

    @Test
    void requestCanonicalizerCanIncludeVersionAndPurpose() {
        CocoWebRequestCanonicalizationProperties properties = new CocoWebRequestCanonicalizationProperties();
        properties.setVersion("coco-v2");
        properties.setIncludeVersion(true);
        properties.setIncludePurpose(true);
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders", null,
                Map.of(), Map.of(), Map.of(), null, null, false);
        CocoWebRequestCanonicalizationContext canonicalizationContext = new CocoWebRequestCanonicalizationContext(
                CocoWebRequestCanonicalizationPurpose.SIGNATURE, input, null, CocoBrowserFingerprint.empty());

        CocoWebRequestCanonicalForm canonicalForm = new DefaultCocoWebRequestCanonicalizer(properties)
                .canonicalize(canonicalizationContext);

        assertTrue(canonicalForm.text().startsWith("version=coco-v2\npurpose=SIGNATURE\n"));
        assertEquals(sha256(canonicalForm.text()), canonicalForm.sha256());
    }

    @Test
    void customizesCanonicalFormFromConfiguration() {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.context.canonicalization.version=coco-v2",
                        "coco.web.context.canonicalization.include-version=true",
                        "coco.web.context.canonicalization.include-purpose=true",
                        "coco.web.context.canonicalization.include-query-string=false",
                        "coco.web.context.canonicalization.include-headers=false",
                        "coco.web.context.canonicalization.include-body-length=false",
                        "coco.web.context.canonicalization.sort-parameter-values=false",
                        "coco.web.context.canonicalization.parameter-value-separator=;")
                .run(context -> {
                    CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
                    CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders",
                            "sku=COCO-STARTER&tag=b&tag=a",
                            Map.of("tag", List.of("b", "a"), "sku", List.of("COCO-STARTER")),
                            Map.of(), Map.of("x-coco-timestamp", "1783300000000"), "body-digest", 12L, true);

                    CocoWebRequestCanonicalForm canonicalForm = canonicalizer.canonicalize(input);

                    assertTrue(canonicalForm.text().contains("version=coco-v2"));
                    assertTrue(canonicalForm.text().contains("purpose=GENERAL"));
                    assertTrue(canonicalForm.text().contains("method=POST"));
                    assertTrue(canonicalForm.text().contains("path=/api/orders"));
                    assertFalse(canonicalForm.text().contains("query="));
                    assertFalse(canonicalForm.text().contains("headers"));
                    assertTrue(canonicalForm.text().contains("parameters"));
                    assertTrue(canonicalForm.text().contains("tag=b;a"));
                    assertTrue(canonicalForm.text().contains("bodySha256=body-digest"));
                    assertFalse(canonicalForm.text().contains("bodyLength="));
                    assertEquals(64, canonicalForm.sha256().length());
                });
    }

    @Test
    void defaultClientIpResolverParsesForwardedHeaders() {
        CocoClientIpResolver resolver = new DefaultCocoClientIpResolver(new CocoWebContextProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("Forwarded", "for=\"[2001:db8:cafe::17]:4711\";proto=https");
        request.addHeader("X-Forwarded-For", "10.0.0.8");

        assertEquals("2001:db8:cafe::17", resolver.resolve(request));
    }

    @Test
    void defaultBrowserFingerprintResolverUsesConfiguredHeaderSignals() {
        CocoWebProperties properties = new CocoWebProperties();
        properties.getContext().setFingerprintHeaderNames(Set.of("User-Agent", "Sec-CH-UA"));
        properties.getContext().setMaxHeaderValueLength(8);
        CocoBrowserFingerprintResolver resolver = new DefaultCocoBrowserFingerprintResolver(properties.getContext());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("User-Agent", " Chrome/126 ");
        request.addHeader("Sec-CH-UA", "\"Chromium\";v=\"126\"");

        CocoBrowserFingerprint fingerprint = resolver.resolve(request);

        assertNotNull(fingerprint.value());
        assertEquals("Chrome/1...", fingerprint.signals().get("user-agent"));
        assertEquals("\"Chromiu...", fingerprint.signals().get("sec-ch-ua"));
    }

    @Test
    void defaultRequestHeaderResolverSanitizesContextHeadersAndKeepsSelectedHeadersRaw() {
        CocoWebProperties properties = new CocoWebProperties();
        properties.getContext().setIncludedHeaderNames(Set.of("Authorization", "X-Name"));
        properties.getContext().setMaskedHeaderNames(Set.of("Authorization"));
        properties.getContext().setMaxHeaderValueLength(4);
        CocoRequestHeaderResolver resolver = new DefaultCocoRequestHeaderResolver(properties.getContext());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("Authorization", "Bearer secret");
        request.addHeader("X-Name", "Coconut");

        Map<String, String> includedHeaders = resolver.resolveIncludedHeaders(request);
        Map<String, String> selectedHeaders = resolver.resolveSelectedHeaders(request, Set.of("X-Name"), true);

        assertEquals("******", includedHeaders.get("authorization"));
        assertEquals("Coco...", includedHeaders.get("x-name"));
        assertEquals("Coco...", selectedHeaders.get("x-name"));
    }

    @Test
    void defaultRequestParameterResolverProvidesSanitizedAndRawViews() {
        CocoWebProperties properties = new CocoWebProperties();
        properties.getAccessLog().setMaxParameterValueLength(4);
        CocoRequestParameterResolver resolver = new DefaultCocoRequestParameterResolver(properties.getAccessLog());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setQueryString("password=abcdef&name=Coconut");
        request.addParameter("password", "abcdef");
        request.addParameter("name", "Coconut");

        assertEquals("password=******&name=Coco...", resolver.resolveQueryString(request));
        assertEquals("password=abcdef&name=Coconut", resolver.resolveRawQueryString(request));
        assertEquals(List.of("******"), resolver.resolveParameters(request).get("password"));
        assertEquals(List.of("Coco..."), resolver.resolveParameters(request).get("name"));
        assertEquals(List.of("abcdef"), resolver.resolveRawParameters(request).get("password"));
    }

    @Test
    void usesCustomClientIpResolverInRequestContext() {
        CocoClientIpResolver resolver = request -> "203.0.113.8";
        this.webContextRunner
                .withBean(CocoClientIpResolver.class, () -> resolver)
                .run(context -> {
                    CocoWebRequestContextResolver contextResolver = context.getBean(CocoWebRequestContextResolver.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    request.addHeader("X-Forwarded-For", "10.0.0.8");

                    CocoWebRequestSnapshot snapshot = contextResolver.resolve("custom-ip-trace", request);

                    assertEquals("203.0.113.8", snapshot.clientIp());
                    assertEquals("203.0.113.8", snapshot.toRequestContext().clientIp().orElseThrow());
                });
    }

    @Test
    void usesCustomRequestHeaderResolverInRequestContext() {
        CocoRequestHeaderResolver resolver = new CocoRequestHeaderResolver() {

            @Override
            public Map<String, String> resolveIncludedHeaders(HttpServletRequest request) {
                return Map.of("x-context", "visible");
            }

            @Override
            public Map<String, String> resolveSelectedHeaders(HttpServletRequest request, Iterable<String> headerNames,
                    boolean trimValue) {
                return Map.of("x-selected", "selected-" + trimValue);
            }
        };
        this.webContextRunner
                .withBean(CocoRequestHeaderResolver.class, () -> resolver)
                .run(context -> {
                    CocoWebRequestContextResolver contextResolver = context.getBean(CocoWebRequestContextResolver.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");

                    CocoWebRequestSnapshot snapshot = contextResolver.resolve("custom-header-trace", request);

                    assertEquals("visible", snapshot.headers().get("x-context"));
                    assertEquals("selected-false",
                            snapshot.securityInput().securityHeader("x-selected").orElseThrow());
                    assertEquals("selected-false",
                            snapshot.securityInput().canonicalHeader("x-selected").orElseThrow());
                });
    }

    @Test
    void usesCustomRequestParameterResolverInRequestContext() {
        CocoRequestParameterResolver resolver = new CocoRequestParameterResolver() {

            @Override
            public String resolveQueryString(HttpServletRequest request) {
                return "clean=query";
            }

            @Override
            public String resolveRawQueryString(HttpServletRequest request) {
                return "raw=query";
            }

            @Override
            public Map<String, List<String>> resolveParameters(HttpServletRequest request) {
                return Map.of("clean", List.of("yes"));
            }

            @Override
            public Map<String, List<String>> resolveRawParameters(HttpServletRequest request) {
                return Map.of("raw", List.of("yes"));
            }
        };
        this.webContextRunner
                .withBean(CocoRequestParameterResolver.class, () -> resolver)
                .run(context -> {
                    CocoWebRequestContextResolver contextResolver = context.getBean(CocoWebRequestContextResolver.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");

                    CocoWebRequestSnapshot snapshot = contextResolver.resolve("custom-parameter-trace", request);

                    assertEquals("clean=query", snapshot.queryString());
                    assertEquals(List.of("yes"), snapshot.parameters().get("clean"));
                    assertEquals("raw=query", snapshot.securityInput().queryString());
                    assertEquals(List.of("yes"), snapshot.securityInput().parameter("raw").orElseThrow());
                });
    }

    @Test
    void usesCustomSecurityInputResolverInRequestContext() {
        CocoWebRequestSecurityInputResolver resolver = (request, method, path) -> new CocoWebRequestSecurityInput(
                method, path, "secure=query", Map.of("secure", List.of("yes")),
                Map.of("x-secure", "header"), Map.of("x-canonical", "header"), "body-sha256", 11L, true);
        this.webContextRunner
                .withBean(CocoWebRequestSecurityInputResolver.class, () -> resolver)
                .run(context -> {
                    CocoWebRequestContextResolver contextResolver = context.getBean(CocoWebRequestContextResolver.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");

                    CocoWebRequestSnapshot snapshot = contextResolver.resolve("custom-security-input-trace", request);

                    assertEquals("secure=query", snapshot.securityInput().queryString());
                    assertEquals(List.of("yes"), snapshot.securityInput().parameter("secure").orElseThrow());
                    assertEquals("header", snapshot.securityInput().securityHeader("x-secure").orElseThrow());
                    assertEquals("header", snapshot.securityInput().canonicalHeader("x-canonical").orElseThrow());
                    assertEquals("body-sha256", snapshot.securityInput().bodySha256());
                    assertEquals(11L, snapshot.securityInput().bodyLength());
                    assertTrue(snapshot.securityInput().bodyCached());
                });
    }

    @Test
    void resolvesSecurityMetadataFromSecurityInput() {
        CocoWebRequestSecurityMetadataResolver resolver = new DefaultCocoWebRequestSecurityMetadataResolver(
                new CocoSignatureProperties(), new CocoEncryptionProperties());
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders", null, Map.of(),
                Map.of(
                        "x-coco-app-id", "sample-app",
                        "x-coco-key-id", "key-1001",
                        "x-coco-timestamp", "1700000000000",
                        "x-coco-nonce", "nonce-1001",
                        "x-coco-sign", "signature-1001",
                        "x-coco-encrypted", "true",
                        "x-coco-iv", "iv-1001"),
                Map.of(), null, null, false);

        CocoWebRequestSecurityMetadata metadata = resolver.resolve(input);

        assertEquals("sample-app", metadata.signatureAppId());
        assertEquals("key-1001", metadata.signatureKeyId());
        assertEquals("1700000000000", metadata.signatureTimestamp());
        assertEquals("nonce-1001", metadata.signatureNonce());
        assertEquals("HMAC-SHA256", metadata.signatureAlgorithm());
        assertEquals("signature-1001", metadata.signature());
        assertTrue(metadata.signed());
        assertEquals("sample-app", metadata.encryptionAppId());
        assertEquals("key-1001", metadata.encryptionKeyId());
        assertEquals("iv-1001", metadata.encryptionIv());
        assertEquals("AES-GCM", metadata.encryptionAlgorithm());
        assertTrue(metadata.encrypted());
        assertEquals("sample-app", metadata.primaryAppId().orElseThrow());
        assertEquals("key-1001", metadata.primaryKeyId().orElseThrow());
    }

    @Test
    void usesCustomBrowserFingerprintResolverInRequestContext() {
        CocoBrowserFingerprintResolver resolver = request ->
                new CocoBrowserFingerprint("custom-browser-fingerprint", Map.of("custom", "yes"));
        this.webContextRunner
                .withBean(CocoBrowserFingerprintResolver.class, () -> resolver)
                .run(context -> {
                    CocoWebRequestContextResolver contextResolver = context.getBean(CocoWebRequestContextResolver.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");

                    CocoWebRequestSnapshot snapshot = contextResolver.resolve("custom-fingerprint-trace", request);

                    assertEquals("custom-browser-fingerprint", snapshot.browserFingerprint().value());
                    assertEquals("custom-browser-fingerprint",
                            snapshot.toRequestContext().browserFingerprint().orElseThrow());
                    assertEquals("yes", snapshot.browserFingerprint().signals().get("custom"));
                });
    }

    @Test
    void cachesSignedJsonRequestBodyBeforeTraceContextResolution() throws Exception {
        this.webContextRunner.run(context -> {
            CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                    context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
            CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                    FilterRegistrationBean.class));
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
            MockHttpServletResponse response = new MockHttpServletResponse();
            byte[] body = "{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8);
            AtomicReference<String> downstreamBody = new AtomicReference<>();
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.setContent(body);
            request.addHeader("X-Trace-Id", "signed-body-trace");
            request.addHeader("X-Coco-Sign", "sign-value");

            bodyFilter.doFilter(request, response, (wrappedRequest, wrappedResponse) ->
                    traceFilter.doFilter(wrappedRequest, wrappedResponse,
                            new MockFilterChain(new BodyReadingServlet(downstreamBody, () -> {
                                CocoRequestContext requestContext = CocoRequestContextHolder.current().orElseThrow();
                                assertEquals(sha256(body), requestContext.requestBodySha256().orElseThrow());
                            }))));

            assertEquals("{\"sku\":\"COCO-STARTER\"}", downstreamBody.get());
        });
    }

    @Test
    void verifiesSignedJsonRequestBeforeBusinessServlet() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.signature.secrets.sample-app=sample-secret")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = signedRequest(context, "valid-signature", "sample-secret");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<Boolean> reachedBusiness = new AtomicReference<>(false);

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse,
                                            new MockFilterChain(new TraceCapturingServlet(() ->
                                                    reachedBusiness.set(true))))));

                    assertEquals(200, response.getStatus());
                    assertEquals(Boolean.TRUE, reachedBusiness.get());
                });
    }

    @Test
    void rejectsReplayedSignedJsonRequest() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.signature.secrets.sample-app=sample-secret")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    CocoReplayFilter replayFilter = replayFilter(context.getBean(
                            "cocoReplayFilterRegistration", FilterRegistrationBean.class));
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String nonce = "nonce-replay-1001";
                    MockHttpServletRequest firstRequest = signedRequest(context, "replay-first", "sample-secret",
                            timestamp, nonce);
                    MockHttpServletResponse firstResponse = new MockHttpServletResponse();
                    AtomicReference<Boolean> reachedBusiness = new AtomicReference<>(false);

                    bodyFilter.doFilter(firstRequest, firstResponse, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse,
                                            (signatureRequest, signatureResponse) ->
                                                    replayFilter.doFilter(signatureRequest, signatureResponse,
                                                            new MockFilterChain(new TraceCapturingServlet(() ->
                                                                    reachedBusiness.set(true)))))));

                    MockHttpServletRequest replayRequest = signedRequest(context, "replay-second", "sample-secret",
                            timestamp, nonce);
                    replayRequest.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse replayResponse = new MockHttpServletResponse();

                    bodyFilter.doFilter(replayRequest, replayResponse, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse,
                                            (signatureRequest, signatureResponse) ->
                                                    replayFilter.doFilter(signatureRequest, signatureResponse,
                                                            new MockFilterChain()))));

                    assertEquals(200, firstResponse.getStatus());
                    assertEquals(Boolean.TRUE, reachedBusiness.get());
                    assertEquals(401, replayResponse.getStatus());
                    Map<?, ?> body = new ObjectMapper().readValue(replayResponse.getContentAsString(), Map.class);
                    assertEquals(Boolean.FALSE, body.get("success"));
                    assertEquals(401, body.get("code"));
                    assertEquals("Request replay has been detected.", body.get("message"));
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenSignatureIsInvalid() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.signature.secrets.sample-app=sample-secret")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = signedRequest(context, "invalid-signature", "sample-secret");
                    request.removeHeader("X-Coco-Sign");
                    request.addHeader("X-Coco-Sign", "bad-signature");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertEquals(401, response.getStatus());
                    Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
                    assertEquals(Boolean.FALSE, body.get("success"));
                    assertEquals(401, body.get("code"));
                    assertEquals("Request signature is invalid.", body.get("message"));
                });
    }

    @Test
    void decryptsEncryptedJsonRequestBeforeBusinessServlet() throws Exception {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        this.webContextRunner
                .withPropertyValues("coco.web.encryption.keys.sample-app="
                        + Base64.getEncoder().encodeToString(key))
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoEncryptionFilter encryptionFilter = encryptionFilter(context.getBean(
                            "cocoEncryptionFilterRegistration", FilterRegistrationBean.class));
                    byte[] plainBody = "{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8);
                    MockHttpServletRequest request = encryptedRequest(plainBody, key, "encrypted-body-trace");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<String> downstreamBody = new AtomicReference<>();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    encryptionFilter.doFilter(traceRequest, traceResponse,
                                            new MockFilterChain(new BodyReadingServlet(downstreamBody, () -> {
                                            })))));

                    assertEquals(200, response.getStatus());
                    assertEquals("{\"sku\":\"COCO-STARTER\"}", downstreamBody.get());
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenEncryptedPayloadIsInvalid() throws Exception {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        this.webContextRunner
                .withPropertyValues("coco.web.encryption.keys.sample-app="
                        + Base64.getEncoder().encodeToString(key))
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoEncryptionFilter encryptionFilter = encryptionFilter(context.getBean(
                            "cocoEncryptionFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    request.setContent("invalid-ciphertext".getBytes(StandardCharsets.UTF_8));
                    request.addHeader("Accept-Language", "en-US");
                    request.addHeader("X-Trace-Id", "invalid-encrypted-body");
                    request.addHeader("X-Coco-Encrypted", "true");
                    request.addHeader("X-Coco-App-Id", "sample-app");
                    request.addHeader("X-Coco-IV", Base64.getEncoder()
                            .encodeToString("123456789012".getBytes(StandardCharsets.UTF_8)));
                    request.addHeader("X-Coco-Algorithm", "AES-GCM");

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    encryptionFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertEquals(401, response.getStatus());
                    Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
                    assertEquals(Boolean.FALSE, body.get("success"));
                    assertEquals(401, body.get("code"));
                    assertEquals("Request encryption payload is invalid.", body.get("message"));
                });
    }

    @Test
    void usesCustomWebRequestContextResolver() throws Exception {
        CapturingAccessLogRecorder recorder = new CapturingAccessLogRecorder();
        CocoWebRequestContextResolver resolver = (traceId, request) -> new CocoWebRequestSnapshot(traceId,
                "PATCH", "/custom/context", null, "127.0.0.7", "custom-agent", "en-US",
                "http", "custom.example.test", 8081, null, Map.of("x-custom", "yes"),
                Map.of("custom", List.of("value")));
        this.webContextRunner
                .withBean(CocoAccessLogRecorder.class, () -> recorder)
                .withBean(CocoWebRequestContextResolver.class, () -> resolver)
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "custom-context");

                    filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() -> {
                        CocoRequestContext requestContext = CocoRequestContextHolder.current().orElseThrow();
                        assertEquals("PATCH", requestContext.method().orElseThrow());
                        assertEquals("/custom/context", requestContext.path().orElseThrow());
                        assertEquals("127.0.0.7", requestContext.clientIp().orElseThrow());
                        assertEquals("yes", requestContext.header("x-custom").orElseThrow());
                        assertEquals("value", requestContext.parameter("custom").orElseThrow());
                    })));

                    CocoAccessLog accessLog = recorder.lastAccessLog();
                    assertEquals("PATCH", accessLog.method().orElseThrow());
                    assertEquals("/custom/context", accessLog.path().orElseThrow());
                    assertEquals("127.0.0.7", accessLog.clientIp().orElseThrow());
                    assertEquals(List.of("value"), accessLog.requestParameters().get("custom"));
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
                    request.addHeader("X-Forwarded-For", " 10.0.0.8, 10.0.0.9 ");
                    request.addHeader("User-Agent", "PostmanRuntime/7.37");
                    request.setQueryString("name=Coco&token=abc");
                    request.addParameter("name", "Coco");
                    request.addParameter("token", "abc");

                    filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() ->
                            response.setStatus(201))));

                    CocoAccessLog accessLog = recorder.lastAccessLog();
                    assertEquals("access-trace", accessLog.traceId());
                    assertEquals("POST", accessLog.method().orElseThrow());
                    assertEquals("/api/users", accessLog.path().orElseThrow());
                    assertEquals("10.0.0.8", accessLog.clientIp().orElseThrow());
                    assertEquals("PostmanRuntime/7.37", accessLog.userAgent().orElseThrow());
                    assertEquals("name=Coco&token=******", accessLog.queryString().orElseThrow());
                    assertEquals(List.of("Coco"), accessLog.requestParameters().get("name"));
                    assertEquals(List.of("******"), accessLog.requestParameters().get("token"));
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

    private static CocoRequestBodyCachingFilter requestBodyCachingFilter(FilterRegistrationBean<?> registrationBean) {
        return (CocoRequestBodyCachingFilter) registrationBean.getFilter();
    }

    private static CocoSignatureFilter signatureFilter(FilterRegistrationBean<?> registrationBean) {
        return (CocoSignatureFilter) registrationBean.getFilter();
    }

    private static CocoReplayFilter replayFilter(FilterRegistrationBean<?> registrationBean) {
        return (CocoReplayFilter) registrationBean.getFilter();
    }

    private static CocoEncryptionFilter encryptionFilter(FilterRegistrationBean<?> registrationBean) {
        return (CocoEncryptionFilter) registrationBean.getFilter();
    }

    private static MockHttpServletRequest signedRequest(org.springframework.context.ApplicationContext context,
            String traceId, String secret) {
        return signedRequest(context, traceId, secret, String.valueOf(System.currentTimeMillis()), "nonce-1001");
    }

    private static MockHttpServletRequest signedRequest(org.springframework.context.ApplicationContext context,
            String traceId, String secret, String timestamp, String nonce) {
        CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
        CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
        byte[] body = "{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body);
        request.addHeader("X-Trace-Id", traceId);
        request.addHeader("X-Coco-App-Id", "sample-app");
        request.addHeader("X-Coco-Timestamp", timestamp);
        request.addHeader("X-Coco-Nonce", nonce);
        request.addHeader("X-Coco-Sign-Algorithm", "HMAC-SHA256");
        CocoWebRequestSnapshot snapshot = resolver.resolve(traceId,
                new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
        String canonicalText = canonicalizer.canonicalize(snapshot.securityInput()).text();
        request.addHeader("X-Coco-Sign", hmacSha256Hex(canonicalText, secret));
        return request;
    }

    private static MockHttpServletRequest encryptedRequest(byte[] plainBody, byte[] key, String traceId) {
        byte[] iv = "123456789012".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(Base64.getEncoder().encode(aesGcmEncrypt(plainBody, key, iv)));
        request.addHeader("X-Trace-Id", traceId);
        request.addHeader("X-Coco-Encrypted", "true");
        request.addHeader("X-Coco-App-Id", "sample-app");
        request.addHeader("X-Coco-IV", Base64.getEncoder().encodeToString(iv));
        request.addHeader("X-Coco-Algorithm", "AES-GCM");
        return request;
    }

    private static byte[] aesGcmEncrypt(byte[] plainBody, byte[] key, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return cipher.doFinal(plainBody);
        }
        catch (Exception ex) {
            throw new IllegalStateException("AES-GCM encryption failed", ex);
        }
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private static String sha256(String content) {
        return sha256(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256Hex(String text, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 failed", ex);
        }
    }

    private static CocoApiResponse<?> apiBody(ResponseEntity<Object> response) {
        Object body = response.getBody();
        assertTrue(body instanceof CocoApiResponse<?>);
        return (CocoApiResponse<?>) body;
    }

    private static CocoResponseWrapAdvice responseWrapAdvice(CocoMessageService messageService) {
        return responseWrapAdvice(messageService, CocoSystemCodes.defaults());
    }

    private static CocoResponseWrapAdvice responseWrapAdvice(CocoMessageService messageService,
            CocoSystemCodeProvider codeProvider) {
        return responseWrapAdvice(messageService, codeProvider, new CocoResponseProperties());
    }

    private static CocoResponseWrapAdvice responseWrapAdvice(CocoMessageService messageService,
            CocoSystemCodeProvider codeProvider, CocoResponseProperties responseProperties) {
        CocoResponseWrapProperties properties = new CocoResponseWrapProperties();
        properties.setSuccessMessageCode("coco.error.unknown");
        return new CocoResponseWrapAdvice(messageService, properties, codeProvider, new ObjectMapper(),
                responseProperties);
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
        return CocoApiResponse.success(200, "操作成功", "hello");
    }

    @CocoIgnoreResponseWrap
    private Object ignoredBody() {
        return Map.of("ignored", true);
    }

    private ResponseEntity<String> responseEntityBody() {
        return ResponseEntity.ok("hello");
    }

    private enum TestBusinessCode implements CocoBusinessCode {

        ORDER_NOT_FOUND(200001, "coco.error.not-found");

        private final int code;

        private final String messageCode;

        TestBusinessCode(int code, String messageCode) {
            this.code = code;
            this.messageCode = messageCode;
        }

        @Override
        public int code() {
            return this.code;
        }

        @Override
        public String messageCode() {
            return this.messageCode;
        }
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

    private static final class BodyReadingServlet implements Servlet {

        private final AtomicReference<String> body;

        private final Runnable assertion;

        private BodyReadingServlet(AtomicReference<String> body, Runnable assertion) {
            this.body = body;
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
        public void service(ServletRequest request, ServletResponse response) throws IOException {
            this.assertion.run();
            this.body.set(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        }

        @Override
        public String getServletInfo() {
            return "body-reading-servlet";
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
