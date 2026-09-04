package io.github.coco.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static java.util.Map.entry;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 请求上下文视图对象测试。
 * <p>
 * 验证每个视图对象的访问器与 {@link CocoRequestContext} 上对应的扁平访问器返回相同值。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-context}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
class CocoRequestContextViewTest {

    private static CocoRequestContext fullContext() {
        return CocoRequestContext.of("trace-view", "POST", "/api/secure", Map.ofEntries(
                entry(CocoRequestContextAttributes.CLIENT_IP, "10.0.0.1"),
                entry(CocoRequestContextAttributes.CLIENT_IP_SOURCE, "FORWARDED_HEADER"),
                entry(CocoRequestContextAttributes.CLIENT_IP_SOURCE_HEADER, "X-Forwarded-For"),
                entry(CocoRequestContextAttributes.CLIENT_IP_SOURCE_HEADER_VALUE, "10.0.0.1, 10.0.0.2"),
                entry(CocoRequestContextAttributes.CLIENT_IP_REMOTE_ADDRESS, "127.0.0.1"),
                entry(CocoRequestContextAttributes.CLIENT_IP_TRUSTED_PROXY, "true"),
                entry(CocoRequestContextAttributes.CLIENT_IP_CHAIN, "10.0.0.1,10.0.0.2"),
                entry(CocoRequestContextAttributes.CLIENT_IP_RESOLVED_CHAIN_INDEX, "0"),
                entry(CocoRequestContextAttributes.REQUEST_SIGNED, "true"),
                entry(CocoRequestContextAttributes.SIGNATURE_ALGORITHM, "HMAC-SHA256"),
                entry(CocoRequestContextAttributes.SIGNATURE_METADATA_SOURCE, "header"),
                entry(CocoRequestContextAttributes.SIGNATURE_VERIFIED, "true"),
                entry(CocoRequestContextAttributes.SIGNATURE_VERIFIED_AT, "2026-01-01T00:00:00Z"),
                entry(CocoRequestContextAttributes.SIGNATURE_CANONICAL_SHA256, "canonical-sha"),
                entry(CocoRequestContextAttributes.REQUEST_ENCRYPTED, "true"),
                entry(CocoRequestContextAttributes.ENCRYPTION_ALGORITHM, "AES-GCM"),
                entry(CocoRequestContextAttributes.ENCRYPTION_METADATA_SOURCE, "header"),
                entry(CocoRequestContextAttributes.REQUEST_DECRYPTED, "true"),
                entry(CocoRequestContextAttributes.ENCRYPTION_ASSOCIATED_DATA_VERSION, "v1"),
                entry(CocoRequestContextAttributes.ENCRYPTION_ASSOCIATED_DATA_SHA256, "aad-sha"),
                entry(CocoRequestContextAttributes.REQUEST_REPLAY_PROTECTED, "true"),
                entry(CocoRequestContextAttributes.REPLAY_METADATA_SOURCE, "header"),
                entry(CocoRequestContextAttributes.REPLAY_RESERVED, "true"),
                entry(CocoRequestContextAttributes.REPLAY_EXPIRES_AT, "2026-01-02T00:00:00Z"),
                entry(CocoRequestContextAttributes.REPLAY_WINDOW_SECONDS, "300"),
                entry(CocoRequestContextAttributes.REPLAY_KEY_SHA256, "replay-key-sha"),
                entry(CocoRequestContextAttributes.REQUEST_BODY_SHA256, "body-sha"),
                entry(CocoRequestContextAttributes.REQUEST_BODY_TRANSPORT_SHA256, "transport-sha"),
                entry(CocoRequestContextAttributes.REQUEST_BODY_EFFECTIVE_SHA256, "effective-sha"),
                entry(CocoRequestContextAttributes.REQUEST_BODY_TRANSPORT_LENGTH, "512"),
                entry(CocoRequestContextAttributes.REQUEST_BODY_EFFECTIVE_LENGTH, "256"),
                entry(CocoRequestContextAttributes.REQUEST_BODY_STAGE, "decrypted"),
                entry(CocoRequestContextAttributes.BROWSER_FINGERPRINT, "fp-abc"),
                entry(CocoRequestContextAttributes.browserFingerprintSignal("Sec-CH-UA"), "Chromium")));
    }

