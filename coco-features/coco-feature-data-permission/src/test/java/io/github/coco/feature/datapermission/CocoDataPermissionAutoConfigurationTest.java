package io.github.coco.feature.datapermission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.Set;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.exception.type.CocoForbiddenException;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContext;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextHolder;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextResolver;
import io.github.coco.feature.datapermission.context.CocoDataPermissionRule;
import io.github.coco.feature.datapermission.context.CocoDataScope;
import io.github.coco.feature.datapermission.mybatisplus.CocoDataPermissionMybatisPlusAutoConfiguration;
import io.github.coco.feature.datapermission.mybatisplus.CocoMybatisPlusDataPermissionHandler;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionMissingContextPolicy;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionMissingRulePolicy;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlProperties;
import io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlResourceProperties;
import io.github.coco.feature.datapermission.sql.DefaultCocoDataPermissionSqlPredicateProvider;
import io.github.coco.feature.datapermission.sql.PropertyCocoDataPermissionSqlResourceResolver;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Coco 数据权限功能自动配置测试。
 * <p>
 * 验证数据权限功能模块注册消息资源、上下文解析器，并在显式开启时接入 MyBatis-Plus 数据权限拦截器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoDataPermissionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoDataPermissionAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    private final ApplicationContextRunner mybatisPlusContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoMybatisPlusAutoConfiguration.class,
                    CocoDataPermissionAutoConfiguration.class,
                    CocoDataPermissionMybatisPlusAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @AfterEach
    void clearDataPermissionContext() {
        CocoDataPermissionContextHolder.clear();
    }

    @Test
    void registersDataPermissionMessageBundle() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertTrue(context.containsBean("cocoDataPermissionMessageBundleRegistrar"));
            assertEquals("Coco 数据权限功能消息资源已就绪。",
                    messageService.getMessage("coco.feature.data-permission.ready"));
            assertEquals("当前请求缺少数据权限上下文。",
                    messageService.getMessage("coco.feature.data-permission.error.context-missing"));
        });
    }

    @Test
    void registersDataPermissionContextResolver() {
        this.contextRunner.run(context -> {
            CocoDataPermissionContextResolver resolver = context.getBean(CocoDataPermissionContextResolver.class);
            CocoDataPermissionContext dataPermissionContext = CocoDataPermissionContext.of(
                    Set.of(CocoDataPermissionRule.all("sample-order")));

            CocoDataPermissionContextHolder.runWithContext(dataPermissionContext,
                    () -> assertEquals(dataPermissionContext, resolver.resolve().orElseThrow()));
        });
    }

    @Test
    void missingContextUsesDataPermissionErrorCode() {
        CocoDataPermissionContextHolder.clear();

        CocoForbiddenException exception = assertThrows(CocoForbiddenException.class,
                CocoDataPermissionContextHolder::requireCurrent);

        assertEquals("coco.feature.data-permission.error.context-missing", exception.message().code());
    }

    @Test
    void keepsSqlInterceptorDisabledByDefault() {
        this.mybatisPlusContextRunner.run(context -> {
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

            assertThat(context).doesNotHaveBean("cocoDataPermissionMybatisPlusInterceptorCustomizer");
            assertThat(interceptor.getInterceptors()).noneMatch(DataPermissionInterceptor.class::isInstance);
        });
    }

    @Test
    void registersDataPermissionInterceptorBeforePaginationWhenSqlEnabled() {
        this.mybatisPlusContextRunner
                .withPropertyValues("coco.data-permission.sql.enabled=true")
                .run(context -> {
                    MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);

                    assertThat(context).hasBean("cocoDataPermissionMybatisPlusInterceptorCustomizer");
                    assertThat(interceptor.getInterceptors()).hasSize(2);
                    assertThat(interceptor.getInterceptors().get(0)).isInstanceOf(DataPermissionInterceptor.class);
                    assertThat(interceptor.getInterceptors().get(1)).isInstanceOf(PaginationInnerInterceptor.class);
                });
    }

    @Test
    void bindsSqlProperties() {
        this.mybatisPlusContextRunner
                .withPropertyValues(
                        "coco.data-permission.sql.enabled=true",
                        "coco.data-permission.sql.missing-context-policy=deny",
                        "coco.data-permission.sql.missing-rule-policy=ignore",
                        "coco.data-permission.sql.resources.sample-order.tables[0]=sample_order",
                        "coco.data-permission.sql.resources.sample-order.column=dept_id")
                .run(context -> {
                    CocoDataPermissionProperties properties = context.getBean(CocoDataPermissionProperties.class);

                    assertThat(properties.getSql().isEnabled()).isTrue();
                    assertThat(properties.getSql().getMissingContextPolicy())
                            .isEqualTo(CocoDataPermissionMissingContextPolicy.DENY);
                    assertThat(properties.getSql().getMissingRulePolicy())
                            .isEqualTo(CocoDataPermissionMissingRulePolicy.IGNORE);
                    assertThat(properties.getSql().getResources()).containsKey("sample-order");
                    assertThat(properties.getSql().resource("sample-order").getTables()).containsExactly("sample_order");
                    assertThat(properties.getSql().resource("sample-order").getColumn()).isEqualTo("dept_id");
                });
    }

    @Test
    void handlerCreatesPredicateForMatchedResourceRule() {
        CocoDataPermissionSqlProperties properties = sqlProperties();
        CocoDataPermissionContext context = CocoDataPermissionContext.of(Set.of(
                new CocoDataPermissionRule("sample-order", CocoDataScope.CUSTOM, Set.of("D1"))));
        CocoMybatisPlusDataPermissionHandler handler = handler(properties, () -> Optional.of(context));
        Table table = new Table("sample_order");
        table.setAlias(new Alias("o"));

        Expression expression = handler.getSqlSegment(table, null, "SampleMapper.selectOrders");

        assertThat(expression.toString()).isEqualTo("o.dept_id IN ('D1')");
    }

    @Test
    void handlerSkipsPredicateForAllDataRule() {
        CocoDataPermissionSqlProperties properties = sqlProperties();
        CocoDataPermissionContext context = CocoDataPermissionContext.of(
                Set.of(CocoDataPermissionRule.all("sample-order")));
        CocoMybatisPlusDataPermissionHandler handler = handler(properties, () -> Optional.of(context));

        Expression expression = handler.getSqlSegment(new Table("sample_order"), null,
                "SampleMapper.selectOrders");

        assertThat(expression).isNull();
    }

    @Test
    void handlerDeniesWhenResourceRuleIsMissingByDefault() {
        CocoDataPermissionSqlProperties properties = sqlProperties();
        CocoMybatisPlusDataPermissionHandler handler = handler(properties,
                () -> Optional.of(CocoDataPermissionContext.empty()));

        Expression expression = handler.getSqlSegment(new Table("sample_order"), null,
                "SampleMapper.selectOrders");

        assertThat(expression.toString()).isEqualTo("1 = 0");
    }

    @Test
    void handlerThrowsWhenContextIsMissingByDefault() {
        CocoMybatisPlusDataPermissionHandler handler = handler(sqlProperties(), Optional::empty);

        CocoForbiddenException exception = assertThrows(CocoForbiddenException.class,
                () -> handler.getSqlSegment(new Table("sample_order"), null, "SampleMapper.selectOrders"));

        assertThat(exception.message().code()).isEqualTo("coco.feature.data-permission.error.context-missing");
    }

    @Test
    void handlerCanIgnoreMissingContextByPolicy() {
        CocoDataPermissionSqlProperties properties = sqlProperties();
        properties.setMissingContextPolicy(CocoDataPermissionMissingContextPolicy.IGNORE);
        CocoMybatisPlusDataPermissionHandler handler = handler(properties, Optional::empty);

        Expression expression = handler.getSqlSegment(new Table("sample_order"), null,
                "SampleMapper.selectOrders");

        assertThat(expression).isNull();
    }

    private static CocoMybatisPlusDataPermissionHandler handler(CocoDataPermissionSqlProperties properties,
            CocoDataPermissionContextResolver contextResolver) {
        return new CocoMybatisPlusDataPermissionHandler(properties, contextResolver,
                new PropertyCocoDataPermissionSqlResourceResolver(properties),
                new DefaultCocoDataPermissionSqlPredicateProvider());
    }

    private static CocoDataPermissionSqlProperties sqlProperties() {
        CocoDataPermissionSqlResourceProperties resource = new CocoDataPermissionSqlResourceProperties();
        resource.setTables(java.util.List.of("sample_order"));
        resource.setColumn("dept_id");
        CocoDataPermissionSqlProperties properties = new CocoDataPermissionSqlProperties();
        properties.getResources().put("sample-order", resource);
        return properties;
    }
}
