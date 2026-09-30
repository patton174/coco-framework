package io.github.coco.compatibility.openapi;

import io.github.coco.feature.openapi.CocoOpenApiAutoConfiguration;
import io.github.coco.feature.openapi.CocoOpenApiFeature;
import io.github.coco.feature.openapi.CocoOpenApiProperties;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadata;
import io.github.coco.feature.openapi.core.CocoOpenApiMetadataProvider;
import io.github.coco.feature.openapi.core.DefaultCocoOpenApiMetadataProvider;
import io.github.coco.feature.openapi.springdoc.CocoSpringDocOpenApiCustomizerFactoryBean;

/**
 * {@code coco-feature-openapi} 兼容门面编译探针。
 * <p>
 * 以编译期引用的方式钉住旧坐标 {@code coco-feature-openapi} 曾经对外暴露、现由 {@code coco-openapi} 承载的公开类型。
 * 只要业务项目仍通过旧坐标引入依赖，这些类型就必须继续可解析；任何一个类型被移动、改名或降低可见性，
 * 本模块的测试编译都会失败，从而在发布前暴露兼容面的破坏。该类不含测试方法，运行期也不会被加载。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-openapi}</li>
 * </ul>
 * @author patton174
 * @since 3.0.0
 */
final class CocoFeatureOpenAPICompatibilityCompileProbe {

    /** 旧坐标消费者可能直接引用的公开类型；仅用于编译期校验，不会被读取。 */
    private static final Class<?>[] PUBLIC_TYPES = {
            CocoOpenApiAutoConfiguration.class,
            CocoOpenApiFeature.class,
            CocoOpenApiProperties.class,
            CocoOpenApiMetadata.class,
            CocoOpenApiMetadataProvider.class,
            DefaultCocoOpenApiMetadataProvider.class,
            CocoSpringDocOpenApiCustomizerFactoryBean.class
    };

    private CocoFeatureOpenAPICompatibilityCompileProbe() {
    }
}
