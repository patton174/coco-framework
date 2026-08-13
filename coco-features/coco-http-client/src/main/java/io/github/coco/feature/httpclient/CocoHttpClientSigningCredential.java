package io.github.coco.feature.httpclient;

/**
 * 命名 HTTP 客户端的不可变签名凭据。
 *
 * @param appId 应用标识
 * @param keyId 密钥标识
 * @param secret 共享密钥
 * @param algorithm 签名算法
 * @author patton174
 * @since 1.0.0
 */
public record CocoHttpClientSigningCredential(String appId, String keyId, String secret, String algorithm) {

    /**
     * 创建签名凭据。
     *
     * @param appId 应用标识
     * @param keyId 密钥标识
     * @param secret 共享密钥
     * @param algorithm 签名算法
     */
    public CocoHttpClientSigningCredential {
        appId = required(appId, "appId");
        keyId = required(keyId, "keyId");
        secret = required(secret, "secret");
        algorithm = required(algorithm, "algorithm");
    }

    @Override
    public String toString() {
        return "CocoHttpClientSigningCredential[appId=" + this.appId + ", keyId=" + this.keyId
                + ", algorithm=" + this.algorithm + "]";
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
