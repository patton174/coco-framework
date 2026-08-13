package io.github.coco.feature.web.signature;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 Coco 请求签名计算器。
 *
 * <p>供出站请求签名和入站验签共同使用，避免复制协议计算规则。</p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class HmacSha256CocoSignatureSigner {

    /** Coco HMAC-SHA256 算法名称。 */
    public static final String ALGORITHM = "HMAC-SHA256";

    private static final String JCA_ALGORITHM = "HmacSHA256";

    private HmacSha256CocoSignatureSigner() {
    }

    /**
     * 使用十六进制文本生成请求签名。
     *
     * @param algorithm 协议算法名称
     * @param canonicalText 规范化请求文本
     * @param secret 共享密钥
     * @return 十六进制签名
     */
    public static String sign(String algorithm, String canonicalText, String secret) {
        if (!supports(algorithm)) {
            throw new IllegalArgumentException("unsupported signature algorithm");
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("signature secret must not be blank");
        }
        return HexFormat.of().formatHex(hmac(canonicalText == null ? "" : canonicalText, secret));
    }

    /**
     * 判断是否支持指定算法。
     *
     * @param algorithm 协议算法名称
     * @return 支持时返回 {@code true}
     */
    public static boolean supports(String algorithm) {
        return algorithm != null && ALGORITHM.equals(normalizeAlgorithm(algorithm));
    }

    private static byte[] hmac(String canonicalText, String secret) {
        try {
            Mac mac = Mac.getInstance(JCA_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), JCA_ALGORITHM));
            return mac.doFinal(canonicalText.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("HMAC-SHA256 algorithm is not available", ex);
        }
        catch (InvalidKeyException ex) {
            throw new IllegalArgumentException("signature secret is invalid", ex);
        }
    }

    private static String normalizeAlgorithm(String algorithm) {
        return algorithm.trim().toUpperCase(Locale.ROOT).replace("_", "-")
                .replace("HMACSHA", "HMAC-SHA").trim();
    }
}
