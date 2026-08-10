package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.exception.CocoException;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.response.CocoSystemCodeProvider;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageCode;
import io.github.coco.i18n.CocoMessageService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 限流请求执行器 HTTP 契约测试。
 */
class CocoRateLimitRequestHandlerTest {

    @Test
    void writesCeilingRetryAfterAndCompatibilityHeadersForEnglishRequests() throws Exception {
        Instant now = Instant.parse("2026-07-15T00:00:00.500Z");
        CocoRateLimitRequestHandler handler = handler(now, (snapshot, route) -> new CocoRateLimitKey("api", "key"));
        MockHttpServletRequest request = request(Locale.US, "en-US");
        MockHttpServletResponse first = new MockHttpServletResponse();
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        CocoRateLimitRoute route = route();

        assertThat(handler.handle(route, request, first)).isTrue();
        assertThat(handler.handle(route, request, rejected)).isFalse();
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(rejected.getHeader("RateLimit-Reset")).isEqualTo("60");
        assertThat(rejected.getHeader("Retry-After")).isEqualTo("60");
        assertThat(rejected.getHeader("X-RateLimit-Limit")).isEqualTo("1");
        assertThat(rejected.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(rejected.getContentAsString()).contains("42900", "Request rate limit has been exceeded.");
    }

    @Test
    void localizesRejectedResponseForChineseRequests() throws Exception {
        Instant now = Instant.parse("2026-07-15T00:00:00Z");
        CocoRateLimitRequestHandler handler = handler(now, (snapshot, route) -> new CocoRateLimitKey("api", "key"));
        MockHttpServletRequest request = request(Locale.SIMPLIFIED_CHINESE, "zh-CN");
        CocoRateLimitRoute route = route();

        assertThat(handler.handle(route, request, new MockHttpServletResponse())).isTrue();
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        assertThat(handler.handle(route, request, rejected)).isFalse();
        assertThat(rejected.getContentAsString()).contains("请求过于频繁，请稍后重试。", "42900");
    }

    @Test
    void setsThe429StatusBeforeTheResponseBodyCanCommit() throws Exception {
        StatusCheckingResponse response = new StatusCheckingResponse(new MockHttpServletResponse());

        responseWriter().write(CocoRateLimitErrorCode.EXCEEDED.request(), request(Locale.US, "en-US"), response);

        assertThat(response.statusWhenOutputStreamWasRequested).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void doesNotWriteAnErrorResponseAfterTheResponseHasBeenCommitted() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(HttpStatus.CREATED.value());
        response.getOutputStream().write("already-written".getBytes(StandardCharsets.UTF_8));
        response.flushBuffer();
        CocoException exception = CocoRateLimitErrorCode.EXCEEDED.request();

        assertThatThrownBy(() -> responseWriter().write(exception, request(Locale.US, "en-US"), response))
                .isSameAs(exception);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED.value());
        assertThat(response.getContentAsString()).isEqualTo("already-written");
    }

