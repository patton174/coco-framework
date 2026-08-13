package io.github.coco.feature.web.signature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;


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
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class HmacSha256CocoSignatureVerifier implements CocoSignatureVerifier {

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
        String expectedHex = HmacSha256CocoSignatureSigner.sign(request.algorithm(), request.canonicalText(),
                checkedContext.secret().value());
        String expectedBase64 = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(expectedHex));
        return constantTimeEquals(request.signature(), expectedHex)
                || constantTimeEquals(request.signature(), expectedBase64);
    }

    private static boolean supports(String algorithm) {
        return HmacSha256CocoSignatureSigner.supports(algorithm);
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.trim().getBytes(StandardCharsets.UTF_8),
                right.trim().getBytes(StandardCharsets.UTF_8));
    }
}
