package io.github.coco.spring.boot.autoconfigure.feature;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.model.CocoFeatureSelection;
import io.github.coco.feature.model.StandardCocoFeatures;

/**
 * 默认 Coco 功能管理器。
 * <p>
 * 根据显式禁用项和标准功能依赖关系，计算最终启用与禁用的功能集合。
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
public final class DefaultCocoFeatureManager implements CocoFeatureManager {

    private final CocoFeaturePlan featurePlan;

    private final Set<CocoFeature> enabledFeatures;

    private final Set<CocoFeature> disabledFeatures;

    /**
     * <p>
     * 基于显式禁用功能集合创建功能管理器。
     * </p>
     * @param disabledFeatures 显式禁用的功能集合
     */
    public DefaultCocoFeatureManager(Set<CocoFeature> disabledFeatures) {
        this(StandardCocoFeatures.resolve(CocoFeatureSelection.ofDisabled(disabledFeatures)));
    }

    /**
     * <p>
     * 基于最终功能启用计划创建功能管理器。
     * </p>
     * @param featurePlan 最终功能启用计划
     */
    public DefaultCocoFeatureManager(CocoFeaturePlan featurePlan) {
        this.featurePlan = Objects.requireNonNull(featurePlan, "featurePlan must not be null");
        this.enabledFeatures = this.featurePlan.enabledFeatures();
        EnumSet<CocoFeature> resolvedDisabled = EnumSet.allOf(CocoFeature.class);
        resolvedDisabled.removeAll(this.enabledFeatures);
        this.disabledFeatures = Set.copyOf(resolvedDisabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled(CocoFeature feature) {
        return this.enabledFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<CocoFeature> enabledFeatures() {
        return this.enabledFeatures;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<CocoFeature> disabledFeatures() {
        return this.disabledFeatures;
    }

    /**
     * <p>
     * 返回当前管理器持有的完整功能计划。
     * </p>
     * @return 功能启用计划
     */
    public CocoFeaturePlan featurePlan() {
        return this.featurePlan;
    }
}
