package io.github.coco.feature.model;

import java.util.EnumSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *   <li>模块：{@code coco-feature-model}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class StandardCocoFeatures {

    private static final List<CocoFeatureDefinition> FEATURES = List.of(
            feature(CocoFeature.WEB, "coco-web",
                    "io.github.coco.feature.web.CocoWebAutoConfiguration",
                    Set.of("coco-web", "coco-feature-web")),
            feature(CocoFeature.MYBATIS_PLUS, "coco-mybatis-plus",
                    "io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration",
                    Set.of("coco-mybatis-plus", "coco-feature-mybatis-plus")),
            feature(CocoFeature.AUDIT, "coco-audit",
                    "io.github.coco.feature.audit.CocoAuditAutoConfiguration",
                    Set.of("coco-audit", "coco-feature-audit")),
            feature(CocoFeature.SECURITY, "coco-security",
                    "io.github.coco.feature.security.CocoSecurityAutoConfiguration",
                    Set.of("coco-security", "coco-feature-security")),
            feature(CocoFeature.TENANT, "coco-tenant",
                    "io.github.coco.feature.tenant.CocoTenantAutoConfiguration",
                    Set.of("coco-tenant", "coco-feature-tenant"),
                    CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
            feature(CocoFeature.DATA_PERMISSION, "coco-data-permission",
                    "io.github.coco.feature.datapermission.CocoDataPermissionAutoConfiguration",
                    Set.of("coco-data-permission", "coco-feature-data-permission"),
                    CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
            feature(CocoFeature.OPENAPI, "coco-openapi",
                    "io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration",
                    Set.of("coco-openapi", "coco-feature-openapi"),
                    CocoFeature.WEB, CocoFeature.SECURITY),
            feature(CocoFeature.CODEGEN, "coco-feature-codegen",
                    "io.github.coco.feature.codegen.CocoCodegenAutoConfiguration",
                    Set.of("coco-feature-codegen"),
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
     * 返回功能定义对应的 canonical 与 2.x 兼容 artifactId。
     * </p>
     * <p>
     * 第三方传递裁剪项不会被视为 Coco 功能制品。
     * </p>
     * @param definition 功能定义
     * @return 等价 Coco 制品 artifactId
     */
    public static Set<String> equivalentArtifactIds(CocoFeatureDefinition definition) {
        CocoFeatureDefinition checkedDefinition = Objects.requireNonNull(definition,
                "definition must not be null");
        Set<String> artifactIds = new HashSet<>();
        artifactIds.add(checkedDefinition.artifactId());
        checkedDefinition.pruneArtifactIds().stream()
                .filter(artifactId -> artifactId.startsWith("coco-"))
                .forEach(artifactIds::add);
        return Set.copyOf(artifactIds);
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
        return new CocoFeaturePlan(enabled, disabled, FEATURES, requestedSelection.disabled());
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
                                .toList(),
                        definition.pruneArtifactIds().stream()
                                .sorted()
                                .toList()))
                .toList();
        List<String> explicitlyDisabledFeatureIds = targetPlan.explicitlyDisabledFeatures().stream()
                .map(CocoFeature::id)
                .sorted()
                .toList();
        return new CocoFeatureManifest(CocoFeatureManifest.CURRENT_SCHEMA_VERSION, generatedBy, entries,
                explicitlyDisabledFeatureIds);
    }

    /**
     * <p>
     * 从构建期功能清单还原最终功能启用计划。
     * </p>
     * <p>
     * 清单中的启用状态表示构建产物实际可用的能力，不代表最终运行态；最终运行计划由
     * {@link #resolveRuntimePlan(CocoFeatureManifest, CocoFeatureSelection)} 在该边界内继续解析。
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
        if (manifest.explicitlyDisabledFeatureIds() == null) {
            return new CocoFeaturePlan(enabled, disabled, FEATURES);
        }
        Set<CocoFeature> explicitlyDisabled = manifest.explicitlyDisabledFeatureIds().stream()
                .map(StandardCocoFeatures::requireManifestFeature)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CocoFeature.class)));
        return new CocoFeaturePlan(enabled, disabled, FEATURES, explicitlyDisabled);
    }

    /**
     * <p>
     * 在构建清单给出的能力可用边界内解析运行期功能计划。
     * </p>
     * <p>
     * 清单中的启用集合表示构建产物实际携带的能力。运行期配置可以继续缩小该集合，但不能重新启用构建时
     * 已裁剪的能力；发生冲突时会立即失败，避免条件装配与实际 classpath 不一致。
     * </p>
     * @param manifest 构建期功能清单；为空时按无构建清单模式解析
     * @param runtimeSelection profile、外部配置、命令行或启动早期代码声明形成的运行期选择
     * @return 最终运行期功能计划
     * @throws IllegalStateException 运行期请求启用构建产物中不可用的能力时抛出
     */
    public static CocoFeaturePlan resolveRuntimePlan(CocoFeatureManifest manifest,
            CocoFeatureSelection runtimeSelection) {
        CocoFeatureSelection requestedSelection = runtimeSelection == null
                ? CocoFeatureSelection.empty()
                : runtimeSelection;
        if (manifest == null) {
            return resolve(requestedSelection);
        }

        CocoFeaturePlan availabilityPlan = fromManifest(manifest);
        EnumSet<CocoFeature> unavailable = EnumSet.noneOf(CocoFeature.class);
        unavailable.addAll(requestedSelection.enabled());
        unavailable.removeAll(requestedSelection.disabled());
        unavailable.removeAll(availabilityPlan.enabledFeatures());
        if (!unavailable.isEmpty()) {
            String featureIds = unavailable.stream()
                    .map(CocoFeature::id)
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Coco features are not available in the build feature manifest: "
                    + featureIds + ". Rebuild the application with these features available.");
        }

        CocoFeatureSelection buildAvailability = manifest.explicitlyDisabledFeatureIds() == null
                ? CocoFeatureSelection.of(availabilityPlan.enabledFeatures(), availabilityPlan.disabledFeatures())
                : CocoFeatureSelection.of(availabilityPlan.enabledFeatures(),
                        availabilityPlan.explicitlyDisabledFeatures());
        return resolve(buildAvailability.merge(requestedSelection));
    }

    /**
     * <p>
     * 校验功能清单的 schema、唯一性、标准元数据和启用依赖闭包。
     * </p>
     * <p>
     * 当前 schema 必须包含全部标准功能；1.0 清单继续允许稀疏条目，但已出现的条目仍必须与当前
     * old/canonical 等价坐标和稳定 FQCN 一致。
     * </p>
     * @param manifest 待校验的功能清单
     * @throws IllegalArgumentException 清单不完整或与标准功能定义不一致时抛出
     */
    public static void validateManifest(CocoFeatureManifest manifest) {
        Objects.requireNonNull(manifest, "manifest must not be null");
        if (!CocoFeatureManifest.SUPPORTED_SCHEMA_VERSIONS.contains(manifest.schemaVersion())) {
            throw new IllegalArgumentException("Unsupported Coco feature manifest schema version '"
                    + manifest.schemaVersion() + "'. Supported schema versions: "
                    + CocoFeatureManifest.SUPPORTED_SCHEMA_VERSIONS + ".");
        }
        Map<CocoFeature, CocoFeatureDefinition> definitions = allByFeature();
        Set<String> featureIds = new HashSet<>();
        Map<CocoFeature, CocoFeatureManifestEntry> entriesByFeature = new java.util.EnumMap<>(CocoFeature.class);
        for (CocoFeatureManifestEntry entry : manifest.features()) {
            CocoFeature feature = requireManifestFeature(entry.id());
            if (!featureIds.add(entry.id())) {
                throw new IllegalArgumentException("Duplicate Coco feature manifest entry '" + entry.id() + "'.");
            }
            CocoFeatureDefinition definition = definitions.get(feature);
            validateManifestEntry(entry, definition);
            entriesByFeature.put(feature, entry);
        }
        if (CocoFeatureManifest.CURRENT_SCHEMA_VERSION.equals(manifest.schemaVersion())
                && entriesByFeature.size() != FEATURES.size()) {
            Set<String> missing = FEATURES.stream()
                    .map(CocoFeatureDefinition::feature)
                    .filter(feature -> !entriesByFeature.containsKey(feature))
                    .map(CocoFeature::id)
                    .collect(Collectors.toCollection(java.util.TreeSet::new));
            throw new IllegalArgumentException("Coco feature manifest schema '"
                    + CocoFeatureManifest.CURRENT_SCHEMA_VERSION + "' is missing standard features: "
                    + String.join(", ", missing) + ".");
        }
        for (Map.Entry<CocoFeature, CocoFeatureManifestEntry> manifestEntry : entriesByFeature.entrySet()) {
            if (!manifestEntry.getValue().enabled()) {
                continue;
            }
            for (CocoFeature dependency : definitions.get(manifestEntry.getKey()).dependencies()) {
                CocoFeatureManifestEntry dependencyEntry = entriesByFeature.get(dependency);
                if (dependencyEntry == null || !dependencyEntry.enabled()) {
                    throw new IllegalArgumentException("Enabled Coco feature manifest entry '"
                            + manifestEntry.getKey().id() + "' requires enabled feature '"
                            + dependency.id() + "'.");
                }
            }
        }
        validateExplicitlyDisabledFeatureIds(manifest, entriesByFeature);
    }

    private static void validateManifestEntry(CocoFeatureManifestEntry entry,
            CocoFeatureDefinition definition) {
        Set<String> equivalentArtifactIds = equivalentArtifactIds(definition);
        if (!equivalentArtifactIds.contains(entry.artifactId())) {
            throw inconsistentManifestEntry(entry.id(), "artifactId", entry.artifactId(), equivalentArtifactIds);
        }
        if (!definition.autoConfigurationClassName().equals(entry.autoConfigurationClassName())) {
            throw inconsistentManifestEntry(entry.id(), "autoConfigurationClassName",
                    entry.autoConfigurationClassName(), Set.of(definition.autoConfigurationClassName()));
        }
        if (definition.defaultEnabled() != entry.defaultEnabled()) {
            throw inconsistentManifestEntry(entry.id(), "defaultEnabled", entry.defaultEnabled(),
                    Set.of(definition.defaultEnabled()));
        }
        Set<String> expectedDependencies = definition.dependencies().stream()
                .map(CocoFeature::id)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> actualDependencies = uniqueManifestValues(entry.id(), "dependencies", entry.dependencies());
        if (!expectedDependencies.equals(actualDependencies)) {
            throw inconsistentManifestEntry(entry.id(), "dependencies", actualDependencies, expectedDependencies);
        }
        Set<String> pruneArtifactIds = uniqueManifestValues(entry.id(), "pruneArtifactIds",
                entry.pruneArtifactIds());
        if (!pruneArtifactIds.contains(entry.artifactId())
                || !equivalentArtifactIds.containsAll(pruneArtifactIds)) {
            throw inconsistentManifestEntry(entry.id(), "pruneArtifactIds", pruneArtifactIds,
                    equivalentArtifactIds);
        }
    }

    private static Set<String> uniqueManifestValues(String featureId, String fieldName, List<String> values) {
        Set<String> uniqueValues = new HashSet<>(values);
        if (uniqueValues.size() != values.size()) {
            throw new IllegalArgumentException("Duplicate value in Coco feature manifest entry '"
                    + featureId + "' field '" + fieldName + "'.");
        }
        return Set.copyOf(uniqueValues);
    }

    private static void validateExplicitlyDisabledFeatureIds(CocoFeatureManifest manifest,
            Map<CocoFeature, CocoFeatureManifestEntry> entriesByFeature) {
        if (manifest.explicitlyDisabledFeatureIds() == null) {
            return;
        }
        Set<String> explicitlyDisabledFeatureIds = uniqueManifestValues("manifest", "explicitlyDisabledFeatureIds",
                manifest.explicitlyDisabledFeatureIds());
        for (String featureId : explicitlyDisabledFeatureIds) {
            CocoFeature feature = requireManifestFeature(featureId);
            CocoFeatureManifestEntry entry = entriesByFeature.get(feature);
            if (entry == null || entry.enabled()) {
                throw new IllegalArgumentException("Explicitly disabled Coco feature manifest entry '" + featureId
                        + "' must be present and disabled.");
            }
        }
    }

    private static IllegalArgumentException inconsistentManifestEntry(String featureId, String fieldName,
            Object actual, Set<?> expected) {
        return new IllegalArgumentException("Coco feature manifest entry '" + featureId + "' has inconsistent "
                + fieldName + " '" + actual + "'; expected " + expected + ".");
    }

    private static CocoFeature requireManifestFeature(String featureId) {
        return CocoFeature.fromId(featureId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Coco feature id '" + featureId
                        + "' in feature manifest."));
    }

    private static CocoFeatureDefinition feature(CocoFeature feature, String artifactId, String autoConfigurationClassName,
            Set<String> pruneArtifactIds, CocoFeature... dependencies) {
        return new CocoFeatureDefinition(feature, artifactId, autoConfigurationClassName, true, dependencies.length == 0
                ? Set.of()
                : Set.of(dependencies), pruneArtifactIds);
    }
}
