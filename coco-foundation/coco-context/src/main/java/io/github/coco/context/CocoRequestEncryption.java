package io.github.coco.context;

import java.util.Map;
import java.util.Optional;

/**
 * 请求加密视图。
 * <p>
 * 提供请求加密算法、解密状态、AAD 等信息的结构化访问，
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
public final class CocoRequestEncryption {

    private final Map<String, String> attributes;

    CocoRequestEncryption(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    /**
     * <p>
     * 返回请求是否已加密。
     * </p>
     * @return 已加密时返回 {@code true}
     */
    public boolean encrypted() {
        return CocoRequestContextAttributeParser.booleanAttribute(this.attributes,
                CocoRequestContextAttributes.REQUEST_ENCRYPTED);
    }

    /**
     * <p>
     * 返回请求加密算法。
     * </p>
     * @return 请求加密算法；未设置时为空
     */
    public Optional<String> algorithm() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.ENCRYPTION_ALGORITHM);
    }

    /**
     * <p>
     * 返回请求加密元数据来源。
     * </p>
     * @return 请求加密元数据来源；未设置时为空
     */
    public Optional<String> metadataSource() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.ENCRYPTION_METADATA_SOURCE);
    }

    /**
     * <p>
     * 返回请求是否已成功解密。
     * </p>
     * @return 解密成功时返回 {@code true}
     */
    public boolean decrypted() {
        return CocoRequestContextAttributeParser.booleanAttribute(this.attributes,
                CocoRequestContextAttributes.REQUEST_DECRYPTED);
    }

    /**
     * <p>
     * 返回请求加密 AAD 版本。
     * </p>
     * @return 请求加密 AAD 版本；未设置时为空
     */
    public Optional<String> associatedDataVersion() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.ENCRYPTION_ASSOCIATED_DATA_VERSION);
    }

    /**
     * <p>
     * 返回请求加密 AAD SHA-256 摘要。
     * </p>
     * @return 请求加密 AAD SHA-256 摘要；未设置时为空
     */
    public Optional<String> associatedDataSha256() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.ENCRYPTION_ASSOCIATED_DATA_SHA256);
    }

    /**
     * <p>
     * 返回加密应用标识。
     * </p>
     * @return 加密应用标识；未设置时为空
     */
    public Optional<String> appId() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.ENCRYPTION_APP_ID);
    }

    /**
     * <p>
     * 返回加密密钥标识。
     * </p>
     * @return 加密密钥标识；未设置时为空
     */
    public Optional<String> keyId() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.ENCRYPTION_KEY_ID);
    }

    /**
     * <p>
     * 返回加密初始向量。
     * </p>
     * @return 加密初始向量；未设置时为空
     */
    public Optional<String> iv() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.ENCRYPTION_IV);
    }
}
