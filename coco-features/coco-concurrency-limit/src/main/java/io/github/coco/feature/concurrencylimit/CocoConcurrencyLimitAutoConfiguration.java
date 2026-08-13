package io.github.coco.feature.concurrencylimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.web.CocoWebAutoConfiguration;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Coco 在途请求并发限制模块自动配置。
 * <p>
 * 应用必须显式依赖本模块并设置 {@code coco.concurrency-limit.enabled=true} 才会注册 Servlet 基础设施。
 * </p>
 */
@AutoConfiguration(after = CocoWebAutoConfiguration.class)
@ConditionalOnCocoFeature(CocoFeature.WEB)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "coco.concurrency-limit", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoConcurrencyLimitProperties.class)
public class CocoConcurrencyLimitAutoConfiguration {

    /**
     * 注册模块内置国际化消息资源。
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoConcurrencyLimitMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoConcurrencyLimitMessageBundleRegistrar() {
        return registry -> registry.add("coco-concurrency-limit-messages");
    }

    /**
     * 创建默认可信客户端 IP 键解析器。
     * @return 并发限制键解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoConcurrencyLimitKeyResolver cocoConcurrencyLimitKeyResolver() {
        return new DefaultCocoConcurrencyLimitKeyResolver();
    }

    /**
     * 创建容量受限的进程内参考存储。
     * @param properties 并发限制配置
     * @return 原子并发许可存储
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public CocoConcurrencyLimitStore cocoConcurrencyLimitStore(CocoConcurrencyLimitProperties properties) {
        return new InMemoryCocoConcurrencyLimitStore(properties);
    }

    /**
     * 创建默认有序显式路由匹配器。
     * @param properties 并发限制配置
     * @param requestMatcher Coco Web 请求匹配器
     * @return 并发限制路由匹配器
     */
    @Bean
    @ConditionalOnBean(CocoWebRequestMatcher.class)
    @ConditionalOnMissingBean
    public CocoConcurrencyLimitRouteMatcher cocoConcurrencyLimitRouteMatcher(
            CocoConcurrencyLimitProperties properties, CocoWebRequestMatcher requestMatcher) {
        return new DefaultCocoConcurrencyLimitRouteMatcher(properties, requestMatcher);
    }

    /**
     * 创建复用 Coco 统一异常响应的拒绝响应写出器。
     * @param exceptionHandler Coco Web 全局异常处理器
     * @param objectMapper JSON 序列化器提供器
     * @param properties 并发限制配置
     * @return 拒绝响应写出器
     */
    @Bean
    @ConditionalOnBean(CocoWebExceptionHandler.class)
    @ConditionalOnMissingBean
    public CocoConcurrencyLimitResponseWriter cocoConcurrencyLimitResponseWriter(
            CocoWebExceptionHandler exceptionHandler, ObjectProvider<ObjectMapper> objectMapper,
            CocoConcurrencyLimitProperties properties) {
        return new DefaultCocoConcurrencyLimitResponseWriter(exceptionHandler,
                objectMapper.getIfAvailable(ObjectMapper::new), properties);
    }

    /**
     * 创建 Filter 与 MVC 注解后备共用的请求执行器。
     * @param properties 并发限制配置
     * @param keyResolver 解析键 SPI
     * @param store 原子存储 SPI
     * @param requestContextResolver Coco Web 请求上下文解析器
     * @param responseWriter 拒绝响应写出器
     * @return 并发许可请求执行器
     */
    @Bean
    @ConditionalOnBean({ CocoWebRequestContextResolver.class, CocoConcurrencyLimitResponseWriter.class })
    @ConditionalOnMissingBean
    public CocoConcurrencyLimitRequestHandler cocoConcurrencyLimitRequestHandler(
            CocoConcurrencyLimitProperties properties, CocoConcurrencyLimitKeyResolver keyResolver,
            CocoConcurrencyLimitStore store, CocoWebRequestContextResolver requestContextResolver,
            CocoConcurrencyLimitResponseWriter responseWriter) {
        return new CocoConcurrencyLimitRequestHandler(properties, keyResolver, store,
                requestContextResolver, responseWriter);
    }

    /**
     * 注册路径配置使用的 Servlet 过滤器。
     * @param properties 并发限制配置
     * @param routeMatcher 有序路由匹配器
     * @param requestHandler 并发许可请求执行器
     * @return Servlet 过滤器注册器
     */
    @Bean
    @ConditionalOnBean({ CocoConcurrencyLimitRouteMatcher.class, CocoConcurrencyLimitRequestHandler.class })
    @ConditionalOnMissingBean(name = "cocoConcurrencyLimitFilterRegistration")
    public FilterRegistrationBean<CocoConcurrencyLimitFilter> cocoConcurrencyLimitFilterRegistration(
            CocoConcurrencyLimitProperties properties, CocoConcurrencyLimitRouteMatcher routeMatcher,
            CocoConcurrencyLimitRequestHandler requestHandler) {
        FilterRegistrationBean<CocoConcurrencyLimitFilter> registration = new FilterRegistrationBean<>(
                new CocoConcurrencyLimitFilter(routeMatcher, requestHandler));
        registration.setName("cocoConcurrencyLimitFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.setAsyncSupported(properties.getAsyncPolicy() != CocoConcurrencyLimitAsyncPolicy.REJECT);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * 注册 {@link CocoConcurrencyLimited} 注解后备拦截器。
     * @param routeMatcher 有序路由匹配器
     * @param requestHandler 并发许可请求执行器
     * @return MVC 配置器
     */
    @Bean
    @ConditionalOnBean({ CocoConcurrencyLimitRouteMatcher.class, CocoConcurrencyLimitRequestHandler.class })
    @ConditionalOnMissingBean(name = "cocoConcurrencyLimitMvcConfigurer")
    public WebMvcConfigurer cocoConcurrencyLimitMvcConfigurer(CocoConcurrencyLimitRouteMatcher routeMatcher,
            CocoConcurrencyLimitRequestHandler requestHandler) {
        CocoConcurrencyLimitMvcInterceptor interceptor = new CocoConcurrencyLimitMvcInterceptor(routeMatcher,
                requestHandler);
        return new ConcurrencyLimitWebMvcConfigurer(interceptor);
    }

    private static final class ConcurrencyLimitWebMvcConfigurer implements WebMvcConfigurer {

        private final CocoConcurrencyLimitMvcInterceptor interceptor;

        private ConcurrencyLimitWebMvcConfigurer(CocoConcurrencyLimitMvcInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(this.interceptor).order(Ordered.HIGHEST_PRECEDENCE);
        }
    }
}
