package io.github.coco.consumer;

import java.util.List;

import io.github.coco.config.CocoConfigAutoConfiguration;
import io.github.coco.config.CocoProperties;
import io.github.coco.feature.audit.CocoAuditFeature;
import io.github.coco.feature.audit.core.CocoAuditPublisher;
import io.github.coco.feature.datapermission.CocoDataPermissionFeature;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextResolver;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusFeature;
import io.github.coco.feature.mybatisplus.interceptor.CocoMybatisPlusInterceptorCustomizer;
import io.github.coco.feature.openapi.CocoOpenApiFeature;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadata;
import io.github.coco.feature.runtime.autoconfigure.CocoFeatureRuntimeAutoConfiguration;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.security.CocoSecurityFeature;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.tenant.CocoTenantFeature;
import io.github.coco.feature.tenant.context.CocoTenantContextResolver;
import io.github.coco.feature.web.CocoWebFeature;
import io.github.coco.feature.web.response.CocoApiResponse;
import io.github.coco.test.CocoTestSupport;

public final class PublicFqcnConsumer {

    private static final List<Class<?>> PUBLIC_TYPES = List.of(
            CocoConfigAutoConfiguration.class,
            CocoProperties.class,
            CocoFeatureRuntimeAutoConfiguration.class,
            ConditionalOnCocoFeature.class,
            CocoWebFeature.class,
            CocoApiResponse.class,
            CocoMybatisPlusFeature.class,
            CocoMybatisPlusInterceptorCustomizer.class,
            CocoTenantFeature.class,
            CocoTenantContextResolver.class,
            CocoDataPermissionFeature.class,
            CocoDataPermissionContextResolver.class,
            CocoAuditFeature.class,
            CocoAuditPublisher.class,
            CocoSecurityFeature.class,
            CocoSecurityContext.class,
            CocoOpenApiFeature.class,
            CocoOpenApiMetadata.class,
            CocoTestSupport.class);

    private PublicFqcnConsumer() {
    }

    public static List<String> publicTypeNames() {
        return PUBLIC_TYPES.stream().map(Class::getName).toList();
    }
}
