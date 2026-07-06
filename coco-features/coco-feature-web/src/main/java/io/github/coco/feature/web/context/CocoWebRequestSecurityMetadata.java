package io.github.coco.feature.web.context;

import java.util.Optional;

/**
 * Coco Web 请求安全元数据。
 * <p>
 * 保存 Sign、AES 和后续防重放能力共享的请求身份、时间戳、随机串、算法和加密标记等协议材料。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param signatureAppId 签名应用标识
 * @param signatureKeyId 签名密钥标识
 * @param signatureTimestamp 签名时间戳
 * @param signatureNonce 签名随机串
 * @param signatureAlgorithm 签名算法
 * @param signature 请求签名
 * @param signed 请求是否携带签名
 * @param encryptionAppId 加密应用标识
 * @param encryptionKeyId 加密密钥标识
 * @param encryptionIv 加密初始向量
 * @param encryptionAlgorithm 加密算法
 * @param encrypted 请求是否声明为加密请求
 * @author patton174
 * @since 1.0.0
 */
public record CocoWebRequestSecurityMetadata(String signatureAppId, String signatureKeyId,
        String signatureTimestamp, String signatureNonce, String signatureAlgorithm, String signature, boolean signed,
        String encryptionAppId, String encryptionKeyId, String encryptionIv, String encryptionAlgorithm,
        boolean encrypted) {

    /**
     * <p>
     * 创建请求安全元数据，并归一化空白字段。
     * </p>
     * @param signatureAppId 签名应用标识
     * @param signatureKeyId 签名密钥标识
     * @param signatureTimestamp 签名时间戳
     * @param signatureNonce 签名随机串
     * @param signatureAlgorithm 签名算法
     * @param signature 请求签名
     * @param signed 请求是否携带签名
     * @param encryptionAppId 加密应用标识
     * @param encryptionKeyId 加密密钥标识
     * @param encryptionIv 加密初始向量
     * @param encryptionAlgorithm 加密算法
     * @param encrypted 请求是否声明为加密请求
     */
    public CocoWebRequestSecurityMetadata {
        signatureAppId = normalizeOptional(signatureAppId);
        signatureKeyId = normalizeOptional(signatureKeyId);
        signatureTimestamp = normalizeOptional(signatureTimestamp);
        signatureNonce = normalizeOptional(signatureNonce);
        signatureAlgorithm = normalizeOptional(signatureAlgorithm);
        signature = normalizeOptional(signature);
        signed = signed || signature != null;
        encryptionAppId = normalizeOptional(encryptionAppId);
        encryptionKeyId = normalizeOptional(encryptionKeyId);
        encryptionIv = normalizeOptional(encryptionIv);
        encryptionAlgorithm = normalizeOptional(encryptionAlgorithm);
    }

    /**
     * <p>
     * 返回空安全元数据。
     * </p>
     * @return 空安全元数据
     */
    public static CocoWebRequestSecurityMetadata empty() {
        return new CocoWebRequestSecurityMetadata(null, null, null, null, null, null, false,
                null, null, null, null, false);
    }

    /**
     * <p>
     * 返回优先可用的应用标识。
     * </p>
     * @return 优先可用的应用标识；未提供时为空
     */
    public Optional<String> primaryAppId() {
        return Optional.ofNullable(firstNonBlank(this.signatureAppId, this.encryptionAppId));
    }

    /**
     * <p>
     * 返回优先可用的密钥标识。
     * </p>
     * @return 优先可用的密钥标识；未提供时为空
     */
    public Optional<String> primaryKeyId() {
        return Optional.ofNullable(firstNonBlank(this.signatureKeyId, this.encryptionKeyId));
    }

    private static String firstNonBlank(String first, String second) {
        return first != null ? first : second;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
