package io.github.coco.feature.web.signature;

/**
 * Coco 请求签名密钥。
 * <p>
 * 表示业务应用、可选密钥标识和对应的共享密钥，供签名验证器计算期望签名。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param appId 应用标识
 * @param keyId 密钥标识
 * @param value 共享密钥
 * @author patton174
 * @since 1.0.0
 */
public record CocoSignatureSecret(String appId, String keyId, String value) {

    /**
     * <p>
     * 创建请求签名密钥。
     * </p>
     * @param appId 应用标识
     * @param keyId 密钥标识
     * @param value 共享密钥
     */
    public CocoSignatureSecret {
        appId = normalizeOptional(appId);
        keyId = normalizeOptional(keyId);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("signature secret must not be blank");
        }
        value = value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
