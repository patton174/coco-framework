package io.github.coco.feature.runtime;

import io.github.coco.feature.runtime.autoconfigure.CocoFeatureRuntimeAutoConfiguration;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;

final class CocoFeatureRuntimeFacadeFqcnCompileContract {

    private CocoFeatureRuntimeFacadeFqcnCompileContract() {
    }

    static Class<?> autoConfigurationType() {
        return CocoFeatureRuntimeAutoConfiguration.class;
    }

    static Class<?> conditionAnnotationType() {
        return ConditionalOnCocoFeature.class;
    }
}
