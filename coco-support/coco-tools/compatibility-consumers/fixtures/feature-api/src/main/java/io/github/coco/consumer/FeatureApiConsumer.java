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
import io.github.coco.feature.web.body.CocoCachedRequestBody;
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
        verifyCachedRequestBodyContract();
        System.out.println(FEATURE_TYPES.stream()
                .map(Class::getName)
                .collect(Collectors.joining(",")));
    }

    private static void verifyCachedRequestBodyContract() {
        byte[] source = { 1, 2, 3 };
        CocoCachedRequestBody cached = new CocoCachedRequestBody(source, null, -1L, true);
        source[0] = 9;
        if (!cached.cached() || cached.length() != 3L || cached.sha256() == null || cached.content()[0] != 1) {
            throw new IllegalStateException("Published CocoCachedRequestBody cached contract changed.");
        }
        byte[] exposed = cached.content();
        exposed[0] = 9;
        if (cached.content()[0] != 1) {
            throw new IllegalStateException("Published CocoCachedRequestBody defensive copy contract changed.");
        }

        CocoCachedRequestBody uncached = new CocoCachedRequestBody(new byte[] { 1 }, "ignored",
                Long.MAX_VALUE, false);
        if (uncached.cached() || uncached.length() != 0L || uncached.sha256() != null) {
            throw new IllegalStateException("Published CocoCachedRequestBody uncached contract changed.");
        }

        CocoCachedRequestBody nullContent = new CocoCachedRequestBody(null, null, Long.MAX_VALUE, true);
        if (nullContent.length() != 0L || nullContent.content().length != 0 || nullContent.sha256() == null) {
            throw new IllegalStateException("Published CocoCachedRequestBody null-content contract changed.");
        }
    }
}
