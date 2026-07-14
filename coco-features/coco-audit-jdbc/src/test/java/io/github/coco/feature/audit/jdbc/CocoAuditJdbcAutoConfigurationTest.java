package io.github.coco.feature.audit.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.logging.autoconfigure.CocoCommonLoggingAutoConfiguration;
import io.github.coco.feature.audit.CocoAuditAutoConfiguration;
import io.github.coco.feature.audit.core.CocoAuditFormatter;
import io.github.coco.feature.audit.core.CocoAuditRecorder;
import io.github.coco.feature.audit.core.LoggingCocoAuditRecorder;
import io.github.coco.i18n.CocoMessageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
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

    @Test
    void createsRecorderWithManualJdbcOperationsWhenJdbcTemplateIsUnavailable() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(JdbcTemplate.class, JdbcTemplateAutoConfiguration.class))
                .withPropertyValues("coco.audit.jdbc.enabled=true")
                .withBean(JdbcOperations.class, CocoAuditJdbcAutoConfigurationTest::manualJdbcOperations)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JdbcCocoAuditRecorder.class);
                });
    }

    @Test
    void backsOffSafelyWhenSpringJdbcIsUnavailable() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(JdbcOperations.class))
                .withPropertyValues("coco.audit.jdbc.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(JdbcCocoAuditRecorder.class);
                    assertThat(context).hasSingleBean(LoggingCocoAuditRecorder.class);
                });
    }

    @Test
    void doesNotActivateWhenTheAuditFeatureIsDisabled() {
        this.contextRunner
                .withPropertyValues("coco.audit.jdbc.enabled=true", "coco.features.disabled[0]=audit")
                .withBean(JdbcOperations.class, () -> jdbcOperations("audit-disabled"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JdbcCocoAuditRecorder.class);
                    assertThat(context).doesNotHaveBean(CocoAuditRecorder.class);
                });
    }

    @Test
    void preservesBusinessFormatterAndRecorderWithoutCreatingJdbcRecorder() {
        CocoAuditRecorder customRecorder = event -> { };
        CocoAuditFormatter customFormatter = event -> "business-format";
        this.contextRunner
                .withPropertyValues("coco.audit.jdbc.enabled=true")
                .withBean(JdbcOperations.class, () -> jdbcOperations("custom-formatter"))
                .withBean(CocoAuditRecorder.class, () -> customRecorder)
                .withBean(CocoAuditFormatter.class, () -> customFormatter)
                .run(context -> {
                    assertThat(context.getBean(CocoAuditRecorder.class)).isSameAs(customRecorder);
                    assertThat(context.getBean(CocoAuditFormatter.class)).isSameAs(customFormatter);
                    assertThat(context).doesNotHaveBean(JdbcCocoAuditRecorder.class);
                    assertThat(context).doesNotHaveBean(LoggingCocoAuditRecorder.class);
                });
    }

    @Test
    void doesNotRequireH2OnTheProductionClasspath() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(org.h2.Driver.class))
                .withPropertyValues("coco.audit.jdbc.enabled=true")
                .withBean(JdbcOperations.class, CocoAuditJdbcAutoConfigurationTest::manualJdbcOperations)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JdbcCocoAuditRecorder.class);
                });
    }

    @Test
    void failsFastWhenSchemaInitializationHasNoBusinessInitializer() {
        this.contextRunner
                .withPropertyValues(
                        "coco.audit.jdbc.enabled=true",
                        "coco.audit.jdbc.initialize-schema=true")
                .withBean(JdbcOperations.class, () -> jdbcOperations("initializer-missing"))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining(CocoAuditSchemaInitializer.class.getSimpleName());
                });
    }

    private static JdbcOperations jdbcOperations(String databaseName) {
        return new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:audit_auto_" + databaseName + ";DB_CLOSE_DELAY=-1", "sa", ""));
    }

    private static JdbcOperations manualJdbcOperations() {
        return (JdbcOperations) Proxy.newProxyInstance(JdbcOperations.class.getClassLoader(),
                new Class<?>[] { JdbcOperations.class }, (proxy, method, arguments) -> {
                    if (method.getName().equals("toString")) {
                        return "manualJdbcOperations";
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == arguments[0];
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
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
