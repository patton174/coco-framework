package io.github.coco.compatibility.security;

import io.github.coco.feature.security.CocoSecurity;
import io.github.coco.feature.security.CocoSecurityAutoConfiguration;
import io.github.coco.feature.security.CocoSecurityErrorCode;
import io.github.coco.feature.security.CocoSecurityFeature;
import io.github.coco.feature.security.CocoSecurityProperties;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityContextResolver;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.security.context.HolderCocoSecurityContextResolver;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoSecurityWebHeaderProperties;
import io.github.coco.feature.security.web.CocoSecurityWebProperties;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import io.github.coco.feature.security.web.HeaderCocoWebSecurityContextResolver;

final class CocoFeatureSecurityCompatibilityCompileProbe {

    private static final Class<?>[] PUBLIC_TYPES = {
            CocoSecurity.class,
            CocoSecurityAutoConfiguration.class,
            CocoSecurityErrorCode.class,
            CocoSecurityFeature.class,
            CocoSecurityProperties.class,
            CocoSecurityContext.class,
            CocoSecurityContextHolder.class,
            CocoSecurityContextResolver.class,
            CocoSecurityPrincipal.class,
            HolderCocoSecurityContextResolver.class,
            CocoSecurityWebFilter.class,
            CocoSecurityWebHeaderProperties.class,
            CocoSecurityWebProperties.class,
            CocoWebSecurityContextResolver.class,
            HeaderCocoWebSecurityContextResolver.class
    };

    private CocoFeatureSecurityCompatibilityCompileProbe() {
    }
}
