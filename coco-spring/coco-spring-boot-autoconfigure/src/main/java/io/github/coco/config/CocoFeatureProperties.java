package io.github.coco.config;

import java.util.LinkedHashSet;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.model.CocoFeatureSelection;

/**
 * Coco 功能配置属性。
 * <p>
 * 绑定 {@code coco.features} 下的功能开关配置。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-autoconfigure}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoFeatureProperties {

    private Set<CocoFeature> enabled = new LinkedHashSet<>();

    private Set<CocoFeature> disabled = new LinkedHashSet<>();

    /**
     * <p>
     * 返回配置文件中显式启用的功能集合。
     * </p>
     * @return 显式启用的功能集合
     */
    public Set<CocoFeature> getEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置配置文件中显式启用的功能集合。
     * </p>
     * @param enabled 显式启用的功能集合
     */
    public void setEnabled(Set<CocoFeature> enabled) {
        this.enabled = enabled == null ? new LinkedHashSet<>() : new LinkedHashSet<>(enabled);
    }

    /**
     * <p>
     * 返回配置文件中显式禁用的功能集合。
     * </p>
     * @return 显式禁用的功能集合
     */
    public Set<CocoFeature> getDisabled() {
        return this.disabled;
    }

    /**
     * <p>
     * 设置配置文件中显式禁用的功能集合。
     * </p>
     * @param disabled 显式禁用的功能集合
     */
    public void setDisabled(Set<CocoFeature> disabled) {
        this.disabled = disabled == null ? new LinkedHashSet<>() : new LinkedHashSet<>(disabled);
    }

    /**
     * <p>
     * 将配置属性适配为核心功能选择模型。
     * </p>
     * @return 功能选择声明
     */
    public CocoFeatureSelection toSelection() {
        return CocoFeatureSelection.of(this.enabled, this.disabled);
    }

    /**
     * <p>
     * 返回最终禁用声明集合。
     * </p>
     * @deprecated 请使用 {@link #toSelection()}，由核心功能选择模型统一承载启用和禁用声明。
     * @return 最终禁用声明集合
     */
    @Deprecated(since = "1.0.0")
    public Set<CocoFeature> disabledFeatures() {
        return Set.copyOf(this.disabled);
    }
}
