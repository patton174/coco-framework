package io.github.coco.feature.codegen;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.i18n.CocoMessageBundleRegistrar;
import io.github.coco.feature.codegen.core.CocoCodeGenerator;
import io.github.coco.feature.codegen.core.CocoGeneratedFileWriter;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.codegen.template.FreeMarkerCocoCodeGenerator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco 代码生成功能自动配置。
 * <p>
 * 负责为代码生成功能模块注册国际化消息资源、配置属性和代码生成器 SPI 默认实现。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-codegen}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration(afterName = "io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration")
@ConditionalOnCocoFeature(CocoFeature.CODEGEN)
@EnableConfigurationProperties(CocoCodegenProperties.class)
public class CocoCodegenAutoConfiguration {

    /**
     * <p>
     * 注册代码生成功能模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoCodegenMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoCodegenMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-codegen-messages");
    }

    /**
     * <p>
     * 创建默认代码生成器。
     * </p>
     * @param properties 代码生成配置属性
     * @return FreeMarker 代码生成器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "coco.codegen", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CocoCodeGenerator cocoCodeGenerator(CocoCodegenProperties properties) {
        CocoCodegenProperties.TemplateProperties templates = properties.getTemplates();
        return new FreeMarkerCocoCodeGenerator(templates.getLocation(), templates.getEncoding());
    }

    /**
     * <p>
     * 创建显式生成文件写入器。
     * </p>
     * @param properties 代码生成配置属性
     * @return 生成文件写入器
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "coco.codegen", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CocoGeneratedFileWriter cocoGeneratedFileWriter(CocoCodegenProperties properties) {
        return new CocoGeneratedFileWriter(properties.getTemplates().getEncoding());
    }
}
