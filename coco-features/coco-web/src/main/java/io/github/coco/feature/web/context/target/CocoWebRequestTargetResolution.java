package io.github.coco.feature.web.context.target;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * <p>
 * Coco Web 请求目标解析结果。
 * </p>
 * <p>
 * 在请求目标基础上补充可信代理、来源头与转发前缀等解析信息，作为 Web 上下文、日志、审计与安全模块之间共享的统一契约。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @param target 解析后的请求目标
 * @param source 请求目标来源
 * @param remoteAddress Servlet 远端地址
 * @param trustedProxy 远端地址是否命中可信代理
 * @param sourceHeaders 实际参与目标解析的来源请求头
 * @param forwardedPrefix 生效的转发前缀
 * @author patton174
 * @since 1.0.0
 */
public record CocoWebRequestTargetResolution(CocoWebRequestTarget target, CocoWebRequestTargetSource source,
        String remoteAddress, boolean trustedProxy, List<String> sourceHeaders, String forwardedPrefix) {

    /**
     * <p>
     * 创建请求目标解析结果，并归一化空白字段与来源头列表。
     * </p>
     * @param target 解析后的请求目标
     * @param source 请求目标来源
     * @param remoteAddress Servlet 远端地址
     * @param trustedProxy 远端地址是否命中可信代理
     * @param sourceHeaders 实际参与目标解析的来源请求头
     * @param forwardedPrefix 生效的转发前缀
     */
    public CocoWebRequestTargetResolution {
        target = target == null ? new CocoWebRequestTarget(null, null, null, null) : target;
        source = normalizeSource(source, target);
        remoteAddress = normalizeOptional(remoteAddress);
        trustedProxy = trustedProxy && remoteAddress != null;
        sourceHeaders = normalizeSourceHeaders(sourceHeaders);
        forwardedPrefix = normalizePrefix(forwardedPrefix);
        if (source == CocoWebRequestTargetSource.SERVLET
                || source == CocoWebRequestTargetSource.CUSTOM
                || source == CocoWebRequestTargetSource.UNRESOLVED) {
            sourceHeaders = List.of();
            forwardedPrefix = null;
        }
    }

    /**
     * <p>
     * 创建 Servlet 目标解析结果。
     * </p>
     * @param target Servlet 目标
     * @param remoteAddress Servlet 远端地址
     * @return 请求目标解析结果
     */
    public static CocoWebRequestTargetResolution servlet(CocoWebRequestTarget target, String remoteAddress) {
        return new CocoWebRequestTargetResolution(target, CocoWebRequestTargetSource.SERVLET, remoteAddress,
                false, List.of(), null);
    }

    /**
     * <p>
     * 创建可信代理解析结果。
     * </p>
     * @param target 外部请求目标
     * @param source 请求目标来源
     * @param remoteAddress Servlet 远端地址
     * @param sourceHeaders 实际参与解析的来源头
     * @param forwardedPrefix 生效的转发前缀
     * @return 请求目标解析结果
     */
    public static CocoWebRequestTargetResolution forwarded(CocoWebRequestTarget target,
            CocoWebRequestTargetSource source, String remoteAddress, List<String> sourceHeaders,
            String forwardedPrefix) {
        return new CocoWebRequestTargetResolution(target, source, remoteAddress, true, sourceHeaders,
                forwardedPrefix);
    }

    /**
     * <p>
     * 创建自定义解析结果。
     * </p>
     * @param target 自定义请求目标
     * @return 请求目标解析结果
     */
    public static CocoWebRequestTargetResolution custom(CocoWebRequestTarget target) {
        return new CocoWebRequestTargetResolution(target, CocoWebRequestTargetSource.CUSTOM, null, false, List.of(),
                null);
    }

    /**
     * <p>
     * 创建未解析结果。
     * </p>
     * @return 请求目标解析结果
     */
    public static CocoWebRequestTargetResolution unresolved() {
        return new CocoWebRequestTargetResolution(null, CocoWebRequestTargetSource.UNRESOLVED, null, false,
                List.of(), null);
    }

    /**
     * <p>
     * 返回可选的 Servlet 远端地址。
     * </p>
     * @return Servlet 远端地址；未设置时为空
     */
    public Optional<String> remoteAddressOptional() {
        return Optional.ofNullable(this.remoteAddress);
    }

    /**
     * <p>
     * 返回替换目标后的解析结果副本。
     * </p>
     * @param target 目标请求地址
     * @return 新的解析结果
     */
    public CocoWebRequestTargetResolution withTarget(CocoWebRequestTarget target) {
        return new CocoWebRequestTargetResolution(target, this.source, this.remoteAddress, this.trustedProxy,
                this.sourceHeaders, this.forwardedPrefix);
    }

    private static CocoWebRequestTargetSource normalizeSource(CocoWebRequestTargetSource source,
            CocoWebRequestTarget target) {
        if (source != null) {
            return source;
        }
        if (target == null) {
            return CocoWebRequestTargetSource.UNRESOLVED;
        }
        if (target.scheme() == null && target.host() == null && target.port() == null && target.path() == null) {
            return CocoWebRequestTargetSource.UNRESOLVED;
        }
        return CocoWebRequestTargetSource.CUSTOM;
    }

    private static List<String> normalizeSourceHeaders(List<String> sourceHeaders) {
        if (sourceHeaders == null || sourceHeaders.isEmpty()) {
            return List.of();
        }
        return sourceHeaders.stream()
                .map(CocoWebRequestTargetResolution::normalizeOptional)
                .filter(value -> value != null)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static String normalizePrefix(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
