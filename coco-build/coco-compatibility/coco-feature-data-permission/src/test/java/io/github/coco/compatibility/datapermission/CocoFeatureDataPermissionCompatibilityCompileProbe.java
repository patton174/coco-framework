package io.github.coco.compatibility.datapermission;

import io.github.coco.feature.datapermission.CocoDataPermissionAutoConfiguration;
import io.github.coco.feature.datapermission.CocoDataPermissionFeature;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextResolver;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlPredicateProvider;

final class CocoFeatureDataPermissionCompatibilityCompileProbe {

    private static final Class<?>[] PUBLIC_TYPES = {
            CocoDataPermissionFeature.class,
            CocoDataPermissionAutoConfiguration.class,
            CocoDataPermissionContextResolver.class,
            CocoDataPermissionSqlPredicateProvider.class
    };

    private CocoFeatureDataPermissionCompatibilityCompileProbe() {
    }
}
