package io.github.coco.web.encryption;

/**
 * Coco AES 解密密钥。
 * <p>
 * 表示业务应用、可选密钥标识和对应的 AES 密钥字节。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @param appId 应用标识
 * @param keyId 密钥标识
 * @param value AES 密钥字节
 * @author patton174
 * @since 1.0.0
 */
public record CocoEncryptionKey(String appId, String keyId, byte[] value) {

    /**
     * <p>
     * 创建 AES 解密密钥。
     * </p>
     * @param appId 应用标识
     * @param keyId 密钥标识
     * @param value AES 密钥字节
     */
    public CocoEncryptionKey {
        appId = normalizeOptional(appId);
        keyId = normalizeOptional(keyId);
        if (value == null || value.length == 0) {
            throw new IllegalArgumentException("encryption key must not be empty");
        }
        value = value.clone();
    }

    /**
     * <p>
     * 返回 AES 密钥字节副本。
     * </p>
     * @return AES 密钥字节副本
     */
    @Override
    public byte[] value() {
        return this.value.clone();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
