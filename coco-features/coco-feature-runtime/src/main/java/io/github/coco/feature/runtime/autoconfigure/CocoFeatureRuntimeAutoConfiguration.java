package io.github.coco.feature.runtime.autoconfigure;

import io.github.coco.i18n.CocoMessageBundleRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Coco 功能运行时自动配置。
 * <p>
 * 负责注册功能运行时模块的内部消息资源，供功能条件、运行时解析和诊断提示统一使用。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-runtime}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration
public class CocoFeatureRuntimeAutoConfiguration {

    /**
     * <p>
     * 注册功能运行时模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoFeatureRuntimeMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoFeatureRuntimeMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-runtime-messages");
    }
}
