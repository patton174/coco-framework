package io.github.coco.feature.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.exception.type.CocoRequestException;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.feature.tenant.context.CocoTenantContext;
import io.github.coco.feature.tenant.context.CocoTenantContextHolder;
import io.github.coco.feature.tenant.context.CocoTenantContextResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
 *   <li>模块：{@code coco-feature-tenant}</li>
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

    @Test
    void registersTenantMessageBundle() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertTrue(context.containsBean("cocoTenantMessageBundleRegistrar"));
            assertEquals("Coco 租户功能消息资源已就绪。", messageService.getMessage("coco.feature.tenant.ready"));
            assertEquals("当前请求缺少租户上下文。",
                    messageService.getMessage("coco.feature.tenant.error.context-missing"));
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
}
