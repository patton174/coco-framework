package io.github.coco.compatibility.mybatisplus;

import io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusErrorCode;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusFeature;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusProperties;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSource;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceAspect;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceContextHolder;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceCreator;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceDefinition;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceDefinitionProperties;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceKeyResolver;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceLoadBalanceStrategy;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceLoadBalancer;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceMissingPolicy;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourcePointcut;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceProvider;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceRegistry;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceRouter;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceScope;
import io.github.coco.feature.mybatisplus.datasource.CocoDataSourceTransactionPolicy;
import io.github.coco.feature.mybatisplus.datasource.CocoMybatisPlusDataSourceAutoConfiguration;
import io.github.coco.feature.mybatisplus.datasource.CocoMybatisPlusDataSourceProperties;
import io.github.coco.feature.mybatisplus.datasource.CocoRoutingDataSource;
import io.github.coco.feature.mybatisplus.datasource.ContextCocoDataSourceRouter;
import io.github.coco.feature.mybatisplus.datasource.DefaultCocoDataSourceCreator;
import io.github.coco.feature.mybatisplus.datasource.DefaultCocoDataSourceKeyResolver;
import io.github.coco.feature.mybatisplus.datasource.PropertiesCocoDataSourceProvider;
import io.github.coco.feature.mybatisplus.datasource.RandomCocoDataSourceLoadBalancer;
import io.github.coco.feature.mybatisplus.datasource.RoundRobinCocoDataSourceLoadBalancer;
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
            CocoMybatisPlusSqlGuardProperties.class,
            CocoDataSource.class,
            CocoDataSourceAspect.class,
            CocoDataSourceContextHolder.class,
            CocoDataSourceCreator.class,
            CocoDataSourceDefinition.class,
            CocoDataSourceDefinitionProperties.class,
            CocoDataSourceKeyResolver.class,
            CocoDataSourceLoadBalanceStrategy.class,
            CocoDataSourceLoadBalancer.class,
            CocoDataSourceMissingPolicy.class,
            CocoDataSourcePointcut.class,
            CocoDataSourceProvider.class,
            CocoDataSourceRegistry.class,
            CocoDataSourceRouter.class,
            CocoDataSourceScope.class,
            CocoDataSourceTransactionPolicy.class,
            CocoMybatisPlusDataSourceAutoConfiguration.class,
            CocoMybatisPlusDataSourceProperties.class,
            CocoRoutingDataSource.class,
            ContextCocoDataSourceRouter.class,
            DefaultCocoDataSourceCreator.class,
            DefaultCocoDataSourceKeyResolver.class,
            PropertiesCocoDataSourceProvider.class,
            RandomCocoDataSourceLoadBalancer.class,
            RoundRobinCocoDataSourceLoadBalancer.class
    };

    private CocoFeatureMybatisPlusCompatibilityCompileProbe() {
    }
}
