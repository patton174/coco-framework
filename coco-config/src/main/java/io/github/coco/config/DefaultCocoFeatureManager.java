package io.github.coco.config;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.registry.CocoFeaturePlan;
import io.github.coco.feature.registry.CocoFeatureSelection;
import io.github.coco.feature.registry.StandardCocoFeatures;

/**
 * 默认 Coco 功能管理器。
 * <p>
 * 根据显式排除项和标准功能依赖关系，计算最终启用与排除的功能集合。
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
public final class DefaultCocoFeatureManager implements CocoFeatureManager {

    private final CocoFeaturePlan featurePlan;

    private final Set<CocoFeature> enabledFeatures;

    private final Set<CocoFeature> excludedFeatures;

    public DefaultCocoFeatureManager(Set<CocoFeature> excludedFeatures) {
        this(StandardCocoFeatures.resolve(CocoFeatureSelection.ofDisabled(excludedFeatures)));
    }

    public DefaultCocoFeatureManager(CocoFeaturePlan featurePlan) {
        this.featurePlan = Objects.requireNonNull(featurePlan, "featurePlan must not be null");
        this.enabledFeatures = this.featurePlan.enabledFeatures();
        EnumSet<CocoFeature> resolvedExcluded = EnumSet.allOf(CocoFeature.class);
        resolvedExcluded.removeAll(this.enabledFeatures);
        this.excludedFeatures = Set.copyOf(resolvedExcluded);
    }

    @Override
    public boolean isEnabled(CocoFeature feature) {
        return this.enabledFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }

    @Override
    public Set<CocoFeature> enabledFeatures() {
        return this.enabledFeatures;
    }

    @Override
    public Set<CocoFeature> excludedFeatures() {
        return this.excludedFeatures;
    }

    public CocoFeaturePlan featurePlan() {
        return this.featurePlan;
    }
}
