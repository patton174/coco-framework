package io.github.coco.feature.web;

import io.github.coco.CocoCommonProperties;
import io.github.coco.i18n.CocoLocaleResolver;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.feature.web.i18n.CocoWebLocaleResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Coco Web 国际化自动配置。
 * <p>
 * 注册 Web 请求语言解析器和 Web 模块内置消息资源。
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
public class CocoWebI18nAutoConfiguration {

    /**
     * <p>
     * 创建 Servlet Web 请求语言解析器。
     * </p>
     * @param properties Coco 通用配置属性
     * @return Coco Web 请求语言解析器
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean
    public CocoLocaleResolver cocoWebLocaleResolver(CocoCommonProperties properties) {
        return new CocoWebLocaleResolver(properties.getI18n());
    }

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
}
