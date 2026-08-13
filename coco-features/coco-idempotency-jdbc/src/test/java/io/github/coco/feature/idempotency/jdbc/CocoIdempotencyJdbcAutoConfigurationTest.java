package io.github.coco.feature.idempotency.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.feature.idempotency.CocoIdempotencyAutoConfiguration;
import io.github.coco.feature.idempotency.store.CocoIdempotencyAcquireResult;
import io.github.coco.feature.idempotency.store.CocoIdempotencyLease;
import io.github.coco.feature.idempotency.store.CocoIdempotencyRequest;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStore;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStoredResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.exception.DefaultCocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.response.CocoSystemCodes;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageService;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

class CocoIdempotencyJdbcAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoIdempotencyJdbcAutoConfiguration.class));

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoIdempotencyJdbcAutoConfiguration.class,
                    CocoIdempotencyAutoConfiguration.class))
            .withUserConfiguration(WebWriterConfiguration.class);

    @Test
    void remainsDisabledByDefaultAndFailsFastWhenEnabledWithoutDataSource() {
        this.contextRunner.run(context -> assertThat(context).doesNotHaveBean(JdbcCocoIdempotencyStore.class));
        this.contextRunner.withPropertyValues(enabled()).run(context -> {
            assertThat(context.getStartupFailure()).isNotNull();
            assertThat(context.getStartupFailure()).hasMessageContaining("DataSource");
        });
    }

    @Test
    void registersStoreFromSingleDataSourceAndUserStoreBacksOff() {
        this.contextRunner.withPropertyValues(enabled()).withBean(DataSource.class,
                () -> dataSource("auto_jdbc")).run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CocoIdempotencyStore.class);
                    assertThat(context.getBean(CocoIdempotencyStore.class)).isInstanceOf(JdbcCocoIdempotencyStore.class);
                });
        this.contextRunner.withUserConfiguration(UserStoreConfiguration.class).withPropertyValues(enabled())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CocoIdempotencyStore.class)).isInstanceOf(UserStore.class);
                    assertThat(context).doesNotHaveBean(JdbcCocoIdempotencyStore.class);
                });
    }

    @Test
    void namedDedicatedDataSourceWinsWhenSeveralDataSourcesExist() {
        DataSource dedicated = dataSource("auto_dedicated");
        this.contextRunner.withPropertyValues(enabled())
                .withBean("primaryBusinessDataSource", DataSource.class,
                        () -> dataSource("auto_primary_business"), beanDefinition -> beanDefinition.setPrimary(true))
                .withBean("secondaryBusinessDataSource", DataSource.class,
                        () -> dataSource("auto_secondary_business"))
                .withBean(CocoIdempotencyJdbcAutoConfiguration.DATA_SOURCE_BEAN_NAME, DataSource.class,
                        () -> dedicated)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JdbcCocoIdempotencyStore.class);
                    assertThat(context.getBean(JdbcCocoIdempotencyStore.class))
                            .extracting("dataSource").isSameAs(dedicated);
                });
    }

    @Test
    void primaryDataSourceDoesNotOverrideStrictSingleBeanFallback() {
        this.contextRunner.withPropertyValues(enabled())
                .withBean("primaryDataSource", DataSource.class, () -> dataSource("auto_primary"),
                        beanDefinition -> beanDefinition.setPrimary(true))
                .withBean("secondaryDataSource", DataSource.class, () -> dataSource("auto_secondary"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause().hasMessageContaining("exactly one DataSource bean");
                });
    }

    @Test
    void fallsBackToTheOnlyDataSourceInAParentContext() {
        DataSource parentDataSource = dataSource("auto_parent_only");
        try (AnnotationConfigApplicationContext parent = parentContext(
                Map.of("parentDataSource", parentDataSource))) {
            this.contextRunner.withParent(parent).withPropertyValues(enabled()).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(JdbcCocoIdempotencyStore.class))
                        .extracting("dataSource").isSameAs(parentDataSource);
            });
        }
    }

    @Test
    void parentAndChildDataSourcesFailStrictFallback() {
        try (AnnotationConfigApplicationContext parent = parentContext(
                Map.of("parentDataSource", dataSource("auto_parent_candidate")))) {
            this.contextRunner.withParent(parent).withPropertyValues(enabled())
                    .withBean("childDataSource", DataSource.class, () -> dataSource("auto_child_candidate"))
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                                .rootCause().hasMessageContaining("exactly one DataSource bean");
                    });
        }
    }

    @Test
    void namedParentDataSourceWinsOverOtherParentAndChildDataSources() {
        DataSource dedicated = dataSource("auto_parent_dedicated");
        try (AnnotationConfigApplicationContext parent = parentContext(Map.of(
                CocoIdempotencyJdbcAutoConfiguration.DATA_SOURCE_BEAN_NAME, dedicated,
                "parentBusinessDataSource", dataSource("auto_parent_business")))) {
            this.contextRunner.withParent(parent).withPropertyValues(enabled())
                    .withBean("childBusinessDataSource", DataSource.class,
                            () -> dataSource("auto_child_business"))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(JdbcCocoIdempotencyStore.class))
                                .extracting("dataSource").isSameAs(dedicated);
                    });
        }
    }

    @Test
    void severalOrdinaryDataSourcesWithoutDedicatedBeanFailFast() {
        this.contextRunner.withPropertyValues(enabled())
                .withBean("firstDataSource", DataSource.class, () -> dataSource("auto_first"))
                .withBean("secondDataSource", DataSource.class, () -> dataSource("auto_second"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                            .rootCause().hasMessageContaining(
                                    CocoIdempotencyJdbcAutoConfiguration.DATA_SOURCE_BEAN_NAME);
                });
    }

    @Test
    void rejectsTransactionAwareDataSourceAtStartupIncludingNestedProxy() {
        DataSource target = dataSource("auto_transaction_aware");
        this.contextRunner.withPropertyValues(enabled())
                .withBean(DataSource.class, () -> new TransactionAwareDataSourceProxy(target))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .rootCause().hasMessageContaining("must not be transaction-aware");
                });
        this.contextRunner.withPropertyValues(enabled())
                .withBean(DataSource.class,
                        () -> new LazyConnectionDataSourceProxy(new TransactionAwareDataSourceProxy(target)))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .rootCause().hasMessageContaining("must not be transaction-aware");
                });
    }

    @Test
    void jdbcStoreWinsOverCoreMemoryStoreWhenBothConfigurationsArePresent() {
        this.webContextRunner
                .withBean(DataSource.class, () -> dataSource("auto_core"))
                .withPropertyValues("coco.idempotency.enabled=true", "coco.idempotency.jdbc.enabled=true",
                        "coco.idempotency.routes[0].methods[0]=POST",
                        "coco.idempotency.routes[0].path-patterns[0]=/orders/**").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CocoIdempotencyStore.class)).isInstanceOf(JdbcCocoIdempotencyStore.class);
                });
    }

    @Test
    void dialectDdlResourcesArePackagedWithoutAmbiguousReferenceDdl() {
        ClassLoader loader = getClass().getClassLoader();
        assertThat(loader.getResource("META-INF/coco/idempotency-jdbc-h2.sql")).isNotNull();
        assertThat(loader.getResource("META-INF/coco/idempotency-jdbc-postgresql.sql")).isNotNull();
        assertThat(loader.getResource("META-INF/coco/idempotency-jdbc-mysql.sql")).isNotNull();
        assertThat(Path.of("src/main/resources/META-INF/coco/idempotency-jdbc-reference.sql")).doesNotExist();
    }

    private static String[] enabled() {
        return new String[] { "coco.idempotency.enabled=true", "coco.idempotency.jdbc.enabled=true" };
    }

    private static DataSource dataSource(String databaseName) {
        return new DriverManagerDataSource("jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static AnnotationConfigApplicationContext parentContext(Map<String, DataSource> dataSources) {
        AnnotationConfigApplicationContext parent = new AnnotationConfigApplicationContext();
        dataSources.forEach((name, dataSource) -> parent.registerBean(name, DataSource.class, () -> dataSource));
        parent.refresh();
        return parent;
    }

    @Configuration(proxyBeanMethods = false)
    static class UserStoreConfiguration {
        @Bean CocoIdempotencyStore userStore() { return new UserStore(); }
    }

    @Configuration(proxyBeanMethods = false)
    static class WebWriterConfiguration {
        @Bean
        CocoFilterExceptionResponseWriter cocoFilterExceptionResponseWriter() {
            return new CocoFilterExceptionResponseWriter(new CocoWebExceptionHandler(new StaticMessageService(),
                    new DefaultCocoExceptionHttpStatusResolver(), CocoSystemCodes.defaults()), new ObjectMapper());
        }
    }

    private static final class UserStore implements CocoIdempotencyStore {
        @Override public CocoIdempotencyAcquireResult acquire(CocoIdempotencyRequest request, Instant now, Instant expiresAt) { return CocoIdempotencyAcquireResult.inProgress(); }
        @Override public boolean complete(CocoIdempotencyLease lease, CocoIdempotencyStoredResponse response, Instant now) { return false; }
        @Override public boolean fail(CocoIdempotencyLease lease, Instant now) { return false; }
    }

    private static final class StaticMessageService implements CocoMessageService {
        @Override public String getMessage(String code, Object... args) { return code; }
        @Override public String getMessage(String code, Locale locale, Object... args) { return code; }
        @Override public String getMessageOrDefault(String code, String defaultMessage, Object... args) { return code; }
        @Override public String getMessageOrDefault(String code, String defaultMessage, Locale locale, Object... args) { return code; }
        @Override public String resolve(CocoMessage message) { return message == null ? "" : message.code(); }
        @Override public String resolve(CocoMessage message, Locale locale) { return resolve(message); }
    }
}
