package io.github.coco.tenant.sql;

import io.github.coco.tenant.context.CocoTenantContext;
import net.sf.jsqlparser.expression.Expression;

/**
 * Coco 租户 ID SQL 表达式解析器。
 * <p>
 * 业务项目可以替换该接口，将字符串、数字、加密租户 ID 或复合租户规则转换为 JSqlParser 表达式。
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
@FunctionalInterface
public interface CocoTenantIdExpressionResolver {

    /**
     * <p>
     * 将当前租户上下文转换为 SQL 租户 ID 表达式。
     * </p>
     * @param tenantContext 租户上下文
     * @return SQL 租户 ID 表达式
     */
    Expression resolve(CocoTenantContext tenantContext);
}
