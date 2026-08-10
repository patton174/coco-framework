package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

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

    private static String resolveRateLimitSubject(MockHttpServletRequest request) {
        String clientIp = new DefaultCocoClientIpResolver(new CocoWebContextProperties()).resolve(request);
        CocoWebRequestSnapshot snapshot = new CocoWebRequestSnapshot("trace", "GET", "/api", null, clientIp,
                null, null, null, null, null, null, Map.of(), Map.of());
        CocoRateLimitRoute route = new CocoRateLimitRoute();
        route.setId("public-api");
        route.setLimit(1);
        route.setWindowSeconds(60);
        return new DefaultCocoRateLimitKeyResolver().resolve(snapshot, route).subject();
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        request.setRemoteAddr("198.51.100.10");
        return request;
    }
}
