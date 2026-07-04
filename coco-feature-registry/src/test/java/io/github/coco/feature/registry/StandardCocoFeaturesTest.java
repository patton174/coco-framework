package io.github.coco.feature.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.coco.api.feature.CocoFeature;
import org.junit.jupiter.api.Test;

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
    void excludesFeaturesThatDependOnExcludedBaseFeature() {
        Set<CocoFeature> enabled = StandardCocoFeatures.resolveEnabled(Set.of(CocoFeature.MYBATIS_PLUS));

        assertFalse(enabled.contains(CocoFeature.MYBATIS_PLUS));
        assertFalse(enabled.contains(CocoFeature.AUDIT));
        assertFalse(enabled.contains(CocoFeature.TENANT));
        assertFalse(enabled.contains(CocoFeature.DATA_PERMISSION));
        assertFalse(enabled.contains(CocoFeature.CODEGEN));
        assertTrue(enabled.contains(CocoFeature.WEB));
        assertTrue(enabled.contains(CocoFeature.SECURITY));
        assertTrue(enabled.contains(CocoFeature.OPENAPI));
    }
}
