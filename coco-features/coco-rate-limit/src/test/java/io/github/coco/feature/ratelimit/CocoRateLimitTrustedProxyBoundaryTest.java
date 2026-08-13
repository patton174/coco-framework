package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import io.github.coco.feature.web.context.CocoClientIpResolution;
import io.github.coco.feature.web.context.CocoClientIpSource;
import io.github.coco.feature.web.context.CocoWebContextProperties;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.context.DefaultCocoClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 默认限流键依赖的可信代理边界测试。
 */
class CocoRateLimitTrustedProxyBoundaryTest {

    @Test
    void ignoresXForwardedForFromAnUntrustedRemoteAddressByDefault() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "203.0.113.20");

        assertThat(resolveRateLimitSubject(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void ignoresForwardedHeaderFromAnUntrustedRemoteAddressByDefault() {
        MockHttpServletRequest request = request();
        request.addHeader("Forwarded", "for=203.0.113.20");

        assertThat(resolveRateLimitSubject(request)).isEqualTo("198.51.100.10");
    }

    @Test
    void usesForwardedClientIpOnlyWhenCocoWebMarksTheProxyAsTrusted() {
        CocoWebRequestSnapshot snapshot = snapshot("203.0.113.20",
                CocoClientIpResolution.forwardedHeader("203.0.113.20", "X-Forwarded-For", "203.0.113.20",
                        "192.0.2.10"));

        assertThat(new DefaultCocoRateLimitKeyResolver().resolve(snapshot, route()).subject())
                .isEqualTo("203.0.113.20");
    }

    @Test
    void usesDirectRemoteAddressWithoutForwardedTrust() {
        CocoWebRequestSnapshot snapshot = snapshot("198.51.100.10",
                CocoClientIpResolution.remoteAddress("198.51.100.10"));

        assertThat(new DefaultCocoRateLimitKeyResolver().resolve(snapshot, route()).subject())
                .isEqualTo("198.51.100.10");
    }

    @Test
    void rejectsAMismatchedRemoteAddressResolution() {
        CocoWebRequestSnapshot snapshot = snapshot("203.0.113.20",
                new CocoClientIpResolution("203.0.113.20", CocoClientIpSource.REMOTE_ADDRESS, null, null,
                        "198.51.100.10", false, List.of(), null));

        assertThatThrownBy(() -> new DefaultCocoRateLimitKeyResolver().resolve(snapshot, route()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted client IP");
    }

    @Test
    void rejectsUnknownCompatibilitySnapshotSourceInsteadOfTrustingItsClientIp() {
        CocoWebRequestSnapshot snapshot = new CocoWebRequestSnapshot("trace", "GET", "/api", null,
                "203.0.113.20", null, null, null, null, null, null, Map.of(), Map.of());

        assertThatThrownBy(() -> new DefaultCocoRateLimitKeyResolver().resolve(snapshot, route()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trusted client IP");
    }

    @Test
    void businessCanReplaceTheDefaultResolverForItsOwnVerifiedIdentityModel() {
        CocoWebRequestSnapshot snapshot = new CocoWebRequestSnapshot("trace", "GET", "/api", null,
                "business-subject", null, null, null, null, null, null, Map.of(), Map.of());
        CocoRateLimitKeyResolver resolver = (request, configuredRoute) ->
                new CocoRateLimitKey(configuredRoute.getId(), request.clientIp());

        assertThat(resolver.resolve(snapshot, route()).subject()).isEqualTo("business-subject");
    }

    private static String resolveRateLimitSubject(MockHttpServletRequest request) {
        CocoClientIpResolution clientIpResolution = new DefaultCocoClientIpResolver(new CocoWebContextProperties())
                .resolveResolution(request);
        CocoWebRequestSnapshot snapshot = snapshot(clientIpResolution.clientIp(), clientIpResolution);
        return new DefaultCocoRateLimitKeyResolver().resolve(snapshot, route()).subject();
    }

    private static CocoWebRequestSnapshot snapshot(String clientIp, CocoClientIpResolution clientIpResolution) {
        return new CocoWebRequestSnapshot("trace", "GET", "/api", null, clientIp,
                null, null, null, null, null, null, Map.of(), Map.of(), null, null, clientIpResolution);
    }

    private static CocoRateLimitRoute route() {
        CocoRateLimitRoute route = new CocoRateLimitRoute();
        route.setId("public-api");
        route.setLimit(1);
        route.setWindowSeconds(60);
        return route;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        request.setRemoteAddr("198.51.100.10");
        return request;
    }
}
