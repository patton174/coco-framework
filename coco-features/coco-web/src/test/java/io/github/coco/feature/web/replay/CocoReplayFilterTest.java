package io.github.coco.feature.web.replay;

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
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.feature.web.request.metadata.CocoWebSecurityMetadataSource;
import io.github.coco.feature.web.response.CocoSystemCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link CocoReplayFilter} 单元测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class CocoReplayFilterTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @AfterEach
    void clearContext() {
        CocoRequestContextHolder.clear();
        CocoTraceContext.clear();
    }

    @Test
    void disabledFilterPassesThrough() throws Exception {
        CocoReplayProperties properties = new CocoReplayProperties();
        properties.setEnabled(false);
        CocoReplayFilter filter = new CocoReplayFilter(properties,
                (key, expiresAt) -> true,
                (snapshot, metadata) -> new CocoReplayKey("app-1", null, FIXED_NOW.toString(),
                        "nonce-1", "POST", "/api/test"),
                (traceId, request) -> baseSnapshot(traceId),
                input -> CocoWebRequestSecurityMetadata.empty(),
                exceptionWriter(),
                (request, rules) -> false,
                FIXED_CLOCK);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> chainCalls.incrementAndGet());

        assertEquals(1, chainCalls.get());
    }

    @Test
    void reservedReplayWritesEvidenceToContext() throws Exception {
        CocoReplayProperties properties = new CocoReplayProperties();
        properties.setRequired(true);
        properties.setTtlSeconds(120);
        CocoReplayKey replayKey = new CocoReplayKey("app-1", "key-1", FIXED_NOW.toString(),
                "nonce-1", "POST", "/api/orders");
        CocoWebRequestSecurityMetadata metadata = new CocoWebRequestSecurityMetadata(
                null, null, null, null, null, null, false,
                null, null, null, null, false, "app-1", "key-1", FIXED_NOW.toString(), "nonce-1");
        CocoReplayFilter filter = new CocoReplayFilter(properties,
                (key, expiresAt) -> true,
                (snapshot, resolvedMetadata) -> replayKey,
                (traceId, request) -> baseSnapshot(traceId),
                input -> metadata,
                exceptionWriter(),
                (request, rules) -> false,
                FIXED_CLOCK);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        AtomicReference<CocoRequestContext> context = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> context.set(CocoRequestContextHolder.current().orElseThrow()));

        CocoRequestContext requestContext = context.get();
        assertTrue(requestContext.replayReserved());
        assertEquals(Optional.of(CocoWebSecurityMetadataSource.HEADER.name()),
                requestContext.replayMetadataSource());
    }

    @Test
    void replayDetectedWritesErrorResponse() throws Exception {
        CocoReplayProperties properties = new CocoReplayProperties();
        properties.setRequired(true);
        properties.setTtlSeconds(120);
        CocoReplayKey replayKey = new CocoReplayKey("app-1", "key-1", FIXED_NOW.toString(),
                "nonce-1", "POST", "/api/orders");
        CocoWebRequestSecurityMetadata metadata = new CocoWebRequestSecurityMetadata(
                null, null, null, null, null, null, false,
                null, null, null, null, false, "app-1", "key-1", FIXED_NOW.toString(), "nonce-1");
        CocoReplayFilter filter = new CocoReplayFilter(properties,
                (key, expiresAt) -> false,
                (snapshot, resolvedMetadata) -> replayKey,
                (traceId, request) -> baseSnapshot(traceId),
                input -> metadata,
                exceptionWriter(),
                (request, rules) -> false,
                FIXED_CLOCK);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger chainCalls = new AtomicInteger();

        filter.doFilter(request, response, (req, res) -> chainCalls.incrementAndGet());

        assertEquals(0, chainCalls.get());
        assertTrue(response.getContentAsString().contains("coco.web.replay.detected"));
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