    @Test
    void clientIpViewMatchesFlatAccessors() {
        CocoRequestContext ctx = fullContext();
        CocoRequestClientIp view = ctx.clientIpInfo();

        assertEquals(ctx.clientIp(), view.ip());
        assertEquals(ctx.clientIpSource(), view.source());
        assertEquals(ctx.clientIpSourceHeader(), view.sourceHeader());
        assertEquals(ctx.clientIpSourceHeaderValue(), view.sourceHeaderValue());
        assertEquals(ctx.clientIpRemoteAddress(), view.remoteAddress());
        assertEquals(ctx.clientIpTrustedProxy(), view.trustedProxy());
        assertEquals(ctx.clientIpChain(), view.chain().orElseGet(List::of));
        assertEquals(ctx.clientIpResolvedChainIndex(), view.resolvedChainIndex());
    }

    @Test
    void signatureViewMatchesFlatAccessors() {
        CocoRequestContext ctx = fullContext();
        CocoRequestSignature view = ctx.signatureInfo();

        assertEquals(ctx.signatureAppId(), view.appId());
        assertEquals(ctx.signatureKeyId(), view.keyId());
        assertEquals(ctx.signatureTimestamp(), view.timestamp());
        assertEquals(ctx.signatureNonce(), view.nonce());
        assertEquals(ctx.signatureValue(), view.value());
        assertEquals(ctx.requestSigned(), view.signed());
        assertEquals(ctx.signatureAlgorithm(), view.algorithm());
        assertEquals(ctx.signatureMetadataSource(), view.metadataSource());
        assertEquals(ctx.signatureVerified(), view.verified());
        assertEquals(ctx.signatureVerifiedAt(), view.verifiedAt());
        assertEquals(ctx.signatureCanonicalSha256(), view.canonicalSha256());
    }

    @Test
    void encryptionViewMatchesFlatAccessors() {
        CocoRequestContext ctx = fullContext();
        CocoRequestEncryption view = ctx.encryptionInfo();

        assertEquals(ctx.requestEncrypted(), view.encrypted());
        assertEquals(ctx.encryptionAlgorithm(), view.algorithm());
        assertEquals(ctx.encryptionMetadataSource(), view.metadataSource());
        assertEquals(ctx.requestDecrypted(), view.decrypted());
        assertEquals(ctx.encryptionAssociatedDataVersion(), view.associatedDataVersion());
        assertEquals(ctx.encryptionAssociatedDataSha256(), view.associatedDataSha256());
        assertEquals(ctx.encryptionAppId(), view.appId());
        assertEquals(ctx.encryptionKeyId(), view.keyId());
        assertEquals(ctx.encryptionIv(), view.iv());
    }

    @Test
    void replayViewMatchesFlatAccessors() {
        CocoRequestContext ctx = fullContext();
        CocoRequestReplay view = ctx.replayInfo();

        assertEquals(ctx.requestReplayProtected(), view.replayProtected());
        assertEquals(ctx.replayAppId(), view.appId());
        assertEquals(ctx.replayKeyId(), view.keyId());
        assertEquals(ctx.replayTimestamp(), view.timestamp());
        assertEquals(ctx.replayNonce(), view.nonce());
        assertEquals(ctx.replayMetadataSource(), view.metadataSource());
        assertEquals(ctx.replayReserved(), view.reserved());
        assertEquals(ctx.replayExpiresAt(), view.expiresAt());
        assertEquals(ctx.replayWindowSeconds(), view.windowSeconds());
        assertEquals(ctx.replayKeySha256(), view.keySha256());
    }

    @Test
    void bodyViewMatchesFlatAccessors() {
        CocoRequestContext ctx = fullContext();
        CocoRequestBody view = ctx.bodyInfo();

        assertEquals(ctx.requestBodySha256(), view.sha256());
        assertEquals(ctx.requestBodyTransportSha256(), view.transportSha256());
        assertEquals(ctx.requestBodyEffectiveSha256(), view.effectiveSha256());
        assertEquals(ctx.requestBodyTransportLength(), view.transportLength());
        assertEquals(ctx.requestBodyEffectiveLength(), view.effectiveLength());
        assertEquals(ctx.requestBodyStage(), view.stage());
    }

