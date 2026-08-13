package io.github.coco.security.spring;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.security.CocoSecurityAutoConfiguration;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Explicit Spring Security to Coco security-context bridge auto-configuration.
 * <p>
 * This module does not create an authentication mechanism or a {@code SecurityFilterChain}. It registers the Coco
 * context filter immediately after {@code springSecurityFilterChain}, allowing an existing Spring Security flow to
 * expose its authenticated identity to Coco code.
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(before = CocoSecurityAutoConfiguration.class)
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
@EnableConfigurationProperties(CocoSpringSecurityProperties.class)
public class CocoSpringSecurityAutoConfiguration {

    @Bean
    SmartInitializingSingleton cocoSpringSecurityBridgeValidator(ConfigurableListableBeanFactory beanFactory,
            CocoSpringSecurityProperties properties, Environment environment) {
        return () -> {
            if (properties.isEnabled() && !isSpringSecurityFilterChain(beanFactory)) {
                throw new IllegalStateException(
                        "coco.security.spring.enabled requires a springSecurityFilterChain bean");
            }
            if (properties.isEnabled() && (environment.getProperty("coco.security.jwt.enabled", Boolean.class, false)
                    || environment.getProperty("coco.security.api-key.enabled", Boolean.class, false))) {
                throw new IllegalStateException("Authentication mechanisms conflict");
            }
        };
    }

    private static boolean isSpringSecurityFilterChain(ConfigurableListableBeanFactory beanFactory) {
        return beanFactory.containsBean("springSecurityFilterChain")
                && beanFactory.getBean("springSecurityFilterChain") instanceof Filter;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnCocoFeature(CocoFeature.SECURITY)
    @ConditionalOnMissingBean(CocoWebSecurityContextResolver.class)
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

        @Bean(name = "cocoSecurityWebFilterRegistration")
        @ConditionalOnMissingBean(name = "cocoSecurityWebFilterRegistration")
        FilterRegistrationBean<CocoSecurityWebFilter> cocoSecurityWebFilterRegistration(
                CocoWebSecurityContextResolver resolver) {
            FilterRegistrationBean<CocoSecurityWebFilter> registration = new FilterRegistrationBean<>(
                    new CocoSecurityWebFilter(resolver));
            registration.setName("cocoSecurityWebFilter");
            registration.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
            registration.setAsyncSupported(true);
            registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
            return registration;
        }

    }
}
