package example.auditjdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration;
import io.github.coco.feature.audit.CocoAuditAutoConfiguration;
import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditPublisher;
import io.github.coco.feature.audit.jdbc.CocoAuditJdbcAutoConfiguration;
import io.github.coco.feature.audit.jdbc.CocoAuditJdbcSchema;
import io.github.coco.feature.audit.jdbc.CocoAuditSchemaInitializer;
import io.github.coco.feature.audit.jdbc.JdbcCocoAuditRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 外部业务项目使用 Hikari 和 H2 的 JDBC 审计集成测试。
 */
class ExternalConsumerHikariIntegrationTest {

    @Test
    void initializesConfiguredTableAndPublishesFromExternalConsumer() {
        HikariDataSource dataSource = newDataSource();
        try {
            new JdbcTemplate(dataSource).execute("CREATE SCHEMA audit");

            runner(dataSource, true, "audit").run(context -> {
                assertThat(context).hasSingleBean(JdbcCocoAuditRecorder.class);
                assertThat(context.getBean(javax.sql.DataSource.class)).isSameAs(dataSource);

                context.getBean(CocoAuditPublisher.class).publish(event("external-create"));

                assertThat(rowCount(context, "audit.coco_audit_event")).isEqualTo(1);
            });
        }
        finally {
            dataSource.close();
        }
    }

