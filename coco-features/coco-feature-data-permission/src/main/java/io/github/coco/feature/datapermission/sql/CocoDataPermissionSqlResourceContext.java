package io.github.coco.feature.datapermission.sql;

import java.util.Objects;

import net.sf.jsqlparser.schema.Table;

/**
 * 数据权限 SQL 资源解析上下文。
 * <p>
 * 封装 MyBatis-Plus 数据权限拦截器当前正在处理的数据表和 Mapper 语句标识。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @param table 当前 SQL 表
 * @param mappedStatementId MyBatis Mapper 语句标识
 * @author patton174
 * @since 1.0.0
 */
public record CocoDataPermissionSqlResourceContext(Table table, String mappedStatementId) {

    /**
     * <p>
     * 创建数据权限 SQL 资源解析上下文。
     * </p>
     */
    public CocoDataPermissionSqlResourceContext {
        table = Objects.requireNonNull(table, "table must not be null");
        mappedStatementId = mappedStatementId == null ? "" : mappedStatementId;
    }
}
