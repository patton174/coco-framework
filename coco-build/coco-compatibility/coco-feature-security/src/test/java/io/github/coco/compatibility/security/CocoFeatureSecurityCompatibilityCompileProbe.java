package io.github.coco.compatibility.security;

import io.github.coco.feature.security.CocoSecurity;
import io.github.coco.feature.security.CocoSecurityFeature;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;

final class CocoFeatureSecurityCompatibilityCompileProbe {

    private static final Class<?>[] PUBLIC_TYPES = {
            CocoSecurityFeature.class,
            CocoSecurity.class,
            CocoSecurityContext.class,
            CocoWebSecurityContextResolver.class
    };

    private CocoFeatureSecurityCompatibilityCompileProbe() {
    }
}
