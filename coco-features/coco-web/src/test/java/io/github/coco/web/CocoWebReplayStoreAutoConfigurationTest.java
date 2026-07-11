package io.github.coco.web;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import io.github.coco.spring.boot.autoconfigure.i18n.CocoI18nAutoConfiguration;
import io.github.coco.web.replay.CocoReplayStore;
import io.github.coco.web.replay.InMemoryCocoReplayStore;
import io.github.coco.web.replay.JdbcCocoReplayStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CocoWebReplayStoreAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoI18nAutoConfiguration.class,
                    CocoWebJdbcReplayAutoConfiguration.class,
                    CocoWebAutoConfiguration.class));

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoI18nAutoConfiguration.class,
                    CocoWebJdbcReplayAutoConfiguration.class,
                    CocoWebAutoConfiguration.class));

    @Test
    void createsInMemoryStoreByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CocoReplayStore.class);
            assertThat(context).hasSingleBean(InMemoryCocoReplayStore.class);
            assertThat(context).doesNotHaveBean(JdbcCocoReplayStore.class);
        });
    }

    @Test
    void createsJdbcStoreForSingleJdbcOperationsCandidate() {
        this.contextRunner
                .withPropertyValues("coco.web.replay.store-type=jdbc")
                .withBean(JdbcOperations.class, () -> jdbcOperations("single"))
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoReplayStore.class);
                    assertThat(context).hasSingleBean(JdbcCocoReplayStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryCocoReplayStore.class);
                });
    }

    @Test
    void createsJdbcStoreFromBootJdbcAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        CocoWebAutoConfiguration.class,
                        CocoWebJdbcReplayAutoConfiguration.class,
                        CocoI18nAutoConfiguration.class,
                        JdbcTemplateAutoConfiguration.class,
                        DataSourceAutoConfiguration.class))
                .withPropertyValues(
                        "spring.datasource.generate-unique-name=true",
                        "coco.web.replay.store-type=jdbc")
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    assertThat(context).hasSingleBean(JdbcOperations.class);
                    assertThat(context).hasSingleBean(JdbcCocoReplayStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryCocoReplayStore.class);
                });
    }

    @Test
    void missingBootJdbcAutoConfigurationIsClassLoadingSafeForDefaultMemoryStore() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(JdbcTemplateAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(InMemoryCocoReplayStore.class);
                    assertThat(context).doesNotHaveBean(JdbcCocoReplayStore.class);
                });
    }

    @Test
    void manualJdbcOperationsDoesNotRequireBootJdbcAutoConfiguration() {
        this.contextRunner
                .withClassLoader(new FilteredClassLoader(JdbcTemplateAutoConfiguration.class))
                .withPropertyValues("coco.web.replay.store-type=jdbc")
                .withBean(JdbcOperations.class, () -> jdbcOperations("manual-without-boot-jdbc"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JdbcCocoReplayStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryCocoReplayStore.class);
                });
    }

    @Test
    void createsJdbcStoreForPrimaryJdbcOperationsCandidate() {
        this.contextRunner
                .withPropertyValues("coco.web.replay.store-type=jdbc")
                .withUserConfiguration(PrimaryJdbcOperationsConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(JdbcCocoReplayStore.class));
    }

    @Test
    void doesNotGuessBetweenMultipleJdbcOperationsCandidates() {
        this.webContextRunner
                .withPropertyValues("coco.web.replay.store-type=jdbc")
                .withUserConfiguration(MultipleJdbcOperationsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class)
                            .hasStackTraceContaining(CocoReplayStore.class.getName());
                });
    }

    @Test
    void jdbcSelectionWithoutJdbcOperationsFailsServletStartup() {
        this.webContextRunner
                .withPropertyValues("coco.web.replay.store-type=jdbc")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class)
                            .hasStackTraceContaining(CocoReplayStore.class.getName());
                });
    }

    @Test
    void missingSpringJdbcIsClassLoadingSafeAndDoesNotFallBackToMemory() {
        this.webContextRunner
                .withClassLoader(new FilteredClassLoader(JdbcOperations.class))
                .withPropertyValues("coco.web.replay.store-type=jdbc")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class)
                            .hasStackTraceContaining(CocoReplayStore.class.getName());
                });
    }

    @Test
    void customReplayStoreBacksOffJdbcWithoutJdbcInfrastructure() {
        CocoReplayStore customStore = (key, expiresAt) -> true;
        this.webContextRunner
                .withClassLoader(new FilteredClassLoader(JdbcOperations.class))
                .withPropertyValues("coco.web.replay.store-type=jdbc")
                .withBean(CocoReplayStore.class, () -> customStore)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CocoReplayStore.class);
                    assertThat(context.getBean(CocoReplayStore.class)).isSameAs(customStore);
                    assertThat(context).doesNotHaveBean(InMemoryCocoReplayStore.class);
                    assertThat(context).doesNotHaveBean(JdbcCocoReplayStore.class);
                });
    }

    @Test
    void customReplayStoreBacksOffJdbcWithEligibleCandidate() {
        CocoReplayStore customStore = (key, expiresAt) -> true;
        this.contextRunner
                .withPropertyValues("coco.web.replay.store-type=jdbc")
                .withBean(JdbcOperations.class, () -> jdbcOperations("custom-store"))
                .withBean(CocoReplayStore.class, () -> customStore)
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoReplayStore.class);
                    assertThat(context.getBean(CocoReplayStore.class)).isSameAs(customStore);
                    assertThat(context).doesNotHaveBean(InMemoryCocoReplayStore.class);
                    assertThat(context).doesNotHaveBean(JdbcCocoReplayStore.class);
                });
    }

    @Test
    void disabledReplayDoesNotCreateJdbcStore() {
        this.contextRunner
                .withPropertyValues(
                        "coco.web.replay.enabled=false",
                        "coco.web.replay.store-type=jdbc")
                .withBean(JdbcOperations.class, () -> jdbcOperations("disabled"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CocoReplayStore.class);
                    assertThat(context).doesNotHaveBean(JdbcCocoReplayStore.class);
                    assertThat(context).doesNotHaveBean(InMemoryCocoReplayStore.class);
                });
    }

    private static JdbcOperations jdbcOperations(String databaseName) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1", "sa", "");
        return new JdbcTemplate(dataSource);
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryJdbcOperationsConfiguration {

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

    @Configuration(proxyBeanMethods = false)
    static class MultipleJdbcOperationsConfiguration {

        @Bean
        JdbcOperations firstJdbcOperations() {
            return jdbcOperations("first");
        }

        @Bean
        JdbcOperations secondJdbcOperations() {
            return jdbcOperations("second");
        }
    }
}
