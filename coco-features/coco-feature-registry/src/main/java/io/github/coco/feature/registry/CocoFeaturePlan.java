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

    /**
     * <p>
     * 创建最终功能启用计划，并复制为不可变集合。
     * </p>
     * @param enabledFeatures 最终启用的功能集合
     * @param disabledFeatures 最终禁用的功能集合
     * @param definitions 标准功能定义列表
     */
    public CocoFeaturePlan {
        enabledFeatures = Set.copyOf(Objects.requireNonNull(enabledFeatures, "enabledFeatures must not be null"));
        disabledFeatures = Set.copyOf(Objects.requireNonNull(disabledFeatures, "disabledFeatures must not be null"));
        definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions must not be null"));
    }

    /**
     * <p>
     * 判断指定功能是否在最终计划中启用。
     * </p>
     * @param feature 需要判断的功能
     * @return 功能启用时返回 {@code true}
     */
    public boolean isEnabled(CocoFeature feature) {
        return this.enabledFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }
}
