package io.github.coco.web.encryption;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.github.coco.web.request.metadata.CocoWebRequestSecurityMetadata;
import io.github.coco.web.context.CocoWebRequestSnapshot;

/**
 * Coco 请求加密附加认证数据构造器�? * <p>
 * �?AES-GCM 请求解密生成稳定�?AAD，使密文认证同时绑定应用标识、密钥标识、初始向量、算法、加密标记、路由和防重放材料�? * </p>
 * <p>
 * 项目信息�? * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库�?a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoEncryptionAssociatedData {

    private static final String VERSION = "coco.web.encryption.v1";

    private CocoEncryptionAssociatedData() {
    }

    /**
     * <p>
     * 根据加密请求材料生成 AES-GCM AAD�?     * </p>
     * @param request 加密请求材料
     * @return UTF-8 编码后的 AAD 字节
     */
    public static byte[] from(CocoEncryptedRequest request) {
        CocoEncryptedRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        return from(checkedRequest.appId(), checkedRequest.keyId(), checkedRequest.iv(), checkedRequest.algorithm(),
                checkedRequest.encrypted());
    }

    /**
     * <p>
     * 根据加密请求材料、请求快照和安全元数据生�?AES-GCM AAD�?     * </p>
     * @param request 加密请求材料
     * @param snapshot Web 请求快照
     * @param metadata Web 请求安全元数�?     * @return UTF-8 编码后的 AAD 字节
     */
    public static byte[] from(CocoEncryptedRequest request, CocoWebRequestSnapshot snapshot,
            CocoWebRequestSecurityMetadata metadata) {
        CocoEncryptedRequest checkedRequest = Objects.requireNonNull(request, "request must not be null");
        CocoWebRequestSnapshot checkedSnapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
        CocoWebRequestSecurityMetadata checkedMetadata = Objects.requireNonNull(metadata, "metadata must not be null");
        return from(checkedRequest.appId(), checkedRequest.keyId(), checkedRequest.iv(), checkedRequest.algorithm(),
                checkedRequest.encrypted(), checkedSnapshot.method(), checkedSnapshot.path(),
                checkedSnapshot.securityInput().queryString(), checkedMetadata.replayTimestamp(),
                checkedMetadata.replayNonce());
    }

    /**
     * <p>
     * 根据加密请求元数据生�?AES-GCM AAD�?     * </p>
     * @param appId 应用标识
     * @param keyId 密钥标识
     * @param iv 初始向量文本
     * @param algorithm 加密算法
     * @param encrypted 是否声明为加密请�?     * @return UTF-8 编码后的 AAD 字节
     */
    public static byte[] from(String appId, String keyId, String iv, String algorithm, boolean encrypted) {
        return from(appId, keyId, iv, algorithm, encrypted, null, null, null, null, null);
    }

    /**
     * <p>
     * 根据加密请求元数据和请求路由元数据生�?AES-GCM AAD�?     * </p>
     * @param appId 应用标识
     * @param keyId 密钥标识
     * @param iv 初始向量文本
     * @param algorithm 加密算法
     * @param encrypted 是否声明为加密请�?     * @param method HTTP 方法
     * @param path 请求路径
     * @param queryString 原始查询字符�?     * @param replayTimestamp 防重放时间戳
     * @param replayNonce 防重放随机串
     * @return UTF-8 编码后的 AAD 字节
     */
    public static byte[] from(String appId, String keyId, String iv, String algorithm, boolean encrypted,
            String method, String path, String queryString, String replayTimestamp, String replayNonce) {
        StringBuilder builder = new StringBuilder(160);
        builder.append(VERSION).append('\n');
        append(builder, "appId", appId);
        append(builder, "keyId", keyId);
        append(builder, "iv", iv);
        append(builder, "algorithm", algorithm);
        append(builder, "encrypted", Boolean.toString(encrypted));
        append(builder, "method", method);
        append(builder, "path", path);
        append(builder, "query", queryString);
        append(builder, "replayTimestamp", replayTimestamp);
        append(builder, "replayNonce", replayNonce);
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * <p>
     * 返回当前 AES-GCM AAD 协议版本�?     * </p>
     * @return 协议版本
     */
    public static String version() {
        return VERSION;
    }

    private static void append(StringBuilder builder, String name, String value) {
        String normalizedValue = value == null ? "" : value.trim();
        builder.append(name)
                .append('=')
                .append(normalizedValue.length())
                .append(':')
                .append(normalizedValue)
                .append('\n');
    }
}
