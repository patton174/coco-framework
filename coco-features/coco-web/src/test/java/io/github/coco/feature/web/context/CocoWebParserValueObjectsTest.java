package io.github.coco.feature.web.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.coco.feature.web.context.target.CocoWebRequestTarget;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetResolution;
import io.github.coco.feature.web.context.target.CocoWebRequestTargetSource;
import io.github.coco.feature.web.context.target.DefaultCocoWebRequestTargetResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * IP parser and request context value object contracts.
 */
class CocoWebParserValueObjectsTest {

    @Test
    void keepsPublishedNullSentinelForInvalidIpLiterals() {
        assertNull(CocoIpAddressSupport.parseIpAddress(null));
        assertNull(CocoIpAddressSupport.parseIpAddress("999.1.1.1"));
        assertNull(CocoIpAddressSupport.parseIpAddress("invalid-address"));
        assertNull(CocoIpAddressSupport.parseIpAddress("2001:db8:::1"));
        assertFalse(CocoIpAddressSupport.isTrustedProxy("invalid-address", Set.of("0.0.0.0/0")));
    }

    @Test
    void copiesFingerprintAndResolutionCollections() {
        Map<String, String> signals = new LinkedHashMap<>();
        signals.put("User-Agent", "agent");
        CocoBrowserFingerprint fingerprint = CocoBrowserFingerprint.from(signals);
        signals.put("accept-language", "zh-CN");
        assertEquals(Map.of("user-agent", "agent"), fingerprint.signals());
        assertThrows(UnsupportedOperationException.class,
                () -> fingerprint.signals().put("changed", "value"));

        List<String> chain = new ArrayList<>(List.of("198.51.100.8"));
        CocoClientIpResolution clientIp = CocoClientIpResolution.forwardedHeader("198.51.100.8", "X-Forwarded-For",
                "198.51.100.8", "10.0.0.1", chain, 0);
        chain.add("10.0.0.1");
        assertEquals(List.of("198.51.100.8"), clientIp.sourceChain());
        assertThrows(UnsupportedOperationException.class, () -> clientIp.sourceChain().add("changed"));

        List<String> headers = new ArrayList<>(List.of("X-Forwarded-Host"));
        CocoWebRequestTargetResolution target = CocoWebRequestTargetResolution.forwarded(
                new CocoWebRequestTarget("https", "example.test", 443, "/api"),
                CocoWebRequestTargetSource.FORWARDED_HEADERS, "10.0.0.1", headers, "/api");
        headers.add("X-Forwarded-Proto");
        assertEquals(List.of("x-forwarded-host"), target.sourceHeaders());
        assertThrows(UnsupportedOperationException.class, () -> target.sourceHeaders().add("changed"));
    }

    @Test
    void resolvesPartialForwardedValuesFromTrustedProxiesWithoutNullFallbacks() {
        CocoWebContextProperties properties = new CocoWebContextProperties();
        properties.setTrustedProxyCidrs(Set.of("10.0.0.0/8"));
        DefaultCocoWebRequestTargetResolver resolver = new DefaultCocoWebRequestTargetResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        request.setRemoteAddr("10.2.3.4");
        request.setScheme("http");
        request.setServerName("internal.example");
        request.setServerPort(8080);
        request.addHeader("Forwarded", "proto=https");

        CocoWebRequestTargetResolution resolution = resolver.resolveResolution(request);
        assertEquals(CocoWebRequestTargetSource.FORWARDED, resolution.source());
        assertEquals("https", resolution.target().scheme());
        assertEquals("internal.example", resolution.target().host());
        assertEquals(8080, resolution.target().port());
    }
}
