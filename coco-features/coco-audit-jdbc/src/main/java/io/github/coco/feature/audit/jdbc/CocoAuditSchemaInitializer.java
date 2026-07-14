package io.github.coco.feature.audit.jdbc;

import org.springframework.jdbc.core.JdbcOperations;

/**
 * 业务数据库方言的审计表初始化 SPI。
 * <p>
 * 只有显式开启 {@code coco.audit.jdbc.initialize-schema} 时才会调用。业务项目必须为使用的数据库方言、迁移治理和
 * 索引策略提供实现；Coco 不提供通用的建表 SQL。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoAuditSchemaInitializer {

    /**
     * 初始化审计表。
     * @param jdbcOperations 业务项目提供的 JDBC 操作入口
     * @param schema 已验证的审计表标识符
     */
    void initialize(JdbcOperations jdbcOperations, CocoAuditJdbcSchema schema);
}
