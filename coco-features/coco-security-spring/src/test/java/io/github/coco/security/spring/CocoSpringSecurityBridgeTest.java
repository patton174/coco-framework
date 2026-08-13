package io.github.coco.security.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.feature.security.CocoSecurity;
import io.github.coco.feature.security.CocoSecurityAutoConfiguration;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

class CocoSpringSecurityBridgeTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoSpringSecurityAutoConfiguration.class,
                    CocoSecurityAutoConfiguration.class));

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        CocoSecurityContextHolder.clear();
    }

    @Test
    void isDisabledByDefault() {
        this.contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(CocoSpringSecurityProperties.class);
            assertThat(context).doesNotHaveBean(CocoSpringSecurityPrincipalConverter.class);
        });
    }

    @Test
    void enabledBridgeRegistersImmediatelyAfterSpringSecurityFilter() {
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true")
                .withBean("springSecurityFilterChain", Filter.class, CocoSpringSecurityBridgeTest::noOpFilter)
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoSpringSecurityProperties.class);
                    assertThat(context).hasSingleBean(CocoSpringSecurityPrincipalConverter.class);
                    assertThat(context).hasSingleBean(CocoWebSecurityContextResolver.class);
                    CocoSpringSecurityProperties properties = context.getBean(CocoSpringSecurityProperties.class);
                    assertThat(properties.getRolePrefix()).isEqualTo("ROLE_");
                    FilterRegistrationBean<?> registration = context.getBean("cocoSecurityWebFilterRegistration",
                            FilterRegistrationBean.class);
                    assertThat(registration.getFilter()).isInstanceOf(CocoSecurityWebFilter.class);
                    assertThat(registration.getFilterName()).isEqualTo("cocoSecurityWebFilter");
                    assertThat(registration.getOrder()).isEqualTo(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
                    assertThat(registration.isAsyncSupported()).isTrue();
                    assertThat(registration.determineDispatcherTypes()).isEqualTo(
                            EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR));
                });
    }

    @Test
    void defaultConverterMapsOnlyNameAndAuthorities() {
        CocoSpringSecurityProperties properties = new CocoSpringSecurityProperties();
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                "operator-42", "credential-that-must-not-leak", List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("invoice:read"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")));
        authentication.setDetails("details-that-must-not-leak");

        CocoSecurityPrincipal principal = new DefaultCocoSpringSecurityPrincipalConverter(properties)
                .convert(authentication);

        assertThat(principal.principalId()).isEqualTo("operator-42");
        assertThat(principal.principalName()).isEqualTo("operator-42");
        assertThat(principal.roles()).containsExactly("ADMIN");
        assertThat(principal.permissions()).containsExactly("invoice:read");
        assertThat(principal.attributes()).isEmpty();
        assertThat(principal.attributes().values()).doesNotContain(authentication.getCredentials(), authentication.getDetails(),
                authentication.getPrincipal());
    }

    @Test
    void converterFailsClosedForBlankNameAndIgnoresInvalidAuthorities() {
        CocoSpringSecurityPrincipalConverter converter = new DefaultCocoSpringSecurityPrincipalConverter(
                new CocoSpringSecurityProperties());
        Authentication blankName = new TestingAuthenticationToken("ignored", "credential") {
            @Override
            public String getName() {
                return "  ";
            }
        };
        Authentication invalidAuthorities = new TestingAuthenticationToken("operator", "credential") {
            @Override
            public java.util.Collection<org.springframework.security.core.GrantedAuthority> getAuthorities() {
                return Arrays.asList(null, () -> null, () -> " ", () -> "ROLE_", () -> " permission ");
            }
        };

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> converter.convert(blankName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Spring Security authentication name must not be blank");
        CocoSecurityPrincipal principal = converter.convert(invalidAuthorities);
        assertThat(principal.roles()).isEmpty();
        assertThat(principal.permissions()).containsExactly("ROLE_", "permission");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> principal.permissions().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resolverRejectsAnonymousAndUnauthenticatedIdentities() {
        CocoSpringSecurityWebSecurityContextResolver resolver = new CocoSpringSecurityWebSecurityContextResolver(
                authentication -> CocoSecurityPrincipal.of(authentication.getName(), authentication.getName()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
        UsernamePasswordAuthenticationToken unauthenticated = new UsernamePasswordAuthenticationToken("operator", "x");

        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        assertThat(resolver.resolve(request)).isEmpty();
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThat(resolver.resolve(request)).isEmpty();
    }

    @Test
    void userConverterOverridesDefault() {
        CocoSpringSecurityPrincipalConverter converter = authentication -> CocoSecurityPrincipal.of("custom", "Custom");
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true")
                .withBean("springSecurityFilterChain", Filter.class, CocoSpringSecurityBridgeTest::noOpFilter)
                .withBean(CocoSpringSecurityPrincipalConverter.class, () -> converter)
                .run(context -> {
                    SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            "operator", "credential", List.of()));
                    CocoWebSecurityContextResolver resolver = context.getBean(CocoWebSecurityContextResolver.class);
                    assertThat(resolver.resolve(new MockHttpServletRequest())).get()
                            .extracting(value -> value.principal().principalId()).isEqualTo("custom");
                });
    }

    @Test
    void userWebResolverMakesDefaultBridgeBackOff() {
        CocoWebSecurityContextResolver resolver = request -> Optional.empty();
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true")
                .withBean("springSecurityFilterChain", Filter.class, CocoSpringSecurityBridgeTest::noOpFilter)
                .withBean(CocoWebSecurityContextResolver.class, () -> resolver)
                .run(context -> {
                    assertThat(context).hasSingleBean(CocoSpringSecurityProperties.class);
                    assertThat(context).doesNotHaveBean(CocoSpringSecurityPrincipalConverter.class);
                    assertThat(context.getBean(CocoWebSecurityContextResolver.class)).isSameAs(resolver);
                    FilterRegistrationBean<?> registration = context.getBean("cocoSecurityWebFilterRegistration",
                            FilterRegistrationBean.class);
                    assertThat(registration.getOrder()).isEqualTo(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 6);
                });
    }

    @Test
    void incompatibleAuthenticationAdaptersFailFast() {
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true", "coco.security.jwt.enabled=true")
                .withBean("springSecurityFilterChain", Filter.class, CocoSpringSecurityBridgeTest::noOpFilter)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessage("Authentication mechanisms conflict"));
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true", "coco.security.api-key.enabled=true")
                .withBean("springSecurityFilterChain", Filter.class, CocoSpringSecurityBridgeTest::noOpFilter)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessage("Authentication mechanisms conflict"));
    }

    @Test
    void conflictingAdapterFailsEvenWhenBusinessResolverMakesBridgeBackOff() {
        CocoWebSecurityContextResolver resolver = request -> Optional.empty();
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true", "coco.security.jwt.enabled=true")
                .withBean("springSecurityFilterChain", Filter.class, CocoSpringSecurityBridgeTest::noOpFilter)
                .withBean(CocoWebSecurityContextResolver.class, () -> resolver)
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasMessage("Authentication mechanisms conflict"));
    }

    @Test
    void missingSpringSecurityFilterChainFailsFast() {
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true")
                .run(context -> assertThat(context).hasFailed().getFailure().hasMessage(
                        "coco.security.spring.enabled requires a springSecurityFilterChain bean"));
    }

    @Test
    void rolePrefixMustBeNonBlankAndVisibleAsciiWhenEnabled() {
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true", "coco.security.spring.role-prefix= ")
                .withBean("springSecurityFilterChain", Filter.class, CocoSpringSecurityBridgeTest::noOpFilter)
                .run(context -> assertThat(context).hasFailed().getFailure().hasRootCauseMessage(
                        "coco.security.spring.role-prefix must not be blank"));
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true", "coco.security.spring.role-prefix=role space")
                .withBean("springSecurityFilterChain", Filter.class, CocoSpringSecurityBridgeTest::noOpFilter)
                .run(context -> assertThat(context).hasFailed().getFailure().hasRootCauseMessage(
                        "coco.security.spring.role-prefix is invalid"));
    }

    @Test
    void filterChainProxyBindsRequestAndErrorContextsThenClearsBothHolders() throws Exception {
        AtomicReference<CocoSecurityContext> observed = new AtomicReference<>();
        AtomicReference<Authentication> observedAuthentication = new AtomicReference<>();
        FilterChainProxy springSecurityFilterChain = new FilterChainProxy(new DefaultSecurityFilterChain(
                AnyRequestMatcher.INSTANCE, authenticatedFilter("operator-42")));
        CocoSecurityWebFilter cocoFilter = new CocoSecurityWebFilter(new CocoSpringSecurityWebSecurityContextResolver(
                new DefaultCocoSpringSecurityPrincipalConverter(new CocoSpringSecurityProperties())));

        for (DispatcherType dispatcherType : List.of(DispatcherType.REQUEST, DispatcherType.ERROR)) {
            CocoSecurityContextHolder.clear();
            SecurityContextHolder.clearContext();
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
            request.setDispatcherType(dispatcherType);
            springSecurityFilterChain.doFilter(request, new MockHttpServletResponse(), (servletRequest, response) ->
                    cocoFilter.doFilter(servletRequest, response, (downstreamRequest, downstreamResponse) -> {
                        observed.set(CocoSecurity.requireAuthenticated());
                        observedAuthentication.set(SecurityContextHolder.getContext().getAuthentication());
                    }));
            assertThat(observed.get().principal().principalId()).isEqualTo("operator-42");
            assertThat(observedAuthentication.get().getName()).isEqualTo("operator-42");
            assertThat(CocoSecurityContextHolder.current()).isEmpty();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }

    private static Filter noOpFilter() {
        return (request, response, chain) -> chain.doFilter(request, response);
    }

    private static Filter authenticatedFilter(String name) {
        return (request, response, chain) -> {
            SecurityContext authenticated = SecurityContextHolder.createEmptyContext();
            authenticated.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(name, "credential", List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("invoice:read"))));
            SecurityContextHolder.setContext(authenticated);
            chain.doFilter(request, response);
        };
    }
}
