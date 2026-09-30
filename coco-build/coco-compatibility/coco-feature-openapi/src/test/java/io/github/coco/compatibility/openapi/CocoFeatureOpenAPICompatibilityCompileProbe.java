package io.github.coco.compatibility.openapi;

import io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration;
import io.github.coco.feature.openapi.CocoOpenApiFeature;
import io.github.coco.feature.openapi.CocoOpenApiProperties;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadata;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;
import io.github.coco.feature.openapi.core.DefaultCocoOpenApiMetadataProvider;
import io.github.coco.feature.openapi.springdoc.CocoSpringDocOpenApiCustomizerFactoryBean;

final class CocoFeatureOpenAPICompatibilityCompileProbe {

    private static final Class<?>[] PUBLIC_TYPES = {
            CocoOpenApiAutoConfiguration.class,
            CocoOpenApiFeature.class,
            CocoOpenApiProperties.class,
            CocoOpenApiMetadata.class,
            CocoOpenApiMetadataProvider.class,
            DefaultCocoOpenApiMetadataProvider.class,
            CocoSpringDocOpenApiCustomizerFactoryBean.class
    };

    private CocoFeatureOpenAPICompatibilityCompileProbe() {
    }
}
