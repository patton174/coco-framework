package io.github.coco.feature.datapermission;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.common.i18n.api.CocoMessageBundleRegistrar;
import io.github.coco.feature.datapermission.context.CocoDataPermissionContextResolver;
import io.github.coco.feature.datapermission.context.HolderCocoDataPermissionContextResolver;
import io.github.coco.feature.runtime.condition.ConditionalOnCocoFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco 数据权限功能自动配置。
 * <p>
 * 负责为数据权限功能模块注册国际化消息资源，后续数据范围和 SQL 权限条件提示都从该资源包扩展。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnCocoFeature(CocoFeature.DATA_PERMISSION)
@EnableConfigurationProperties(CocoDataPermissionProperties.class)
public class CocoDataPermissionAutoConfiguration {

    /**
     * <p>
     * 注册数据权限功能模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoDataPermissionMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoDataPermissionMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-data-permission-messages");
    }

    /**
     * <p>
     * 创建默认数据权限上下文解析器。
     * </p>
     * @return 数据权限上下文解析器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoDataPermissionContextResolver cocoDataPermissionContextResolver() {
        return new HolderCocoDataPermissionContextResolver();
    }
}
