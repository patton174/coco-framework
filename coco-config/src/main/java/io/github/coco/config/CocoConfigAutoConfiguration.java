package io.github.coco.config;

import io.github.coco.api.CocoConfigurer;
import io.github.coco.common.i18n.api.CocoMessageBundleRegistrar;
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
@SuppressWarnings("deprecation")
public class CocoConfigAutoConfiguration {

    /**
     * <p>
     * 创建 Coco 功能启用计划。
     * </p>
     * <p>
     * 优先读取构建期生成的功能清单；清单不存在时，回退到配置文件、{@link CocoConfigurer} 和
     * {@code @CocoFeatures} 声明合并后的运行期解析结果。
     * </p>
     * @param properties Coco 配置属性
     * @param configurers 业务方提供的 Coco 配置器
     * @param beanFactory Spring Bean 工厂，用于查找注解声明
     * @return 最终功能启用计划
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoFeaturePlan cocoFeaturePlan(CocoProperties properties, ObjectProvider<CocoConfigurer> configurers,
            ConfigurableListableBeanFactory beanFactory) {
        return CocoFeatureManifestLoader.load(Thread.currentThread().getContextClassLoader())
                .map(StandardCocoFeatures::fromManifest)
                .orElseGet(() -> {
                    CocoFeatureSelection propertySelection = properties.getFeatures().toSelection();
                    CocoFeatureSelection codeSelection = CocoFeatureSelectionCollector.collect(beanFactory, configurers);
                    return StandardCocoFeatures.resolve(propertySelection.merge(codeSelection));
                });
    }

    /**
     * <p>
     * 基于最终功能启用计划创建运行期功能管理器。
     * </p>
     * @param featurePlan 最终功能启用计划
     * @return 功能管理器
     */
    @Bean
    @ConditionalOnMissingBean
    public CocoFeatureManager cocoFeatureManager(CocoFeaturePlan featurePlan) {
        return new DefaultCocoFeatureManager(featurePlan);
    }

    /**
     * <p>
     * 注册配置模块内置的国际化消息资源。
     * </p>
     * @return 消息资源注册器
     */
    @Bean
    @ConditionalOnMissingBean(name = "cocoConfigMessageBundleRegistrar")
    public CocoMessageBundleRegistrar cocoConfigMessageBundleRegistrar() {
        return registry -> registry.add("coco-config-messages");
    }
}
