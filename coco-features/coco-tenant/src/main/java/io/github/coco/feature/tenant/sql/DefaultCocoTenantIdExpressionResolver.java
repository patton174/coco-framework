package io.github.coco.feature.tenant.sql;

import java.util.Objects;

import io.github.coco.feature.tenant.context.CocoTenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;

/**
 * 默认 Coco 租户 ID SQL 表达式解析器。
 * <p>
 * 默认把 {@link CocoTenantContext#tenantId()} 作为字符串字面量写入 MyBatis-Plus 租户条件。
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
public final class DefaultCocoTenantIdExpressionResolver implements CocoTenantIdExpressionResolver {

    /**
     * {@inheritDoc}
     */
    @Override
    public Expression resolve(CocoTenantContext tenantContext) {
        CocoTenantContext checkedContext = Objects.requireNonNull(tenantContext,
                "tenantContext must not be null");
        return new StringValue(checkedContext.tenantId());
    }
}
