package io.github.coco.api.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class DefaultCocoFeatureRegistryTest {

    @Test
    void startsWithNoExcludedFeatures() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        assertTrue(registry.excludedFeatures().isEmpty());
        assertFalse(registry.isExcluded(CocoFeature.TENANT));
    }

    @Test
    void recordsExcludedFeatures() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        registry.exclude(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION);

        assertTrue(registry.isExcluded(CocoFeature.TENANT));
        assertTrue(registry.isExcluded(CocoFeature.DATA_PERMISSION));
        assertEquals(Set.of(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION), registry.excludedFeatures());
    }

    @Test
    void ignoresDuplicateExclusions() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        registry.exclude(CocoFeature.TENANT, CocoFeature.TENANT);

        assertEquals(Set.of(CocoFeature.TENANT), registry.excludedFeatures());
    }
}
