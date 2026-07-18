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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditFailurePolicy;
import io.github.coco.feature.audit.core.CocoAuditPublisher;
import io.github.coco.feature.audit.core.CompositeCocoAuditPublisher;
import io.github.coco.feature.audit.core.PolicyCocoAuditErrorHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
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
    void logsSafeWarningAndHonorsIgnoreAndThrowFailurePolicies() {
        JdbcCocoAuditRecorder recorder = newRecorder(1);
        this.jdbcTemplate.execute("DROP TABLE coco_audit_event");
        String secret = "secret-json-body-" + "x".repeat(20_000);
        CocoAuditEvent event = CocoAuditEvent.builder("database-down")
                .attribute("requestBody", secret)
                .build();
        Logger logger = (Logger) LoggerFactory.getLogger(JdbcCocoAuditRecorder.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            CocoAuditPublisher ignoredPublisher = new CompositeCocoAuditPublisher(List.of(recorder),
                    new PolicyCocoAuditErrorHandler(CocoAuditFailurePolicy.IGNORE));
            ignoredPublisher.publish(event);

            CocoAuditPublisher throwingPublisher = new CompositeCocoAuditPublisher(List.of(recorder),
                    new PolicyCocoAuditErrorHandler(CocoAuditFailurePolicy.THROW));
            assertThatThrownBy(() -> throwingPublisher.publish(event))
                    .isInstanceOf(DataAccessException.class)
                    .satisfies(failure -> assertThat(failure.getMessage()).doesNotContain(secret));

            assertThat(appender.list).hasSize(2).allSatisfy(loggingEvent -> {
                assertThat(loggingEvent.getFormattedMessage()).contains("Coco JDBC audit write failed")
                        .doesNotContain(secret);
                assertThat(loggingEvent.getThrowableProxy()).isNull();
            });
        }
        finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void rollsBackEveryBatchWhenTheCallingTransactionFailsMidBatch() {
        this.jdbcTemplate.execute("""
                ALTER TABLE coco_audit_event ADD CONSTRAINT occurred_at_non_negative
                    CHECK (occurred_at_epoch_millis >= 0)
                """);
        JdbcCocoAuditRecorder recorder = newRecorder(2);
        TransactionTemplate transactionTemplate = new TransactionTemplate(
                new DataSourceTransactionManager(this.dataSource));

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> recorder.recordBatch(List.of(
                event("first-batch"), eventAt("failing-batch", Instant.ofEpochMilli(-1)), event("last-batch")))))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("occurred_at_epoch_millis");
        assertThat(rowCount()).isZero();
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
        List<String> unsafeTableNames = List.of("audit;DROP", "audit.table", "audit table", "audit--comment",
                "\"audit\"", "`audit`", "audit\nnext", "审计表", "1audit");
        List<String> unsafeSchemaNames = List.of("bad.schema", "audit;DROP", "audit schema", "\"audit\"",
                "audit--comment", "audit\nnext", "审计", "1audit");

        for (String tableName : unsafeTableNames) {
            assertThatThrownBy(() -> new JdbcCocoAuditRecorder(this.jdbcTemplate, properties(tableName, null, 1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (String schemaName : unsafeSchemaNames) {
            assertThatThrownBy(() -> new JdbcCocoAuditRecorder(this.jdbcTemplate, properties("audit", schemaName, 1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new JdbcCocoAuditRecorder(this.jdbcTemplate, properties("audit", null, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiresAnExplicitSchemaInitializerWhenInitializationIsEnabled() {
        CocoAuditJdbcProperties properties = properties("initializer_required", null, 1);
        properties.setInitializeSchema(true);

        assertThatThrownBy(() -> new JdbcCocoAuditRecorder(this.jdbcTemplate, properties))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(CocoAuditSchemaInitializer.class.getSimpleName());
        Integer tables = this.jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                 WHERE TABLE_NAME = 'INITIALIZER_REQUIRED'
                """, Integer.class);
        assertThat(tables).isZero();
    }

    @Test
    void delegatesSchemaInitializationToTheExplicitDatabaseDialectSpi() {
        CocoAuditJdbcProperties properties = properties("spi_initialized_audit", null, 1);
        properties.setInitializeSchema(true);
        AtomicReference<CocoAuditJdbcSchema> initializedSchema = new AtomicReference<>();
        CocoAuditSchemaInitializer initializer = (jdbcOperations, schema) -> {
            initializedSchema.set(schema);
            jdbcOperations.execute("""
                    CREATE TABLE spi_initialized_audit (
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
        };

        JdbcCocoAuditRecorder recorder = new JdbcCocoAuditRecorder(this.jdbcTemplate, properties, initializer);
        recorder.record(event("spi-initialized"));

        assertThat(initializedSchema.get())
                .extracting(CocoAuditJdbcSchema::tableReference)
                .isEqualTo("spi_initialized_audit");
        Integer rows = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM spi_initialized_audit", Integer.class);
        assertThat(rows).isEqualTo(1);
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

    @Test
    void closeWaitsForAnInFlightWriteAndPreventsWritesAfterReturning() throws Exception {
        BlockingJdbcTemplate blockingTemplate = new BlockingJdbcTemplate(this.dataSource);
        JdbcCocoAuditRecorder recorder = new JdbcCocoAuditRecorder(
                blockingTemplate, properties("coco_audit_event", null, 1));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> write = executor.submit(() -> recorder.record(event("in-flight")));
            assertThat(blockingTemplate.entered.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> close = executor.submit(recorder::close);
            assertThatThrownBy(() -> close.get(100, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);

            blockingTemplate.release.countDown();
            write.get(5, TimeUnit.SECONDS);
            close.get(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> recorder.record(event("after-concurrent-close")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
            assertThat(rowCount()).isEqualTo(1);
        }
        finally {
            blockingTemplate.release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void closeCompletesUnderSustainedWritePressureAndRejectsNewWrites() throws Exception {
        JdbcCocoAuditRecorder recorder = newRecorder(1);
        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicBoolean keepWriting = new AtomicBoolean(true);
        AtomicInteger successfulWrites = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(4);
        CountDownLatch wrote = new CountDownLatch(1);
        try {
            List<Future<?>> writers = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                int writer = index;
                writers.add(executor.submit(() -> {
                    started.countDown();
                    while (keepWriting.get()) {
                        try {
                            recorder.record(event("pressure-" + writer));
                            successfulWrites.incrementAndGet();
                            wrote.countDown();
                        }
                        catch (IllegalStateException ex) {
                            return null;
                        }
                    }
                    return null;
                }));
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(wrote.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> close = executor.submit(recorder::close);
            close.get(5, TimeUnit.SECONDS);
            keepWriting.set(false);
            for (Future<?> writer : writers) {
                writer.get(5, TimeUnit.SECONDS);
            }

            assertThat(successfulWrites.get()).isPositive();
            assertThatThrownBy(() -> recorder.record(event("after-pressure-close")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }
        finally {
            keepWriting.set(false);
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
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
        return eventAt(type, Instant.parse("2026-07-15T08:30:00Z"));
    }

    private static CocoAuditEvent eventAt(String type, Instant occurredAt) {
        return CocoAuditEvent.builder(type).occurredAt(occurredAt).build();
    }

    private static final class BlockingJdbcTemplate extends JdbcTemplate {

        private final CountDownLatch entered = new CountDownLatch(1);

        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingJdbcTemplate(DriverManagerDataSource dataSource) {
            super(dataSource);
        }

        @Override
        public int[] batchUpdate(String sql, BatchPreparedStatementSetter preparedStatementSetter) {
            this.entered.countDown();
            try {
                if (!this.release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test batch write was not released");
                }
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test batch write was interrupted", ex);
            }
            return super.batchUpdate(sql, preparedStatementSetter);
        }
    }
}
