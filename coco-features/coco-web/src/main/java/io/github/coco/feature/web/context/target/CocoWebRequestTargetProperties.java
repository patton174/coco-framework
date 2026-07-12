package io.github.coco.feature.web.context.target;

import java.util.List;

/**
 * <p>
 * Coco Web 请求目标解析配置。
 * </p>
 * <p>
 * 控制反向代理场景下协议、主机、端口和路径前缀的可信转发头解析顺序。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoWebRequestTargetProperties {

    private static final List<String> DEFAULT_PROTO_HEADER_NAMES = List.of(
            "X-Forwarded-Proto", "X-Forwarded-Protocol", "X-Forwarded-Ssl", "Front-End-Https");

    private static final List<String> DEFAULT_HOST_HEADER_NAMES = List.of(
            "X-Forwarded-Host", "X-Original-Host");

    private static final List<String> DEFAULT_PORT_HEADER_NAMES = List.of("X-Forwarded-Port");

    private static final List<String> DEFAULT_PREFIX_HEADER_NAMES = List.of(
            "X-Forwarded-Prefix", "X-Forwarded-PathBase", "X-Original-Prefix");

    private List<String> protoHeaderNames = DEFAULT_PROTO_HEADER_NAMES;

    private List<String> hostHeaderNames = DEFAULT_HOST_HEADER_NAMES;

    private List<String> portHeaderNames = DEFAULT_PORT_HEADER_NAMES;

    private List<String> prefixHeaderNames = DEFAULT_PREFIX_HEADER_NAMES;

    private boolean applyForwardedPrefix = true;

    /**
     * <p>
     * 返回协议转发头名称列表。
     * </p>
     * @return 协议转发头名称列表
     */
    public List<String> getProtoHeaderNames() {
        return this.protoHeaderNames;
    }

    /**
     * <p>
     * 设置协议转发头名称列表。
     * </p>
     * @param protoHeaderNames 协议转发头名称列表
     */
    public void setProtoHeaderNames(List<String> protoHeaderNames) {
        this.protoHeaderNames = normalizeHeaderNames(protoHeaderNames, DEFAULT_PROTO_HEADER_NAMES);
    }

    /**
     * <p>
     * 返回主机转发头名称列表。
     * </p>
     * @return 主机转发头名称列表
     */
    public List<String> getHostHeaderNames() {
        return this.hostHeaderNames;
    }

    /**
     * <p>
     * 设置主机转发头名称列表。
     * </p>
     * @param hostHeaderNames 主机转发头名称列表
     */
    public void setHostHeaderNames(List<String> hostHeaderNames) {
        this.hostHeaderNames = normalizeHeaderNames(hostHeaderNames, DEFAULT_HOST_HEADER_NAMES);
    }

    /**
     * <p>
     * 返回端口转发头名称列表。
     * </p>
     * @return 端口转发头名称列表
     */
    public List<String> getPortHeaderNames() {
        return this.portHeaderNames;
    }

    /**
     * <p>
     * 设置端口转发头名称列表。
     * </p>
     * @param portHeaderNames 端口转发头名称列表
     */
    public void setPortHeaderNames(List<String> portHeaderNames) {
        this.portHeaderNames = normalizeHeaderNames(portHeaderNames, DEFAULT_PORT_HEADER_NAMES);
    }

    /**
     * <p>
     * 返回路径前缀转发头名称列表。
     * </p>
     * @return 路径前缀转发头名称列表
     */
    public List<String> getPrefixHeaderNames() {
        return this.prefixHeaderNames;
    }

    /**
     * <p>
     * 设置路径前缀转发头名称列表。
     * </p>
     * @param prefixHeaderNames 路径前缀转发头名称列表
     */
    public void setPrefixHeaderNames(List<String> prefixHeaderNames) {
        this.prefixHeaderNames = normalizeHeaderNames(prefixHeaderNames, DEFAULT_PREFIX_HEADER_NAMES);
    }

    /**
     * <p>
     * 返回是否应用可信代理转发的路径前缀。
     * </p>
     * @return 应用路径前缀时返回 {@code true}
     */
    public boolean isApplyForwardedPrefix() {
        return this.applyForwardedPrefix;
    }

    /**
     * <p>
     * 设置是否应用可信代理转发的路径前缀。
     * </p>
     * @param applyForwardedPrefix 是否应用路径前缀
     */
    public void setApplyForwardedPrefix(boolean applyForwardedPrefix) {
        this.applyForwardedPrefix = applyForwardedPrefix;
    }

    private static List<String> normalizeHeaderNames(List<String> headerNames, List<String> defaults) {
        if (headerNames == null || headerNames.isEmpty()) {
            return defaults;
        }
        List<String> normalizedHeaderNames = headerNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        return normalizedHeaderNames.isEmpty() ? defaults : List.copyOf(normalizedHeaderNames);
    }
}
