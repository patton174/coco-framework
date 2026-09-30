package io.github.coco.feature.web.encryption;

import java.security.GeneralSecurityException;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-GCM Coco 请求解密器。
 * <p>
 * 使用 AES/GCM/NoPadding 对请求体密文执行认证解密，认证失败时视为解密失败。
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
public final class AesGcmCocoRequestDecryptor implements CocoRequestDecryptor {

    private static final String ALGORITHM_NAME = "AES-GCM";

    private static final String JCA_TRANSFORMATION = "AES/GCM/NoPadding";

    private static final String DECRYPT_FAILED_CODE = "coco.web.encryption.decrypt-failed";

    private static final String MALFORMED_REQUEST_CODE = "coco.web.encryption.malformed-request";

    private static final int BITS_PER_BYTE = Byte.SIZE;

    private final CocoEncryptionProperties properties;

    /**
     * <p>
     * 创建 AES-GCM 请求解密器。
     * </p>
     * @param properties 请求加密配置属性
     */
    public AesGcmCocoRequestDecryptor(CocoEncryptionProperties properties) {
        this.properties = properties == null ? new CocoEncryptionProperties() : properties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] decrypt(CocoRequestDecryptionContext context) {
        CocoRequestDecryptionContext checkedContext = Objects.requireNonNull(context, "context must not be null");
        CocoEncryptedRequest request = checkedContext.request();
        if (!supports(request.algorithm())) {
            throw CocoRequestDecryptException.malformed(MALFORMED_REQUEST_CODE, null);
        }
        byte[] iv = decodeIv(request);
        byte[] payload = decodePayload(request);
        try {
            Cipher cipher = Cipher.getInstance(JCA_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(checkedContext.key().value(), "AES"),
                    new GCMParameterSpec(this.properties.getGcmTagLengthBits(), iv));
            cipher.updateAAD(checkedContext.associatedData());
            return cipher.doFinal(payload);
        }
        catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw CocoRequestDecryptException.authenticationFailed(DECRYPT_FAILED_CODE, ex);
        }
    }

    private byte[] decodeIv(CocoEncryptedRequest request) {
        try {
            byte[] iv = CocoEncryptionCodecs.decode(request.iv(), this.properties.getIvEncoding());
            if (iv.length == 0) {
                throw CocoRequestDecryptException.malformed(MALFORMED_REQUEST_CODE, null);
            }
            return iv;
        }
        catch (IllegalArgumentException ex) {
            throw CocoRequestDecryptException.malformed(MALFORMED_REQUEST_CODE, ex);
        }
    }

    private byte[] decodePayload(CocoEncryptedRequest request) {
        try {
            byte[] payload = CocoEncryptionCodecs.decode(request.payload(), this.properties.getPayloadEncoding());
            if (payload.length < minimumGcmPayloadLengthBytes()) {
                throw CocoRequestDecryptException.malformed(MALFORMED_REQUEST_CODE, null);
            }
            return payload;
        }
        catch (IllegalArgumentException ex) {
            throw CocoRequestDecryptException.malformed(MALFORMED_REQUEST_CODE, ex);
        }
    }

    private int minimumGcmPayloadLengthBytes() {
        return Math.max(1, (this.properties.getGcmTagLengthBits() + BITS_PER_BYTE - 1) / BITS_PER_BYTE);
    }

    private static boolean supports(String algorithm) {
        return algorithm != null && ALGORITHM_NAME.equals(normalizeAlgorithm(algorithm));
    }

    private static String normalizeAlgorithm(String algorithm) {
        return algorithm.trim()
                .toUpperCase(Locale.ROOT)
                .replace("_", "-")
                .replace("/", "-")
                .replace("-GCM-NOPADDING", "-GCM")
                .trim();
    }
}
