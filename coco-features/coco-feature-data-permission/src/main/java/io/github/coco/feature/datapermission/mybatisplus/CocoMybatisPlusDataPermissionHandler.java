package io.github.coco.feature.datapermission.mybatisplus;

import java.util.Objects;
import java.util.Optional;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import io.github.coco.feature.datapermission.CocoDataPermissionErrorCode;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContext;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextResolver;
import io.github.coco.feature.datapermission.context.CocoDataPermissionRule;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionMissingContextPolicy;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionMissingRulePolicy;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlPredicateContext;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlPredicateProvider;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlProperties;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlResourceContext;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlResourceProperties;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlResourceResolver;
import io.github.coco.feature.datapermission.sql.DefaultCocoDataPermissionSqlPredicateProvider;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;

/**
 * Coco MyBatis-Plus 数据权限处理器。
 * <p>
 * 作为 MyBatis-Plus {@link MultiDataPermissionHandler} 适配层，负责串联资源解析、上下文解析和 SQL 谓词生成。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoMybatisPlusDataPermissionHandler implements MultiDataPermissionHandler {

    private final CocoDataPermissionSqlProperties properties;

    private final CocoDataPermissionContextResolver contextResolver;

    private final CocoDataPermissionSqlResourceResolver resourceResolver;

    private final CocoDataPermissionSqlPredicateProvider predicateProvider;

    /**
     * <p>
     * 创建 Coco MyBatis-Plus 数据权限处理器。
     * </p>
     * @param properties 数据权限 SQL 配置
     * @param contextResolver 数据权限上下文解析器
     * @param resourceResolver SQL 资源解析器
     * @param predicateProvider SQL 谓词提供器
     */
    public CocoMybatisPlusDataPermissionHandler(CocoDataPermissionSqlProperties properties,
            CocoDataPermissionContextResolver contextResolver,
            CocoDataPermissionSqlResourceResolver resourceResolver,
            CocoDataPermissionSqlPredicateProvider predicateProvider) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.contextResolver = Objects.requireNonNull(contextResolver, "contextResolver must not be null");
        this.resourceResolver = Objects.requireNonNull(resourceResolver, "resourceResolver must not be null");
        this.predicateProvider = Objects.requireNonNull(predicateProvider, "predicateProvider must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        Objects.requireNonNull(table, "table must not be null");
        Optional<String> resource = this.resourceResolver.resolve(
                new CocoDataPermissionSqlResourceContext(table, mappedStatementId));
        if (resource.isEmpty()) {
            return null;
        }
        Optional<CocoDataPermissionContext> dataPermissionContext = this.contextResolver.resolve();
        if (dataPermissionContext.isEmpty()) {
            return handleMissingContext();
        }
        Optional<CocoDataPermissionRule> rule = dataPermissionContext.get().rule(resource.get());
        if (rule.isEmpty()) {
            return handleMissingRule();
        }
        CocoDataPermissionSqlResourceProperties resourceProperties = this.properties.resource(resource.get());
        return this.predicateProvider.predicate(new CocoDataPermissionSqlPredicateContext(table, where,
                mappedStatementId, resource.get(), dataPermissionContext.get(), rule.get(), resourceProperties))
                .orElse(null);
    }

    private Expression handleMissingContext() {
        CocoDataPermissionMissingContextPolicy policy = this.properties.getMissingContextPolicy();
        if (policy == CocoDataPermissionMissingContextPolicy.IGNORE) {
            return null;
        }
        if (policy == CocoDataPermissionMissingContextPolicy.DENY) {
            return DefaultCocoDataPermissionSqlPredicateProvider.denyExpression();
        }
        throw CocoDataPermissionErrorCode.CONTEXT_MISSING.forbidden();
    }

    private Expression handleMissingRule() {
        CocoDataPermissionMissingRulePolicy policy = this.properties.getMissingRulePolicy();
        if (policy == CocoDataPermissionMissingRulePolicy.IGNORE) {
            return null;
        }
        return DefaultCocoDataPermissionSqlPredicateProvider.denyExpression();
    }
}
