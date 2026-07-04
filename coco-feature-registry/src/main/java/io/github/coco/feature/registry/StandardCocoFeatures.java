package io.github.coco.feature.registry;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.coco.api.feature.CocoFeature;

/**
 * Standard Coco feature metadata used by build-time assembly.
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
