package io.github.coco.feature.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.coco.common.autoconfigure.CocoCommonAutoConfiguration;
import io.github.coco.exception.type.CocoForbiddenException;
import io.github.coco.exception.type.CocoUnauthorizedException;
import io.github.coco.feature.security.authorization.CocoAuthorizationMode;
import io.github.coco.feature.security.authorization.CocoAuthorizationRequirement;
import io.github.coco.feature.security.authorization.CocoAuthorize;
import io.github.coco.feature.security.authorization.CocoMethodAuthorizationManager;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityContextResolver;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco 方法授权 Spring 集成测试。
 */
class CocoMethodAuthorizationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CocoCommonAutoConfiguration.class,
                    CocoSecurityAutoConfiguration.class))
            .withPropertyValues("coco.common.i18n.basename=coco-messages")
            .withUserConfiguration(AuthorizedServiceConfiguration.class);

    @AfterEach
    void clearSecurityContext() {
        CocoSecurityContextHolder.clear();
    }

    @Test
    void defaultsToAuthenticatedAndRejectsMissingOrAnonymousBeforeBusinessInvocation() {
        this.contextRunner.run(context -> {
            AuthorizedService service = context.getBean(AuthorizedService.class);

            CocoUnauthorizedException missing = assertThrows(CocoUnauthorizedException.class, service::authenticated);
            assertEquals("coco.feature.security.error.context-missing", missing.message().code());
            assertEquals(0, target(service).calls.get());

            CocoSecurityContextHolder.runWithContext(CocoSecurityContext.anonymous(), () -> {
                CocoUnauthorizedException anonymous = assertThrows(CocoUnauthorizedException.class, service::authenticated);
                assertEquals("coco.feature.security.error.unauthenticated", anonymous.message().code());
            });
            assertEquals(0, target(service).calls.get());

            assertEquals("authenticated", runAs(principal(Set.of(), Set.of()), service::authenticated));
            assertEquals(1, target(service).calls.get());
        });
    }

    @Test
    void appliesAllAnyAndFixedAndRulesWithNormalizedRequirements() throws Throwable {
        this.contextRunner.run(context -> {
            AuthorizedService service = context.getBean(AuthorizedService.class);
            CocoSecurityPrincipal full = principal(Set.of("admin", "operator"), Set.of("order:read", "order:write"));
            CocoSecurityPrincipal partial = principal(Set.of("admin"), Set.of("order:read"));

            assertEquals("roles-all", runAs(full, service::rolesAll));
            assertForbidden(() -> runAs(partial, service::rolesAll));
            assertEquals("roles-any", runAs(partial, service::rolesAny));
            assertEquals("permissions-all", runAs(full, service::permissionsAll));
            assertForbidden(() -> runAs(partial, service::permissionsAll));
            assertEquals("permissions-any", runAs(partial, service::permissionsAny));
            assertEquals("both", runAs(full, service::both));
            assertForbidden(() -> runAs(principal(Set.of("admin"), Set.of()), service::both));

            CocoAuthorizationRequirement normalized = new CocoAuthorizationRequirement(
                    List.of(" admin ", "admin", " operator "), CocoAuthorizationMode.ALL,
                    List.of(" order:read ", "order:read"), CocoAuthorizationMode.ANY);
            assertEquals(List.of("admin", "operator"), normalized.roles());
            assertEquals(List.of("order:read"), normalized.permissions());
        });
    }

    @Test
    void validatesInvalidRequirementsBeforeBusinessMethodExecution() {
        this.contextRunner.run(context -> {
            assertThrows(IllegalArgumentException.class, () -> new CocoAuthorizationRequirement(
                    List.of(" "), CocoAuthorizationMode.ALL, List.of(), CocoAuthorizationMode.ALL));
            assertThrows(IllegalArgumentException.class, () -> new CocoAuthorizationRequirement(
                    List.of("a\u0000b"), CocoAuthorizationMode.ALL, List.of(), CocoAuthorizationMode.ALL));
            assertThrows(IllegalArgumentException.class, () -> new CocoAuthorizationRequirement(
                    Arrays.asList("admin", null), CocoAuthorizationMode.ALL, List.of(), CocoAuthorizationMode.ALL));
            assertThrows(NullPointerException.class, () -> new CocoAuthorizationRequirement(
                    List.of(), null, List.of(), CocoAuthorizationMode.ALL));
        });
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(CocoCommonAutoConfiguration.class,
                CocoSecurityAutoConfiguration.class)).withUserConfiguration(InvalidServiceConfiguration.class)
                .run(context -> assertTrue(context.getStartupFailure() != null
                        && context.getStartupFailure().getMessage().contains("must not contain blank values")));
    }

    @Test
    void methodDeclarationOverridesTypeAndSupportsInterfaceAndImplementationDeclarations() throws Throwable {
        this.contextRunner.run(context -> {
            AuthorizedService service = context.getBean(AuthorizedService.class);
            InterfaceAuthorizedService interfaceService = context.getBean(InterfaceAuthorizedService.class);
            assertEquals("method", runAs(principal(Set.of("method"), Set.of()), service::methodOverridesType));
            assertForbidden(() -> runAs(principal(Set.of("type"), Set.of()), service::methodOverridesType));
            assertEquals("type", runAs(principal(Set.of("type"), Set.of()), service::typeOnly));
            assertEquals("interface", runAs(principal(Set.of("interface"), Set.of()), interfaceService::interfaceMethod));
            assertEquals("implementation", runAs(principal(Set.of("implementation"), Set.of()),
                    interfaceService::implementationMethod));
            assertEquals("interface-type", runAs(principal(Set.of("interface-type"), Set.of()),
                    context.getBean(InterfaceTypeAuthorizedService.class)::fromInterfaceType));
            OpenService openService = context.getBean(OpenService.class);
            assertFalse(AopUtils.isAopProxy(openService));
            assertEquals("open", openService.open());
        });
    }

    @Test
    void preservesReturnCheckedExceptionAndError() {
        this.contextRunner.run(context -> {
            AuthorizedService service = context.getBean(AuthorizedService.class);
            CocoSecurityContextHolder.runWithContext(CocoSecurityContext.authenticated(principal(Set.of(), Set.of())), () -> {
                assertEquals("value", service.value());
                IOException exception = assertThrows(IOException.class, service::checked);
                assertEquals("checked", exception.getMessage());
                AssertionError error = assertThrows(AssertionError.class, service::error);
                assertEquals("error", error.getMessage());
            });
        });
    }

    @Test
    void permitsCustomManagerAndCombinesWithAnotherAdvisorWithoutDuplicateAuthorization() {
        AtomicInteger authorizations = new AtomicInteger();
        AtomicInteger companionCalls = new AtomicInteger();
        this.contextRunner.withBean(CocoMethodAuthorizationManager.class, () -> (requirement, resolver) -> {
            authorizations.incrementAndGet();
        }).withBean("companionAdvisor", DefaultPointcutAdvisor.class, () -> companionAdvisor(companionCalls)).run(context -> {
            AuthorizedService service = context.getBean(AuthorizedService.class);
            assertTrue(service instanceof Advised);
            assertEquals("authenticated", service.authenticated());
            assertEquals(1, authorizations.get());
            assertEquals(1, companionCalls.get());
        });
    }

    @Test
    void backsOffWhenDisabledOrFeatureIsDisabledAndDoesNotRequireSpringSecurity() {
        this.contextRunner.withPropertyValues("coco.security.method.enabled=false").run(context -> {
            assertFalse(context.containsBean("cocoMethodAuthorizationManager"));
            assertFalse(context.containsBean("cocoMethodAuthorizationAdvisor"));
            assertFalse(context.containsBean("cocoMethodAuthorizationAutoProxyCreator"));
            assertEquals("authenticated", context.getBean(AuthorizedService.class).authenticated());
        });
        this.contextRunner.withPropertyValues("coco.features.disabled[0]=security").run(context -> {
            assertFalse(context.getBeansOfType(CocoMethodAuthorizationManager.class).size() > 0);
            assertFalse(context.getBeansOfType(CocoSecurityContextResolver.class).size() > 0);
            assertFalse(context.containsBean("cocoMethodAuthorizationAdvisor"));
        });
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.springframework.security.core.Authentication", false, getClass().getClassLoader()));
    }

    @Test
    void honorsCustomSecurityContextResolver() {
        CocoSecurityContext expected = CocoSecurityContext.authenticated(principal(Set.of(), Set.of()));
        this.contextRunner.withBean(CocoSecurityContextResolver.class, () -> () -> Optional.of(expected)).run(context -> {
            assertEquals("authenticated", context.getBean(AuthorizedService.class).authenticated());
        });
    }

    @Test
    void honorsExistingAdvisorAutoProxyCreatorWithoutRegisteringAnotherOne() throws Throwable {
        this.contextRunner.withBean(AbstractAdvisorAutoProxyCreator.class, DefaultAdvisorAutoProxyCreator::new)
                .run(context -> {
                    assertFalse(context.containsBean("cocoMethodAuthorizationAutoProxyCreator"));
                    assertEquals("authenticated", runAs(principal(Set.of(), Set.of()),
                            context.getBean(AuthorizedService.class)::authenticated));
                });
    }

    private static DefaultPointcutAdvisor companionAdvisor(AtomicInteger calls) {
        return new DefaultPointcutAdvisor(new StaticMethodMatcherPointcut() {
            @Override
            public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
                return targetClass == AuthorizedService.class && method.getName().equals("authenticated");
            }
        }, (MethodInterceptor) invocation -> {
            calls.incrementAndGet();
            return invocation.proceed();
        });
    }

    private static CocoSecurityPrincipal principal(Set<String> roles, Set<String> permissions) {
        return new CocoSecurityPrincipal("1001", "Patton", roles, permissions, Map.of());
    }

    private static AuthorizedService target(AuthorizedService service) {
        try {
            return (AuthorizedService) ((Advised) service).getTargetSource().getTarget();
        }
        catch (Exception exception) {
            throw new AssertionError("Expected an advised AuthorizedService", exception);
        }
    }

    private static <T> T runAs(CocoSecurityPrincipal principal, ThrowingSupplier<T> supplier) throws Throwable {
        try {
            return CocoSecurityContextHolder.callWithContext(CocoSecurityContext.authenticated(principal), () -> {
                try {
                    return supplier.get();
                }
                catch (RuntimeException | Error exception) {
                    throw exception;
                }
                catch (Throwable exception) {
                    throw new CheckedInvocationException(exception);
                }
            });
        }
        catch (CheckedInvocationException exception) {
            throw exception.getCause();
        }
    }

    private static void assertForbidden(ThrowingSupplier<?> supplier) {
        CocoForbiddenException exception = assertThrows(CocoForbiddenException.class, supplier::get);
        assertEquals("coco.feature.security.error.access-denied", exception.message().code());
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    @Configuration(proxyBeanMethods = false)
    static class AuthorizedServiceConfiguration {
        @Bean
        AuthorizedService authorizedService() {
            return new AuthorizedService();
        }

        @Bean
        OpenService openService() {
            return new OpenService();
        }

        @Bean
        InterfaceAuthorizedService interfaceAuthorizedService() {
            return new InterfaceAuthorizedServiceImpl();
        }

        @Bean
        InterfaceTypeAuthorizedService interfaceTypeAuthorizedService() {
            return new InterfaceTypeAuthorizedServiceImpl();
        }
    }

    @CocoAuthorize(roles = "type")
    static class AuthorizedService {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger invalidCalls = new AtomicInteger();

        @CocoAuthorize
        public String authenticated() { this.calls.incrementAndGet(); return "authenticated"; }

        @CocoAuthorize(roles = { "admin", "operator" })
        public String rolesAll() { return "roles-all"; }

        @CocoAuthorize(roles = { "admin", "operator" }, roleMode = CocoAuthorizationMode.ANY)
        public String rolesAny() { return "roles-any"; }

        @CocoAuthorize(permissions = { "order:read", "order:write" })
        public String permissionsAll() { return "permissions-all"; }

        @CocoAuthorize(permissions = { "order:read", "order:write" }, permissionMode = CocoAuthorizationMode.ANY)
        public String permissionsAny() { return "permissions-any"; }

        @CocoAuthorize(roles = "admin", permissions = "order:read")
        public String both() { return "both"; }

        @CocoAuthorize(roles = "method")
        public String methodOverridesType() { return "method"; }

        public String typeOnly() { return "type"; }

        @CocoAuthorize
        public String value() { return "value"; }

        @CocoAuthorize
        public String checked() throws IOException { throw new IOException("checked"); }

        @CocoAuthorize
        public String error() { throw new AssertionError("error"); }
    }

    interface InterfaceAuthorizedService {
        @CocoAuthorize(roles = "interface")
        String interfaceMethod();

        String implementationMethod();
    }

    static class InterfaceAuthorizedServiceImpl implements InterfaceAuthorizedService {
        @Override
        public String interfaceMethod() { return "interface"; }

        @Override
        @CocoAuthorize(roles = "implementation")
        public String implementationMethod() { return "implementation"; }
    }

    @CocoAuthorize(roles = "interface-type")
    interface InterfaceTypeAuthorizedService {
        String fromInterfaceType();
    }

    static class InterfaceTypeAuthorizedServiceImpl implements InterfaceTypeAuthorizedService {
        @Override
        public String fromInterfaceType() { return "interface-type"; }
    }

    static class OpenService {
        String open() { return "open"; }
    }

    @Configuration(proxyBeanMethods = false)
    static class InvalidServiceConfiguration {
        @Bean
        InvalidService invalidService() {
            return new InvalidService();
        }
    }

    static class InvalidService {
        final AtomicInteger calls = new AtomicInteger();

        @CocoAuthorize(roles = " ")
        public String invalid() { this.calls.incrementAndGet(); return "invalid"; }
    }

    static final class CheckedInvocationException extends RuntimeException {
        CheckedInvocationException(Throwable cause) {
            super(cause);
        }
    }
}
