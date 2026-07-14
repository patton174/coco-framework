package io.github.coco.feature.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.context.CocoRequestContext;
import io.github.coco.context.CocoRequestContextHolder;
import io.github.coco.feature.web.body.CocoCachedBodyHttpServletRequest;
import io.github.coco.feature.web.body.CocoCachedRequestBody;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationContext;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizationPurpose;
import io.github.coco.feature.web.context.CocoWebRequestCanonicalizer;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestSnapshot;
import io.github.coco.feature.web.encryption.CocoEncryptionAssociatedData;
import io.github.coco.feature.web.signature.CocoSignatureVerificationContext;
import io.github.coco.feature.web.signature.CocoSignatureVerifier;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CocoWebCryptoFilterChainIntegrationTest {

    private static final String APP_ID = "crypto-client";

    private static final String PATH = "/api/crypto";

    private static final String SECRET_V1 = "signature-secret-v1";

    private static final String SECRET_V2 = "signature-secret-v2";

    private static final byte[] KEY_V1 = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private static final byte[] KEY_V2 = "abcdef0123456789".getBytes(StandardCharsets.UTF_8);

    private static final byte[] IV = "123456789012".getBytes(StandardCharsets.UTF_8);

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoWebAutoConfiguration.class));

    @AfterEach
    void clearRequestContext() {
        CocoRequestContextHolder.clear();
    }

    @Test
    void decryptsBeforeVerifyingSignatureWhenBothProtectionsAreEnabled() {
        cryptoContext().run(context -> {
            assertThat(context).hasNotFailed();
            FilterRegistrationBean<?> encryption = registration(context, "cocoEncryptionFilterRegistration");
            FilterRegistrationBean<?> signature = registration(context, "cocoSignatureFilterRegistration");

            assertThat(encryption.getOrder())
                    .as("encrypted requests must be decrypted before plaintext signature verification")
                    .isLessThan(signature.getOrder());
        });
    }

    @Test
    void decryptsCachedBodyAndVerifiesCanonicalPlaintextThroughRealFilterChain() {
        AtomicReference<CocoSignatureVerificationContext> verification = new AtomicReference<>();
        CocoSignatureVerifier capturingVerifier = context -> {
            verification.set(context);
            return true;
        };

        cryptoContext()
                .withBean(CocoSignatureVerifier.class, () -> capturingVerifier)
                .run(context -> {
                    byte[] plaintext = "{\"amount\":128,\"currency\":\"CNY\"}"
                            .getBytes(StandardCharsets.UTF_8);
                    ProtectedRequest protectedRequest = protectedRequest(
                            context, plaintext, KEY_V1, SECRET_V1, null);
                    CapturingServlet servlet = new CapturingServlet();

                    MockHttpServletResponse response = executeCryptoChain(
                            context, protectedRequest.request(), servlet);

                    assertThat(response.getStatus()).isEqualTo(204);
                    assertThat(servlet.calls()).isEqualTo(1);
                    assertThat(servlet.body()).containsExactly(plaintext);
                    assertThat(servlet.context().requestDecrypted()).isTrue();
                    assertThat(servlet.context().signatureVerified()).isTrue();
                    assertThat(verification.get()).isNotNull();
                    assertThat(verification.get().request().canonicalText())
                            .isEqualTo(protectedRequest.canonicalText());
                    assertThat(verification.get().request().canonicalText())
                            .contains(sha256(plaintext))
                            .doesNotContain(sha256(protectedRequest.transportBody()));
                });
    }

    @Test
    void bindsCiphertextToAssociatedDataAndRejectsChangedRoute() {
        cryptoContext().run(context -> {
            ProtectedRequest protectedRequest = protectedRequest(
                    context, "{\"aad\":true}".getBytes(StandardCharsets.UTF_8), KEY_V1, SECRET_V1, null);
            protectedRequest.request().setRequestURI("/api/other");
            CapturingServlet servlet = new CapturingServlet();

            MockHttpServletResponse response = executeCryptoChain(context, protectedRequest.request(), servlet);

            assertUnauthorized(response);
            assertThat(servlet.calls()).isZero();
        });
    }

    @Test
    void resolvesRotatedEncryptionAndSignatureKeysByKeyId() {
        cryptoContext()
                .withInitializer(applicationContext -> TestPropertyValues.of(Map.of(
                        "coco.web.encryption.keys[" + APP_ID + ":v2]",
                        Base64.getEncoder().encodeToString(KEY_V2),
                        "coco.web.signature.secrets[" + APP_ID + ":v2]",
                        SECRET_V2)).applyTo(applicationContext))
                .run(context -> {
                    CocoWebProperties properties = context.getBean(CocoWebProperties.class);
                    assertThat(properties.getEncryption().getKeys())
                            .containsEntry(APP_ID + ":v2", Base64.getEncoder().encodeToString(KEY_V2));
                    assertThat(properties.getSignature().getSecrets())
                            .containsEntry(APP_ID + ":v2", SECRET_V2);
                    byte[] plaintext = "{\"rotation\":\"v2\"}".getBytes(StandardCharsets.UTF_8);
                    ProtectedRequest protectedRequest = protectedRequest(
                            context, plaintext, KEY_V2, SECRET_V2, "v2");
                    CapturingServlet servlet = new CapturingServlet();

                    MockHttpServletResponse response = executeCryptoChain(
                            context, protectedRequest.request(), servlet);

                    assertThat(response.getStatus()).isEqualTo(204);
                    assertThat(servlet.body()).containsExactly(plaintext);
                });
    }

    @Test
    void rejectsCiphertextEncryptedWithWrongKey() {
        cryptoContext().run(context -> {
            ProtectedRequest protectedRequest = protectedRequest(
                    context, "{\"key\":\"wrong\"}".getBytes(StandardCharsets.UTF_8), KEY_V2, SECRET_V1, null);
            CapturingServlet servlet = new CapturingServlet();

            MockHttpServletResponse response = executeCryptoChain(context, protectedRequest.request(), servlet);

            assertUnauthorized(response);
            assertThat(servlet.calls()).isZero();
        });
    }

    @Test
    void rejectsModifiedGcmTag() {
        cryptoContext().run(context -> {
            ProtectedRequest protectedRequest = protectedRequest(
                    context, "{\"tag\":\"modified\"}".getBytes(StandardCharsets.UTF_8), KEY_V1, SECRET_V1, null);
            byte[] ciphertext = Base64.getDecoder().decode(protectedRequest.transportBody());
            ciphertext[ciphertext.length - 1] ^= 0x01;
            protectedRequest.request().setContent(Base64.getEncoder().encode(ciphertext));
            CapturingServlet servlet = new CapturingServlet();

            MockHttpServletResponse response = executeCryptoChain(context, protectedRequest.request(), servlet);

            assertUnauthorized(response);
            assertThat(servlet.calls()).isZero();
        });
    }

    @Test
    void rejectsDuplicateEncryptionHeaderInsteadOfUsingFirstValue() {
        cryptoContext().run(context -> {
            ProtectedRequest protectedRequest = protectedRequest(
                    context, "{\"duplicate\":\"iv\"}".getBytes(StandardCharsets.UTF_8), KEY_V1, SECRET_V1, null);
            protectedRequest.request().addHeader("X-Coco-IV", Base64.getEncoder().encodeToString(KEY_V1));
            CapturingServlet servlet = new CapturingServlet();

            MockHttpServletResponse response = executeCryptoChain(context, protectedRequest.request(), servlet);

            assertSecurityRejected(response);
            assertThat(servlet.calls()).isZero();
        });
    }

    @Test
    void rejectsDuplicateSignatureHeaderInsteadOfUsingFirstValue() {
        cryptoContext().run(context -> {
            ProtectedRequest protectedRequest = protectedRequest(
                    context, "{\"duplicate\":\"signature\"}".getBytes(StandardCharsets.UTF_8),
                    KEY_V1, SECRET_V1, null);
            protectedRequest.request().addHeader("X-Coco-Sign", "invalid-second-signature");
            CapturingServlet servlet = new CapturingServlet();

            MockHttpServletResponse response = executeCryptoChain(context, protectedRequest.request(), servlet);

            assertUnauthorized(response);
            assertThat(servlet.calls()).isZero();
        });
    }

    @Test
    void returnsUnifiedExceptionResponseAndFailsClosedWhenPlaintextSignatureIsInvalid() {
        cryptoContext().run(context -> {
            ProtectedRequest protectedRequest = protectedRequest(
                    context, "{\"signature\":\"invalid\"}".getBytes(StandardCharsets.UTF_8),
                    KEY_V1, SECRET_V1, null);
            protectedRequest.request().removeHeader("X-Coco-Sign");
            protectedRequest.request().addHeader("X-Coco-Sign", "invalid-signature");
            CapturingServlet servlet = new CapturingServlet();

            MockHttpServletResponse response = executeCryptoChain(context, protectedRequest.request(), servlet);

            assertUnauthorized(response);
            Map<String, Object> body = responseBody(response);
            assertThat(body)
                    .containsEntry("success", false)
                    .containsEntry("code", 401);
            assertThat(body.get("message")).isInstanceOf(String.class);
            assertThat((String) body.get("message")).isNotBlank();
            assertThat(servlet.calls()).isZero();
        });
    }

    @Test
    void rejectsOversizedEncryptedPayloadBeforeDecryption() {
        cryptoContext()
                .withPropertyValues("coco.web.request-body.max-cache-bytes=48")
                .run(context -> {
                    MockHttpServletRequest request = requestWithSecurityHeaders(
                            Base64.getEncoder().encodeToString(IV),
                            Long.toString(System.currentTimeMillis()), null);
                    request.setContent(new byte[49]);
                    CapturingServlet servlet = new CapturingServlet();

                    MockHttpServletResponse response = executeCryptoChain(
                            context, request, servlet);

                    assertThat(response.getStatus()).isEqualTo(413);
                    assertThat(responseBody(response))
                            .containsEntry("success", false)
                            .containsEntry("code", 413);
                    assertThat(servlet.calls()).isZero();
                });
    }

    private WebApplicationContextRunner cryptoContext() {
        return this.contextRunner.withPropertyValues(
                "coco.web.replay.enabled=false",
                "coco.web.encryption.required=true",
                "coco.web.encryption.keys." + APP_ID + "=" + Base64.getEncoder().encodeToString(KEY_V1),
                "coco.web.signature.required=true",
                "coco.web.signature.secrets." + APP_ID + "=" + SECRET_V1);
    }

    private static ProtectedRequest protectedRequest(ApplicationContext context, byte[] plaintext,
            byte[] encryptionKey, String signatureSecret, String keyId) {
        String encodedIv = Base64.getEncoder().encodeToString(IV);
        String timestamp = Long.toString(System.currentTimeMillis());
        byte[] associatedData = CocoEncryptionAssociatedData.from(
                APP_ID, keyId, encodedIv, "AES-GCM", true, "POST", PATH, null,
                timestamp, "crypto-nonce");
        byte[] transportBody = Base64.getEncoder().encode(
                aesGcmEncrypt(plaintext, encryptionKey, IV, associatedData));
        MockHttpServletRequest canonicalRequest = requestWithSecurityHeaders(encodedIv, timestamp, keyId);
        canonicalRequest.setContent(transportBody);
        String canonicalText = canonicalText(context, canonicalRequest, plaintext);

        MockHttpServletRequest transportRequest = requestWithSecurityHeaders(encodedIv, timestamp, keyId);
        transportRequest.setContent(transportBody);
        transportRequest.addHeader("X-Coco-Sign", hmacSha256Hex(canonicalText, signatureSecret));
        return new ProtectedRequest(transportRequest, transportBody, canonicalText);
    }

    private static String canonicalText(ApplicationContext context, MockHttpServletRequest request,
            byte[] plaintext) {
        CocoWebRequestContextResolver resolver = context.getBean(CocoWebRequestContextResolver.class);
        CocoWebRequestCanonicalizer canonicalizer = context.getBean(CocoWebRequestCanonicalizer.class);
        AtomicReference<String> canonicalText = new AtomicReference<>();
        Filter bodyCachingFilter = registration(context, "cocoRequestBodyCachingFilterRegistration").getFilter();
        try {
            bodyCachingFilter.doFilter(request, new MockHttpServletResponse(), (cachedRequest, response) -> {
                HttpServletRequest effectiveRequest = new CocoCachedBodyHttpServletRequest(
                        (HttpServletRequest) cachedRequest, CocoCachedRequestBody.cached(plaintext));
                CocoWebRequestSnapshot snapshot = resolver.resolve(
                        "crypto-trace", effectiveRequest);
                canonicalText.set(canonicalizer.canonicalize(CocoWebRequestCanonicalizationContext.of(
                        CocoWebRequestCanonicalizationPurpose.SIGNATURE, snapshot, null)).text());
            });
        }
        catch (IOException | ServletException ex) {
            throw new IllegalStateException("Canonical request caching failed", ex);
        }
        return canonicalText.get();
    }

    private static MockHttpServletRequest requestWithSecurityHeaders(String encodedIv, String timestamp,
            String keyId) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.addHeader("X-Trace-Id", "crypto-trace");
        request.addHeader("X-Coco-Encrypted", "true");
        request.addHeader("X-Coco-App-Id", APP_ID);
        if (keyId != null) {
            request.addHeader("X-Coco-Key-Id", keyId);
        }
        request.addHeader("X-Coco-IV", encodedIv);
        request.addHeader("X-Coco-Algorithm", "AES-GCM");
        request.addHeader("X-Coco-Timestamp", timestamp);
        request.addHeader("X-Coco-Nonce", "crypto-nonce");
        request.addHeader("X-Coco-Sign-Algorithm", "HMAC-SHA256");
        return request;
    }

    private static MockHttpServletResponse executeCryptoChain(ApplicationContext context,
            MockHttpServletRequest request, CapturingServlet servlet) {
        List<FilterRegistrationBean<?>> registrations = List.of(
                registration(context, "cocoRequestBodyCachingFilterRegistration"),
                registration(context, "cocoEncryptionFilterRegistration"),
                registration(context, "cocoSignatureFilterRegistration"));
        Filter[] filters = registrations.stream()
                .sorted(Comparator.comparingInt(FilterRegistrationBean::getOrder))
                .map(FilterRegistrationBean::getFilter)
                .toArray(Filter[]::new);
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            new MockFilterChain(servlet, filters).doFilter(request, response);
        }
        catch (IOException | ServletException ex) {
            throw new IllegalStateException("Crypto filter chain execution failed", ex);
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private static FilterRegistrationBean<?> registration(ApplicationContext context, String beanName) {
        return context.getBean(beanName, FilterRegistrationBean.class);
    }

    private static byte[] aesGcmEncrypt(byte[] plaintext, byte[] key, byte[] iv, byte[] associatedData) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(associatedData);
            return cipher.doFinal(plaintext);
        }
        catch (Exception ex) {
            throw new IllegalStateException("AES-GCM encryption failed", ex);
        }
    }

    private static String hmacSha256Hex(String text, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 failed", ex);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (Exception ex) {
            throw new IllegalStateException("SHA-256 failed", ex);
        }
    }

    private static void assertUnauthorized(MockHttpServletResponse response) {
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(responseBody(response))
                .containsEntry("success", false)
                .containsEntry("code", 401);
    }

    private static void assertSecurityRejected(MockHttpServletResponse response) {
        assertThat(response.getStatus()).isIn(400, 401);
        assertThat(responseBody(response)).containsEntry("success", false);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> responseBody(MockHttpServletResponse response) {
        try {
            return new ObjectMapper().readValue(response.getContentAsByteArray(), Map.class);
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to parse filter response", ex);
        }
    }

    private record ProtectedRequest(MockHttpServletRequest request, byte[] transportBody, String canonicalText) {

        private ProtectedRequest {
            transportBody = transportBody.clone();
        }

        @Override
        public byte[] transportBody() {
            return this.transportBody.clone();
        }
    }

    private static final class CapturingServlet extends HttpServlet {

        private final AtomicInteger calls = new AtomicInteger();

        private final AtomicReference<byte[]> body = new AtomicReference<>();

        private final AtomicReference<CocoRequestContext> context = new AtomicReference<>();

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            this.calls.incrementAndGet();
            this.body.set(request.getInputStream().readAllBytes());
            this.context.set(CocoRequestContextHolder.current().orElseThrow());
            response.setStatus(204);
        }

        int calls() {
            return this.calls.get();
        }

        byte[] body() {
            byte[] value = this.body.get();
            return value == null ? null : value.clone();
        }

        CocoRequestContext context() {
            return this.context.get();
        }
    }
}
