package io.github.coco.feature.model;

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
 *   <li>模块：{@code coco-feature-model}</li>
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
        assertEquals("coco-mybatis-plus", definitions.get(CocoFeature.MYBATIS_PLUS).artifactId());
        assertTrue(definitions.get(CocoFeature.MYBATIS_PLUS).pruneArtifactIds().containsAll(
                Set.of("coco-mybatis-plus", "coco-feature-mybatis-plus")));
        assertEquals(Set.of(), definitions.get(CocoFeature.AUDIT).dependencies());
        assertEquals(Set.of(CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
                definitions.get(CocoFeature.TENANT).dependencies());
        assertEquals(Set.of(CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
                definitions.get(CocoFeature.DATA_PERMISSION).dependencies());
        assertEquals(Set.of(CocoFeature.WEB, CocoFeature.SECURITY),
                definitions.get(CocoFeature.OPENAPI).dependencies());
        assertEquals("rate-limit", CocoFeature.RATE_LIMIT.id());
        assertEquals("coco-rate-limit", definitions.get(CocoFeature.RATE_LIMIT).artifactId());
        assertEquals("io.github.coco.feature.ratelimit.CocoRateLimitAutoConfiguration",
                definitions.get(CocoFeature.RATE_LIMIT).autoConfigurationClassName());
        assertTrue(definitions.get(CocoFeature.RATE_LIMIT).defaultEnabled());
        assertEquals(Set.of(CocoFeature.WEB), definitions.get(CocoFeature.RATE_LIMIT).dependencies());
        assertEquals("idempotency", CocoFeature.IDEMPOTENCY.id());
        assertEquals("coco-idempotency", definitions.get(CocoFeature.IDEMPOTENCY).artifactId());
        assertEquals(Set.of(CocoFeature.WEB), definitions.get(CocoFeature.IDEMPOTENCY).dependencies());
        assertEquals("storage", CocoFeature.STORAGE.id());
        assertEquals("coco-storage", definitions.get(CocoFeature.STORAGE).artifactId());
        assertEquals("io.github.coco.storage.CocoStorageAutoConfiguration",
                definitions.get(CocoFeature.STORAGE).autoConfigurationClassName());
        assertTrue(definitions.get(CocoFeature.STORAGE).defaultEnabled());
        assertEquals(Set.of(), definitions.get(CocoFeature.STORAGE).dependencies());
        assertEquals("messaging", CocoFeature.MESSAGING.id());
        assertEquals("coco-messaging", definitions.get(CocoFeature.MESSAGING).artifactId());
        assertEquals("io.github.coco.messaging.CocoMessagingAutoConfiguration",
                definitions.get(CocoFeature.MESSAGING).autoConfigurationClassName());
        assertTrue(definitions.get(CocoFeature.MESSAGING).defaultEnabled());
        assertEquals(Set.of(), definitions.get(CocoFeature.MESSAGING).dependencies());
        assertEquals("cache", CocoFeature.CACHE.id());
        assertEquals("coco-cache", definitions.get(CocoFeature.CACHE).artifactId());
        assertEquals("io.github.coco.cache.CocoCacheAutoConfiguration",
                definitions.get(CocoFeature.CACHE).autoConfigurationClassName());
        assertTrue(definitions.get(CocoFeature.CACHE).defaultEnabled());
        assertEquals(Set.of(), definitions.get(CocoFeature.CACHE).dependencies());
        assertEquals("notification", CocoFeature.NOTIFICATION.id());
        assertEquals("coco-notification", definitions.get(CocoFeature.NOTIFICATION).artifactId());
        assertEquals("io.github.coco.notification.CocoNotificationAutoConfiguration",
                definitions.get(CocoFeature.NOTIFICATION).autoConfigurationClassName());
        assertTrue(definitions.get(CocoFeature.NOTIFICATION).defaultEnabled());
        assertEquals(Set.of(), definitions.get(CocoFeature.NOTIFICATION).dependencies());
        assertEquals("captcha", CocoFeature.CAPTCHA.id());
        assertEquals("coco-captcha", definitions.get(CocoFeature.CAPTCHA).artifactId());
        assertEquals("io.github.coco.captcha.CocoCaptchaAutoConfiguration",
                definitions.get(CocoFeature.CAPTCHA).autoConfigurationClassName());
        assertTrue(definitions.get(CocoFeature.CAPTCHA).defaultEnabled());
        assertEquals(Set.of(), definitions.get(CocoFeature.CAPTCHA).dependencies());
        assertEquals(Set.of(CocoFeature.MYBATIS_PLUS),
                definitions.get(CocoFeature.CODEGEN).dependencies());
        assertEquals(Set.of("coco-feature-codegen", "freemarker"),
                definitions.get(CocoFeature.CODEGEN).pruneArtifactIds());
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
        assertTrue(enabled.contains(CocoFeature.RATE_LIMIT));
        assertTrue(enabled.contains(CocoFeature.IDEMPOTENCY));
    }

    @Test
    void keepsAuditEnabledWhenWebIsDisabled() {
        Set<CocoFeature> enabled = StandardCocoFeatures.resolveEnabledFeatures(Set.of(CocoFeature.WEB));

        assertFalse(enabled.contains(CocoFeature.WEB));
        assertTrue(enabled.contains(CocoFeature.AUDIT));
        assertFalse(enabled.contains(CocoFeature.OPENAPI));
        assertFalse(enabled.contains(CocoFeature.RATE_LIMIT));
        assertFalse(enabled.contains(CocoFeature.IDEMPOTENCY));
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
    void explicitDisableWithSatisfiedDependenciesIsNotDependencyDisabled() {
        CocoFeaturePlan plan = StandardCocoFeatures.resolve(
                CocoFeatureSelection.ofDisabled(Set.of(CocoFeature.OPENAPI)));

        assertEquals(Set.of(), plan.disabledByDependencyFeatures());
    }

    @Test
    void resolvesSelectionWithHigherPriorityOverrides() {
        CocoFeatureSelection applicationSelection = CocoFeatureSelection.of(
                Set.of(),
                Set.of(CocoFeature.TENANT));
        CocoFeatureSelection codeSelection = CocoFeatureSelection.of(
                Set.of(CocoFeature.TENANT),
                Set.of());

        CocoFeaturePlan plan = StandardCocoFeatures.resolve(applicationSelection.merge(codeSelection));

        assertTrue(plan.enabledFeatures().contains(CocoFeature.TENANT));
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
        assertTrue(loadedPlan.enabledFeatures().contains(CocoFeature.RATE_LIMIT));
        assertTrue(loadedPlan.enabledFeatures().contains(CocoFeature.IDEMPOTENCY));
        CocoFeatureDefinition loadedMybatisPlusDefinition = loadedPlan.definitions().stream()
                .filter(definition -> definition.feature() == CocoFeature.MYBATIS_PLUS)
                .findFirst()
                .orElseThrow();
        assertTrue(loadedMybatisPlusDefinition.pruneArtifactIds().containsAll(
                Set.of("coco-mybatis-plus", "coco-feature-mybatis-plus")));
        assertEquals(List.of(
                "coco-feature-mybatis-plus",
                "coco-mybatis-plus",
                "mybatis",
                "mybatis-plus",
                "mybatis-plus-annotation",
                "mybatis-plus-core",
                "mybatis-plus-extension",
                "mybatis-plus-jsqlparser-4.9",
                "mybatis-plus-jsqlparser-common",
                "mybatis-plus-spring",
                "mybatis-plus-spring-boot-autoconfigure",
                "mybatis-plus-spring-boot-native-image",
                "mybatis-plus-spring-boot4-starter",
                "mybatis-spring"), manifest.features().stream()
                .filter(entry -> "mybatis-plus".equals(entry.id()))
                .findFirst()
                .orElseThrow()
                .pruneArtifactIds());
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
}
