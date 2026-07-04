package io.github.coco.api;

import io.github.coco.api.feature.CocoFeatureRegistry;

/**
 * # Coco 配置入口
 *
 * - **作者**: [patton174](https://github.com/patton174)
 * - **仓库**: [coco-framework](https://github.com/patton174/coco-framework)
 * - **模块**: `coco-api`
 *
 * 业务项目通过实现该接口，以接近 Spring `WebMvcConfigurer` 的方式配置 Coco。
 *
 * @author patton174
 * @since 1.0.0
 */
public interface CocoConfigurer {

    default void configureFeatures(CocoFeatureRegistry features) {
    }
}
