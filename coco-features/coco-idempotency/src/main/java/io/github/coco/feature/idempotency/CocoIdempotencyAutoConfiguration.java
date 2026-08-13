package io.github.coco.feature.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.web.CocoWebAutoConfiguration;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.DefaultCocoExceptionHttpStatusResolver;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.idempotency.servlet.CocoIdempotencyFilter;
import io.github.coco.feature.idempotency.store.CocoIdempotencyStore;
import io.github.coco.feature.idempotency.store.InMemoryCocoIdempotencyStore;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Coco 请求幂等 Servlet 自动配置。
 *
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(before = CocoWebAutoConfiguration.class)
@ConditionalOnCocoFeature(CocoFeature.WEB)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = CocoIdempotencyFeature.PROPERTY_PREFIX, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CocoIdempotencyProperties.class)
public class CocoIdempotencyAutoConfiguration {

    /**
     * Registers the idempotency feature message bundle.
     * @return message bundle registrar
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoIdempotencyMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoIdempotencyMessageBundleRegistrar() {
        return registry -> registry.add("coco-idempotency-messages");
    }

    /**
     * Creates the default status resolver before Coco Web creates its exception handler.
     * @return idempotency-aware exception status resolver
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoExceptionHttpStatusResolver cocoIdempotencyExceptionHttpStatusResolver() {
        return new CocoIdempotencyExceptionHttpStatusResolver(new DefaultCocoExceptionHttpStatusResolver());
    }

    /**
     * Creates the default scope resolver from the verified Coco security context.
     * @return idempotency scope resolver
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoIdempotencyScopeResolver cocoIdempotencyScopeResolver() {
        return new DefaultCocoIdempotencyScopeResolver();
    }

    /**
     * 创建并校验显式路由匹配器。
     * @param properties 幂等配置
     * @return 路由匹配器
     */
    @Bean
    public CocoIdempotencyRouteMatcher cocoIdempotencyRouteMatcher(CocoIdempotencyProperties properties) {
        properties.validate();
        return new CocoIdempotencyRouteMatcher(properties.getRoutes());
    }

    /**
     * 创建默认进程内幂等存储。
     * @param properties 幂等配置
     * @param routeMatcher 已校验路由匹配器
     * @return 进程内幂等存储
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(CocoIdempotencyStore.class)
    public CocoIdempotencyStore cocoIdempotencyStore(CocoIdempotencyProperties properties,
            CocoIdempotencyRouteMatcher routeMatcher) {
        return new InMemoryCocoIdempotencyStore(properties.getMaxEntries(),
                Duration.ofSeconds(properties.getCleanupIntervalSeconds()), Clock.systemUTC());
    }

    /**
     * 注册幂等 Servlet 过滤器。
     * @param properties 幂等配置
     * @param routeMatcher 显式路由匹配器
     * @param store 幂等存储
     * @param scopeResolver 请求作用域解析器
     * @param exceptionResponseWriter Coco 统一异常响应写出器
     * @return 过滤器注册 Bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoIdempotencyFilterRegistration")
    public FilterRegistrationBean<CocoIdempotencyFilter> cocoIdempotencyFilterRegistration(
            CocoIdempotencyProperties properties, CocoIdempotencyRouteMatcher routeMatcher,
            CocoIdempotencyStore store, CocoIdempotencyScopeResolver scopeResolver,
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        CocoIdempotencyFilter filter = new CocoIdempotencyFilter(properties, routeMatcher, store,
                scopeResolver, exceptionResponseWriter);
        FilterRegistrationBean<CocoIdempotencyFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setName("cocoIdempotencyFilter");
        registration.setOrder(properties.getFilterOrder());
        registration.setAsyncSupported(false);
        registration.setDispatcherTypes(EnumSet.of(DispatcherType.REQUEST));
        return registration;
    }
}
