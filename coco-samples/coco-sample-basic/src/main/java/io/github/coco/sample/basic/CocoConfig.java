package io.github.coco.sample.basic;

import io.github.coco.api.CocoConfigurer;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.CocoFeatureRegistry;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CocoConfig implements CocoConfigurer {

    @Override
    public void configureFeatures(CocoFeatureRegistry features) {
        features.exclude(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION);
    }
}
