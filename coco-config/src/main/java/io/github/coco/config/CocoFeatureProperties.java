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

    public Set<CocoFeature> getEnabled() {
        return this.enabled;
    }

    public void setEnabled(Set<CocoFeature> enabled) {
        this.enabled = enabled == null ? new LinkedHashSet<>() : new LinkedHashSet<>(enabled);
    }

    public Set<CocoFeature> getDisabled() {
        return this.disabled;
    }

    public void setDisabled(Set<CocoFeature> disabled) {
        this.disabled = disabled == null ? new LinkedHashSet<>() : new LinkedHashSet<>(disabled);
    }

    public Set<CocoFeature> getExclude() {
        return this.exclude;
    }

    public void setExclude(Set<CocoFeature> exclude) {
        this.exclude = exclude == null ? new LinkedHashSet<>() : new LinkedHashSet<>(exclude);
    }

    public Set<CocoFeature> disabledFeatures() {
        LinkedHashSet<CocoFeature> features = new LinkedHashSet<>(this.disabled);
        features.addAll(this.exclude);
        return Set.copyOf(features);
    }
}
