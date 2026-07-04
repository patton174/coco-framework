package io.github.coco.feature.registry;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.coco.api.feature.CocoFeature;

/**
 * # Coco 标准功能元数据
 *
 * - **作者**: [patton174](https://github.com/patton174)
 * - **仓库**: [coco-framework](https://github.com/patton174/coco-framework)
 * - **模块**: `coco-feature-registry`
 *
 * 维护框架标准功能清单，并根据排除项递归计算最终启用的功能集合。
 *
 * @author patton174
 * @since 1.0.0
 */
public final class StandardCocoFeatures {

    private static final List<CocoFeatureDefinition> FEATURES = List.of(
            feature(CocoFeature.WEB, "coco-feature-web"),
            feature(CocoFeature.MYBATIS_PLUS, "coco-feature-mybatis-plus"),
            feature(CocoFeature.AUDIT, "coco-feature-audit", CocoFeature.WEB, CocoFeature.MYBATIS_PLUS),
            feature(CocoFeature.SECURITY, "coco-feature-security"),
            feature(CocoFeature.TENANT, "coco-feature-tenant", CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
            feature(CocoFeature.DATA_PERMISSION, "coco-feature-data-permission",
                    CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
            feature(CocoFeature.OPENAPI, "coco-feature-openapi", CocoFeature.WEB, CocoFeature.SECURITY),
            feature(CocoFeature.CODEGEN, "coco-feature-codegen", CocoFeature.MYBATIS_PLUS)
    );

    private StandardCocoFeatures() {
    }

    public static List<CocoFeatureDefinition> all() {
        return FEATURES;
    }

    public static Map<CocoFeature, CocoFeatureDefinition> allByFeature() {
        return FEATURES.stream()
                .collect(Collectors.toUnmodifiableMap(CocoFeatureDefinition::feature, Function.identity()));
    }

    public static Set<CocoFeature> resolveEnabled(Set<CocoFeature> excluded) {
        EnumSet<CocoFeature> resolvedExcluded = excluded == null || excluded.isEmpty()
                ? EnumSet.noneOf(CocoFeature.class)
                : EnumSet.copyOf(excluded);

        boolean changed;
        do {
            changed = false;
            for (CocoFeatureDefinition definition : FEATURES) {
                if (!resolvedExcluded.contains(definition.feature())
                        && resolvedExcluded.stream().anyMatch(definition.dependencies()::contains)) {
                    changed = resolvedExcluded.add(definition.feature());
                }
            }
        }
        while (changed);

        EnumSet<CocoFeature> enabled = EnumSet.allOf(CocoFeature.class);
        enabled.removeAll(resolvedExcluded);
        return Set.copyOf(enabled);
    }

    private static CocoFeatureDefinition feature(CocoFeature feature, String artifactId, CocoFeature... dependencies) {
        return new CocoFeatureDefinition(feature, artifactId, true, dependencies.length == 0
                ? Set.of()
                : Set.of(dependencies));
    }
}
