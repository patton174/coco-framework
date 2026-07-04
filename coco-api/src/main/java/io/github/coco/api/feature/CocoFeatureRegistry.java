package io.github.coco.api.feature;

import java.util.Set;

/**
 * Mutable feature selection used by {@code CocoConfigurer}.
 */
public interface CocoFeatureRegistry {

    CocoFeatureRegistry exclude(CocoFeature... features);

    boolean isExcluded(CocoFeature feature);

    Set<CocoFeature> excludedFeatures();
}
