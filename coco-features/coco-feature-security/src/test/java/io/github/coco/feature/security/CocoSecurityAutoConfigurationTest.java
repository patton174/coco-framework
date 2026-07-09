package io.github.coco.feature.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.common.exception.type.CocoForbiddenException;
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
            assertEquals("当前请求尚未完成认证。",
                    messageService.getMessage("coco.feature.security.error.unauthenticated"));
            assertEquals("当前主体无权访问该资源。",
                    messageService.getMessage("coco.feature.security.error.access-denied"));
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

        CocoUnauthorizedException exception = assertThrows(CocoUnauthorizedException.class,
                CocoSecurityContextHolder::requireCurrent);

        assertEquals("coco.feature.security.error.context-missing", exception.message().code());
    }

    @Test
    void securityFacadeRequiresAuthenticatedContext() {
        CocoSecurityContext anonymous = CocoSecurityContext.anonymous();

        CocoSecurityContextHolder.runWithContext(anonymous, () -> {
            assertFalse(CocoSecurity.isAuthenticated());

            CocoUnauthorizedException exception = assertThrows(CocoUnauthorizedException.class,
                    CocoSecurity::requireAuthenticated);

            assertEquals("coco.feature.security.error.unauthenticated", exception.message().code());
        });
    }

    @Test
    void securityFacadeChecksRoleAndPermission() {
        CocoSecurityPrincipal principal = new CocoSecurityPrincipal("1001", "Patton",
                Set.of("admin"), Set.of("order:read"), Map.of());
        CocoSecurityContext securityContext = CocoSecurityContext.authenticated(principal);

        CocoSecurityContextHolder.runWithContext(securityContext, () -> {
            assertTrue(CocoSecurity.isAuthenticated());
            assertEquals(securityContext, CocoSecurity.requireCurrent());
            assertEquals(securityContext, CocoSecurity.requireAuthenticated());
            assertEquals(principal, CocoSecurity.principal().orElseThrow());
            assertEquals(principal, CocoSecurity.requirePrincipal());
            assertTrue(CocoSecurity.hasRole("admin"));
            assertTrue(CocoSecurity.hasPermission("order:read"));
            assertFalse(CocoSecurity.hasRole("operator"));
            assertFalse(CocoSecurity.hasPermission("order:write"));
            assertEquals(principal, CocoSecurity.requireRole("admin"));
            assertEquals(principal, CocoSecurity.requirePermission("order:read"));

            CocoForbiddenException roleException = assertThrows(CocoForbiddenException.class,
                    () -> CocoSecurity.requireRole("operator"));
            CocoForbiddenException permissionException = assertThrows(CocoForbiddenException.class,
                    () -> CocoSecurity.requirePermission("order:write"));

            assertEquals("coco.feature.security.error.access-denied", roleException.message().code());
            assertEquals("coco.feature.security.error.access-denied", permissionException.message().code());
        });
    }
}
