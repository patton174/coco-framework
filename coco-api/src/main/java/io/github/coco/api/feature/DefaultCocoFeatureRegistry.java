package io.github.coco.api.feature;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 默认功能排除注册器。
 * <p>
 * 在内存中记录被业务配置排除的标准功能，供构建期和运行期复用。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-api}</li>
 * </ul>
 * <p>
 * 代码注释采用标准 JavaDoc HTML 标签，不使用 Markdown 语法。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoFeatureRegistry implements CocoFeatureRegistry {

    private final EnumSet<CocoFeature> excludedFeatures = EnumSet.noneOf(CocoFeature.class);

    @Override
    public CocoFeatureRegistry exclude(CocoFeature... features) {
        if (features == null) {
            return this;
        }
        for (CocoFeature feature : features) {
            if (feature != null) {
                this.excludedFeatures.add(feature);
            }
        }
        return this;
    }

    @Override
    public boolean isExcluded(CocoFeature feature) {
        return this.excludedFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }

    @Override
    public Set<CocoFeature> excludedFeatures() {
        if (this.excludedFeatures.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(this.excludedFeatures));
    }
}
