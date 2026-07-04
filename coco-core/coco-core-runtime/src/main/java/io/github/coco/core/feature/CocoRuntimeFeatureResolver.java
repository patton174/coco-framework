package io.github.coco.core.feature;

import java.util.LinkedHashSet;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.registry.CocoFeatureManifestLoader;
import io.github.coco.feature.registry.CocoFeaturePlan;
import io.github.coco.feature.registry.CocoFeatureSelection;
import io.github.coco.feature.registry.StandardCocoFeatures;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * Coco 运行期功能解析器。
 * <p>
 * 优先读取构建期生成的功能清单；当清单不存在时，退回到运行期环境配置计算功能状态。
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
public final class CocoRuntimeFeatureResolver {

    public CocoFeaturePlan resolve(Environment environment, ClassLoader classLoader) {
        return CocoFeatureManifestLoader.load(classLoader)
                .map(StandardCocoFeatures::fromManifest)
                .orElseGet(() -> resolveFromEnvironment(environment));
    }

    private CocoFeaturePlan resolveFromEnvironment(Environment environment) {
        if (environment == null) {
            return StandardCocoFeatures.resolve(CocoFeatureSelection.empty());
        }
        Set<CocoFeature> disabled = new LinkedHashSet<>(bind(environment, "coco.features.disabled"));
        disabled.addAll(bind(environment, "coco.features.exclude"));
        return StandardCocoFeatures.resolve(new CocoFeatureSelection(
                bind(environment, "coco.features.enabled"),
                disabled));
    }

    private Set<CocoFeature> bind(Environment environment, String propertyName) {
        return Binder.get(environment)
                .bind(propertyName, Bindable.setOf(CocoFeature.class))
                .orElse(Set.of());
    }
}
