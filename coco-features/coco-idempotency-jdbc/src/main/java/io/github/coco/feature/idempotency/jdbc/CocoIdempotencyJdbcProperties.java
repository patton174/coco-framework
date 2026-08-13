package io.github.coco.feature.idempotency.jdbc;

import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JDBC 幂等共享存储配置。
 * <p>该适配器不会建表，也不会管理业务数据源、事务管理器或业务事务。</p>
 *
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(CocoIdempotencyJdbcProperties.PROPERTY_PREFIX)
public class CocoIdempotencyJdbcProperties {

    /** JDBC 适配器配置前缀。 */
    public static final String PROPERTY_PREFIX = "coco.idempotency.jdbc";

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private boolean enabled;

    private String schema;

    private String tableName = "coco_idempotency";

    private String keyPrefix = "coco:";

    private int maxResponseBytes = 1_048_576;

    private int maxHeaderBytes = 65_536;

    /** @return 是否启用 JDBC 幂等共享存储 */
    public boolean isEnabled() { return this.enabled; }

    /** @param enabled 是否启用 JDBC 幂等共享存储 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    /** @return 可选的单段 schema 名称 */
    public String getSchema() { return this.schema; }

    /** @param schema 可选的单段 schema 名称 */
    public void setSchema(String schema) { this.schema = schema; }

    /** @return 单段、未引用的表名称 */
    public String getTableName() { return this.tableName; }

    /** @param tableName 单段、未引用的表名称 */
    public void setTableName(String tableName) { this.tableName = tableName; }

    /** @return 数据库存储键前缀 */
    public String getKeyPrefix() { return this.keyPrefix; }

    /** @param keyPrefix 数据库存储键前缀 */
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

    /** @return 最大响应体字节数 */
    public int getMaxResponseBytes() { return this.maxResponseBytes; }

    /** @param maxResponseBytes 最大响应体字节数 */
    public void setMaxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; }

    /** @return 最大响应头 JSON 字节数 */
    public int getMaxHeaderBytes() { return this.maxHeaderBytes; }

    /** @param maxHeaderBytes 最大响应头 JSON 字节数 */
    public void setMaxHeaderBytes(int maxHeaderBytes) { this.maxHeaderBytes = maxHeaderBytes; }

    /** 校验配置，避免将不可信文本拼接为 SQL 标识符。 */
    public void validate() {
        requireIdentifier(this.tableName, "table-name");
        if (this.schema != null) requireIdentifier(this.schema, "schema");
        String prefix = Objects.requireNonNull(this.keyPrefix,
                "coco.idempotency.jdbc.key-prefix must not be null");
        if (prefix.isEmpty() || prefix.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new IllegalArgumentException("coco.idempotency.jdbc.key-prefix must be non-empty and contain no whitespace or control characters");
        }
        requireRange(this.maxResponseBytes, 0, 16_777_216, "max-response-bytes");
        requireRange(this.maxHeaderBytes, 1, 1_048_576, "max-header-bytes");
    }

    /** @return 经校验的表限定名 */
    String qualifiedTableName() {
        return this.schema == null ? this.tableName : this.schema + "." + this.tableName;
    }

    private static void requireIdentifier(String value, String name) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("coco.idempotency.jdbc." + name
                    + " must be an unquoted single-part SQL identifier");
        }
    }

    private static void requireRange(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("coco.idempotency.jdbc." + name + " must be between "
                    + minimum + " and " + maximum);
        }
    }
}
