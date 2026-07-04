package io.github.coco.api.feature;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Default in-memory feature exclusion registry.
 */
public final class DefaultCocoFeatureRegistry implements CocoFeatureRegistry {

    private final EnumSet<CocoFeature> excludedFeatures = EnumSet.noneOf(CocoFeature.class);

    @Override
    public CocoFeatureRegistry exclude(CocoFeature... features) {
        if (features == null) {
            return this;
        }
        for (CocoFeature feature : features) {
            if (feature != null) {
                this.excludedFeatures.add(feature);
            }
        }
        return this;
    }

    @Override
    public boolean isExcluded(CocoFeature feature) {
        return this.excludedFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }

    @Override
    public Set<CocoFeature> excludedFeatures() {
        if (this.excludedFeatures.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(this.excludedFeatures));
    }
}