    @Test
    void bodyViewEffectiveSha256FallsBackToSha256() {
        CocoRequestContext ctx = CocoRequestContext.of("trace-fallback", "POST", "/api", Map.of(
                CocoRequestContextAttributes.REQUEST_BODY_SHA256, "fallback-sha"));
        CocoRequestBody view = ctx.bodyInfo();

        assertEquals(ctx.requestBodyEffectiveSha256(), view.effectiveSha256());
        assertEquals("fallback-sha", view.effectiveSha256().orElseThrow());
    }

    @Test
    void browserFingerprintViewMatchesFlatAccessors() {
        CocoRequestContext ctx = fullContext();
        CocoRequestBrowserFingerprint view = ctx.browserFingerprintInfo();

        assertEquals(ctx.browserFingerprint(), view.value());
        assertEquals(ctx.browserFingerprintSignals(), view.signals());
        assertEquals(ctx.browserFingerprintSignal("sec-ch-ua"), view.signal("sec-ch-ua"));
    }

    @Test
    void viewsReturnEmptyForMissingAttributes() {
        CocoRequestContext ctx = CocoRequestContext.of("trace-empty");
        CocoRequestClientIp clientIp = ctx.clientIpInfo();
        CocoRequestSignature signature = ctx.signatureInfo();
        CocoRequestEncryption encryption = ctx.encryptionInfo();
        CocoRequestReplay replay = ctx.replayInfo();
        CocoRequestBody body = ctx.bodyInfo();
        CocoRequestBrowserFingerprint fingerprint = ctx.browserFingerprintInfo();

        assertTrue(clientIp.ip().isEmpty());
        assertFalse(clientIp.trustedProxy());
        assertTrue(clientIp.chain().isEmpty());

        assertTrue(signature.appId().isEmpty());
        assertFalse(signature.signed());
        assertFalse(signature.verified());

        assertTrue(encryption.algorithm().isEmpty());
        assertFalse(encryption.encrypted());
        assertFalse(encryption.decrypted());

        assertFalse(replay.replayProtected());
        assertFalse(replay.reserved());
        assertTrue(replay.windowSeconds().isEmpty());

        assertTrue(body.sha256().isEmpty());
        assertTrue(body.effectiveSha256().isEmpty());
        assertTrue(body.transportLength().isEmpty());

        assertTrue(fingerprint.value().isEmpty());
        assertEquals(Map.of(), fingerprint.signals());
        assertTrue(fingerprint.signal("anything").isEmpty());
    }

    @Test
    void existingFlatAccessorsRemainUnchanged() {
        CocoRequestContext ctx = fullContext();

        assertEquals("10.0.0.1", ctx.clientIp().orElseThrow());
        assertTrue(ctx.clientIpTrustedProxy());
        assertEquals(List.of("10.0.0.1", "10.0.0.2"), ctx.clientIpChain());
        assertEquals(0, ctx.clientIpResolvedChainIndex().orElseThrow());
        assertTrue(ctx.requestSigned());
        assertEquals("HMAC-SHA256", ctx.signatureAlgorithm().orElseThrow());
        assertTrue(ctx.signatureVerified());
        assertTrue(ctx.requestEncrypted());
        assertEquals("AES-GCM", ctx.encryptionAlgorithm().orElseThrow());
        assertTrue(ctx.requestDecrypted());
        assertTrue(ctx.requestReplayProtected());
        assertTrue(ctx.replayReserved());
        assertEquals(300L, ctx.replayWindowSeconds().orElseThrow());
        assertEquals("body-sha", ctx.requestBodySha256().orElseThrow());
        assertEquals(512L, ctx.requestBodyTransportLength().orElseThrow());
        assertEquals("fp-abc", ctx.browserFingerprint().orElseThrow());
    }
}
