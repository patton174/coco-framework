package io.github.coco.feature.security.web;

import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco Web 安全上下文桥接配置。
 * <p>
 * 控制 Servlet 请求进入业务代码前，是否由框架统一管理 {@code CocoSecurityContextHolder} 的请求生命周期。
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
public class CocoSecurityWebProperties {

    /**
     * 是否注册 Web 安全上下文桥接过滤器。
     */
    private boolean enabled = true;

    @NestedConfigurationProperty
    private CocoSecurityWebHeaderProperties header = new CocoSecurityWebHeaderProperties();

    /**
     * <p>
     * 返回是否注册 Web 安全上下文桥接过滤器。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否注册 Web 安全上下文桥接过滤器。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回可信请求头安全上下文适配配置。
     * </p>
     * @return 可信请求头安全上下文适配配置
     */
    public CocoSecurityWebHeaderProperties getHeader() {
        return this.header;
    }

    /**
     * <p>
     * 设置可信请求头安全上下文适配配置。
     * </p>
     * @param header 可信请求头安全上下文适配配置
     */
    public void setHeader(CocoSecurityWebHeaderProperties header) {
        this.header = header == null ? new CocoSecurityWebHeaderProperties() : header;
    }
}
