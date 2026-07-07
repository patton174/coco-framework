package io.github.coco.feature.web.signature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 Coco 请求签名验证器。
 * <p>
 * 使用共享密钥对规范化请求文本计算 HMAC-SHA256，支持十六进制和 Base64 两种签名文本格式。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class HmacSha256CocoSignatureVerifier implements CocoSignatureVerifier {

    private static final String ALGORITHM_NAME = "HMAC-SHA256";

    private static final String JCA_ALGORITHM_NAME = "HmacSHA256";

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verify(CocoSignatureVerificationContext context) {
        CocoSignatureVerificationContext checkedContext = Objects.requireNonNull(context,
                "context must not be null");
        CocoSignatureRequest request = checkedContext.request();
        if (!supports(request.algorithm())) {
            return false;
        }
        byte[] signature = hmacSha256(request.canonicalText(), checkedContext.secret().value());
        String expectedHex = HexFormat.of().formatHex(signature);
        String expectedBase64 = Base64.getEncoder().encodeToString(signature);
        return constantTimeEquals(request.signature(), expectedHex)
                || constantTimeEquals(request.signature(), expectedBase64);
    }

    private static boolean supports(String algorithm) {
        return algorithm != null && ALGORITHM_NAME.equals(normalizeAlgorithm(algorithm));
    }

    private static String normalizeAlgorithm(String algorithm) {
        return algorithm.trim()
                .toUpperCase(Locale.ROOT)
                .replace("_", "-")
                .replace("HMACSHA", "HMAC-SHA")
                .trim();
    }

    private static byte[] hmacSha256(String canonicalText, String secret) {
        try {
            Mac mac = Mac.getInstance(JCA_ALGORITHM_NAME);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), JCA_ALGORITHM_NAME));
            return mac.doFinal(canonicalText.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("HMAC-SHA256 algorithm is not available", ex);
        }
        catch (java.security.InvalidKeyException ex) {
            throw new IllegalArgumentException("signature secret is invalid", ex);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.trim().getBytes(StandardCharsets.UTF_8),
                right.trim().getBytes(StandardCharsets.UTF_8));
    }
}
