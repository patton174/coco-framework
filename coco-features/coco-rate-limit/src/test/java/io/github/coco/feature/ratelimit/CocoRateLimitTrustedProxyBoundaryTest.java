package io.github.coco.feature.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coco.feature.web.context.CocoWebContextProperties;
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

        assertThat(new DefaultCocoClientIpResolver(new CocoWebContextProperties()).resolve(request))
                .isEqualTo("198.51.100.10");
    }

    @Test
    void ignoresForwardedHeaderFromAnUntrustedRemoteAddressByDefault() {
        MockHttpServletRequest request = request();
        request.addHeader("Forwarded", "for=203.0.113.20");

        assertThat(new DefaultCocoClientIpResolver(new CocoWebContextProperties()).resolve(request))
                .isEqualTo("198.51.100.10");
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api");
        request.setRemoteAddr("198.51.100.10");
        return request;
    }
}
