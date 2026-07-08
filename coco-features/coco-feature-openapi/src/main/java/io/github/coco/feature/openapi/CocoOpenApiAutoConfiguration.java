package io.github.coco.feature.openapi;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.common.i18n.api.CocoMessageBundleRegistrar;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;
import io.github.coco.feature.openapi.core.DefaultCocoOpenApiMetadataProvider;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco OpenAPI 功能自动配置。
 * <p>
 * 负责为 OpenAPI 功能模块注册国际化消息资源、配置属性和文档元数据提供器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-openapi}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(afterName = {
        "io.github.coco.feature.web.CocoWebAutoConfiguration",
        "io.github.coco.feature.security.CocoSecurityAutoConfiguration"
})
@ConditionalOnCocoFeature(CocoFeature.OPENAPI)
@EnableConfigurationProperties(CocoOpenApiProperties.class)
public class CocoOpenApiAutoConfiguration {

    /**
     * <p>
     * 注册 OpenAPI 功能模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoOpenApiMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoOpenApiMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-openapi-messages");
    }

    /**
     * <p>
     * 创建默认 OpenAPI 元数据提供器。
     * </p>
     * @param properties OpenAPI 配置属性
     * @return OpenAPI 元数据提供器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "coco.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CocoOpenApiMetadataProvider cocoOpenApiMetadataProvider(CocoOpenApiProperties properties) {
        return new DefaultCocoOpenApiMetadataProvider(properties);
    }
}
