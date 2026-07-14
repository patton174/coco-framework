package io.github.coco.feature.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.feature.web.CocoWebAutoConfiguration;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Coco 限流模块自动配置。
 * <p>
 * 该模块不加入标准特性集合或 starter。应用显式依赖该模块并设置 {@code coco.rate-limit.enabled=true} 后，
 * 才会注册限流基础设施。
 * </p>
 */
@AutoConfiguration(after = CocoWebAutoConfiguration.class)
@EnableConfigurationProperties(CocoRateLimitProperties.class)
@ConditionalOnProperty(prefix = "coco.rate-limit", name = "enabled", havingValue = "true")
public class CocoRateLimitAutoConfiguration {

    /**
     * 注册限流模块内置国际化消息资源。
     * @return 限流消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoRateLimitMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoRateLimitMessageBundleRegistrar() {
        return registry -> registry.add("coco-rate-limit-messages");
    }

    /**
     * 创建默认限流键解析器。
     * @return 默认限流键解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoRateLimitKeyResolver cocoRateLimitKeyResolver() {
        return new DefaultCocoRateLimitKeyResolver();
    }

    /**
     * 创建进程内限流参考存储。
     * @param properties 限流配置
     * @return 进程内限流存储
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public CocoRateLimitStore cocoRateLimitStore(CocoRateLimitProperties properties) {
        return new InMemoryCocoRateLimitStore(properties);
    }

    /**
     * 创建默认限流路由匹配器。
     * @param properties 限流配置
     * @param requestMatcher Coco Web 请求匹配器
     * @return 默认限流路由匹配器
     */
    @Bean
    @ConditionalOnBean(CocoWebRequestMatcher.class)
    @ConditionalOnMissingBean
    public CocoRateLimitRouteMatcher cocoRateLimitRouteMatcher(CocoRateLimitProperties properties,
            CocoWebRequestMatcher requestMatcher) {
        return new DefaultCocoRateLimitRouteMatcher(properties, requestMatcher);
    }

    /**
     * 创建限流拒绝响应写出器。
     * @param exceptionHandler Coco Web 全局异常处理器
     * @param objectMapper JSON 序列化器
     * @return 限流拒绝响应写出器
     */
    @Bean
    @ConditionalOnBean(CocoWebExceptionHandler.class)
    @ConditionalOnMissingBean
    public CocoRateLimitResponseWriter cocoRateLimitResponseWriter(CocoWebExceptionHandler exceptionHandler,
            ObjectMapper objectMapper) {
        return new CocoRateLimitResponseWriter(exceptionHandler, objectMapper);
    }

    /**
     * 创建限流 Servlet 过滤器注册器。
     * @param routeMatcher 限流路由匹配器
     * @param keyResolver 限流键解析器
     * @param store 限流原子存储
     * @param requestContextResolver Coco Web 请求上下文解析器
     * @param responseWriter 限流拒绝响应写出器
     * @return 限流 Servlet 过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean({ CocoRateLimitRouteMatcher.class, CocoWebRequestContextResolver.class,
            CocoRateLimitResponseWriter.class })
    @ConditionalOnMissingBean(name = "cocoRateLimitFilterRegistration")
    public FilterRegistrationBean<CocoRateLimitFilter> cocoRateLimitFilterRegistration(
            CocoRateLimitRouteMatcher routeMatcher, CocoRateLimitKeyResolver keyResolver, CocoRateLimitStore store,
            CocoWebRequestContextResolver requestContextResolver, CocoRateLimitResponseWriter responseWriter) {
        FilterRegistrationBean<CocoRateLimitFilter> registration = new FilterRegistrationBean<>(
                new CocoRateLimitFilter(routeMatcher, keyResolver, store, requestContextResolver, responseWriter));
        registration.setName("cocoRateLimitFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
