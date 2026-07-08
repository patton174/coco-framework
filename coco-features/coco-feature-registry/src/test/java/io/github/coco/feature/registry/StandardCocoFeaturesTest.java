package io.github.coco.feature.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.coco.api.feature.CocoFeature;
import org.junit.jupiter.api.Test;

/**
 * 标准功能元数据测试。
 * <p>
 * 验证标准功能清单、依赖声明和依赖禁用传播规则。
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
class StandardCocoFeaturesTest {

    @Test
    void registersAllStandardFeatures() {
        Set<CocoFeature> registered = StandardCocoFeatures.all().stream()
                .map(CocoFeatureDefinition::feature)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CocoFeature.class)));

        assertEquals(EnumSet.allOf(CocoFeature.class), registered);
    }

    @Test
    void declaresPlannedFeatureDependencies() {
        Map<CocoFeature, CocoFeatureDefinition> definitions = StandardCocoFeatures.allByFeature();

        assertEquals("io.github.coco.feature.web.CocoWebAutoConfiguration",
                definitions.get(CocoFeature.WEB).autoConfigurationClassName());
        assertEquals(Set.of(CocoFeature.WEB, CocoFeature.MYBATIS_PLUS),
                definitions.get(CocoFeature.AUDIT).dependencies());
        assertEquals(Set.of(CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
                definitions.get(CocoFeature.TENANT).dependencies());
        assertEquals(Set.of(CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
                definitions.get(CocoFeature.DATA_PERMISSION).dependencies());
        assertEquals(Set.of(CocoFeature.WEB, CocoFeature.SECURITY),
                definitions.get(CocoFeature.OPENAPI).dependencies());
        assertEquals(Set.of(CocoFeature.MYBATIS_PLUS),
                definitions.get(CocoFeature.CODEGEN).dependencies());
    }

    @Test
    void disablesFeaturesThatDependOnDisabledBaseFeature() {
        Set<CocoFeature> enabled = StandardCocoFeatures.resolveEnabledFeatures(Set.of(CocoFeature.MYBATIS_PLUS));

        assertFalse(enabled.contains(CocoFeature.MYBATIS_PLUS));
        assertFalse(enabled.contains(CocoFeature.AUDIT));
        assertFalse(enabled.contains(CocoFeature.TENANT));
        assertFalse(enabled.contains(CocoFeature.DATA_PERMISSION));
        assertFalse(enabled.contains(CocoFeature.CODEGEN));
        assertTrue(enabled.contains(CocoFeature.WEB));
        assertTrue(enabled.contains(CocoFeature.SECURITY));
        assertTrue(enabled.contains(CocoFeature.OPENAPI));
    }

    @Test
    void resolvesSelectionWithHigherPriorityOverrides() {
        CocoFeatureSelection applicationSelection = new CocoFeatureSelection(
                Set.of(),
                Set.of(CocoFeature.TENANT));
        CocoFeatureSelection codeSelection = new CocoFeatureSelection(
                Set.of(CocoFeature.TENANT),
                Set.of());

        CocoFeaturePlan plan = StandardCocoFeatures.resolve(applicationSelection.merge(codeSelection));

        assertTrue(plan.enabledFeatures().contains(CocoFeature.TENANT));
    }

    @Test
    void disabledFeatureWinsWithinSameSelection() {
        CocoFeatureSelection selection = new CocoFeatureSelection(
                Set.of(CocoFeature.OPENAPI),
                Set.of(CocoFeature.OPENAPI));

        CocoFeaturePlan plan = StandardCocoFeatures.resolve(selection);

        assertFalse(plan.enabledFeatures().contains(CocoFeature.OPENAPI));
        assertTrue(plan.disabledFeatures().contains(CocoFeature.OPENAPI));
    }

    @Test
    void writesAndReadsFeatureManifest() {
        CocoFeaturePlan plan = StandardCocoFeatures.resolve(
                CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION)));

        String json = CocoFeatureManifestLoader.write(StandardCocoFeatures.toManifest(plan, "test"));
        CocoFeatureManifest manifest = CocoFeatureManifestLoader.read(
                new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        CocoFeaturePlan loadedPlan = StandardCocoFeatures.fromManifest(manifest);

        assertEquals(CocoFeatureManifest.CURRENT_SCHEMA_VERSION, manifest.schemaVersion());
        assertFalse(loadedPlan.enabledFeatures().contains(CocoFeature.TENANT));
        assertFalse(loadedPlan.enabledFeatures().contains(CocoFeature.DATA_PERMISSION));
        assertTrue(loadedPlan.enabledFeatures().contains(CocoFeature.WEB));
    }

    @Test
    void rejectsUnsupportedFeatureManifestSchemaVersion() {
        CocoFeatureManifest manifest = new CocoFeatureManifest("2.0", "test", List.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> StandardCocoFeatures.fromManifest(manifest));

        assertEquals("Unsupported Coco feature manifest schema version '2.0'. Supported schema version: 1.0.",
                exception.getMessage());
    }

    @Test
    void rejectsUnknownFeatureManifestEntry() {
        CocoFeatureManifest manifest = new CocoFeatureManifest(CocoFeatureManifest.CURRENT_SCHEMA_VERSION, "test",
                List.of(new CocoFeatureManifestEntry("wrong-feature", "wrong-artifact",
                        "com.example.WrongAutoConfiguration", true, true, List.of())));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> StandardCocoFeatures.fromManifest(manifest));

        assertEquals("Unknown Coco feature id 'wrong-feature' in feature manifest.", exception.getMessage());
    }

    @Test
    void rejectsDuplicateFeatureManifestEntry() {
        CocoFeatureManifest manifest = new CocoFeatureManifest(CocoFeatureManifest.CURRENT_SCHEMA_VERSION, "test",
                List.of(
                        new CocoFeatureManifestEntry("web", "coco-feature-web",
                                "io.github.coco.feature.web.CocoWebAutoConfiguration", true, true, List.of()),
                        new CocoFeatureManifestEntry("web", "coco-feature-web",
                                "io.github.coco.feature.web.CocoWebAutoConfiguration", true, true, List.of())));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> StandardCocoFeatures.fromManifest(manifest));

        assertEquals("Duplicate Coco feature manifest entry 'web'.", exception.getMessage());
    }
}
