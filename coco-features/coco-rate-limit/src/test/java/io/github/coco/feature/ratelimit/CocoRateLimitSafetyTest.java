package io.github.coco.feature.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageCode;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CocoRateLimitSafetyTest {

    @Test
    void defaultKeyUsesOnlyServletRemoteAddressEvenWhenForwardingHeadersExist() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "198.51.100.9");
        request.addHeader("Forwarded", "for=198.51.100.10");

        assertThat(new DefaultCocoRateLimitKeyResolver().resolve(request, route()).subject())
                .isEqualTo("10.0.0.8");
    }

    @Test
    void responseSets429BeforeWritingTheBodyAndNeverWritesCommittedResponses() throws Exception {
        CocoRateLimitResponseWriter writer = new CocoRateLimitResponseWriter(messages(), new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(CocoRateLimitErrorCode.EXCEEDED, request, response);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("42900", "limited");
        response.setCommitted(true);
        assertThatThrownBy(() -> writer.write(CocoRateLimitErrorCode.EXCEEDED, request, response))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void handlerFailsClosedWhenTheStoreReportsCapacityExhaustion() throws Exception {
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        CocoRateLimitResponseWriter writer = new CocoRateLimitResponseWriter(messages(), new ObjectMapper());
        CocoRateLimitStore store = permit -> new CocoRateLimitDecision(false, permit.limit(), 0,
                permit.resetAt(), true);
        CocoRateLimitRequestHandler handler = new CocoRateLimitRequestHandler(
                new DefaultCocoRateLimitKeyResolver(), store, writer, clock);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(handler.handle(route(), request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("unavailable");
    }

    @Test
    void matcherUsesConfiguredMethodAndPathWithoutWebFeatureTypes() {
        CocoRateLimitProperties properties = new CocoRateLimitProperties();
        properties.getRoutes().add(route());
        DefaultCocoRateLimitRouteMatcher matcher = new DefaultCocoRateLimitRouteMatcher(properties);
        MockHttpServletRequest matching = new MockHttpServletRequest("GET", "/orders/7");
        MockHttpServletRequest wrongMethod = new MockHttpServletRequest("POST", "/orders/7");

        assertThat(matcher.resolve(matching)).isPresent();
        assertThat(matcher.resolve(wrongMethod)).isEmpty();
    }

    @Test
    void servletFallbackOrdersRemainExplicitAndStable() {
        assertThat(CocoRateLimitAutoConfiguration.MVC_INTERCEPTOR_ORDER)
                .isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
        assertThat(CocoRateLimitAutoConfiguration.FILTER_ORDER)
                .isGreaterThan(CocoRateLimitAutoConfiguration.MVC_INTERCEPTOR_ORDER);
    }

    @Test
    void retryAfterRoundsPositiveFractionalWindowsUpConservatively() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Instant now = Instant.parse("2026-08-14T00:00:00.250Z");

        CocoRateLimitRequestHandler.writeRateLimitHeaders(response,
                new CocoRateLimitDecision(false, 2, 0, now.plusMillis(1_500), false), now);

        assertThat(response.getHeader("Retry-After")).isEqualTo("2");
        assertThat(response.getHeader("RateLimit-Reset")).isEqualTo("2");
    }

    @Test
    void handlerDoesNotOverwriteAnAlreadyCommittedResponse() throws Exception {
        CocoRateLimitResponseWriter writer = new CocoRateLimitResponseWriter(messages(), new ObjectMapper());
        CocoRateLimitRequestHandler handler = new CocoRateLimitRequestHandler(new DefaultCocoRateLimitKeyResolver(),
                permit -> new CocoRateLimitDecision(false, permit.limit(), 0, permit.resetAt(), false), writer,
                Clock.systemUTC());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(204);
        response.setCommitted(true);

        assertThat(handler.handle(route(), request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentAsString()).isEmpty();
    }

    private static CocoRateLimitRoute route() {
        CocoRateLimitRoute route = new CocoRateLimitRoute();
        route.setId("orders");
        route.setLimit(2);
        route.setWindowSeconds(60);
        route.getMatcher().setMethods(java.util.List.of("GET"));
        route.getMatcher().setPathPatterns(java.util.List.of("/orders/**"));
        return route;
    }

    private static CocoMessageService messages() {
        return new CocoMessageService() {
            @Override public String getMessage(String code, Object... args) { return code; }
            @Override public String getMessage(String code, Locale locale, Object... args) { return code.contains("unavailable") ? "unavailable" : "limited"; }
            @Override public String getMessage(CocoMessageCode code, Object... args) { return code.code(); }
            @Override public String getMessage(CocoMessageCode code, Locale locale, Object... args) { return getMessage(code.code(), locale, args); }
            @Override public String getMessageOrDefault(String code, String defaultMessage, Object... args) { return defaultMessage; }
            @Override public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) { return defaultMessage; }
            @Override public String resolve(CocoMessage message) { return message.code(); }
            @Override public String resolve(CocoMessage message, Locale locale) { return message.code(); }
        };
    }
}
