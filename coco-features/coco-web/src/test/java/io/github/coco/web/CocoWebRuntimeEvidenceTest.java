package io.github.coco.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.context.CocoRequestContext;
import io.github.coco.context.CocoRequestContextAttributes;
import io.github.coco.context.CocoRequestContextHolder;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.context.trace.CocoTraceContext;
import io.github.coco.web.context.CocoWebRequestCanonicalForm;
import io.github.coco.web.context.CocoWebRequestContextResolver;
import io.github.coco.web.context.CocoWebRequestSnapshot;
import io.github.coco.web.encryption.CocoEncryptedRequest;
import io.github.coco.web.encryption.CocoEncryptionAssociatedData;
import io.github.coco.web.encryption.CocoEncryptionFilter;
import io.github.coco.web.encryption.CocoEncryptionKey;
import io.github.coco.web.encryption.CocoEncryptionProperties;
import io.github.coco.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.web.exception.CocoWebExceptionHandler;
import io.github.coco.web.replay.CocoReplayFilter;
import io.github.coco.web.replay.CocoReplayKey;
import io.github.coco.web.replay.CocoReplayProperties;
import io.github.coco.web.response.CocoSystemCodes;
import io.github.coco.web.request.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.web.request.metadata.CocoWebSecurityMetadataSource;
import io.github.coco.web.signature.CocoSignatureFilter;
import io.github.coco.web.signature.CocoSignatureProperties;
import io.github.coco.web.signature.CocoSignatureSecret;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CocoWebRuntimeEvidenceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @AfterEach
    void clearContext() {
        CocoRequestContextHolder.clear();
        CocoTraceContext.clear();
    }

    @Test
    void snapshotContextAttributesAreVisibleFromRequestContextGetters() {
        CocoWebRequestSnapshot snapshot = baseSnapshot("snapshot-evidence")
                .withContextAttributes(Map.of(
                        CocoRequestContextAttributes.SIGNATURE_VERIFIED, "true",
                        CocoRequestContextAttributes.SIGNATURE_CANONICAL_SHA256, "canonical-sha",
                        CocoRequestContextAttributes.REQUEST_DECRYPTED, "true",
                        CocoRequestContextAttributes.REPLAY_RESERVED, "true"));

        CocoRequestContext context = snapshot.toRequestContext();

        assertTrue(context.signatureVerified());
        assertEquals(Optional.of("canonical-sha"), context.signatureCanonicalSha256());
        assertTrue(context.requestDecrypted());
        assertTrue(context.replayReserved());
    }

    @Test
    void signatureFilterWritesVerificationEvidenceToCurrentRequestContext() throws Exception {
        CocoSignatureProperties properties = new CocoSignatureProperties();
        properties.setTimestampValidationEnabled(false);
        CocoWebRequestSecurityMetadata metadata = signatureMetadata();
        CocoSignatureFilter filter = new CocoSignatureFilter(
                properties,
                request -> Optional.of(new CocoSignatureSecret(request.appId(), request.keyId(), "secret")),
                context -> true,
                (traceId, request) -> baseSnapshot(traceId),
                context -> new CocoWebRequestCanonicalForm("canonical", "canonical-sha"),
                exceptionWriter(),
                input -> metadata,
                (request, rules) -> false,
                FIXED_CLOCK);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader(properties.getSignatureHeaderName(), "signature-value");
        request.addHeader("X-Trace-Id", "signature-evidence");
        AtomicReference<CocoRequestContext> context = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), capturingChain(context));

        CocoRequestContext requestContext = context.get();
        assertTrue(requestContext.signatureVerified());
        assertEquals(Optional.of(FIXED_NOW.toString()), requestContext.signatureVerifiedAt());
        assertEquals(Optional.of("canonical-sha"), requestContext.signatureCanonicalSha256());
        assertEquals(Optional.of(CocoWebSecurityMetadataSource.HEADER.name()),
                requestContext.signatureMetadataSource());
        assertEquals(Optional.empty(), requestContext.signatureAppId());
    }

    @Test
    void encryptionFilterWritesDecryptionEvidenceAndKeepsPreviousEvidence() throws Exception {
        CocoEncryptionProperties properties = new CocoEncryptionProperties();
        CocoWebRequestSecurityMetadata metadata = encryptionMetadata();
        CocoWebRequestSnapshot transportSnapshot = baseSnapshot("encryption-evidence")
                .withContextAttribute(CocoRequestContextAttributes.SIGNATURE_VERIFIED, "true");
        CocoWebRequestSnapshot effectiveSnapshot = baseSnapshot("encryption-evidence");
        byte[] encryptedPayload = "cipher".getBytes(StandardCharsets.UTF_8);
        byte[] expectedAssociatedData = CocoEncryptionAssociatedData.from(
                new CocoEncryptedRequest("app-1", "key-1", "iv-1", "AES-GCM", true, encryptedPayload),
                transportSnapshot, metadata);
        AtomicInteger resolveCalls = new AtomicInteger();
        CocoWebRequestContextResolver resolver = (traceId, request) ->
                resolveCalls.getAndIncrement() == 0 ? transportSnapshot : effectiveSnapshot;
        CocoEncryptionFilter filter = new CocoEncryptionFilter(
                properties,
                request -> Optional.of(new CocoEncryptionKey(request.appId(), request.keyId(),
                        "1234567890123456".getBytes(StandardCharsets.UTF_8))),
                context -> "plain".getBytes(StandardCharsets.UTF_8),
                resolver,
                exceptionWriter(),
                input -> metadata,
                (request, rules) -> false);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.setContent(encryptedPayload);
        request.addHeader(properties.getEncryptedHeaderName(), "true");
        AtomicReference<CocoRequestContext> context = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), capturingChain(context));

        CocoRequestContext requestContext = context.get();
        assertTrue(requestContext.requestDecrypted());
        assertTrue(requestContext.signatureVerified());
        assertEquals(Optional.of(CocoWebSecurityMetadataSource.HEADER.name()),
                requestContext.encryptionMetadataSource());
        assertEquals(Optional.of("coco.web.encryption.v1"),
                requestContext.encryptionAssociatedDataVersion());
        assertEquals(Optional.of(sha256(expectedAssociatedData)),
                requestContext.encryptionAssociatedDataSha256());
        assertEquals(Optional.empty(), requestContext.encryptionAppId());
    }

    @Test
    void replayFilterWritesReservationEvidenceToCurrentRequestContext() throws Exception {
        CocoReplayProperties properties = new CocoReplayProperties();
        properties.setRequired(true);
        properties.setTtlSeconds(120);
        CocoReplayKey replayKey = new CocoReplayKey("app-1", "key-1", FIXED_NOW.toString(),
                "nonce-1", "POST", "/api/orders");
        CocoWebRequestSecurityMetadata metadata = replayMetadata();
        CocoReplayFilter filter = new CocoReplayFilter(
                properties,
                (key, expiresAt) -> true,
                (snapshot, resolvedMetadata) -> replayKey,
                (traceId, request) -> baseSnapshot(traceId)
                        .withContextAttribute(CocoRequestContextAttributes.REQUEST_DECRYPTED, "true"),
                input -> metadata,
                exceptionWriter(),
                (request, rules) -> false,
                FIXED_CLOCK);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        AtomicReference<CocoRequestContext> context = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), capturingChain(context));

        CocoRequestContext requestContext = context.get();
        assertTrue(requestContext.replayReserved());
        assertTrue(requestContext.requestDecrypted());
        assertEquals(Optional.of(FIXED_NOW.plus(Duration.ofSeconds(120)).toString()),
                requestContext.replayExpiresAt());
        assertEquals(Optional.of(120L), requestContext.replayWindowSeconds());
        assertEquals(Optional.of(sha256(replayKey.value())), requestContext.replayKeySha256());
        assertEquals(Optional.of(CocoWebSecurityMetadataSource.HEADER.name()),
                requestContext.replayMetadataSource());
        assertEquals(Optional.empty(), requestContext.replayAppId());
    }

    private static FilterChain capturingChain(AtomicReference<CocoRequestContext> context) {
        return (request, response) -> context.set(CocoRequestContextHolder.current().orElseThrow());
    }

    private static CocoWebRequestSnapshot baseSnapshot(String traceId) {
        return new CocoWebRequestSnapshot(traceId, "POST", "/api/orders", null, "127.0.0.1",
                null, null, "http", "localhost", 80, null, Map.of(), Map.of())
                .withContextAttribute(CocoRequestContextAttributes.CLIENT_IP, "127.0.0.1");
    }

    private static CocoWebRequestSecurityMetadata signatureMetadata() {
        return new CocoWebRequestSecurityMetadata("app-1", "key-1", FIXED_NOW.toString(), "nonce-1",
                "HMAC-SHA256", "signature-value", true, null, null, null, null, false,
                null, null, null, null);
    }

    private static CocoWebRequestSecurityMetadata encryptionMetadata() {
        return new CocoWebRequestSecurityMetadata(null, null, null, null, null, null, false,
                "app-1", "key-1", "iv-1", "AES-GCM", true, null, null, null, null);
    }

    private static CocoWebRequestSecurityMetadata replayMetadata() {
        return new CocoWebRequestSecurityMetadata(null, null, null, null, null, null, false,
                null, null, null, null, false, "app-1", "key-1", FIXED_NOW.toString(), "nonce-1");
    }

    private static CocoFilterExceptionResponseWriter exceptionWriter() {
        CocoExceptionHttpStatusResolver statusResolver = exception -> HttpStatus.UNAUTHORIZED;
        return new CocoFilterExceptionResponseWriter(new CocoWebExceptionHandler(new StaticMessageService(),
                statusResolver, CocoSystemCodes.defaults()), new ObjectMapper());
    }

    private static String sha256(String content) {
        return sha256((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content == null ? new byte[0] : content));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private static final class StaticMessageService implements CocoMessageService {

        @Override
        public String getMessage(String code, Object... args) {
            return code;
        }

        @Override
        public String getMessage(String code, Locale locale, Object... args) {
            return code;
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Object... args) {
            return defaultMessage == null ? code : defaultMessage;
        }

        @Override
        public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) {
            return defaultMessage == null ? code : defaultMessage;
        }

        @Override
        public String resolve(CocoMessage message) {
            return message == null ? "" : message.code();
        }

        @Override
        public String resolve(CocoMessage message, Locale locale) {
            return resolve(message);
        }
    }
}
