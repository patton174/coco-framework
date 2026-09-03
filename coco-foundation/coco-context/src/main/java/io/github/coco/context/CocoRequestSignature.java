package io.github.coco.context;

import java.util.Map;
import java.util.Optional;

/**
 * 请求签名视图。
 * <p>
 * 提供请求签名应用标识、密钥、算法、验签状态等信息的结构化访问，
 * 属性来源于 {@link CocoRequestContext} 的内部属性快照。
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
public final class CocoRequestSignature {

    private final Map<String, String> attributes;

    CocoRequestSignature(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    /**
     * <p>
     * 返回签名应用标识。
     * </p>
     * @return 签名应用标识；未设置时为空
     */
    public Optional<String> appId() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.SIGNATURE_APP_ID);
    }

    /**
     * <p>
     * 返回签名密钥标识。
     * </p>
     * @return 签名密钥标识；未设置时为空
     */
    public Optional<String> keyId() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.SIGNATURE_KEY_ID);
    }

    /**
     * <p>
     * 返回签名时间戳。
     * </p>
     * @return 签名时间戳；未设置时为空
     */
    public Optional<String> timestamp() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.SIGNATURE_TIMESTAMP);
    }

    /**
     * <p>
     * 返回签名随机串。
     * </p>
     * @return 签名随机串；未设置时为空
     */
    public Optional<String> nonce() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.SIGNATURE_NONCE);
    }

    /**
     * <p>
     * 返回签名值。
     * </p>
     * @return 签名值；未设置时为空
     */
    public Optional<String> value() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.SIGNATURE_VALUE);
    }

    /**
     * <p>
     * 返回请求是否已签名。
     * </p>
     * @return 已签名时返回 {@code true}
     */
    public boolean signed() {
        return CocoRequestContextAttributeParser.booleanAttribute(this.attributes,
                CocoRequestContextAttributes.REQUEST_SIGNED);
    }

    /**
     * <p>
     * 返回请求签名算法。
     * </p>
     * @return 请求签名算法；未设置时为空
     */
    public Optional<String> algorithm() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.SIGNATURE_ALGORITHM);
    }

    /**
     * <p>
     * 返回请求签名元数据来源。
     * </p>
     * @return 请求签名元数据来源；未设置时为空
     */
    public Optional<String> metadataSource() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.SIGNATURE_METADATA_SOURCE);
    }

    /**
     * <p>
     * 返回请求签名是否已验证通过。
     * </p>
     * @return 验签通过时返回 {@code true}
     */
    public boolean verified() {
        return CocoRequestContextAttributeParser.booleanAttribute(this.attributes,
                CocoRequestContextAttributes.SIGNATURE_VERIFIED);
    }

    /**
     * <p>
     * 返回请求签名验证完成时间。
     * </p>
     * @return 请求签名验证完成时间；未设置时为空
     */
    public Optional<String> verifiedAt() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.SIGNATURE_VERIFIED_AT);
    }

    /**
     * <p>
     * 返回请求签名规范化文本 SHA-256 摘要。
     * </p>
     * @return 请求签名规范化文本 SHA-256 摘要；未设置时为空
     */
    public Optional<String> canonicalSha256() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.SIGNATURE_CANONICAL_SHA256);
    }
}
