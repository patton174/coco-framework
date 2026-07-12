package io.github.coco.compatibility.openapi;

import io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration;
import io.github.coco.feature.openapi.CocoOpenApiFeature;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadata;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;

final class CocoFeatureOpenApiCompatibilityCompileProbe {

    private static final Class<?>[] PUBLIC_TYPES = {
            CocoOpenApiFeature.class,
            CocoOpenApiAutoConfiguration.class,
            CocoOpenApiMetadata.class,
            CocoOpenApiMetadataProvider.class
    };

    private CocoFeatureOpenApiCompatibilityCompileProbe() {
    }
}
