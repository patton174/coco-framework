package io.github.coco.compatibility.mybatisplus;

import io.github.coco.feature.mybatisplus.CocoMybatisPlusAutoConfiguration;
import io.github.coco.feature.mybatisplus.CocoMybatisPlusFeature;
import io.github.coco.feature.mybatisplus.interceptor.CocoMybatisPlusInterceptorCustomizer;

final class CocoFeatureMybatisPlusCompatibilityCompileProbe {

    private static final Class<?>[] PUBLIC_TYPES = {
            CocoMybatisPlusFeature.class,
            CocoMybatisPlusAutoConfiguration.class,
            CocoMybatisPlusInterceptorCustomizer.class
    };

    private CocoFeatureMybatisPlusCompatibilityCompileProbe() {
    }
}
