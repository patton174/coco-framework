package io.github.coco.feature.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.exception.type.CocoForbiddenException;
import io.github.coco.exception.type.CocoUnauthorizedException;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityContextResolver;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoSecurityWebHeaderProperties;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

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
 *   <li>模块：{@code coco-security}</li>
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

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoCommonAutoConfiguration.class,
                    CocoSecurityAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages");

    @AfterEach
    void clearSecurityContext() {
        CocoSecurityContextHolder.clear();
    }

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
    void registersSecurityPropertiesAndWebFilterInServletApplication() {
        this.webContextRunner.run(context -> {
            assertTrue(context.containsBean("cocoWebSecurityContextResolver"));
            assertTrue(context.containsBean("cocoSecurityWebFilterRegistration"));

            CocoSecurityProperties properties = context.getBean(CocoSecurityProperties.class);
            assertTrue(properties.getWeb().isEnabled());
            assertFalse(properties.getWeb().getHeader().isEnabled());
            FilterRegistrationBean<?> registration = context.getBean("cocoSecurityWebFilterRegistration",
                    FilterRegistrationBean.class);
            assertTrue(registration.getFilter() instanceof CocoSecurityWebFilter);
            assertEquals(Ordered.HIGHEST_PRECEDENCE + 6, registration.getOrder());
            assertEquals(EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR),
                    registration.determineDispatcherTypes());
        });
    }

    @Test
    void disablesSecurityWebFilterByProperty() {
        this.webContextRunner
                .withPropertyValues("coco.security.web.enabled=false")
                .run(context -> assertFalse(context.containsBean("cocoSecurityWebFilterRegistration")));
    }

    @Test
    void webFilterClearsMissingContextDuringRequestAndRestoresPreviousContext() throws Exception {
        this.webContextRunner.run(context -> {
            CocoSecurityWebFilter filter = securityWebFilter(context);
            CocoSecurityContext previous = CocoSecurityContext.authenticated(
                    CocoSecurityPrincipal.of("previous", "Previous"));
            AtomicReference<Optional<CocoSecurityContext>> currentInChain = new AtomicReference<>();
            CocoSecurityContextHolder.set(previous);

            filter.doFilter(new MockHttpServletRequest("GET", "/api/users"), new MockHttpServletResponse(),
                    (request, response) -> currentInChain.set(CocoSecurityContextHolder.current()));

            assertTrue(currentInChain.get().isEmpty());
            assertEquals(previous, CocoSecurityContextHolder.current().orElseThrow());
        });
    }

    @Test
    void webFilterBindsTrustedHeaderSecurityContextAndRestoresPreviousContext() throws Exception {
        this.webContextRunner
                .withPropertyValues(
                        "coco.security.web.header.enabled=true",
                        "coco.security.web.header.principal-id-header-name=X-Trusted-Principal-Id",
                        "coco.security.web.header.principal-name-header-name=X-Trusted-Principal-Name",
                        "coco.security.web.header.roles-header-name=X-Trusted-Roles",
                        "coco.security.web.header.permissions-header-name=X-Trusted-Permissions",
                        "coco.security.web.header.authority-delimiter=|")
                .run(context -> {
                    CocoSecurityWebFilter filter = securityWebFilter(context);
                    CocoSecurityWebHeaderProperties header = context.getBean(CocoSecurityProperties.class)
                            .getWeb().getHeader();
                    assertTrue(header.isEnabled());
                    assertEquals("X-Trusted-Principal-Id", header.getPrincipalIdHeaderName());
                    assertEquals("|", header.getAuthorityDelimiter());
                    CocoSecurityContext previous = CocoSecurityContext.authenticated(
                            CocoSecurityPrincipal.of("previous", "Previous"));
                    AtomicReference<CocoSecurityContext> currentInChain = new AtomicReference<>();
                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
                    request.addHeader("X-Trusted-Principal-Id", "1001");
                    request.addHeader("X-Trusted-Principal-Name", "Patton");
                    request.addHeader("X-Trusted-Roles", "admin|operator");
                    request.addHeader("X-Trusted-Permissions", "order:read|order:write");
                    CocoSecurityContextHolder.set(previous);

                    filter.doFilter(request, new MockHttpServletResponse(),
                            (servletRequest, response) -> currentInChain.set(CocoSecurity.requireCurrent()));

                    CocoSecurityContext securityContext = currentInChain.get();
                    assertEquals("1001", securityContext.principal().principalId());
                    assertEquals("Patton", securityContext.principal().principalName());
                    assertTrue(securityContext.principal().hasRole("admin"));
                    assertTrue(securityContext.principal().hasRole("operator"));
                    assertTrue(securityContext.principal().hasPermission("order:read"));
                    assertTrue(securityContext.principal().hasPermission("order:write"));
                    assertEquals(previous, CocoSecurityContextHolder.current().orElseThrow());
                });
    }

    @Test
    void customWebSecurityContextResolverOverridesDefaultHeaderAdapter() throws Exception {
        CocoSecurityContext customContext = CocoSecurityContext.authenticated(
                CocoSecurityPrincipal.of("custom", "Custom User"));
        this.webContextRunner
                .withBean(CocoWebSecurityContextResolver.class, () -> request -> Optional.of(customContext))
                .run(context -> {
                    CocoSecurityWebFilter filter = securityWebFilter(context);
                    AtomicReference<CocoSecurityContext> currentInChain = new AtomicReference<>();

                    filter.doFilter(new MockHttpServletRequest("GET", "/api/users"), new MockHttpServletResponse(),
                            (request, response) -> currentInChain.set(CocoSecurity.requireCurrent()));

                    assertEquals(customContext, currentInChain.get());
                    assertTrue(CocoSecurityContextHolder.current().isEmpty());
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

    private static CocoSecurityWebFilter securityWebFilter(org.springframework.context.ApplicationContext context) {
        FilterRegistrationBean<?> registration = context.getBean("cocoSecurityWebFilterRegistration",
                FilterRegistrationBean.class);
        return (CocoSecurityWebFilter) registration.getFilter();
    }
}