    @Test
    void failsClosedWhenTrustedKeyResolutionFails() throws Exception {
        Instant now = Instant.parse("2026-07-15T00:00:00Z");
        CocoRateLimitRequestHandler handler = handler(now, (snapshot, route) -> {
            throw new IllegalStateException("untrusted header");
        });
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(handler.handle(route(), request(Locale.US, "en-US"), response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("42900");
    }

    @Test
    void failsClosedWhenAProgrammaticRouteUsesAnUnsupportedWindow() throws Exception {
        Instant now = Instant.parse("2026-07-15T00:00:00Z");
        CocoRateLimitRequestHandler handler = handler(now, (snapshot, route) -> new CocoRateLimitKey("api", "key"));
        CocoRateLimitRoute route = route();
        route.setWindowSeconds(Long.MAX_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(handler.handle(route, request(Locale.US, "en-US"), response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void failsClosedWhenTheClockIsAtTheLatestRepresentableInstant() throws Exception {
        CocoRateLimitRequestHandler handler = handler(Instant.MAX,
                (snapshot, route) -> new CocoRateLimitKey("api", "key"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(handler.handle(route(), request(Locale.US, "en-US"), response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("1");
    }

    @Test
    void snapshotsProgrammaticRouteBeforeCallingTheKeyResolver() throws Exception {
        Instant now = Instant.parse("2026-07-15T00:00:00Z");
        AtomicReference<CocoRateLimitRoute> resolvedRoute = new AtomicReference<>();
        CocoRateLimitRequestHandler handler = handler(now, (snapshot, route) -> {
            resolvedRoute.set(route);
            route.setLimit(99);
            route.getMatcher().setPathPatterns(Set.of("/changed"));
            return new CocoRateLimitKey("api", "key");
        });
        CocoRateLimitRoute route = route();

        assertThat(handler.handle(route, request(Locale.US, "en-US"), new MockHttpServletResponse())).isTrue();
        assertThat(resolvedRoute.get()).isNotSameAs(route);
        assertThat(route.getLimit()).isEqualTo(1);
        assertThat(route.getMatcher().getPathPatterns()).isEmpty();
    }

    @Test
    void logsQuotaRejectionsAtInfoAndCapacityRejectionsAtWarn() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(CocoRateLimitRequestHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        Instant now = Instant.parse("2026-07-15T00:00:00Z");
        try {
            CocoRateLimitStore quotaStore = permit -> new CocoRateLimitDecision(false, permit.limit(), 0,
                    permit.resetAt(), false);
            CocoRateLimitStore capacityStore = permit -> new CocoRateLimitDecision(false, permit.limit(), 0,
                    permit.resetAt(), true);

            assertThat(handler(now, (snapshot, route) -> new CocoRateLimitKey("api", "quota"), quotaStore)
                    .handle(route(), request(Locale.US, "en-US"), new MockHttpServletResponse())).isFalse();
            assertThat(handler(now, (snapshot, route) -> new CocoRateLimitKey("api", "capacity"), capacityStore)
                    .handle(route(), request(Locale.US, "en-US"), new MockHttpServletResponse())).isFalse();

            assertThat(appender.list)
                    .filteredOn(event -> event.getFormattedMessage().contains("quota is exhausted"))
                    .singleElement()
                    .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.INFO));
            assertThat(appender.list)
                    .filteredOn(event -> event.getFormattedMessage().contains("capacity is exhausted"))
                    .singleElement()
                    .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
        }
        finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    static CocoRateLimitRequestHandler handler(Instant now, CocoRateLimitKeyResolver keyResolver) {
        CocoRateLimitProperties properties = new CocoRateLimitProperties();
        CocoRateLimitProperties.InMemory inMemory = new CocoRateLimitProperties.InMemory();
        inMemory.setMaxEntries(10);
        properties.setInMemory(inMemory);
        return handler(now, keyResolver, new InMemoryCocoRateLimitStore(properties,
                Clock.fixed(now, ZoneOffset.UTC), false));
    }

    static CocoRateLimitRequestHandler handler(Instant now, CocoRateLimitKeyResolver keyResolver,
            CocoRateLimitStore store) {
        return new CocoRateLimitRequestHandler(keyResolver, store, (traceId, request) -> null,
                responseWriter(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static CocoRateLimitResponseWriter responseWriter() {
        CocoWebExceptionHandler exceptionHandler = new CocoWebExceptionHandler(new TestMessageService(),
                (CocoExceptionHttpStatusResolver) exception -> HttpStatus.BAD_REQUEST, new TestSystemCodeProvider());
        return new CocoRateLimitResponseWriter(exceptionHandler, new ObjectMapper());
    }

    private static CocoRateLimitRoute route() {
        CocoRateLimitRoute route = new CocoRateLimitRoute();
        route.setId("api");
        route.setLimit(1);
        route.setWindowSeconds(60);
        return route;
    }

    private static MockHttpServletRequest request(Locale locale, String acceptLanguage) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        request.addHeader("Accept-Language", acceptLanguage);
        request.setPreferredLocales(java.util.List.of(locale));
        return request;
    }

    private static final class TestMessageService implements CocoMessageService {

        @Override
        public String getMessage(String code, Object... args) {
            return code;
        }

        @Override
        public String getMessage(String code, Locale locale, Object... args) {
            return message(locale);
        }

        @Override
        public String getMessage(CocoMessageCode messageCode, Object... args) {
            return messageCode.code();
        }

        @Override
        public String getMessage(CocoMessageCode messageCode, Locale locale, Object... args) {
            return message(locale);
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Object... args) {
            return defaultMessage;
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) {
            return message(locale);
        }

        @Override
        public String resolve(CocoMessage message) {
            return message(Locale.US);
        }

        @Override
        public String resolve(CocoMessage message, Locale locale) {
            return message(locale);
        }

        private static String message(Locale locale) {
            return Locale.SIMPLIFIED_CHINESE.getLanguage().equals(locale.getLanguage())
                    ? "请求过于频繁，请稍后重试。"
                    : "Request rate limit has been exceeded.";
        }
    }

    private static final class TestSystemCodeProvider implements CocoSystemCodeProvider {

        @Override public int success() { return 0; }
        @Override public int invalidArgument() { return 40000; }
        @Override public int unauthorized() { return 40100; }
        @Override public int forbidden() { return 40300; }
        @Override public int notFound() { return 40400; }
        @Override public int conflict() { return 40900; }
        @Override public int internalError() { return 50000; }
    }

    private static final class StatusCheckingResponse extends HttpServletResponseWrapper {

        private int statusWhenOutputStreamWasRequested = -1;

        private StatusCheckingResponse(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            this.statusWhenOutputStreamWasRequested = getStatus();
            return super.getOutputStream();
        }
    }
}
