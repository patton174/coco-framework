package io.github.coco.config;

import io.github.coco.api.CocoConfigurer;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.DefaultCocoFeatureRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Coco 配置自动装配。
 * <p>
 * 负责绑定 {@code coco} 配置，并合并业务侧提供的 {@code CocoConfigurer} Bean，生成运行期功能管理器。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-config}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(CocoProperties.class)
public class CocoConfigAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CocoFeatureManager cocoFeatureManager(CocoProperties properties,
            ObjectProvider<CocoConfigurer> configurers) {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();
        registry.exclude(properties.getFeatures().getExclude().toArray(CocoFeature[]::new));
        configurers.orderedStream().forEach(configurer -> configurer.configureFeatures(registry));
        return new DefaultCocoFeatureManager(registry.excludedFeatures());
    }
}
