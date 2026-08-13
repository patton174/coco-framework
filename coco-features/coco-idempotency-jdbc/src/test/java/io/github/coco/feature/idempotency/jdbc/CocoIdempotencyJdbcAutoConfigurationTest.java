package io.github.coco.feature.idempotency.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Locale;

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
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.exception.DefaultCocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.response.CocoSystemCodes;
import io.github.coco.i18n.CocoMessage;
import io.github.coco.i18n.CocoMessageService;

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
    void registersStoreFromJdbcTemplateAndUserStoreBacksOff() {
        this.contextRunner.withPropertyValues(enabled()).withBean(JdbcTemplate.class,
                () -> new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        "jdbc:h2:mem:auto_jdbc;DB_CLOSE_DELAY=-1", "sa", ""))).run(context -> {
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
    void jdbcStoreWinsOverCoreMemoryStoreWhenBothConfigurationsArePresent() {
        this.webContextRunner
                .withBean(JdbcTemplate.class, () -> new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        "jdbc:h2:mem:auto_core;DB_CLOSE_DELAY=-1", "sa", "")))
                .withPropertyValues("coco.idempotency.enabled=true", "coco.idempotency.jdbc.enabled=true",
                        "coco.idempotency.routes[0].methods[0]=POST",
                        "coco.idempotency.routes[0].path-patterns[0]=/orders/**").run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CocoIdempotencyStore.class)).isInstanceOf(JdbcCocoIdempotencyStore.class);
                });
    }

    @Test
    void referenceDdlIsPackaged() {
        assertThat(getClass().getClassLoader().getResource("META-INF/coco/idempotency-jdbc-reference.sql")).isNotNull();
    }

    private static String[] enabled() {
        return new String[] { "coco.idempotency.enabled=true", "coco.idempotency.jdbc.enabled=true" };
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
