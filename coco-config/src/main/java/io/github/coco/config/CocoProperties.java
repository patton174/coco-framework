package io.github.coco.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 根配置属性。
 * <p>
 * 绑定 {@code coco} 命名空间，作为后续各功能配置的统一入口。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-config}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco")
public class CocoProperties {

    @NestedConfigurationProperty
    private CocoFeatureProperties features = new CocoFeatureProperties();

    /**
     * <p>
     * 返回 Coco 功能开关配置。
     * </p>
     * @return 功能开关配置
     */
    public CocoFeatureProperties getFeatures() {
        return this.features;
    }

    /**
     * <p>
     * 设置 Coco 功能开关配置。
     * </p>
     * @param features 功能开关配置
     */
    public void setFeatures(CocoFeatureProperties features) {
        this.features = features == null ? new CocoFeatureProperties() : features;
    }
}
