package io.github.coco.api.feature;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 默认功能选择注册器。
 * <p>
 * 在内存中记录被业务配置显式启用或禁用的标准功能，供构建期和运行期复用。
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

    private final EnumSet<CocoFeature> enabledFeatures = EnumSet.noneOf(CocoFeature.class);

    private final EnumSet<CocoFeature> disabledFeatures = EnumSet.noneOf(CocoFeature.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoFeatureRegistry enable(CocoFeature... features) {
        addAll(this.enabledFeatures, features);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoFeatureRegistry disable(CocoFeature... features) {
        addAll(this.disabledFeatures, features);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled(CocoFeature feature) {
        return this.enabledFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDisabled(CocoFeature feature) {
        return this.disabledFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<CocoFeature> enabledFeatures() {
        if (this.enabledFeatures.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(this.enabledFeatures));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<CocoFeature> disabledFeatures() {
        if (this.disabledFeatures.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(this.disabledFeatures));
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
