package io.github.coco.security.spring;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.security.CocoSecurityAutoConfiguration;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Explicit Spring Security to Coco security-context bridge auto-configuration.
 * <p>
 * This module does not create an authentication mechanism or a {@code SecurityFilterChain}. Its default
 * {@link CocoSpringSecurityConfigurer} adds the Coco context filter to each business Spring Security chain after
 * authentication has completed and before authorization.
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(before = CocoSecurityAutoConfiguration.class, beforeName = {
        "io.github.coco.security.jwt.CocoSecurityJwtAutoConfiguration",
        "io.github.coco.security.apikey.CocoSecurityApiKeyAutoConfiguration"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnCocoFeature(CocoFeature.WEB)
@ConditionalOnClass(name = {
        "jakarta.servlet.Filter",
        "io.github.coco.feature.security.web.CocoSecurityWebFilter",
        "io.github.coco.feature.security.web.CocoWebSecurityContextResolver",
        "org.springframework.security.core.Authentication",
        "org.springframework.security.core.context.SecurityContextHolder",
        "org.springframework.security.authentication.AnonymousAuthenticationToken"
})
@ConditionalOnProperty(prefix = "coco.security.spring", name = "enabled", havingValue = "true")
public class CocoSpringSecurityAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnCocoFeature(CocoFeature.SECURITY)
    @ConditionalOnMissingBean(CocoWebSecurityContextResolver.class)
    @EnableConfigurationProperties(CocoSpringSecurityProperties.class)
    static class CocoSpringSecurityBridgeConfiguration {

        @Bean
        @ConditionalOnMissingBean(CocoSpringSecurityPrincipalConverter.class)
        CocoSpringSecurityPrincipalConverter cocoSpringSecurityPrincipalConverter(
                CocoSpringSecurityProperties properties) {
            return new DefaultCocoSpringSecurityPrincipalConverter(properties);
        }

        @Bean
        CocoWebSecurityContextResolver cocoSpringSecurityWebSecurityContextResolver(
                CocoSpringSecurityPrincipalConverter principalConverter) {
            return new CocoSpringSecurityWebSecurityContextResolver(principalConverter);
        }

        @Bean
        CocoSpringSecurityBridgeMarker cocoSpringSecurityBridgeMarker(
                CocoWebSecurityContextResolver resolver) {
            return new CocoSpringSecurityBridgeMarker(resolver);
        }

        /**
         * Prevents Coco Security core from registering its bridge before Spring Security.
         * @return disabled registration occupying the core bean name
         */
        @Bean(name = "cocoSecurityWebFilterRegistration")
        @ConditionalOnMissingBean(name = "cocoSecurityWebFilterRegistration")
        FilterRegistrationBean<CocoSecurityWebFilter> cocoSecurityWebFilterRegistration(
                CocoSpringSecurityBridgeMarker marker) {
            FilterRegistrationBean<CocoSecurityWebFilter> registration = new FilterRegistrationBean<>();
            registration.setEnabled(false);
            return registration;
        }

        @Bean
        SmartInitializingSingleton cocoSpringSecurityBridgeValidator(
                ObjectProvider<SecurityFilterChain> securityFilterChains,
                ConfigurableListableBeanFactory beanFactory, Environment environment) {
            if (environment.getProperty("coco.security.jwt.enabled", Boolean.class, false)
                    || environment.getProperty("coco.security.api-key.enabled", Boolean.class, false)) {
                throw new IllegalStateException("Authentication mechanisms conflict");
            }
            return () -> {
                if (securityFilterChains.stream().findAny().isEmpty()
                        || !isSpringSecurityFilterChain(beanFactory)) {
                    throw new IllegalStateException(
                            "coco.security.spring.enabled requires an active business SecurityFilterChain");
                }
            };
        }

        private static boolean isSpringSecurityFilterChain(ConfigurableListableBeanFactory beanFactory) {
            return beanFactory.containsBean("springSecurityFilterChain")
                    && beanFactory.getBean("springSecurityFilterChain") instanceof Filter;
        }
    }
}
