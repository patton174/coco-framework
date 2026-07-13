package io.github.coco.feature.tenant;

import io.github.coco.feature.tenant.sql.CocoTenantSqlProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 租户功能配置属性。
 * <p>
 * 绑定 {@code coco.tenant} 命名空间，集中维护租户上下文、SQL 隔离和后续租户扩展能力的配置。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.tenant")
public class CocoTenantProperties {

    @NestedConfigurationProperty
    private CocoTenantSqlProperties sql = new CocoTenantSqlProperties();

    /**
     * <p>
     * 返回租户 SQL 隔离配置。
     * </p>
     * @return 租户 SQL 隔离配置
     */
    public CocoTenantSqlProperties getSql() {
        return this.sql;
    }

    /**
     * <p>
     * 设置租户 SQL 隔离配置。
     * </p>
     * @param sql 租户 SQL 隔离配置
     */
    public void setSql(CocoTenantSqlProperties sql) {
        this.sql = sql == null ? new CocoTenantSqlProperties() : sql;
    }
}
