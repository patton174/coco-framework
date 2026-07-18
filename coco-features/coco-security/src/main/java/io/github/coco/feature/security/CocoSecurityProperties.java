package io.github.coco.feature.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
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
 *   <li>模块：{@code coco-security}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.security")
public class CocoSecurityProperties {

    @NestedConfigurationProperty
    private CocoSecurityWebProperties web = new CocoSecurityWebProperties();

    public CocoSecurityProperties() {
    }

    /**
     * <p>
     * 返回 Web 安全上下文桥接配置的可变 JavaBean 引用。
     * Spring Binder 和已有 Java 配置使用者会通过 {@code getWeb().set...} 更新嵌套属性，
     * 因此这里有意暴露受 {@link #setWeb(CocoSecurityWebProperties)} 防御性复制保护的内部配置。
     * </p>
     * @return Web 安全上下文桥接配置
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "ConfigurationProperties nested JavaBean "
            + "accessors must retain their established live mutable semantics for Spring Binder and Java consumers.")
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
        this.web = web == null ? new CocoSecurityWebProperties() : new CocoSecurityWebProperties(web);
    }
}
