package io.github.coco.config;

import io.github.coco.api.CocoConfigurer;
import io.github.coco.common.i18n.CocoMessageBundleRegistrar;
import io.github.coco.feature.registry.CocoFeatureManifestLoader;
import io.github.coco.feature.registry.CocoFeaturePlan;
import io.github.coco.feature.registry.CocoFeatureSelection;
import io.github.coco.feature.registry.StandardCocoFeatures;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
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
    public CocoFeaturePlan cocoFeaturePlan(CocoProperties properties, ObjectProvider<CocoConfigurer> configurers,
            ConfigurableListableBeanFactory beanFactory) {
        return CocoFeatureManifestLoader.load(Thread.currentThread().getContextClassLoader())
                .map(StandardCocoFeatures::fromManifest)
                .orElseGet(() -> {
                    CocoFeatureSelection propertySelection = new CocoFeatureSelection(
                            properties.getFeatures().getEnabled(),
                            properties.getFeatures().disabledFeatures());
                    CocoFeatureSelection codeSelection = CocoFeatureSelectionCollector.collect(beanFactory, configurers);
                    return StandardCocoFeatures.resolve(propertySelection.merge(codeSelection));
                });
    }

    @Bean
    @ConditionalOnMissingBean
    public CocoFeatureManager cocoFeatureManager(CocoFeaturePlan featurePlan) {
        return new DefaultCocoFeatureManager(featurePlan);
    }

    @Bean
    @ConditionalOnMissingBean(name = "cocoConfigMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoConfigMessageBundleRegistrar() {
        return registry -> registry.add("coco-config-messages");
    }
}
