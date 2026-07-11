package io.github.coco.spring.boot.autoconfigure.i18n;

import io.github.coco.CocoCommonProperties;
import io.github.coco.i18n.CocoLocaleResolver;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.i18n.CocoMessageService;
import io.github.coco.i18n.internal.DefaultCocoLocaleResolver;
import io.github.coco.i18n.internal.DefaultCocoMessageBundleRegistry;
import io.github.coco.i18n.internal.DefaultCocoMessageService;
import org.springframework.beans.factory.ObjectProvider;
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
 *   <li>模块：{@code coco-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoCommonProperties.class)
public class CocoI18nAutoConfiguration {

    /**
     * <p>
     * 创建 Coco 专用消息源。
     * </p>
     * <p>
     * 该消息源只服务 Coco 框架消息解析，不覆盖业务应用自己的 Spring {@code messageSource}。
     * </p>
     * @param properties Coco 通用配置
     * @param registrars 模块消息资源注册器集合
     * @return Coco 专用消息源
     */
    @Bean("cocoMessageSource")
    @ConditionalOnMissingBean(name = "cocoMessageSource")
    public MessageSource cocoMessageSource(CocoCommonProperties properties,
            ObjectProvider<CocoMessageBundleRegistrar> registrars) {
        DefaultCocoMessageBundleRegistry registry = new DefaultCocoMessageBundleRegistry();
        properties.getI18n().getBasename().stream()
                .filter(basename -> !"coco-messages".equals(basename))
                .forEach(registry::add);
        registrars.orderedStream().forEach(registrar -> registrar.registerBundles(registry));
        registry.add("coco-messages");

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames(registry.basenames().toArray(String[]::new));
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(properties.getI18n().isFallbackToSystemLocale());
        return messageSource;
    }

    /**
     * <p>
     * 创建 Coco 语言解析器。
     * </p>
     * @param properties Coco 通用配置
     * @return Coco 语言解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoLocaleResolver cocoLocaleResolver(CocoCommonProperties properties) {
        return new DefaultCocoLocaleResolver(properties.getI18n());
    }

    /**
     * <p>
     * 创建 Coco 消息服务。
     * </p>
     * @param messageSource Coco 专用消息源
     * @param localeResolver Coco 语言解析器
     * @param properties Coco 通用配置
     * @return Coco 消息服务
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoMessageService cocoMessageService(@Qualifier("cocoMessageSource") MessageSource messageSource,
            CocoLocaleResolver localeResolver, CocoCommonProperties properties) {
        return new DefaultCocoMessageService(messageSource, localeResolver,
                properties.getI18n().isUseCodeAsDefaultMessage());
    }
}
