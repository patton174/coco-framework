package io.github.coco.feature.audit.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration;
import io.github.coco.feature.audit.CocoAuditAutoConfiguration;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.feature.audit.core.LoggingCocoAuditRecorder;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * JDBC 审计自动配置测试。
 */
class CocoAuditJdbcAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoCommonLoggingAutoConfiguration.class,
                    CocoAuditJdbcAutoConfiguration.class,
                    CocoAuditAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @Test
    void injectsJdbcRecorderBeforeTheDefaultLoggingRecorder() {
        this.contextRunner
                .withPropertyValues("coco.audit.jdbc.enabled=true")
                .withBean(JdbcOperations.class, () -> jdbcOperations("inject"))
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoAuditRecorder.class);
                    assertThat(context).hasSingleBean(JdbcCocoAuditRecorder.class);
                    assertThat(context).doesNotHaveBean(LoggingCocoAuditRecorder.class);

                    CocoMessageService messageService = context.getBean(CocoMessageService.class);
                    assertThat(messageService.getMessage("coco.audit.jdbc.ready")).contains("Coco JDBC");
                });
    }

    @Test
    void backsOffWhenTheBusinessProvidesAnAuditRecorder() {
        CocoAuditRecorder customRecorder = event -> { };
        this.contextRunner
                .withPropertyValues("coco.audit.jdbc.enabled=true")
                .withBean(JdbcOperations.class, () -> jdbcOperations("custom"))
                .withBean(CocoAuditRecorder.class, () -> customRecorder)
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoAuditRecorder.class);
                    assertThat(context.getBean(CocoAuditRecorder.class)).isSameAs(customRecorder);
                    assertThat(context).doesNotHaveBean(JdbcCocoAuditRecorder.class);
                });
    }

    @Test
    void remainsOptInAndLeavesTheDefaultLoggingRecorderInPlace() {
        this.contextRunner
                .withBean(JdbcOperations.class, () -> jdbcOperations("disabled"))
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoAuditRecorder.class);
                    assertThat(context).hasSingleBean(LoggingCocoAuditRecorder.class);
                    assertThat(context).doesNotHaveBean(JdbcCocoAuditRecorder.class);
                });
    }

    @Test
    void usesPrimaryJdbcOperationsWhenSeveralCandidatesExist() {
        this.contextRunner
                .withPropertyValues("coco.audit.jdbc.enabled=true")
                .withUserConfiguration(MultipleJdbcOperationsConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(JdbcCocoAuditRecorder.class));
    }

    private static JdbcOperations jdbcOperations(String databaseName) {
        return new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:audit_auto_" + databaseName + ";DB_CLOSE_DELAY=-1", "sa", ""));
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleJdbcOperationsConfiguration {

        @Bean
        @Primary
        JdbcOperations primaryJdbcOperations() {
            return jdbcOperations("primary");
        }

        @Bean
        JdbcOperations secondaryJdbcOperations() {
            return jdbcOperations("secondary");
        }
    }
}
