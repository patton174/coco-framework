package io.github.coco.feature.mybatisplus.interceptor;

import java.util.Objects;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.IllegalSQLInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusProperties;
import io.github.coco.feature.mybatisplus.pagination.CocoMybatisPlusDbTypeResolver;
import io.github.coco.feature.mybatisplus.pagination.CocoMybatisPlusPaginationProperties;
import io.github.coco.feature.mybatisplus.sqlguard.CocoMybatisPlusSqlGuardProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Coco MyBatis-Plus 拦截器工厂。
 * <p>
 * 负责按统一顺序组装 MyBatis-Plus 内置拦截器。其他框架模块先通过
 * {@link CocoMybatisPlusInterceptorCustomizer} 注册自己的拦截器，默认分页拦截器最后追加，避免分页插件提前改写 SQL。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoMybatisPlusInterceptorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoMybatisPlusInterceptorFactory.class);

    private final CocoMybatisPlusProperties properties;

    private final ObjectProvider<InnerInterceptor> innerInterceptors;

    private final ObjectProvider<CocoMybatisPlusInterceptorCustomizer> customizers;

    /**
     * <p>
     * 创建 MyBatis-Plus 拦截器工厂。
     * </p>
     * @param properties MyBatis-Plus 功能配置属性
     * @param innerInterceptors 业务或其他框架注册的 MyBatis-Plus 内置拦截器集合
     * @param customizers 拦截器定制器集合
     */
    public CocoMybatisPlusInterceptorFactory(CocoMybatisPlusProperties properties,
            ObjectProvider<InnerInterceptor> innerInterceptors,
            ObjectProvider<CocoMybatisPlusInterceptorCustomizer> customizers) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.innerInterceptors = Objects.requireNonNull(innerInterceptors, "innerInterceptors must not be null");
        this.customizers = Objects.requireNonNull(customizers, "customizers must not be null");
    }

    /**
     * <p>
     * 创建 Coco 托管的 MyBatis-Plus 拦截器。
     * </p>
     * @return MyBatis-Plus 拦截器
     */
    public MybatisPlusInterceptor create() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        this.innerInterceptors.orderedStream().forEach(interceptor::addInnerInterceptor);
        this.customizers.orderedStream().forEach(customizer -> customizer.customize(interceptor));
        CocoMybatisPlusSqlGuardProperties sqlGuard = this.properties.getSqlGuard();
        logSqlGuardProductionRecommendation(sqlGuard);
        addSqlGuardInnerInterceptors(interceptor, sqlGuard);
        CocoMybatisPlusPaginationProperties pagination = this.properties.getPagination();
        if (pagination.isEnabled()) {
            interceptor.addInnerInterceptor(createPaginationInnerInterceptor(pagination));
        }
        return interceptor;
    }

    private static void addSqlGuardInnerInterceptors(MybatisPlusInterceptor interceptor,
            CocoMybatisPlusSqlGuardProperties properties) {
        if (properties.isBlockAttackEnabled()) {
            interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        }
        if (properties.isIllegalSqlEnabled()) {
            interceptor.addInnerInterceptor(new IllegalSQLInnerInterceptor());
        }
    }

    private static void logSqlGuardProductionRecommendation(CocoMybatisPlusSqlGuardProperties properties) {
        if (!properties.isBlockAttackEnabled() && !properties.isIllegalSqlEnabled()) {
            LOGGER.info("Coco MyBatis-Plus SQL guard is disabled. For production, evaluate enabling "
                    + "coco.mybatis-plus.sql-guard.block-attack-enabled and "
                    + "coco.mybatis-plus.sql-guard.illegal-sql-enabled after validating application SQL.");
        }
    }

    private static PaginationInnerInterceptor createPaginationInnerInterceptor(
            CocoMybatisPlusPaginationProperties properties) {
        DbType dbType = CocoMybatisPlusDbTypeResolver.resolve(properties.getDbType());
        PaginationInnerInterceptor interceptor = dbType == null
                ? new PaginationInnerInterceptor()
                : new PaginationInnerInterceptor(dbType);
        interceptor.setOverflow(properties.isOverflow());
        interceptor.setMaxLimit(properties.getMaxLimit());
        interceptor.setOptimizeJoin(properties.isOptimizeJoin());
        return interceptor;
    }
}
