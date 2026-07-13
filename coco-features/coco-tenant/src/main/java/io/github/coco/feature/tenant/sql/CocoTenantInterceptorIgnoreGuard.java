package io.github.coco.feature.tenant.sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import io.github.coco.feature.tenant.CocoTenantErrorCode;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.PatternMatchUtils;

/**
 * Coco 租户拦截器忽略治理 guard。
 * <p>
 * 在 MyBatis-Plus 租户行拦截器执行前检测 {@code @InterceptorIgnore(tenantLine = true)}
 * 或线程级忽略策略，避免租户 SQL 隔离被静默绕过。
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
public final class CocoTenantInterceptorIgnoreGuard implements InnerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(CocoTenantInterceptorIgnoreGuard.class);

    private final CocoTenantSqlProperties properties;

    private final CocoTenantInterceptorIgnoreEventPublisher eventPublisher;

    /**
     * <p>
     * 创建租户拦截器忽略治理 guard。
     * </p>
     * @param properties 租户 SQL 隔离配置
     * @param eventPublisher 治理事件发布器
     */
    public CocoTenantInterceptorIgnoreGuard(CocoTenantSqlProperties properties,
            CocoTenantInterceptorIgnoreEventPublisher eventPublisher) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
            ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        govern(ms);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        MappedStatement mappedStatement = PluginUtils.mpStatementHandler(sh).mappedStatement();
        SqlCommandType commandType = mappedStatement.getSqlCommandType();
        if (commandType == SqlCommandType.INSERT || commandType == SqlCommandType.UPDATE
                || commandType == SqlCommandType.DELETE) {
            govern(mappedStatement);
        }
    }

    private void govern(MappedStatement mappedStatement) {
        String mappedStatementId = mappedStatement.getId();
        if (!InterceptorIgnoreHelper.willIgnoreTenantLine(mappedStatementId)) {
            return;
        }
        boolean shouldBlock = !isAllowed(mappedStatementId)
                && this.properties.getInterceptorIgnore().isBlockUnlisted();
        CocoTenantInterceptorIgnoreDecision decision = shouldBlock
                ? CocoTenantInterceptorIgnoreDecision.BLOCKED
                : CocoTenantInterceptorIgnoreDecision.ALLOWED;
        publish(mappedStatement, decision);
        if (!shouldBlock) {
            warn(mappedStatement, decision);
            return;
        }
        warn(mappedStatement, decision);
        throw CocoTenantErrorCode.INTERCEPTOR_IGNORE_BLOCKED.forbidden(mappedStatementId);
    }

    private boolean isAllowed(String mappedStatementId) {
        return this.properties.getInterceptorIgnore().getAllowedMappedStatements().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .anyMatch(pattern -> PatternMatchUtils.simpleMatch(pattern, mappedStatementId));
    }

    private void publish(MappedStatement mappedStatement, CocoTenantInterceptorIgnoreDecision decision) {
        this.eventPublisher.publish(new CocoTenantInterceptorIgnoreEvent(mappedStatement.getId(),
                mappedStatement.getSqlCommandType(), decision));
    }

    private static void warn(MappedStatement mappedStatement, CocoTenantInterceptorIgnoreDecision decision) {
        LOGGER.warn("Coco tenant SQL isolation bypass {} for mapped statement [{}] command [{}].",
                decision.name().toLowerCase(java.util.Locale.ROOT),
                mappedStatement.getId(),
                mappedStatement.getSqlCommandType());
    }
}
