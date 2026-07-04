package io.github.coco.common;

import io.github.coco.common.i18n.CocoI18nProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 通用基础设施配置属性。
 * <p>
 * 绑定 {@code coco.common} 命名空间，作为框架通用能力配置的统一入口。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.common")
public class CocoCommonProperties {

    @NestedConfigurationProperty
    private CocoI18nProperties i18n = new CocoI18nProperties();

    public CocoI18nProperties getI18n() {
        return this.i18n;
    }

    public void setI18n(CocoI18nProperties i18n) {
        this.i18n = i18n == null ? new CocoI18nProperties() : i18n;
    }
}
