package io.github.coco.feature.audit.jdbc;

/**
 * JDBC 审计表的已验证标识符。
 * <p>
 * 仅供 {@link CocoAuditSchemaInitializer} 按业务数据库方言创建审计表使用，不能用于绕过 JDBC 记录器的标识符校验。
 * </p>
 *
 * @param schema 可选 schema 标识符
 * @param tableName 审计表标识符
 * @param tableReference 已验证的表引用
 * @author patton174
 * @since 1.0.0
 */
public record CocoAuditJdbcSchema(String schema, String tableName, String tableReference) {
}
