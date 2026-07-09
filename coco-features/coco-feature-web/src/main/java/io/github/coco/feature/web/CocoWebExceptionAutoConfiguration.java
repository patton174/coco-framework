package io.github.coco.feature.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.common.CocoCommonProperties;
import io.github.coco.common.i18n.api.CocoMessageService;
import io.github.coco.common.logging.core.CocoLogManager;
import io.github.coco.feature.web.exception.CocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.exception.CocoFilterExceptionResponseWriter;
import io.github.coco.feature.web.exception.CocoWebExceptionHandler;
import io.github.coco.feature.web.exception.DefaultCocoExceptionHttpStatusResolver;
import io.github.coco.feature.web.response.CocoResponseBodyFactory;
import io.github.coco.feature.web.response.CocoSystemCodeProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco Web 异常处理自动配置。
 * <p>
 * 注册统一异常响应处理器和过滤器异常响应写出器。
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
public class CocoWebExceptionAutoConfiguration {

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
     * @param codeProvider 系统响应码提供器
     * @param commonProperties Coco 通用配置属性
     * @param properties Coco Web 配置属性
     * @param responseBodyFactory 响应体工厂
     * @param logManager 日志管理器提供器
     * @return Coco Web 全局异常处理器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoWebExceptionHandler cocoWebExceptionHandler(CocoMessageService messageService,
            CocoExceptionHttpStatusResolver httpStatusResolver, CocoSystemCodeProvider codeProvider,
            CocoCommonProperties commonProperties, CocoWebProperties properties,
            CocoResponseBodyFactory responseBodyFactory,
            ObjectProvider<CocoLogManager> logManager) {
        return new CocoWebExceptionHandler(messageService, httpStatusResolver, codeProvider,
                properties.getResponse(), properties.getTrace(), responseBodyFactory, logManager.getIfAvailable(),
                commonProperties.getI18n().getDefaultLocale());
    }

    /**
     * <p>
     * 创建 Coco 过滤器异常响应写出器。
     * </p>
     * @param exceptionHandler Coco Web 全局异常处理器
     * @param objectMapper JSON 序列化器提供器
     * @return 过滤器异常响应写出器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoFilterExceptionResponseWriter cocoFilterExceptionResponseWriter(
            CocoWebExceptionHandler exceptionHandler, ObjectProvider<ObjectMapper> objectMapper) {
        return new CocoFilterExceptionResponseWriter(exceptionHandler, objectMapper.getIfAvailable(ObjectMapper::new));
    }
}
