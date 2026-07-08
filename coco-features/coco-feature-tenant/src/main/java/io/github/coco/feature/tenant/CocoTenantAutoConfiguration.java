package io.github.coco.feature.tenant;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.common.i18n.api.CocoMessageBundleRegistrar;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import io.github.coco.feature.tenant.context.CocoTenantContextResolver;
import io.github.coco.feature.tenant.context.HolderCocoTenantContextResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Coco 租户功能自动配置。
 * <p>
 * 负责为租户功能模块注册国际化消息资源，后续租户识别、隔离和上下文提示都从该资源包扩展。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnCocoFeature(CocoFeature.TENANT)
public class CocoTenantAutoConfiguration {

    /**
     * <p>
     * 注册租户功能模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoTenantMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoTenantMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-tenant-messages");
    }

    /**
     * <p>
     * 创建默认租户上下文解析器。
     * </p>
     * @return 租户上下文解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoTenantContextResolver cocoTenantContextResolver() {
        return new HolderCocoTenantContextResolver();
    }
}
