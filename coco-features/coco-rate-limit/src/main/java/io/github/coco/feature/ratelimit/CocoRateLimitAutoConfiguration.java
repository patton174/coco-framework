package io.github.coco.feature.ratelimit;

import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
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
 * 限流功能同时受 Coco 标准功能计划和 {@code coco.rate-limit.enabled} 属性控制。属性默认关闭，避免升级后
 * 自动启用限流。
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoRateLimitProperties.class)
@ConditionalOnCocoFeature(CocoFeature.RATE_LIMIT)
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
    public CocoRateLimitKeyResolver cocoRateLimitKeyResolver(CocoRateLimitProperties properties) {
        return new DefaultCocoRateLimitKeyResolver(properties.getTrustedProxy());
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
     * @param messageService 国际化消息服务
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
     * @param routeMatcher 限流路由匹配器
     * @param requestHandler 限流请求执行器
     * @param properties 限流配置
     * @return 限流 Servlet 过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean({ CocoRateLimitRouteMatcher.class, CocoRateLimitRequestHandler.class })
    @ConditionalOnMissingBean(name = "cocoRateLimitFilterRegistration")
    public FilterRegistrationBean<CocoRateLimitFilter> cocoRateLimitFilterRegistration(
            CocoRateLimitRouteMatcher routeMatcher, CocoRateLimitRequestHandler requestHandler,
            CocoRateLimitProperties properties) {
        FilterRegistrationBean<CocoRateLimitFilter> registration = new FilterRegistrationBean<>(
                new CocoRateLimitFilter(routeMatcher, requestHandler, properties.getFilter()));
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
