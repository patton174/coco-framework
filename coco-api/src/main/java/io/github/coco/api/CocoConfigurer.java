package io.github.coco.api;

import io.github.coco.api.feature.CocoFeatureRegistry;

/**
 * Type-safe configuration entry point for business projects.
 */
public interface CocoConfigurer {

    default void configureFeatures(CocoFeatureRegistry features) {
    }
}
