package io.github.coco.feature.runtime.condition;

import java.util.LinkedHashSet;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.feature.model.CocoFeatureManifestLoader;
import io.github.coco.feature.model.CocoFeaturePlan;
import io.github.coco.feature.model.CocoFeatureSelection;
import io.github.coco.feature.model.StandardCocoFeatures;
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
 *   <li>模块：{@code coco-spring-boot-autoconfigure}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoRuntimeFeatureResolver {

    /**
     * <p>
     * 解析当前应用运行期可见的最终功能启用计划。
     * </p>
     * <p>
     * 方法会优先读取构建期清单；当清单不存在时，再从 Spring 环境中的
     * {@code coco.features.enabled} 和 {@code coco.features.disabled} 解析。
     * </p>
     * @param environment Spring 环境
     * @param classLoader 用于读取构建期清单的类加载器
     * @return 最终功能启用计划
     */
    public CocoFeaturePlan resolve(Environment environment, ClassLoader classLoader) {
        return CocoFeatureManifestLoader.load(classLoader)
                .map(StandardCocoFeatures::fromManifest)
                .orElseGet(() -> resolveFromEnvironment(environment));
    }

    private CocoFeaturePlan resolveFromEnvironment(Environment environment) {
        if (environment == null) {
            return StandardCocoFeatures.resolve(CocoFeatureSelection.empty());
        }
        return StandardCocoFeatures.resolve(CocoFeatureSelection.of(
                bind(environment, "coco.features.enabled"),
                new LinkedHashSet<>(bind(environment, "coco.features.disabled"))));
    }

    private Set<CocoFeature> bind(Environment environment, String propertyName) {
        return Binder.get(environment)
                .bind(propertyName, Bindable.setOf(CocoFeature.class))
                .orElse(Set.of());
    }
}
