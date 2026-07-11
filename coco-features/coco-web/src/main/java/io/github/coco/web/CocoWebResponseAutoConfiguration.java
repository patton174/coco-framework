package io.github.coco.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.web.response.CocoResponseBodyFactory;
import io.github.coco.web.response.CocoResponseWrapAdvice;
import io.github.coco.web.response.CocoSystemCodeProvider;
import io.github.coco.web.response.CocoSystemCodes;
import io.github.coco.web.response.DefaultCocoResponseBodyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco Web 响应自动配置。
 * <p>
 * 注册统一响应码、响应体工厂和正常响应包装处理器。
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
public class CocoWebResponseAutoConfiguration {

    /**
     * <p>
     * 创建默认系统响应码提供器。
     * </p>
     * @return 系统响应码提供器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoSystemCodeProvider cocoSystemCodeProvider() {
        return CocoSystemCodes.defaults();
    }

    /**
     * <p>
     * 创建默认 Coco 响应体工厂。
     * </p>
     * @return Coco 响应体工厂
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoResponseBodyFactory cocoResponseBodyFactory() {
        return new DefaultCocoResponseBodyFactory();
    }

    /**
     * <p>
     * 创建 Coco Web 正常响应包装处理器。
     * </p>
     * @param messageService Coco 消息服务
     * @param properties Coco Web 配置属性
     * @param codeProvider 系统响应码提供器
     * @param objectMapper JSON 序列化器提供器
     * @param responseBodyFactory 响应体工厂
     * @return Coco Web 正常响应包装处理器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "coco.web.response-wrap", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean
    public CocoResponseWrapAdvice cocoResponseWrapAdvice(CocoMessageService messageService,
            CocoWebProperties properties, CocoSystemCodeProvider codeProvider, ObjectProvider<ObjectMapper> objectMapper,
            CocoResponseBodyFactory responseBodyFactory) {
        return new CocoResponseWrapAdvice(messageService, properties.getResponseWrap(),
                codeProvider, objectMapper.getIfAvailable(ObjectMapper::new), properties.getResponse(),
                properties.getTrace(), responseBodyFactory);
    }
}
