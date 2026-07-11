package io.github.coco.web.signature;

/**
 * Coco 请求签名材料。
 * <p>
 * 保存一次请求中和 Sign 验签相关的请求头、规范化文本和签名摘要。
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
 * @param timestamp 请求时间戳
 * @param nonce 请求随机串
 * @param algorithm 签名算法
 * @param signature 请求签名
 * @param canonicalText 规范化请求文本
 * @param canonicalSha256 规范化请求文本 SHA-256 摘要
 * @author patton174
 * @since 1.0.0
 */
public record CocoSignatureRequest(String appId, String keyId, String timestamp, String nonce,
        String algorithm, String signature, String canonicalText, String canonicalSha256) {

    /**
     * <p>
     * 创建请求签名材料，并归一化空白字段。
     * </p>
     * @param appId 应用标识
     * @param keyId 密钥标识
     * @param timestamp 请求时间戳
     * @param nonce 请求随机串
     * @param algorithm 签名算法
     * @param signature 请求签名
     * @param canonicalText 规范化请求文本
     * @param canonicalSha256 规范化请求文本 SHA-256 摘要
     */
    public CocoSignatureRequest {
        appId = normalizeOptional(appId);
        keyId = normalizeOptional(keyId);
        timestamp = normalizeOptional(timestamp);
        nonce = normalizeOptional(nonce);
        algorithm = normalizeOptional(algorithm);
        signature = normalizeOptional(signature);
        canonicalText = canonicalText == null ? "" : canonicalText;
        canonicalSha256 = normalizeOptional(canonicalSha256);
    }

    /**
     * <p>
     * 返回当前请求是否携带签名。
     * </p>
     * @return 携带签名时返回 {@code true}
     */
    public boolean signed() {
        return this.signature != null;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
