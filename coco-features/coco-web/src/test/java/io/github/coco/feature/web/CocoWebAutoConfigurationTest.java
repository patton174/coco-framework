package io.github.coco.feature.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.context.CocoRequestContext;
import io.github.coco.context.CocoRequestContextHolder;
import io.github.coco.exception.CocoBusinessCode;
import io.github.coco.exception.CocoBusinessExceptions;
import io.github.coco.exception.CocoCommonErrorCode;
import io.github.coco.exception.CocoException;
import io.github.coco.exception.CocoExceptions;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.logging.access.CocoAccessLog;
import io.github.coco.logging.access.CocoAccessLogFormatter;
import io.github.coco.logging.access.CocoAccessLogProperties;
import io.github.coco.logging.access.CocoAccessLogRecorder;
import io.github.coco.logging.access.CocoAccessLogStyle;
import io.github.coco.logging.access.DefaultCocoAccessLogFormatter;
import io.github.coco.logging.core.CocoLogHandleRegistry;
import io.github.coco.logging.core.CocoLogHandles;
import io.github.coco.logging.core.CocoLogLevel;
import io.github.coco.logging.core.CocoLogManager;
import io.github.coco.logging.core.CocoLogRecord;
import io.github.coco.logging.core.CocoLogSink;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.accesslog.CocoAccessLogCaptureProperties;
import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.body.CocoRequestBodyCachingFilter;
import io.github.coco.feature.web.body.CocoRequestBodyCachingMode;
import io.github.coco.feature.web.body.CocoRequestBodyProperties;
import io.github.coco.feature.web.body.CocoRequestBodyResolver;
import io.github.coco.feature.web.body.CocoResolvedRequestBody;
import io.github.coco.feature.web.body.DefaultCocoRequestBodyResolver;
import io.github.coco.feature.web.context.CocoBrowserFingerprint;
import io.github.coco.feature.web.context.CocoBrowserFingerprintResolver;
import io.github.coco.feature.web.context.CocoClientIpResolution;
import io.github.coco.feature.web.context.CocoClientIpResolver;
import io.github.coco.feature.web.context.CocoClientIpSource;
import io.github.coco.feature.web.context.CocoRequestHeaderResolver;
import io.github.coco.feature.web.context.CocoRequestParameterResolver;
import io.github.coco.feature.web.context.CocoWebParameterSource;
import io.github.coco.feature.web.context.CocoWebContextProperties;
import io.github.coco.feature.web.context.CocoWebRequestParameters;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalForm;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationContext;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationProperties;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationPurpose;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatchRule;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityInput;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityInputResolver;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.CocoWebRequestSnapshotAttributes;
import io.github.coco.feature.web.request.metadata.CocoWebSecurityMetadataSource;
import io.github.coco.feature.web.context.CocoWebParameterProperties;
import io.github.coco.feature.web.context.DefaultCocoBrowserFingerprintResolver;
import io.github.coco.feature.web.context.DefaultCocoClientIpResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestHeaderResolver;
import io.github.coco.feature.web.context.DefaultCocoRequestParameterResolver;
import io.github.coco.feature.web.context.DefaultCocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.DefaultCocoWebRequestContextResolver;
import io.github.coco.feature.web.context.DefaultCocoWebRequestMatcher;
import io.github.coco.feature.web.context.target.CocoWebRequestTarget;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetResolution;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetResolver;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetSource;
import io.github.coco.feature.web.context.target.DefaultCocoWebRequestTargetResolver;
import io.github.coco.feature.web.request.metadata.DefaultCocoWebRequestSecurityMetadataResolver;
import io.github.coco.feature.web.context.payload.CocoPayloadParameterResolver;
import io.github.coco.feature.web.encryption.CocoCryptoTextEncoding;
import io.github.coco.feature.web.encryption.CocoEncryptedRequest;
import io.github.coco.feature.web.encryption.CocoEncryptionAssociatedData;
import io.github.coco.feature.web.encryption.CocoEncryptionFilter;
import io.github.coco.feature.web.encryption.CocoEncryptionProperties;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoPayloadTooLargeException;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.replay.CocoReplayFilter;
import io.github.coco.feature.web.replay.CocoReplayKey;
import io.github.coco.feature.web.replay.CocoReplayProperties;
import io.github.coco.feature.web.replay.CocoReplayRequestShapeFilter;
import io.github.coco.feature.web.replay.CocoReplayStore;
import io.github.coco.feature.web.replay.DefaultCocoReplayKeyResolver;
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
import io.github.coco.feature.web.signature.CocoSignatureVerifier;
import io.github.coco.feature.web.trace.CocoTraceFilter;
import io.github.coco.feature.web.trace.CocoTraceIdValidator;
import io.github.coco.feature.web.trace.CocoTraceProperties;
import jakarta.servlet.Servlet;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.http.HttpHeaders;
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
import org.springframework.validation.BindException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

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
            assertTrue(context.containsBean("cocoPayloadParameterResolver"));
            assertTrue(context.containsBean("cocoRequestBodyResolver"));
            assertTrue(context.containsBean("cocoRequestParameterResolver"));
            assertTrue(context.containsBean("cocoWebRequestTargetResolver"));
            assertTrue(context.containsBean("cocoWebRequestSecurityInputResolver"));
            assertTrue(context.containsBean("cocoWebRequestSecurityMetadataResolver"));
            assertTrue(context.containsBean("cocoWebRequestCanonicalizer"));
            assertTrue(context.containsBean("cocoWebRequestMatcher"));
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
            assertTrue(context.containsBean("cocoTraceIdValidator"));
            assertTrue(context.containsBean("cocoReplayRequestShapeFilterRegistration"));
            assertTrue(context.containsBean("cocoReplayFilterRegistration"));
            assertTrue(context.containsBean("cocoFilterExceptionResponseWriter"));
            assertTrue(context.containsBean("cocoEncryptionFilterRegistration"));
            FilterRegistrationBean<?> bodyRegistration = context.getBean("cocoRequestBodyCachingFilterRegistration",
                    FilterRegistrationBean.class);
            FilterRegistrationBean<?> traceRegistration = context.getBean("cocoTraceFilterRegistration",
                    FilterRegistrationBean.class);
            FilterRegistrationBean<?> replayRequestShapeRegistration = context.getBean(
                    "cocoReplayRequestShapeFilterRegistration", FilterRegistrationBean.class);
            FilterRegistrationBean<?> signatureRegistration = context.getBean("cocoSignatureFilterRegistration",
                    FilterRegistrationBean.class);
            FilterRegistrationBean<?> encryptionRegistration = context.getBean("cocoEncryptionFilterRegistration",
                    FilterRegistrationBean.class);
            FilterRegistrationBean<?> replayRegistration = context.getBean("cocoReplayFilterRegistration",
                    FilterRegistrationBean.class);
            assertTrue(bodyRegistration.getOrder() < traceRegistration.getOrder());
            assertTrue(traceRegistration.getOrder() < replayRequestShapeRegistration.getOrder());
            assertTrue(replayRequestShapeRegistration.getOrder() < signatureRegistration.getOrder());
            assertTrue(signatureRegistration.getOrder() < encryptionRegistration.getOrder());
            assertTrue(encryptionRegistration.getOrder() < replayRegistration.getOrder());
        });
    }

    @Test
    void disablesWebAutoConfigurationWhenFeatureIsDisabled() {
        this.webContextRunner
                .withPropertyValues("coco.features.disabled[0]=web")
                .run(context -> {
                    assertFalse(context.containsBean("cocoExceptionHttpStatusResolver"));
                    assertFalse(context.containsBean("cocoTraceFilterRegistration"));
                    assertFalse(context.containsBean("cocoResponseWrapAdvice"));
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
    void disablesSignatureFilterRegistrationByProperty() {
        this.webContextRunner
                .withPropertyValues("coco.web.signature.enabled=false")
                .run(context -> assertFalse(context.containsBean("cocoSignatureFilterRegistration")));
    }

    @Test
    void disablesEncryptionFilterRegistrationByProperty() {
        this.webContextRunner
                .withPropertyValues("coco.web.encryption.enabled=false")
                .run(context -> assertFalse(context.containsBean("cocoEncryptionFilterRegistration")));
    }

    @Test
    void disablesReplayFilterRegistrationByProperty() {
        this.webContextRunner
                .withPropertyValues("coco.web.replay.enabled=false")
                .run(context -> {
                    assertFalse(context.containsBean("cocoReplayRequestShapeFilterRegistration"));
                    assertFalse(context.containsBean("cocoReplayFilterRegistration"));
                });
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
    void filterExceptionWriterUsesExplicitRequestLocaleWithoutReplacingRequestAttributes() throws Exception {
        this.webContextRunner.run(context -> {
            CocoFilterExceptionResponseWriter writer = context.getBean(CocoFilterExceptionResponseWriter.class);
            MockHttpServletRequest staleRequest = new MockHttpServletRequest("GET", "/stale");
            ServletRequestAttributes staleAttributes = new ServletRequestAttributes(staleRequest);
            RequestContextHolder.setRequestAttributes(staleAttributes);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/filter-error");
            request.addHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US");
            request.addPreferredLocale(Locale.US);
            MockHttpServletResponse response = new MockHttpServletResponse();

            writer.write(CocoCommonErrorCode.INVALID_ARGUMENT.exception("name"), request, response);

            Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
            assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
            assertEquals(Boolean.FALSE, body.get("success"));
            assertEquals(400, body.get("code"));
            assertEquals("Invalid argument: name", body.get("message"));
            assertEquals(staleAttributes, RequestContextHolder.getRequestAttributes());
        });
    }

    @Test
    void logsHandledCocoExceptionThroughExceptionLogHandle() {
        CapturingCocoLogSink sink = new CapturingCocoLogSink();
        this.webContextRunner
                .withBean(CocoLogManager.class, () -> cocoLogManager(sink))
                .run(context -> {
                    CocoTraceContext.setTraceId("trace-exception");
                    CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    CocoException exception = CocoCommonErrorCode.INTERNAL_ERROR.exception();

                    handler.handleCocoException(exception, new ServletWebRequest(request));

                    assertEquals(1, sink.records().size());
                    CocoLogRecord record = sink.records().get(0);
                    assertEquals(CocoLogHandles.EXCEPTION, record.handle().name());
                    assertEquals(CocoLogLevel.ERROR, record.level());
                    assertTrue(record.message().contains("traceId=trace-exception"));
                    assertTrue(record.message().contains("path=/api/users"));
                    Throwable failure = record.failure().orElseThrow();
                    assertEquals(CocoException.class, failure.getClass());
                    assertEquals("服务器内部错误", failure.getMessage());
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
    void writesTraceCookieInErrorResponseWhenCookieModeIsEnabled() {
        CocoTraceContext.setTraceId("trace-cookie");
        this.webContextRunner
                .withPropertyValues("coco.web.response.metadata-mode=cookie")
                .run(context -> {
                    CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");

                    ResponseEntity<Object> response = handler.handleCocoException(
                            CocoCommonErrorCode.INVALID_ARGUMENT.exception("name"),
                            new ServletWebRequest(request));

                    CocoApiResponse<?> body = apiBody(response);
                    String cookie = response.getHeaders().getFirst(org.springframework.http.HttpHeaders.SET_COOKIE);
                    assertNull(body.traceId());
                    assertNull(body.path());
                    assertNotNull(cookie);
                    assertTrue(cookie.contains("COCO_TRACE_ID=trace-cookie"));
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
    void returnsHttpStatusFromBuiltInMessageCodeForUntypedCocoException() {
        this.webContextRunner.run(context -> {
            CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");

            ResponseEntity<Object> unauthorized = handler.handleCocoException(
                    CocoCommonErrorCode.UNAUTHORIZED.exception(),
                    new ServletWebRequest(request));
            ResponseEntity<Object> forbidden = handler.handleCocoException(
                    CocoCommonErrorCode.FORBIDDEN.exception(),
                    new ServletWebRequest(request));
            ResponseEntity<Object> notFound = handler.handleCocoException(
                    CocoCommonErrorCode.NOT_FOUND.exception("user"),
                    new ServletWebRequest(request));
            ResponseEntity<Object> internalError = handler.handleCocoException(
                    CocoCommonErrorCode.INTERNAL_ERROR.exception(),
                    new ServletWebRequest(request));

            assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.getStatusCode());
            assertEquals(401, apiBody(unauthorized).code());
            assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());
            assertEquals(403, apiBody(forbidden).code());
            assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
            assertEquals(404, apiBody(notFound).code());
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, internalError.getStatusCode());
            assertEquals(500, apiBody(internalError).code());
        });
    }

    @Test
    void returnsLocalizedWebMessagesForSpringMvcExceptions() {
        this.webContextRunner.run(context -> {
            CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");

            ResponseEntity<Object> badRequest = handler.handleBadRequestException(
                    new BindException(new Object(), "request"),
                    new ServletWebRequest(request));
            ResponseEntity<Object> methodNotAllowed = handler.handleMethodNotAllowedException(
                    new HttpRequestMethodNotSupportedException("TRACE"),
                    new ServletWebRequest(request));

            assertEquals(HttpStatus.BAD_REQUEST, badRequest.getStatusCode());
            assertEquals(400, apiBody(badRequest).code());
            assertEquals("请求参数不合法。", apiBody(badRequest).message());
            assertFalse(apiBody(badRequest).message().contains("request"));
            assertEquals(HttpStatus.METHOD_NOT_ALLOWED, methodNotAllowed.getStatusCode());
            assertEquals(400, apiBody(methodNotAllowed).code());
            assertEquals("请求方法不支持。", apiBody(methodNotAllowed).message());
            assertFalse(apiBody(methodNotAllowed).message().contains("method"));
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
        assertEquals(-1L, properties.getResponseWrap().getMaxBodyBytes());
        properties.getResponseWrap().setMaxBodyBytes(4096);
        assertEquals(4096L, properties.getResponseWrap().getMaxBodyBytes());
        properties.getResponseWrap().setMaxBodyBytes(-10);
        assertEquals(-1L, properties.getResponseWrap().getMaxBodyBytes());

        properties.setResponse(null);
        properties.setResponseWrap(null);

        assertEquals(CocoResponseMetadataMode.NONE, properties.getResponse().getMetadataMode());
        assertTrue(properties.getResponseWrap().isEnabled());
        assertEquals(-1L, properties.getResponseWrap().getMaxBodyBytes());
    }

    @Test
    void accessLogPropertiesUseDefaultsAndResetNullNestedValue() {
        CocoWebProperties properties = new CocoWebProperties();

        assertTrue(properties.getAccessLog().isEnabled());
        assertTrue(properties.getRequestBody().isEnabled());
        assertEquals(CocoRequestBodyCachingMode.SECURITY_HEADERS, properties.getRequestBody().getMode());
        assertEquals(1024 * 1024, properties.getRequestBody().getMaxCacheBytes());
        assertTrue(properties.getRequestBody().getCacheMethods().contains("POST"));
        assertTrue(properties.getRequestBody().getTriggerHeaderNames().contains("x-coco-sign"));
        assertTrue(properties.getRequestBody().getIncludedContentTypes().contains("application/json"));
        assertTrue(properties.getRequestBody().getExcludedContentTypePrefixes().contains("multipart/"));
        assertTrue(properties.getSignature().isEnabled());
        assertFalse(properties.getSignature().isRequired());
        assertTrue(properties.getSignature().getMatcher().getRequired().isEmpty());
        assertTrue(properties.getSignature().getMatcher().getIgnored().isEmpty());
        assertEquals("X-Coco-App-Id", properties.getSignature().getAppIdHeaderName());
        assertEquals("X-Coco-Sign", properties.getSignature().getSignatureHeaderName());
        assertEquals("X-Coco-Sign-Algorithm", properties.getSignature().getAlgorithmHeaderName());
        assertEquals("HMAC-SHA256", properties.getSignature().getDefaultAlgorithm());
        assertEquals(300L, properties.getSignature().getMaxClockSkewSeconds());
        assertTrue(properties.getEncryption().isEnabled());
        assertFalse(properties.getEncryption().isRequired());
        assertTrue(properties.getEncryption().getMatcher().getRequired().isEmpty());
        assertTrue(properties.getEncryption().getMatcher().getIgnored().isEmpty());
        assertEquals("X-Coco-Encrypted", properties.getEncryption().getEncryptedHeaderName());
        assertEquals("X-Coco-App-Id", properties.getEncryption().getAppIdHeaderName());
        assertEquals("X-Coco-Key-Id", properties.getEncryption().getKeyIdHeaderName());
        assertEquals("X-Coco-IV", properties.getEncryption().getIvHeaderName());
        assertEquals("X-Coco-Algorithm", properties.getEncryption().getAlgorithmHeaderName());
        assertEquals("payload", properties.getEncryption().getPayloadParameterName());
        assertEquals("AES-GCM", properties.getEncryption().getDefaultAlgorithm());
        assertEquals(CocoCryptoTextEncoding.BASE64, properties.getEncryption().getKeyEncoding());
        assertEquals(CocoCryptoTextEncoding.BASE64, properties.getEncryption().getIvEncoding());
        assertEquals(CocoCryptoTextEncoding.BASE64, properties.getEncryption().getPayloadEncoding());
        assertEquals(128, properties.getEncryption().getGcmTagLengthBits());
        assertTrue(properties.getReplay().isEnabled());
        assertFalse(properties.getReplay().isRequired());
        assertTrue(properties.getReplay().getMatcher().getRequired().isEmpty());
        assertTrue(properties.getReplay().getMatcher().getIgnored().isEmpty());
        assertTrue(properties.getReplay().isProtectSignedRequests());
        assertTrue(properties.getReplay().isProtectEncryptedRequests());
        assertTrue(properties.getReplay().isIncludeMethod());
        assertTrue(properties.getReplay().isIncludePath());
        assertEquals("X-Coco-App-Id", properties.getReplay().getAppIdHeaderName());
        assertEquals("X-Coco-Key-Id", properties.getReplay().getKeyIdHeaderName());
        assertEquals("X-Coco-Timestamp", properties.getReplay().getTimestampHeaderName());
        assertEquals("X-Coco-Nonce", properties.getReplay().getNonceHeaderName());
        assertEquals(300L, properties.getReplay().getTtlSeconds());
        assertEquals(300L, properties.getReplay().getMaxClockSkewSeconds());
        assertEquals(60L, properties.getReplay().getCleanupIntervalSeconds());
        assertEquals(128, properties.getTrace().getMaxLength());
        assertEquals(CocoTraceProperties.DEFAULT_ALLOWED_PATTERN, properties.getTrace().getAllowedPattern());
        assertTrue(properties.getContext().isIncludeHeaders());
        assertTrue(properties.getContext().getClientIpHeaderNames().contains("X-Forwarded-For"));
        assertTrue(properties.getContext().getTrustedProxyCidrs().isEmpty());
        assertTrue(properties.getContext().getIncludedHeaderNames().contains("user-agent"));
        assertTrue(properties.getContext().getMaskedHeaderNames().contains("authorization"));
        assertTrue(properties.getContext().getSecurityHeaderNames().contains("x-coco-sign"));
        assertTrue(properties.getContext().getSecurityHeaderNames().contains("x-coco-sign-algorithm"));
        assertTrue(properties.getContext().getCanonicalHeaderNames().contains("x-coco-timestamp"));
        assertTrue(properties.getContext().getCanonicalHeaderNames().contains("x-coco-sign-algorithm"));
        assertTrue(properties.getContext().getFingerprintHeaderNames().contains("sec-ch-ua"));
        assertEquals(256, properties.getContext().getMaxHeaderValueLength());
        assertTrue(properties.getContext().getParameter().isIncludeParameters());
        assertTrue(properties.getContext().getTarget().getProtoHeaderNames().contains("X-Forwarded-Proto"));
        assertTrue(properties.getContext().getTarget().getHostHeaderNames().contains("X-Forwarded-Host"));
        assertTrue(properties.getContext().getTarget().getPortHeaderNames().contains("X-Forwarded-Port"));
        assertTrue(properties.getContext().getTarget().getPrefixHeaderNames().contains("X-Forwarded-Prefix"));
        assertTrue(properties.getContext().getTarget().isApplyForwardedPrefix());
        assertEquals(256, properties.getContext().getParameter().getMaxParameterValueLength());
        assertTrue(properties.getContext().getParameter().getMaskedParameterNames().contains("token"));
        assertTrue(properties.getContext().getParameter().getPayload().isEnabled());
        assertTrue(properties.getContext().getParameter().getPayload().getIncludedContentTypes()
                .contains("application/json"));
        assertTrue(properties.getContext().getParameter().getPayload().getIncludedContentTypes()
                .contains("application/*+json"));
        assertTrue(properties.getContext().getParameter().getPayload().getIncludedContentTypes()
                .contains("application/x-www-form-urlencoded"));
        assertEquals(8, properties.getContext().getParameter().getPayload().getMaxJsonDepth());
        assertEquals(128, properties.getContext().getParameter().getPayload().getMaxParameterCount());
        assertEquals("coco-v2", properties.getContext().getCanonicalization().getVersion());
        assertFalse(properties.getContext().getCanonicalization().isIncludeVersion());
        assertFalse(properties.getContext().getCanonicalization().isIncludePurpose());
        assertTrue(properties.getContext().getCanonicalization().isIncludeMethod());
        assertTrue(properties.getContext().getCanonicalization().isIncludePath());
        assertTrue(properties.getContext().getCanonicalization().isIncludeQueryString());
        assertTrue(properties.getContext().getCanonicalization().isIncludeHeaders());
        assertTrue(properties.getContext().getCanonicalization().isIncludeParameters());
        assertTrue(properties.getContext().getCanonicalization().isIncludeParameterSources());
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

        assertEquals("▸ request\n"
                        + "  traceId            trace-1001\n"
                        + "  method             POST\n"
                        + "  path               /sample/orders?sku=COCO-STARTER&token=******\n"
                        + "  clientIp           10.0.0.8\n"
                        + "  userAgent          \"PostmanRuntime/7.37\"\n"
                        + "  params             sku=COCO-STARTER&token=******\n"
                        + "◂ response\n"
                        + "  traceId            trace-1001\n"
                        + "  status             201\n"
                        + "  duration           42ms\n"
                        + "  success            true",
                formatter.format(accessLog, properties).replace("\r\n", "\n"));

        properties.setStyle(CocoAccessLogStyle.JSON);

        assertEquals("{\"traceId\":\"trace-1001\",\"method\":\"POST\",\"path\":\"/sample/orders\","
                        + "\"clientIp\":\"10.0.0.8\",\"userAgent\":\"PostmanRuntime/7.37\","
                        + "\"queryString\":\"sku=COCO-STARTER&token=******\","
                        + "\"parameters\":{\"sku\":[\"COCO-STARTER\"],\"token\":[\"******\"]},"
                        + "\"status\":201,\"durationMs\":42,\"success\":true}",
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
    void writesTraceCookieForWrappedResponseWhenCookieModeIsEnabled() throws Exception {
        CocoTraceContext.setTraceId("trace-wrap");
        this.webContextRunner
                .withPropertyValues("coco.web.response.metadata-mode=cookie")
                .run(context -> {
                    CocoResponseWrapAdvice advice = context.getBean(CocoResponseWrapAdvice.class);
                    MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/users");
                    ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
                    ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

                    Object body = advice.beforeBodyWrite(Map.of("name", "Coco"), methodParameter("objectBody"),
                            MediaType.APPLICATION_JSON, TestHttpMessageConverter.class, request, response);

                    assertTrue(body instanceof CocoApiResponse<?>);
                    CocoApiResponse<?> wrapped = (CocoApiResponse<?>) body;
                    assertNull(wrapped.traceId());
                    assertNull(wrapped.path());
                    String cookie = response.getHeaders().getFirst(org.springframework.http.HttpHeaders.SET_COOKIE);
                    assertNotNull(cookie);
                    assertTrue(cookie.contains("COCO_TRACE_ID=trace-wrap"));
                });
    }

    @Test
    void writesConfiguredTraceCookieForWrappedResponseWhenCookieModeIsEnabled() throws Exception {
        CocoTraceContext.setTraceId("trace-custom");
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.response.metadata-mode=cookie",
                        "coco.web.trace.cookie-name=TRACE_ALIAS",
                        "coco.web.trace.cookie-path=/sample",
                        "coco.web.trace.cookie-max-age=60",
                        "coco.web.trace.cookie-same-site=Strict")
                .run(context -> {
                    CocoResponseWrapAdvice advice = context.getBean(CocoResponseWrapAdvice.class);
                    MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/users");
                    ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
                    ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

                    advice.beforeBodyWrite(Map.of("name", "Coco"), methodParameter("objectBody"),
                            MediaType.APPLICATION_JSON, TestHttpMessageConverter.class, request, response);

                    String cookie = response.getHeaders().getFirst(org.springframework.http.HttpHeaders.SET_COOKIE);
                    assertNotNull(cookie);
                    assertTrue(cookie.contains("TRACE_ALIAS=trace-custom"));
                    assertTrue(cookie.contains("Path=/sample"));
                    assertTrue(cookie.contains("Max-Age=60"));
                    assertTrue(cookie.contains("SameSite=Strict"));
                });
    }

    @Test
    void wrapsStringResponseBodyAsJsonString() throws Exception {
        this.webContextRunner.run(context -> {
            CocoResponseWrapAdvice advice = responseWrapAdvice(context.getBean(CocoMessageService.class));
            MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/text");
            ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
            ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

            Object body = advice.beforeBodyWrite("hello", methodParameter("stringBody"),
                    MediaType.TEXT_PLAIN, StringHttpMessageConverter.class, request,
                    response);

            assertTrue(body instanceof String);
            assertTrue(((String) body).contains("\"success\":true"));
            assertTrue(((String) body).contains("\"data\":\"hello\""));
            assertFalse(((String) body).contains("\"traceId\""));
            assertFalse(((String) body).contains("\"path\""));
            assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        });
    }

    @Test
    void skipsStringResponseWrapWhenKnownBodyBytesExceedConfiguredLimit() throws Exception {
        this.webContextRunner.run(context -> {
            CocoResponseWrapProperties properties = new CocoResponseWrapProperties();
            properties.setMaxBodyBytes(4);
            CocoResponseWrapAdvice advice = new CocoResponseWrapAdvice(context.getBean(CocoMessageService.class),
                    properties, CocoSystemCodes.defaults(), new ObjectMapper());
            MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/text");
            ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
            ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());

            Object body = advice.beforeBodyWrite("hello", methodParameter("stringBody"),
                    MediaType.TEXT_PLAIN, StringHttpMessageConverter.class, request,
                    response);

            assertEquals("hello", body);
            assertNull(response.getHeaders().getContentType());
        });
    }

    @Test
    void skipsObjectResponseWrapWhenContentLengthExceedsConfiguredLimit() throws Exception {
        this.webContextRunner.run(context -> {
            CocoResponseWrapProperties properties = new CocoResponseWrapProperties();
            properties.setMaxBodyBytes(8);
            CocoResponseWrapAdvice advice = new CocoResponseWrapAdvice(context.getBean(CocoMessageService.class),
                    properties, CocoSystemCodes.defaults(), new ObjectMapper());
            MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/api/users");
            ServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
            ServletServerHttpResponse response = new ServletServerHttpResponse(new MockHttpServletResponse());
            response.getHeaders().setContentLength(128);
            Map<String, String> rawBody = Map.of("name", "Coco");

            Object body = advice.beforeBodyWrite(rawBody, methodParameter("objectBody"),
                    MediaType.APPLICATION_JSON, TestHttpMessageConverter.class, request,
                    response);

            assertEquals(rawBody, body);
            assertFalse(body instanceof CocoApiResponse<?>);
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
    void returnsUnifiedErrorResponseForSpringBadRequestException() {
        this.webContextRunner.run(context -> {
            CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");

            ResponseEntity<Object> response = handler.handleBadRequestException(
                    new org.springframework.web.bind.MissingServletRequestParameterException("sku", "String"),
                    new ServletWebRequest(request));

            CocoApiResponse<?> body = apiBody(response);
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertFalse(body.success());
            assertEquals(400, body.code());
            assertEquals("请求参数不合法。", body.message());
        });
    }

    @Test
    void returnsUnifiedErrorResponseForSpringNotFoundException() {
        this.webContextRunner.run(context -> {
            CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/missing");

            ResponseEntity<Object> response = handler.handleNotFoundException(
                    new org.springframework.web.servlet.NoHandlerFoundException("GET", "/api/missing",
                            new org.springframework.http.HttpHeaders()),
                    new ServletWebRequest(request));

            CocoApiResponse<?> body = apiBody(response);
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertFalse(body.success());
            assertEquals(404, body.code());
            assertEquals("资源不存在：/api/missing", body.message());
        });
    }

    @Test
    void returnsUnifiedErrorResponseForUnhandledException() {
        this.webContextRunner.run(context -> {
            CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/error");

            ResponseEntity<Object> response = handler.handleUnhandledException(
                    new IllegalStateException("boom"),
                    new ServletWebRequest(request));

            CocoApiResponse<?> body = apiBody(response);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertFalse(body.success());
            assertEquals(500, body.code());
            assertEquals("服务器内部错误", body.message());
        });
    }

    @Test
    void rethrowsClientDisconnectExceptionWithoutUnifiedErrorResponse() {
        CapturingCocoLogSink sink = new CapturingCocoLogSink();
        this.webContextRunner
                .withBean(CocoLogManager.class, () -> cocoLogManager(sink))
                .run(context -> {
                    CocoWebExceptionHandler handler = context.getBean(CocoWebExceptionHandler.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stream");
                    AsyncRequestNotUsableException exception =
                            new AsyncRequestNotUsableException("ServletOutputStream failed to write");

                    AsyncRequestNotUsableException thrown = assertThrows(AsyncRequestNotUsableException.class,
                            () -> handler.handleUnhandledException(exception, new ServletWebRequest(request)));

                    assertEquals(exception, thrown);
                    assertTrue(sink.records().isEmpty());
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
    void replacesInvalidIncomingTraceId() throws Exception {
        this.webContextRunner.run(context -> {
            CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                    FilterRegistrationBean.class));
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();
            String[] traceId = new String[1];
            request.addHeader("X-Trace-Id", "invalid trace id");

            filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() ->
                    traceId[0] = CocoTraceContext.currentTraceId().orElseThrow())));

            assertNotNull(traceId[0]);
            assertNotEquals("invalid trace id", traceId[0]);
            assertTrue(traceId[0].matches(CocoTraceProperties.DEFAULT_ALLOWED_PATTERN));
            assertEquals(traceId[0], response.getHeader("X-Trace-Id"));
        });
    }

    @Test
    void customTraceIdValidatorCanAcceptApplicationTraceFormat() throws Exception {
        this.webContextRunner
                .withBean(CocoTraceIdValidator.class, () -> traceId -> traceId != null && traceId.startsWith("biz "))
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "biz trace");

                    filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() ->
                            assertEquals("biz trace", CocoTraceContext.currentTraceId().orElseThrow()))));

                    assertEquals("biz trace", response.getHeader("X-Trace-Id"));
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
        this.webContextRunner
                .withPropertyValues("coco.web.context.trusted-proxy-cidrs=127.0.0.1/32")
                .run(context -> {
            CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                    FilterRegistrationBean.class));
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.addHeader("X-Trace-Id", " incoming-trace ");
            request.addHeader("X-Forwarded-For", " 10.0.0.8, 10.0.0.9 ");
            request.addHeader("User-Agent", "PostmanRuntime/7.37");
            request.addHeader("Accept-Language", "zh-CN");
            request.addHeader("X-Coco-App-Id", "sample-app");
            request.addHeader("X-Coco-Key-Id", "key-001");
            request.addHeader("X-Coco-Sign", "sign-value");
            request.addHeader("X-Coco-Sign-Algorithm", "HMAC-SHA256");
            request.addHeader("X-Coco-Encrypted", "true");
            request.addHeader("X-Coco-Algorithm", "AES-GCM");
            request.addHeader("X-Coco-Timestamp", "1783300000000");
            request.addHeader("X-Coco-Nonce", "nonce-1001");
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
                assertEquals("10.0.0.9", requestContext.clientIp().orElseThrow());
                assertEquals("FORWARDED_HEADER", requestContext.clientIpSource().orElseThrow());
                assertEquals("X-Forwarded-For", requestContext.clientIpSourceHeader().orElseThrow());
                assertEquals("10.0.0.8, 10.0.0.9", requestContext.clientIpSourceHeaderValue().orElseThrow());
                assertEquals("127.0.0.1", requestContext.clientIpRemoteAddress().orElseThrow());
                assertTrue(requestContext.clientIpTrustedProxy());
                assertEquals(List.of("10.0.0.8", "10.0.0.9"), requestContext.clientIpChain());
                assertEquals(1, requestContext.clientIpResolvedChainIndex().orElseThrow());
                assertEquals("PostmanRuntime/7.37", requestContext.userAgent().orElseThrow());
                assertEquals("name=Coco&token=******", requestContext.queryString().orElseThrow());
                assertEquals("zh-CN", requestContext.locale().orElseThrow());
                assertEquals("https", requestContext.attribute("scheme").orElseThrow());
                assertEquals("api.example.test", requestContext.attribute("host").orElseThrow());
                assertEquals("8443", requestContext.attribute("port").orElseThrow());
                assertEquals("application/json", requestContext.attribute("contentType").orElseThrow());
                assertTrue(requestContext.browserFingerprint().orElseThrow().length() == 64);
                assertTrue(requestContext.securityAppId().isEmpty());
                assertTrue(requestContext.securityKeyId().isEmpty());
                assertTrue(requestContext.requestSigned());
                assertTrue(requestContext.requestEncrypted());
                assertTrue(requestContext.requestReplayProtected());
                assertEquals("HMAC-SHA256", requestContext.signatureAlgorithm().orElseThrow());
                assertEquals("AES-GCM", requestContext.encryptionAlgorithm().orElseThrow());
                assertEquals("transport-captured", requestContext.requestContextPhase().orElseThrow());
                assertEquals("no-body", requestContext.requestPayloadParseStatus().orElseThrow());
                assertTrue(requestContext.header("accept-language").orElseThrow().contains("zh-cn"));
                assertEquals("Coco", requestContext.parameter("name").orElseThrow());
                assertEquals("******", requestContext.parameter("token").orElseThrow());
                assertEquals("Coco", requestContext.queryParameter("name").orElseThrow());
                assertEquals("******", requestContext.queryParameter("token").orElseThrow());
                assertTrue(requestContext.payloadParameter("name").isEmpty());
                assertEquals("incoming-trace", MDC.get("traceId"));
            })));

            assertTrue(CocoRequestContextHolder.current().isEmpty());
            assertNull(MDC.get("traceId"));
                });
    }

    @Test
    void doesNotCollectRequestCookiesByDefault() throws Exception {
        this.webContextRunner.run(context -> {
            CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                    FilterRegistrationBean.class));
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
            MockHttpServletResponse response = new MockHttpServletResponse();
            request.addHeader("X-Trace-Id", "cookie-default-trace");
            request.setCookies(new Cookie("COCO_TRACE", "cookie-trace"));

            filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() -> {
                CocoRequestContext requestContext = CocoRequestContextHolder.current().orElseThrow();
                assertTrue(requestContext.cookie("COCO_TRACE").isEmpty());
            })));
        });
    }

    @Test
    void resolvesIncludedRequestCookiesIntoRequestContext() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.context.included-cookie-names=COCO_TRACE,SESSION,theme",
                        "coco.web.context.max-cookie-value-length=4")
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "cookie-context-trace");
                    request.setCookies(
                            new Cookie("COCO_TRACE", "incoming-trace-cookie"),
                            new Cookie("SESSION", "secret-session"),
                            new Cookie("theme", "dark-theme"),
                            new Cookie("ignored", "ignored-value"));

                    filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() -> {
                        CocoRequestContext requestContext = CocoRequestContextHolder.current().orElseThrow();
                        assertEquals("inco...", requestContext.cookie("COCO_TRACE").orElseThrow());
                        assertEquals("******", requestContext.cookie("SESSION").orElseThrow());
                        assertEquals("dark...", requestContext.cookie("theme").orElseThrow());
                        assertTrue(requestContext.cookie("ignored").isEmpty());
                    })));

                    CocoWebRequestSnapshot snapshot = CocoWebRequestSnapshotAttributes.get(request).orElseThrow();
                    assertEquals("inco...", snapshot.cookies().get("COCO_TRACE"));
                    assertEquals("******", snapshot.cookies().get("SESSION"));
                    assertEquals("dark...", snapshot.cookies().get("theme"));
                    assertFalse(snapshot.cookies().containsKey("ignored"));
                });
    }

    @Test
    void resolvesCanonicalRequestCookiesIntoSecurityInput() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.context.canonical-cookie-names=SESSION,theme")
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "cookie-security-trace");
                    request.setCookies(
                            new Cookie("SESSION", "secret-session"),
                            new Cookie("theme", "dark-theme"),
                            new Cookie("ignored", "ignored-value"));

                    filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() -> {
                        CocoRequestContext requestContext = CocoRequestContextHolder.current().orElseThrow();
                        assertTrue(requestContext.cookie("SESSION").isEmpty());
                    })));

                    CocoWebRequestSnapshot snapshot = CocoWebRequestSnapshotAttributes.get(request).orElseThrow();
                    assertEquals("secret-session", snapshot.securityInput().canonicalCookie("SESSION").orElseThrow());
                    assertEquals("dark-theme", snapshot.securityInput().canonicalCookie("theme").orElseThrow());
                    assertTrue(snapshot.securityInput().canonicalCookie("ignored").isEmpty());
                    assertTrue(snapshot.cookies().isEmpty());
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
        MDC.put("cocoTraceId", "outer-configured-trace");
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

                    assertEquals("outer-configured-trace", MDC.get("cocoTraceId"));
                    assertNull(MDC.get("traceId"));
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
                        "coco.web.context.trusted-proxy-cidrs=127.0.0.1/32",
                        "coco.web.context.max-header-value-length=8",
                        "coco.web.context.parameter.max-parameter-value-length=4")
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "context-trace");
                    request.setRemoteAddr("127.0.0.1");
                    request.addHeader("Forwarded",
                            "for=\"[2001:db8:cafe::17]:4711\";proto=https;host=\"api.coco.dev:8443\"");
                    request.addHeader("X-Forwarded-Prefix", "/gateway");
                    request.addHeader("Authorization", "Bearer secret-token");
                    request.addHeader("Accept-Language", "zh-CN,en;q=0.8");
                    request.setQueryString("password=abcdef&name=Coconut");
                    request.addParameter("password", "abcdef");
                    request.addParameter("name", "Coconut");

                    filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() -> {
                        CocoRequestContext requestContext = CocoRequestContextHolder.current().orElseThrow();
                        assertEquals("2001:db8:cafe::17", requestContext.clientIp().orElseThrow());
                        assertEquals("for=\"[2001:db8:cafe::17]:4711\";proto=https;host=\"api.coco.dev:8443\"",
                                requestContext.clientIpSourceHeaderValue().orElseThrow());
                        assertEquals(List.of("2001:db8:cafe::17"), requestContext.clientIpChain());
                        assertEquals(0, requestContext.clientIpResolvedChainIndex().orElseThrow());
                        assertEquals("MIXED", requestContext.requestTargetSource().orElseThrow());
                        assertEquals("127.0.0.1", requestContext.requestTargetRemoteAddress().orElseThrow());
                        assertTrue(requestContext.requestTargetTrustedProxy());
                        assertEquals(List.of("forwarded", "x-forwarded-prefix"),
                                requestContext.requestTargetSourceHeaders());
                        assertEquals("/gateway", requestContext.requestTargetForwardedPrefix().orElseThrow());
                        assertEquals("https", requestContext.scheme().orElseThrow());
                        assertEquals("api.coco.dev", requestContext.host().orElseThrow());
                        assertEquals(8443, requestContext.port().orElseThrow());
                        assertEquals("/gateway/api/users", requestContext.path().orElseThrow());
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
            request.addHeader("X-Coco-Sign-Algorithm", "HMAC-SHA256");
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
            assertEquals(List.of("COCO-STARTER"), snapshot.securityInput().queryParameter("sku").orElseThrow());
            assertTrue(snapshot.securityInput().payloadParameter("sku").isEmpty());
            assertEquals(List.of("abc"), snapshot.securityInput().queryParameter("token").orElseThrow());
            assertTrue(snapshot.securityInput().payloadParameter("token").isEmpty());
            assertEquals("sku=COCO-STARTER&token=abc", snapshot.securityInput().queryString());
            assertEquals(sha256(body), snapshot.securityInput().bodySha256());
            assertEquals(body.length, snapshot.securityInput().bodyLength());
            assertTrue(snapshot.securityInput().bodyCached());
            assertEquals("sample-app", snapshot.securityMetadata().primaryAppId().orElseThrow());
            assertEquals("key-1", snapshot.securityMetadata().primaryKeyId().orElseThrow());
            assertTrue(snapshot.securityMetadata().signed());
            assertTrue(snapshot.securityMetadata().encrypted());
            assertTrue(snapshot.securityMetadata().replayProtected());
            assertEquals("HMAC-SHA256", snapshot.toRequestContext().signatureAlgorithm().orElseThrow());
            assertEquals("AES/GCM/NoPadding", snapshot.toRequestContext().encryptionAlgorithm().orElseThrow());
            assertTrue(snapshot.toRequestContext().requestSigned());
            assertTrue(snapshot.toRequestContext().requestEncrypted());
            assertTrue(snapshot.toRequestContext().requestReplayProtected());
            assertTrue(snapshot.browserFingerprint().value().length() == 64);
            assertEquals(snapshot.browserFingerprint().value(),
                    snapshot.toRequestContext().browserFingerprint().orElseThrow());
            assertEquals(sha256(body), snapshot.toRequestContext().requestBodySha256().orElseThrow());
            assertEquals("transport", snapshot.toRequestContext().requestBodyStage().orElseThrow());
            assertEquals(sha256(body), snapshot.toRequestContext().requestBodyTransportSha256().orElseThrow());
            assertEquals(sha256(body), snapshot.toRequestContext().requestBodyEffectiveSha256().orElseThrow());
            assertEquals(body.length, snapshot.toRequestContext().requestBodyTransportLength().orElseThrow());
            assertEquals(body.length, snapshot.toRequestContext().requestBodyEffectiveLength().orElseThrow());
            assertTrue(canonicalForm.text().contains("method=POST"));
            assertTrue(canonicalForm.text().contains("path=/api/orders"));
            assertTrue(canonicalForm.text().contains("x-coco-timestamp[0]=13:1783300000000"));
            assertTrue(canonicalForm.text().contains("bodySha256=" + sha256(body)));
            assertTrue(canonicalForm.text().contains("bodyLength=" + body.length));
            assertTrue(canonicalForm.sha256().length() == 64);
        });
    }

    @Test
    void resolvesCachedJsonPayloadParametersIntoContextAndSecurityInput() {
        this.webContextRunner
                .withPropertyValues("coco.web.context.canonicalization.version=coco-v2")
                .run(context -> {
            CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
            CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    byte[] body = ("{\"sku\":\"COCO-STARTER\",\"buyer\":{\"name\":\"Patton\"},"
                            + "\"tags\":[\"web\",\"sign\"],\"nickname\":\"Coco%20Spring\","
                            + "\"password\":\"plain-secret\"}")
                    .getBytes(StandardCharsets.UTF_8);
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);

            CocoWebRequestSnapshot snapshot = resolver.resolve("json-payload-trace",
                    new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
            CocoWebRequestCanonicalForm canonicalForm = canonicalizer.canonicalize(snapshot.securityInput());
            CocoRequestContext requestContext = snapshot.toRequestContext();

                    assertEquals(List.of("COCO-STARTER"), snapshot.parameters().get("sku"));
                    assertEquals(List.of("Patton"), snapshot.parameters().get("buyer.name"));
                    assertEquals(List.of("web", "sign"), snapshot.parameters().get("tags"));
            assertEquals(List.of("Coco%20Spring"), snapshot.parameters().get("nickname"));
            assertEquals(List.of("******"), snapshot.parameters().get("password"));
            assertEquals(List.of("plain-secret"), snapshot.securityInput().parameter("password").orElseThrow());
            assertTrue(snapshot.securityInput().queryParameters().isEmpty());
            assertEquals(List.of("plain-secret"), snapshot.securityInput().payloadParameter("password").orElseThrow());
            assertEquals("Patton", requestContext.parameter("buyer.name").orElseThrow());
            assertEquals("web,sign", requestContext.parameter("tags").orElseThrow());
            assertEquals(List.of("web", "sign"), requestContext.parameterValues("tags").orElseThrow());
            assertEquals("******", requestContext.parameter("password").orElseThrow());
            assertEquals("JSON", requestContext.requestPayloadSource().orElseThrow());
            assertEquals("transport-captured", requestContext.requestContextPhase().orElseThrow());
            assertEquals("parsed", requestContext.requestPayloadParseStatus().orElseThrow());
            assertEquals(CocoWebParameterSource.JSON, snapshot.payloadSource());
            assertEquals(CocoWebParameterSource.JSON, snapshot.parameterSnapshot().payloadSource());
            assertEquals(CocoWebParameterSource.JSON, snapshot.securityInput().payloadSource());
            assertEquals(List.of("******"),
                    snapshot.parameterSnapshot().sourceParameters(CocoWebParameterSource.JSON).get("password"));
            assertTrue(snapshot.parameterSnapshot().sourceParameters(CocoWebParameterSource.FORM).isEmpty());
            assertTrue(snapshot.queryParameters().isEmpty());
            assertEquals(List.of("******"), snapshot.payloadParameters().get("password"));
            assertTrue(requestContext.queryParameter("password").isEmpty());
            assertEquals("******", requestContext.payloadParameter("password").orElseThrow());
            assertTrue(canonicalForm.text().contains("queryParameters\n"));
            assertTrue(canonicalForm.text().contains("payloadParameters\n"));
            assertTrue(canonicalForm.text().contains("buyer.name#1\n"));
            assertTrue(canonicalForm.text().contains("buyer.name[0]=6:Patton\n"));
            assertTrue(canonicalForm.text().contains("tags#2\n"));
            assertTrue(canonicalForm.text().contains("tags[0]=4:sign\n"));
            assertTrue(canonicalForm.text().contains("tags[1]=3:web\n"));
                });
    }

    @Test
    void resolvesCachedFormPayloadParametersIntoContextAndSecurityInput() {
        this.webContextRunner
                .withPropertyValues("coco.web.context.canonicalization.version=coco-v2")
                .run(context -> {
            CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
            CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    byte[] body = "sku=COCO-STARTER&tag=b&tag=a&tag=a&token=abc&name=Coco%20Spring"
                            .getBytes(StandardCharsets.UTF_8);
                    request.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
                    request.addParameter("sku", "COCO-STARTER");
                    request.addParameter("tag", "b", "a", "a");
                    request.addParameter("token", "abc");
                    request.addParameter("name", "Coco Spring");

                    CocoWebRequestSnapshot snapshot = resolver.resolve("form-payload-trace",
                            new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
            CocoWebRequestCanonicalForm canonicalForm = canonicalizer.canonicalize(snapshot.securityInput());

                    assertEquals(List.of("COCO-STARTER"), snapshot.securityInput().parameter("sku").orElseThrow());
                    assertEquals(List.of("b", "a", "a"), snapshot.securityInput().parameter("tag").orElseThrow());
                    assertEquals(List.of("Coco%20Spring"), snapshot.securityInput().parameter("name").orElseThrow());
                    assertTrue(snapshot.securityInput().queryParameters().isEmpty());
                    assertEquals(List.of("b", "a", "a"),
                            snapshot.securityInput().payloadParameter("tag").orElseThrow());
                    assertEquals(List.of("Coco Spring"), snapshot.parameters().get("name"));
                    assertEquals(List.of("b", "a", "a"), snapshot.parameters().get("tag"));
                    assertEquals(List.of("******"), snapshot.parameters().get("token"));
                    assertEquals(CocoWebParameterSource.FORM, snapshot.payloadSource());
                    assertEquals(CocoWebParameterSource.FORM, snapshot.parameterSnapshot().payloadSource());
                    assertEquals(CocoWebParameterSource.FORM, snapshot.securityInput().payloadSource());
                    assertEquals(List.of("******"),
                            snapshot.parameterSnapshot().sourceParameters(CocoWebParameterSource.FORM).get("token"));
                    assertTrue(snapshot.parameterSnapshot().sourceParameters(CocoWebParameterSource.JSON).isEmpty());
                    assertTrue(snapshot.queryParameters().isEmpty());
                    assertEquals(List.of("******"), snapshot.payloadParameters().get("token"));
                    CocoRequestContext requestContext = snapshot.toRequestContext();
                    assertEquals("FORM", requestContext.requestPayloadSource().orElseThrow());
                    assertEquals("transport-captured", requestContext.requestContextPhase().orElseThrow());
                    assertEquals("parsed", requestContext.requestPayloadParseStatus().orElseThrow());
                    assertEquals("******", requestContext.payloadParameter("token").orElseThrow());
                    assertTrue(canonicalForm.text().contains("queryParameters\n"));
                    assertTrue(canonicalForm.text().contains("payloadParameters\n"));
                    assertTrue(canonicalForm.text().contains("tag#3\n"));
                    assertTrue(canonicalForm.text().contains("tag[0]=1:a\n"));
                    assertTrue(canonicalForm.text().contains("tag[1]=1:a\n"));
                    assertTrue(canonicalForm.text().contains("tag[2]=1:b\n"));
                });
    }

    @Test
    void canonicalizerV2SeparatesQueryAndPayloadParameters() {
        this.webContextRunner
                .withPropertyValues("coco.web.context.canonicalization.version=coco-v2")
                .run(context -> {
                    CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
                    CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    byte[] body = "{\"sku\":\"BODY\",\"tag\":\"payload\"}".getBytes(StandardCharsets.UTF_8);
                    request.setQueryString("sku=QUERY&tag=query");
                    request.addParameter("sku", "QUERY");
                    request.addParameter("tag", "query");
                    request.setContentType(MediaType.APPLICATION_JSON_VALUE);

                    CocoWebRequestSnapshot snapshot = resolver.resolve("split-parameter-trace",
                            new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
                    CocoWebRequestCanonicalForm canonicalForm = canonicalizer.canonicalize(snapshot.securityInput());

                    assertEquals(List.of("QUERY", "BODY"),
                            snapshot.securityInput().parameter("sku").orElseThrow());
                    assertEquals(List.of("QUERY"), snapshot.securityInput().queryParameter("sku").orElseThrow());
                    assertEquals(List.of("BODY"), snapshot.securityInput().payloadParameter("sku").orElseThrow());
                    assertTrue(canonicalForm.text().contains("queryParameters\n"));
                    assertTrue(canonicalForm.text().contains("payloadParameters\n"));
                    assertTrue(canonicalForm.text().contains("sku[0]=5:QUERY\n"));
                    assertTrue(canonicalForm.text().contains("sku[0]=4:BODY\n"));
                    assertFalse(canonicalForm.text().contains("parameters\nsku#2\n"));
                });
    }

    @Test
    void skipsEncryptedTransportPayloadParametersBeforeDecryption() {
        this.webContextRunner
                .withPropertyValues("coco.web.context.canonicalization.version=coco-v2")
                .run(context -> {
                    CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
                    CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    byte[] body = "{\"sku\":\"TRANSPORT\",\"token\":\"cipher\"}".getBytes(StandardCharsets.UTF_8);
                    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    request.addHeader("X-Coco-Encrypted", "true");

                    CocoWebRequestSnapshot snapshot = resolver.resolve("encrypted-transport-trace",
                            new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
                    CocoWebRequestCanonicalForm canonicalForm = canonicalizer.canonicalize(snapshot.securityInput());

                    assertTrue(snapshot.parameters().isEmpty());
                    assertTrue(snapshot.securityInput().parameter("sku").isEmpty());
                    assertTrue(snapshot.securityInput().payloadParameter("sku").isEmpty());
                    assertTrue(snapshot.securityInput().payloadParameters().isEmpty());
                    assertEquals("transport", snapshot.requestBody().stage().id());
                    assertEquals(sha256(body), snapshot.requestBody().transportSha256());
                    assertEquals(sha256(body), snapshot.requestBody().effectiveSha256());
                    assertEquals("transport-captured",
                            snapshot.toRequestContext().requestContextPhase().orElseThrow());
                    assertEquals("encrypted-transport",
                            snapshot.toRequestContext().requestPayloadParseStatus().orElseThrow());
                    assertFalse(canonicalForm.text().contains("TRANSPORT"));
                    assertFalse(canonicalForm.text().contains("cipher"));
                });
    }

    @Test
    void marksMalformedJsonPayloadAndPreservesPayloadSource() {
        this.webContextRunner.run(context -> {
            CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
            byte[] body = "{\"sku\":\"COCO-STARTER\"".getBytes(StandardCharsets.UTF_8);
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);

            CocoWebRequestSnapshot snapshot = resolver.resolve("malformed-json-payload",
                    new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
            CocoRequestContext requestContext = snapshot.toRequestContext();

            assertTrue(snapshot.parameters().isEmpty());
            assertTrue(snapshot.payloadParameters().isEmpty());
            assertEquals(CocoWebParameterSource.JSON, snapshot.payloadSource());
            assertEquals(CocoWebParameterSource.JSON, snapshot.parameterSnapshot().payloadSource());
            assertEquals(CocoWebParameterSource.JSON, snapshot.securityInput().payloadSource());
            assertEquals("JSON", requestContext.requestPayloadSource().orElseThrow());
            assertEquals("malformed-payload", requestContext.requestPayloadParseStatus().orElseThrow());
        });
    }

    @Test
    void marksJsonPayloadAsDepthLimitedWhenMaxDepthIsReached() {
        this.webContextRunner
                .withPropertyValues("coco.web.context.parameter.payload.max-json-depth=1")
                .run(context -> {
                    CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    byte[] body = ("{\"sku\":\"COCO-STARTER\",\"buyer\":{\"profile\":{\"name\":\"Patton\"}}}")
                            .getBytes(StandardCharsets.UTF_8);
                    request.setContentType(MediaType.APPLICATION_JSON_VALUE);

                    CocoWebRequestSnapshot snapshot = resolver.resolve("json-depth-limit-payload",
                            new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
                    CocoRequestContext requestContext = snapshot.toRequestContext();

                    assertEquals(List.of("COCO-STARTER"), snapshot.payloadParameters().get("sku"));
                    assertFalse(snapshot.payloadParameters().containsKey("buyer.profile.name"));
                    assertEquals(CocoWebParameterSource.JSON, snapshot.payloadSource());
                    assertEquals("json-depth-limit-reached",
                            requestContext.requestPayloadParseStatus().orElseThrow());
                });
    }

    @Test
    void marksFormPayloadAsParameterLimitedWhenMaxCountIsReached() {
        this.webContextRunner
                .withPropertyValues("coco.web.context.parameter.payload.max-parameter-count=2")
                .run(context -> {
                    CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    byte[] body = "sku=COCO-STARTER&tag=web&token=secret".getBytes(StandardCharsets.UTF_8);
                    request.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
                    request.addParameter("sku", "COCO-STARTER");
                    request.addParameter("tag", "web");
                    request.addParameter("token", "secret");

                    CocoWebRequestSnapshot snapshot = resolver.resolve("form-parameter-limit-payload",
                            new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
                    CocoRequestContext requestContext = snapshot.toRequestContext();

                    assertEquals(List.of("COCO-STARTER"), snapshot.payloadParameters().get("sku"));
                    assertEquals(List.of("web"), snapshot.payloadParameters().get("tag"));
                    assertFalse(snapshot.payloadParameters().containsKey("token"));
                    assertEquals(CocoWebParameterSource.FORM, snapshot.payloadSource());
                    assertEquals("parameter-limit-reached",
                            requestContext.requestPayloadParseStatus().orElseThrow());
                });
    }

    @Test
    void skipsCachedPayloadParametersWhenPayloadParsingIsDisabled() {
        this.webContextRunner
                .withPropertyValues("coco.web.context.parameter.payload.enabled=false")
                .run(context -> {
                    CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    byte[] body = "{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8);
                    request.setContentType(MediaType.APPLICATION_JSON_VALUE);

                    CocoWebRequestSnapshot snapshot = resolver.resolve("payload-disabled-trace",
                            new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));

                    assertFalse(snapshot.parameters().containsKey("sku"));
                    assertTrue(snapshot.securityInput().parameter("sku").isEmpty());
                    assertEquals("transport-captured",
                            snapshot.toRequestContext().requestContextPhase().orElseThrow());
                    assertEquals("disabled",
                            snapshot.toRequestContext().requestPayloadParseStatus().orElseThrow());
                });
    }

    @Test
    void marksJsonPayloadAsNotCachedWhenRequestBodyWasNotWrapped() {
        this.webContextRunner.run(context -> {
            CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.setContent("{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8));

            CocoRequestContext requestContext = resolver.resolve("payload-not-cached-trace", request).toRequestContext();

            assertEquals("transport-captured", requestContext.requestContextPhase().orElseThrow());
            assertEquals("not-cached", requestContext.requestPayloadParseStatus().orElseThrow());
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
                + "query=tag\\=b&tag\\=a&sku\\=COCO-STARTER\n"
                + "headers\n"
                + "content-type#1\n"
                + "content-type[0]=16:application/json\n"
                + "x-coco-timestamp#1\n"
                + "x-coco-timestamp[0]=13:1783300000000\n"
                + "parameters\n"
                + "sku#1\n"
                + "sku[0]=12:COCO-STARTER\n"
                + "tag#2\n"
                + "tag[0]=1:a\n"
                + "tag[1]=1:b\n"
                + "bodySha256=body-digest\n"
                + "bodyLength=12\n";
        assertEquals(expectedText, canonicalForm.text());
        assertEquals(sha256(expectedText), canonicalForm.sha256());
    }

    @Test
    void defaultRequestCanonicalizerEscapesFramedValues() {
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders",
                "tag=a,b&tag=a&tag=b",
                Map.of("tag", List.of("a,b", "a", "b")),
                Map.of(), Map.of("x-coco-name", "Coco:Runtime", "x-coco-flags", "a=b|c"),
                "body-digest", 12L, true);

        CocoWebRequestCanonicalForm canonicalForm = new DefaultCocoWebRequestCanonicalizer().canonicalize(input);

        assertTrue(canonicalForm.text().contains("x-coco-name[0]=13:Coco\\:Runtime"));
        assertTrue(canonicalForm.text().contains("x-coco-flags[0]=7:a\\=b\\|c"));
        assertTrue(canonicalForm.text().contains("tag#3\n"));
        assertTrue(canonicalForm.text().contains("tag[0]=1:a\n"));
        assertTrue(canonicalForm.text().contains("tag[1]=4:a\\,b\n"));
        assertTrue(canonicalForm.text().contains("tag[2]=1:b\n"));
        assertEquals(sha256(canonicalForm.text()), canonicalForm.sha256());
    }

    @Test
    void requestCanonicalizerUsesFramedParametersForV2() {
        CocoWebRequestCanonicalizationProperties properties = new CocoWebRequestCanonicalizationProperties();
        properties.setVersion("coco-v2");
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders",
                "tag=a&tag=a%3Bb&tag=a%7Cb",
                Map.of("tag", List.of("a|b", "a;b", "a"), "empty", List.of("")),
                Map.of(), Map.of(), null, null, false);

        CocoWebRequestCanonicalForm canonicalForm = new DefaultCocoWebRequestCanonicalizer(properties)
                .canonicalize(input);

        assertTrue(canonicalForm.text().contains("parameters\n"));
        assertTrue(canonicalForm.text().contains("empty#1\n"));
        assertTrue(canonicalForm.text().contains("empty[0]=0:\n"));
        assertTrue(canonicalForm.text().contains("tag#3\n"));
        assertTrue(canonicalForm.text().contains("tag[0]=1:a\n"));
        assertTrue(canonicalForm.text().contains("tag[1]=4:a\\;b\n"));
        assertTrue(canonicalForm.text().contains("tag[2]=4:a\\|b\n"));
        assertEquals(sha256(canonicalForm.text()), canonicalForm.sha256());
    }

    @Test
    void requestCanonicalizerSeparatesValuesWithoutParameterSeparatorAmbiguity() {
        CocoWebRequestCanonicalizationProperties framedProperties =
                parameterOnlyCanonicalizationProperties("coco-v2");
        framedProperties.setParameterValueSeparator("a");
        CocoWebRequestSecurityInput singleValue = new CocoWebRequestSecurityInput("POST", "/api/orders",
                null, Map.of("tag", List.of("a")), Map.of(), Map.of(), null, null, false);
        CocoWebRequestSecurityInput twoEmptyValues = new CocoWebRequestSecurityInput("POST", "/api/orders",
                null, Map.of("tag", List.of("", "")), Map.of(), Map.of(), null, null, false);

        String framedSingleValueText = new DefaultCocoWebRequestCanonicalizer(framedProperties)
                .canonicalize(singleValue).text();
        String framedTwoEmptyValuesText = new DefaultCocoWebRequestCanonicalizer(framedProperties)
                .canonicalize(twoEmptyValues).text();

        assertTrue(framedSingleValueText.contains("tag#1\n"));
        assertTrue(framedSingleValueText.contains("tag[0]=1:a\n"));
        assertTrue(framedTwoEmptyValuesText.contains("tag#2\n"));
        assertTrue(framedTwoEmptyValuesText.contains("tag[0]=0:\n"));
        assertTrue(framedTwoEmptyValuesText.contains("tag[1]=0:\n"));
        assertFalse(framedSingleValueText.equals(framedTwoEmptyValuesText));
    }

    @Test
    void defaultRequestCanonicalizerFramesMultiValueHeaders() {
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders",
                null, Map.of(), Map.of(), Map.of("x-name", "Coconut,Runtime"), null, null, false,
                Map.of("x-name", List.of("Coconut", "Runtime"), "x-comma", List.of("Coconut,Runtime")));

        CocoWebRequestCanonicalForm canonicalForm = new DefaultCocoWebRequestCanonicalizer().canonicalize(input);

        assertTrue(canonicalForm.text().contains("x-name#2"));
        assertTrue(canonicalForm.text().contains("x-name[0]=7:Coconut"));
        assertTrue(canonicalForm.text().contains("x-name[1]=7:Runtime"));
        assertTrue(canonicalForm.text().contains("x-comma#1"));
        assertTrue(canonicalForm.text().contains("x-comma[0]=16:Coconut\\,Runtime"));
    }

    @Test
    void requestCanonicalizerIncludesCanonicalCookiesWhenEnabledOrSigning() {
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders",
                null, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), null, null, false,
                Map.of(), Map.of("SESSION", "secret-session", "theme", "dark=blue"));
        CocoWebRequestCanonicalizationProperties generalProperties = new CocoWebRequestCanonicalizationProperties();
        generalProperties.setIncludeCookies(true);

        CocoWebRequestCanonicalForm generalForm =
                new DefaultCocoWebRequestCanonicalizer(generalProperties).canonicalize(input);
        CocoWebRequestCanonicalForm signatureForm = new DefaultCocoWebRequestCanonicalizer()
                .canonicalize(new CocoWebRequestCanonicalizationContext(
                        CocoWebRequestCanonicalizationPurpose.SIGNATURE, input, null,
                        CocoBrowserFingerprint.empty()));

        assertTrue(generalForm.text().contains("cookies\n"));
        assertTrue(generalForm.text().contains("SESSION=14:secret-session\n"));
        assertTrue(generalForm.text().contains("theme=10:dark\\=blue\n"));
        assertTrue(signatureForm.text().contains("cookies\n"));
        assertEquals(sha256(generalForm.text()), generalForm.sha256());
        assertEquals(sha256(signatureForm.text()), signatureForm.sha256());
    }

    @Test
    void requestCanonicalizerCanIncludeBrowserFingerprintContext() {
        CocoWebRequestCanonicalizationProperties properties = new CocoWebRequestCanonicalizationProperties();
        properties.setIncludeBrowserFingerprint(true);
        properties.setIncludeBrowserFingerprintSignals(true);
        CocoBrowserFingerprint browserFingerprint = CocoBrowserFingerprint.from(Map.of(
                "User-Agent", "Chrome",
                "Accept-Language", "zh-CN"));
        CocoWebRequestCanonicalizationContext canonicalizationContext =
                new CocoWebRequestCanonicalizationContext(CocoWebRequestCanonicalizationPurpose.GENERAL,
                        CocoWebRequestSecurityInput.empty(), CocoWebRequestSecurityMetadata.empty(),
                        browserFingerprint);

        CocoWebRequestCanonicalForm canonicalForm =
                new DefaultCocoWebRequestCanonicalizer(properties).canonicalize(canonicalizationContext);
        CocoWebRequestCanonicalForm defaultForm =
                new DefaultCocoWebRequestCanonicalizer().canonicalize(canonicalizationContext);

        assertTrue(canonicalForm.text().contains("browserFingerprint=" + browserFingerprint.value() + "\n"));
        assertTrue(canonicalForm.text().contains("browserFingerprintSignals\n"));
        assertTrue(canonicalForm.text().contains("accept-language=5:zh-CN\n"));
        assertTrue(canonicalForm.text().contains("user-agent=6:Chrome\n"));
        assertFalse(defaultForm.text().contains("browserFingerprint"));
        assertEquals(sha256(canonicalForm.text()), canonicalForm.sha256());
    }

    @Test
    void defaultWebRequestMatcherMatchesMethodAndContextRelativePath() {
        CocoWebRequestMatchRule rule = new CocoWebRequestMatchRule();
        rule.setMethods(Set.of("post"));
        rule.setPathPatterns(Set.of("/api/**"));
        CocoWebRequestMatcher matcher = new DefaultCocoWebRequestMatcher();
        MockHttpServletRequest matchedRequest = new MockHttpServletRequest("POST", "/sample/api/orders");
        matchedRequest.setContextPath("/sample");
        MockHttpServletRequest methodMismatchRequest = new MockHttpServletRequest("GET", "/sample/api/orders");
        methodMismatchRequest.setContextPath("/sample");
        MockHttpServletRequest pathMismatchRequest = new MockHttpServletRequest("POST", "/sample/public/orders");
        pathMismatchRequest.setContextPath("/sample");

        assertTrue(matcher.matches(matchedRequest, List.of(rule)));
        assertFalse(matcher.matches(methodMismatchRequest, List.of(rule)));
        assertFalse(matcher.matches(pathMismatchRequest, List.of(rule)));
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
                    assertTrue(canonicalForm.text().contains("tag#2\n"));
                    assertTrue(canonicalForm.text().contains("tag[0]=1:b\n"));
                    assertTrue(canonicalForm.text().contains("tag[1]=1:a\n"));
                    assertFalse(canonicalForm.text().contains("tag=b;a"));
                    assertTrue(canonicalForm.text().contains("bodySha256=body-digest"));
                    assertFalse(canonicalForm.text().contains("bodyLength="));
                    assertEquals(64, canonicalForm.sha256().length());
                });
    }

    @Test
    void signatureCanonicalFormKeepsSecurityFieldsWhenGeneralConfigurationDisablesThem() {
        CocoWebRequestCanonicalizationProperties properties = new CocoWebRequestCanonicalizationProperties();
        properties.setIncludeVersion(false);
        properties.setIncludePurpose(false);
        properties.setIncludeMethod(false);
        properties.setIncludePath(false);
        properties.setIncludeQueryString(false);
        properties.setIncludeHeaders(false);
        properties.setIncludeBodySha256(false);
        properties.setIncludeBodyLength(false);
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders",
                "sku=COCO-STARTER",
                Map.of("sku", List.of("COCO-STARTER")),
                Map.of(), Map.of("x-coco-timestamp", "1783300000000"),
                "body-digest", 12L, true);
        CocoWebRequestCanonicalizationContext canonicalizationContext = new CocoWebRequestCanonicalizationContext(
                CocoWebRequestCanonicalizationPurpose.SIGNATURE, input, null, CocoBrowserFingerprint.empty());

        CocoWebRequestCanonicalForm canonicalForm = new DefaultCocoWebRequestCanonicalizer(properties)
                .canonicalize(canonicalizationContext);

        assertTrue(canonicalForm.text().contains("version=coco-v2"));
        assertTrue(canonicalForm.text().contains("purpose=SIGNATURE"));
        assertTrue(canonicalForm.text().contains("method=POST"));
        assertTrue(canonicalForm.text().contains("path=/api/orders"));
        assertTrue(canonicalForm.text().contains("query=sku\\=COCO-STARTER"));
        assertTrue(canonicalForm.text().contains("headers"));
        assertTrue(canonicalForm.text().contains("x-coco-timestamp[0]=13:1783300000000"));
        assertTrue(canonicalForm.text().contains("bodySha256=body-digest"));
        assertTrue(canonicalForm.text().contains("bodyLength=12"));
    }

    @Test
    void defaultClientIpResolverIgnoresForwardedHeadersFromUntrustedRemoteAddress() {
        CocoClientIpResolver resolver = new DefaultCocoClientIpResolver(new CocoWebContextProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Forwarded", "for=\"[2001:db8:cafe::17]:4711\";proto=https");
        request.addHeader("X-Forwarded-For", "10.0.0.8");

        assertEquals("127.0.0.1", resolver.resolve(request));
        CocoClientIpResolution resolution = resolver.resolveResolution(request);
        assertEquals("127.0.0.1", resolution.clientIp());
        assertEquals(CocoClientIpSource.REMOTE_ADDRESS, resolution.source());
        assertEquals("127.0.0.1", resolution.remoteAddress());
        assertFalse(resolution.trustedProxy());
        assertTrue(resolution.sourceChain().isEmpty());
        assertTrue(resolution.resolvedChainIndex() == null);
    }

    @Test
    void defaultClientIpResolverParsesForwardedHeadersFromTrustedProxy() {
        CocoWebContextProperties properties = new CocoWebContextProperties();
        properties.setTrustedProxyCidrs(Set.of("127.0.0.1/32"));
        CocoClientIpResolver resolver = new DefaultCocoClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Forwarded", "for=\"[2001:db8:cafe::17]:4711\";proto=https");
        request.addHeader("X-Forwarded-For", "10.0.0.8");

        assertEquals("2001:db8:cafe::17", resolver.resolve(request));
        CocoClientIpResolution resolution = resolver.resolveResolution(request);
        assertEquals("2001:db8:cafe::17", resolution.clientIp());
        assertEquals(CocoClientIpSource.FORWARDED_HEADER, resolution.source());
        assertEquals("Forwarded", resolution.sourceHeaderName());
        assertEquals("for=\"[2001:db8:cafe::17]:4711\";proto=https", resolution.sourceHeaderValue());
        assertEquals("127.0.0.1", resolution.remoteAddress());
        assertTrue(resolution.trustedProxy());
        assertEquals(List.of("2001:db8:cafe::17"), resolution.sourceChain());
        assertEquals(0, resolution.resolvedChainIndex());
    }

    @Test
    void defaultClientIpResolverUsesNearestUntrustedHopFromProxyChain() {
        CocoWebContextProperties properties = new CocoWebContextProperties();
        properties.setTrustedProxyCidrs(Set.of("127.0.0.1/32", "10.0.0.0/8"));
        CocoClientIpResolver resolver = new DefaultCocoClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.20, 203.0.113.8, 10.0.0.9");

        assertEquals("203.0.113.8", resolver.resolve(request));
        CocoClientIpResolution resolution = resolver.resolveResolution(request);
        assertEquals("203.0.113.8", resolution.clientIp());
        assertEquals(CocoClientIpSource.FORWARDED_HEADER, resolution.source());
        assertEquals("X-Forwarded-For", resolution.sourceHeaderName());
        assertTrue(resolution.trustedProxy());
        assertEquals(List.of("198.51.100.20", "203.0.113.8", "10.0.0.9"), resolution.sourceChain());
        assertEquals(1, resolution.resolvedChainIndex());
    }

    @Test
    void defaultClientIpResolverSkipsInvalidForwardedValues() {
        CocoWebContextProperties properties = new CocoWebContextProperties();
        properties.setTrustedProxyCidrs(Set.of("127.0.0.1"));
        CocoClientIpResolver resolver = new DefaultCocoClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Forwarded", "for=_hidden, for=unknown, for=10.0.0.8:8443");

        assertEquals("10.0.0.8", resolver.resolve(request));
        CocoClientIpResolution resolution = resolver.resolveResolution(request);
        assertEquals(CocoClientIpSource.FORWARDED_HEADER, resolution.source());
        assertEquals("Forwarded", resolution.sourceHeaderName());
        assertTrue(resolution.trustedProxy());
    }

    @Test
    void defaultClientIpResolverRejectsMalformedForwardedPorts() {
        CocoWebContextProperties properties = new CocoWebContextProperties();
        properties.setTrustedProxyCidrs(Set.of("127.0.0.1"));
        CocoClientIpResolver resolver = new DefaultCocoClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Forwarded", "for=\"[2001:db8:cafe::17]bad\", for=10.0.0.8:https");
        request.addHeader("X-Forwarded-For", "10.0.0.9:8443");

        CocoClientIpResolution resolution = resolver.resolveResolution(request);

        assertEquals("10.0.0.9", resolution.clientIp());
        assertEquals(CocoClientIpSource.FORWARDED_HEADER, resolution.source());
        assertEquals("X-Forwarded-For", resolution.sourceHeaderName());
        assertTrue(resolution.trustedProxy());
    }

    @Test
    void defaultRequestTargetResolverUsesServletTargetWhenProxyIsUntrusted() {
        CocoWebContextProperties properties = new CocoWebContextProperties();
        CocoWebRequestTargetResolver resolver = new DefaultCocoWebRequestTargetResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/orders");
        request.setScheme("http");
        request.setServerName("internal.coco.local");
        request.setServerPort(8080);
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("Forwarded", "proto=https;host=api.coco.dev");
        request.addHeader("X-Forwarded-Prefix", "/gateway");

        CocoWebRequestTargetResolution resolution = resolver.resolveResolution(request);
        CocoWebRequestTarget target = resolution.target();

        assertEquals("http", target.scheme());
        assertEquals("internal.coco.local", target.host());
        assertEquals(8080, target.port());
        assertEquals("/internal/orders", target.path());
        assertEquals(CocoWebRequestTargetSource.SERVLET, resolution.source());
        assertEquals("203.0.113.10", resolution.remoteAddress());
        assertFalse(resolution.trustedProxy());
        assertTrue(resolution.sourceHeaders().isEmpty());
        assertNull(resolution.forwardedPrefix());
    }

    @Test
    void defaultRequestTargetResolverParsesForwardedTargetFromTrustedProxy() {
        CocoWebContextProperties properties = new CocoWebContextProperties();
        properties.setTrustedProxyCidrs(Set.of("127.0.0.1/32"));
        CocoWebRequestTargetResolver resolver = new DefaultCocoWebRequestTargetResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.setScheme("http");
        request.setServerName("internal.coco.local");
        request.setServerPort(8080);
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Forwarded", "for=203.0.113.10;proto=https;host=\"api.coco.dev:8443\"");
        request.addHeader("X-Forwarded-Prefix", "/gateway");

        CocoWebRequestTargetResolution resolution = resolver.resolveResolution(request);
        CocoWebRequestTarget target = resolution.target();

        assertEquals("https", target.scheme());
        assertEquals("api.coco.dev", target.host());
        assertEquals(8443, target.port());
        assertEquals("/gateway/orders", target.path());
        assertEquals(CocoWebRequestTargetSource.MIXED, resolution.source());
        assertEquals("127.0.0.1", resolution.remoteAddress());
        assertTrue(resolution.trustedProxy());
        assertEquals(List.of("forwarded", "x-forwarded-prefix"), resolution.sourceHeaders());
        assertEquals("/gateway", resolution.forwardedPrefix());
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
    void defaultBrowserFingerprintResolverFramesMultiValueHeaderSignals() {
        CocoWebProperties properties = new CocoWebProperties();
        properties.getContext().setFingerprintHeaderNames(Set.of("X-Fp"));
        properties.getContext().setMaxHeaderValueLength(32);
        CocoBrowserFingerprintResolver resolver = new DefaultCocoBrowserFingerprintResolver(properties.getContext());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("X-Fp", "alpha");
        request.addHeader("X-Fp", "beta,gamma");

        CocoBrowserFingerprint fingerprint = resolver.resolve(request);

        assertNotNull(fingerprint.value());
        assertEquals("0=5:alpha;1=10:beta,gamma;", fingerprint.signals().get("x-fp"));
    }

    @Test
    void defaultBrowserFingerprintResolverHashesFullSignalsBeforeDisplayTrimming() {
        CocoWebProperties properties = new CocoWebProperties();
        properties.getContext().setFingerprintHeaderNames(Set.of("User-Agent"));
        properties.getContext().setMaxHeaderValueLength(8);
        CocoBrowserFingerprintResolver resolver = new DefaultCocoBrowserFingerprintResolver(properties.getContext());
        MockHttpServletRequest firstRequest = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletRequest secondRequest = new MockHttpServletRequest("GET", "/api/users");
        firstRequest.addHeader("User-Agent", "Chrome/126-A");
        secondRequest.addHeader("User-Agent", "Chrome/126-B");

        CocoBrowserFingerprint firstFingerprint = resolver.resolve(firstRequest);
        CocoBrowserFingerprint secondFingerprint = resolver.resolve(secondRequest);

        assertEquals("Chrome/1...", firstFingerprint.signals().get("user-agent"));
        assertEquals("Chrome/1...", secondFingerprint.signals().get("user-agent"));
        assertNotEquals(firstFingerprint.value(), secondFingerprint.value());
    }

    @Test
    void defaultRequestBodyResolverSeparatesTransportAndEffectiveBodies() {
        CocoRequestBodyResolver resolver = new DefaultCocoRequestBodyResolver();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        byte[] transportBody = "cipher".getBytes(StandardCharsets.UTF_8);
        byte[] effectiveBody = "{\"sku\":\"COCO\"}".getBytes(StandardCharsets.UTF_8);
        CocoCachedBodyHttpServletRequest transportRequest = new CocoCachedBodyHttpServletRequest(request,
                CocoCachedRequestBody.cached(transportBody));
        CocoCachedBodyHttpServletRequest effectiveRequest = new CocoCachedBodyHttpServletRequest(transportRequest,
                CocoCachedRequestBody.cached(effectiveBody));

        CocoResolvedRequestBody resolvedBody = resolver.resolve(effectiveRequest);

        assertTrue(resolvedBody.transportCached());
        assertTrue(resolvedBody.effectiveCached());
        assertEquals(sha256(transportBody), resolvedBody.transportBody().sha256());
        assertEquals(sha256(effectiveBody), resolvedBody.effectiveBody().sha256());
        assertEquals(sha256(transportBody), resolvedBody.metadata().transportSha256());
        assertEquals(sha256(effectiveBody), resolvedBody.metadata().effectiveSha256());
        assertEquals("decrypted", resolvedBody.metadata().stage().id());
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
        request.addHeader("X-Name", "Runtime");

        Map<String, String> includedHeaders = resolver.resolveIncludedHeaders(request);
        Map<String, String> selectedHeaders = resolver.resolveSelectedHeaders(request, Set.of("X-Name"), true);
        Map<String, String> rawSelectedHeaders = resolver.resolveSelectedHeaders(request, Set.of("X-Name"), false);
        Map<String, List<String>> rawSelectedHeaderValues = resolver.resolveSelectedHeaderValues(request,
                Set.of("X-Name"), false);

        assertEquals("******", includedHeaders.get("authorization"));
        assertEquals("Coco...", includedHeaders.get("x-name"));
        assertEquals("Coco...", selectedHeaders.get("x-name"));
        assertEquals("Coconut,Runtime", rawSelectedHeaders.get("x-name"));
        assertEquals(List.of("Coconut", "Runtime"), rawSelectedHeaderValues.get("x-name"));
    }

    @Test
    void defaultRequestParameterResolverProvidesSanitizedAndRawViews() {
        CocoWebParameterProperties properties = new CocoWebParameterProperties();
        properties.setMaxParameterValueLength(4);
        CocoRequestParameterResolver resolver = new DefaultCocoRequestParameterResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setQueryString("password=abcdef&name=Coconut");
        request.addParameter("password", "abcdef");
        request.addParameter("name", "Coconut");

        assertEquals("password=******&name=Coco...", resolver.resolveQueryString(request));
        assertEquals("password=abcdef&name=Coconut", resolver.resolveRawQueryString(request));
        assertEquals(List.of("******"), resolver.resolveParameters(request).get("password"));
        assertEquals(List.of("Coco..."), resolver.resolveParameters(request).get("name"));
        assertEquals(List.of("abcdef"), resolver.resolveRawParameters(request).get("password"));

        CocoWebRequestParameters parameterSnapshot = resolver.resolveParameterSnapshot(request);

        assertEquals("password=******&name=Coco...", parameterSnapshot.queryString());
        assertEquals(List.of("******"), parameterSnapshot.values("password").orElseThrow());
        assertEquals("Coco...", parameterSnapshot.firstValue("name", CocoWebParameterSource.QUERY).orElseThrow());
        assertTrue(parameterSnapshot.contains("name", CocoWebParameterSource.MERGED));
        assertEquals(CocoWebParameterSource.NONE, parameterSnapshot.payloadSource());
        assertTrue(parameterSnapshot.payloadParameters().isEmpty());
    }

    @Test
    void rawParametersAreParsedFromRawQueryWithoutServletDecodedValues() {
        CocoRequestParameterResolver resolver = new DefaultCocoRequestParameterResolver(new CocoWebParameterProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");
        request.setQueryString("name=Coco%20Spring&tag=a%2Cb&tag=a,b&empty");
        request.addParameter("name", "Coco Spring");
        request.addParameter("tag", "a,b");
        request.addParameter("bodyOnly", "must-not-enter-security-input");

        Map<String, List<String>> rawParameters = resolver.resolveRawParameters(request);

        assertEquals(List.of("Coco%20Spring"), rawParameters.get("name"));
        assertEquals(List.of("a%2Cb", "a,b"), rawParameters.get("tag"));
        assertEquals(List.of(""), rawParameters.get("empty"));
        assertFalse(rawParameters.containsKey("bodyOnly"));

        CocoWebRequestParameters rawSnapshot = resolver.resolveRawParameterSnapshot(request);
        CocoWebRequestParameters filteredSnapshot = rawSnapshot.without(Set.of("tag"));

        assertEquals("name=Coco%20Spring&tag=a%2Cb&tag=a,b&empty", rawSnapshot.queryString());
        assertEquals(List.of("a%2Cb", "a,b"),
                rawSnapshot.values("tag", CocoWebParameterSource.QUERY).orElseThrow());
        assertEquals("Coco%20Spring", rawSnapshot.firstValue("name").orElseThrow());
        assertEquals(CocoWebParameterSource.NONE, rawSnapshot.payloadSource());
        assertFalse(rawSnapshot.contains("bodyOnly"));
        assertEquals("name=Coco%20Spring&empty", filteredSnapshot.queryString());
        assertTrue(filteredSnapshot.values("tag", CocoWebParameterSource.QUERY).isEmpty());
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
                    assertEquals(CocoClientIpSource.CUSTOM, snapshot.clientIpResolution().source());
                    assertEquals("CUSTOM", snapshot.toRequestContext().clientIpSource().orElseThrow());
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
                    assertTrue(snapshot.queryParameters().isEmpty());
                    assertTrue(snapshot.payloadParameters().isEmpty());
                    assertEquals("raw=query", snapshot.securityInput().queryString());
                    assertEquals(List.of("yes"), snapshot.securityInput().parameter("raw").orElseThrow());
                });
    }

    @Test
    void usesCustomPayloadParameterResolverInRequestContext() {
        CocoPayloadParameterResolver resolver = new CocoPayloadParameterResolver() {

            @Override
            public Map<String, List<String>> resolvePayloadParameters(HttpServletRequest request) {
                return Map.of("payload.clean", List.of("yes"));
            }

            @Override
            public Map<String, List<String>> resolveRawPayloadParameters(HttpServletRequest request) {
                return Map.of("payload.raw", List.of("yes"));
            }
        };
        this.webContextRunner
                .withBean(CocoPayloadParameterResolver.class, () -> resolver)
                .run(context -> {
                    CocoWebRequestContextResolver contextResolver = context.getBean(CocoWebRequestContextResolver.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");

                    CocoWebRequestSnapshot snapshot = contextResolver.resolve("custom-payload-trace", request);

                    assertEquals(List.of("yes"), snapshot.parameters().get("payload.clean"));
                    assertEquals(List.of("yes"), snapshot.payloadParameters().get("payload.clean"));
                    assertEquals(List.of("yes"), snapshot.securityInput().parameter("payload.raw").orElseThrow());
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
    void usesCustomSecurityMetadataResolverInRequestContextAndSignatureFilter() throws Exception {
        AtomicReference<String> verifiedAppId = new AtomicReference<>();
        CocoWebRequestSecurityMetadataResolver metadataResolver = input -> new CocoWebRequestSecurityMetadata(
                "custom-app", "custom-key", String.valueOf(System.currentTimeMillis()), "custom-nonce",
                "HMAC-SHA256", "custom-signature", true,
                null, null, null, null, false, null, null, null, null);
        CocoSignatureVerifier verifier = context -> {
            verifiedAppId.set(context.request().appId());
            return true;
        };
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.signature.required=true",
                        "coco.web.signature.secrets.custom-app=custom-secret")
                .withBean(CocoWebRequestSecurityMetadataResolver.class, () -> metadataResolver)
                .withBean(CocoSignatureVerifier.class, () -> verifier)
                .run(context -> {
                    CocoWebRequestContextResolver contextResolver =
                            context.getBean(CocoWebRequestContextResolver.class);
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/custom-metadata");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "custom-metadata-trace");

                    CocoWebRequestSnapshot snapshot = contextResolver.resolve("custom-metadata-trace", request);

                    assertEquals("custom-app", snapshot.securityMetadata().signatureAppId());
                    signatureFilter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() ->
                            assertTrue(CocoRequestContextHolder.current().orElseThrow().signatureVerified()))));

                    assertEquals(200, response.getStatus(), response.getContentAsString());
                    assertEquals("custom-app", verifiedAppId.get());
                });
    }

    @Test
    void defaultRequestContextResolverCachesSnapshotForSameRequest() {
        DefaultCocoWebRequestContextResolver resolver = new DefaultCocoWebRequestContextResolver(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader("User-Agent", "JUnit");

        CocoWebRequestSnapshot first = resolver.resolve("cached-trace", request);
        CocoWebRequestSnapshot second = resolver.resolve("cached-trace", request);

        assertTrue(first == second);
        assertEquals("JUnit", second.userAgent());
    }

    @Test
    void defaultRequestContextResolverCreatesTraceIdWhenCallerDoesNotProvideOne() {
        DefaultCocoWebRequestContextResolver resolver = new DefaultCocoWebRequestContextResolver(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");

        CocoWebRequestSnapshot snapshot = resolver.resolve(null, request);

        assertNotNull(snapshot.traceId());
        assertFalse(snapshot.traceId().isBlank());
        assertEquals(snapshot.traceId(), CocoTraceContext.currentTraceId().orElseThrow());
    }

    @Test
    void defaultRequestContextResolverRefreshesSnapshotWhenQueryStringChanges() {
        DefaultCocoWebRequestContextResolver resolver = new DefaultCocoWebRequestContextResolver(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setQueryString("name=first");

        CocoWebRequestSnapshot first = resolver.resolve("cached-query-trace", request);
        request.setQueryString("name=second");
        CocoWebRequestSnapshot second = resolver.resolve("cached-query-trace", request);

        assertFalse(first == second);
        assertEquals("name=first", first.securityInput().queryString());
        assertEquals("name=second", second.securityInput().queryString());
    }

    @Test
    void defaultRequestContextResolverRefreshesSnapshotWhenBodyBecomesCached() {
        DefaultCocoWebRequestContextResolver resolver = new DefaultCocoWebRequestContextResolver(null);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        byte[] body = "{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8);

        CocoWebRequestSnapshot beforeBodyCache = resolver.resolve("body-cache-refresh", request);
        CocoWebRequestSnapshot afterBodyCache = resolver.resolve("body-cache-refresh",
                new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));

        assertNull(beforeBodyCache.securityInput().bodySha256());
        assertNotNull(afterBodyCache.securityInput().bodySha256());
        assertFalse(beforeBodyCache == afterBodyCache);
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
                        "x-coco-sign-algorithm", "HMAC-SHA256",
                        "x-coco-encrypted", "true",
                        "x-coco-iv", "iv-1001",
                        "x-coco-algorithm", "AES-GCM"),
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
        assertEquals("sample-app", metadata.replayAppId());
        assertEquals("key-1001", metadata.replayKeyId());
        assertEquals("1700000000000", metadata.replayTimestamp());
        assertEquals("nonce-1001", metadata.replayNonce());
        assertEquals("sample-app", metadata.primaryAppId().orElseThrow());
        assertEquals("key-1001", metadata.primaryKeyId().orElseThrow());
    }

    @Test
    void resolvesSecurityMetadataFromConfiguredParameterSource() {
        CocoSignatureProperties signatureProperties = new CocoSignatureProperties();
        signatureProperties.setMetadataSource(CocoWebSecurityMetadataSource.PARAMETER);
        CocoEncryptionProperties encryptionProperties = new CocoEncryptionProperties();
        encryptionProperties.setMetadataSource(CocoWebSecurityMetadataSource.PARAMETER);
        CocoReplayProperties replayProperties = new CocoReplayProperties();
        replayProperties.setMetadataSource(CocoWebSecurityMetadataSource.PARAMETER);
        CocoWebRequestSecurityMetadataResolver resolver = new DefaultCocoWebRequestSecurityMetadataResolver(
                signatureProperties, encryptionProperties, replayProperties);
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders", null,
                Map.of(
                        "appId", List.of("sample-app"),
                        "keyId", List.of("key-1001"),
                        "timestamp", List.of("1700000000000"),
                        "nonce", List.of("nonce-1001"),
                        "sign", List.of("signature-1001"),
                        "signAlgorithm", List.of("HMAC-SHA256"),
                        "encrypted", List.of("1"),
                        "iv", List.of("iv-1001"),
                        "algorithm", List.of("AES-GCM")),
                Map.of(), Map.of(), null);

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
        assertEquals("sample-app", metadata.replayAppId());
        assertEquals("key-1001", metadata.replayKeyId());
        assertEquals("1700000000000", metadata.replayTimestamp());
        assertEquals("nonce-1001", metadata.replayNonce());
    }

    @Test
    void prefersQueryParametersOverPayloadParametersForSecurityMetadata() {
        CocoSignatureProperties signatureProperties = new CocoSignatureProperties();
        signatureProperties.setMetadataSource(CocoWebSecurityMetadataSource.PARAMETER);
        CocoWebRequestSecurityMetadataResolver resolver = new DefaultCocoWebRequestSecurityMetadataResolver(
                signatureProperties, new CocoEncryptionProperties(), new CocoReplayProperties());
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders",
                "appId=query-app&nonce=query-nonce&sign=query-signature", Map.of(),
                Map.of(
                        "appId", List.of("query-app"),
                        "nonce", List.of("query-nonce"),
                        "sign", List.of("query-signature")),
                Map.of(
                        "appId", List.of("payload-app"),
                        "nonce", List.of("payload-nonce"),
                        "sign", List.of("payload-signature")),
                Map.of(), Map.of(), null, null, false, Map.of(), Map.of(), CocoWebParameterSource.FORM);

        CocoWebRequestSecurityMetadata metadata = resolver.resolve(input);

        assertEquals("query-app", metadata.signatureAppId());
        assertEquals("query-nonce", metadata.signatureNonce());
        assertEquals("query-signature", metadata.signature());
    }

    @Test
    void fallsBackToPayloadParametersWhenQuerySecurityMetadataIsMissing() {
        CocoSignatureProperties signatureProperties = new CocoSignatureProperties();
        signatureProperties.setMetadataSource(CocoWebSecurityMetadataSource.PARAMETER);
        CocoWebRequestSecurityMetadataResolver resolver = new DefaultCocoWebRequestSecurityMetadataResolver(
                signatureProperties, new CocoEncryptionProperties(), new CocoReplayProperties());
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders", null, Map.of(),
                Map.of(),
                Map.of(
                        "appId", List.of("payload-app"),
                        "nonce", List.of("payload-nonce"),
                        "sign", List.of("payload-signature")),
                Map.of(), Map.of(), null, null, false, Map.of(), Map.of(), CocoWebParameterSource.FORM);

        CocoWebRequestSecurityMetadata metadata = resolver.resolve(input);

        assertEquals("payload-app", metadata.signatureAppId());
        assertEquals("payload-nonce", metadata.signatureNonce());
        assertEquals("payload-signature", metadata.signature());
    }

    @Test
    void resolvesSecurityMetadataUsingConfiguredSourcePriority() {
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders", null,
                Map.of(
                        "appId", List.of("parameter-app"),
                        "timestamp", List.of("1700000000000"),
                        "nonce", List.of("parameter-nonce"),
                        "sign", List.of("parameter-signature")),
                Map.of(
                        "x-coco-app-id", "header-app",
                        "x-coco-timestamp", "1700000000001",
                        "x-coco-nonce", "header-nonce",
                        "x-coco-sign", "header-signature"),
                Map.of(), null);
        CocoSignatureProperties headerFirstProperties = new CocoSignatureProperties();
        headerFirstProperties.setMetadataSource(CocoWebSecurityMetadataSource.HEADER_THEN_PARAMETER);
        CocoSignatureProperties parameterFirstProperties = new CocoSignatureProperties();
        parameterFirstProperties.setMetadataSource(CocoWebSecurityMetadataSource.PARAMETER_THEN_HEADER);

        CocoWebRequestSecurityMetadata headerFirst = new DefaultCocoWebRequestSecurityMetadataResolver(
                headerFirstProperties, new CocoEncryptionProperties()).resolve(input);
        CocoWebRequestSecurityMetadata parameterFirst = new DefaultCocoWebRequestSecurityMetadataResolver(
                parameterFirstProperties, new CocoEncryptionProperties()).resolve(input);

        assertEquals("header-app", headerFirst.signatureAppId());
        assertEquals("header-nonce", headerFirst.signatureNonce());
        assertEquals("header-signature", headerFirst.signature());
        assertEquals("parameter-app", parameterFirst.signatureAppId());
        assertEquals("parameter-nonce", parameterFirst.signatureNonce());
        assertEquals("parameter-signature", parameterFirst.signature());
    }

    @Test
    void replayKeyResolverHonorsConfiguredMetadataSourcePriority() {
        CocoWebRequestSecurityInput input = new CocoWebRequestSecurityInput("POST", "/api/orders", null,
                Map.of(
                        "appId", List.of("parameter-app"),
                        "keyId", List.of("parameter-key"),
                        "timestamp", List.of("1700000000000"),
                        "nonce", List.of("parameter-nonce")),
                Map.of(
                        "x-coco-app-id", "header-app",
                        "x-coco-key-id", "header-key",
                        "x-coco-timestamp", "1700000000001",
                        "x-coco-nonce", "header-nonce"),
                Map.of(), null);
        CocoReplayProperties replayProperties = new CocoReplayProperties();
        replayProperties.setMetadataSource(CocoWebSecurityMetadataSource.PARAMETER_THEN_HEADER);
        CocoWebRequestSecurityMetadata metadata = new DefaultCocoWebRequestSecurityMetadataResolver(
                new CocoSignatureProperties(), new CocoEncryptionProperties(), replayProperties).resolve(input);
        CocoReplayKey replayKey = new DefaultCocoReplayKeyResolver(replayProperties).resolve(
                new CocoWebRequestSnapshot("replay-priority-trace", "POST", "/api/orders", null, "127.0.0.1", null,
                        null, null, null, null, null, Map.of(), Map.of(), input, CocoBrowserFingerprint.empty()),
                metadata);

        assertEquals("parameter-app", replayKey.appId());
        assertEquals("parameter-key", replayKey.keyId());
        assertEquals("1700000000000", replayKey.timestamp());
        assertEquals("parameter-nonce", replayKey.nonce());
        assertEquals("POST", replayKey.method());
        assertEquals("/api/orders", replayKey.path());
    }

    @Test
    void includesCustomSecurityHeaderNamesInSecurityInputAutomatically() {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.signature.app-id-header-name=X-Sign-App",
                        "coco.web.signature.key-id-header-name=X-Sign-Key",
                        "coco.web.signature.timestamp-header-name=X-Sign-Time",
                        "coco.web.signature.nonce-header-name=X-Sign-Nonce",
                        "coco.web.signature.signature-header-name=X-Signature",
                        "coco.web.signature.algorithm-header-name=X-Sign-Alg",
                        "coco.web.encryption.encrypted-header-name=X-Crypto-Enabled",
                        "coco.web.encryption.app-id-header-name=X-Crypto-App",
                        "coco.web.encryption.key-id-header-name=X-Crypto-Key",
                        "coco.web.encryption.iv-header-name=X-Crypto-IV",
                        "coco.web.encryption.algorithm-header-name=X-Crypto-Alg")
                .run(context -> {
                    CocoWebRequestContextResolver requestResolver =
                            context.getBean(CocoWebRequestContextResolver.class);
                    CocoWebRequestSecurityMetadataResolver metadataResolver =
                            context.getBean(CocoWebRequestSecurityMetadataResolver.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    request.addHeader("X-Sign-App", "sign-app");
                    request.addHeader("X-Sign-Key", "sign-key");
                    request.addHeader("X-Sign-Time", "1783300000000");
                    request.addHeader("X-Sign-Nonce", "nonce-1001");
                    request.addHeader("X-Signature", "signature-1001");
                    request.addHeader("X-Sign-Alg", "HMAC-SHA256");
                    request.addHeader("X-Crypto-Enabled", "1");
                    request.addHeader("X-Crypto-App", "crypto-app");
                    request.addHeader("X-Crypto-Key", "crypto-key");
                    request.addHeader("X-Crypto-IV", "iv-1001");
                    request.addHeader("X-Crypto-Alg", "AES-GCM");

                    CocoWebRequestSnapshot snapshot = requestResolver.resolve("custom-security-headers", request);
                    CocoWebRequestSecurityInput securityInput = snapshot.securityInput();
                    CocoWebRequestSecurityMetadata metadata = metadataResolver.resolve(securityInput);

                    assertEquals("signature-1001", securityInput.securityHeader("x-signature").orElseThrow());
                    assertEquals("1", securityInput.securityHeader("x-crypto-enabled").orElseThrow());
                    assertEquals("1783300000000", securityInput.canonicalHeader("x-sign-time").orElseThrow());
                    assertEquals("iv-1001", securityInput.canonicalHeader("x-crypto-iv").orElseThrow());
                    assertTrue(securityInput.canonicalHeader("x-signature").isEmpty());
                    assertTrue(securityInput.canonicalHeader("x-crypto-enabled").isEmpty());
                    assertEquals("sign-app", metadata.signatureAppId());
                    assertEquals("sign-key", metadata.signatureKeyId());
                    assertEquals("1783300000000", metadata.signatureTimestamp());
                    assertEquals("nonce-1001", metadata.signatureNonce());
                    assertEquals("signature-1001", metadata.signature());
                    assertTrue(metadata.signed());
                    assertEquals("crypto-app", metadata.encryptionAppId());
                    assertEquals("crypto-key", metadata.encryptionKeyId());
                    assertEquals("iv-1001", metadata.encryptionIv());
                    assertEquals("AES-GCM", metadata.encryptionAlgorithm());
                    assertTrue(metadata.encrypted());
                    assertEquals("sign-app", snapshot.securityMetadata().primaryAppId().orElseThrow());
                    assertEquals("sign-key", snapshot.securityMetadata().primaryKeyId().orElseThrow());
                    assertTrue(snapshot.securityMetadata().signed());
                    assertTrue(snapshot.securityMetadata().encrypted());
                });
    }

    @Test
    void includesCustomReplayHeaderNamesInSecurityInputAutomatically() {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.replay.app-id-header-name=X-Replay-App",
                        "coco.web.replay.key-id-header-name=X-Replay-Key",
                        "coco.web.replay.timestamp-header-name=X-Replay-Time",
                        "coco.web.replay.nonce-header-name=X-Replay-Nonce")
                .run(context -> {
                    CocoWebRequestContextResolver requestResolver =
                            context.getBean(CocoWebRequestContextResolver.class);
                    CocoWebRequestSecurityMetadataResolver metadataResolver =
                            context.getBean(CocoWebRequestSecurityMetadataResolver.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    request.addHeader("X-Replay-App", "replay-app");
                    request.addHeader("X-Replay-Key", "replay-key");
                    request.addHeader("X-Replay-Time", "1783300000000");
                    request.addHeader("X-Replay-Nonce", "nonce-replay-1001");

                    CocoWebRequestSnapshot snapshot = requestResolver.resolve("custom-replay-headers", request);
                    CocoWebRequestSecurityInput securityInput = snapshot.securityInput();
                    CocoWebRequestSecurityMetadata metadata = metadataResolver.resolve(securityInput);

                    assertEquals("replay-app", securityInput.securityHeader("x-replay-app").orElseThrow());
                    assertEquals("replay-key", securityInput.securityHeader("x-replay-key").orElseThrow());
                    assertEquals("1783300000000", securityInput.securityHeader("x-replay-time").orElseThrow());
                    assertEquals("nonce-replay-1001", securityInput.securityHeader("x-replay-nonce").orElseThrow());
                    assertEquals("replay-app", securityInput.canonicalHeader("x-replay-app").orElseThrow());
                    assertEquals("replay-key", securityInput.canonicalHeader("x-replay-key").orElseThrow());
                    assertEquals("1783300000000", securityInput.canonicalHeader("x-replay-time").orElseThrow());
                    assertEquals("nonce-replay-1001", securityInput.canonicalHeader("x-replay-nonce").orElseThrow());
                    assertEquals("replay-app", metadata.replayAppId());
                    assertEquals("replay-key", metadata.replayKeyId());
                    assertEquals("1783300000000", metadata.replayTimestamp());
                    assertEquals("nonce-replay-1001", metadata.replayNonce());
                    assertEquals("replay-app", metadata.primaryAppId().orElseThrow());
                    assertEquals("replay-key", metadata.primaryKeyId().orElseThrow());
                    assertTrue(metadata.replayProtected());
                    assertEquals("replay-app", snapshot.securityMetadata().primaryAppId().orElseThrow());
                    assertEquals("replay-key", snapshot.securityMetadata().primaryKeyId().orElseThrow());
                    assertTrue(snapshot.securityMetadata().replayProtected());
                });
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
                    CocoRequestContext requestContext = snapshot.toRequestContext();

                    assertEquals("custom-browser-fingerprint", snapshot.browserFingerprint().value());
                    assertEquals("custom-browser-fingerprint", requestContext.browserFingerprint().orElseThrow());
                    assertEquals("yes", snapshot.browserFingerprint().signals().get("custom"));
                    assertEquals("yes", requestContext.browserFingerprintSignal("custom").orElseThrow());
                    assertEquals(Map.of("custom", "yes"), requestContext.browserFingerprintSignals());
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
    void returnsUnifiedErrorResponseWhenRequestBodyExceedsMaxCacheBytes() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.request-body.mode=always",
                        "coco.web.request-body.max-cache-bytes=4")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    request.setContent("{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8));
                    request.addHeader("Accept-Language", "en-US");

                    bodyFilter.doFilter(request, response, new MockFilterChain());

                    assertEquals(413, response.getStatus());
                    Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
                    assertEquals(Boolean.FALSE, body.get("success"));
                    assertEquals(413, body.get("code"));
                    assertEquals("Request body exceeds the maximum allowed size.", body.get("message"));
                });
    }

    @Test
    void throwsCocoExceptionWhenRequestBodyExceedsMaxCacheBytesWithoutResponseWriter() throws Exception {
        CocoRequestBodyProperties properties = new CocoRequestBodyProperties();
        properties.setMode(CocoRequestBodyCachingMode.ALWAYS);
        properties.setMaxCacheBytes(4);
        CocoRequestBodyCachingFilter bodyFilter = new CocoRequestBodyCachingFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8));

        CocoPayloadTooLargeException exception = assertThrows(CocoPayloadTooLargeException.class,
                () -> bodyFilter.doFilter(request, response, new MockFilterChain()));

        assertEquals("coco.web.request-body.payload-too-large", exception.messageCode());
        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().isBlank());
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
    void verifiesSignedJsonRequestAgainstForwardedExternalTarget() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.signature.secrets.sample-app=sample-secret",
                        "coco.web.context.trusted-proxy-cidrs[0]=127.0.0.1/32")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = signedForwardedRequest(context, "forwarded-signature",
                            "sample-secret", String.valueOf(System.currentTimeMillis()), "nonce-forwarded-signature",
                            "/gateway");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<Boolean> reachedBusiness = new AtomicReference<>(false);

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse,
                                            new MockFilterChain(new TraceCapturingServlet(() -> {
                                                CocoRequestContext requestContext =
                                                        CocoRequestContextHolder.current().orElseThrow();
                                                assertEquals("https", requestContext.scheme().orElseThrow());
                                                assertEquals("api.coco.dev", requestContext.host().orElseThrow());
                                                assertEquals(8443, requestContext.port().orElseThrow());
                                                assertEquals("/gateway/api/orders", requestContext.path().orElseThrow());
                                                assertTrue(requestContext.signatureAppId().isEmpty());
                                                assertTrue(requestContext.signatureTimestamp().isEmpty());
                                                assertTrue(requestContext.signatureNonce().isEmpty());
                                                assertTrue(requestContext.signatureValue().isEmpty());
                                                reachedBusiness.set(true);
                                            })))));

                    assertEquals(200, response.getStatus(), response.getContentAsString());
                    assertEquals(Boolean.TRUE, reachedBusiness.get());
                });
    }

    @Test
    void requiresSignatureWhenRequestMatcherRequiresRequest() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.signature.matcher.required[0].methods[0]=POST",
                        "coco.web.signature.matcher.required[0].path-patterns[0]=/secure/**")
                .run(context -> {
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/secure/orders");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("Accept-Language", "en-US");

                    traceFilter.doFilter(request, response, (traceRequest, traceResponse) ->
                            signatureFilter.doFilter(traceRequest, traceResponse, new MockFilterChain()));

                    assertEquals(401, response.getStatus());
                    Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
                    assertEquals(Boolean.FALSE, body.get("success"));
                    assertEquals(401, body.get("code"));
                    assertEquals("Request signature is missing.", body.get("message"));
                });
    }

    @Test
    void cachesRequestBodyWhenSignatureMatcherRequiresRequest() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.request-body.mode=security-headers",
                        "coco.web.signature.matcher.required[0].methods[0]=POST",
                        "coco.web.signature.matcher.required[0].path-patterns[0]=/secure/**")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/secure/orders");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    byte[] body = "{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8);
                    AtomicReference<String> cachedSha256 = new AtomicReference<>();
                    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    request.setContent(body);

                    bodyFilter.doFilter(request, response, (wrappedRequest, wrappedResponse) ->
                            cachedSha256.set(CocoCachedBodyHttpServletRequest.cachedBody(
                                    (HttpServletRequest) wrappedRequest).orElseThrow().sha256()));

                    assertEquals(sha256(body), cachedSha256.get());
                });
    }

    @Test
    void ignoresSignatureWhenRequestMatcherIgnoresRequest() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.signature.required=true",
                        "coco.web.signature.matcher.ignored[0].path-patterns[0]=/public/**")
                .run(context -> {
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/public/ping");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<Boolean> reachedBusiness = new AtomicReference<>(false);

                    signatureFilter.doFilter(request, response,
                            new MockFilterChain(new TraceCapturingServlet(() -> reachedBusiness.set(true))));

                    assertEquals(200, response.getStatus());
                    assertEquals(Boolean.TRUE, reachedBusiness.get());
                });
    }

    @Test
    void skipsRequestBodyCacheWhenSignatureMatcherIgnoresGlobalRequiredRequest() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.request-body.mode=security-headers",
                        "coco.web.signature.required=true",
                        "coco.web.signature.matcher.ignored[0].path-patterns[0]=/public/**")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/public/orders");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<Boolean> cached = new AtomicReference<>(true);
                    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    request.setContent("{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8));

                    bodyFilter.doFilter(request, response, (wrappedRequest, wrappedResponse) ->
                            cached.set(CocoCachedBodyHttpServletRequest.cachedBody(
                                    (HttpServletRequest) wrappedRequest).isPresent()));

                    assertEquals(Boolean.FALSE, cached.get());
                });
    }

    @Test
    void requiresEncryptionWhenRequestMatcherRequiresRequest() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.encryption.matcher.required[0].path-patterns[0]=/secure/**")
                .run(context -> {
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoEncryptionFilter encryptionFilter = encryptionFilter(context.getBean(
                            "cocoEncryptionFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/secure/orders");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("Accept-Language", "en-US");

                    traceFilter.doFilter(request, response, (traceRequest, traceResponse) ->
                            encryptionFilter.doFilter(traceRequest, traceResponse, new MockFilterChain()));

                    assertEquals(401, response.getStatus());
                    Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
                    assertEquals(Boolean.FALSE, body.get("success"));
                    assertEquals(401, body.get("code"));
                    assertEquals("Request encryption flag is missing.", body.get("message"));
                });
    }

    @Test
    void cachesRequestBodyWhenEncryptionMatcherRequiresRequest() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.request-body.mode=security-headers",
                        "coco.web.encryption.matcher.required[0].methods[0]=POST",
                        "coco.web.encryption.matcher.required[0].path-patterns[0]=/secure/**")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/secure/orders");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    byte[] body = "encrypted-payload".getBytes(StandardCharsets.UTF_8);
                    AtomicReference<String> cachedSha256 = new AtomicReference<>();
                    request.setContentType("application/vnd.coco.raw");
                    request.setContent(body);

                    bodyFilter.doFilter(request, response, (wrappedRequest, wrappedResponse) ->
                            cachedSha256.set(CocoCachedBodyHttpServletRequest.cachedBody(
                                    (HttpServletRequest) wrappedRequest).orElseThrow().sha256()));

                    assertEquals(sha256(body), cachedSha256.get());
                });
    }

    @Test
    void requiresReplayWhenRequestMatcherRequiresRequest() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.replay.matcher.required[0].path-patterns[0]=/secure/**")
                .run(context -> {
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoReplayFilter replayFilter = replayFilter(context.getBean(
                            "cocoReplayFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/secure/orders");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("Accept-Language", "en-US");

                    traceFilter.doFilter(request, response, (traceRequest, traceResponse) ->
                            replayFilter.doFilter(traceRequest, traceResponse, new MockFilterChain()));

                    assertEquals(401, response.getStatus());
                    Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
                    assertEquals(Boolean.FALSE, body.get("success"));
                    assertEquals(401, body.get("code"));
                    assertEquals("Request replay app id is missing.", body.get("message"));
                });
    }

    @Test
    void cachesSignedRequestBodyWhenContentTypeIsNotIncluded() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.signature.secrets.sample-app=sample-secret")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = signedRequest(context, "signed-custom-type", "sample-secret",
                            String.valueOf(System.currentTimeMillis()), "nonce-custom-type",
                            "application/vnd.coco.raw");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<Boolean> reachedBusiness = new AtomicReference<>(false);

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse,
                                            new MockFilterChain(new TraceCapturingServlet(() -> {
                                                CocoRequestContext requestContext =
                                                        CocoRequestContextHolder.current().orElseThrow();
                                                assertEquals(sha256("{\"sku\":\"COCO-STARTER\"}"),
                                                        requestContext.requestBodySha256().orElseThrow());
                                                assertEquals("signature-verified",
                                                        requestContext.requestContextPhase().orElseThrow());
                                                assertEquals("unsupported-content-type",
                                                        requestContext.requestPayloadParseStatus().orElseThrow());
                                                reachedBusiness.set(true);
                                            })))));

                    assertEquals(200, response.getStatus());
                    assertEquals(Boolean.TRUE, reachedBusiness.get());
                });
    }

    @Test
    void rejectsSignedRequestWhenBodyHashIsMissing() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.signature.secrets.sample-app=sample-secret",
                        "coco.web.request-body.enabled=false")
                .run(context -> {
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = signedRequest(context, "missing-body-hash", "sample-secret");
                    request.addHeader("Content-Length", String.valueOf("{\"sku\":\"COCO-STARTER\"}"
                            .getBytes(StandardCharsets.UTF_8).length));
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    traceFilter.doFilter(request, response, (traceRequest, traceResponse) ->
                            signatureFilter.doFilter(traceRequest, traceResponse, new MockFilterChain()));

                    assertEquals(401, response.getStatus());
                    Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
                    assertEquals(Boolean.FALSE, body.get("success"));
                    assertEquals(401, body.get("code"));
                    assertEquals("Request signature body hash is missing.", body.get("message"));
                });
    }

    @Test
    void verifiesSignedRequestWithCustomHeaderNames() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.signature.secrets.sample-app=sample-secret",
                        "coco.web.signature.app-id-header-name=X-App",
                        "coco.web.signature.key-id-header-name=X-Key",
                        "coco.web.signature.timestamp-header-name=X-Time",
                        "coco.web.signature.nonce-header-name=X-Nonce",
                        "coco.web.signature.signature-header-name=X-Signature",
                        "coco.web.signature.algorithm-header-name=X-Algorithm")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    CocoWebRequestContextResolver requestResolver =
                            context.getBean(CocoWebRequestContextResolver.class);
                    CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/custom-signature");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<Boolean> reachedBusiness = new AtomicReference<>(false);
                    byte[] body = "{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8);
                    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    request.setContent(body);
                    request.addHeader("X-Trace-Id", "custom-signature");
                    request.addHeader("X-App", "sample-app");
                    request.addHeader("X-Key", "key-1001");
                    request.addHeader("X-Time", String.valueOf(System.currentTimeMillis()));
                    request.addHeader("X-Nonce", "nonce-custom-1001");
                    request.addHeader("X-Algorithm", "HMAC-SHA256");
                    CocoWebRequestSnapshot snapshot = requestResolver.resolve("custom-signature",
                            new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
                    String canonicalText = canonicalizer.canonicalize(CocoWebRequestCanonicalizationContext.of(
                            CocoWebRequestCanonicalizationPurpose.SIGNATURE, snapshot, null)).text();
                    request.addHeader("X-Signature", hmacSha256Hex(canonicalText, "sample-secret"));

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
    void verifiesSignedRequestWithQueryMetadataSource() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.signature.secrets.sample-app=sample-secret",
                        "coco.web.signature.metadata-source=parameter")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = querySignedRequest(context, "query-signature",
                            "sample-secret", String.valueOf(System.currentTimeMillis()), "nonce-query-1001");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<Boolean> reachedBusiness = new AtomicReference<>(false);

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse,
                                            new MockFilterChain(new TraceCapturingServlet(() -> {
                                                CocoRequestContext requestContext =
                                                        CocoRequestContextHolder.current().orElseThrow();
                                                assertTrue(requestContext.securityAppId().isEmpty());
                                                assertEquals(sha256("{\"sku\":\"COCO-STARTER\"}"),
                                                        requestContext.requestBodySha256().orElseThrow());
                                                assertEquals("signature-verified",
                                                        requestContext.requestContextPhase().orElseThrow());
                                                reachedBusiness.set(true);
                                            })))));

                    assertEquals(200, response.getStatus());
                    assertEquals(Boolean.TRUE, reachedBusiness.get());
                });
    }

    @Test
    void cachesRequestBodyWhenEncodedQueryParameterNameMatchesSecurityTrigger() throws Exception {
        CocoSignatureProperties signatureProperties = new CocoSignatureProperties();
        signatureProperties.setMetadataSource(CocoWebSecurityMetadataSource.PARAMETER);
        signatureProperties.setSignatureParameterName("sig.value");
        CocoRequestBodyCachingFilter bodyFilter = new CocoRequestBodyCachingFilter(
                new CocoRequestBodyProperties(), signatureProperties, null);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> cached = new AtomicReference<>(false);
        request.setContentType("application/x-coco-binary");
        request.setContent("{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8));
        request.setQueryString("sig%2Evalue=signature");

        bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                cached.set(CocoCachedBodyHttpServletRequest.cachedBody((HttpServletRequest) bodyRequest)
                        .isPresent()));

        assertEquals(Boolean.TRUE, cached.get());
    }

    @Test
    void verifiesSignedJsonPayloadWithParameterMetadataSourceWhenSignatureIsOptional() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.signature.secrets.sample-app=sample-secret",
                        "coco.web.signature.metadata-source=parameter")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = jsonPayloadSignedRequest(context, "json-payload-signature",
                            "sample-secret", String.valueOf(System.currentTimeMillis()), "nonce-json-payload-1001");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<Boolean> reachedBusiness = new AtomicReference<>(false);

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse,
                                            new MockFilterChain(new TraceCapturingServlet(() -> {
                                                CocoRequestContext requestContext =
                                                        CocoRequestContextHolder.current().orElseThrow();
                                                assertTrue(requestContext.signatureVerified());
                                                assertEquals("signature-verified",
                                                        requestContext.requestContextPhase().orElseThrow());
                                                assertTrue(requestContext.securityAppId().isEmpty());
                                                assertEquals("COCO-BODY",
                                                        requestContext.payloadParameter("sku").orElseThrow());
                                                reachedBusiness.set(true);
                                            })))));

                    assertEquals(200, response.getStatus(), response.getContentAsString());
                    assertEquals(Boolean.TRUE, reachedBusiness.get());
                });
    }

    @Test
    void verifiesSignedFormUrlencodedRequestWithParameterMetadataSource() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.signature.secrets.sample-app=sample-secret",
                        "coco.web.signature.metadata-source=parameter")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = formSignedRequest(context, "form-signature",
                            "sample-secret", String.valueOf(System.currentTimeMillis()), "nonce-form-1001");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<Boolean> reachedBusiness = new AtomicReference<>(false);

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse,
                                            new MockFilterChain(new TraceCapturingServlet(() -> {
                                                CocoRequestContext requestContext =
                                                        CocoRequestContextHolder.current().orElseThrow();
                                                assertTrue(requestContext.securityAppId().isEmpty());
                                                assertEquals("COCO-FORM", requestContext.parameter("sku").orElseThrow());
                                                assertEquals("COCO-FORM",
                                                        requestContext.payloadParameter("sku").orElseThrow());
                                                assertEquals("sample-app",
                                                        requestContext.payloadParameter("appId").orElseThrow());
                                                reachedBusiness.set(true);
                                            })))));

                    assertEquals(200, response.getStatus(), response.getContentAsString());
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
                                                                    {
                                                                        CocoRequestContext requestContext =
                                                                                CocoRequestContextHolder.current()
                                                                                        .orElseThrow();
                                                                        assertEquals("replay-verified",
                                                                                requestContext.requestContextPhase()
                                                                                        .orElseThrow());
                                                                        reachedBusiness.set(true);
                                                                    }))))));

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
    void distinguishesReplayKeysByForwardedExternalPath() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.signature.secrets.sample-app=sample-secret",
                        "coco.web.context.trusted-proxy-cidrs[0]=127.0.0.1/32")
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
                    String nonce = "nonce-forwarded-replay-1001";
                    AtomicInteger businessHits = new AtomicInteger();

                    MockHttpServletRequest firstRequest = signedForwardedRequest(context, "forwarded-replay-first",
                            "sample-secret", timestamp, nonce, "/gateway-a");
                    MockHttpServletResponse firstResponse = new MockHttpServletResponse();
                    bodyFilter.doFilter(firstRequest, firstResponse, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse,
                                            (signatureRequest, signatureResponse) ->
                                                    replayFilter.doFilter(signatureRequest, signatureResponse,
                                                            new MockFilterChain(new TraceCapturingServlet(() -> {
                                                                CocoRequestContext requestContext =
                                                                        CocoRequestContextHolder.current().orElseThrow();
                                                                assertEquals("/gateway-a/api/orders",
                                                                        requestContext.path().orElseThrow());
                                                                businessHits.incrementAndGet();
                                                            }))))));

                    MockHttpServletRequest secondRequest = signedForwardedRequest(context, "forwarded-replay-second",
                            "sample-secret", timestamp, nonce, "/gateway-b");
                    MockHttpServletResponse secondResponse = new MockHttpServletResponse();
                    bodyFilter.doFilter(secondRequest, secondResponse, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse,
                                            (signatureRequest, signatureResponse) ->
                                                    replayFilter.doFilter(signatureRequest, signatureResponse,
                                                            new MockFilterChain(new TraceCapturingServlet(() -> {
                                                                CocoRequestContext requestContext =
                                                                        CocoRequestContextHolder.current().orElseThrow();
                                                                assertEquals("/gateway-b/api/orders",
                                                                        requestContext.path().orElseThrow());
                                                                businessHits.incrementAndGet();
                                                            }))))));

                    assertEquals(200, firstResponse.getStatus(), firstResponse.getContentAsString());
                    assertEquals(200, secondResponse.getStatus(), secondResponse.getContentAsString());
                    assertEquals(2, businessHits.get());
                });
    }

    @Test
    void rejectsReplayedFormUrlencodedRequestWithParameterMetadataSource() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.replay.required=true",
                        "coco.web.replay.metadata-source=parameter")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoReplayFilter replayFilter = replayFilter(context.getBean(
                            "cocoReplayFilterRegistration", FilterRegistrationBean.class));
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String nonce = "nonce-form-replay-1001";
                    MockHttpServletRequest firstRequest = replayFormRequest("form-replay-first", timestamp, nonce);
                    MockHttpServletResponse firstResponse = new MockHttpServletResponse();
                    AtomicReference<Boolean> reachedBusiness = new AtomicReference<>(false);

                    bodyFilter.doFilter(firstRequest, firstResponse, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    replayFilter.doFilter(traceRequest, traceResponse,
                                            new MockFilterChain(new TraceCapturingServlet(() -> {
                                                CocoRequestContext requestContext =
                                                        CocoRequestContextHolder.current().orElseThrow();
                                                assertEquals(nonce, requestContext.payloadParameter("nonce").orElseThrow());
                                                assertEquals(timestamp,
                                                        requestContext.payloadParameter("timestamp").orElseThrow());
                                                assertEquals("replay-verified",
                                                        requestContext.requestContextPhase().orElseThrow());
                                                reachedBusiness.set(true);
                                            })))));

                    MockHttpServletRequest replayRequest = replayFormRequest("form-replay-second", timestamp, nonce);
                    replayRequest.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse replayResponse = new MockHttpServletResponse();

                    bodyFilter.doFilter(replayRequest, replayResponse, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    replayFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertEquals(200, firstResponse.getStatus(), firstResponse.getContentAsString());
                    assertEquals(Boolean.TRUE, reachedBusiness.get());
                    assertEquals(401, replayResponse.getStatus());
                    Map<?, ?> body = new ObjectMapper().readValue(replayResponse.getContentAsString(), Map.class);
                    assertEquals(Boolean.FALSE, body.get("success"));
                    assertEquals(401, body.get("code"));
                    assertEquals("Request replay has been detected.", body.get("message"));
                });
    }

    @Test
    void rejectsReplayedJsonPayloadRequestWithOptionalParameterMetadataSource() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.replay.metadata-source=parameter")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoReplayFilter replayFilter = replayFilter(context.getBean(
                            "cocoReplayFilterRegistration", FilterRegistrationBean.class));
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String nonce = "nonce-json-replay-1001";
                    MockHttpServletRequest firstRequest = replayJsonPayloadRequest("json-replay-first", timestamp,
                            nonce);
                    MockHttpServletResponse firstResponse = new MockHttpServletResponse();
                    AtomicReference<Boolean> reachedBusiness = new AtomicReference<>(false);

                    bodyFilter.doFilter(firstRequest, firstResponse, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    replayFilter.doFilter(traceRequest, traceResponse,
                                            new MockFilterChain(new TraceCapturingServlet(() -> {
                                                CocoRequestContext requestContext =
                                                        CocoRequestContextHolder.current().orElseThrow();
                                                assertEquals(nonce, requestContext.payloadParameter("nonce").orElseThrow());
                                                assertEquals(timestamp,
                                                        requestContext.payloadParameter("timestamp").orElseThrow());
                                                assertEquals("replay-verified",
                                                        requestContext.requestContextPhase().orElseThrow());
                                                reachedBusiness.set(true);
                                            })))));

                    MockHttpServletRequest replayRequest = replayJsonPayloadRequest("json-replay-second", timestamp,
                            nonce);
                    replayRequest.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse replayResponse = new MockHttpServletResponse();

                    bodyFilter.doFilter(replayRequest, replayResponse, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    replayFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertEquals(200, firstResponse.getStatus(), firstResponse.getContentAsString());
                    assertEquals(Boolean.TRUE, reachedBusiness.get());
                    assertEquals(401, replayResponse.getStatus());
                    Map<?, ?> body = new ObjectMapper().readValue(replayResponse.getContentAsString(), Map.class);
                    assertEquals(Boolean.FALSE, body.get("success"));
                    assertEquals(401, body.get("code"));
                    assertEquals("Request replay has been detected.", body.get("message"));
                });
    }

    @Test
    void rejectsReplayedEncryptedRequestWithCustomReplayHeaders() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.replay.protect-encrypted-requests=true",
                        "coco.web.replay.app-id-header-name=X-Replay-App",
                        "coco.web.replay.key-id-header-name=X-Replay-Key",
                        "coco.web.replay.timestamp-header-name=X-Replay-Time",
                        "coco.web.replay.nonce-header-name=X-Replay-Nonce",
                        "coco.web.encryption.encrypted-header-name=X-Crypto-Enabled")
                .run(context -> {
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoReplayFilter replayFilter = replayFilter(context.getBean(
                            "cocoReplayFilterRegistration", FilterRegistrationBean.class));
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    String nonce = "nonce-encrypted-replay-1001";
                    MockHttpServletRequest firstRequest = encryptedReplayRequest("encrypted-replay-first",
                            timestamp, nonce);
                    MockHttpServletResponse firstResponse = new MockHttpServletResponse();
                    AtomicReference<Boolean> reachedBusiness = new AtomicReference<>(false);

                    traceFilter.doFilter(firstRequest, firstResponse, (traceRequest, traceResponse) ->
                            replayFilter.doFilter(traceRequest, traceResponse,
                                    new MockFilterChain(new TraceCapturingServlet(() ->
                                            reachedBusiness.set(true)))));

                    MockHttpServletRequest replayRequest = encryptedReplayRequest("encrypted-replay-second",
                            timestamp, nonce);
                    replayRequest.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse replayResponse = new MockHttpServletResponse();

                    traceFilter.doFilter(replayRequest, replayResponse, (traceRequest, traceResponse) ->
                            replayFilter.doFilter(traceRequest, traceResponse, new MockFilterChain()));

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
    void returnsUnifiedErrorResponseWhenSignatureAppIdIsMissing() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.signature.secrets.sample-app=sample-secret")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = signedRequest(context, "missing-signature-app-id",
                            "sample-secret");
                    request.removeHeader("X-Coco-App-Id");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertEquals(401, response.getStatus());
                    Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
                    assertEquals(Boolean.FALSE, body.get("success"));
                    assertEquals(401, body.get("code"));
                    assertEquals("Request signature app id is missing.", body.get("message"));
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenSignatureAlgorithmIsMissing() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.signature.secrets.sample-app=sample-secret")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = signedRequest(context, "missing-signature-algorithm",
                            "sample-secret");
                    request.removeHeader("X-Coco-Sign-Algorithm");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertUnauthorizedResponse(response, "Request signature algorithm is missing.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenSignatureTimestampIsMissing() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.signature.secrets.sample-app=sample-secret")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = signedRequest(context, "missing-signature-timestamp",
                            "sample-secret");
                    request.removeHeader("X-Coco-Timestamp");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertUnauthorizedResponse(response, "Request signature timestamp is missing.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenSignatureTimestampIsInvalid() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.signature.secrets.sample-app=sample-secret")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = signedRequest(context, "invalid-signature-timestamp",
                            "sample-secret", "invalid-timestamp", "nonce-invalid-timestamp");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertUnauthorizedResponse(response, "Request signature timestamp is invalid.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenSignatureTimestampHasExpired() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.signature.secrets.sample-app=sample-secret")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = signedRequest(context, "expired-signature-timestamp",
                            "sample-secret", "1", "nonce-expired-timestamp");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertUnauthorizedResponse(response, "Request signature has expired.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenSignatureTimestampIsInFuture() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.signature.secrets.sample-app=sample-secret")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = signedRequest(context, "future-signature-timestamp",
                            "sample-secret", String.valueOf(System.currentTimeMillis() + 86_400_000L),
                            "nonce-future-timestamp");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertUnauthorizedResponse(response, "Request signature has expired.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenSignatureAlgorithmIsUnsupported() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.signature.secrets.sample-app=sample-secret")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = signedRequest(context, "unsupported-signature-algorithm",
                            "sample-secret");
                    request.removeHeader("X-Coco-Sign-Algorithm");
                    request.addHeader("X-Coco-Sign-Algorithm", "HMAC-SHA1");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    signatureFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertUnauthorizedResponse(response, "Request signature is invalid.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenSignatureSecretIsMissing() throws Exception {
        this.webContextRunner.run(context -> {
            CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                    context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
            CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                    FilterRegistrationBean.class));
            CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                    "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
            MockHttpServletRequest request = signedRequest(context, "missing-signature-secret", "sample-secret");
            request.addHeader("Accept-Language", "en-US");
            MockHttpServletResponse response = new MockHttpServletResponse();

            bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                    traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                            signatureFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

            assertUnauthorizedResponse(response, "Request signature secret is not configured.");
        });
    }

    @Test
    void returnsUnifiedErrorResponseWhenReplayNonceIsMissing() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.replay.required=true")
                .run(context -> {
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoReplayFilter replayFilter = replayFilter(context.getBean(
                            "cocoReplayFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    request.addHeader("X-Trace-Id", "missing-replay-nonce");
                    request.addHeader("Accept-Language", "en-US");
                    request.addHeader("X-Coco-App-Id", "sample-app");
                    request.addHeader("X-Coco-Key-Id", "key-1001");
                    request.addHeader("X-Coco-Timestamp", String.valueOf(System.currentTimeMillis()));
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    traceFilter.doFilter(request, response, (traceRequest, traceResponse) ->
                            replayFilter.doFilter(traceRequest, traceResponse, new MockFilterChain()));

                    assertEquals(401, response.getStatus());
                    Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
                    assertEquals(Boolean.FALSE, body.get("success"));
                    assertEquals(401, body.get("code"));
                    assertEquals("Request replay nonce is missing.", body.get("message"));
                });
    }

    @Test
    void preflightsReplayShapeBeforeSignatureVerification() throws Exception {
        AtomicInteger signatureVerifierCalls = new AtomicInteger();
        CocoSignatureVerifier verifier = context -> {
            signatureVerifierCalls.incrementAndGet();
            return true;
        };
        this.webContextRunner
                .withBean(CocoSignatureVerifier.class, () -> verifier)
                .run(context -> {
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoReplayRequestShapeFilter replayRequestShapeFilter = replayRequestShapeFilter(context.getBean(
                            "cocoReplayRequestShapeFilterRegistration", FilterRegistrationBean.class));
                    CocoSignatureFilter signatureFilter = signatureFilter(context.getBean(
                            "cocoSignatureFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    request.addHeader("X-Trace-Id", "preflight-missing-replay-nonce");
                    request.addHeader("Accept-Language", "en-US");
                    request.addHeader("X-Coco-App-Id", "sample-app");
                    request.addHeader("X-Coco-Timestamp", String.valueOf(System.currentTimeMillis()));
                    request.addHeader("X-Coco-Sign-Algorithm", "HMAC-SHA256");
                    request.addHeader("X-Coco-Sign", "dummy-signature");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    traceFilter.doFilter(request, response, (traceRequest, traceResponse) ->
                            replayRequestShapeFilter.doFilter(traceRequest, traceResponse,
                                    (shapeRequest, shapeResponse) ->
                                            signatureFilter.doFilter(shapeRequest, shapeResponse,
                                                    new MockFilterChain())));

                    assertUnauthorizedResponse(response, "Request replay nonce is missing.");
                    assertEquals(0, signatureVerifierCalls.get());
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenReplayTimestampIsMissing() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.replay.required=true")
                .run(context -> {
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoReplayFilter replayFilter = replayFilter(context.getBean(
                            "cocoReplayFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    request.addHeader("X-Trace-Id", "missing-replay-timestamp");
                    request.addHeader("Accept-Language", "en-US");
                    request.addHeader("X-Coco-App-Id", "sample-app");
                    request.addHeader("X-Coco-Key-Id", "key-1001");
                    request.addHeader("X-Coco-Nonce", "nonce-missing-timestamp");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    traceFilter.doFilter(request, response, (traceRequest, traceResponse) ->
                            replayFilter.doFilter(traceRequest, traceResponse, new MockFilterChain()));

                    assertUnauthorizedResponse(response, "Request replay timestamp is missing.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenReplayTimestampIsInvalid() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.replay.required=true")
                .run(context -> {
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoReplayFilter replayFilter = replayFilter(context.getBean(
                            "cocoReplayFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    request.addHeader("X-Trace-Id", "invalid-replay-timestamp");
                    request.addHeader("Accept-Language", "en-US");
                    request.addHeader("X-Coco-App-Id", "sample-app");
                    request.addHeader("X-Coco-Key-Id", "key-1001");
                    request.addHeader("X-Coco-Timestamp", "invalid-timestamp");
                    request.addHeader("X-Coco-Nonce", "nonce-invalid-timestamp");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    traceFilter.doFilter(request, response, (traceRequest, traceResponse) ->
                            replayFilter.doFilter(traceRequest, traceResponse, new MockFilterChain()));

                    assertUnauthorizedResponse(response, "Request replay timestamp is invalid.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenReplayWindowHasExpired() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.replay.required=true")
                .run(context -> {
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoReplayFilter replayFilter = replayFilter(context.getBean(
                            "cocoReplayFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    request.addHeader("X-Trace-Id", "expired-replay-window");
                    request.addHeader("Accept-Language", "en-US");
                    request.addHeader("X-Coco-App-Id", "sample-app");
                    request.addHeader("X-Coco-Key-Id", "key-1001");
                    request.addHeader("X-Coco-Timestamp", "1");
                    request.addHeader("X-Coco-Nonce", "nonce-expired-window");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    traceFilter.doFilter(request, response, (traceRequest, traceResponse) ->
                            replayFilter.doFilter(traceRequest, traceResponse, new MockFilterChain()));

                    assertUnauthorizedResponse(response, "Request replay window has expired.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenReplayTimestampIsInFuture() throws Exception {
        this.webContextRunner
                .withPropertyValues("coco.web.replay.required=true")
                .run(context -> {
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoReplayFilter replayFilter = replayFilter(context.getBean(
                            "cocoReplayFilterRegistration", FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
                    request.addHeader("X-Trace-Id", "future-replay-timestamp");
                    request.addHeader("Accept-Language", "en-US");
                    request.addHeader("X-Coco-App-Id", "sample-app");
                    request.addHeader("X-Coco-Key-Id", "key-1001");
                    request.addHeader("X-Coco-Timestamp", String.valueOf(System.currentTimeMillis() + 86_400_000L));
                    request.addHeader("X-Coco-Nonce", "nonce-future-window");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    traceFilter.doFilter(request, response, (traceRequest, traceResponse) ->
                            replayFilter.doFilter(traceRequest, traceResponse, new MockFilterChain()));

                    assertUnauthorizedResponse(response, "Request replay timestamp is invalid.");
                });
    }

    @Test
    void replayReservationExpiresFromServerReceiveTime() throws Exception {
        this.webContextRunner.run(context -> {
            Instant now = Instant.parse("2026-07-09T12:00:00Z");
            CocoReplayProperties properties = new CocoReplayProperties();
            properties.setRequired(true);
            properties.setTtlSeconds(120);
            properties.setMaxClockSkewSeconds(300);
            RecordingReplayStore replayStore = new RecordingReplayStore();
            CocoReplayFilter replayFilter = new CocoReplayFilter(properties, replayStore,
                    new DefaultCocoReplayKeyResolver(properties),
                    context.getBean(CocoWebRequestContextResolver.class),
                    context.getBean(CocoWebRequestSecurityMetadataResolver.class),
                    context.getBean(CocoFilterExceptionResponseWriter.class),
                    context.getBean(CocoWebRequestMatcher.class),
                    Clock.fixed(now, ZoneOffset.UTC));
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
            request.addHeader("X-Coco-App-Id", "sample-app");
            request.addHeader("X-Coco-Key-Id", "key-1001");
            request.addHeader("X-Coco-Timestamp", Long.toString(now.minusSeconds(60).toEpochMilli()));
            request.addHeader("X-Coco-Nonce", "nonce-server-expiry");
            MockHttpServletResponse response = new MockHttpServletResponse();

            replayFilter.doFilter(request, response, new MockFilterChain());

            assertEquals(200, response.getStatus());
            assertEquals(now.plusSeconds(120), replayStore.expiresAt.get());
        });
    }

    @Test
    void decryptsEncryptedJsonRequestBeforeBusinessServlet() throws Exception {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        CapturingAccessLogRecorder recorder = new CapturingAccessLogRecorder();
        this.webContextRunner
                .withPropertyValues("coco.web.context.canonicalization.version=coco-v2",
                        "coco.web.encryption.keys.sample-app="
                        + Base64.getEncoder().encodeToString(key))
                .withBean(CocoAccessLogRecorder.class, () -> recorder)
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoEncryptionFilter encryptionFilter = encryptionFilter(context.getBean(
                            "cocoEncryptionFilterRegistration", FilterRegistrationBean.class));
                    CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
                    byte[] plainBody = "{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8);
                    MockHttpServletRequest request = encryptedRequest(plainBody, key, "encrypted-body-trace");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<String> downstreamBody = new AtomicReference<>();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    encryptionFilter.doFilter(traceRequest, traceResponse,
                                            new MockFilterChain(new BodyReadingServlet(downstreamBody, () -> {
                                                CocoRequestContext requestContext =
                                                        CocoRequestContextHolder.current().orElseThrow();
                                                assertEquals(sha256(plainBody),
                                                        requestContext.requestBodySha256().orElseThrow());
                                                assertEquals("decrypted",
                                                        requestContext.requestBodyStage().orElseThrow());
                                                assertEquals(sha256(plainBody),
                                                        requestContext.requestBodyEffectiveSha256().orElseThrow());
                                                assertTrue(requestContext.requestBodyTransportSha256().isPresent());
                                                assertFalse(sha256(plainBody).equals(
                                                        requestContext.requestBodyTransportSha256().orElseThrow()));
                                                assertTrue(requestContext.securityAppId().isEmpty());
                                                assertTrue(requestContext.requestEncrypted());
                                                assertEquals("decrypted",
                                                        requestContext.requestContextPhase().orElseThrow());
                                                assertEquals("parsed",
                                                        requestContext.requestPayloadParseStatus().orElseThrow());
                                            })))));

                    assertEquals(200, response.getStatus());
                    assertEquals("{\"sku\":\"COCO-STARTER\"}", downstreamBody.get());
                    CocoWebRequestSnapshot effectiveSnapshot =
                            CocoWebRequestSnapshotAttributes.get(request).orElseThrow();
                    String transportSha256 = CocoCachedBodyHttpServletRequest.transportBody(request)
                            .orElseThrow()
                            .sha256();
                    assertEquals(sha256(plainBody), effectiveSnapshot.securityInput().bodySha256());
                    assertEquals("decrypted", effectiveSnapshot.requestBody().stage().id());
                    assertEquals(transportSha256, effectiveSnapshot.requestBody().transportSha256());
                    assertEquals(sha256(plainBody), effectiveSnapshot.requestBody().effectiveSha256());
                    assertEquals("sample-app", effectiveSnapshot.securityMetadata().primaryAppId().orElseThrow());
                    assertTrue(effectiveSnapshot.securityMetadata().encrypted());
                    assertEquals(List.of("COCO-STARTER"),
                            effectiveSnapshot.securityInput().parameter("sku").orElseThrow());
                    assertTrue(effectiveSnapshot.securityInput().queryParameters().isEmpty());
                    assertEquals(List.of("COCO-STARTER"),
                            effectiveSnapshot.securityInput().payloadParameter("sku").orElseThrow());
                    CocoWebRequestCanonicalForm canonicalForm =
                            canonicalizer.canonicalize(effectiveSnapshot.securityInput());
                    assertTrue(canonicalForm.text().contains("payloadParameters\n"));
                    assertTrue(canonicalForm.text().contains("sku[0]=12:COCO-STARTER\n"));
                    assertEquals("encrypted-body-trace", recorder.lastAccessLog().traceId());
                });
    }

    @Test
    void decryptsEncryptedJsonRequestAgainstForwardedExternalTarget() throws Exception {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.encryption.keys.sample-app=" + Base64.getEncoder().encodeToString(key),
                        "coco.web.context.trusted-proxy-cidrs[0]=127.0.0.1/32")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoEncryptionFilter encryptionFilter = encryptionFilter(context.getBean(
                            "cocoEncryptionFilterRegistration", FilterRegistrationBean.class));
                    byte[] plainBody = "{\"sku\":\"COCO-FORWARDED\"}".getBytes(StandardCharsets.UTF_8);
                    MockHttpServletRequest request = encryptedForwardedRequest(plainBody, key,
                            "encrypted-forwarded-trace", "/gateway");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<String> downstreamBody = new AtomicReference<>();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    encryptionFilter.doFilter(traceRequest, traceResponse,
                                            new MockFilterChain(new BodyReadingServlet(downstreamBody, () -> {
                                                CocoRequestContext requestContext =
                                                        CocoRequestContextHolder.current().orElseThrow();
                                                assertEquals("https", requestContext.scheme().orElseThrow());
                                                assertEquals("api.coco.dev", requestContext.host().orElseThrow());
                                                assertEquals(8443, requestContext.port().orElseThrow());
                                                assertEquals("/gateway/api/orders", requestContext.path().orElseThrow());
                                                assertEquals("decrypted",
                                                        requestContext.requestContextPhase().orElseThrow());
                                            })))));

                    assertEquals(200, response.getStatus(), response.getContentAsString());
                    assertEquals("{\"sku\":\"COCO-FORWARDED\"}", downstreamBody.get());
                    assertEquals("/gateway/api/orders",
                            CocoWebRequestSnapshotAttributes.get(request).orElseThrow().path());
                });
    }

    @Test
    void decryptsEncryptedJsonRequestWhenMetadataSourceIsParameter() throws Exception {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.encryption.keys.sample-app=" + Base64.getEncoder().encodeToString(key),
                        "coco.web.encryption.metadata-source=parameter")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoEncryptionFilter encryptionFilter = encryptionFilter(context.getBean(
                            "cocoEncryptionFilterRegistration", FilterRegistrationBean.class));
                    byte[] plainBody = "{\"sku\":\"COCO-PARAM\"}".getBytes(StandardCharsets.UTF_8);
                    MockHttpServletRequest request = encryptedParameterMetadataRequest(plainBody, key,
                            "encrypted-parameter-trace");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<String> downstreamBody = new AtomicReference<>();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) -> {
                        traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) -> {
                            encryptionFilter.doFilter(traceRequest, traceResponse,
                                    new MockFilterChain(new BodyReadingServlet(downstreamBody, () -> {
                                        CocoRequestContext requestContext =
                                                CocoRequestContextHolder.current().orElseThrow();
                                        assertTrue(requestContext.securityAppId().isEmpty());
                                        assertTrue(requestContext.requestEncrypted());
                                        assertEquals("decrypted",
                                                requestContext.requestContextPhase().orElseThrow());
                                    })));
                        });
                    });

                    assertEquals(200, response.getStatus());
                    assertEquals("{\"sku\":\"COCO-PARAM\"}", downstreamBody.get());
                });
    }

    @Test
    void decryptsEncryptedJsonEnvelopeWhenMetadataSourceIsParameter() throws Exception {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.encryption.keys.sample-app=" + Base64.getEncoder().encodeToString(key),
                        "coco.web.encryption.metadata-source=parameter")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoEncryptionFilter encryptionFilter = encryptionFilter(context.getBean(
                            "cocoEncryptionFilterRegistration", FilterRegistrationBean.class));
                    byte[] plainBody = "{\"sku\":\"COCO-ENVELOPE\"}".getBytes(StandardCharsets.UTF_8);
                    MockHttpServletRequest request = encryptedPayloadMetadataRequest(plainBody, key,
                            "encrypted-envelope-trace");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<String> downstreamBody = new AtomicReference<>();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    encryptionFilter.doFilter(traceRequest, traceResponse,
                                            new MockFilterChain(new BodyReadingServlet(downstreamBody, () -> {
                                                CocoRequestContext requestContext =
                                                        CocoRequestContextHolder.current().orElseThrow();
                                                assertTrue(requestContext.securityAppId().isEmpty());
                                                assertTrue(requestContext.requestEncrypted());
                                                assertEquals("decrypted",
                                                        requestContext.requestContextPhase().orElseThrow());
                                                assertEquals("COCO-ENVELOPE",
                                                        requestContext.payloadParameter("sku").orElseThrow());
                                            })))));

                    assertEquals(200, response.getStatus(), response.getContentAsString());
                    assertEquals("{\"sku\":\"COCO-ENVELOPE\"}", downstreamBody.get());
                });
    }

    @Test
    void decryptsEncryptedJsonRequestWithHexEncodings() throws Exception {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.encryption.keys.sample-app=" + HexFormat.of().formatHex(key),
                        "coco.web.encryption.key-encoding=hex",
                        "coco.web.encryption.iv-encoding=hex",
                        "coco.web.encryption.payload-encoding=hex")
                .run(context -> {
                    CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                            context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
                    CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    CocoEncryptionFilter encryptionFilter = encryptionFilter(context.getBean(
                            "cocoEncryptionFilterRegistration", FilterRegistrationBean.class));
                    byte[] plainBody = "{\"sku\":\"COCO-HEX\"}".getBytes(StandardCharsets.UTF_8);
                    MockHttpServletRequest request = hexEncodedEncryptedRequest(plainBody, key,
                            "encrypted-hex-trace");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    AtomicReference<String> downstreamBody = new AtomicReference<>();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) -> {
                        traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) -> {
                            encryptionFilter.doFilter(traceRequest, traceResponse,
                                    new MockFilterChain(new BodyReadingServlet(downstreamBody, () -> {
                                    })));
                        });
                    });

                    assertEquals(200, response.getStatus());
                    assertEquals("{\"sku\":\"COCO-HEX\"}", downstreamBody.get());
                });
    }

    @Test
    void encryptionAssociatedDataUsesStableProtocolFormat() {
        byte[] associatedData = CocoEncryptionAssociatedData.from("app-1001", "key-1001", "iv-1001",
                "AES-GCM", true, "POST", "/api/orders", "sku=COCO-STARTER", "1783300000000",
                "nonce-1001");

        String expectedText = "coco.web.encryption.v1\n"
                + "appId=8:app-1001\n"
                + "keyId=8:key-1001\n"
                + "iv=7:iv-1001\n"
                + "algorithm=7:AES-GCM\n"
                + "encrypted=4:true\n"
                + "method=4:POST\n"
                + "path=11:/api/orders\n"
                + "query=16:sku=COCO-STARTER\n"
                + "replayTimestamp=13:1783300000000\n"
                + "replayNonce=10:nonce-1001\n";
        assertEquals(expectedText, new String(associatedData, StandardCharsets.UTF_8));
    }

    @Test
    void encryptionAssociatedDataUsesSecurityRawQueryString() {
        CocoEncryptedRequest encryptedRequest = new CocoEncryptedRequest("app-1001", "key-1001", "iv-1001",
                "AES-GCM", true, new byte[0]);
        CocoWebRequestSecurityInput securityInput = new CocoWebRequestSecurityInput("POST", "/api/orders",
                "sku=COCO-STARTER&token=secret", Map.of("token", List.of("secret")),
                Map.of(), Map.of(), null);
        CocoWebRequestSnapshot snapshot = new CocoWebRequestSnapshot("trace-aad", "POST", "/api/orders",
                "sku=COCO-STARTER&token=******", "127.0.0.1", "JUnit", "zh-CN", "https",
                "api.example.test", 443, "application/json", Map.of(), Map.of(), securityInput,
                CocoWebRequestSecurityMetadata.empty(), CocoBrowserFingerprint.empty(),
                CocoClientIpResolution.remoteAddress("127.0.0.1"));

        String aad = new String(CocoEncryptionAssociatedData.from(encryptedRequest, snapshot,
                CocoWebRequestSecurityMetadata.empty()), StandardCharsets.UTF_8);

        assertTrue(aad.contains("query=29:sku=COCO-STARTER&token=secret\n"));
        assertFalse(aad.contains("token=******"));
    }

    @Test
    void returnsUnifiedErrorResponseWhenEncryptedAssociatedDataIsChanged() throws Exception {
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
                    MockHttpServletRequest request = encryptedRequest(plainBody, key, "encrypted-aad-tampered");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("Accept-Language", "en-US");
                    request.addHeader("X-Coco-Nonce", "nonce-after-encryption");

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
    void returnsBadRequestWhenEncryptedPayloadIsMalformed() throws Exception {
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

                    assertBadRequestResponse(response, "Request encryption data is malformed.");
                });
    }

    @Test
    void returnsBadRequestWhenEncryptedPayloadIsTooShortForGcmTag() throws Exception {
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
                    MockHttpServletRequest request = encryptedRequest("{}".getBytes(StandardCharsets.UTF_8), key,
                            "short-encrypted-payload");
                    request.setContent(Base64.getEncoder().encode(new byte[] { 1, 2, 3 }));
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    encryptionFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertBadRequestResponse(response, "Request encryption data is malformed.");
                });
    }

    @Test
    void returnsBadRequestWhenEncryptionIvIsMalformed() throws Exception {
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
                    MockHttpServletRequest request = encryptedRequest("{}".getBytes(StandardCharsets.UTF_8), key,
                            "malformed-encryption-iv");
                    request.removeHeader("X-Coco-IV");
                    request.addHeader("X-Coco-IV", "not-base64%");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    encryptionFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertBadRequestResponse(response, "Request encryption data is malformed.");
                });
    }

    @Test
    void returnsBadRequestWhenEncryptionAlgorithmIsUnsupported() throws Exception {
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
                    MockHttpServletRequest request = encryptedRequest("{}".getBytes(StandardCharsets.UTF_8), key,
                            "unsupported-encryption-algorithm");
                    request.removeHeader("X-Coco-Algorithm");
                    request.addHeader("X-Coco-Algorithm", "AES-CBC");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    encryptionFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertBadRequestResponse(response, "Request encryption data is malformed.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenEncryptionAppIdIsMissing() throws Exception {
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
                    MockHttpServletRequest request = encryptedRequest("{}".getBytes(StandardCharsets.UTF_8), key,
                            "missing-encryption-app-id");
                    request.removeHeader("X-Coco-App-Id");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    encryptionFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertUnauthorizedResponse(response, "Request encryption app id is missing.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenEncryptionIvIsMissing() throws Exception {
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
                    MockHttpServletRequest request = encryptedRequest("{}".getBytes(StandardCharsets.UTF_8), key,
                            "missing-encryption-iv");
                    request.removeHeader("X-Coco-IV");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    encryptionFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertUnauthorizedResponse(response, "Request encryption iv is missing.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenEncryptionAlgorithmIsMissing() throws Exception {
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
                    MockHttpServletRequest request = encryptedRequest("{}".getBytes(StandardCharsets.UTF_8), key,
                            "missing-encryption-algorithm");
                    request.removeHeader("X-Coco-Algorithm");
                    request.addHeader("Accept-Language", "en-US");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    encryptionFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertUnauthorizedResponse(response, "Request encryption algorithm is missing.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenEncryptionPayloadIsMissing() throws Exception {
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
                    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    request.setContent(new byte[0]);
                    request.addHeader("X-Trace-Id", "missing-encryption-payload");
                    request.addHeader("Accept-Language", "en-US");
                    request.addHeader("X-Coco-Encrypted", "true");
                    request.addHeader("X-Coco-App-Id", "sample-app");
                    request.addHeader("X-Coco-IV", Base64.getEncoder()
                            .encodeToString("123456789012".getBytes(StandardCharsets.UTF_8)));
                    request.addHeader("X-Coco-Algorithm", "AES-GCM");
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                            traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                                    encryptionFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

                    assertUnauthorizedResponse(response, "Request encryption payload is missing.");
                });
    }

    @Test
    void returnsUnifiedErrorResponseWhenEncryptionKeyIsMissing() throws Exception {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        this.webContextRunner.run(context -> {
            CocoRequestBodyCachingFilter bodyFilter = requestBodyCachingFilter(
                    context.getBean("cocoRequestBodyCachingFilterRegistration", FilterRegistrationBean.class));
            CocoTraceFilter traceFilter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                    FilterRegistrationBean.class));
            CocoEncryptionFilter encryptionFilter = encryptionFilter(context.getBean(
                    "cocoEncryptionFilterRegistration", FilterRegistrationBean.class));
            MockHttpServletRequest request = encryptedRequest("{}".getBytes(StandardCharsets.UTF_8), key,
                    "missing-encryption-key");
            request.addHeader("Accept-Language", "en-US");
            MockHttpServletResponse response = new MockHttpServletResponse();

            bodyFilter.doFilter(request, response, (bodyRequest, bodyResponse) ->
                    traceFilter.doFilter(bodyRequest, bodyResponse, (traceRequest, traceResponse) ->
                            encryptionFilter.doFilter(traceRequest, traceResponse, new MockFilterChain())));

            assertUnauthorizedResponse(response, "Request encryption key is not configured.");
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
                .withPropertyValues("coco.web.context.trusted-proxy-cidrs=127.0.0.1/32")
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
                    assertEquals("10.0.0.9", accessLog.clientIp().orElseThrow());
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
    void recordsAccessLogWithLatestRequestSnapshot() throws Exception {
        CapturingAccessLogRecorder recorder = new CapturingAccessLogRecorder();
        CocoTraceProperties traceProperties = new CocoTraceProperties();
        CocoAccessLogCaptureProperties accessLogProperties = new CocoAccessLogCaptureProperties();
        CocoWebRequestSnapshot initialSnapshot = new CocoWebRequestSnapshot("latest-snapshot-trace",
                "POST", "/initial", null, "127.0.0.1", "agent", "zh-CN",
                "http", "example.test", 8080, null, Map.of(), Map.of());
        CocoWebRequestSnapshot effectiveSnapshot = new CocoWebRequestSnapshot("latest-snapshot-trace",
                "POST", "/effective", "stage=decrypted", "127.0.0.2", "agent", "zh-CN",
                "http", "example.test", 8080, null, Map.of(), Map.of("stage", List.of("decrypted")));
        CocoWebRequestContextResolver resolver = (traceId, request) -> initialSnapshot;
        CocoTraceFilter filter = new CocoTraceFilter(traceProperties, List.of(recorder), accessLogProperties,
                resolver);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Trace-Id", "latest-snapshot-trace");

        filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() ->
                CocoWebRequestSnapshotAttributes.set(request, effectiveSnapshot))));

        CocoAccessLog accessLog = recorder.lastAccessLog();
        assertEquals("/effective", accessLog.path().orElseThrow());
        assertEquals("127.0.0.2", accessLog.clientIp().orElseThrow());
        assertEquals("stage=decrypted", accessLog.queryString().orElseThrow());
        assertEquals(List.of("decrypted"), accessLog.requestParameters().get("stage"));
    }

    @Test
    void contextParameterSwitchControlsAccessLogParameters() throws Exception {
        CapturingAccessLogRecorder recorder = new CapturingAccessLogRecorder();
        this.webContextRunner
                .withPropertyValues(
                        "coco.web.context.parameter.include-parameters=false",
                        "coco.web.context.parameter.max-parameter-value-length=4")
                .withBean(CocoAccessLogRecorder.class, () -> recorder)
                .run(context -> {
                    CocoTraceFilter filter = traceFilter(context.getBean("cocoTraceFilterRegistration",
                            FilterRegistrationBean.class));
                    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/users");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    request.addHeader("X-Trace-Id", "access-no-params");
                    request.setQueryString("name=Coconut&token=abc");
                    request.addParameter("name", "Coconut");
                    request.addParameter("token", "abc");

                    filter.doFilter(request, response, new MockFilterChain(new TraceCapturingServlet(() -> {
                        CocoRequestContext requestContext = CocoRequestContextHolder.current().orElseThrow();
                        assertTrue(requestContext.parameter("name").isEmpty());
                        assertTrue(requestContext.parameter("token").isEmpty());
                    })));

                    CocoAccessLog accessLog = recorder.lastAccessLog();
                    assertTrue(accessLog.queryString().isEmpty());
                    assertTrue(accessLog.requestParameters().isEmpty());
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

    private static CocoReplayRequestShapeFilter replayRequestShapeFilter(FilterRegistrationBean<?> registrationBean) {
        return (CocoReplayRequestShapeFilter) registrationBean.getFilter();
    }

    private static CocoEncryptionFilter encryptionFilter(FilterRegistrationBean<?> registrationBean) {
        return (CocoEncryptionFilter) registrationBean.getFilter();
    }

    private static CocoWebRequestCanonicalizationProperties parameterOnlyCanonicalizationProperties(String version) {
        CocoWebRequestCanonicalizationProperties properties = new CocoWebRequestCanonicalizationProperties();
        properties.setVersion(version);
        properties.setIncludeMethod(false);
        properties.setIncludePath(false);
        properties.setIncludeQueryString(false);
        properties.setIncludeHeaders(false);
        properties.setIncludeBodySha256(false);
        properties.setIncludeBodyLength(false);
        return properties;
    }

    private static MockHttpServletRequest signedRequest(org.springframework.context.ApplicationContext context,
            String traceId, String secret) {
        return signedRequest(context, traceId, secret, String.valueOf(System.currentTimeMillis()), "nonce-1001");
    }

    private static MockHttpServletRequest signedRequest(org.springframework.context.ApplicationContext context,
            String traceId, String secret, String timestamp, String nonce) {
        return signedRequest(context, traceId, secret, timestamp, nonce, MediaType.APPLICATION_JSON_VALUE);
    }

    private static MockHttpServletRequest signedRequest(org.springframework.context.ApplicationContext context,
            String traceId, String secret, String timestamp, String nonce, String contentType) {
        CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
        CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
        byte[] body = "{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(contentType);
        request.setContent(body);
        request.addHeader("X-Trace-Id", traceId);
        request.addHeader("X-Coco-App-Id", "sample-app");
        request.addHeader("X-Coco-Timestamp", timestamp);
        request.addHeader("X-Coco-Nonce", nonce);
        request.addHeader("X-Coco-Sign-Algorithm", "HMAC-SHA256");
        CocoWebRequestSnapshot snapshot = resolver.resolve(traceId,
                new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
        String canonicalText = canonicalizer.canonicalize(CocoWebRequestCanonicalizationContext.of(
                CocoWebRequestCanonicalizationPurpose.SIGNATURE, snapshot, null)).text();
        CocoWebRequestSnapshotAttributes.clear(request);
        CocoCachedBodyHttpServletRequest.clear(request);
        request.addHeader("X-Coco-Sign", hmacSha256Hex(canonicalText, secret));
        return request;
    }

    private static MockHttpServletRequest querySignedRequest(org.springframework.context.ApplicationContext context,
            String traceId, String secret, String timestamp, String nonce) {
        CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
        CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
        byte[] body = "{\"sku\":\"COCO-STARTER\"}".getBytes(StandardCharsets.UTF_8);
        String unsignedQuery = "appId=sample-app&timestamp=" + timestamp + "&nonce=" + nonce
                + "&signAlgorithm=HMAC-SHA256";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body);
        request.setQueryString(unsignedQuery);
        request.addParameter("appId", "sample-app");
        request.addParameter("timestamp", timestamp);
        request.addParameter("nonce", nonce);
        request.addParameter("signAlgorithm", "HMAC-SHA256");
        request.addHeader("X-Trace-Id", traceId);
        CocoWebRequestSnapshot snapshot = resolver.resolve(traceId,
                new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
        String canonicalText = canonicalizer.canonicalize(CocoWebRequestCanonicalizationContext.of(
                CocoWebRequestCanonicalizationPurpose.SIGNATURE, snapshot, null)).text();
        String signature = hmacSha256Hex(canonicalText, secret);
        CocoWebRequestSnapshotAttributes.clear(request);
        CocoCachedBodyHttpServletRequest.clear(request);
        request.setQueryString(unsignedQuery + "&sign=" + signature);
        request.addParameter("sign", signature);
        return request;
    }

    private static MockHttpServletRequest jsonPayloadSignedRequest(org.springframework.context.ApplicationContext context,
            String traceId, String secret, String timestamp, String nonce) {
        CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
        CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
        String basePayload = "{\"sku\":\"COCO-BODY\",\"appId\":\"sample-app\",\"timestamp\":\"" + timestamp
                + "\",\"nonce\":\"" + nonce + "\",\"signAlgorithm\":\"HMAC-SHA256\"}";
        byte[] baseBody = basePayload.getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(baseBody);
        request.addHeader("X-Trace-Id", traceId);
        CocoWebRequestSnapshot snapshot = resolver.resolve(traceId,
                new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(baseBody)));
        CocoWebRequestSecurityInput canonicalInput = snapshot.securityInput().withoutBodyMetadata();
        String canonicalText = canonicalizer.canonicalize(new CocoWebRequestCanonicalizationContext(
                CocoWebRequestCanonicalizationPurpose.SIGNATURE, canonicalInput, null,
                snapshot.browserFingerprint())).text();
        String signature = hmacSha256Hex(canonicalText, secret);
        CocoWebRequestSnapshotAttributes.clear(request);
        CocoCachedBodyHttpServletRequest.clear(request);
        String signedPayload = basePayload.substring(0, basePayload.length() - 1)
                + ",\"sign\":\"" + signature + "\"}";
        request.setContent(signedPayload.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static MockHttpServletRequest formSignedRequest(org.springframework.context.ApplicationContext context,
            String traceId, String secret, String timestamp, String nonce) {
        CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
        CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
        String basePayload = "sku=COCO-FORM&appId=sample-app&timestamp=" + timestamp
                + "&nonce=" + nonce + "&signAlgorithm=HMAC-SHA256";
        byte[] baseBody = basePayload.getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        request.setContent(baseBody);
        request.addHeader("X-Trace-Id", traceId);
        request.addParameter("sku", "COCO-FORM");
        request.addParameter("appId", "sample-app");
        request.addParameter("timestamp", timestamp);
        request.addParameter("nonce", nonce);
        request.addParameter("signAlgorithm", "HMAC-SHA256");
        CocoWebRequestSnapshot snapshot = resolver.resolve(traceId,
                new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(baseBody)));
        CocoWebRequestSecurityInput canonicalInput = snapshot.securityInput().withoutBodyMetadata();
        String canonicalText = canonicalizer.canonicalize(new CocoWebRequestCanonicalizationContext(
                CocoWebRequestCanonicalizationPurpose.SIGNATURE, canonicalInput, null,
                snapshot.browserFingerprint())).text();
        String signature = hmacSha256Hex(canonicalText, secret);
        CocoWebRequestSnapshotAttributes.clear(request);
        CocoCachedBodyHttpServletRequest.clear(request);
        request.setContent((basePayload + "&sign=" + signature).getBytes(StandardCharsets.UTF_8));
        request.addParameter("sign", signature);
        return request;
    }

    private static MockHttpServletRequest replayFormRequest(String traceId, String timestamp, String nonce) {
        String payload = "sku=COCO-FORM&appId=sample-app&keyId=key-1001&timestamp=" + timestamp
                + "&nonce=" + nonce;
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE);
        request.setContent(payload.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Trace-Id", traceId);
        request.addParameter("sku", "COCO-FORM");
        request.addParameter("appId", "sample-app");
        request.addParameter("keyId", "key-1001");
        request.addParameter("timestamp", timestamp);
        request.addParameter("nonce", nonce);
        return request;
    }

    private static MockHttpServletRequest replayJsonPayloadRequest(String traceId, String timestamp, String nonce) {
        String payload = "{\"sku\":\"COCO-JSON\",\"appId\":\"sample-app\",\"keyId\":\"key-1001\","
                + "\"timestamp\":\"" + timestamp + "\",\"nonce\":\"" + nonce + "\"}";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(payload.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Trace-Id", traceId);
        return request;
    }

    private static MockHttpServletRequest signedForwardedRequest(org.springframework.context.ApplicationContext context,
            String traceId, String secret, String timestamp, String nonce, String forwardedPrefix) {
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
        applyForwardedTarget(request, forwardedPrefix);
        CocoWebRequestSnapshot snapshot = resolver.resolve(traceId,
                new CocoCachedBodyHttpServletRequest(request, CocoCachedRequestBody.cached(body)));
        String canonicalText = canonicalizer.canonicalize(CocoWebRequestCanonicalizationContext.of(
                CocoWebRequestCanonicalizationPurpose.SIGNATURE, snapshot, null)).text();
        CocoWebRequestSnapshotAttributes.clear(request);
        CocoCachedBodyHttpServletRequest.clear(request);
        request.addHeader("X-Coco-Sign", hmacSha256Hex(canonicalText, secret));
        return request;
    }

    private static MockHttpServletRequest encryptedRequest(byte[] plainBody, byte[] key, String traceId) {
        byte[] iv = "123456789012".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String encodedIv = Base64.getEncoder().encodeToString(iv);
        byte[] aad = CocoEncryptionAssociatedData.from("sample-app", null, encodedIv, "AES-GCM", true,
                "POST", "/api/orders", null, null, null);
        request.setContent(Base64.getEncoder().encode(aesGcmEncrypt(plainBody, key, iv, aad)));
        request.addHeader("X-Trace-Id", traceId);
        request.addHeader("X-Coco-Encrypted", "true");
        request.addHeader("X-Coco-App-Id", "sample-app");
        request.addHeader("X-Coco-IV", encodedIv);
        request.addHeader("X-Coco-Algorithm", "AES-GCM");
        return request;
    }

    private static MockHttpServletRequest encryptedForwardedRequest(byte[] plainBody, byte[] key, String traceId,
            String forwardedPrefix) {
        byte[] iv = "123456789012".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        applyForwardedTarget(request, forwardedPrefix);
        String encodedIv = Base64.getEncoder().encodeToString(iv);
        byte[] aad = CocoEncryptionAssociatedData.from("sample-app", null, encodedIv, "AES-GCM", true,
                "POST", forwardedPrefix + "/api/orders", null, null, null);
        request.setContent(Base64.getEncoder().encode(aesGcmEncrypt(plainBody, key, iv, aad)));
        request.addHeader("X-Trace-Id", traceId);
        request.addHeader("X-Coco-Encrypted", "true");
        request.addHeader("X-Coco-App-Id", "sample-app");
        request.addHeader("X-Coco-IV", encodedIv);
        request.addHeader("X-Coco-Algorithm", "AES-GCM");
        return request;
    }

    private static MockHttpServletRequest encryptedParameterMetadataRequest(byte[] plainBody, byte[] key,
            String traceId) {
        byte[] iv = "123456789012".getBytes(StandardCharsets.UTF_8);
        String encodedIv = Base64.getEncoder().encodeToString(iv);
        String queryString = "encrypted=true&appId=sample-app&iv=" + encodedIv + "&algorithm=AES-GCM";
        byte[] aad = CocoEncryptionAssociatedData.from("sample-app", null, encodedIv, "AES-GCM", true,
                "POST", "/api/orders", queryString, null, null);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(Base64.getEncoder().encode(aesGcmEncrypt(plainBody, key, iv, aad)));
        request.setQueryString(queryString);
        request.addParameter("encrypted", "true");
        request.addParameter("appId", "sample-app");
        request.addParameter("iv", encodedIv);
        request.addParameter("algorithm", "AES-GCM");
        request.addHeader("X-Trace-Id", traceId);
        return request;
    }

    private static MockHttpServletRequest encryptedPayloadMetadataRequest(byte[] plainBody, byte[] key,
            String traceId) {
        byte[] iv = "123456789012".getBytes(StandardCharsets.UTF_8);
        String encodedIv = Base64.getEncoder().encodeToString(iv);
        byte[] aad = CocoEncryptionAssociatedData.from("sample-app", null, encodedIv, "AES-GCM", true,
                "POST", "/api/orders", null, null, null);
        String encodedPayload = Base64.getEncoder().encodeToString(aesGcmEncrypt(plainBody, key, iv, aad));
        String envelope = "{\"encrypted\":true,\"appId\":\"sample-app\",\"iv\":\"" + encodedIv
                + "\",\"algorithm\":\"AES-GCM\",\"payload\":\"" + encodedPayload + "\"}";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(envelope.getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Trace-Id", traceId);
        return request;
    }

    private static MockHttpServletRequest hexEncodedEncryptedRequest(byte[] plainBody, byte[] key, String traceId) {
        byte[] iv = "123456789012".getBytes(StandardCharsets.UTF_8);
        String encodedIv = HexFormat.of().formatHex(iv);
        byte[] aad = CocoEncryptionAssociatedData.from("sample-app", null, encodedIv, "AES-GCM", true,
                "POST", "/api/orders", null, null, null);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(HexFormat.of().formatHex(aesGcmEncrypt(plainBody, key, iv, aad))
                .getBytes(StandardCharsets.UTF_8));
        request.addHeader("X-Trace-Id", traceId);
        request.addHeader("X-Coco-Encrypted", "true");
        request.addHeader("X-Coco-App-Id", "sample-app");
        request.addHeader("X-Coco-IV", encodedIv);
        request.addHeader("X-Coco-Algorithm", "AES-GCM");
        return request;
    }

    private static MockHttpServletRequest encryptedReplayRequest(String traceId, String timestamp, String nonce) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Trace-Id", traceId);
        request.addHeader("X-Crypto-Enabled", "true");
        request.addHeader("X-Replay-App", "sample-app");
        request.addHeader("X-Replay-Key", "key-1001");
        request.addHeader("X-Replay-Time", timestamp);
        request.addHeader("X-Replay-Nonce", nonce);
        return request;
    }

    private static void applyForwardedTarget(MockHttpServletRequest request, String forwardedPrefix) {
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("Forwarded", "for=203.0.113.10;proto=https;host=\"api.coco.dev:8443\"");
        if (forwardedPrefix != null) {
            request.addHeader("X-Forwarded-Prefix", forwardedPrefix);
        }
    }

    private static byte[] aesGcmEncrypt(byte[] plainBody, byte[] key, byte[] iv, byte[] aad) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(aad);
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

    private static void assertUnauthorizedResponse(MockHttpServletResponse response, String message) throws Exception {
        assertEquals(401, response.getStatus());
        Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
        assertEquals(Boolean.FALSE, body.get("success"));
        assertEquals(401, body.get("code"));
        assertEquals(message, body.get("message"));
    }

    private static void assertBadRequestResponse(MockHttpServletResponse response, String message) throws Exception {
        assertEquals(400, response.getStatus());
        Map<?, ?> body = new ObjectMapper().readValue(response.getContentAsString(), Map.class);
        assertEquals(Boolean.FALSE, body.get("success"));
        assertEquals(400, body.get("code"));
        assertEquals(message, body.get("message"));
    }

    private static final class RecordingReplayStore implements CocoReplayStore {

        private final AtomicReference<Instant> expiresAt = new AtomicReference<>();

        @Override
        public boolean reserve(CocoReplayKey key, Instant expiresAt) {
            this.expiresAt.set(expiresAt);
            return true;
        }
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

    private static CocoLogManager cocoLogManager(CapturingCocoLogSink sink) {
        CocoLogHandleRegistry registry = new CocoLogHandleRegistry();
        CocoLogHandles.registerDefaults(registry);
        return new CocoLogManager(registry, sink);
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

    private static final class CapturingCocoLogSink implements CocoLogSink {

        private final List<CocoLogRecord> records = new ArrayList<>();

        @Override
        public void log(CocoLogRecord record) {
            this.records.add(record);
        }

        private List<CocoLogRecord> records() {
            return this.records;
        }
    }

    private static final class ThrowingAccessLogRecorder implements CocoAccessLogRecorder {

        @Override
        public void record(CocoAccessLog accessLog) {
            throw new IllegalStateException("recorder failed");
        }
    }
}
