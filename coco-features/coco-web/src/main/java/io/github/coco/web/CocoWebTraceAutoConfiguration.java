package io.github.coco.web;

import io.github.coco.logging.access.CocoAccessLogRecorder;
import io.github.coco.web.context.CocoWebRequestContextResolver;
import io.github.coco.web.trace.CocoTraceFilter;
import io.github.coco.web.trace.CocoTraceIdValidator;
import io.github.coco.web.trace.DefaultCocoTraceIdValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Coco Web Trace 自动配置。
 * <p>
 * 注册 TraceId 校验器和 Trace 过滤器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
public class CocoWebTraceAutoConfiguration {

    /**
     * <p>
     * 创建默认 TraceId 校验器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return TraceId 校验器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoTraceIdValidator cocoTraceIdValidator(CocoWebProperties properties) {
        return new DefaultCocoTraceIdValidator(properties.getTrace());
    }

    /**
     * <p>
     * 创建 Coco Trace 过滤器注册器。
     * </p>
     * @param properties Coco Web 配置属性
     * @param accessLogRecorders 访问日志记录器提供器
     * @param requestContextResolver Web 请求上下文解析器
     * @param traceIdValidator TraceId 校验器
     * @return Trace 过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoTraceFilterRegistration")
    public FilterRegistrationBean<CocoTraceFilter> cocoTraceFilterRegistration(CocoWebProperties properties,
            ObjectProvider<CocoAccessLogRecorder> accessLogRecorders,
            CocoWebRequestContextResolver requestContextResolver,
            CocoTraceIdValidator traceIdValidator) {
        FilterRegistrationBean<CocoTraceFilter> registration = new FilterRegistrationBean<>(
                new CocoTraceFilter(properties.getTrace(), accessLogRecorders.orderedStream().toList(),
                        properties.getAccessLog(), requestContextResolver, traceIdValidator));
        registration.setName("cocoTraceFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
