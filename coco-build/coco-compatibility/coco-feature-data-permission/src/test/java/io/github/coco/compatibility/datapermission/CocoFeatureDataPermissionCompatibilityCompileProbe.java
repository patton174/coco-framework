package io.github.coco.compatibility.datapermission;

import io.github.coco.feature.datapermission.CocoDataPermissionAutoConfiguration;
import io.github.coco.feature.datapermission.CocoDataPermissionErrorCode;
import io.github.coco.feature.datapermission.CocoDataPermissionFeature;
import io.github.coco.feature.datapermission.CocoDataPermissionProperties;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContext;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextHolder;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextResolver;
import io.github.coco.feature.datapermission.context.CocoDataPermissionRule;
import io.github.coco.feature.datapermission.context.CocoDataScope;
import io.github.coco.feature.datapermission.context.HolderCocoDataPermissionContextResolver;
import io.github.coco.feature.datapermission.mybatisplus.CocoDataPermissionMybatisPlusAutoConfiguration;
import io.github.coco.feature.datapermission.mybatisplus.CocoMybatisPlusDataPermissionHandler;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionMissingContextPolicy;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionMissingRulePolicy;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlColumnType;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlPredicateContext;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlPredicateProvider;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlProperties;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlResourceContext;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlResourceProperties;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlResourceResolver;
import io.github.coco.feature.datapermission.sql.DefaultCocoDataPermissionSqlPredicateProvider;
import io.github.coco.feature.datapermission.sql.PropertyCocoDataPermissionSqlResourceResolver;

final class CocoFeatureDataPermissionCompatibilityCompileProbe {

    private static final Class<?>[] PUBLIC_TYPES = {
            CocoDataPermissionAutoConfiguration.class,
            CocoDataPermissionErrorCode.class,
            CocoDataPermissionFeature.class,
            CocoDataPermissionProperties.class,
            CocoDataPermissionContext.class,
            CocoDataPermissionContextHolder.class,
            CocoDataPermissionContextResolver.class,
            CocoDataPermissionRule.class,
            CocoDataScope.class,
            HolderCocoDataPermissionContextResolver.class,
            CocoDataPermissionMybatisPlusAutoConfiguration.class,
            CocoMybatisPlusDataPermissionHandler.class,
            CocoDataPermissionMissingContextPolicy.class,
            CocoDataPermissionMissingRulePolicy.class,
            CocoDataPermissionSqlColumnType.class,
            CocoDataPermissionSqlPredicateContext.class,
            CocoDataPermissionSqlPredicateProvider.class,
            CocoDataPermissionSqlProperties.class,
            CocoDataPermissionSqlResourceContext.class,
            CocoDataPermissionSqlResourceProperties.class,
            CocoDataPermissionSqlResourceResolver.class,
            DefaultCocoDataPermissionSqlPredicateProvider.class,
            PropertyCocoDataPermissionSqlResourceResolver.class
    };

    private CocoFeatureDataPermissionCompatibilityCompileProbe() {
    }
}
