package io.github.coco.feature.web.request.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.body.CocoResolvedRequestBody;
import io.github.coco.feature.web.context.CocoWebContextProperties;
import io.github.coco.feature.web.context.CocoWebParameterSource;
import io.github.coco.feature.web.encryption.CocoEncryptionProperties;
import io.github.coco.feature.web.replay.CocoReplayProperties;
import io.github.coco.feature.web.signature.CocoSignatureProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class DefaultCocoWebRequestSecurityInputResolverTest {

    @Test
    void capturesSecurityHeadersCanonicalHeadersCookiesAndCachedBody() {
        CocoWebContextProperties contextProperties = new CocoWebContextProperties();
        contextProperties.setMaxHeaderValueLength(4);
        contextProperties.setMaxCookieValueLength(4);
        contextProperties.setSecurityHeaderNames(Set.of("X-Custom-Security"));
        contextProperties.setCanonicalHeaderNames(Set.of("X-Custom-Canonical"));
        contextProperties.setCanonicalCookieNames(Set.of("signed-session"));
        CocoSignatureProperties signatureProperties = new CocoSignatureProperties();
        signatureProperties.setAppIdHeaderName("X-Sign-App");
        signatureProperties.setAlgorithmHeaderName("X-Sign-Algorithm");
        signatureProperties.setSignatureHeaderName("X-Signature");
        CocoEncryptionProperties encryptionProperties = new CocoEncryptionProperties();
        encryptionProperties.setIvHeaderName("X-Enc-IV");
        CocoReplayProperties replayProperties = new CocoReplayProperties();
        replayProperties.setNonceHeaderName("X-Replay-Nonce");
        byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        CocoCachedRequestBody cachedBody = CocoCachedRequestBody.cached(body);
        DefaultCocoWebRequestSecurityInputResolver resolver = new DefaultCocoWebRequestSecurityInputResolver(
                contextProperties,
                null,
                null,
                null,
                signatureProperties,
                encryptionProperties,
                replayProperties,
                request -> new CocoResolvedRequestBody(null, cachedBody, null, request.getContentType(),
                        request.getCharacterEncoding()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("X-Custom-Security", "custom-security-value");
        request.addHeader("X-Custom-Canonical", " left ");
        request.addHeader("X-Custom-Canonical", "right");
        request.addHeader("X-Sign-App", "app-1");
        request.addHeader("X-Sign-Algorithm", "HMAC-SHA256");
        request.addHeader("X-Signature", "signature-value");
        request.addHeader("X-Enc-IV", "iv-value");
        request.addHeader("X-Replay-Nonce", "nonce-1");
        request.setCookies(new Cookie("signed-session", "session-value-long"), new Cookie("ignored", "ignored"));

        CocoWebRequestSecurityInput input = resolver.resolve(request, "post", "/api/orders");

        assertEquals("POST", input.method());
        assertEquals("/api/orders", input.path());
        assertEquals("custom-security-value", input.securityHeader("x-custom-security").orElseThrow());
        assertEquals("app-1", input.securityHeader("x-sign-app").orElseThrow());
        assertEquals("signature-value", input.securityHeader("x-signature").orElseThrow());
        assertEquals("iv-value", input.canonicalHeader("x-enc-iv").orElseThrow());
        assertEquals("nonce-1", input.canonicalHeader("x-replay-nonce").orElseThrow());
        assertEquals("left,right", input.canonicalHeader("x-custom-canonical").orElseThrow());
        assertEquals(List.of("left", "right"), input.canonicalHeaderValues("x-custom-canonical").orElseThrow());
        assertFalse(input.canonicalHeader("x-signature").isPresent());
        assertEquals("session-value-long", input.canonicalCookie("signed-session").orElseThrow());
        assertTrue(input.bodyCached());
        assertEquals(body.length, input.bodyLength());
        assertEquals(cachedBody.sha256(), input.bodySha256());
    }

    @Test
    void separatesRawQueryAndCachedFormPayloadParameters() {
        DefaultCocoWebRequestSecurityInputResolver resolver = new DefaultCocoWebRequestSecurityInputResolver(
                new CocoWebContextProperties(),
                null,
                null,
                null,
                new CocoSignatureProperties(),
                new CocoEncryptionProperties(),
                new CocoReplayProperties(),
                null);
        MockHttpServletRequest rawRequest = new MockHttpServletRequest("POST", "/api/orders");
        rawRequest.setQueryString("tenant=t1&item=query&encoded=a%2Bb");
        rawRequest.setContentType("application/x-www-form-urlencoded");
        rawRequest.setCharacterEncoding(StandardCharsets.UTF_8.name());
        byte[] body = "item=body&qty=2&empty=".getBytes(StandardCharsets.UTF_8);
        HttpServletRequest request = new CocoCachedBodyHttpServletRequest(rawRequest,
                CocoCachedRequestBody.cached(body));

        CocoWebRequestSecurityInput input = resolver.resolve(request, "post", "/api/orders");

        assertEquals("tenant=t1&item=query&encoded=a%2Bb", input.queryString());
        assertEquals(List.of("t1"), input.queryParameter("tenant").orElseThrow());
        assertEquals(List.of("a%2Bb"), input.queryParameter("encoded").orElseThrow());
        assertEquals(List.of("body"), input.payloadParameter("item").orElseThrow());
        assertEquals(List.of("2"), input.payloadParameter("qty").orElseThrow());
        assertEquals(List.of(""), input.payloadParameter("empty").orElseThrow());
        assertEquals(List.of("query", "body"), input.parameter("item").orElseThrow());
        assertEquals(CocoWebParameterSource.FORM, input.payloadSource());
        assertTrue(input.bodyCached());
        assertEquals(body.length, input.bodyLength());
    }

    @Test
    void treatsNullResolvedBodyAsUncachedInput() {
        DefaultCocoWebRequestSecurityInputResolver resolver = new DefaultCocoWebRequestSecurityInputResolver(
                new CocoWebContextProperties(),
                null,
                null,
                null,
                new CocoSignatureProperties(),
                new CocoEncryptionProperties(),
                new CocoReplayProperties(),
                request -> null);

        CocoWebRequestSecurityInput input = resolver.resolve(new MockHttpServletRequest("GET", "/actuator/health"),
                "get", "/actuator/health");

        assertEquals("GET", input.method());
        assertFalse(input.bodyCached());
        assertNull(input.bodyLength());
        assertNull(input.bodySha256());
    }
}
