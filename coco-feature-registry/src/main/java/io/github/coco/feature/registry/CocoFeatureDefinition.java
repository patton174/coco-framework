package io.github.coco.feature.registry;

import java.util.Objects;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;

/**
 * Metadata for one standard Coco feature.
 */
public record CocoFeatureDefinition(
        CocoFeature feature,
        String artifactId,
        boolean defaultEnabled,
        Set<CocoFeature> dependencies) {

    public CocoFeatureDefinition {
        Objects.requireNonNull(feature, "feature must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        dependencies = Set.copyOf(Objects.requireNonNull(dependencies, "dependencies must not be null"));
    }
}
