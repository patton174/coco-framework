package io.github.coco.feature.web;

import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.context.CocoWebRequestMatcher;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.replay.CocoReplayFilter;
import io.github.coco.feature.web.replay.CocoReplayKeyResolver;
import io.github.coco.feature.web.replay.CocoReplayRequestShapeFilter;
import io.github.coco.feature.web.replay.CocoReplayStore;
import io.github.coco.feature.web.replay.DefaultCocoReplayKeyResolver;
import io.github.coco.feature.web.replay.InMemoryCocoReplayStore;
import io.github.coco.feature.web.request.metadata.CocoWebRequestSecurityMetadataResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Coco Web 防重放自动配置�? * <p>
 * 注册防重放键解析器、防重放存储和防重放过滤器�? * </p>
 * <p>
 * 项目信息�? * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库�?a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
public class CocoWebReplayAutoConfiguration {

    /**
     * <p>
     * 创建默认 Coco 防重放键解析器�?     * </p>
     * @param properties Coco Web 配置属�?     * @return 防重放键解析�?     */
    @Bean
    @ConditionalOnMissingBean
    public CocoReplayKeyResolver cocoReplayKeyResolver(CocoWebProperties properties) {
        return new DefaultCocoReplayKeyResolver(properties.getReplay());
    }

    /**
     * <p>
     * 创建默认 Coco 防重放存储�?     * </p>
     * @param properties Coco Web 配置属�?     * @return 防重放存�?     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "coco.web.replay", name = "store-type", havingValue = "in-memory",
            matchIfMissing = true)
    public CocoReplayStore cocoReplayStore(CocoWebProperties properties) {
        return new InMemoryCocoReplayStore(properties.getReplay());
    }

    /**
     * <p>
     * 创建 Coco Web 防重放请求形态预检过滤器注册器。
     * </p>
     * @param properties Coco Web 配置属性
     * @param replayKeyResolver 防重放键解析器
     * @param requestContextResolver Web 请求上下文解析器
     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @param requestMatcher Web 请求匹配器
     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @return 防重放请求形态预检过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.replay", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoReplayRequestShapeFilterRegistration")
    public FilterRegistrationBean<CocoReplayRequestShapeFilter> cocoReplayRequestShapeFilterRegistration(
            CocoWebProperties properties, CocoReplayKeyResolver replayKeyResolver,
            CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver,
            CocoWebRequestMatcher requestMatcher,
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        FilterRegistrationBean<CocoReplayRequestShapeFilter> registration = new FilterRegistrationBean<>(
                new CocoReplayRequestShapeFilter(properties.getReplay(), replayKeyResolver, requestContextResolver,
                        securityMetadataResolver, exceptionResponseWriter, requestMatcher));
        registration.setName("cocoReplayRequestShapeFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        return registration;
    }

    /**
     * <p>
     * 创建 Coco Web 防重放过滤器注册器�?     * </p>
     * @param properties Coco Web 配置属�?     * @param replayStore 防重放存�?     * @param replayKeyResolver 防重放键解析�?     * @param requestContextResolver Web 请求上下文解析器
     * @param securityMetadataResolver Web 请求安全元数据解析器
     * @param requestMatcher Web 请求匹配�?     * @param exceptionResponseWriter 过滤器异常响应写出器
     * @return 防重放过滤器注册�?     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.replay", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoReplayFilterRegistration")
    public FilterRegistrationBean<CocoReplayFilter> cocoReplayFilterRegistration(CocoWebProperties properties,
            CocoReplayStore replayStore, CocoReplayKeyResolver replayKeyResolver,
            CocoWebRequestContextResolver requestContextResolver,
            CocoWebRequestSecurityMetadataResolver securityMetadataResolver,
            CocoWebRequestMatcher requestMatcher,
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        FilterRegistrationBean<CocoReplayFilter> registration = new FilterRegistrationBean<>(
                new CocoReplayFilter(properties.getReplay(), replayStore, replayKeyResolver, requestContextResolver,
                        securityMetadataResolver, exceptionResponseWriter, requestMatcher, null));
        registration.setName("cocoReplayFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return registration;
    }
}
