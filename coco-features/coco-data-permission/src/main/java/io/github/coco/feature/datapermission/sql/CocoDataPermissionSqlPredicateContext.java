package io.github.coco.feature.datapermission.sql;

import java.util.Objects;

import io.github.coco.feature.datapermission.context.CocoDataPermissionContext;
import io.github.coco.feature.datapermission.context.CocoDataPermissionRule;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;

/**
 * 数据权限 SQL 谓词生成上下文。
 * <p>
 * 聚合生成权限 SQL 条件所需的表、原始条件、业务资源、当前权限上下文和资源配置。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @param table 当前 SQL 表
 * @param where 当前 SQL 原始条件
 * @param mappedStatementId MyBatis Mapper 语句标识
 * @param resource 业务资源标识
 * @param dataPermissionContext 数据权限上下文
 * @param rule 命中的数据权限规则
 * @param resourceProperties 资源 SQL 配置
 * @author patton174
 * @since 1.0.0
 */
public record CocoDataPermissionSqlPredicateContext(Table table, Expression where, String mappedStatementId,
        String resource, CocoDataPermissionContext dataPermissionContext, CocoDataPermissionRule rule,
        CocoDataPermissionSqlResourceProperties resourceProperties) {

    /**
     * <p>
     * 创建数据权限 SQL 谓词生成上下文。
     * </p>
     */
    public CocoDataPermissionSqlPredicateContext {
        table = Objects.requireNonNull(table, "table must not be null");
        mappedStatementId = mappedStatementId == null ? "" : mappedStatementId;
        resource = requireText(resource, "resource");
        dataPermissionContext = Objects.requireNonNull(dataPermissionContext,
                "dataPermissionContext must not be null");
        rule = Objects.requireNonNull(rule, "rule must not be null");
        resourceProperties = resourceProperties == null
                ? new CocoDataPermissionSqlResourceProperties()
                : resourceProperties;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
