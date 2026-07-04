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
 *   <li>模块：{@code coco-api-core}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoFeatureRegistry implements CocoFeatureRegistry {

    private final EnumSet<CocoFeature> includedFeatures = EnumSet.noneOf(CocoFeature.class);

    private final EnumSet<CocoFeature> excludedFeatures = EnumSet.noneOf(CocoFeature.class);

    @Override
    public CocoFeatureRegistry include(CocoFeature... features) {
        addAll(this.includedFeatures, features);
        return this;
    }

    @Override
    public CocoFeatureRegistry exclude(CocoFeature... features) {
        addAll(this.excludedFeatures, features);
        return this;
    }

    @Override
    public boolean isIncluded(CocoFeature feature) {
        return this.includedFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }

    @Override
    public boolean isExcluded(CocoFeature feature) {
        return this.excludedFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }

    @Override
    public Set<CocoFeature> includedFeatures() {
        if (this.includedFeatures.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(this.includedFeatures));
    }

    @Override
    public Set<CocoFeature> excludedFeatures() {
        if (this.excludedFeatures.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(this.excludedFeatures));
    }

    private static void addAll(EnumSet<CocoFeature> target, CocoFeature... features) {
        if (features == null) {
            return;
        }
        for (CocoFeature feature : features) {
            if (feature != null) {
                target.add(feature);
            }
        }
    }
}
