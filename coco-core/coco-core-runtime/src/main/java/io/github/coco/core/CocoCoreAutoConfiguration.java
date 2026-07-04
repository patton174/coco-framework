package io.github.coco.core;

import io.github.coco.common.i18n.CocoMessageBundleRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Coco 核心运行时自动配置。
 * <p>
 * 负责为核心运行时模块注册框架内部消息资源，供后续核心基础设施、异常和提示统一使用。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-core-runtime}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration
public class CocoCoreAutoConfiguration {

    /**
     * <p>
     * 注册核心运行时模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoCoreRuntimeMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoCoreRuntimeMessageBundleRegistrar() {
        return registry -> registry.add("coco-core-runtime-messages");
    }
}
