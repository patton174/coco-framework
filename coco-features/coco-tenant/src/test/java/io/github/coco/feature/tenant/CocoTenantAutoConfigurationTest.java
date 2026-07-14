package io.github.coco.feature.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.exception.type.CocoForbiddenException;
import io.github.coco.exception.type.CocoRequestException;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration;
import io.github.coco.feature.tenant.context.CocoTenantContext;
import io.github.coco.feature.tenant.context.CocoTenantContextHolder;
import io.github.coco.feature.tenant.context.CocoTenantContextResolver;
import io.github.coco.feature.tenant.sql.CocoTenantIdExpressionResolver;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreDecision;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreEvent;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreEventPublisher;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreGuard;
import io.github.coco.feature.tenant.sql.CocoTenantInterceptorIgnoreLegacyConfigurationEvent;
import io.github.coco.feature.tenant.sql.CocoTenantMybatisPlusAutoConfiguration;
import io.github.coco.feature.tenant.sql.CocoTenantSqlProperties;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco 租户功能自动配置测试。
 * <p>
 * 验证租户功能模块可以通过 Coco 国际化基础设施注册自己的消息资源。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoTenantAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoTenantAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    private final ApplicationContextRunner mybatisContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoTenantAutoConfiguration.class,
                    CocoTenantMybatisPlusAutoConfiguration.class,
                    CocoMybatisPlusAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @AfterEach
    void clearTenantState() {
        CocoTenantContextHolder.clear();
        InterceptorIgnoreHelper.clearIgnoreStrategy();
    }

    @Test
    void registersTenantMessageBundle() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertTrue(context.containsBean("cocoTenantMessageBundleRegistrar"));
            assertEquals("Coco 租户功能消息资源已就绪。", messageService.getMessage("coco.feature.tenant.ready"));
            assertEquals("当前请求缺少租户上下文。",
                    messageService.getMessage("coco.feature.tenant.error.context-missing"));
            assertEquals("检测到已废弃的租户旁路模式白名单，请迁移到 exact-mapped-statements：com.example.Mapper.*。",
                    messageService.getMessage("coco.feature.tenant.warn.interceptor-ignore-legacy-patterns",
                            "com.example.Mapper.*"));
            assertEquals("租户旁路严格治理不允许使用已废弃的 allowed-mapped-statements：com.example.Mapper.*。",
                    messageService.getMessage(
                            "coco.feature.tenant.error.interceptor-ignore-legacy-patterns-strict",
                            "com.example.Mapper.*"));
        });
    }

    @Test
    void registersTenantContextResolver() {
        this.contextRunner.run(context -> {
            CocoTenantContextResolver resolver = context.getBean(CocoTenantContextResolver.class);
            CocoTenantContext tenantContext = CocoTenantContext.of("tenant-1", "默认租户");

            CocoTenantContextHolder.runWithContext(tenantContext,
                    () -> assertEquals(tenantContext, resolver.resolve().orElseThrow()));
        });
    }

    @Test
    void missingContextUsesTenantErrorCode() {
        CocoTenantContextHolder.clear();

        try {
            CocoTenantContextHolder.requireCurrent();
        }
        catch (CocoRequestException exception) {
            assertEquals("coco.feature.tenant.error.context-missing", exception.message().code());
            return;
        }

        throw new AssertionError("Expected CocoRequestException");
    }

    @Test
    void registersTenantLineInterceptorBeforePagination() {
        this.mybatisContextRunner.run(context -> {
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

            assertThat(context).hasSingleBean(CocoTenantIdExpressionResolver.class);
            assertThat(context).hasSingleBean(CocoTenantInterceptorIgnoreEventPublisher.class);
            assertThat(context).hasBean("cocoTenantMybatisPlusInterceptorCustomizer");
            assertThat(interceptor.getInterceptors()).hasSize(3);
            assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(CocoTenantInterceptorIgnoreGuard.class);
            assertThat(interceptor.getInterceptors().get(1)).isInstanceOf(TenantLineInnerInterceptor.class);
            assertThat(interceptor.getInterceptors().get(2)).isInstanceOf(PaginationInnerInterceptor.class);
        });
    }

    @Test
    void usesTypeSafeMybatisPlusAutoConfigurationOrdering() {
        AutoConfiguration annotation =
                CocoTenantMybatisPlusAutoConfiguration.class.getAnnotation(AutoConfiguration.class);

        assertThat(annotation.after())
                .containsExactly(CocoTenantAutoConfiguration.class, CocoMybatisPlusAutoConfiguration.class);
        assertThat(annotation.afterName()).isEmpty();
    }

    @Test
    void bindsTenantSqlProperties() {
        this.mybatisContextRunner
                .withPropertyValues(
                        "coco.tenant.sql.tenant-id-column=org_id",
                        "coco.tenant.sql.ignore-tables[0]=sys_tenant",
                        "coco.tenant.sql.ignore-tables[1]=coco_dictionary",
                        "coco.tenant.sql.fail-on-missing-context=false",
                        "coco.tenant.sql.interceptor-ignore.block-unlisted=false",
                        "coco.tenant.sql.interceptor-ignore.strict-mode=true",
                        "coco.tenant.sql.interceptor-ignore.exact-mapped-statements[0]=com.example.AdminMapper.selectShared")
                .run(context -> {
                    CocoTenantProperties properties = context.getBean(CocoTenantProperties.class);
                    TenantLineHandler handler = tenantLineHandler(context);

                    assertThat(properties.getSql().getTenantIdColumn()).isEqualTo("org_id");
                    assertThat(properties.getSql().getIgnoreTables()).containsExactly("sys_tenant",
                            "coco_dictionary");
                    assertThat(properties.getSql().isFailOnMissingContext()).isFalse();
                    assertThat(properties.getSql().getInterceptorIgnore().isBlockUnlisted()).isFalse();
                    assertThat(properties.getSql().getInterceptorIgnore().isStrictMode()).isTrue();
                    assertThat(properties.getSql().getInterceptorIgnore().getExactMappedStatements())
                            .containsExactly("com.example.AdminMapper.selectShared");
                    assertThat(handler.getTenantIdColumn()).isEqualTo("org_id");
                    assertThat(handler.ignoreTable("SYS_TENANT")).isTrue();
                    assertThat(handler.ignoreTable("business_order")).isFalse();
                    assertThat(handler.getTenantId()).isInstanceOf(NullValue.class);
                });
    }

    @Test
    void disablesTenantSqlCustomizer() {
        this.mybatisContextRunner
                .withPropertyValues("coco.tenant.sql.enabled=false")
                .run(context -> {
                    MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

                    assertThat(context).doesNotHaveBean(CocoTenantIdExpressionResolver.class);
                    assertThat(context).doesNotHaveBean("cocoTenantMybatisPlusInterceptorCustomizer");
                    assertThat(interceptor.getInterceptors()).hasSize(1);
                    assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(PaginationInnerInterceptor.class);
                });
    }

    @Test
    void blocksUnlistedTenantInterceptorIgnore() {
        CocoTenantSqlProperties properties = new CocoTenantSqlProperties();
        List<CocoTenantInterceptorIgnoreEvent> events = new ArrayList<>();
        CocoTenantInterceptorIgnoreGuard guard = new CocoTenantInterceptorIgnoreGuard(properties, events::add);
        MappedStatement mappedStatement = mappedStatement("com.example.OrderMapper.selectAll", SqlCommandType.SELECT);

        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());

        assertThatThrownBy(() -> guard.willDoQuery(null, mappedStatement, null, RowBounds.DEFAULT, null, null))
                .isInstanceOf(CocoForbiddenException.class)
                .hasMessage("coco.feature.tenant.error.interceptor-ignore-blocked");
        assertThat(events).singleElement()
                .extracting(CocoTenantInterceptorIgnoreEvent::decision)
                .isEqualTo(CocoTenantInterceptorIgnoreDecision.BLOCKED);
    }

    @Test
    void allowsExactAllowlistedTenantInterceptorIgnore() throws Exception {
        CocoTenantSqlProperties properties = new CocoTenantSqlProperties();
        properties.getInterceptorIgnore().getExactMappedStatements().add("com.example.AdminMapper.selectShared");
        List<CocoTenantInterceptorIgnoreEvent> events = new ArrayList<>();
        CocoTenantInterceptorIgnoreGuard guard = new CocoTenantInterceptorIgnoreGuard(properties, events::add);
        MappedStatement mappedStatement = mappedStatement("com.example.AdminMapper.selectShared",
                SqlCommandType.SELECT);

        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());

        assertThat(guard.willDoQuery(null, mappedStatement, null, RowBounds.DEFAULT, null, null)).isTrue();
        assertThat(events).singleElement()
                .extracting(CocoTenantInterceptorIgnoreEvent::decision)
                .isEqualTo(CocoTenantInterceptorIgnoreDecision.ALLOWED);
    }

    @Test
    @SuppressWarnings("deprecation")
    void preservesLegacyPatternMatching() throws Exception {
        CocoTenantSqlProperties properties = new CocoTenantSqlProperties();
        properties.getInterceptorIgnore().getAllowedMappedStatements().add("com.example.AdminMapper.*");
        CocoTenantInterceptorIgnoreGuard guard = new CocoTenantInterceptorIgnoreGuard(properties, event -> {
        });
        MappedStatement mappedStatement = mappedStatement("com.example.AdminMapper.selectShared",
                SqlCommandType.SELECT);

        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());

        assertThat(guard.willDoQuery(null, mappedStatement, null, RowBounds.DEFAULT, null, null)).isTrue();
    }

    @Test
    void allowsUnlistedTenantInterceptorIgnoreWhenBlockingIsDisabled() throws Exception {
        CocoTenantSqlProperties properties = new CocoTenantSqlProperties();
        properties.getInterceptorIgnore().setBlockUnlisted(false);
        List<CocoTenantInterceptorIgnoreEvent> events = new ArrayList<>();
        CocoTenantInterceptorIgnoreGuard guard = new CocoTenantInterceptorIgnoreGuard(properties, events::add);
        MappedStatement mappedStatement = mappedStatement("com.example.ReportMapper.selectAll",
                SqlCommandType.SELECT);

        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());

        assertThat(guard.willDoQuery(null, mappedStatement, null, RowBounds.DEFAULT, null, null)).isTrue();
        assertThat(events).singleElement()
                .extracting(CocoTenantInterceptorIgnoreEvent::decision)
                .isEqualTo(CocoTenantInterceptorIgnoreDecision.ALLOWED);
    }

    @Test
    void strictModeBlocksUnlistedBypassWhenCompatibilityBlockingIsDisabled() {
        CocoTenantSqlProperties properties = new CocoTenantSqlProperties();
        properties.getInterceptorIgnore().setBlockUnlisted(false);
        properties.getInterceptorIgnore().setStrictMode(true);
        CocoTenantInterceptorIgnoreGuard guard = new CocoTenantInterceptorIgnoreGuard(properties, event -> {
        });
        MappedStatement mappedStatement = mappedStatement("com.example.ReportMapper.selectAll",
                SqlCommandType.SELECT);

        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());

        assertThatThrownBy(() -> guard.willDoQuery(null, mappedStatement, null, RowBounds.DEFAULT, null, null))
                .isInstanceOf(CocoForbiddenException.class);
    }

    @Test
    void doesNotAllowPrefixOrWildcardMappedStatementIds() {
        CocoTenantSqlProperties properties = new CocoTenantSqlProperties();
        properties.getInterceptorIgnore().getExactMappedStatements().add("com.example.AdminMapper.*");
        CocoTenantInterceptorIgnoreGuard guard = new CocoTenantInterceptorIgnoreGuard(properties, event -> {
        });
        MappedStatement mappedStatement = mappedStatement("com.example.AdminMapper.selectShared",
                SqlCommandType.SELECT);

        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());

        assertThatThrownBy(() -> guard.willDoQuery(null, mappedStatement, null, RowBounds.DEFAULT, null, null))
                .isInstanceOf(CocoForbiddenException.class);
    }

    @Test
    void publishesStartupEventForDeprecatedLegacyPatterns() {
        List<CocoTenantInterceptorIgnoreLegacyConfigurationEvent> events = new CopyOnWriteArrayList<>();

        this.mybatisContextRunner
                .withInitializer(context -> context.addApplicationListener(event -> {
                    if (event instanceof PayloadApplicationEvent<?> payloadEvent
                            && payloadEvent.getPayload()
                            instanceof CocoTenantInterceptorIgnoreLegacyConfigurationEvent legacyEvent) {
                        events.add(legacyEvent);
                    }
                }))
                .withPropertyValues(
                        "coco.tenant.sql.interceptor-ignore.allowed-mapped-statements[0]=com.example.AdminMapper.*")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(events).singleElement().satisfies(event -> {
                        assertThat(event.patterns()).containsExactly("com.example.AdminMapper.*");
                        assertThat(event.strictMode()).isFalse();
                    });
                });
    }

    @Test
    void strictModeRejectsDeprecatedLegacyPatternsAtStartup() {
        this.mybatisContextRunner
                .withPropertyValues(
                        "coco.tenant.sql.interceptor-ignore.strict-mode=true",
                        "coco.tenant.sql.interceptor-ignore.allowed-mapped-statements[0]=com.example.AdminMapper.*")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("租户旁路严格治理不允许使用已废弃的 "
                                    + "allowed-mapped-statements：com.example.AdminMapper.*。");
                });
    }

    @Test
    void resolvesTenantIdFromCurrentContext() {
        this.mybatisContextRunner.run(context -> {
            TenantLineHandler handler = tenantLineHandler(context);
            CocoTenantContext tenantContext = CocoTenantContext.of("tenant-1001", "租户 1001");

            CocoTenantContextHolder.runWithContext(tenantContext, () -> {
                StringValue tenantId = (StringValue) handler.getTenantId();

                assertThat(tenantId.getValue()).isEqualTo("tenant-1001");
            });
        });
    }

    @Test
    void failsWhenTenantContextIsMissing() {
        CocoTenantContextHolder.clear();

        this.mybatisContextRunner.run(context -> {
            TenantLineHandler handler = tenantLineHandler(context);

            assertThatThrownBy(handler::getTenantId)
                    .isInstanceOf(CocoRequestException.class)
                    .hasMessage("coco.feature.tenant.error.context-missing");
        });
    }

    @Test
    void customTenantIdExpressionResolverBacksOffDefaultResolver() {
        this.mybatisContextRunner
                .withUserConfiguration(CustomTenantExpressionConfiguration.class)
                .run(context -> {
                    TenantLineHandler handler = tenantLineHandler(context);
                    CocoTenantContext tenantContext = CocoTenantContext.of("ignored", "ignored");

                    CocoTenantContextHolder.runWithContext(tenantContext, () -> {
                        LongValue tenantId = (LongValue) handler.getTenantId();

                        assertThat(tenantId.getValue()).isEqualTo(1001L);
                    });
                });
    }

    private static TenantLineHandler tenantLineHandler(org.springframework.context.ApplicationContext context) {
        MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
        return interceptor.getInterceptors().stream()
                .filter(TenantLineInnerInterceptor.class::isInstance)
                .map(TenantLineInnerInterceptor.class::cast)
                .findFirst()
                .map(TenantLineInnerInterceptor::getTenantLineHandler)
                .orElseThrow();
    }

    private static MappedStatement mappedStatement(String id, SqlCommandType commandType) {
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        return new MappedStatement.Builder(configuration, id,
                new StaticSqlSource(configuration, "select 1"), commandType).build();
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomTenantExpressionConfiguration {

        @Bean
        CocoTenantIdExpressionResolver cocoTenantIdExpressionResolver() {
            return tenantContext -> new LongValue(1001L);
        }
    }
}
