package io.github.coco.compatibility.mybatisplus;

import io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusErrorCode;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusFeature;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusProperties;
import io.github.coco.feature.mybatisplus.interceptor.CocoMybatisPlusInterceptorCustomizer;
import io.github.coco.feature.mybatisplus.interceptor.CocoMybatisPlusInterceptorFactory;
import io.github.coco.feature.mybatisplus.pagination.CocoMybatisPlusDbTypeResolver;
import io.github.coco.feature.mybatisplus.pagination.CocoMybatisPlusPaginationProperties;
import io.github.coco.feature.mybatisplus.sqlguard.CocoMybatisPlusSqlGuardProperties;

final class CocoFeatureMybatisPlusCompatibilityCompileProbe {

    private static final Class<?>[] PUBLIC_TYPES = {
            CocoMybatisPlusFeature.class,
            CocoMybatisPlusAutoConfiguration.class,
            CocoMybatisPlusErrorCode.class,
            CocoMybatisPlusProperties.class,
            CocoMybatisPlusInterceptorCustomizer.class,
            CocoMybatisPlusInterceptorFactory.class,
            CocoMybatisPlusDbTypeResolver.class,
            CocoMybatisPlusPaginationProperties.class,
            CocoMybatisPlusSqlGuardProperties.class
    };

    private CocoFeatureMybatisPlusCompatibilityCompileProbe() {
    }
}
