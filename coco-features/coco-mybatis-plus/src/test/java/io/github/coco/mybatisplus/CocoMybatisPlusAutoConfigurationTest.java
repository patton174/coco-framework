package io.github.coco.mybatisplus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusInnerInterceptorAutoConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.IllegalSQLInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.coco.spring.boot.autoconfigure.i18n.CocoI18nAutoConfiguration;
import io.github.coco.exception.type.CocoRequestException;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.mybatisplus.interceptor.CocoMybatisPlusInterceptorCustomizer;
import io.github.coco.spring.boot.autoconfigure.feature.CocoFeatureAutoConfigurationImportFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.env.MockEnvironment;

/**
 * Coco MyBatis-Plus 功能自动配置测试。
 * <p>
 * 验证 MyBatis-Plus 功能模块会注册消息资源、默认分页拦截器和 SQL 拦截扩展点。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ExtendWith(OutputCaptureExtension.class)
class CocoMybatisPlusAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoI18nAutoConfiguration.class,
                    CocoMybatisPlusAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @Test
    void registersMybatisPlusMessageBundle() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertTrue(context.containsBean("cocoMybatisPlusMessageBundleRegistrar"));
            assertEquals("Coco MyBatis-Plus 功能消息资源已就绪。",
                    messageService.getMessage("coco.feature.mybatis-plus.ready"));
        });
    }

    @Test
    void createsDefaultPaginationInterceptor() {
        this.contextRunner
                .withPropertyValues(
                        "coco.mybatis-plus.pagination.db-type=mysql",
                        "coco.mybatis-plus.pagination.overflow=true",
                        "coco.mybatis-plus.pagination.max-limit=200",
                        "coco.mybatis-plus.pagination.optimize-join=false")
                .run(context -> {
                    MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

                    assertThat(interceptor.getInterceptors()).hasSize(1);
                    assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(PaginationInnerInterceptor.class);
                    PaginationInnerInterceptor pagination = (PaginationInnerInterceptor) interceptor.getInterceptors()
                            .get(0);
                    assertThat(pagination.getDbType()).isEqualTo(DbType.MYSQL);
                    assertThat(pagination.isOverflow()).isTrue();
                    assertThat(pagination.getMaxLimit()).isEqualTo(200L);
                    assertThat(pagination.isOptimizeJoin()).isFalse();
                });
    }

    @Test
    void appendsPaginationAfterCustomInterceptors() {
        this.contextRunner
                .withUserConfiguration(CustomizerConfiguration.class)
                .run(context -> {
                    MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

                    assertThat(interceptor.getInterceptors()).hasSize(2);
                    assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(OptimisticLockerInnerInterceptor.class);
                    assertThat(interceptor.getInterceptors().get(1)).isInstanceOf(PaginationInnerInterceptor.class);
                });
    }

    @Test
    void registersSqlGuardInterceptorsBeforePagination() {
        this.contextRunner
                .withPropertyValues(
                        "coco.mybatis-plus.sql-guard.block-attack-enabled=true",
                        "coco.mybatis-plus.sql-guard.illegal-sql-enabled=true")
                .run(context -> {
                    MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

                    assertThat(interceptor.getInterceptors()).hasSize(3);
                    assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(BlockAttackInnerInterceptor.class);
                    assertThat(interceptor.getInterceptors().get(1)).isInstanceOf(IllegalSQLInnerInterceptor.class);
                    assertThat(interceptor.getInterceptors().get(2)).isInstanceOf(PaginationInnerInterceptor.class);
                });
    }

    @Test
    void keepsSqlGuardDisabledByDefault() {
        this.contextRunner.run(context -> {
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

            assertThat(interceptor.getInterceptors()).noneMatch(BlockAttackInnerInterceptor.class::isInstance);
            assertThat(interceptor.getInterceptors()).noneMatch(IllegalSQLInnerInterceptor.class::isInstance);
        });
    }

    @Test
    void logsProductionRecommendationWhenSqlGuardIsDisabled(CapturedOutput output) {
        this.contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);

            assertThat(output)
                    .contains("Coco MyBatis-Plus SQL guard is disabled")
                    .contains("coco.mybatis-plus.sql-guard.block-attack-enabled")
                    .contains("coco.mybatis-plus.sql-guard.illegal-sql-enabled");
        });
    }

    @Test
    void keepsSqlGuardWhenPaginationIsDisabled() {
        this.contextRunner
                .withPropertyValues(
                        "coco.mybatis-plus.pagination.enabled=false",
                        "coco.mybatis-plus.sql-guard.block-attack-enabled=true")
                .run(context -> {
                    MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

                    assertThat(interceptor.getInterceptors()).hasSize(1);
                    assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(BlockAttackInnerInterceptor.class);
                });
    }

    @Test
    void disablesDefaultPaginationInterceptor() {
        this.contextRunner
                .withPropertyValues("coco.mybatis-plus.pagination.enabled=false")
                .run(context -> {
                    MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

                    assertThat(interceptor.getInterceptors()).isEmpty();
                });
    }

    @Test
    void createsInterceptorBeforeMybatisPlusInnerInterceptorAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        CocoI18nAutoConfiguration.class,
                        CocoMybatisPlusAutoConfiguration.class,
                        MybatisPlusInnerInterceptorAutoConfiguration.class))
                .withUserConfiguration(InnerInterceptorConfiguration.class)
                .withPropertyValues(
                        "coco.common.i18n.basename=coco-messages",
                        "coco.mybatis-plus.pagination.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
                    MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

                    assertThat(interceptor.getInterceptors()).hasSize(1);
                    assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(OptimisticLockerInnerInterceptor.class);
                });
    }

    @Test
    void usesTypeSafeMybatisPlusAutoConfigurationOrdering() {
        AutoConfiguration annotation = CocoMybatisPlusAutoConfiguration.class.getAnnotation(AutoConfiguration.class);

        assertThat(annotation.after())
                .containsExactly(com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration.class);
        assertThat(annotation.before())
                .containsExactly(MybatisPlusInnerInterceptorAutoConfiguration.class);
        assertThat(annotation.afterName()).isEmpty();
        assertThat(annotation.beforeName()).isEmpty();
    }

    @Test
    void keepsThirdPartyAutoConfigurationsWhenFeatureIsEnabled() {
        CocoFeatureAutoConfigurationImportFilter filter = autoConfigurationImportFilter(new MockEnvironment());

        boolean[] matches = filter.match(new String[] {
                "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration",
                null
        }, null);

        assertThat(matches).containsExactly(true, true, true, true);
    }

    @Test
    void filtersThirdPartyAutoConfigurationsWhenFeatureIsDisabledWithoutDataSource() {
        CocoFeatureAutoConfigurationImportFilter filter = autoConfigurationImportFilter(new MockEnvironment()
                .withProperty("coco.features.disabled[0]", "mybatis-plus"));

        boolean[] matches = filter.match(new String[] {
                "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
                "org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration",
                null
        }, null);

        assertThat(matches).containsExactly(false, false, true, true);
    }

    @Test
    void keepsDataSourceAutoConfigurationWhenFeatureIsDisabledWithDataSourceConfiguration() {
        CocoFeatureAutoConfigurationImportFilter filter = autoConfigurationImportFilter(new MockEnvironment()
                .withProperty("coco.features.disabled[0]", "mybatis-plus")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/coco"));

        boolean[] matches = filter.match(new String[] {
                "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
        }, null);

        assertThat(matches).containsExactly(false, true);
    }

    @Test
    void failsWhenPaginationDbTypeIsInvalid() {
        this.contextRunner
                .withPropertyValues("coco.mybatis-plus.pagination.db-type=unknown-database")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable cocoFailure = firstCause(context.getStartupFailure(), CocoRequestException.class);
                    assertThat(cocoFailure)
                            .isInstanceOf(CocoRequestException.class)
                            .hasMessage("coco.feature.mybatis-plus.error.invalid-db-type");
                });
    }

    private static Throwable firstCause(Throwable failure, Class<? extends Throwable> failureType) {
        Throwable currentFailure = failure;
        while (currentFailure != null) {
            if (failureType.isInstance(currentFailure)) {
                return currentFailure;
            }
            currentFailure = currentFailure.getCause();
        }
        return failure;
    }

    private static CocoFeatureAutoConfigurationImportFilter autoConfigurationImportFilter(
            MockEnvironment environment) {
        CocoFeatureAutoConfigurationImportFilter filter = new CocoFeatureAutoConfigurationImportFilter();
        filter.setEnvironment(environment);
        filter.setBeanClassLoader(CocoMybatisPlusAutoConfigurationTest.class.getClassLoader());
        return filter;
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomizerConfiguration {

        @Bean
        CocoMybatisPlusInterceptorCustomizer optimisticLockerCustomizer() {
            return interceptor -> interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InnerInterceptorConfiguration {

        @Bean
        InnerInterceptor optimisticLockerInnerInterceptor() {
            return new OptimisticLockerInnerInterceptor();
        }
    }
}
