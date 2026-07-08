package io.github.coco.feature.registry;

import java.util.Objects;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;

/**
 * Coco 功能定义。
 * <p>
 * 描述一个标准功能的构建期元数据，包括模块坐标、默认状态、依赖关系和禁用时可裁剪 artifact。
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
        String autoConfigurationClassName,
        boolean defaultEnabled,
        Set<CocoFeature> dependencies,
        Set<String> pruneArtifactIds) {

    /**
     * <p>
     * 创建标准功能定义，并默认只裁剪功能模块自身 artifactId。
     * </p>
     * @param feature 功能枚举
     * @param artifactId 功能模块 Maven artifactId
     * @param autoConfigurationClassName 功能自动配置类全限定名
     * @param defaultEnabled 是否默认启用
     * @param dependencies 功能依赖集合
     */
    public CocoFeatureDefinition(CocoFeature feature, String artifactId, String autoConfigurationClassName,
            boolean defaultEnabled, Set<CocoFeature> dependencies) {
        this(feature, artifactId, autoConfigurationClassName, defaultEnabled, dependencies, Set.of(artifactId));
    }

    /**
     * <p>
     * 创建标准功能定义，并复制依赖集合和裁剪 artifactId 集合。
     * </p>
     * @param feature 功能枚举
     * @param artifactId 功能模块 Maven artifactId
     * @param autoConfigurationClassName 功能自动配置类全限定名
     * @param defaultEnabled 是否默认启用
     * @param dependencies 功能依赖集合
     * @param pruneArtifactIds 功能禁用时需要从可执行包中移除的 artifactId 集合
     */
    public CocoFeatureDefinition {
        Objects.requireNonNull(feature, "feature must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(autoConfigurationClassName, "autoConfigurationClassName must not be null");
        dependencies = Set.copyOf(Objects.requireNonNull(dependencies, "dependencies must not be null"));
        pruneArtifactIds = Set.copyOf(Objects.requireNonNull(pruneArtifactIds, "pruneArtifactIds must not be null"));
    }
}
