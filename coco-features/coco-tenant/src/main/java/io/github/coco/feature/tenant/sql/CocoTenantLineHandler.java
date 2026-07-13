package io.github.coco.feature.tenant.sql;

import java.util.Locale;
import java.util.Objects;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import io.github.coco.feature.tenant.CocoTenantErrorCode;
import io.github.coco.feature.tenant.context.CocoTenantContext;
import io.github.coco.feature.tenant.context.CocoTenantContextResolver;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.NullValue;

/**
 * Coco MyBatis-Plus 租户行处理器。
 * <p>
 * 负责把 Coco 租户上下文转换为 MyBatis-Plus {@link TenantLineHandler} 所需的字段名、租户值和忽略表规则。
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
public final class CocoTenantLineHandler implements TenantLineHandler {

    private final CocoTenantSqlProperties properties;

    private final CocoTenantContextResolver contextResolver;

    private final CocoTenantIdExpressionResolver expressionResolver;

    /**
     * <p>
     * 创建 Coco MyBatis-Plus 租户行处理器。
     * </p>
     * @param properties 租户 SQL 隔离配置
     * @param contextResolver 租户上下文解析器
     * @param expressionResolver 租户 ID SQL 表达式解析器
     */
    public CocoTenantLineHandler(CocoTenantSqlProperties properties,
            CocoTenantContextResolver contextResolver,
            CocoTenantIdExpressionResolver expressionResolver) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.contextResolver = Objects.requireNonNull(contextResolver, "contextResolver must not be null");
        this.expressionResolver = Objects.requireNonNull(expressionResolver,
                "expressionResolver must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Expression getTenantId() {
        return this.contextResolver.resolve()
                .map(this::resolveTenantId)
                .orElseGet(this::missingContextExpression);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTenantIdColumn() {
        return this.properties.getTenantIdColumn();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean ignoreTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return false;
        }
        String normalizedTableName = normalize(tableName);
        return this.properties.getIgnoreTables().stream()
                .filter(Objects::nonNull)
                .map(CocoTenantLineHandler::normalize)
                .anyMatch(normalizedTableName::equals);
    }

    private Expression resolveTenantId(CocoTenantContext tenantContext) {
        return Objects.requireNonNull(this.expressionResolver.resolve(tenantContext),
                "tenant id expression must not be null");
    }

    private Expression missingContextExpression() {
        if (this.properties.isFailOnMissingContext()) {
            throw CocoTenantErrorCode.CONTEXT_MISSING.request();
        }
        return new NullValue();
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
