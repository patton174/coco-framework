package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.response.CocoSystemCodeProvider;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageCode;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
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

    static CocoRateLimitRequestHandler handler(Instant now, CocoRateLimitKeyResolver keyResolver) {
        CocoRateLimitProperties properties = new CocoRateLimitProperties();
        properties.getInMemory().setMaxEntries(10);
        InMemoryCocoRateLimitStore store = new InMemoryCocoRateLimitStore(properties,
                Clock.fixed(now, ZoneOffset.UTC), false);
        CocoWebExceptionHandler exceptionHandler = new CocoWebExceptionHandler(new TestMessageService(),
                (CocoExceptionHttpStatusResolver) exception -> HttpStatus.BAD_REQUEST, new TestSystemCodeProvider());
        return new CocoRateLimitRequestHandler(keyResolver, store, (traceId, request) -> null,
                new CocoRateLimitResponseWriter(exceptionHandler, new ObjectMapper()), Clock.fixed(now, ZoneOffset.UTC));
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
}
