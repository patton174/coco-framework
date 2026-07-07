package io.github.coco.feature.web.context;

import java.util.List;
import java.util.Optional;

/**
 * Coco 客户端 IP 解析结果。
 * <p>
 * 保存客户端 IP 及其来源信息，让访问日志、审计、风控、签名和浏览器指纹能力可以判断该 IP 是否来自可信代理链。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @param clientIp 客户端 IP
 * @param source 客户端 IP 来源
 * @param sourceHeaderName 来源请求头名称
 * @param sourceHeaderValue 来源请求头原始值
 * @param remoteAddress Servlet 远端地址
 * @param trustedProxy 远端地址是否匹配可信代理
 * @param sourceChain 来源请求头解析出的 IP 链
 * @param resolvedChainIndex 命中的客户端 IP 在代理链中的下标
 * @author patton174
 * @since 1.0.0
 */
public record CocoClientIpResolution(String clientIp, CocoClientIpSource source, String sourceHeaderName,
        String sourceHeaderValue, String remoteAddress, boolean trustedProxy, List<String> sourceChain,
        Integer resolvedChainIndex) {

    /**
     * <p>
     * 创建客户端 IP 解析结果，并归一化空白字段。
     * </p>
     * @param clientIp 客户端 IP
     * @param source 客户端 IP 来源
     * @param sourceHeaderName 来源请求头名称
     * @param sourceHeaderValue 来源请求头原始值
     * @param remoteAddress Servlet 远端地址
     * @param trustedProxy 远端地址是否匹配可信代理
     * @param sourceChain 来源请求头解析出的 IP 链
     * @param resolvedChainIndex 命中的客户端 IP 在代理链中的下标
     */
    public CocoClientIpResolution {
        clientIp = normalizeOptional(clientIp);
        source = source == null ? CocoClientIpSource.UNRESOLVED : source;
        sourceHeaderName = normalizeOptional(sourceHeaderName);
        sourceHeaderValue = normalizeOptional(sourceHeaderValue);
        remoteAddress = normalizeOptional(remoteAddress);
        sourceChain = normalizeSourceChain(sourceChain);
        resolvedChainIndex = normalizeResolvedChainIndex(sourceChain, resolvedChainIndex);
        trustedProxy = trustedProxy && remoteAddress != null;
        if (clientIp == null && source != CocoClientIpSource.UNRESOLVED) {
            source = CocoClientIpSource.UNRESOLVED;
            sourceHeaderName = null;
            sourceHeaderValue = null;
            trustedProxy = false;
            sourceChain = List.of();
            resolvedChainIndex = null;
        }
        if (source != CocoClientIpSource.FORWARDED_HEADER) {
            sourceChain = List.of();
            resolvedChainIndex = null;
        }
    }

    /**
     * <p>
     * 创建远端地址解析结果。
     * </p>
     * @param remoteAddress Servlet 远端地址
     * @return 客户端 IP 解析结果
     */
    public static CocoClientIpResolution remoteAddress(String remoteAddress) {
        return new CocoClientIpResolution(remoteAddress, CocoClientIpSource.REMOTE_ADDRESS, null, null,
                remoteAddress, false, List.of(), null);
    }

    /**
     * <p>
     * 创建代理请求头解析结果。
     * </p>
     * @param clientIp 客户端 IP
     * @param sourceHeaderName 来源请求头名称
     * @param sourceHeaderValue 来源请求头原始值
     * @param remoteAddress Servlet 远端地址
     * @return 客户端 IP 解析结果
     */
    public static CocoClientIpResolution forwardedHeader(String clientIp, String sourceHeaderName,
            String sourceHeaderValue, String remoteAddress) {
        return new CocoClientIpResolution(clientIp, CocoClientIpSource.FORWARDED_HEADER, sourceHeaderName,
                sourceHeaderValue, remoteAddress, true, List.of(clientIp), 0);
    }

    /**
     * <p>
     * 创建代理请求头解析结果。
     * </p>
     * @param clientIp 客户端 IP
     * @param sourceHeaderName 来源请求头名称
     * @param sourceHeaderValue 来源请求头原始值
     * @param remoteAddress Servlet 远端地址
     * @param sourceChain 来源请求头解析出的 IP 链
     * @param resolvedChainIndex 命中的客户端 IP 在代理链中的下标
     * @return 客户端 IP 解析结果
     */
    public static CocoClientIpResolution forwardedHeader(String clientIp, String sourceHeaderName,
            String sourceHeaderValue, String remoteAddress, List<String> sourceChain, Integer resolvedChainIndex) {
        return new CocoClientIpResolution(clientIp, CocoClientIpSource.FORWARDED_HEADER, sourceHeaderName,
                sourceHeaderValue, remoteAddress, true, sourceChain, resolvedChainIndex);
    }

    /**
     * <p>
     * 创建自定义解析结果。
     * </p>
     * @param clientIp 客户端 IP
     * @return 客户端 IP 解析结果
     */
    public static CocoClientIpResolution custom(String clientIp) {
        return new CocoClientIpResolution(clientIp, CocoClientIpSource.CUSTOM, null, null, null, false, List.of(),
                null);
    }

    /**
     * <p>
     * 创建未解析结果。
     * </p>
     * @return 客户端 IP 解析结果
     */
    public static CocoClientIpResolution unresolved() {
        return new CocoClientIpResolution(null, CocoClientIpSource.UNRESOLVED, null, null, null, false, List.of(),
                null);
    }

    /**
     * <p>
     * 返回是否解析到有效客户端 IP。
     * </p>
     * @return 解析到客户端 IP 时返回 {@code true}
     */
    public boolean present() {
        return this.clientIp != null;
    }

    /**
     * <p>
     * 返回客户端 IP。
     * </p>
     * @return 客户端 IP；未解析时为空
     */
    public Optional<String> clientIpOptional() {
        return Optional.ofNullable(this.clientIp);
    }

    private static List<String> normalizeSourceChain(List<String> sourceChain) {
        if (sourceChain == null || sourceChain.isEmpty()) {
            return List.of();
        }
        return sourceChain.stream()
                .map(CocoClientIpResolution::normalizeOptional)
                .filter(value -> value != null)
                .toList();
    }

    private static Integer normalizeResolvedChainIndex(List<String> sourceChain, Integer resolvedChainIndex) {
        if (resolvedChainIndex == null || sourceChain == null || sourceChain.isEmpty()) {
            return null;
        }
        return resolvedChainIndex >= 0 && resolvedChainIndex < sourceChain.size() ? resolvedChainIndex : null;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