    @Test
    void leavesTheTableAbsentWhenSchemaInitializationIsDisabled() {
        HikariDataSource dataSource = newDataSource();
        try {
            new JdbcTemplate(dataSource).execute("CREATE SCHEMA audit");

            runner(dataSource, false, "audit").run(context -> {
                JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                Integer tables = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
                         WHERE TABLE_SCHEMA = 'AUDIT' AND TABLE_NAME = 'COCO_AUDIT_EVENT'
                        """, Integer.class);

                assertThat(tables).isZero();
            });
        }
        finally {
            dataSource.close();
        }
    }

    @Test
    void commitsAuditWritesWithinTheExternalConsumerTransaction() {
        HikariDataSource dataSource = newDataSource();
        try {
            runner(dataSource, true, null).run(context -> {
                TransactionTemplate transactionTemplate = new TransactionTemplate(
                        new DataSourceTransactionManager(dataSource));
                CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);

                transactionTemplate.executeWithoutResult(status -> publisher.publish(event("committed")));

                assertThat(rowCount(context, "coco_audit_event")).isEqualTo(1);
            });
        }
        finally {
            dataSource.close();
        }
    }

    @Test
    void rollsBackAuditWritesWithinTheExternalConsumerTransaction() {
        HikariDataSource dataSource = newDataSource();
        try {
            runner(dataSource, true, null).run(context -> {
                TransactionTemplate transactionTemplate = new TransactionTemplate(
                        new DataSourceTransactionManager(dataSource));
                CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);

                transactionTemplate.executeWithoutResult(status -> {
                    publisher.publish(event("rolled-back"));
                    status.setRollbackOnly();
                });

                assertThat(rowCount(context, "coco_audit_event")).isZero();
            });
        }
        finally {
            dataSource.close();
        }
    }

    @Test
    void writesBatchesAndConcurrentEventsThroughTheExternalConsumerPool() throws Exception {
        HikariDataSource dataSource = newDataSource();
        try {
            runner(dataSource, true, null).run(context -> {
                JdbcCocoAuditRecorder recorder = context.getBean(JdbcCocoAuditRecorder.class);
                recorder.recordBatch(List.of(event("batch-1"), event("batch-2"), event("batch-3")));

                int contenders = 12;
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

                assertThat(rowCount(context, "coco_audit_event")).isEqualTo(contenders + 3);
            });
        }
        finally {
            dataSource.close();
        }
    }

    @Test
    void preservesLongJsonAndNormalizesEmptyExternalConsumerFields() {
        HikariDataSource dataSource = newDataSource();
        try {
            runner(dataSource, true, null).run(context -> {
                String payload = "p".repeat(50_000);
                JdbcCocoAuditRecorder recorder = context.getBean(JdbcCocoAuditRecorder.class);
                recorder.record(CocoAuditEvent.builder("t".repeat(20_000))
                        .action(" ")
                        .resourceId(" ")
                        .attribute("payload", payload)
                        .build());

                JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT LENGTH(event_type) FROM coco_audit_event", Integer.class)).isEqualTo(20_000);
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT LENGTH(attributes_json) FROM coco_audit_event", Integer.class)).isGreaterThan(50_000);
                assertThat(jdbcTemplate.queryForObject("SELECT action FROM coco_audit_event", String.class)).isNull();
                assertThat(jdbcTemplate.queryForObject("SELECT resource_id FROM coco_audit_event", String.class))
                        .isNull();
            });
        }
        finally {
            dataSource.close();
        }
    }

    @Test
    void failsSafelyWhenAnExternalConsumerDoesNotProvideTheTable() {
        HikariDataSource dataSource = newDataSource();
        try {
            runner(dataSource, false, null).run(context -> {
                CocoAuditPublisher publisher = context.getBean(CocoAuditPublisher.class);

                assertThatCode(() -> publisher.publish(event("table-missing"))).doesNotThrowAnyException();
                assertThat(dataSource.isClosed()).isFalse();
            });
        }
        finally {
            dataSource.close();
        }
    }

    @Test
    void closingTheExternalConsumerRecorderDoesNotCloseItsDataSource() {
        HikariDataSource dataSource = newDataSource();
        try {
            runner(dataSource, true, null).run(context -> {
                JdbcCocoAuditRecorder recorder = context.getBean(JdbcCocoAuditRecorder.class);
                recorder.close();

                assertThatThrownBy(() -> recorder.record(event("after-close")))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("closed");
                assertThat(dataSource.isClosed()).isFalse();
            });
        }
        finally {
            dataSource.close();
        }
    }

    private static ApplicationContextRunner runner(HikariDataSource dataSource, boolean initializeSchema,
            String schema) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        CocoCommonAutoConfiguration.class,
                        CocoCommonLoggingAutoConfiguration.class,
                        CocoAuditJdbcAutoConfiguration.class,
                        CocoAuditAutoConfiguration.class,
                        JdbcTemplateAutoConfiguration.class))
                .withBean(javax.sql.DataSource.class, () -> dataSource)
                .withPropertyValues(
                        "coco.common.i18n.basename=coco-messages",
                        "coco.audit.jdbc.enabled=true",
                        "coco.audit.jdbc.initialize-schema=" + initializeSchema);
        if (initializeSchema) {
            runner = runner.withBean(CocoAuditSchemaInitializer.class,
                    () -> ExternalConsumerHikariIntegrationTest::initializeH2Schema);
        }
        return schema == null ? runner : runner.withPropertyValues("coco.audit.jdbc.schema=" + schema);
    }

    private static HikariDataSource newDataSource() {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl("jdbc:h2:mem:external_audit_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        configuration.setUsername("sa");
        configuration.setMaximumPoolSize(8);
        return new HikariDataSource(configuration);
    }

    private static int rowCount(org.springframework.boot.test.context.assertj.AssertableApplicationContext context,
            String table) {
        Integer count = context.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM " + table,
                Integer.class);
        return count == null ? 0 : count;
    }

    private static void initializeH2Schema(org.springframework.jdbc.core.JdbcOperations jdbcOperations,
            CocoAuditJdbcSchema schema) {
        jdbcOperations.execute("CREATE TABLE " + schema.tableReference() + " ("
                + "event_type CLOB NOT NULL, action CLOB NULL, resource_type CLOB NULL, resource_id CLOB NULL, "
                + "trace_id CLOB NULL, actor CLOB NULL, tenant_id CLOB NULL, success BOOLEAN NOT NULL, "
                + "occurred_at_epoch_millis BIGINT NOT NULL, attributes_json CLOB NOT NULL)");
    }

    private static CocoAuditEvent event(String type) {
        return CocoAuditEvent.builder(type).occurredAt(Instant.parse("2026-07-15T08:30:00Z")).build();
    }
}
