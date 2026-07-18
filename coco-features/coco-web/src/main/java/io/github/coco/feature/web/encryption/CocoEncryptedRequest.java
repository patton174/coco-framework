package io.github.coco.feature.web.encryption;

/**
 * Coco 加密请求材料。
 * <p>
 * 保存一次请求中和 AES 解密相关的请求头、密文请求体和解密算法。
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
 * @param iv 初始向量
 * @param algorithm 解密算法
 * @param encrypted 是否声明为加密请求
 * @param payload 密文请求体
 * @author patton174
 * @since 1.0.0
 */
public record CocoEncryptedRequest(String appId, String keyId, String iv, String algorithm,
        boolean encrypted, byte[] payload) {

    /**
     * <p>
     * 创建加密请求材料。
     * </p>
     * @param appId 应用标识
     * @param keyId 密钥标识
     * @param iv 初始向量
     * @param algorithm 解密算法
     * @param encrypted 是否声明为加密请求
     * @param payload 密文请求体
     */
    public CocoEncryptedRequest {
        appId = normalizeOptional(appId);
        keyId = normalizeOptional(keyId);
        iv = normalizeOptional(iv);
        algorithm = normalizeOptional(algorithm);
        payload = payload == null ? new byte[0] : payload.clone();
    }

    /**
     * <p>
     * 返回密文请求体副本。
     * </p>
     * @return 密文请求体副本
     */
    @Override
    public byte[] payload() {
        return this.payload.clone();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
