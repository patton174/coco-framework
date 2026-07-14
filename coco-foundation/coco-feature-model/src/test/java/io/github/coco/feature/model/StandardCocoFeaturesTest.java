package io.github.coco.feature.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.coco.api.feature.CocoFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 *   <li>模块：{@code coco-feature-model}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class StandardCocoFeaturesTest {

    @Test
    void onlyCocoOwnedArtifactsAreSafeToPrune() {
        assertTrue(StandardCocoFeatures.all().stream()
                .flatMap(definition -> definition.pruneArtifactIds().stream())
                .allMatch(artifactId -> artifactId.startsWith("coco-")));
    }

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

        assertEquals(Map.of(
                CocoFeature.WEB, "coco-web",
                CocoFeature.MYBATIS_PLUS, "coco-mybatis-plus",
                CocoFeature.AUDIT, "coco-audit",
                CocoFeature.SECURITY, "coco-security",
                CocoFeature.TENANT, "coco-tenant",
                CocoFeature.DATA_PERMISSION, "coco-data-permission",
                CocoFeature.OPENAPI, "coco-openapi",
                CocoFeature.CODEGEN, "coco-feature-codegen"),
                definitions.entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                                entry -> entry.getValue().artifactId())));
        assertEquals("io.github.coco.feature.web.CocoWebAutoConfiguration",
                definitions.get(CocoFeature.WEB).autoConfigurationClassName());
        assertEquals(Set.of(), definitions.get(CocoFeature.AUDIT).dependencies());
        assertEquals(Set.of(CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
                definitions.get(CocoFeature.TENANT).dependencies());
        assertEquals(Set.of(CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
                definitions.get(CocoFeature.DATA_PERMISSION).dependencies());
        assertEquals(Set.of(CocoFeature.WEB, CocoFeature.SECURITY),
                definitions.get(CocoFeature.OPENAPI).dependencies());
        assertEquals(Set.of(CocoFeature.MYBATIS_PLUS),
                definitions.get(CocoFeature.CODEGEN).dependencies());
        assertEquals(Set.of("coco-feature-codegen"),
                definitions.get(CocoFeature.CODEGEN).pruneArtifactIds());
    }

    @Test
    void exposesOnlyCanonicalAndCompatibilityCocoArtifactIdsAsEquivalent() {
        Map<CocoFeature, CocoFeatureDefinition> definitions = StandardCocoFeatures.allByFeature();

        assertEquals(Set.of("coco-web", "coco-feature-web"),
                StandardCocoFeatures.equivalentArtifactIds(definitions.get(CocoFeature.WEB)));
        assertEquals(Set.of("coco-mybatis-plus", "coco-feature-mybatis-plus"),
                StandardCocoFeatures.equivalentArtifactIds(definitions.get(CocoFeature.MYBATIS_PLUS)));
        assertEquals(Set.of("coco-feature-codegen"),
                StandardCocoFeatures.equivalentArtifactIds(definitions.get(CocoFeature.CODEGEN)));
    }

    @Test
    void preservesPublishedFeatureDefinitionRecordShape() {
        assertEquals(List.of(
                "feature",
                "artifactId",
                "autoConfigurationClassName",
                "defaultEnabled",
                "dependencies",
                "pruneArtifactIds"), java.util.Arrays.stream(CocoFeatureDefinition.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList());
    }

    @Test
    void disablesOnlyFeaturesThatDependOnDisabledMybatisPlus() {
        Set<CocoFeature> enabled = StandardCocoFeatures.resolveEnabledFeatures(Set.of(CocoFeature.MYBATIS_PLUS));

        assertFalse(enabled.contains(CocoFeature.MYBATIS_PLUS));
        assertTrue(enabled.contains(CocoFeature.AUDIT));
        assertFalse(enabled.contains(CocoFeature.TENANT));
        assertFalse(enabled.contains(CocoFeature.DATA_PERMISSION));
        assertFalse(enabled.contains(CocoFeature.CODEGEN));
        assertTrue(enabled.contains(CocoFeature.WEB));
        assertTrue(enabled.contains(CocoFeature.SECURITY));
        assertTrue(enabled.contains(CocoFeature.OPENAPI));
    }

    @Test
    void keepsAuditEnabledWhenWebIsDisabled() {
        Set<CocoFeature> enabled = StandardCocoFeatures.resolveEnabledFeatures(Set.of(CocoFeature.WEB));

        assertFalse(enabled.contains(CocoFeature.WEB));
        assertTrue(enabled.contains(CocoFeature.AUDIT));
        assertFalse(enabled.contains(CocoFeature.OPENAPI));
    }

    @Test
    void exposesFeaturesDisabledByMissingDependencies() {
        CocoFeaturePlan plan = StandardCocoFeatures.resolve(
                CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.MYBATIS_PLUS)));

        assertEquals(Set.of(
                CocoFeature.TENANT,
                CocoFeature.DATA_PERMISSION,
                CocoFeature.CODEGEN), plan.disabledByDependencyFeatures());
    }

    @Test
    void preservesFinalStateDependencyDiagnosticsWhenExplicitSourcesAreNotRetained() {
        CocoFeaturePlan plan = StandardCocoFeatures.resolve(
                CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.TENANT, CocoFeature.MYBATIS_PLUS)));

        assertEquals(Set.of(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION, CocoFeature.CODEGEN),
                plan.disabledByDependencyFeatures());
    }

    @Test
    void explicitDisableWithSatisfiedDependenciesIsNotDependencyDisabled() {
        CocoFeaturePlan plan = StandardCocoFeatures.resolve(
                CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.OPENAPI)));

        assertEquals(Set.of(), plan.disabledByDependencyFeatures());
    }

    @Test
    void keepsFeatureDisabledWhenAnotherSourceEnablesIt() {
        CocoFeatureSelection applicationSelection = CocoFeatureSelection.of(
                Set.of(),
                Set.of(CocoFeature.TENANT));
        CocoFeatureSelection codeSelection = CocoFeatureSelection.of(
                Set.of(CocoFeature.TENANT),
                Set.of());

        CocoFeaturePlan plan = StandardCocoFeatures.resolve(applicationSelection.merge(codeSelection));

        assertFalse(plan.enabledFeatures().contains(CocoFeature.TENANT));
        assertTrue(plan.disabledFeatures().contains(CocoFeature.TENANT));
    }

    @Test
    void disabledFeatureWinsWithinSameSelection() {
        CocoFeatureSelection selection = CocoFeatureSelection.of(
                Set.of(CocoFeature.OPENAPI),
                Set.of(CocoFeature.OPENAPI));

        CocoFeaturePlan plan = StandardCocoFeatures.resolve(selection);

        assertFalse(plan.enabledFeatures().contains(CocoFeature.OPENAPI));
        assertTrue(plan.disabledFeatures().contains(CocoFeature.OPENAPI));
    }

    @Test
    void selectionFactoryIgnoresNullAnnotationEntries() {
        CocoFeatureSelection selection = CocoFeatureSelection.of(
                new CocoFeature[] {CocoFeature.WEB, null},
                new CocoFeature[] {CocoFeature.TENANT, null});

        assertEquals(Set.of(CocoFeature.WEB), selection.enabled());
        assertEquals(Set.of(CocoFeature.TENANT), selection.disabled());
    }

    @Test
    void exposesImmutableFeatureSelectionSnapshots() {
        Set<CocoFeature> enabled = EnumSet.of(CocoFeature.WEB);
        Set<CocoFeature> disabled = EnumSet.of(CocoFeature.TENANT);

        CocoFeatureSelection selection = new CocoFeatureSelection(enabled, disabled);
        enabled.add(CocoFeature.AUDIT);
        disabled.add(CocoFeature.DATA_PERMISSION);

        assertEquals(Set.of(CocoFeature.WEB), selection.enabled());
        assertEquals(Set.of(CocoFeature.TENANT), selection.disabled());
        assertThrows(UnsupportedOperationException.class, () -> selection.enabled().add(CocoFeature.AUDIT));
        assertThrows(UnsupportedOperationException.class, () -> selection.disabled().add(CocoFeature.DATA_PERMISSION));
    }

    @Test
    void preservesPublishedFeatureSelectionRecordApi() throws ReflectiveOperationException {
        Class<?> selectionType = Class.forName("io.github.coco.feature.model.CocoFeatureSelection");

        assertTrue(selectionType.isRecord());
        assertEquals(List.of("enabled", "disabled"), java.util.Arrays.stream(selectionType.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList());
        assertEquals(List.of(Set.class, Set.class), java.util.Arrays.stream(selectionType.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getType)
                .toList());
        assertEquals(CocoFeatureSelection.class, selectionType.getConstructor(Set.class, Set.class).getDeclaringClass());
        assertEquals(Set.class, selectionType.getMethod("enabled").getReturnType());
        assertEquals(Set.class, selectionType.getMethod("disabled").getReturnType());
    }

    @Test
    void writesAndReadsFeatureManifest() {
        CocoFeaturePlan plan = StandardCocoFeatures.resolve(
                CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.TENANT, CocoFeature.MYBATIS_PLUS)));

        String json = CocoFeatureManifestLoader.write(StandardCocoFeatures.toManifest(plan, "test"));
        CocoFeatureManifest manifest = CocoFeatureManifestLoader.read(
                new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        CocoFeaturePlan loadedPlan = StandardCocoFeatures.fromManifest(manifest);

        assertEquals(CocoFeatureManifest.CURRENT_SCHEMA_VERSION, manifest.schemaVersion());
        assertFalse(loadedPlan.enabledFeatures().contains(CocoFeature.TENANT));
        assertFalse(loadedPlan.enabledFeatures().contains(CocoFeature.MYBATIS_PLUS));
        assertTrue(loadedPlan.enabledFeatures().contains(CocoFeature.WEB));
        assertEquals(plan, loadedPlan);
        assertEquals(List.of(
                "coco-feature-mybatis-plus",
                "coco-mybatis-plus"), manifest.features().stream()
                .filter(entry -> "mybatis-plus".equals(entry.id()))
                .findFirst()
                .orElseThrow()
                .pruneArtifactIds());
        assertEquals("coco-mybatis-plus", manifest.features().stream()
                .filter(entry -> "mybatis-plus".equals(entry.id()))
                .findFirst()
                .orElseThrow()
                .artifactId());
    }

    @Test
    void preservesPublishedPlanAndManifestRecordShapes() throws NoSuchMethodException {
        assertEquals(List.of("enabledFeatures", "disabledFeatures", "definitions"),
                recordComponentNames(CocoFeaturePlan.class));
        assertEquals(List.of(Set.class, Set.class, List.class), recordComponentTypes(CocoFeaturePlan.class));
        assertEquals(1, CocoFeaturePlan.class.getDeclaredConstructors().length);
        assertEquals(CocoFeaturePlan.class.getDeclaredConstructor(Set.class, Set.class, List.class).getParameterCount(),
                3);

        assertEquals(List.of("schemaVersion", "generatedBy", "features"),
                recordComponentNames(CocoFeatureManifest.class));
        assertEquals(List.of(String.class, String.class, List.class), recordComponentTypes(CocoFeatureManifest.class));
        assertEquals(1, CocoFeatureManifest.class.getDeclaredConstructors().length);
        assertEquals(CocoFeatureManifest.class.getDeclaredConstructor(String.class, String.class, List.class)
                .getParameterCount(), 3);
    }

    @Test
    void legacyCanonicalConstructorsRemainLinkable() throws Throwable {
        CocoFeaturePlan plan = (CocoFeaturePlan) MethodHandles.publicLookup()
                .findConstructor(CocoFeaturePlan.class, MethodType.methodType(void.class, Set.class, Set.class,
                        List.class))
                .invokeWithArguments(Set.of(CocoFeature.WEB),
                        Set.of(CocoFeature.MYBATIS_PLUS, CocoFeature.TENANT), StandardCocoFeatures.all());
        CocoFeatureManifest manifest = (CocoFeatureManifest) MethodHandles.publicLookup()
                .findConstructor(CocoFeatureManifest.class, MethodType.methodType(void.class, String.class, String.class,
                        List.class))
                .invokeWithArguments("1.1", "test", StandardCocoFeatures.toManifest(plan, "test").features());

        assertEquals(Set.of(CocoFeature.TENANT), plan.disabledByDependencyFeatures());
        assertEquals("test", manifest.generatedBy());
    }

    @Test
    void newManifestRemainsReadableByStrictLegacyLoader() throws Exception {
        CocoFeatureManifest manifest = StandardCocoFeatures.toManifest(StandardCocoFeatures.resolve(
                CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.TENANT, CocoFeature.MYBATIS_PLUS))), "test");
        String json = CocoFeatureManifestLoader.write(manifest);
        ObjectMapper strictLegacyLoader = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        CocoFeatureManifest loaded = strictLegacyLoader.readValue(json, CocoFeatureManifest.class);

        assertEquals(manifest, loaded);
        assertFalse(json.contains("explicitlyDisabled"));
    }

    private static List<String> recordComponentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static List<Class<?>> recordComponentTypes(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getType).toList();
    }

    @Test
    void writesAuditManifestEntryWithoutDependencies() {
        CocoFeaturePlan plan = StandardCocoFeatures.resolve(
                CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.WEB, CocoFeature.MYBATIS_PLUS)));

        CocoFeatureManifestEntry auditEntry = StandardCocoFeatures.toManifest(plan, "test").features().stream()
                .filter(entry -> CocoFeature.AUDIT.id().equals(entry.id()))
                .findFirst()
                .orElseThrow();

        assertTrue(auditEntry.enabled());
        assertEquals(List.of(), auditEntry.dependencies());
    }

    @Test
    void defaultsPruneArtifactIdsToFeatureArtifactId() {
        CocoFeatureDefinition definition = new CocoFeatureDefinition(CocoFeature.WEB, "coco-feature-web",
                "io.github.coco.feature.web.CocoWebAutoConfiguration", true, Set.of());
        CocoFeatureManifestEntry entry = new CocoFeatureManifestEntry("web", "coco-feature-web",
                "io.github.coco.feature.web.CocoWebAutoConfiguration", true, true, List.of());

        assertEquals(Set.of("coco-feature-web"), definition.pruneArtifactIds());
        assertEquals(List.of("coco-feature-web"), entry.pruneArtifactIds());
    }

    @Test
    void readsLegacyFeatureManifestWithoutPruneArtifactIds() {
        CocoFeatureManifest manifest = CocoFeatureManifestLoader.read(new java.io.ByteArrayInputStream("""
                {
                  "schemaVersion" : "1.0",
                  "generatedBy" : "legacy-test",
                  "features" : [ {
                    "id" : "web",
                    "artifactId" : "coco-feature-web",
                    "autoConfigurationClassName" : "io.github.coco.feature.web.CocoWebAutoConfiguration",
                    "defaultEnabled" : true,
                    "enabled" : true,
                    "dependencies" : [ ],
                    "futureField" : "ignored"
                  } ],
                  "futureRootField" : "ignored"
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertEquals("1.0", manifest.schemaVersion());
        assertEquals(List.of("coco-feature-web"), manifest.features().get(0).pruneArtifactIds());
        assertTrue(StandardCocoFeatures.fromManifest(manifest).enabledFeatures().contains(CocoFeature.WEB));
    }

    @Test
    void runtimeSelectionCanNarrowBuildManifestAvailability() {
        CocoFeatureManifest manifest = StandardCocoFeatures.toManifest(
                StandardCocoFeatures.resolve(CocoFeatureSelection.empty()), "test");

        CocoFeaturePlan runtimePlan = StandardCocoFeatures.resolveRuntimePlan(manifest,
                CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.WEB)));

        assertFalse(runtimePlan.isEnabled(CocoFeature.WEB));
        assertFalse(runtimePlan.isEnabled(CocoFeature.OPENAPI));
        assertTrue(runtimePlan.isEnabled(CocoFeature.AUDIT));
    }

    @Test
    void runtimeSelectionCannotEnableFeatureOutsideBuildManifestAvailability() {
        CocoFeatureManifest manifest = StandardCocoFeatures.toManifest(
                StandardCocoFeatures.resolve(CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.WEB))), "test");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> StandardCocoFeatures.resolveRuntimePlan(manifest,
                        CocoFeatureSelection.ofEnabled(Set.of(CocoFeature.WEB))));

        assertTrue(exception.getMessage().contains("web"));
        assertTrue(exception.getMessage().contains("not available"));
    }

    @Test
    void runtimeDisableWinsWithoutAvailabilityConflictWhenSameFeatureIsAlsoEnabled() {
        CocoFeatureManifest manifest = StandardCocoFeatures.toManifest(
                StandardCocoFeatures.resolve(CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.WEB))), "test");

        CocoFeaturePlan runtimePlan = StandardCocoFeatures.resolveRuntimePlan(manifest,
                CocoFeatureSelection.of(Set.of(CocoFeature.WEB), Set.of(CocoFeature.WEB)));

        assertFalse(runtimePlan.isEnabled(CocoFeature.WEB));
    }

    @Test
    void legacyManifestRemainsACompatibleRuntimeAvailabilityBoundary() {
        CocoFeatureManifest manifest = CocoFeatureManifestLoader.read(new java.io.ByteArrayInputStream("""
                {
                  "schemaVersion" : "1.0",
                  "generatedBy" : "legacy-test",
                  "features" : [ {
                    "id" : "web",
                    "artifactId" : "coco-feature-web",
                    "autoConfigurationClassName" : "io.github.coco.feature.web.CocoWebAutoConfiguration",
                    "defaultEnabled" : true,
                    "enabled" : true,
                    "dependencies" : [ ]
                  } ]
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        CocoFeaturePlan runtimePlan = StandardCocoFeatures.resolveRuntimePlan(manifest,
                CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.WEB)));

        assertFalse(runtimePlan.isEnabled(CocoFeature.WEB));
        assertEquals(Set.of(), runtimePlan.enabledFeatures());
    }

    @Test
    void rejectsUnsupportedFeatureManifestSchemaVersion() {
        CocoFeatureManifest manifest = new CocoFeatureManifest("2.0", "test", List.of());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> StandardCocoFeatures.fromManifest(manifest));

        assertEquals("Unsupported Coco feature manifest schema version '2.0'. Supported schema versions: [1.0, 1.1].",
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

    @Test
    void rejectsIncompleteCurrentFeatureManifest() {
        CocoFeatureManifest manifest = new CocoFeatureManifest(CocoFeatureManifest.CURRENT_SCHEMA_VERSION, "test",
                List.of(new CocoFeatureManifestEntry("web", "coco-web",
                        "io.github.coco.feature.web.CocoWebAutoConfiguration", true, true, List.of(),
                        List.of("coco-web"))));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> StandardCocoFeatures.validateManifest(manifest));

        assertTrue(exception.getMessage().contains("missing standard features"));
        assertTrue(exception.getMessage().contains("mybatis-plus"));
    }

    @Test
    void rejectsManifestMetadataOutsideCanonicalAndLegacyDefinitions() {
        CocoFeatureManifest valid = StandardCocoFeatures.toManifest(
                StandardCocoFeatures.resolve(CocoFeatureSelection.empty()), "test");
        CocoFeatureManifestEntry web = valid.features().stream()
                .filter(entry -> "web".equals(entry.id()))
                .findFirst()
                .orElseThrow();
        CocoFeatureManifestEntry unsafeWeb = new CocoFeatureManifestEntry(web.id(), web.artifactId(),
                web.autoConfigurationClassName(), web.defaultEnabled(), web.enabled(), web.dependencies(),
                List.of("coco-web", "business-library"));
        List<CocoFeatureManifestEntry> entries = valid.features().stream()
                .map(entry -> "web".equals(entry.id()) ? unsafeWeb : entry)
                .toList();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> StandardCocoFeatures.validateManifest(new CocoFeatureManifest(
                        CocoFeatureManifest.CURRENT_SCHEMA_VERSION, "test", entries)));

        assertTrue(exception.getMessage().contains("web"));
        assertTrue(exception.getMessage().contains("pruneArtifactIds"));
        assertTrue(exception.getMessage().contains("business-library"));
    }

    @Test
    void rejectsEnabledManifestFeatureWithDisabledDependency() {
        CocoFeatureManifest valid = StandardCocoFeatures.toManifest(
                StandardCocoFeatures.resolve(CocoFeatureSelection.empty()), "test");
        List<CocoFeatureManifestEntry> entries = valid.features().stream()
                .map(entry -> "web".equals(entry.id())
                        ? new CocoFeatureManifestEntry(entry.id(), entry.artifactId(),
                                entry.autoConfigurationClassName(), entry.defaultEnabled(), false,
                                entry.dependencies(), entry.pruneArtifactIds())
                        : entry)
                .toList();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> StandardCocoFeatures.validateManifest(new CocoFeatureManifest(
                        CocoFeatureManifest.CURRENT_SCHEMA_VERSION, "test", entries)));

        assertTrue(exception.getMessage().contains("openapi"));
        assertTrue(exception.getMessage().contains("web"));
    }

    @Test
    void rejectsUnknownJsonFieldsInSupportedManifestSchema() {
        java.io.UncheckedIOException exception = assertThrows(java.io.UncheckedIOException.class,
                () -> CocoFeatureManifestLoader.read(new java.io.ByteArrayInputStream("""
                        {
                          "schemaVersion": "1.1",
                          "generatedBy": "test",
                          "features": [],
                          "unexpected": true
                        }
                        """.getBytes(java.nio.charset.StandardCharsets.UTF_8))));

        assertTrue(exception.getMessage().contains("Failed to parse Coco feature manifest"));
    }
}
