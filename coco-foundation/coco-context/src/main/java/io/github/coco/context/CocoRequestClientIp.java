package io.github.coco.context;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 客户端 IP 视图。
 * <p>
 * 提供客户端 IP 地址、来源、代理链等信息的结构化访问，
 * 属性来源于 {@link CocoRequestContext} 的内部属性快照。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-context}</li>
 * </ul>
 * @author patton174
 * @since 1.1.0
 */
public final class CocoRequestClientIp {

    private final Map<String, String> attributes;

    CocoRequestClientIp(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    /**
     * <p>
     * 返回客户端 IP。
     * </p>
     * @return 客户端 IP；未设置时为空
     */
    public Optional<String> ip() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.CLIENT_IP);
    }

    /**
     * <p>
     * 返回客户端 IP 来源。
     * </p>
     * @return 客户端 IP 来源；未设置时为空
     */
    public Optional<String> source() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.CLIENT_IP_SOURCE);
    }

    /**
     * <p>
     * 返回客户端 IP 来源请求头。
     * </p>
     * @return 客户端 IP 来源请求头；未设置时为空
     */
    public Optional<String> sourceHeader() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.CLIENT_IP_SOURCE_HEADER);
    }

    /**
     * <p>
     * 返回客户端 IP 来源请求头原始值。
     * </p>
     * @return 客户端 IP 来源请求头原始值；未设置时为空
     */
    public Optional<String> sourceHeaderValue() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.CLIENT_IP_SOURCE_HEADER_VALUE);
    }

    /**
     * <p>
     * 返回 Servlet 远端地址。
     * </p>
     * @return Servlet 远端地址；未设置时为空
     */
    public Optional<String> remoteAddress() {
        return CocoRequestContextAttributeParser.attribute(this.attributes,
                CocoRequestContextAttributes.CLIENT_IP_REMOTE_ADDRESS);
    }

    /**
     * <p>
     * 返回客户端 IP 是否来自可信代理链。
     * </p>
     * @return 客户端 IP 来自可信代理链时返回 {@code true}
     */
    public boolean trustedProxy() {
        return CocoRequestContextAttributeParser.booleanAttribute(this.attributes,
                CocoRequestContextAttributes.CLIENT_IP_TRUSTED_PROXY);
    }

    /**
     * <p>
     * 返回客户端 IP 代理链。
     * </p>
     * @return 客户端 IP 代理链；未设置时为空
     */
    public Optional<List<String>> chain() {
        return CocoRequestContextAttributeParser.listAttribute(this.attributes,
                CocoRequestContextAttributes.CLIENT_IP_CHAIN, true);
    }

    /**
     * <p>
     * 返回命中的客户端 IP 在代理链中的下标。
     * </p>
     * @return 命中的客户端 IP 在代理链中的下标；未设置时为空
     */
    public Optional<Integer> resolvedChainIndex() {
        return CocoRequestContextAttributeParser.intAttribute(this.attributes,
                CocoRequestContextAttributes.CLIENT_IP_RESOLVED_CHAIN_INDEX);
    }
}
