package io.github.coco.feature.web.replay;

import java.util.StringJoiner;

/**
 * Coco Web 防重放键。
 * <p>
 * 保存一次请求用于重放判定的稳定键材料，默认由应用标识、密钥标识、时间戳、随机串、请求方法和请求路径组成。
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
 * @param method HTTP 方法
 * @param path 请求路径
 * @author patton174
 * @since 1.0.0
 */
public record CocoReplayKey(String appId, String keyId, String timestamp, String nonce, String method, String path) {

    /**
     * <p>
     * 创建防重放键，并归一化空白字段。
     * </p>
     * @param appId 应用标识
     * @param keyId 密钥标识
     * @param timestamp 请求时间戳
     * @param nonce 请求随机串
     * @param method HTTP 方法
     * @param path 请求路径
     */
    public CocoReplayKey {
        appId = normalizeOptional(appId);
        keyId = normalizeOptional(keyId);
        timestamp = normalizeOptional(timestamp);
        nonce = normalizeOptional(nonce);
        method = normalizeOptional(method);
        path = normalizeOptional(path);
    }

    /**
     * <p>
     * 返回可用于存储层去重的稳定字符串。
     * </p>
     * @return 防重放存储键
     */
    public String value() {
        StringJoiner joiner = new StringJoiner("|");
        append(joiner, this.appId);
        append(joiner, this.keyId);
        append(joiner, this.timestamp);
        append(joiner, this.nonce);
        append(joiner, this.method);
        append(joiner, this.path);
        return joiner.toString();
    }

    private static void append(StringJoiner joiner, String value) {
        String normalizedValue = value == null ? "" : value;
        joiner.add(normalizedValue.length() + ":" + normalizedValue);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
