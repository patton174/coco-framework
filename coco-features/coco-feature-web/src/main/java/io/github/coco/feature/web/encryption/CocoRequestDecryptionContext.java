package io.github.coco.feature.web.encryption;

import java.util.Objects;

/**
 * Coco 请求解密上下文。
 * <p>
 * 聚合加密请求材料、已解析 AES 密钥和 AES-GCM 附加认证数据，作为请求解密器的稳定输入。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param request 加密请求材料
 * @param key AES 解密密钥
 * @param associatedData AES-GCM 附加认证数据
 * @author patton174
 * @since 1.0.0
 */
public record CocoRequestDecryptionContext(CocoEncryptedRequest request, CocoEncryptionKey key,
        byte[] associatedData) {

    /**
     * <p>
     * 创建请求解密上下文。
     * </p>
     * @param request 加密请求材料
     * @param key AES 解密密钥
     */
    public CocoRequestDecryptionContext(CocoEncryptedRequest request, CocoEncryptionKey key) {
        this(request, key, CocoEncryptionAssociatedData.from(request));
    }

    /**
     * <p>
     * 创建请求解密上下文。
     * </p>
     * @param request 加密请求材料
     * @param key AES 解密密钥
     * @param associatedData AES-GCM 附加认证数据
     */
    public CocoRequestDecryptionContext {
        request = Objects.requireNonNull(request, "request must not be null");
        key = Objects.requireNonNull(key, "key must not be null");
        associatedData = associatedData == null ? new byte[0] : associatedData.clone();
    }

    /**
     * <p>
     * 返回 AES-GCM AAD 副本。
     * </p>
     * @return AES-GCM AAD 副本
     */
    @Override
    public byte[] associatedData() {
        return this.associatedData.clone();
    }
}
