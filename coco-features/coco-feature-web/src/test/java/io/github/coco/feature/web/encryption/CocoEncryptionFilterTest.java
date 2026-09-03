package io.github.coco.feature.web.encryption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
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
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.response.CocoSystemCodes;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link CocoEncryptionFilter} 单元测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class CocoEncryptionFilterTest {

    @AfterEach
    void clearContext() {
        CocoRequestContextHolder.clear();
        CocoTraceContext.clear();
    }

    @Test
    void disabledFilterPassesThroughWithoutModification() throws Exception {
        CocoEncryptionProperties properties = new CocoEncryptionProperties();
        properties.setEnabled(false);
        CocoEncryptionFilter filter = new CocoEncryptionFilter(properties,
                request -> Optional.of(new CocoEncryptionKey("app", null, new byte[16])),
                context -> new byte[0],
                (traceId, request) -> baseSnapshot(traceId),
                exceptionWriter(),
                input -> CocoWebRequestSecurityMetadata.empty(),
                (request, rules) -> false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> chainCalls.incrementAndGet());

        assertEquals(1, chainCalls.get());
    }

    @Test
    void nonEncryptedRequestPassesThroughWhenNotRequired() throws Exception {
        CocoEncryptionProperties properties = new CocoEncryptionProperties();
        properties.setRequired(false);
        CocoEncryptionFilter filter = new CocoEncryptionFilter(properties,
                request -> Optional.of(new CocoEncryptionKey("app", null, new byte[16])),
                context -> new byte[0],
                (traceId, request) -> baseSnapshot(traceId),
                exceptionWriter(),
                input -> CocoWebRequestSecurityMetadata.empty(),
                (request, rules) -> false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> chainCalls.incrementAndGet());

        assertEquals(1, chainCalls.get());
    }

    @Test
    void encryptedRequestDecryptsAndSetsContext() throws Exception {
        CocoEncryptionProperties properties = new CocoEncryptionProperties();
        byte[] encryptedPayload = "cipher".getBytes(StandardCharsets.UTF_8);
        CocoWebRequestSecurityMetadata metadata = new CocoWebRequestSecurityMetadata(
                null, null, null, null, null, null, false,
                "app-1", "key-1", "iv-1", "AES-GCM", true, null, null, null, null);
        AtomicInteger resolveCalls = new AtomicInteger();
        CocoWebRequestSnapshot transportSnapshot = baseSnapshot("trace-1")
                .withContextAttribute(CocoRequestContextAttributes.SIGNATURE_VERIFIED, "true");
        CocoWebRequestSnapshot effectiveSnapshot = baseSnapshot("trace-1");
        CocoWebRequestContextResolver resolver = (traceId, request) ->
                resolveCalls.getAndIncrement() == 0 ? transportSnapshot : effectiveSnapshot;
        CocoEncryptionFilter filter = new CocoEncryptionFilter(properties,
                request -> Optional.of(new CocoEncryptionKey(request.appId(), request.keyId(),
                        "1234567890123456".getBytes(StandardCharsets.UTF_8))),
                context -> "plain".getBytes(StandardCharsets.UTF_8),
                resolver,
                exceptionWriter(),
                input -> metadata,
                (request, rules) -> false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContent(encryptedPayload);
        request.addHeader(properties.getEncryptedHeaderName(), "true");
        AtomicReference<CocoRequestContext> context = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> context.set(CocoRequestContextHolder.current().orElseThrow()));

        CocoRequestContext requestContext = context.get();
        assertNotNull(requestContext);
        assertTrue(requestContext.requestDecrypted());
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
