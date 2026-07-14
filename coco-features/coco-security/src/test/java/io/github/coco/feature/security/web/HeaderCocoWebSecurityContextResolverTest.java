package io.github.coco.feature.security.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HeaderCocoWebSecurityContextResolverTest {

    @Test
    void trustedHeaderAdapterRemainsDisabledByDefault() {
        CocoSecurityWebHeaderProperties properties = new CocoSecurityWebHeaderProperties();
        HeaderCocoWebSecurityContextResolver resolver = new HeaderCocoWebSecurityContextResolver(properties);
        MockHttpServletRequest request = trustedRequest();

        assertTrue(resolver.resolve(request).isEmpty());
    }

    @Test
    void repeatedTrustedHeadersFailClosedEvenWhenValuesMatch() {
        assertRepeatedHeadersFailClosed(false);
    }

    @Test
    void conflictingTrustedHeadersFailClosed() {
        assertRepeatedHeadersFailClosed(true);
    }

    private static void assertRepeatedHeadersFailClosed(boolean conflicting) {
        CocoSecurityWebHeaderProperties properties = new CocoSecurityWebHeaderProperties();
        properties.setEnabled(true);
        HeaderCocoWebSecurityContextResolver resolver = new HeaderCocoWebSecurityContextResolver(properties);
        List<HeaderCase> headers = List.of(
                new HeaderCase(properties.getPrincipalIdHeaderName(), "1001", "1002"),
                new HeaderCase(properties.getPrincipalNameHeaderName(), "Patton", "Other"),
                new HeaderCase(properties.getRolesHeaderName(), "admin", "operator"),
                new HeaderCase(properties.getPermissionsHeaderName(), "order:read", "order:write"));

        for (HeaderCase header : headers) {
            MockHttpServletRequest request = trustedRequest();
            request.addHeader(header.name(), conflicting ? header.conflictingValue() : header.originalValue());

            assertTrue(resolver.resolve(request).isEmpty(), header.name());
        }
    }

    private static MockHttpServletRequest trustedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/trusted");
        request.addHeader("X-Coco-Principal-Id", "1001");
        request.addHeader("X-Coco-Principal-Name", "Patton");
        request.addHeader("X-Coco-Roles", "admin");
        request.addHeader("X-Coco-Permissions", "order:read");
        return request;
    }

    private record HeaderCase(String name, String originalValue, String conflictingValue) {
    }
}
