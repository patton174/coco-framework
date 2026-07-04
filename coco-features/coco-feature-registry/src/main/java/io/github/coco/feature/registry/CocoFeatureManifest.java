package io.github.coco.feature.registry;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Coco 功能构建清单。
 * <p>
 * 由 Maven 插件写入业务应用产物，运行期据此获得与构建期一致的功能启用结果。
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
public record CocoFeatureManifest(String schemaVersion, String generatedBy, List<CocoFeatureManifestEntry> features) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public CocoFeatureManifest {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(generatedBy, "generatedBy must not be null");
        features = List.copyOf(Objects.requireNonNull(features, "features must not be null"));
    }

    public Set<String> enabledFeatureIds() {
        return this.features.stream()
                .filter(CocoFeatureManifestEntry::enabled)
                .map(CocoFeatureManifestEntry::id)
                .collect(Collectors.toUnmodifiableSet());
    }
}
