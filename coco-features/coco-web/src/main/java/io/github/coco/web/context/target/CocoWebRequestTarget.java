package io.github.coco.web.context.target;

import java.util.Locale;

/**
 * <p>
 * Coco Web 请求目标。
 * </p>
 * <p>
 * 表示业务侧和安全侧应当感知到的外部请求目标，包括协议、主机、端口和路径。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @param scheme 请求协议
 * @param host 请求主机
 * @param port 请求端口
 * @param path 请求路径
 * @author patton174
 * @since 1.0.0
 */
public record CocoWebRequestTarget(String scheme, String host, Integer port, String path) {

    /**
     * <p>
     * 创建 Web 请求目标，并归一化字段值。
     * </p>
     * @param scheme 请求协议
     * @param host 请求主机
     * @param port 请求端口
     * @param path 请求路径
     */
    public CocoWebRequestTarget {
        scheme = normalizeScheme(scheme);
        host = normalizeHost(host);
        port = normalizePort(port);
        path = normalizePath(path);
    }

    private static String normalizeScheme(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeHost(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static Integer normalizePort(Integer value) {
        if (value == null || value <= 0 || value > 65535) {
            return null;
        }
        return value;
    }

    private static String normalizePath(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
