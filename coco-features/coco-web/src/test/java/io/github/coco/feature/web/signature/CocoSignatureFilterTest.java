package io.github.coco.feature.web.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.context.CocoRequestContext;
import io.github.coco.context.CocoRequestContextAttributes;
import io.github.coco.context.CocoRequestContextHolder;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalForm;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.request.metadata.CocoWebSecurityMetadataSource;
import io.github.coco.feature.web.response.CocoSystemCodes;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link CocoSignatureFilter} 单元测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class CocoSignatureFilterTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @AfterEach
    void clearContext() {
        CocoRequestContextHolder.clear();
        CocoTraceContext.clear();
    }

    @Test
    void disabledFilterPassesThrough() throws Exception {
        CocoSignatureProperties properties = new CocoSignatureProperties();
        properties.setEnabled(false);
        CocoSignatureFilter filter = new CocoSignatureFilter(properties,
                request -> Optional.of(new CocoSignatureSecret("app", null, "secret")),
                context -> true,
                (traceId, request) -> baseSnapshot(traceId),
                context -> new CocoWebRequestCanonicalForm("canonical", "sha"),
                exceptionWriter(),
                null, null, FIXED_CLOCK);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> chainCalls.incrementAndGet());

        assertEquals(1, chainCalls.get());
    }

    @Test
    void noSignatureHeaderPassesThroughWhenNotRequired() throws Exception {
        CocoSignatureProperties properties = new CocoSignatureProperties();
        properties.setRequired(false);
        CocoSignatureFilter filter = new CocoSignatureFilter(properties,
                request -> Optional.of(new CocoSignatureSecret("app", null, "secret")),
                context -> true,
                (traceId, request) -> baseSnapshot(traceId),
                context -> new CocoWebRequestCanonicalForm("canonical", "sha"),
                exceptionWriter(),
                null, null, FIXED_CLOCK);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> chainCalls.incrementAndGet());

        assertEquals(1, chainCalls.get());
    }

    @Test
    void validSignatureWritesEvidenceToContext() throws Exception {
        CocoSignatureProperties properties = new CocoSignatureProperties();
        properties.setTimestampValidationEnabled(false);
        CocoWebRequestSecurityMetadata metadata = new CocoWebRequestSecurityMetadata("app-1", "key-1",
                FIXED_NOW.toString(), "nonce-1", "HMAC-SHA256", "signature-value", true,
                null, null, null, null, false, null, null, null, null);
        CocoSignatureFilter filter = new CocoSignatureFilter(properties,
                request -> Optional.of(new CocoSignatureSecret(request.appId(), request.keyId(), "secret")),
                context -> true,
                (traceId, request) -> baseSnapshot(traceId),
                context -> new CocoWebRequestCanonicalForm("canonical", "canonical-sha"),
                exceptionWriter(),
                input -> metadata,
                (request, rules) -> false,
                FIXED_CLOCK);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader(properties.getSignatureHeaderName(), "signature-value");
        AtomicReference<CocoRequestContext> context = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> context.set(CocoRequestContextHolder.current().orElseThrow()));

        CocoRequestContext requestContext = context.get();
        assertTrue(requestContext.signatureVerified());
        assertEquals(Optional.of(FIXED_NOW.toString()), requestContext.signatureVerifiedAt());
        assertEquals(Optional.of("canonical-sha"), requestContext.signatureCanonicalSha256());
        assertEquals(Optional.of(CocoWebSecurityMetadataSource.HEADER.name()),
                requestContext.signatureMetadataSource());
    }

    private static CocoWebRequestSnapshot baseSnapshot(String traceId) {
        return new CocoWebRequestSnapshot(traceId, "POST", "/api/orders", null, "127.0.0.1",
                null, null, "http", "localhost", 80, null, Map.of(), Map.of())
                .withContextAttribute(CocoRequestContextAttributes.CLIENT_IP, "127.0.0.1");
    }

    private static CocoFilterExceptionResponseWriter exceptionWriter() {
        CocoExceptionHttpStatusResolver statusResolver = exception -> HttpStatus.UNAUTHORIZED;
        return new CocoFilterExceptionResponseWriter(new CocoWebExceptionHandler(new StaticMessageService(),
                statusResolver, CocoSystemCodes.defaults()), new ObjectMapper());
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
            return defaultMessage == null ? code : defaultMessage;
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) {
            return defaultMessage == null ? code : defaultMessage;
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