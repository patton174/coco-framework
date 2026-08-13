package io.github.coco.feature.security;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.coco.feature.security.method.CocoSecurityMethodProperties;
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

    @NestedConfigurationProperty
    private CocoSecurityMethodProperties method = new CocoSecurityMethodProperties();

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

    /**
     * 返回方法授权配置。
     * <p>
     * Spring Binder 和 Java 配置使用者需要通过 {@code getMethod().set...} 更新该嵌套配置，
     * 因此保留可变的实时引用语义。
     * </p>
     * @return 方法授权配置
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "ConfigurationProperties nested JavaBean "
            + "accessors retain live mutable semantics for Spring Binder and Java consumers.")
    public CocoSecurityMethodProperties getMethod() {
        return this.method;
    }

    /**
     * 设置方法授权配置。
     * @param method 方法授权配置
     */
    public void setMethod(CocoSecurityMethodProperties method) {
        this.method = method == null ? new CocoSecurityMethodProperties() : method;
    }
}
