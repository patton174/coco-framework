package io.github.coco.feature.security;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.security.context.CocoSecurityContextResolver;
import io.github.coco.feature.security.context.HolderCocoSecurityContextResolver;
import io.github.coco.feature.security.authorization.CocoMethodAuthorizationInterceptor;
import io.github.coco.feature.security.authorization.CocoMethodAuthorizationManager;
import io.github.coco.feature.security.authorization.CocoMethodAuthorizationResolver;
import io.github.coco.feature.security.authorization.DefaultCocoMethodAuthorizationManager;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import io.github.coco.feature.security.web.HeaderCocoWebSecurityContextResolver;
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
import org.springframework.context.annotation.Role;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.core.Ordered;

/**
 * Coco 安全功能自动配置。
 * <p>
 * 负责为安全功能模块注册国际化消息资源，后续鉴权、认证和安全上下文提示都从该资源包扩展。
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
@AutoConfiguration
@ConditionalOnCocoFeature(CocoFeature.SECURITY)
@EnableConfigurationProperties(CocoSecurityProperties.class)
public class CocoSecurityAutoConfiguration {

    /**
     * <p>
     * 注册安全功能模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoSecurityMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoSecurityMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-security-messages");
    }

    /**
     * <p>
     * 创建默认安全上下文解析器。
     * </p>
     * @return 安全上下文解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoSecurityContextResolver cocoSecurityContextResolver() {
        return new HolderCocoSecurityContextResolver();
    }

    /**
     * 创建默认方法授权决策器。
     * @return 方法授权决策器
     */
    @Bean
    @ConditionalOnProperty(prefix = "coco.security.method", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(CocoMethodAuthorizationManager.class)
    public CocoMethodAuthorizationManager cocoMethodAuthorizationManager() {
        return new DefaultCocoMethodAuthorizationManager();
    }

    /**
     * 创建方法授权注解解析器。
     * @return 方法授权注解解析器
     */
    @Bean
    @ConditionalOnProperty(prefix = "coco.security.method", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    public CocoMethodAuthorizationResolver cocoMethodAuthorizationResolver() {
        return new CocoMethodAuthorizationResolver();
    }

    /**
     * 创建 {@link io.github.coco.feature.security.authorization.CocoAuthorize} AOP 顾问。
     * @param authorizationManager 方法授权决策器
     * @param contextResolver 安全上下文解析器
     * @param authorizationResolver 注解解析器
     * @return 方法授权顾问
     */
    @Bean
    @ConditionalOnProperty(prefix = "coco.security.method", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoMethodAuthorizationAdvisor")
    public DefaultPointcutAdvisor cocoMethodAuthorizationAdvisor(CocoMethodAuthorizationManager authorizationManager,
            CocoSecurityContextResolver contextResolver, CocoMethodAuthorizationResolver authorizationResolver) {
        return new DefaultPointcutAdvisor(new CocoMethodAuthorizationPointcut(authorizationResolver),
                new CocoMethodAuthorizationInterceptor(authorizationManager, contextResolver, authorizationResolver));
    }

    /**
     * 在应用未自行提供 Advisor 自动代理创建器时注册基础设施代理创建器。
     * @return Advisor 自动代理创建器
     */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    @ConditionalOnProperty(prefix = "coco.security.method", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(AbstractAdvisorAutoProxyCreator.class)
    public DefaultAdvisorAutoProxyCreator cocoMethodAuthorizationAutoProxyCreator() {
        return new DefaultAdvisorAutoProxyCreator();
    }

    /**
     * 方法授权静态切点。
     */
    private static final class CocoMethodAuthorizationPointcut extends StaticMethodMatcherPointcut {

        private final CocoMethodAuthorizationResolver authorizationResolver;

        private CocoMethodAuthorizationPointcut(CocoMethodAuthorizationResolver authorizationResolver) {
            this.authorizationResolver = authorizationResolver;
        }

        @Override
        public boolean matches(java.lang.reflect.Method method, Class<?> targetClass) {
            return this.authorizationResolver.requiresAuthorization(method, targetClass);
        }
    }

    /**
     * <p>
     * 创建默认 Web 安全上下文解析器。
     * </p>
     * @param properties Coco 安全配置属性
     * @return Web 安全上下文解析器
     */
    @Bean
    @ConditionalOnClass(Filter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoWebSecurityContextResolver cocoWebSecurityContextResolver(CocoSecurityProperties properties) {
        return new HeaderCocoWebSecurityContextResolver(properties.getWeb().getHeader());
    }

    /**
     * <p>
     * 创建 Web 安全上下文桥接过滤器注册器。
     * </p>
     * @param resolver Web 安全上下文解析器
     * @return Web 安全上下文桥接过滤器注册器
     */
    @Bean
    @ConditionalOnClass(Filter.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.security.web", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoSecurityWebFilterRegistration")
    public FilterRegistrationBean<CocoSecurityWebFilter> cocoSecurityWebFilterRegistration(
            CocoWebSecurityContextResolver resolver) {
        FilterRegistrationBean<CocoSecurityWebFilter> registration = new FilterRegistrationBean<>(
                new CocoSecurityWebFilter(resolver));
        registration.setName("cocoSecurityWebFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 6);
        registration.setAsyncSupported(true);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        return registration;
    }
}
