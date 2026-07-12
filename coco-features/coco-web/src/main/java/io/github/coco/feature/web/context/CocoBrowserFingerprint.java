package io.github.coco.feature.web.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Coco 浏览器指纹。
 * <p>
 * 基于服务端可见的浏览器请求信号生成稳定摘要，为后续风控、审计和浏览器指纹功能提供基础输入。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @param value 指纹摘要
 * @param signals 参与生成指纹的信号
 * @author patton174
 * @since 1.0.0
 */
public record CocoBrowserFingerprint(String value, Map<String, String> signals) {

    /**
     * <p>
     * 创建浏览器指纹，并归一化信号集合。
     * </p>
     * @param value 指纹摘要
     * @param signals 参与生成指纹的信号
     */
    public CocoBrowserFingerprint {
        value = value == null || value.isBlank() ? null : value.trim();
        signals = copySignals(signals);
    }

    /**
     * <p>
     * 创建空浏览器指纹。
     * </p>
     * @return 空浏览器指纹
     */
    public static CocoBrowserFingerprint empty() {
        return new CocoBrowserFingerprint(null, Map.of());
    }

    /**
     * <p>
     * 根据浏览器信号生成指纹。
     * </p>
     * @param signals 浏览器信号
     * @return 浏览器指纹
     */
    public static CocoBrowserFingerprint from(Map<String, String> signals) {
        Map<String, String> copiedSignals = copySignals(signals);
        if (copiedSignals.isEmpty()) {
            return empty();
        }
        return new CocoBrowserFingerprint(sha256(canonicalSignals(copiedSignals)), copiedSignals);
    }

    /**
     * <p>
     * 根据展示信号和完整哈希信号生成浏览器指纹。
     * </p>
     * <p>
     * 展示信号可以按配置裁剪后写入上下文，完整哈希信号用于生成摘要，避免长请求头裁剪后造成指纹碰撞。
     * </p>
     * @param signals 写入上下文的展示信号
     * @param hashSignals 参与生成摘要的完整信号
     * @return 浏览器指纹
     */
    public static CocoBrowserFingerprint from(Map<String, String> signals, Map<String, String> hashSignals) {
        Map<String, String> copiedSignals = copySignals(signals);
        Map<String, String> copiedHashSignals = copySignals(hashSignals);
        if (copiedHashSignals.isEmpty()) {
            return copiedSignals.isEmpty() ? empty() : from(copiedSignals);
        }
        return new CocoBrowserFingerprint(sha256(canonicalSignals(copiedHashSignals)), copiedSignals);
    }

    private static Map<String, String> copySignals(Map<String, String> signals) {
        if (signals == null || signals.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copied = new LinkedHashMap<>();
        signals.forEach((name, value) -> {
            if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
                copied.put(name.trim().toLowerCase(Locale.ROOT), value.trim());
            }
        });
        return copied.isEmpty() ? Map.of() : Collections.unmodifiableMap(copied);
    }

    private static String canonicalSignals(Map<String, String> signals) {
        StringBuilder builder = new StringBuilder();
        new TreeMap<>(signals).forEach((name, value) ->
                builder.append(name).append('=').append(value).append('\n'));
        return builder.toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
