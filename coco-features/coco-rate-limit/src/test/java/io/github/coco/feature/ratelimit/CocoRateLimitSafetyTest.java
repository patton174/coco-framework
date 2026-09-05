package io.github.coco.feature.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageCode;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CocoRateLimitSafetyTest {

    @Test
    void untrustedRemoteAddressCannotInjectForwardedIdentity() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "198.51.100.9");
        request.addHeader("Forwarded", "for=198.51.100.10");

        assertThat(new DefaultCocoRateLimitKeyResolver().resolve(request, route()).subject())
                .isEqualTo("10.0.0.8");
    }

    @Test
    void trustedProxyChainSelectsTheRightmostNonProxyAddress() {
        CocoRateLimitProperties.TrustedProxy trustedProxy = new CocoRateLimitProperties.TrustedProxy();
        trustedProxy.setRemoteAddresses(List.of("10.0.0.1", "10.0.0.2"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.1");

        assertThat(new DefaultCocoRateLimitKeyResolver(trustedProxy).resolve(request, route()).subject())
                .isEqualTo("198.51.100.7");
    }

    @Test
    void keyResolverSpiCanReplaceTrustedProxyPolicyCompletely() {
        CocoRateLimitKeyResolver resolver = (request, configuredRoute) ->
                new CocoRateLimitKey(configuredRoute.getId(), request.getHeader("X-Verified-Client"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.addHeader("X-Verified-Client", "gateway-subject");

        assertThat(resolver.resolve(request, route()).subject()).isEqualTo("gateway-subject");
    }

    @Test
    void externalPropertiesBindTrustedProxyAndFilterExclusions() {
        CocoRateLimitProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
                "coco.rate-limit.trusted-proxy.remote-addresses[0]", "10.0.0.2",
                "coco.rate-limit.filter.excluded-path-patterns[0]", "/internal/**")))
                .bind("coco.rate-limit", Bindable.of(CocoRateLimitProperties.class))
                .orElseThrow(() -> new AssertionError("rate-limit properties did not bind"));

        assertThat(properties.getTrustedProxy().getRemoteAddresses()).containsExactly("10.0.0.2");
        assertThat(properties.getFilter().getExcludedPathPatterns()).containsExactly("/internal/**");
    }

    @Test
    void responseUsesDistinctStatusesBeforeWritingBodiesAndNeverWritesCommittedResponses() throws Exception {
        CocoRateLimitResponseWriter writer = new CocoRateLimitResponseWriter(messages(), new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.addPreferredLocale(Locale.US);
        MockHttpServletResponse exceededResponse = new MockHttpServletResponse();

        writer.write(CocoRateLimitErrorCode.EXCEEDED, request, exceededResponse);

        assertThat(exceededResponse.getStatus()).isEqualTo(429);
        assertThat(exceededResponse.getContentAsString()).contains("\"code\":42900",
                "Request rate limit has been exceeded.");
        MockHttpServletResponse unavailableResponse = new MockHttpServletResponse();

        writer.write(CocoRateLimitErrorCode.UNAVAILABLE, request, unavailableResponse);

        assertThat(unavailableResponse.getStatus()).isEqualTo(503);
        assertThat(unavailableResponse.getContentAsString()).contains("\"code\":50300",
                "Request rate limiting is temporarily unavailable.");
        exceededResponse.setCommitted(true);
        assertThatThrownBy(() -> writer.write(CocoRateLimitErrorCode.EXCEEDED, request, exceededResponse))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void handlerFailsClosedWhenTheStoreReportsCapacityExhaustion() throws Exception {
        Instant now = Instant.parse("2026-08-14T00:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        CocoRateLimitResponseWriter writer = new CocoRateLimitResponseWriter(messages(), new ObjectMapper());
        CocoRateLimitStore store = permit -> new CocoRateLimitDecision(false, permit.limit(), 0,
                Instant.EPOCH.plusSeconds(permit.windowSeconds()), true);
        CocoRateLimitRequestHandler handler = new CocoRateLimitRequestHandler(
                new DefaultCocoRateLimitKeyResolver(), store, writer, clock);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.addPreferredLocale(Locale.US);
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(handler.handle(route(), request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"code\":50300",
                "Request rate limiting is temporarily unavailable.");
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
                permit -> new CocoRateLimitDecision(false, permit.limit(), 0, Instant.EPOCH.plusSeconds(permit.windowSeconds()), false), writer,
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

    @Test
    void filterSkipsDefaultManagementAndHealthPathsWithoutConsumingQuota() throws Exception {
        AtomicInteger acquisitions = new AtomicInteger();
        CocoRateLimitFilter filter = filter(acquisitions, new CocoRateLimitProperties.Filter(), wildcardRoute());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(acquisitions).hasValue(0);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void filterStillConsumesQuotaForExplicitBusinessRoutes() throws Exception {
        AtomicInteger acquisitions = new AtomicInteger();
        CocoRateLimitFilter filter = filter(acquisitions, new CocoRateLimitProperties.Filter());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/7");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(acquisitions).hasValue(1);
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void filterExclusionsCanBeConfiguredForApplicationSpecificPaths() throws Exception {
        AtomicInteger acquisitions = new AtomicInteger();
        CocoRateLimitProperties.Filter filterProperties = new CocoRateLimitProperties.Filter();
        filterProperties.setExcludedPathPatterns(List.of("/orders/**"));
        CocoRateLimitFilter filter = filter(acquisitions, filterProperties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders/7");
        request.setRemoteAddr("127.0.0.1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(acquisitions).hasValue(0);
        assertThat(chain.getRequest()).isSameAs(request);
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

    private static CocoRateLimitRoute wildcardRoute() {
        CocoRateLimitRoute route = route();
        route.getMatcher().setPathPatterns(List.of("/**"));
        return route;
    }

    private static CocoRateLimitFilter filter(AtomicInteger acquisitions, CocoRateLimitProperties.Filter filter) {
        return filter(acquisitions, filter, route());
    }

    private static CocoRateLimitFilter filter(AtomicInteger acquisitions, CocoRateLimitProperties.Filter filter,
            CocoRateLimitRoute route) {
        CocoRateLimitProperties properties = new CocoRateLimitProperties();
        properties.getRoutes().add(route);
        CocoRateLimitStore store = permit -> {
            acquisitions.incrementAndGet();
            return new CocoRateLimitDecision(true, permit.limit(), permit.limit() - 1, Instant.EPOCH.plusSeconds(permit.windowSeconds()), false);
        };
        CocoRateLimitRequestHandler handler = new CocoRateLimitRequestHandler(new DefaultCocoRateLimitKeyResolver(),
                store, new CocoRateLimitResponseWriter(messages(), new ObjectMapper()), Clock.systemUTC());
        return new CocoRateLimitFilter(new DefaultCocoRateLimitRouteMatcher(properties), handler, filter);
    }

    private static CocoMessageService messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("coco-rate-limit-messages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(false);
        return new CocoMessageService() {
            @Override public String getMessage(String code, Object... args) { return source.getMessage(code, args, Locale.getDefault()); }
            @Override public String getMessage(String code, Locale locale, Object... args) { return source.getMessage(code, args, locale); }
            @Override public String getMessage(CocoMessageCode code, Object... args) { return getMessage(code.code(), args); }
            @Override public String getMessage(CocoMessageCode code, Locale locale, Object... args) { return getMessage(code.code(), locale, args); }
            @Override public String getMessageOrDefault(String code, String defaultMessage, Object... args) { return source.getMessage(code, args, defaultMessage, Locale.getDefault()); }
            @Override public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) { return source.getMessage(code, args, defaultMessage, locale); }
            @Override public String resolve(CocoMessage message) { return getMessage(message.code(), message.args()); }
            @Override public String resolve(CocoMessage message, Locale locale) { return getMessage(message.code(), locale, message.args()); }
        };
    }
}
