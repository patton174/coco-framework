package io.github.coco.security.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.github.coco.feature.security.CocoSecurityAutoConfiguration;
import io.github.coco.feature.security.context.CocoSecurityPrincipal;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

class CocoSpringSecurityAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    CocoSpringSecurityAutoConfiguration.class,
                    CocoSecurityAutoConfiguration.class));

    @Test
    void springBridgeIsOrderedBeforeOtherCocoAuthenticationAdapters() {
        AutoConfiguration autoConfiguration = CocoSpringSecurityAutoConfiguration.class
                .getAnnotation(AutoConfiguration.class);

        assertThat(autoConfiguration.beforeName()).containsExactlyInAnyOrder(
                "io.github.coco.security.jwt.CocoSecurityJwtAutoConfiguration",
                "io.github.coco.security.apikey.CocoSecurityApiKeyAutoConfiguration");
    }

    @Test
    void moduleOnClasspathWithoutOptInDoesNotCreateSecurityInfrastructureOrChangeResponse() {
        new WebApplicationContextRunner()
                .withUserConfiguration(FullBootApplication.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
                    assertThat(context).doesNotHaveBean("springSecurityFilterChain");
                    assertThat(context).doesNotHaveBean(CocoSpringSecurityBridgeMarker.class);

                    FilterRegistrationBean<?> registration = context.getBean("cocoSecurityWebFilterRegistration",
                            FilterRegistrationBean.class);
                    org.springframework.mock.web.MockHttpServletResponse response =
                            new org.springframework.mock.web.MockHttpServletResponse();
                    registration.getFilter().doFilter(new org.springframework.mock.web.MockHttpServletRequest(), response,
                            (request, servletResponse) ->
                                    ((jakarta.servlet.http.HttpServletResponse) servletResponse).setStatus(204));
                    assertThat(response.getStatus()).isEqualTo(204);
                });
    }

    @Test
    void defaultConfigurerIsNoOpForBusinessChainWhenModuleIsNotEnabled() {
        this.contextRunner
                .withUserConfiguration(SingleChainSecurityConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CocoSpringSecurityBridgeMarker.class);
                    assertThat(context.getBean(SecurityFilterChain.class).getFilters())
                            .noneMatch(CocoSecurityWebFilter.class::isInstance);
                });
    }

    @Test
    void enabledDefaultBridgeOwnsResolverMarkerAndDisabledCoreRegistration() {
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true")
                .withUserConfiguration(SingleChainSecurityConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CocoSpringSecurityProperties.class);
                    assertThat(context).hasSingleBean(CocoSpringSecurityPrincipalConverter.class);
                    assertThat(context).hasSingleBean(CocoSpringSecurityBridgeMarker.class);
                    assertThat(context).hasSingleBean(CocoWebSecurityContextResolver.class);
                    assertThat(context).hasBean("cocoSpringSecurityBridgeValidator");

                    FilterRegistrationBean<?> registration = context.getBean("cocoSecurityWebFilterRegistration",
                            FilterRegistrationBean.class);
                    assertThat(registration.isEnabled()).isFalse();
                    assertThat(registration.getFilter()).isNull();
                    assertThat(context).hasBean("springSecurityFilterChain");
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                });
    }

    @Test
    void defaultConfigurerIsAutomaticallyAppliedToEveryHttpSecurity() {
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true")
                .withUserConfiguration(MultipleChainSecurityConfiguration.class)
                .run(context -> {
                    Map<String, SecurityFilterChain> chains = context.getBeansOfType(SecurityFilterChain.class);
                    assertThat(chains).hasSize(2);
                    assertThat(chains.values()).allSatisfy(chain ->
                            assertThat(chain.getFilters()).anyMatch(CocoSecurityWebFilter.class::isInstance));
                });
    }

    @Test
    void userPrincipalConverterOverridesDefaultConverter() {
        CocoSpringSecurityPrincipalConverter converter = authentication ->
                CocoSecurityPrincipal.of("custom", "Custom");
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true")
                .withBean(CocoSpringSecurityPrincipalConverter.class, () -> converter)
                .withUserConfiguration(SingleChainSecurityConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CocoSpringSecurityPrincipalConverter.class);
                    assertThat(context.getBean(CocoSpringSecurityPrincipalConverter.class)).isSameAs(converter);
                    assertThat(context).hasSingleBean(CocoSpringSecurityBridgeMarker.class);
                });
    }

    @Test
    void customWebResolverMakesEntireDefaultBridgeBackOff() {
        CocoWebSecurityContextResolver resolver = request -> java.util.Optional.empty();
        this.contextRunner
                .withPropertyValues(
                        "coco.security.spring.enabled=true",
                        "coco.security.jwt.enabled=true")
                .withBean(CocoWebSecurityContextResolver.class, () -> resolver)
                .withUserConfiguration(SingleChainSecurityConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CocoSpringSecurityProperties.class);
                    assertThat(context).doesNotHaveBean(CocoSpringSecurityPrincipalConverter.class);
                    assertThat(context).doesNotHaveBean(CocoSpringSecurityBridgeMarker.class);
                    assertThat(context).doesNotHaveBean("cocoSpringSecurityBridgeValidator");
                    assertThat(context.getBean(CocoWebSecurityContextResolver.class)).isSameAs(resolver);

                    FilterRegistrationBean<?> registration = context.getBean("cocoSecurityWebFilterRegistration",
                            FilterRegistrationBean.class);
                    assertThat(registration.isEnabled()).isTrue();
                    assertThat(registration.getFilter()).isInstanceOf(CocoSecurityWebFilter.class);
                    assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 6);
                    assertThat(context.getBean(SecurityFilterChain.class).getFilters())
                            .noneMatch(CocoSecurityWebFilter.class::isInstance);
                });
    }

    @Test
    void disabledSecurityFeatureMakesEntireDefaultBridgeBackOff() {
        this.contextRunner
                .withPropertyValues(
                        "coco.security.spring.enabled=true",
                        "coco.features.disabled=security",
                        "coco.security.jwt.enabled=true")
                .withUserConfiguration(SingleChainSecurityConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CocoSpringSecurityProperties.class);
                    assertThat(context).doesNotHaveBean(CocoSpringSecurityPrincipalConverter.class);
                    assertThat(context).doesNotHaveBean(CocoSpringSecurityBridgeMarker.class);
                    assertThat(context).doesNotHaveBean("cocoSpringSecurityBridgeValidator");
                    assertThat(context).doesNotHaveBean("cocoSecurityWebFilterRegistration");
                    assertThat(context.getBean(SecurityFilterChain.class).getFilters())
                            .noneMatch(CocoSecurityWebFilter.class::isInstance);
                });
    }

    @Test
    void defaultBridgeFailsFastWithoutActiveBusinessChain() {
        this.contextRunner
                .withPropertyValues("coco.security.spring.enabled=true")
                .run(context -> assertThat(context).hasFailed().getFailure().hasMessage(
                        "coco.security.spring.enabled requires an active business SecurityFilterChain"));
    }

    @Test
    void defaultBridgeFailsFastForOtherCocoAuthenticationAdapters() {
        this.contextRunner
                .withPropertyValues(
                        "coco.security.spring.enabled=true",
                        "coco.security.jwt.enabled=true")
                .withUserConfiguration(SingleChainSecurityConfiguration.class)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseMessage("Authentication mechanisms conflict"));
        this.contextRunner
                .withPropertyValues(
                        "coco.security.spring.enabled=true",
                        "coco.security.api-key.enabled=true")
                .withUserConfiguration(SingleChainSecurityConfiguration.class)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseMessage("Authentication mechanisms conflict"));
    }

    @Test
    void rolePrefixMustBeNonBlankAndVisibleAsciiWhenDefaultBridgeIsOwned() {
        this.contextRunner
                .withPropertyValues(
                        "coco.security.spring.enabled=true",
                        "coco.security.spring.role-prefix= ")
                .withUserConfiguration(SingleChainSecurityConfiguration.class)
                .run(context -> assertThat(context).hasFailed().getFailure().hasRootCauseMessage(
                        "coco.security.spring.role-prefix must not be blank"));
        this.contextRunner
                .withPropertyValues(
                        "coco.security.spring.enabled=true",
                        "coco.security.spring.role-prefix=role space")
                .withUserConfiguration(SingleChainSecurityConfiguration.class)
                .run(context -> assertThat(context).hasFailed().getFailure().hasRootCauseMessage(
                        "coco.security.spring.role-prefix is invalid"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class FullBootApplication {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class SingleChainSecurityConfiguration {

        @Bean
        SecurityFilterChain businessSecurityFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class MultipleChainSecurityConfiguration {

        @Bean
        @Order(1)
        SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
            http.securityMatcher("/api/**");
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }

        @Bean
        @Order(2)
        SecurityFilterChain fallbackSecurityFilterChain(HttpSecurity http) throws Exception {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
            return http.build();
        }
    }
}
