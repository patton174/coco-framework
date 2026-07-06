package io.github.coco.feature.web.context;

import java.util.Objects;
import java.util.Optional;

import io.github.coco.feature.web.encryption.CocoEncryptionProperties;
import io.github.coco.feature.web.signature.CocoSignatureProperties;

/**
 * 默认 Coco Web 请求安全元数据解析器。
 * <p>
 * 按 Web 模块 Sign 和 AES 配置中的请求头名称，从安全输入中解析签名材料、加密材料和能力启用标记。
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
public final class DefaultCocoWebRequestSecurityMetadataResolver
        implements CocoWebRequestSecurityMetadataResolver {

    private final CocoSignatureProperties signatureProperties;

    private final CocoEncryptionProperties encryptionProperties;

    /**
     * <p>
     * 创建默认请求安全元数据解析器。
     * </p>
     * @param signatureProperties 请求签名配置
     * @param encryptionProperties 请求加密配置
     */
    public DefaultCocoWebRequestSecurityMetadataResolver(CocoSignatureProperties signatureProperties,
            CocoEncryptionProperties encryptionProperties) {
        this.signatureProperties = signatureProperties == null ? new CocoSignatureProperties() : signatureProperties;
        this.encryptionProperties = encryptionProperties == null ? new CocoEncryptionProperties() : encryptionProperties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebRequestSecurityMetadata resolve(CocoWebRequestSecurityInput input) {
        CocoWebRequestSecurityInput checkedInput = Objects.requireNonNull(input, "input must not be null");
        String signature = signatureHeader(checkedInput).orElse(null);
        boolean signed = signature != null;
        boolean encrypted = encrypted(checkedInput.securityHeader(
                this.encryptionProperties.getEncryptedHeaderName()).orElse(null));
        return new CocoWebRequestSecurityMetadata(
                checkedInput.securityHeader(this.signatureProperties.getAppIdHeaderName()).orElse(null),
                checkedInput.securityHeader(this.signatureProperties.getKeyIdHeaderName()).orElse(null),
                checkedInput.securityHeader(this.signatureProperties.getTimestampHeaderName()).orElse(null),
                checkedInput.securityHeader(this.signatureProperties.getNonceHeaderName()).orElse(null),
                signatureAlgorithm(checkedInput, signed),
                signature,
                signed,
                checkedInput.securityHeader(this.encryptionProperties.getAppIdHeaderName()).orElse(null),
                checkedInput.securityHeader(this.encryptionProperties.getKeyIdHeaderName()).orElse(null),
                checkedInput.securityHeader(this.encryptionProperties.getIvHeaderName()).orElse(null),
                encryptionAlgorithm(checkedInput, encrypted),
                encrypted);
    }

    private Optional<String> signatureHeader(CocoWebRequestSecurityInput input) {
        return input.securityHeader(this.signatureProperties.getSignatureHeaderName())
                .or(() -> input.securityHeader(this.signatureProperties.getSignatureFallbackHeaderName()));
    }

    private String signatureAlgorithm(CocoWebRequestSecurityInput input, boolean signed) {
        return input.securityHeader(this.signatureProperties.getAlgorithmHeaderName())
                .orElse(signed || this.signatureProperties.isRequired()
                        ? this.signatureProperties.getDefaultAlgorithm()
                        : null);
    }

    private String encryptionAlgorithm(CocoWebRequestSecurityInput input, boolean encrypted) {
        return input.securityHeader(this.encryptionProperties.getAlgorithmHeaderName())
                .orElse(encrypted || this.encryptionProperties.isRequired()
                        ? this.encryptionProperties.getDefaultAlgorithm()
                        : null);
    }

    private static boolean encrypted(String value) {
        return value != null && ("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()));
    }
}
