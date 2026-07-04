package io.github.coco.api.feature;

import java.util.Set;

/**
 * # Coco 功能注册配置
 *
 * - **作者**: [patton174](https://github.com/patton174)
 * - **仓库**: [coco-framework](https://github.com/patton174/coco-framework)
 * - **模块**: `coco-api`
 *
 * 提供给 `CocoConfigurer` 使用，用于声明业务项目需要排除的框架能力。
 *
 * @author patton174
 * @since 1.0.0
 */
public interface CocoFeatureRegistry {

    CocoFeatureRegistry exclude(CocoFeature... features);

    boolean isExcluded(CocoFeature feature);

    Set<CocoFeature> excludedFeatures();
}
