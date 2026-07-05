package io.github.coco.feature.codegen;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.common.i18n.api.CocoMessageBundleRegistrar;
import io.github.coco.core.feature.ConditionalOnCocoFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Coco 代码生成功能自动配置。
 * <p>
 * 负责为代码生成功能模块注册国际化消息资源，后续模板、生成器和校验提示都从该资源包扩展。
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
@AutoConfiguration
@ConditionalOnCocoFeature(CocoFeature.CODEGEN)
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
}
