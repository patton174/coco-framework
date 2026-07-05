package io.github.coco.feature.registry;

import io.github.coco.common.i18n.api.CocoMessageBundleRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Coco 功能注册表自动配置。
 * <p>
 * 为功能注册表模块注册国际化消息资源，不改变标准功能元数据的解析行为。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-registry}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration
public class CocoFeatureRegistryAutoConfiguration {

    /**
     * <p>
     * 注册功能注册表模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoFeatureRegistryMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoFeatureRegistryMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-registry-messages");
    }
}
