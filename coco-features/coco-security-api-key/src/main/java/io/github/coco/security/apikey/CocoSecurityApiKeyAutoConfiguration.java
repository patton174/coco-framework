package io.github.coco.security.apikey;

import io.github.coco.feature.security.CocoSecurityAutoConfiguration;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * Coco API Key 认证自动配置。
 * <p>
 * 该模块需要显式设置 {@code coco.security.api-key.enabled=true} 才会注册任何认证接线。
 * 业务项目提供 {@link CocoApiKeyVerifier} 可替换默认校验，提供 {@link CocoWebSecurityContextResolver}
 * 则整个默认 API Key 解析和过滤接线回退。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(before = CocoSecurityAutoConfiguration.class)
@ConditionalOnClass(Filter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "coco.security.api-key", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoApiKeyProperties.class)
public class CocoSecurityApiKeyAutoConfiguration {

    /**
     * 在自动配置阶段检查认证机制互斥关系。
     * @param environment 当前环境
     */
    @Bean
    public CocoApiKeyAuthenticationConfigurationValidator cocoApiKeyAuthenticationConfigurationValidator(
            Environment environment) {
        return new CocoApiKeyAuthenticationConfigurationValidator(environment);
    }

    /**
     * API Key 默认 Web 认证接线。
     * <p>
     * 业务提供 Web 安全上下文解析器时，该配置组整体回退。
     * </p>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(CocoWebSecurityContextResolver.class)
    static class CocoApiKeyWebSecurityConfiguration {

        /**
         * 创建默认 API Key 校验器。
         * @param properties API Key 配置
         * @return API Key 校验器
         */
        @Bean
        @ConditionalOnMissingBean(CocoApiKeyVerifier.class)
        CocoApiKeyVerifier cocoApiKeyVerifier(CocoApiKeyProperties properties) {
            return new DefaultCocoApiKeyVerifier(properties.getCredentials());
        }

        /**
         * 创建默认 API Key Web 安全上下文解析器。
         * @param properties API Key 配置
         * @param verifier API Key 校验器
         * @return Web 安全上下文解析器
         */
        @Bean
        CocoWebSecurityContextResolver cocoApiKeyWebSecurityContextResolver(CocoApiKeyProperties properties,
                CocoApiKeyVerifier verifier) {
            return new CocoApiKeyWebSecurityContextResolver(properties, verifier);
        }

        /**
         * 创建 API Key 认证失败响应过滤器。
         * @param responseWriter Coco 统一过滤器异常响应写出器
         * @return API Key 认证失败响应过滤器注册器
         */
        @Bean
        @ConditionalOnMissingBean(name = "cocoApiKeyAuthenticationFilterRegistration")
        FilterRegistrationBean<CocoApiKeyAuthenticationFilter> cocoApiKeyAuthenticationFilterRegistration(
                CocoFilterExceptionResponseWriter responseWriter) {
            FilterRegistrationBean<CocoApiKeyAuthenticationFilter> registration = new FilterRegistrationBean<>(
                    new CocoApiKeyAuthenticationFilter(responseWriter));
            registration.setName("cocoApiKeyAuthenticationFilter");
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 4);
            registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
            return registration;
        }
    }

    static final class CocoApiKeyAuthenticationConfigurationValidator {

        private CocoApiKeyAuthenticationConfigurationValidator(Environment environment) {
            if (environment.getProperty("coco.security.jwt.enabled", Boolean.class, false)) {
                throw new IllegalStateException("Authentication mechanisms conflict");
            }
            if (!environment.getProperty("coco.security.web.enabled", Boolean.class, true)) {
                throw new IllegalStateException("API Key authentication requires Coco security web bridge");
            }
        }
    }
}
