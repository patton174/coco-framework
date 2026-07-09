package io.github.coco.feature.security;

import io.github.coco.feature.security.web.CocoSecurityWebProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 安全功能配置属性。
 * <p>
 * 绑定 {@code coco.security} 命名空间，集中维护安全上下文、Web 入口适配和后续安全扩展能力的配置。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-security}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.security")
public class CocoSecurityProperties {

    @NestedConfigurationProperty
    private CocoSecurityWebProperties web = new CocoSecurityWebProperties();

    /**
     * <p>
     * 返回 Web 安全上下文桥接配置。
     * </p>
     * @return Web 安全上下文桥接配置
     */
    public CocoSecurityWebProperties getWeb() {
        return this.web;
    }

    /**
     * <p>
     * 设置 Web 安全上下文桥接配置。
     * </p>
     * @param web Web 安全上下文桥接配置
     */
    public void setWeb(CocoSecurityWebProperties web) {
        this.web = web == null ? new CocoSecurityWebProperties() : web;
    }
}
