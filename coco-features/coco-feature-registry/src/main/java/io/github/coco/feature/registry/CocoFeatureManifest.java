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

    public static final String CURRENT_SCHEMA_VERSION = "1.1";

    public static final List<String> SUPPORTED_SCHEMA_VERSIONS = List.of("1.0", CURRENT_SCHEMA_VERSION);

    /**
     * <p>
     * 创建构建期功能清单，并复制功能条目列表。
     * </p>
     * @param schemaVersion 清单结构版本
     * @param generatedBy 清单生成来源
     * @param features 功能条目列表
     */
    public CocoFeatureManifest {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(generatedBy, "generatedBy must not be null");
        features = List.copyOf(Objects.requireNonNull(features, "features must not be null"));
    }

    /**
     * <p>
     * 返回清单中处于启用状态的功能标识集合。
     * </p>
     * @return 启用的功能标识集合
     */
    public Set<String> enabledFeatureIds() {
        return this.features.stream()
                .filter(CocoFeatureManifestEntry::enabled)
                .map(CocoFeatureManifestEntry::id)
                .collect(Collectors.toUnmodifiableSet());
    }
}
