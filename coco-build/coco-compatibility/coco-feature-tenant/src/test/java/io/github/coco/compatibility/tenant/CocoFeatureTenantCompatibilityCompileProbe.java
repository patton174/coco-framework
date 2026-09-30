package io.github.coco.compatibility.tenant;

import io.github.coco.feature.tenant.CocoTenantAutoConfiguration;
import io.github.coco.feature.tenant.CocoTenantErrorCode;
import io.github.coco.feature.tenant.CocoTenantFeature;
import io.github.coco.feature.tenant.CocoTenantProperties;
import io.github.coco.feature.tenant.context.CocoTenantContext;
import io.github.coco.feature.tenant.context.CocoTenantContextHolder;
import io.github.coco.feature.tenant.context.CocoTenantContextResolver;
import io.github.coco.feature.tenant.context.HolderCocoTenantContextResolver;
import io.github.coco.feature.tenant.sql.CocoTenantIdExpressionResolver;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreDecision;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreEvent;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreEventPublisher;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreGuard;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreProperties;
import io.github.coco.feature.tenant.sql.CocoTenantLineHandler;
import io.github.coco.feature.tenant.sql.CocoTenantMybatisPlusAutoConfiguration;
import io.github.coco.feature.tenant.sql.CocoTenantSqlProperties;
import io.github.coco.feature.tenant.sql.DefaultCocoTenantIdExpressionResolver;
import io.github.coco.feature.tenant.sql.NoOpCocoTenantInterceptorIgnoreEventPublisher;

final class CocoFeatureTenantCompatibilityCompileProbe {

    private static final Class<?>[] PUBLIC_TYPES = {
            CocoTenantAutoConfiguration.class,
            CocoTenantErrorCode.class,
            CocoTenantFeature.class,
            CocoTenantProperties.class,
            CocoTenantContext.class,
            CocoTenantContextHolder.class,
            CocoTenantContextResolver.class,
            HolderCocoTenantContextResolver.class,
            CocoTenantIdExpressionResolver.class,
            CocoTenantInterceptorIgnoreDecision.class,
            CocoTenantInterceptorIgnoreEvent.class,
            CocoTenantInterceptorIgnoreEventPublisher.class,
            CocoTenantInterceptorIgnoreGuard.class,
            CocoTenantInterceptorIgnoreProperties.class,
            CocoTenantLineHandler.class,
            CocoTenantMybatisPlusAutoConfiguration.class,
            CocoTenantSqlProperties.class,
            DefaultCocoTenantIdExpressionResolver.class,
            NoOpCocoTenantInterceptorIgnoreEventPublisher.class
    };

    private CocoFeatureTenantCompatibilityCompileProbe() {
    }
}
