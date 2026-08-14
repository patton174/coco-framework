package io.github.coco.feature.ratelimit;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.i18n.CocoMessageService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Coco 限流模块自动配置。
 * <p>
 * 该模块不加入标准特性集合或 starter。应用显式依赖该模块并设置 {@code coco.rate-limit.enabled=true} 后，
 * 才会注册限流基础设施。
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoRateLimitProperties.class)
@ConditionalOnProperty(prefix = "coco.rate-limit", name = "enabled", havingValue = "true")
public class CocoRateLimitAutoConfiguration {

    static final int MVC_INTERCEPTOR_ORDER = Ordered.HIGHEST_PRECEDENCE;

    static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

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
     * 创建限流专用 UTC 时钟。
     * @return 限流时钟
     */
    @Bean("cocoRateLimitClock")
    @ConditionalOnMissingBean(name = "cocoRateLimitClock")
    public Clock cocoRateLimitClock() {
        return Clock.systemUTC();
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
    public CocoRateLimitStore cocoRateLimitStore(CocoRateLimitProperties properties,
            @Qualifier("cocoRateLimitClock") Clock clock) {
        return new InMemoryCocoRateLimitStore(properties, clock, true);
    }

    /**
     * 创建默认限流路由匹配器。
     * @param properties 限流配置
     * @param requestMatcher Coco Web 请求匹配器
     * @return 默认限流路由匹配器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoRateLimitRouteMatcher cocoRateLimitRouteMatcher(CocoRateLimitProperties properties) {
        return new DefaultCocoRateLimitRouteMatcher(properties);
    }

    /**
     * 创建限流拒绝响应写出器。
     * @param exceptionHandler Coco Web 全局异常处理器
     * @param objectMapper JSON 序列化器
     * @return 限流拒绝响应写出器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoRateLimitResponseWriter cocoRateLimitResponseWriter(CocoMessageService messageService,
            ObjectMapper objectMapper) {
        return new CocoRateLimitResponseWriter(messageService, objectMapper);
    }

    /**
     * 创建 Filter 和 MVC 注解后备共用的限流请求执行器。
     * @param keyResolver 限流键解析器
     * @param store 限流原子存储
     * @param requestContextResolver Coco Web 请求上下文解析器
     * @param responseWriter 限流拒绝响应写出器
     * @param clock 限流时钟
     * @return 限流请求执行器
     */
    @Bean
    @ConditionalOnBean(CocoRateLimitResponseWriter.class)
    @ConditionalOnMissingBean
    public CocoRateLimitRequestHandler cocoRateLimitRequestHandler(CocoRateLimitKeyResolver keyResolver,
            CocoRateLimitStore store, CocoRateLimitResponseWriter responseWriter,
            @Qualifier("cocoRateLimitClock") Clock clock) {
        return new CocoRateLimitRequestHandler(keyResolver, store, responseWriter, clock);
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
    @ConditionalOnBean({ CocoRateLimitRouteMatcher.class, CocoRateLimitRequestHandler.class })
    @ConditionalOnMissingBean(name = "cocoRateLimitFilterRegistration")
    public FilterRegistrationBean<CocoRateLimitFilter> cocoRateLimitFilterRegistration(
            CocoRateLimitRouteMatcher routeMatcher, CocoRateLimitRequestHandler requestHandler) {
        FilterRegistrationBean<CocoRateLimitFilter> registration = new FilterRegistrationBean<>(
                new CocoRateLimitFilter(routeMatcher, requestHandler));
        registration.setName("cocoRateLimitFilter");
        registration.setOrder(FILTER_ORDER);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * 注册注解限流后备拦截器；路径匹配的 Filter 已执行时该拦截器不会重复占用配额。
     * @param routeMatcher 限流路由匹配器
     * @param requestHandler 限流请求执行器
     * @return MVC 限流配置器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean({ CocoRateLimitRouteMatcher.class, CocoRateLimitRequestHandler.class })
    @ConditionalOnMissingBean(name = "cocoRateLimitMvcConfigurer")
    public WebMvcConfigurer cocoRateLimitMvcConfigurer(CocoRateLimitRouteMatcher routeMatcher,
            CocoRateLimitRequestHandler requestHandler) {
        return new CocoRateLimitWebMvcConfigurer(routeMatcher, requestHandler);
    }

    private static final class CocoRateLimitWebMvcConfigurer implements WebMvcConfigurer {

        private final CocoRateLimitMvcInterceptor interceptor;

        private CocoRateLimitWebMvcConfigurer(CocoRateLimitRouteMatcher routeMatcher,
                CocoRateLimitRequestHandler requestHandler) {
            this.interceptor = new CocoRateLimitMvcInterceptor(routeMatcher, requestHandler);
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(this.interceptor).order(MVC_INTERCEPTOR_ORDER);
        }
    }
}
