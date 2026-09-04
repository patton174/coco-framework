package io.github.coco.feature.web.signature;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/**
 * {@link HmacSha256CocoSignatureVerifier} 单元测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class HmacSha256CocoSignatureVerifierTest {

    private static final String SECRET = "test-secret";

    private final HmacSha256CocoSignatureVerifier verifier = new HmacSha256CocoSignatureVerifier();

    @Test
    void verifyReturnsTrueForValidHexSignature() throws Exception {
        String canonicalText = "POST\n/api/orders\ntimestamp=1735689600";
        String hexSignature = HexFormat.of().formatHex(hmacSha256(canonicalText, SECRET));
        CocoSignatureRequest request = new CocoSignatureRequest("app-1", "key-1", "1735689600",
                "nonce-1", "HMAC-SHA256", hexSignature, canonicalText, null);

        assertTrue(verifier.verify(new CocoSignatureVerificationContext(request,
                new CocoSignatureSecret("app-1", "key-1", SECRET))));
    }

    @Test
    void verifyReturnsTrueForValidBase64Signature() throws Exception {
        String canonicalText = "POST\n/api/orders\ntimestamp=1735689600";
        String base64Signature = Base64.getEncoder().encodeToString(hmacSha256(canonicalText, SECRET));
        CocoSignatureRequest request = new CocoSignatureRequest("app-1", "key-1", "1735689600",
                "nonce-1", "HMAC-SHA256", base64Signature, canonicalText, null);

        assertTrue(verifier.verify(new CocoSignatureVerificationContext(request,
                new CocoSignatureSecret("app-1", "key-1", SECRET))));
    }

    @Test
    void verifyReturnsFalseForWrongSignature() {
        String canonicalText = "POST\n/api/orders\ntimestamp=1735689600";
        CocoSignatureRequest request = new CocoSignatureRequest("app-1", "key-1", "1735689600",
                "nonce-1", "HMAC-SHA256", "wrong-signature", canonicalText, null);

        assertFalse(verifier.verify(new CocoSignatureVerificationContext(request,
                new CocoSignatureSecret("app-1", "key-1", SECRET))));
    }

    @Test
    void verifyReturnsFalseForUnsupportedAlgorithm() {
        String canonicalText = "POST\n/api/orders";
        CocoSignatureRequest request = new CocoSignatureRequest("app-1", "key-1", null, null,
                "RSA-SHA256", "some-signature", canonicalText, null);

        assertFalse(verifier.verify(new CocoSignatureVerificationContext(request,
                new CocoSignatureSecret("app-1", "key-1", SECRET))));
    }

    @Test
    void verifyAcceptsNormalizedAlgorithmNames() throws Exception {
        String canonicalText = "GET\n/api/users";
        for (String algorithm : new String[]{"hmacsha256", "hmac-sha256"}) {
            String hexSignature = HexFormat.of().formatHex(hmacSha256(canonicalText, SECRET));
            CocoSignatureRequest request = new CocoSignatureRequest("app-1", "key-1", null, null,
                    algorithm, hexSignature, canonicalText, null);

            assertTrue(verifier.verify(new CocoSignatureVerificationContext(request,
                    new CocoSignatureSecret("app-1", "key-1", SECRET))),
                    "should accept algorithm: " + algorithm);
        }
    }

    private static byte[] hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }
}
