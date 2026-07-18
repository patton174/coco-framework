package io.github.coco.feature.web;

import io.github.coco.logging.access.CocoAccessLogRecorder;
import io.github.coco.feature.web.context.CocoWebRequestContextResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.trace.CocoTraceFilter;
import io.github.coco.feature.web.trace.CocoTraceIdValidator;
import io.github.coco.feature.web.trace.DefaultCocoTraceIdValidator;
import jakarta.servlet.DispatcherType;
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
     * @param exceptionResponseWriter 过滤器异常响应写出器提供器
     * @return Trace 过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoTraceFilterRegistration")
    public FilterRegistrationBean<CocoTraceFilter> cocoTraceFilterRegistration(CocoWebProperties properties,
            ObjectProvider<CocoAccessLogRecorder> accessLogRecorders,
            CocoWebRequestContextResolver requestContextResolver,
            CocoTraceIdValidator traceIdValidator,
            ObjectProvider<CocoFilterExceptionResponseWriter> exceptionResponseWriter) {
        FilterRegistrationBean<CocoTraceFilter> registration = createTraceFilterRegistration(properties,
                accessLogRecorders, requestContextResolver, traceIdValidator,
                exceptionResponseWriter.getIfAvailable());
        registration.setAsyncSupported(true);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        return registration;
    }

    /**
     * <p>
     * 使用 2.0.1 参数列表创建 Trace 过滤器注册器。
     * </p>
     * @param properties Coco Web 配置属性
     * @param accessLogRecorders 访问日志记录器提供器
     * @param requestContextResolver Web 请求上下文解析器
     * @param traceIdValidator TraceId 校验器
     * @return Trace 过滤器注册器
     */
    @Deprecated(since = "2.0.2", forRemoval = false)
    public FilterRegistrationBean<CocoTraceFilter> cocoTraceFilterRegistration(CocoWebProperties properties,
            ObjectProvider<CocoAccessLogRecorder> accessLogRecorders,
            CocoWebRequestContextResolver requestContextResolver,
            CocoTraceIdValidator traceIdValidator) {
        return createTraceFilterRegistration(properties, accessLogRecorders, requestContextResolver, traceIdValidator,
                null);
    }

    private static FilterRegistrationBean<CocoTraceFilter> createTraceFilterRegistration(CocoWebProperties properties,
            ObjectProvider<CocoAccessLogRecorder> accessLogRecorders,
            CocoWebRequestContextResolver requestContextResolver,
            CocoTraceIdValidator traceIdValidator,
            CocoFilterExceptionResponseWriter exceptionResponseWriter) {
        FilterRegistrationBean<CocoTraceFilter> registration = new FilterRegistrationBean<>(
                new CocoTraceFilter(properties.getTrace(), accessLogRecorders.orderedStream().toList(),
                        properties.getAccessLog(), requestContextResolver, traceIdValidator,
                        exceptionResponseWriter));
        registration.setName("cocoTraceFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
