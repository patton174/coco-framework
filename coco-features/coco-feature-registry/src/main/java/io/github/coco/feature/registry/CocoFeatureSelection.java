package io.github.coco.feature.registry;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;

/**
 * Coco 功能选择声明。
 * <p>
 * 描述某个配置源显式启用和禁用的功能集合，不直接执行依赖传播。
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
public record CocoFeatureSelection(Set<CocoFeature> enabled, Set<CocoFeature> disabled) {

    /**
     * <p>
     * 创建功能选择声明，并将输入集合复制为不可变集合。
     * </p>
     * @param enabled 显式启用的功能集合
     * @param disabled 显式禁用的功能集合
     */
    public CocoFeatureSelection {
        enabled = copy(enabled);
        disabled = copy(disabled);
    }

    /**
     * <p>
     * 创建不包含任何显式启用或禁用声明的选择。
     * </p>
     * @return 空功能选择
     */
    public static CocoFeatureSelection empty() {
        return new CocoFeatureSelection(Set.of(), Set.of());
    }

    /**
     * <p>
     * 创建只包含启用声明的功能选择。
     * </p>
     * @param enabled 显式启用的功能集合
     * @return 功能选择
     */
    public static CocoFeatureSelection ofEnabled(Set<CocoFeature> enabled) {
        return new CocoFeatureSelection(enabled, Set.of());
    }

    /**
     * <p>
     * 创建只包含禁用声明的功能选择。
     * </p>
     * @param disabled 显式禁用的功能集合
     * @return 功能选择
     */
    public static CocoFeatureSelection ofDisabled(Set<CocoFeature> disabled) {
        return new CocoFeatureSelection(Set.of(), disabled);
    }

    /**
     * <p>
     * 合并两个不同优先级的功能选择声明。
     * </p>
     * <p>
     * 高优先级声明会覆盖低优先级声明；在同一选择内部，禁用声明会覆盖启用声明。
     * </p>
     * @param lowerPriority 低优先级选择
     * @param higherPriority 高优先级选择
     * @return 合并后的功能选择
     */
    public static CocoFeatureSelection merge(CocoFeatureSelection lowerPriority,
            CocoFeatureSelection higherPriority) {
        EnumMap<CocoFeature, Boolean> directives = new EnumMap<>(CocoFeature.class);
        apply(directives, lowerPriority);
        apply(directives, higherPriority);

        EnumSet<CocoFeature> mergedEnabled = EnumSet.noneOf(CocoFeature.class);
        EnumSet<CocoFeature> mergedDisabled = EnumSet.noneOf(CocoFeature.class);
        directives.forEach((feature, enabled) -> {
            if (Boolean.TRUE.equals(enabled)) {
                mergedEnabled.add(feature);
            }
            else {
                mergedDisabled.add(feature);
            }
        });
        return new CocoFeatureSelection(mergedEnabled, mergedDisabled);
    }

    /**
     * <p>
     * 将当前选择与更高优先级选择合并。
     * </p>
     * @param higherPriority 高优先级选择
     * @return 合并后的功能选择
     */
    public CocoFeatureSelection merge(CocoFeatureSelection higherPriority) {
        return merge(this, higherPriority);
    }

    private static void apply(EnumMap<CocoFeature, Boolean> directives, CocoFeatureSelection selection) {
        if (selection == null) {
            return;
        }
        selection.enabled().forEach(feature -> directives.put(feature, true));
        selection.disabled().forEach(feature -> directives.put(feature, false));
    }

    private static Set<CocoFeature> copy(Set<CocoFeature> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(EnumSet.copyOf(source));
    }
}
