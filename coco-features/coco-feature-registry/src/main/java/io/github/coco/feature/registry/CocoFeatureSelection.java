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

    public CocoFeatureSelection {
        enabled = copy(enabled);
        disabled = copy(disabled);
    }

    public static CocoFeatureSelection empty() {
        return new CocoFeatureSelection(Set.of(), Set.of());
    }

    public static CocoFeatureSelection ofEnabled(Set<CocoFeature> enabled) {
        return new CocoFeatureSelection(enabled, Set.of());
    }

    public static CocoFeatureSelection ofDisabled(Set<CocoFeature> disabled) {
        return new CocoFeatureSelection(Set.of(), disabled);
    }

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
