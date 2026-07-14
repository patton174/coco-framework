package io.github.coco.feature.audit.jdbc;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco JDBC 审计记录器配置属性。
 * <p>
 * 该配置只控制显式引入 {@code coco-audit-jdbc} 后的 JDBC 记录器，不创建数据源、表或事务管理器。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.audit.jdbc")
public class CocoAuditJdbcProperties {

    /** 默认审计表名。 */
    public static final String DEFAULT_TABLE_NAME = "coco_audit_event";

    private boolean enabled;

    private String schema;

    private String tableName = DEFAULT_TABLE_NAME;

    private int batchSize = 100;

    /**
     * 返回是否启用 JDBC 审计记录器。
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * 设置是否启用 JDBC 审计记录器。
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回可选的数据库 schema 标识符。
     * @return schema 标识符；未设置时为空
     */
    public String getSchema() {
        return this.schema;
    }

    /**
     * 设置可选的数据库 schema 标识符。
     * @param schema schema 标识符
     */
    public void setSchema(String schema) {
        this.schema = schema;
    }

    /**
     * 返回审计表标识符。
     * @return 审计表标识符
     */
    public String getTableName() {
        return this.tableName;
    }

    /**
     * 设置审计表标识符。
     * @param tableName 审计表标识符
     */
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    /**
     * 返回单次 JDBC 批处理的最大事件数。
     * @return 批处理最大事件数
     */
    public int getBatchSize() {
        return this.batchSize;
    }

    /**
     * 设置单次 JDBC 批处理的最大事件数。
     * @param batchSize 批处理最大事件数
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
