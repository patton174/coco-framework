package io.github.coco.feature.model;

import java.util.List;
import java.util.Objects;

/**
 * Coco 功能清单条目。
 * <p>
 * 描述一个标准功能在构建产物中的最终启用状态、运行期装配坐标和禁用时可裁剪 artifact。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-model}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public record CocoFeatureManifestEntry(
        String id,
        String artifactId,
        String autoConfigurationClassName,
        boolean defaultEnabled,
        boolean enabled,
        List<String> dependencies,
        List<String> pruneArtifactIds) {

    /**
     * <p>
     * 创建功能清单条目，并默认只裁剪功能模块自身 artifactId。
     * </p>
     * @param id 功能稳定标识
     * @param artifactId 功能模块 Maven artifactId
     * @param autoConfigurationClassName 功能自动配置类全限定名
     * @param defaultEnabled 是否默认启用
     * @param enabled 构建后是否最终启用
     * @param dependencies 依赖功能标识列表
     */
    public CocoFeatureManifestEntry(String id, String artifactId, String autoConfigurationClassName,
            boolean defaultEnabled, boolean enabled, List<String> dependencies) {
        this(id, artifactId, autoConfigurationClassName, defaultEnabled, enabled, dependencies, List.of(artifactId));
    }

    /**
     * <p>
     * 创建功能清单条目，并复制依赖标识列表和裁剪 artifactId 列表。
     * </p>
     * @param id 功能稳定标识
     * @param artifactId 功能模块 Maven artifactId
     * @param autoConfigurationClassName 功能自动配置类全限定名
     * @param defaultEnabled 是否默认启用
     * @param enabled 构建后是否最终启用
     * @param dependencies 依赖功能标识列表
     * @param pruneArtifactIds 功能禁用时需要从可执行包中移除的 artifactId 列表
     */
    public CocoFeatureManifestEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(autoConfigurationClassName, "autoConfigurationClassName must not be null");
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies must not be null"));
        pruneArtifactIds = pruneArtifactIds == null ? List.of(artifactId) : List.copyOf(pruneArtifactIds);
    }
}
