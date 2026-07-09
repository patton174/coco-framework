package io.github.coco.feature.web;

import io.github.coco.feature.web.body.CocoRequestBodyCachingFilter;
import io.github.coco.feature.web.body.CocoRequestBodyResolver;
import io.github.coco.feature.web.body.DefaultCocoRequestBodyResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Coco Web 请求体自动配置。
 * <p>
 * 注册请求体解析器和请求体缓存过滤器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
public class CocoWebRequestBodyAutoConfiguration {

    /**
     * <p>
     * 创建默认 Coco 请求体解析器。
     * </p>
     * @return 请求体解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoRequestBodyResolver cocoRequestBodyResolver() {
        return new DefaultCocoRequestBodyResolver();
    }

    /**
     * <p>
     * 创建 Coco 请求体缓存过滤器注册器。
     * </p>
     * @param properties Coco Web 配置属性
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @param requestMatcher Web 请求匹配器
     * @return 请求体缓存过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.request-body", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoRequestBodyCachingFilterRegistration")
    public FilterRegistrationBean<CocoRequestBodyCachingFilter> cocoRequestBodyCachingFilterRegistration(
            CocoWebProperties properties, CocoFilterExceptionResponseWriter exceptionResponseWriter,
            CocoWebRequestMatcher requestMatcher) {
        FilterRegistrationBean<CocoRequestBodyCachingFilter> registration = new FilterRegistrationBean<>(
                new CocoRequestBodyCachingFilter(properties.getRequestBody(), properties.getSignature(),
                        properties.getEncryption(), properties.getReplay(), exceptionResponseWriter, requestMatcher));
        registration.setName("cocoRequestBodyCachingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
