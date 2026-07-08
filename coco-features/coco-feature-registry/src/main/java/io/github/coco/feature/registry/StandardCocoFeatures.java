package io.github.coco.feature.registry;

import java.util.EnumSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.coco.api.feature.CocoFeature;

/**
 * Coco 标准功能元数据。
 * <p>
 * 维护框架标准功能清单，并根据显式禁用项递归计算最终启用的功能集合。
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

    /**
     * <p>
     * 返回框架内置的全部标准功能定义。
     * </p>
     * @return 标准功能定义列表
     */
    public static List<CocoFeatureDefinition> all() {
        return FEATURES;
    }

    /**
     * <p>
     * 按功能枚举返回标准功能定义映射。
     * </p>
     * @return 以功能枚举为键的定义映射
     */
    public static Map<CocoFeature, CocoFeatureDefinition> allByFeature() {
        return FEATURES.stream()
                .collect(Collectors.toUnmodifiableMap(CocoFeatureDefinition::feature, Function.identity()));
    }

    /**
     * <p>
     * 根据显式禁用集合计算最终启用的功能集合。
     * </p>
     * @param disabled 显式禁用的功能集合
     * @return 经过依赖传播后的最终启用功能集合
     */
    public static Set<CocoFeature> resolveEnabledFeatures(Set<CocoFeature> disabled) {
        return resolve(CocoFeatureSelection.ofDisabled(disabled)).enabledFeatures();
    }

    /**
     * <p>
     * 根据功能选择声明计算最终功能启用计划。
     * </p>
     * <p>
     * 解析流程会先应用默认启用功能，再合并显式启用和禁用声明，最后递归移除依赖不完整的功能。
     * </p>
     * @param selection 功能选择声明
     * @return 最终功能启用计划
     */
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

    /**
     * <p>
     * 将最终功能启用计划转换为构建期功能清单。
     * </p>
     * @param plan 最终功能启用计划
     * @param generatedBy 清单生成来源
     * @return 可写入业务应用产物的功能清单
     */
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

    /**
     * <p>
     * 从构建期功能清单还原最终功能启用计划。
     * </p>
     * <p>
     * 清单中的启用状态是构建期已经计算完成的结果，运行期不再重复合并业务配置。
     * </p>
     * @param manifest 构建期功能清单
     * @return 最终功能启用计划
     * @throws IllegalArgumentException 清单结构版本不支持、包含未知功能标识或重复功能条目时抛出
     */
    public static CocoFeaturePlan fromManifest(CocoFeatureManifest manifest) {
        if (manifest == null) {
            return resolve(CocoFeatureSelection.empty());
        }
        validateManifest(manifest);
        Set<CocoFeature> enabled = manifest.features().stream()
                .filter(CocoFeatureManifestEntry::enabled)
                .map(CocoFeatureManifestEntry::id)
                .map(StandardCocoFeatures::requireManifestFeature)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CocoFeature.class)));
        EnumSet<CocoFeature> disabled = EnumSet.allOf(CocoFeature.class);
        disabled.removeAll(enabled);
        return new CocoFeaturePlan(enabled, disabled, FEATURES);
    }

    private static void validateManifest(CocoFeatureManifest manifest) {
        if (!CocoFeatureManifest.CURRENT_SCHEMA_VERSION.equals(manifest.schemaVersion())) {
            throw new IllegalArgumentException("Unsupported Coco feature manifest schema version '"
                    + manifest.schemaVersion() + "'. Supported schema version: "
                    + CocoFeatureManifest.CURRENT_SCHEMA_VERSION + ".");
        }
        Set<String> featureIds = new HashSet<>();
        for (CocoFeatureManifestEntry entry : manifest.features()) {
            requireManifestFeature(entry.id());
            if (!featureIds.add(entry.id())) {
                throw new IllegalArgumentException("Duplicate Coco feature manifest entry '" + entry.id() + "'.");
            }
        }
    }

    private static CocoFeature requireManifestFeature(String featureId) {
        return CocoFeature.fromId(featureId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Coco feature id '" + featureId
                        + "' in feature manifest."));
    }

    private static CocoFeatureDefinition feature(CocoFeature feature, String artifactId, String autoConfigurationClassName,
            CocoFeature... dependencies) {
        return new CocoFeatureDefinition(feature, artifactId, autoConfigurationClassName, true, dependencies.length == 0
                ? Set.of()
                : Set.of(dependencies));
    }
}
