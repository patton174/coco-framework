package io.github.coco.feature.web.request.metadata;

import java.util.Objects;
import java.util.Optional;

import io.github.coco.feature.web.encryption.CocoEncryptionProperties;
import io.github.coco.feature.web.replay.CocoReplayProperties;
import io.github.coco.feature.web.signature.CocoSignatureProperties;

/**
 * 默认 Coco Web 请求安全元数据解析器�? * <p>
 * �?Web 模块签名、加密和防重放配置中的请求头名称，从安全输入中解析签名材料、加密材料和防重放材料�? * </p>
 * <p>
 * 项目信息�? * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库�?a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoWebRequestSecurityMetadataResolver
        implements CocoWebRequestSecurityMetadataResolver {

    private final CocoSignatureProperties signatureProperties;

    private final CocoEncryptionProperties encryptionProperties;

    private final CocoReplayProperties replayProperties;

    /**
     * <p>
     * 创建默认请求安全元数据解析器�?     * </p>
     * @param signatureProperties 请求签名配置
     * @param encryptionProperties 请求加密配置
     */
    public DefaultCocoWebRequestSecurityMetadataResolver(CocoSignatureProperties signatureProperties,
            CocoEncryptionProperties encryptionProperties) {
        this(signatureProperties, encryptionProperties, null);
    }

    /**
     * <p>
     * 创建默认请求安全元数据解析器�?     * </p>
     * @param signatureProperties 请求签名配置
     * @param encryptionProperties 请求加密配置
     * @param replayProperties 请求防重放配�?     */
    public DefaultCocoWebRequestSecurityMetadataResolver(CocoSignatureProperties signatureProperties,
            CocoEncryptionProperties encryptionProperties, CocoReplayProperties replayProperties) {
        this.signatureProperties = signatureProperties == null ? new CocoSignatureProperties() : signatureProperties;
        this.encryptionProperties = encryptionProperties == null ? new CocoEncryptionProperties() : encryptionProperties;
        this.replayProperties = replayProperties == null ? new CocoReplayProperties() : replayProperties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoWebRequestSecurityMetadata resolve(CocoWebRequestSecurityInput input) {
        CocoWebRequestSecurityInput checkedInput = Objects.requireNonNull(input, "input must not be null");
        String signature = signatureHeader(checkedInput).orElse(null);
        boolean signed = signature != null;
        boolean encrypted = encrypted(read(checkedInput, this.encryptionProperties.getMetadataSource(),
                this.encryptionProperties.getEncryptedHeaderName(),
                this.encryptionProperties.getEncryptedParameterName()).orElse(null));
        return new CocoWebRequestSecurityMetadata(
                read(checkedInput, this.signatureProperties.getMetadataSource(),
                        this.signatureProperties.getAppIdHeaderName(),
                        this.signatureProperties.getAppIdParameterName()).orElse(null),
                read(checkedInput, this.signatureProperties.getMetadataSource(),
                        this.signatureProperties.getKeyIdHeaderName(),
                        this.signatureProperties.getKeyIdParameterName()).orElse(null),
                read(checkedInput, this.signatureProperties.getMetadataSource(),
                        this.signatureProperties.getTimestampHeaderName(),
                        this.signatureProperties.getTimestampParameterName()).orElse(null),
                read(checkedInput, this.signatureProperties.getMetadataSource(),
                        this.signatureProperties.getNonceHeaderName(),
                        this.signatureProperties.getNonceParameterName()).orElse(null),
                signatureAlgorithm(checkedInput),
                signature,
                signed,
                read(checkedInput, this.encryptionProperties.getMetadataSource(),
                        this.encryptionProperties.getAppIdHeaderName(),
                        this.encryptionProperties.getAppIdParameterName()).orElse(null),
                read(checkedInput, this.encryptionProperties.getMetadataSource(),
                        this.encryptionProperties.getKeyIdHeaderName(),
                        this.encryptionProperties.getKeyIdParameterName()).orElse(null),
                read(checkedInput, this.encryptionProperties.getMetadataSource(),
                        this.encryptionProperties.getIvHeaderName(),
                        this.encryptionProperties.getIvParameterName()).orElse(null),
                encryptionAlgorithm(checkedInput),
                encrypted,
                read(checkedInput, this.replayProperties.getMetadataSource(),
                        this.replayProperties.getAppIdHeaderName(),
                        this.replayProperties.getAppIdParameterName()).orElse(null),
                read(checkedInput, this.replayProperties.getMetadataSource(),
                        this.replayProperties.getKeyIdHeaderName(),
                        this.replayProperties.getKeyIdParameterName()).orElse(null),
                read(checkedInput, this.replayProperties.getMetadataSource(),
                        this.replayProperties.getTimestampHeaderName(),
                        this.replayProperties.getTimestampParameterName()).orElse(null),
                read(checkedInput, this.replayProperties.getMetadataSource(),
                        this.replayProperties.getNonceHeaderName(),
                        this.replayProperties.getNonceParameterName()).orElse(null));
    }

    private Optional<String> signatureHeader(CocoWebRequestSecurityInput input) {
        CocoWebSecurityMetadataSource source = this.signatureProperties.getMetadataSource();
        return read(input, source, this.signatureProperties.getSignatureHeaderName(),
                this.signatureProperties.getSignatureParameterName())
                .or(() -> read(input, source, this.signatureProperties.getSignatureFallbackHeaderName(),
                        this.signatureProperties.getSignatureFallbackParameterName()));
    }

    private String signatureAlgorithm(CocoWebRequestSecurityInput input) {
        return read(input, this.signatureProperties.getMetadataSource(),
                this.signatureProperties.getAlgorithmHeaderName(),
                this.signatureProperties.getAlgorithmParameterName())
                .orElse(null);
    }

    private String encryptionAlgorithm(CocoWebRequestSecurityInput input) {
        return read(input, this.encryptionProperties.getMetadataSource(),
                this.encryptionProperties.getAlgorithmHeaderName(),
                this.encryptionProperties.getAlgorithmParameterName())
                .orElse(null);
    }

    private static Optional<String> read(CocoWebRequestSecurityInput input, CocoWebSecurityMetadataSource source,
            String headerName, String parameterName) {
        CocoWebSecurityMetadataSource metadataSource = source == null ? CocoWebSecurityMetadataSource.HEADER : source;
        Optional<String> headerValue = metadataSource.supportsHeader()
                ? input.securityHeader(headerName)
                : Optional.empty();
        Optional<String> parameterValue = metadataSource.supportsParameter()
                ? parameter(input, parameterName)
                : Optional.empty();
        return metadataSource.parameterFirst()
                ? parameterValue.or(() -> headerValue)
                : headerValue.or(() -> parameterValue);
    }

    private static Optional<String> parameter(CocoWebRequestSecurityInput input, String parameterName) {
        Optional<String> queryValue = firstNonBlank(input.queryParameter(parameterName));
        if (queryValue.isPresent()) {
            return queryValue;
        }
        Optional<String> payloadValue = firstNonBlank(input.payloadParameter(parameterName));
        if (payloadValue.isPresent()) {
            return payloadValue;
        }
        return firstNonBlank(input.parameter(parameterName));
    }

    private static Optional<String> firstNonBlank(Optional<java.util.List<String>> values) {
        return values.flatMap(parameterValues -> parameterValues.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .findFirst());
    }

    private static boolean encrypted(String value) {
        return value != null && ("true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim()));
    }
}
