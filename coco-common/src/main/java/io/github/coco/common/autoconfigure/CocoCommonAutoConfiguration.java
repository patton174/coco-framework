package io.github.coco.common.autoconfigure;

import io.github.coco.common.CocoCommonProperties;
import io.github.coco.common.i18n.CocoLocaleResolver;
import io.github.coco.common.i18n.CocoMessageService;
import io.github.coco.common.i18n.DefaultCocoLocaleResolver;
import io.github.coco.common.i18n.DefaultCocoMessageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Coco 通用基础设施自动装配。
 * <p>
 * 装配 Coco 专用消息源、语言解析器和消息服务，不覆盖业务应用自己的 Spring {@code messageSource}。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoCommonProperties.class)
public class CocoCommonAutoConfiguration {

    @Bean("cocoMessageSource")
    @ConditionalOnMissingBean(name = "cocoMessageSource")
    public MessageSource cocoMessageSource(CocoCommonProperties properties) {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames(properties.getI18n().getBasename().toArray(String[]::new));
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(properties.getI18n().isFallbackToSystemLocale());
        return messageSource;
    }

    @Bean
    @ConditionalOnMissingBean
    public CocoLocaleResolver cocoLocaleResolver(CocoCommonProperties properties) {
        return new DefaultCocoLocaleResolver(properties.getI18n());
    }

    @Bean
    @ConditionalOnMissingBean
    public CocoMessageService cocoMessageService(@Qualifier("cocoMessageSource") MessageSource messageSource,
            CocoLocaleResolver localeResolver, CocoCommonProperties properties) {
        return new DefaultCocoMessageService(messageSource, localeResolver,
                properties.getI18n().isUseCodeAsDefaultMessage());
    }
}
