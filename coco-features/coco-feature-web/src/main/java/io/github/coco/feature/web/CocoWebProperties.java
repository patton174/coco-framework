package io.github.coco.feature.web;

import io.github.coco.feature.web.trace.CocoTraceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco Web 功能配置属性。
 * <p>
 * 绑定 {@code coco.web} 命名空间，集中维护 Web 功能模块自己的配置项。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.web")
public class CocoWebProperties {

    @NestedConfigurationProperty
    private CocoTraceProperties trace = new CocoTraceProperties();

    /**
     * <p>
     * 返回 Trace 配置属性。
     * </p>
     * @return Trace 配置属性
     */
    public CocoTraceProperties getTrace() {
        return this.trace;
    }

    /**
     * <p>
     * 设置 Trace 配置属性。
     * </p>
     * @param trace Trace 配置属性
     */
    public void setTrace(CocoTraceProperties trace) {
        this.trace = trace == null ? new CocoTraceProperties() : trace;
    }
}
