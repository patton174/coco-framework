package io.github.coco.feature.registry;

import java.util.List;
import java.util.Objects;

/**
 * Coco 功能清单条目。
 * <p>
 * 描述一个标准功能在构建产物中的最终启用状态和运行期装配坐标。
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
public record CocoFeatureManifestEntry(
        String id,
        String artifactId,
        String autoConfigurationClassName,
        boolean defaultEnabled,
        boolean enabled,
        List<String> dependencies) {

    public CocoFeatureManifestEntry {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(autoConfigurationClassName, "autoConfigurationClassName must not be null");
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies must not be null"));
    }
}
