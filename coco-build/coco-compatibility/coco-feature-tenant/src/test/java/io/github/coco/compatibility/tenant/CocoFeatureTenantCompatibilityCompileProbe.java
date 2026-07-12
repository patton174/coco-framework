package io.github.coco.compatibility.tenant;

import io.github.coco.feature.tenant.CocoTenantAutoConfiguration;
import io.github.coco.feature.tenant.CocoTenantFeature;
import io.github.coco.feature.tenant.context.CocoTenantContextResolver;

final class CocoFeatureTenantCompatibilityCompileProbe {

    private static final Class<?>[] PUBLIC_TYPES = {
            CocoTenantFeature.class,
            CocoTenantAutoConfiguration.class,
            CocoTenantContextResolver.class
    };

    private CocoFeatureTenantCompatibilityCompileProbe() {
    }
}
