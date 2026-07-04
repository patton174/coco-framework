package io.github.coco.api.feature;

import java.util.Set;

/**
 * Coco 功能注册配置。
 * <p>
 * 提供给 {@code CocoConfigurer} 使用，用于声明业务项目需要排除的框架能力。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-api-core}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public interface CocoFeatureRegistry {

    CocoFeatureRegistry include(CocoFeature... features);

    CocoFeatureRegistry exclude(CocoFeature... features);

    default CocoFeatureRegistry enable(CocoFeature... features) {
        return include(features);
    }

    default CocoFeatureRegistry disable(CocoFeature... features) {
        return exclude(features);
    }

    boolean isIncluded(CocoFeature feature);

    boolean isExcluded(CocoFeature feature);

    Set<CocoFeature> includedFeatures();

    Set<CocoFeature> excludedFeatures();
}
