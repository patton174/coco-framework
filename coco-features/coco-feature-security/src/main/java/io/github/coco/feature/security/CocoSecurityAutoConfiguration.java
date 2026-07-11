package io.github.coco.feature.security;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.security.context.CocoSecurityContextResolver;
import io.github.coco.feature.security.context.HolderCocoSecurityContextResolver;
import io.github.coco.feature.security.web.CocoSecurityWebFilter;
import io.github.coco.feature.security.web.CocoWebSecurityContextResolver;
import io.github.coco.feature.security.web.HeaderCocoWebSecurityContextResolver;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
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
 *   <li>模块：{@code coco-feature-security}</li>
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
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }
}
