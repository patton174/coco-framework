package io.github.coco.feature.web;

import io.github.coco.common.i18n.CocoMessageBundleRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Coco Web 功能自动配置。
 * <p>
 * 负责为 Web 功能模块注册国际化消息资源，后续统一响应、异常处理和请求上下文提示都从该资源包扩展。
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
public class CocoWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "cocoWebMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoWebMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-web-messages");
    }
}
