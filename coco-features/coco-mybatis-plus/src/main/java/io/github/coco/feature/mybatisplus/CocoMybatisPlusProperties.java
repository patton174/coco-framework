package io.github.coco.feature.mybatisplus;

import io.github.coco.feature.mybatisplus.pagination.CocoMybatisPlusPaginationProperties;
import io.github.coco.feature.mybatisplus.sqlguard.CocoMybatisPlusSqlGuardProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco MyBatis-Plus 功能配置属性。
 * <p>
 * 绑定 {@code coco.mybatis-plus} 命名空间，集中维护 MyBatis-Plus 集成、分页拦截器和后续 SQL 扩展能力的配置。
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

    @NestedConfigurationProperty
    private final CocoMybatisPlusPaginationProperties pagination = new CocoMybatisPlusPaginationProperties();

    @NestedConfigurationProperty
    private final CocoMybatisPlusSqlGuardProperties sqlGuard = new CocoMybatisPlusSqlGuardProperties();

    /**
     * <p>
     * 返回分页拦截器配置属性。
     * </p>
     * @return 分页拦截器配置属性
     */
    public CocoMybatisPlusPaginationProperties getPagination() {
        return new PaginationPropertiesView();
    }

    /**
     * <p>
     * 设置分页拦截器配置属性。
     * </p>
     * @param pagination 分页拦截器配置属性
     */
    public void setPagination(CocoMybatisPlusPaginationProperties pagination) {
        CocoMybatisPlusPaginationProperties source = pagination == null
                ? new CocoMybatisPlusPaginationProperties()
                : pagination;
        this.pagination.setEnabled(source.isEnabled());
        this.pagination.setDbType(source.getDbType());
        this.pagination.setOverflow(source.isOverflow());
        this.pagination.setMaxLimit(source.getMaxLimit());
        this.pagination.setOptimizeJoin(source.isOptimizeJoin());
    }

    /**
     * <p>
     * 返回 SQL 防护配置属性。
     * </p>
     * @return SQL 防护配置属性
     */
    public CocoMybatisPlusSqlGuardProperties getSqlGuard() {
        return new SqlGuardPropertiesView();
    }

    /**
     * <p>
     * 设置 SQL 防护配置属性。
     * </p>
     * @param sqlGuard SQL 防护配置属性
     */
    public void setSqlGuard(CocoMybatisPlusSqlGuardProperties sqlGuard) {
        CocoMybatisPlusSqlGuardProperties source = sqlGuard == null
                ? new CocoMybatisPlusSqlGuardProperties()
                : sqlGuard;
        this.sqlGuard.setBlockAttackEnabled(source.isBlockAttackEnabled());
        this.sqlGuard.setIllegalSqlEnabled(source.isIllegalSqlEnabled());
    }

    private final class PaginationPropertiesView extends CocoMybatisPlusPaginationProperties {

        @Override
        public boolean isEnabled() {
            return CocoMybatisPlusProperties.this.pagination.isEnabled();
        }

        @Override
        public void setEnabled(boolean enabled) {
            CocoMybatisPlusProperties.this.pagination.setEnabled(enabled);
        }

        @Override
        public String getDbType() {
            return CocoMybatisPlusProperties.this.pagination.getDbType();
        }

        @Override
        public void setDbType(String dbType) {
            CocoMybatisPlusProperties.this.pagination.setDbType(dbType);
        }

        @Override
        public boolean isOverflow() {
            return CocoMybatisPlusProperties.this.pagination.isOverflow();
        }

        @Override
        public void setOverflow(boolean overflow) {
            CocoMybatisPlusProperties.this.pagination.setOverflow(overflow);
        }

        @Override
        public Long getMaxLimit() {
            return CocoMybatisPlusProperties.this.pagination.getMaxLimit();
        }

        @Override
        public void setMaxLimit(Long maxLimit) {
            CocoMybatisPlusProperties.this.pagination.setMaxLimit(maxLimit);
        }

        @Override
        public boolean isOptimizeJoin() {
            return CocoMybatisPlusProperties.this.pagination.isOptimizeJoin();
        }

        @Override
        public void setOptimizeJoin(boolean optimizeJoin) {
            CocoMybatisPlusProperties.this.pagination.setOptimizeJoin(optimizeJoin);
        }
    }

    private final class SqlGuardPropertiesView extends CocoMybatisPlusSqlGuardProperties {

        @Override
        public boolean isBlockAttackEnabled() {
            return CocoMybatisPlusProperties.this.sqlGuard.isBlockAttackEnabled();
        }

        @Override
        public void setBlockAttackEnabled(boolean blockAttackEnabled) {
            CocoMybatisPlusProperties.this.sqlGuard.setBlockAttackEnabled(blockAttackEnabled);
        }

        @Override
        public boolean isIllegalSqlEnabled() {
            return CocoMybatisPlusProperties.this.sqlGuard.isIllegalSqlEnabled();
        }

        @Override
        public void setIllegalSqlEnabled(boolean illegalSqlEnabled) {
            CocoMybatisPlusProperties.this.sqlGuard.setIllegalSqlEnabled(illegalSqlEnabled);
        }
    }
}
