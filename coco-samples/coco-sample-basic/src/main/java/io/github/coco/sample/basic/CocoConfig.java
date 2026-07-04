package io.github.coco.sample.basic;

import io.github.coco.api.CocoConfigurer;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.CocoFeatureRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * # Coco 示例配置类
 *
 * - **作者**: [patton174](https://github.com/patton174)
 * - **仓库**: [coco-framework](https://github.com/patton174/coco-framework)
 * - **模块**: `coco-sample-basic`
 *
 * 展示类似 `WebMvcConfigurer` 的强类型配置方式，示例中排除租户和数据权限能力。
 *
 * @author patton174
 * @since 1.0.0
 */
@Configuration(proxyBeanMethods = false)
public class CocoConfig implements CocoConfigurer {

    @Override
    public void configureFeatures(CocoFeatureRegistry features) {
        features.exclude(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION);
    }
}
