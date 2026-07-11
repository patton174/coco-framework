package io.github.coco.datapermission;

import io.github.coco.datapermission.sql.CocoDataPermissionSqlProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 数据权限功能配置属性。
 * <p>
 * 保存数据权限模块对外开放的框架级配置，业务系统可以通过 {@code coco.data-permission.*}
 * 控制 SQL 接入、缺省策略和资源映射。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.data-permission")
public class CocoDataPermissionProperties {

    @NestedConfigurationProperty
    private CocoDataPermissionSqlProperties sql = new CocoDataPermissionSqlProperties();

    /**
     * <p>
     * 返回数据权限 SQL 接入配置。
     * </p>
     * @return 数据权限 SQL 接入配置
     */
    public CocoDataPermissionSqlProperties getSql() {
        return this.sql;
    }

    /**
     * <p>
     * 设置数据权限 SQL 接入配置。
     * </p>
     * @param sql 数据权限 SQL 接入配置
     */
    public void setSql(CocoDataPermissionSqlProperties sql) {
        this.sql = sql == null ? new CocoDataPermissionSqlProperties() : sql;
    }
}
