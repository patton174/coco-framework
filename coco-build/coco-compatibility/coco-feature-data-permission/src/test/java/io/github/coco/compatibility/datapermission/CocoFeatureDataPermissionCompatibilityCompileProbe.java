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

/**
 * {@code coco-feature-data-permission} 兼容门面编译探针。
 * <p>
 * 以编译期引用的方式钉住旧坐标 {@code coco-feature-data-permission} 曾经对外暴露、现由 {@code coco-data-permission} 承载的公开类型。
 * 只要业务项目仍通过旧坐标引入依赖，这些类型就必须继续可解析；任何一个类型被移动、改名或降低可见性，
 * 本模块的测试编译都会失败，从而在发布前暴露兼容面的破坏。该类不含测试方法，运行期也不会被加载。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 3.0.0
 */
final class CocoFeatureDataPermissionCompatibilityCompileProbe {

    /** 旧坐标消费者可能直接引用的公开类型；仅用于编译期校验，不会被读取。 */
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
