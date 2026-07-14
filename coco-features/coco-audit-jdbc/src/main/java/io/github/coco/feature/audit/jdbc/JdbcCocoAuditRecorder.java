package io.github.coco.feature.audit.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcOperations;

/**
 * 基于 JDBC 的 Coco 审计记录器。
 * <p>
 * 使用业务项目提供的 {@link JdbcOperations} 和预建审计表。每次写入均使用预编译参数，不接受任意 SQL 标识符；
 * 记录器不创建表、连接、事务管理器或后台线程。调用线程已有事务时，所有批次参与该事务；没有事务时，各批次遵循
 * 数据源的默认提交策略。数据库失败会原样向上传递，由 Coco 审计发布器的失败策略决定后续行为。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
public final class JdbcCocoAuditRecorder implements CocoAuditRecorder, AutoCloseable {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final JdbcOperations jdbcOperations;

    private final String insertSql;

    private final int batchSize;

    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * 创建 JDBC 审计记录器。
     * @param jdbcOperations 业务项目提供的 JDBC 操作入口
     * @param properties JDBC 审计记录器配置
     */
    public JdbcCocoAuditRecorder(JdbcOperations jdbcOperations, CocoAuditJdbcProperties properties) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "jdbcOperations must not be null");
        CocoAuditJdbcProperties checkedProperties = Objects.requireNonNull(properties, "properties must not be null");
        this.batchSize = requirePositive(checkedProperties.getBatchSize(), "batchSize");
        this.insertSql = buildInsertSql(checkedProperties.getSchema(), checkedProperties.getTableName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void record(CocoAuditEvent event) {
        recordBatch(List.of(Objects.requireNonNull(event, "event must not be null")));
    }

    /**
     * 按配置的批次大小写入审计事件。
     * <p>
     * 该方法不开始或提交事务。调用方需要批次原子性时，应在业务已选择的 Spring 事务边界内调用；无事务情况下，
     * 已成功提交的先前批次不会因后续批次失败而自动回滚。
     * </p>
     * @param events 待写入的审计事件
     */
    public void recordBatch(Collection<CocoAuditEvent> events) {
        ensureOpen();
        Objects.requireNonNull(events, "events must not be null");
        if (events.isEmpty()) {
            return;
        }
        List<AuditRow> rows = new ArrayList<>(events.size());
        for (CocoAuditEvent event : events) {
            rows.add(AuditRow.from(Objects.requireNonNull(event, "audit event must not be null")));
        }
        for (int start = 0; start < rows.size(); start += this.batchSize) {
            writeBatch(rows.subList(start, Math.min(start + this.batchSize, rows.size())));
        }
    }

    /**
     * 关闭记录器，阻止后续写入。
     * <p>
     * 记录器不拥有连接或后台资源，因此关闭不会影响业务数据源。
     * </p>
     */
    @Override
    public void close() {
        this.closed.set(true);
    }

    private void writeBatch(List<AuditRow> rows) {
        int[] counts = this.jdbcOperations.batchUpdate(this.insertSql, new AuditBatchPreparedStatementSetter(rows));
        if (counts.length != rows.size()) {
            throw new IllegalStateException("Coco audit JDBC batch returned an unexpected update count");
        }
        for (int count : counts) {
            if (count != 1 && count != Statement.SUCCESS_NO_INFO) {
                throw new IllegalStateException("Coco audit JDBC batch did not write exactly one event");
            }
        }
    }

    private void ensureOpen() {
        if (this.closed.get()) {
            throw new IllegalStateException("Coco audit JDBC recorder is closed");
        }
    }

    private static String buildInsertSql(String schema, String tableName) {
        String checkedTableName = requireIdentifier(tableName, "tableName");
        String tableReference = schema == null || schema.isBlank()
                ? checkedTableName
                : requireIdentifier(schema, "schema") + "." + checkedTableName;
        return "INSERT INTO " + tableReference + " (event_type, action, resource_type, resource_id, trace_id, actor, "
                + "tenant_id, success, occurred_at_epoch_millis, attributes_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    private static String requireIdentifier(String identifier, String propertyName) {
        if (identifier == null || !IDENTIFIER_PATTERN.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Coco audit JDBC " + propertyName
                    + " must be an unquoted SQL identifier");
        }
        return identifier;
    }

    private static int requirePositive(int value, String propertyName) {
        if (value <= 0) {
            throw new IllegalArgumentException("Coco audit JDBC " + propertyName + " must be greater than zero");
        }
        return value;
    }

    private static String serializeAttributes(Map<String, Object> attributes) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attributes);
        }
        catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Coco audit attributes cannot be serialized as JSON", ex);
        }
    }

    private record AuditRow(String type, String action, String resourceType, String resourceId, String traceId,
            String actor, String tenantId, boolean success, long occurredAtEpochMillis, String attributesJson) {

        private static AuditRow from(CocoAuditEvent event) {
            return new AuditRow(event.type(), event.action().orElse(null), event.resourceType().orElse(null),
                    event.resourceId().orElse(null), event.traceId().orElse(null), event.actor().orElse(null),
                    event.tenantId().orElse(null), event.success(), event.occurredAt().toEpochMilli(),
                    serializeAttributes(event.attributes()));
        }
    }

    private static final class AuditBatchPreparedStatementSetter implements BatchPreparedStatementSetter {

        private final List<AuditRow> rows;

        private AuditBatchPreparedStatementSetter(List<AuditRow> rows) {
            this.rows = rows;
        }

        @Override
        public void setValues(PreparedStatement statement, int index) throws SQLException {
            AuditRow row = this.rows.get(index);
            setNullableString(statement, 1, row.type());
            setNullableString(statement, 2, row.action());
            setNullableString(statement, 3, row.resourceType());
            setNullableString(statement, 4, row.resourceId());
            setNullableString(statement, 5, row.traceId());
            setNullableString(statement, 6, row.actor());
            setNullableString(statement, 7, row.tenantId());
            statement.setBoolean(8, row.success());
            statement.setLong(9, row.occurredAtEpochMillis());
            setNullableString(statement, 10, row.attributesJson());
        }

        @Override
        public int getBatchSize() {
            return this.rows.size();
        }

        private static void setNullableString(PreparedStatement statement, int index, String value) throws SQLException {
            if (value == null) {
                statement.setNull(index, Types.LONGVARCHAR);
            }
            else {
                statement.setString(index, value);
            }
        }
    }
}
