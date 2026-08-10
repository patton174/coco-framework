package io.github.coco.feature.mybatisplus;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.coco.feature.mybatisplus.pagination.CocoMybatisPlusPaginationProperties;
import io.github.coco.feature.mybatisplus.sqlguard.CocoMybatisPlusSqlGuardProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco MyBatis-Plus 功能配置属性。
 * <p>
 * 绑定 {@code coco.mybatis-plus} 命名空间，集中维护 MyBatis-Plus 集成、乐观锁、分页拦截器和后续 SQL 扩展能力的配置。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.mybatis-plus")
public class CocoMybatisPlusProperties {

    private boolean optimisticLockerEnabled;

    @NestedConfigurationProperty
    private volatile CocoMybatisPlusPaginationProperties pagination = new CocoMybatisPlusPaginationProperties();

    @NestedConfigurationProperty
    private volatile CocoMybatisPlusSqlGuardProperties sqlGuard = new CocoMybatisPlusSqlGuardProperties();

    /**
     * <p>
     * 返回是否启用 MyBatis-Plus 乐观锁内置拦截器。
     * </p>
     * <p>
     * 启用后，带有 {@code @Version} 字段的实体更新会追加版本条件并递增版本值。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isOptimisticLockerEnabled() {
        return this.optimisticLockerEnabled;
    }

    /**
     * <p>
     * 设置是否启用 MyBatis-Plus 乐观锁内置拦截器。
     * </p>
     * @param optimisticLockerEnabled 是否启用乐观锁内置拦截器
     */
    public void setOptimisticLockerEnabled(boolean optimisticLockerEnabled) {
        this.optimisticLockerEnabled = optimisticLockerEnabled;
    }

    /**
     * <p>
     * 返回分页拦截器的可变 JavaBean 配置。
     * </p>
     * <p>
     * 为保持既有 Spring Binder 和业务侧 {@code getPagination().setXxx(...)} 调用的语义，该访问器有意返回
     * 当前 live bean。框架内部读取请使用 {@link #paginationSnapshot()}，以避免将该引用保存为配置快照。
     * </p>
     * @return 分页拦截器配置属性
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The public JavaBean accessor must retain live "
            + "nested-property mutation compatibility; framework consumers use paginationSnapshot().")
    public CocoMybatisPlusPaginationProperties getPagination() {
        return this.pagination;
    }

    /**
     * <p>
     * 设置分页拦截器配置属性。
     * </p>
     * @param pagination 分页拦截器配置属性
     */
    public void setPagination(CocoMybatisPlusPaginationProperties pagination) {
        this.pagination = copy(pagination);
    }

    /**
     * <p>
     * 返回 SQL 防护的可变 JavaBean 配置。
     * </p>
     * <p>
     * 为保持既有 Spring Binder 和业务侧 {@code getSqlGuard().setXxx(...)} 调用的语义，该访问器有意返回
     * 当前 live bean。框架内部读取请使用 {@link #sqlGuardSnapshot()}，以避免将该引用保存为配置快照。
     * </p>
     * @return SQL 防护配置属性
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The public JavaBean accessor must retain live "
            + "nested-property mutation compatibility; framework consumers use sqlGuardSnapshot().")
    public CocoMybatisPlusSqlGuardProperties getSqlGuard() {
        return this.sqlGuard;
    }

    /**
     * <p>
     * 设置 SQL 防护配置属性。
     * </p>
     * @param sqlGuard SQL 防护配置属性
     */
    public void setSqlGuard(CocoMybatisPlusSqlGuardProperties sqlGuard) {
        this.sqlGuard = copy(sqlGuard);
    }

    /**
     * <p>
     * 返回供框架内部读取的分页配置快照。
     * </p>
     * @return 分页配置快照
     */
    public CocoMybatisPlusPaginationProperties paginationSnapshot() {
        return copy(this.pagination);
    }

    /**
     * <p>
     * 返回供框架内部读取的 SQL 防护配置快照。
     * </p>
     * @return SQL 防护配置快照
     */
    public CocoMybatisPlusSqlGuardProperties sqlGuardSnapshot() {
        return copy(this.sqlGuard);
    }

    private static CocoMybatisPlusPaginationProperties copy(CocoMybatisPlusPaginationProperties source) {
        CocoMybatisPlusPaginationProperties copy = new CocoMybatisPlusPaginationProperties();
        if (source != null) {
            copy.setEnabled(source.isEnabled());
            copy.setDbType(source.getDbType());
            copy.setOverflow(source.isOverflow());
            copy.setMaxLimit(source.getMaxLimit());
            copy.setOptimizeJoin(source.isOptimizeJoin());
        }
        return copy;
    }

    private static CocoMybatisPlusSqlGuardProperties copy(CocoMybatisPlusSqlGuardProperties source) {
        CocoMybatisPlusSqlGuardProperties copy = new CocoMybatisPlusSqlGuardProperties();
        if (source != null) {
            copy.setBlockAttackEnabled(source.isBlockAttackEnabled());
            copy.setIllegalSqlEnabled(source.isIllegalSqlEnabled());
        }
        return copy;
    }
}
