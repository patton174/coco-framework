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

/**
 * {@code coco-feature-tenant} 兼容门面编译探针。
 * <p>
 * 以编译期引用的方式钉住旧坐标 {@code coco-feature-tenant} 曾经对外暴露、现由 {@code coco-tenant} 承载的公开类型。
 * 只要业务项目仍通过旧坐标引入依赖，这些类型就必须继续可解析；任何一个类型被移动、改名或降低可见性，
 * 本模块的测试编译都会失败，从而在发布前暴露兼容面的破坏。该类不含测试方法，运行期也不会被加载。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-tenant}</li>
 * </ul>
 * @author patton174
 * @since 3.0.0
 */
final class CocoFeatureTenantCompatibilityCompileProbe {

    /** 旧坐标消费者可能直接引用的公开类型；仅用于编译期校验，不会被读取。 */
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
