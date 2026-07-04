package io.github.coco.sample.basic;

import io.github.coco.api.CocoConfigurer;
import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.CocoFeatureRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * Coco 示例配置类。
 * <p>
 * 展示类似 {@code WebMvcConfigurer} 的强类型配置方式，示例中排除租户和数据权限能力。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-sample-basic}</li>
 * </ul>
 * <p>
 * 代码注释采用标准 JavaDoc HTML 标签，不使用 Markdown 语法。
 * </p>
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
