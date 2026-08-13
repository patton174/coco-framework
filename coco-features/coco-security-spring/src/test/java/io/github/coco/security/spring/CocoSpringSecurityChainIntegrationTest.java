package io.github.coco.security.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.feature.security.CocoSecurity;
import io.github.coco.feature.security.CocoSecurityAutoConfiguration;
import io.github.coco.feature.security.context.CocoSecurityContext;
import io.github.coco.feature.security.context.CocoSecurityContextHolder;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.DefaultSecurityFilterChain;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;

class CocoSpringSecurityChainIntegrationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoSpringSecurityAutoConfiguration.class,
                    CocoSecurityAutoConfiguration.class))
            .withPropertyValues("coco.security.spring.enabled=true")
            .withUserConfiguration(AuthenticatedSecurityConfiguration.class);

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        CocoSecurityContextHolder.clear();
    }

    @Test
    void configurerPlacesBridgeAfterAuthenticationAndAnonymousButBeforeAuthorization() {
        this.contextRunner.run(context -> {
            SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
            int authenticationIndex = indexOf(chain, TestAuthenticationFilter.class);
            int anonymousIndex = indexOf(chain, AnonymousAuthenticationFilter.class);
            int cocoIndex = indexOf(chain, CocoSecurityWebFilter.class);
            int authorizationIndex = indexOf(chain, AuthorizationFilter.class);

            assertThat(authenticationIndex).isNotNegative();
            assertThat(anonymousIndex).isGreaterThan(authenticationIndex);
            assertThat(cocoIndex).isGreaterThan(anonymousIndex);
            assertThat(authorizationIndex).isGreaterThan(cocoIndex);
        });
    }

    @Test
    void realFilterChainBindsAndClearsRequestErrorAndAsyncDispatches() {
        this.contextRunner.run(context -> {
            Filter springSecurityFilterChain = context.getBean("springSecurityFilterChain", Filter.class);
            for (DispatcherType dispatcherType : List.of(
                    DispatcherType.REQUEST, DispatcherType.ERROR, DispatcherType.ASYNC)) {
                AtomicReference<CocoSecurityContext> observedCoco = new AtomicReference<>();
                AtomicReference<Authentication> observedSpring = new AtomicReference<>();
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");
                request.setDispatcherType(dispatcherType);
                request.setAsyncSupported(true);

                springSecurityFilterChain.doFilter(request, new MockHttpServletResponse(), (servletRequest, response) -> {
                    observedCoco.set(CocoSecurity.requireAuthenticated());
                    observedSpring.set(SecurityContextHolder.getContext().getAuthentication());
                });

                assertThat(observedCoco.get().principal().principalId()).isEqualTo("operator-42");
                assertThat(observedCoco.get().principal().roles()).containsExactlyInAnyOrder("ADMIN");
                assertThat(observedCoco.get().principal().permissions()).containsExactlyInAnyOrder("invoice:read");
                assertThat(observedSpring.get().getName()).isEqualTo("operator-42");
                assertThat(CocoSecurityContextHolder.current()).isEmpty();
                assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            }
        });
    }

    @Test
    void resolverRejectsAnonymousAndUnauthenticatedIdentities() {
        CocoSpringSecurityWebSecurityContextResolver resolver = new CocoSpringSecurityWebSecurityContextResolver(
                authentication -> CocoSecurityPrincipal.of(authentication.getName(), authentication.getName()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/orders");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("operator", "credential"));
        assertThat(resolver.resolve(request)).isEmpty();
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThat(resolver.resolve(request)).isEmpty();
    }

    @Test
    void externalContainerSuccessorRunsTooLateToExposeCocoIdentityInsideSecurityChain() throws Exception {
        AtomicBoolean chainBusinessSawCocoIdentity = new AtomicBoolean();
        AtomicBoolean externalSuccessorSawCocoIdentity = new AtomicBoolean();
        Filter chainBusinessFilter = (request, response, chain) -> {
            chainBusinessSawCocoIdentity.set(CocoSecurity.isAuthenticated());
            chain.doFilter(request, response);
        };
        FilterChainProxy springSecurityFilterChain = new FilterChainProxy(new DefaultSecurityFilterChain(
                AnyRequestMatcher.INSTANCE, new TestAuthenticationFilter(), chainBusinessFilter));
        CocoSecurityWebFilter oldExternalSuccessor = new CocoSecurityWebFilter(
                new CocoSpringSecurityWebSecurityContextResolver(
                        new DefaultCocoSpringSecurityPrincipalConverter(new CocoSpringSecurityProperties())));

        springSecurityFilterChain.doFilter(new MockHttpServletRequest("GET", "/orders"),
                new MockHttpServletResponse(), (request, response) -> oldExternalSuccessor.doFilter(request, response,
                        (downstreamRequest, downstreamResponse) ->
                                externalSuccessorSawCocoIdentity.set(CocoSecurity.isAuthenticated())));

        assertThat(chainBusinessSawCocoIdentity).isFalse();
        assertThat(externalSuccessorSawCocoIdentity).isTrue();
        assertThat(CocoSecurityContextHolder.current()).isEmpty();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void externalContainerSuccessorDoesNotRunWhenAuthorizationRejectsTheRequest() {
        AtomicBoolean bridgeConvertedAuthentication = new AtomicBoolean();
        AtomicBoolean externalSuccessorRan = new AtomicBoolean();
        CocoSpringSecurityPrincipalConverter converter = authentication -> {
            bridgeConvertedAuthentication.set(true);
            return CocoSecurityPrincipal.of(authentication.getName(), authentication.getName());
        };

        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        CocoSpringSecurityAutoConfiguration.class,
                        CocoSecurityAutoConfiguration.class))
                .withPropertyValues("coco.security.spring.enabled=true")
                .withBean(CocoSpringSecurityPrincipalConverter.class, () -> converter)
                .withUserConfiguration(DeniedSecurityConfiguration.class)
                .run(context -> {
                    Filter springSecurityFilterChain = context.getBean("springSecurityFilterChain", Filter.class);
                    MockHttpServletResponse response = new MockHttpServletResponse();

                    springSecurityFilterChain.doFilter(new MockHttpServletRequest("GET", "/orders"), response,
                            (request, servletResponse) -> externalSuccessorRan.set(true));

                    assertThat(response.getStatus()).isEqualTo(403);
                    assertThat(bridgeConvertedAuthentication).isTrue();
                    assertThat(externalSuccessorRan).isFalse();
                    assertThat(CocoSecurityContextHolder.current()).isEmpty();
                    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
                });
    }

    private static int indexOf(SecurityFilterChain chain, Class<?> filterType) {
        for (int index = 0; index < chain.getFilters().size(); index++) {
            if (filterType.isInstance(chain.getFilters().get(index))) {
                return index;
            }
        }
        return -1;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class AuthenticatedSecurityConfiguration {

        @Bean
        SecurityFilterChain businessSecurityFilterChain(HttpSecurity http) throws Exception {
            http.addFilterBefore(new TestAuthenticationFilter(), AnonymousAuthenticationFilter.class);
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class DeniedSecurityConfiguration {

        @Bean
        SecurityFilterChain deniedSecurityFilterChain(HttpSecurity http) throws Exception {
            http.addFilterBefore(new TestAuthenticationFilter(), AnonymousAuthenticationFilter.class);
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll());
            return http.build();
        }
    }

    static final class TestAuthenticationFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "operator-42", "credential-that-must-not-leak", List.of(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("invoice:read"))));
            SecurityContextHolder.setContext(securityContext);
            chain.doFilter(request, response);
        }
    }
}
