package io.github.coco.feature.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.common.i18n.api.CocoMessageBundleRegistrar;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.core.feature.ConditionalOnCocoFeature;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.exception.DefaultCocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.response.CocoResponseWrapAdvice;
import io.github.coco.feature.web.trace.CocoTraceFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Coco Web 功能自动配置。
 * <p>
 * 负责为 Web 功能模块注册国际化消息资源、统一响应包装处理器、统一异常响应处理器、异常 HTTP 状态解析器和 Trace 过滤器。
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
@AutoConfiguration
@ConditionalOnCocoFeature(CocoFeature.WEB)
@EnableConfigurationProperties(CocoWebProperties.class)
public class CocoWebAutoConfiguration {

    /**
     * <p>
     * 注册 Web 功能模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoWebMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoWebMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-web-messages");
    }

    /**
     * <p>
     * 创建默认 Coco 异常 HTTP 状态解析器。
     * </p>
     * @return 异常 HTTP 状态解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoExceptionHttpStatusResolver cocoExceptionHttpStatusResolver() {
        return new DefaultCocoExceptionHttpStatusResolver();
    }

    /**
     * <p>
     * 创建 Coco Web 全局异常处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param httpStatusResolver 异常 HTTP 状态解析器
     * @return Coco Web 全局异常处理器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoWebExceptionHandler cocoWebExceptionHandler(CocoMessageService messageService,
            CocoExceptionHttpStatusResolver httpStatusResolver) {
        return new CocoWebExceptionHandler(messageService, httpStatusResolver);
    }

    /**
     * <p>
     * 创建 Coco Web 正常响应包装处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param properties Coco Web 配置属性
     * @param objectMapper JSON 序列化器提供器
     * @return Coco Web 正常响应包装处理器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.response-wrap", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    public CocoResponseWrapAdvice cocoResponseWrapAdvice(CocoMessageService messageService,
            CocoWebProperties properties, ObjectProvider<ObjectMapper> objectMapper) {
        return new CocoResponseWrapAdvice(messageService, properties.getResponseWrap(),
                objectMapper.getIfAvailable(ObjectMapper::new));
    }

    /**
     * <p>
     * 创建 Coco Trace 过滤器注册器。
     * </p>
     * @param properties Coco Web 配置属性
     * @return Trace 过滤器注册器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cocoTraceFilterRegistration")
    public FilterRegistrationBean<CocoTraceFilter> cocoTraceFilterRegistration(CocoWebProperties properties) {
        FilterRegistrationBean<CocoTraceFilter> registration = new FilterRegistrationBean<>(
                new CocoTraceFilter(properties.getTrace()));
        registration.setName("cocoTraceFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
