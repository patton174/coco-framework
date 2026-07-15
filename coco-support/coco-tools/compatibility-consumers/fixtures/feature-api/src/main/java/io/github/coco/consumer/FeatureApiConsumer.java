package io.github.coco.consumer;

import java.util.List;
import java.util.stream.Collectors;

import io.github.coco.feature.audit.CocoAuditFeature;
import io.github.coco.feature.datapermission.CocoDataPermissionFeature;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusFeature;
import io.github.coco.feature.openapi.CocoOpenApiFeature;
import io.github.coco.feature.security.CocoSecurityFeature;
import io.github.coco.feature.tenant.CocoTenantFeature;
import io.github.coco.feature.web.CocoWebFeature;
import io.github.coco.feature.web.context.CocoIpAddressSupport;

public final class FeatureApiConsumer {

    private static final List<Class<?>> FEATURE_TYPES = List.of(
            CocoWebFeature.class,
            CocoMybatisPlusFeature.class,
            CocoTenantFeature.class,
            CocoDataPermissionFeature.class,
            CocoAuditFeature.class,
            CocoSecurityFeature.class,
            CocoOpenApiFeature.class);

    private FeatureApiConsumer() {
    }

    public static void main(String[] args) {
        if (FEATURE_TYPES.size() != 7) {
            throw new IllegalStateException("Expected seven Coco feature APIs.");
        }
        if (CocoIpAddressSupport.parseIpAddress("invalid-address") != null) {
            throw new IllegalStateException("Published CocoIpAddressSupport null sentinel changed.");
        }
        System.out.println(FEATURE_TYPES.stream()
                .map(Class::getName)
                .collect(Collectors.joining(",")));
    }
}
