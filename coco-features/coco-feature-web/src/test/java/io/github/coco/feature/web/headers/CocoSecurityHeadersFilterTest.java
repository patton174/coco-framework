package io.github.coco.feature.web.headers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * {@link CocoSecurityHeadersFilter} 单元测试。
 *
 * @author patton174
 * @since 1.1.0
 */
class CocoSecurityHeadersFilterTest {

    private static final String X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";

    private static final String X_FRAME_OPTIONS = "X-Frame-Options";

    private static final String REFERRER_POLICY = "Referrer-Policy";

    private static final String CONTENT_SECURITY_POLICY = "Content-Security-Policy";

    private static final String PERMISSIONS_POLICY = "Permissions-Policy";

    private static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";

    @Test
    void writesDefaultSecurityHeaders() throws Exception {
        MockHttpServletResponse response = doFilter(new CocoSecurityHeadersProperties(),
                new MockHttpServletRequest(), new MockFilterChain());

        assertThat(response.getHeader(X_CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
        assertThat(response.getHeader(X_FRAME_OPTIONS)).isEqualTo("DENY");
        assertThat(response.getHeader(REFERRER_POLICY)).isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    void doesNotWriteOptInHeadersByDefault() throws Exception {
        MockHttpServletResponse response = doFilter(new CocoSecurityHeadersProperties(),
                new MockHttpServletRequest(), new MockFilterChain());

        assertThat(response.getHeader(CONTENT_SECURITY_POLICY)).isNull();
        assertThat(response.getHeader(PERMISSIONS_POLICY)).isNull();
        assertThat(response.getHeader(STRICT_TRANSPORT_SECURITY)).isNull();
    }

    @Test
    void writesContentSecurityPolicyWhenConfigured() throws Exception {
        CocoSecurityHeadersProperties properties = new CocoSecurityHeadersProperties();
        properties.setContentSecurityPolicy("default-src 'self'");

        MockHttpServletResponse response = doFilter(properties, new MockHttpServletRequest(), new MockFilterChain());

        assertThat(response.getHeader(CONTENT_SECURITY_POLICY)).isEqualTo("default-src 'self'");
    }

    @Test
    void writesPermissionsPolicyWhenConfigured() throws Exception {
        CocoSecurityHeadersProperties properties = new CocoSecurityHeadersProperties();
        properties.setPermissionsPolicy("geolocation=(), camera=()");

        MockHttpServletResponse response = doFilter(properties, new MockHttpServletRequest(), new MockFilterChain());

        assertThat(response.getHeader(PERMISSIONS_POLICY)).isEqualTo("geolocation=(), camera=()");
    }

    @Test
    void blankDefaultedHeaderFallsBackToDefault() throws Exception {
        CocoSecurityHeadersProperties properties = new CocoSecurityHeadersProperties();
        properties.setContentTypeOptions("");
        properties.setFrameOptions("");
        properties.setReferrerPolicy("");

        MockHttpServletResponse response = doFilter(properties, new MockHttpServletRequest(), new MockFilterChain());

        assertThat(response.getHeader(X_CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
        assertThat(response.getHeader(X_FRAME_OPTIONS)).isEqualTo("DENY");
        assertThat(response.getHeader(REFERRER_POLICY)).isEqualTo("strict-origin-when-cross-origin");
    }

    @Test
    void skipsStrictTransportSecurityOverPlainHttp() throws Exception {
        CocoSecurityHeadersProperties properties = new CocoSecurityHeadersProperties();
        properties.setStrictTransportSecurity("max-age=31536000");

        MockHttpServletResponse response = doFilter(properties, new MockHttpServletRequest(), new MockFilterChain());

        assertThat(response.getHeader(STRICT_TRANSPORT_SECURITY)).isNull();
    }

    @Test
    void writesStrictTransportSecurityOverHttps() throws Exception {
        CocoSecurityHeadersProperties properties = new CocoSecurityHeadersProperties();
        properties.setStrictTransportSecurity("max-age=31536000; includeSubDomains");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);

        MockHttpServletResponse response = doFilter(properties, request, new MockFilterChain());

        assertThat(response.getHeader(STRICT_TRANSPORT_SECURITY))
                .isEqualTo("max-age=31536000; includeSubDomains");
    }

    @Test
    void invokesFilterChain() throws Exception {
        AtomicBoolean invoked = new AtomicBoolean();
        FilterChain chain = (chainRequest, chainResponse) -> invoked.set(true);

        doFilter(new CocoSecurityHeadersProperties(), new MockHttpServletRequest(), chain);

        assertThat(invoked).isTrue();
    }

    @Test
    void keepsSecurityHeadersOnErrorResponses() throws Exception {
        FilterChain chain = (chainRequest, chainResponse) ->
                ((MockHttpServletResponse) chainResponse).setStatus(401);

        MockHttpServletResponse response = doFilter(new CocoSecurityHeadersProperties(),
                new MockHttpServletRequest(), chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(X_CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
        assertThat(response.getHeader(X_FRAME_OPTIONS)).isEqualTo("DENY");
        assertThat(response.getHeader(REFERRER_POLICY)).isEqualTo("strict-origin-when-cross-origin");
    }

    private static MockHttpServletResponse doFilter(CocoSecurityHeadersProperties properties,
            MockHttpServletRequest request, FilterChain filterChain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new CocoSecurityHeadersFilter(properties).doFilter(request, response, filterChain);
        return response;
    }
}
