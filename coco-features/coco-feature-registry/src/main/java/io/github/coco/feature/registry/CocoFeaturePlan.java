package io.github.coco.feature.registry;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;

/**
 * Coco 功能启用计划。
 * <p>
 * 表示功能选择经过默认策略和依赖传播后的最终结果，供构建期和运行期共同使用。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-registry}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public record CocoFeaturePlan(
        Set<CocoFeature> enabledFeatures,
        Set<CocoFeature> disabledFeatures,
        List<CocoFeatureDefinition> definitions) {

    public CocoFeaturePlan {
        enabledFeatures = Set.copyOf(Objects.requireNonNull(enabledFeatures, "enabledFeatures must not be null"));
        disabledFeatures = Set.copyOf(Objects.requireNonNull(disabledFeatures, "disabledFeatures must not be null"));
        definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions must not be null"));
    }

    public boolean isEnabled(CocoFeature feature) {
        return this.enabledFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }
}
