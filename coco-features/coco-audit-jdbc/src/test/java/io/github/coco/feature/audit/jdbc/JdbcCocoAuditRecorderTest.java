package io.github.coco.feature.audit.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.github.coco.feature.audit.core.CocoAuditEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC 审计记录器 H2 集成测试。
 */
class JdbcCocoAuditRecorderTest {

    private JdbcTemplate jdbcTemplate;

    private DriverManagerDataSource dataSource;

    @BeforeEach
    void setUp() {
        this.dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:audit_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        this.jdbcTemplate = new JdbcTemplate(this.dataSource);
        this.jdbcTemplate.execute("""
                CREATE TABLE coco_audit_event (
                    event_type CLOB NOT NULL,
                    action CLOB NULL,
                    resource_type CLOB NULL,
                    resource_id CLOB NULL,
                    trace_id CLOB NULL,
                    actor CLOB NULL,
                    tenant_id CLOB NULL,
                    success BOOLEAN NOT NULL,
                    occurred_at_epoch_millis BIGINT NOT NULL,
                    attributes_json CLOB NOT NULL
                )
                """);
    }

    @Test
    void writesStructuredEventFieldsAndSortedAttributesJson() {
        JdbcCocoAuditRecorder recorder = newRecorder(2);
        Instant occurredAt = Instant.parse("2026-07-15T08:30:00Z");

        recorder.record(CocoAuditEvent.builder("order.changed")
                .action("update")
                .resourceType("order")
                .resourceId("1001")
                .traceId("trace-1001")
                .actor("operator-7")
                .tenantId("tenant-a")
                .success(false)
                .occurredAt(occurredAt)
                .attribute("zeta", 2)
                .attribute("alpha", List.of("cn", "hz"))
                .build());

        assertThat(this.jdbcTemplate.queryForMap("SELECT * FROM coco_audit_event"))
                .containsEntry("EVENT_TYPE", "order.changed")
                .containsEntry("ACTION", "update")
                .containsEntry("RESOURCE_TYPE", "order")
                .containsEntry("RESOURCE_ID", "1001")
                .containsEntry("TRACE_ID", "trace-1001")
                .containsEntry("ACTOR", "operator-7")
                .containsEntry("TENANT_ID", "tenant-a")
                .containsEntry("SUCCESS", false)
                .containsEntry("OCCURRED_AT_EPOCH_MILLIS", occurredAt.toEpochMilli())
                .containsEntry("ATTRIBUTES_JSON", "{\"alpha\":[\"cn\",\"hz\"],\"zeta\":2}");
    }

    @Test
    void preservesLongFieldsAndWritesEmptyOptionalValuesAsNull() {
        JdbcCocoAuditRecorder recorder = newRecorder(100);
        String longValue = "x".repeat(20_000);

        recorder.record(CocoAuditEvent.builder(longValue)
                .action("   ")
                .resourceId(" ")
                .build());

        assertThat(this.jdbcTemplate.queryForObject(
                "SELECT LENGTH(event_type) FROM coco_audit_event", Integer.class)).isEqualTo(longValue.length());
        assertThat(this.jdbcTemplate.queryForObject(
                "SELECT action FROM coco_audit_event", String.class)).isNull();
        assertThat(this.jdbcTemplate.queryForObject(
                "SELECT resource_id FROM coco_audit_event", String.class)).isNull();
        assertThat(this.jdbcTemplate.queryForObject(
                "SELECT attributes_json FROM coco_audit_event", String.class)).isEqualTo("{}");
    }

    @Test
    void writesConfiguredBatchesAndSupportsConcurrentCallers() throws Exception {
        JdbcCocoAuditRecorder recorder = newRecorder(2);
        recorder.recordBatch(List.of(event("batch-1"), event("batch-2"), event("batch-3")));

        int contenders = 16;
        ExecutorService executor = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int index = 0; index < contenders; index++) {
                int eventIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    recorder.record(event("concurrent-" + eventIndex));
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
        finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(rowCount()).isEqualTo(contenders + 3);
    }

    @Test
    void propagatesDatabaseFailuresWithoutEventContentInRecorderMessages() {
        JdbcCocoAuditRecorder recorder = newRecorder(1);
        this.jdbcTemplate.execute("DROP TABLE coco_audit_event");

        assertThatThrownBy(() -> recorder.record(CocoAuditEvent.builder("secret-body-value").build()))
                .isInstanceOf(DataAccessException.class)
                .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain("secret-body-value"));
    }

    @Test
    void participatesInTheCallingTransactionWithoutOpeningOne() {
        JdbcCocoAuditRecorder recorder = newRecorder(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(
                new DataSourceTransactionManager(this.dataSource));

        transactionTemplate.executeWithoutResult(status -> {
            recorder.record(event("rolled-back"));
            status.setRollbackOnly();
        });

        assertThat(rowCount()).isZero();
    }

    @Test
    void rejectsUnsafeIdentifiersAndInvalidBatchSizes() {
        assertThatThrownBy(() -> new JdbcCocoAuditRecorder(this.jdbcTemplate, properties("audit;DROP", null, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JdbcCocoAuditRecorder(this.jdbcTemplate, properties("audit", "bad.schema", 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JdbcCocoAuditRecorder(this.jdbcTemplate, properties("audit", null, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsWritesAfterCloseWithoutClosingTheBusinessDataSource() {
        JdbcCocoAuditRecorder recorder = newRecorder(1);
        recorder.close();

        assertThatThrownBy(() -> recorder.record(event("after-close")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
        assertThat(this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coco_audit_event", Integer.class))
                .isZero();
    }

    private JdbcCocoAuditRecorder newRecorder(int batchSize) {
        return new JdbcCocoAuditRecorder(this.jdbcTemplate, properties("coco_audit_event", null, batchSize));
    }

    private static CocoAuditJdbcProperties properties(String tableName, String schema, int batchSize) {
        CocoAuditJdbcProperties properties = new CocoAuditJdbcProperties();
        properties.setTableName(tableName);
        properties.setSchema(schema);
        properties.setBatchSize(batchSize);
        return properties;
    }

    private int rowCount() {
        Integer count = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coco_audit_event", Integer.class);
        return count == null ? 0 : count;
    }

    private static CocoAuditEvent event(String type) {
        return CocoAuditEvent.builder(type).occurredAt(Instant.parse("2026-07-15T08:30:00Z")).build();
    }
}
