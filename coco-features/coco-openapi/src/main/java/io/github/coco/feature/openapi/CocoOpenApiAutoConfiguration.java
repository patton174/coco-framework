package io.github.coco.feature.openapi;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;
import io.github.coco.feature.openapi.core.DefaultCocoOpenApiMetadataProvider;
import io.github.coco.feature.openapi.springdoc.CocoSpringDocOpenApiCustomizerFactoryBean;
import io.github.coco.feature.openapi.springdoc.SpringDocOpenApiCustomizerCondition;
import io.github.coco.feature.openapi.springdoc.SpringDocOpenApiRuntimeHints;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.ImportRuntimeHints;

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
 *   <li>模块：{@code coco-openapi}</li>
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
@ImportRuntimeHints(SpringDocOpenApiRuntimeHints.class)
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

    /**
     * <p>
     * 当业务项目引入 SpringDoc 时，注册 OpenAPI 元数据适配器。
     * </p>
     * @param metadataProvider Coco OpenAPI 元数据提供器
     * @return SpringDoc OpenAPI 定制器工厂
     */
    @Bean(name = "cocoSpringDocOpenApiCustomizer")
    @ConditionalOnBean(CocoOpenApiMetadataProvider.class)
    @ConditionalOnMissingBean(name = "cocoSpringDocOpenApiCustomizer")
    @Conditional(SpringDocOpenApiCustomizerCondition.class)
    @ConditionalOnProperty(prefix = "coco.openapi.springdoc", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public CocoSpringDocOpenApiCustomizerFactoryBean cocoSpringDocOpenApiCustomizer(
            CocoOpenApiMetadataProvider metadataProvider, CocoOpenApiProperties properties) {
        return springDocOpenApiCustomizer(metadataProvider,
                properties.getSpringdoc().isResponseSchemasEnabled());
    }

    /**
     * <p>
     * 创建默认 SpringDoc OpenAPI 定制器。
     * </p>
     * <p>
     * 保留该公开重载以兼容已编译的业务配置代码；通过此入口创建的定制器沿用历史默认行为并发布响应组件模型。
     * </p>
     * @param metadataProvider Coco OpenAPI 元数据提供器
     * @return SpringDoc OpenAPI 定制器工厂
     */
    public CocoSpringDocOpenApiCustomizerFactoryBean cocoSpringDocOpenApiCustomizer(
            CocoOpenApiMetadataProvider metadataProvider) {
        return springDocOpenApiCustomizer(metadataProvider, true);
    }

    private CocoSpringDocOpenApiCustomizerFactoryBean springDocOpenApiCustomizer(
            CocoOpenApiMetadataProvider metadataProvider, boolean responseSchemasEnabled) {
        return new CocoSpringDocOpenApiCustomizerFactoryBean(metadataProvider, responseSchemasEnabled);
    }
}
