package io.github.coco.feature.registry;

import java.util.EnumSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.coco.api.feature.CocoFeature;

/**
 * Coco 标准功能元数据。
 * <p>
 * 维护框架标准功能清单，并根据排除项递归计算最终启用的功能集合。
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
public final class StandardCocoFeatures {

    private static final List<CocoFeatureDefinition> FEATURES = List.of(
            feature(CocoFeature.WEB, "coco-feature-web",
                    "io.github.coco.feature.web.CocoWebAutoConfiguration"),
            feature(CocoFeature.MYBATIS_PLUS, "coco-feature-mybatis-plus",
                    "io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration"),
            feature(CocoFeature.AUDIT, "coco-feature-audit",
                    "io.github.coco.feature.audit.CocoAuditAutoConfiguration",
                    CocoFeature.WEB, CocoFeature.MYBATIS_PLUS),
            feature(CocoFeature.SECURITY, "coco-feature-security",
                    "io.github.coco.feature.security.CocoSecurityAutoConfiguration"),
            feature(CocoFeature.TENANT, "coco-feature-tenant",
                    "io.github.coco.feature.tenant.CocoTenantAutoConfiguration",
                    CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
            feature(CocoFeature.DATA_PERMISSION, "coco-feature-data-permission",
                    "io.github.coco.feature.datapermission.CocoDataPermissionAutoConfiguration",
                    CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
            feature(CocoFeature.OPENAPI, "coco-feature-openapi",
                    "io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration",
                    CocoFeature.WEB, CocoFeature.SECURITY),
            feature(CocoFeature.CODEGEN, "coco-feature-codegen",
                    "io.github.coco.feature.codegen.CocoCodegenAutoConfiguration",
                    CocoFeature.MYBATIS_PLUS)
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
        return resolve(CocoFeatureSelection.ofDisabled(excluded)).enabledFeatures();
    }

    public static CocoFeaturePlan resolve(CocoFeatureSelection selection) {
        CocoFeatureSelection requestedSelection = selection == null ? CocoFeatureSelection.empty() : selection;
        EnumSet<CocoFeature> enabled = EnumSet.noneOf(CocoFeature.class);
        FEATURES.stream()
                .filter(CocoFeatureDefinition::defaultEnabled)
                .map(CocoFeatureDefinition::feature)
                .forEach(enabled::add);
        enabled.addAll(requestedSelection.enabled());
        enabled.removeAll(requestedSelection.disabled());

        boolean changed;
        do {
            changed = false;
            for (CocoFeatureDefinition definition : FEATURES) {
                if (enabled.contains(definition.feature()) && !enabled.containsAll(definition.dependencies())) {
                    changed = enabled.remove(definition.feature());
                }
            }
        }
        while (changed);

        EnumSet<CocoFeature> disabled = EnumSet.allOf(CocoFeature.class);
        disabled.removeAll(enabled);
        return new CocoFeaturePlan(enabled, disabled, FEATURES);
    }

    public static CocoFeatureManifest toManifest(CocoFeaturePlan plan, String generatedBy) {
        CocoFeaturePlan targetPlan = plan == null ? resolve(CocoFeatureSelection.empty()) : plan;
        List<CocoFeatureManifestEntry> entries = targetPlan.definitions().stream()
                .sorted(Comparator.comparing(definition -> definition.feature().id()))
                .map(definition -> new CocoFeatureManifestEntry(
                        definition.feature().id(),
                        definition.artifactId(),
                        definition.autoConfigurationClassName(),
                        definition.defaultEnabled(),
                        targetPlan.isEnabled(definition.feature()),
                        definition.dependencies().stream()
                                .map(CocoFeature::id)
                                .sorted()
                                .toList()))
                .toList();
        return new CocoFeatureManifest(CocoFeatureManifest.CURRENT_SCHEMA_VERSION, generatedBy, entries);
    }

    public static CocoFeaturePlan fromManifest(CocoFeatureManifest manifest) {
        if (manifest == null) {
            return resolve(CocoFeatureSelection.empty());
        }
        Set<CocoFeature> enabled = manifest.features().stream()
                .filter(CocoFeatureManifestEntry::enabled)
                .map(CocoFeatureManifestEntry::id)
                .map(CocoFeature::fromId)
                .flatMap(Optional::stream)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CocoFeature.class)));
        EnumSet<CocoFeature> disabled = EnumSet.allOf(CocoFeature.class);
        disabled.removeAll(enabled);
        return new CocoFeaturePlan(enabled, disabled, FEATURES);
    }

    private static CocoFeatureDefinition feature(CocoFeature feature, String artifactId, String autoConfigurationClassName,
            CocoFeature... dependencies) {
        return new CocoFeatureDefinition(feature, artifactId, autoConfigurationClassName, true, dependencies.length == 0
                ? Set.of()
                : Set.of(dependencies));
    }
}
