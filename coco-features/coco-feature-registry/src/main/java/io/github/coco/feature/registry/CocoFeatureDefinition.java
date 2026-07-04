package io.github.coco.feature.registry;

import java.util.Objects;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;

/**
 * Coco 功能定义。
 * <p>
 * 描述一个标准功能的构建期元数据，包括模块坐标、默认状态和依赖关系。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-registry}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public record CocoFeatureDefinition(
        CocoFeature feature,
        String artifactId,
        boolean defaultEnabled,
        Set<CocoFeature> dependencies) {

    public CocoFeatureDefinition {
        Objects.requireNonNull(feature, "feature must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        dependencies = Set.copyOf(Objects.requireNonNull(dependencies, "dependencies must not be null"));
    }
}
