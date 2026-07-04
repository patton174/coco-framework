package io.github.coco.config;

import java.util.EnumSet;

import io.github.coco.api.CocoConfigurer;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.CocoFeatures;
import io.github.coco.api.feature.DefaultCocoFeatureRegistry;
import io.github.coco.feature.registry.CocoFeatureSelection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Coco 功能选择收集器。
 * <p>
 * 从业务方提供的 {@code CocoConfigurer} Bean 和 {@code @CocoFeatures} 配置类中收集代码级功能声明。
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
final class CocoFeatureSelectionCollector {

    private CocoFeatureSelectionCollector() {
    }

    static CocoFeatureSelection collect(ConfigurableListableBeanFactory beanFactory,
            ObjectProvider<CocoConfigurer> configurers) {
        EnumSet<CocoFeature> enabled = EnumSet.noneOf(CocoFeature.class);
        EnumSet<CocoFeature> disabled = EnumSet.noneOf(CocoFeature.class);

        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();
        configurers.orderedStream().forEach(configurer -> configurer.configureFeatures(registry));
        enabled.addAll(registry.includedFeatures());
        disabled.addAll(registry.excludedFeatures());

        if (beanFactory != null) {
            for (String beanName : beanFactory.getBeanNamesForAnnotation(CocoFeatures.class)) {
                CocoFeatures features = beanFactory.findAnnotationOnBean(beanName, CocoFeatures.class);
                if (features != null) {
                    addAll(enabled, features.enabled());
                    addAll(disabled, features.disabled());
                }
            }
        }

        return new CocoFeatureSelection(enabled, disabled);
    }

    private static void addAll(EnumSet<CocoFeature> target, CocoFeature[] features) {
        if (features == null) {
            return;
        }
        for (CocoFeature feature : features) {
            if (feature != null) {
                target.add(feature);
            }
        }
    }
}
