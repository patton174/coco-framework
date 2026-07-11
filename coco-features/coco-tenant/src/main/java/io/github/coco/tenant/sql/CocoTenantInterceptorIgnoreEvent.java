package io.github.coco.tenant.sql;

import org.apache.ibatis.mapping.SqlCommandType;

/**
 * Coco 租户拦截器忽略治理事件。
 * <p>
 * 当 MyBatis-Plus Mapper 语句通过 {@code @InterceptorIgnore(tenantLine = true)} 或线程级忽略策略绕过租户隔离时，
 * guard 会发布该事件，业务系统可桥接到审计、告警或指标系统。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-tenant}</li>
 * </ul>
 * @param mappedStatementId MyBatis MappedStatement ID
 * @param commandType SQL 命令类型
 * @param decision 治理决策
 * @author patton174
 * @since 1.0.0
 */
public record CocoTenantInterceptorIgnoreEvent(String mappedStatementId, SqlCommandType commandType,
        CocoTenantInterceptorIgnoreDecision decision) {
}
