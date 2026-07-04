package io.github.coco.config;

import java.util.LinkedHashSet;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;

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
 *   <li>模块：{@code coco-config}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoFeatureProperties {

    private Set<CocoFeature> enabled = new LinkedHashSet<>();

    private Set<CocoFeature> disabled = new LinkedHashSet<>();

    private Set<CocoFeature> exclude = new LinkedHashSet<>();

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
     * 返回旧版排除式配置集合。
     * </p>
     * @return 旧版排除式配置集合
     */
    public Set<CocoFeature> getExclude() {
        return this.exclude;
    }

    /**
     * <p>
     * 设置旧版排除式配置集合。
     * </p>
     * <p>
     * 该属性作为 {@code disabled} 的兼容别名保留。
     * </p>
     * @param exclude 旧版排除式配置集合
     */
    public void setExclude(Set<CocoFeature> exclude) {
        this.exclude = exclude == null ? new LinkedHashSet<>() : new LinkedHashSet<>(exclude);
    }

    /**
     * <p>
     * 合并新版禁用配置和旧版排除式配置。
     * </p>
     * @return 最终禁用声明集合
     */
    public Set<CocoFeature> disabledFeatures() {
        LinkedHashSet<CocoFeature> features = new LinkedHashSet<>(this.disabled);
        features.addAll(this.exclude);
        return Set.copyOf(features);
    }
}
