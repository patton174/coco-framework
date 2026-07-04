package io.github.coco.feature.audit;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.common.i18n.CocoMessageBundleRegistrar;
import io.github.coco.core.feature.ConditionalOnCocoFeature;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Coco 审计功能自动配置。
 * <p>
 * 负责为审计功能模块注册国际化消息资源，后续接口日志和表变更日志提示都从该资源包扩展。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-audit}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnCocoFeature(CocoFeature.AUDIT)
public class CocoAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "cocoAuditMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoAuditMessageBundleRegistrar() {
        return registry -> registry.add("coco-feature-audit-messages");
    }
}
