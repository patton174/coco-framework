package io.github.coco.api.feature;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * # 默认功能排除注册器
 *
 * - **作者**: [patton174](https://github.com/patton174)
 * - **仓库**: [coco-framework](https://github.com/patton174/coco-framework)
 * - **模块**: `coco-api`
 *
 * 在内存中记录被业务配置排除的标准功能，供构建期和运行期复用。
 *
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
