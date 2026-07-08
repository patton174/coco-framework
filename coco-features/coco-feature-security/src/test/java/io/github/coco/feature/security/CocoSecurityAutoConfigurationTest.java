package io.github.coco.feature.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.exception.type.CocoUnauthorizedException;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityContextResolver;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Coco 安全功能自动配置测试。
 * <p>
 * 验证安全功能模块可以通过 Coco 国际化基础设施注册自己的消息资源。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-security}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoSecurityAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoSecurityAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @Test
    void registersSecurityMessageBundle() {
        this.contextRunner.run(context -> {
            CocoMessageService messageService = context.getBean(CocoMessageService.class);

            assertTrue(context.containsBean("cocoSecurityMessageBundleRegistrar"));
            assertEquals("Coco 安全功能消息资源已就绪。", messageService.getMessage("coco.feature.security.ready"));
            assertEquals("当前请求缺少安全上下文。",
                    messageService.getMessage("coco.feature.security.error.context-missing"));
        });
    }

    @Test
    void registersSecurityContextResolver() {
        this.contextRunner.run(context -> {
            CocoSecurityContextResolver resolver = context.getBean(CocoSecurityContextResolver.class);
            CocoSecurityContext securityContext = CocoSecurityContext.authenticated(
                    CocoSecurityPrincipal.of("1001", "Patton"));

            CocoSecurityContextHolder.runWithContext(securityContext,
                    () -> assertEquals(securityContext, resolver.resolve().orElseThrow()));
        });
    }

    @Test
    void missingContextUsesSecurityErrorCode() {
        CocoSecurityContextHolder.clear();

        try {
            CocoSecurityContextHolder.requireCurrent();
        }
        catch (CocoUnauthorizedException exception) {
            assertEquals("coco.feature.security.error.context-missing", exception.message().code());
            return;
        }

        throw new AssertionError("Expected CocoUnauthorizedException");
    }
}
