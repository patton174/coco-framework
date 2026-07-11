package io.github.coco.spring.boot.autoconfigure.feature;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import io.github.coco.api.feature.CocoFeatureRegistry;

/**
 * Coco 配置模块内部使用的可变功能选择注册器。
 * <p>
 * 该实现只用于兼容已废弃的 {@code CocoConfigurer} 收集流程，不作为业务侧扩展点暴露。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-autoconfigure}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@SuppressWarnings("deprecation")
final class MutableCocoFeatureRegistry implements CocoFeatureRegistry {

    private final EnumSet<CocoFeature> enabledFeatures = EnumSet.noneOf(CocoFeature.class);

    private final EnumSet<CocoFeature> disabledFeatures = EnumSet.noneOf(CocoFeature.class);

    @Override
    public CocoFeatureRegistry enable(CocoFeature... features) {
        addAll(this.enabledFeatures, features);
        return this;
    }

    @Override
    public CocoFeatureRegistry disable(CocoFeature... features) {
        addAll(this.disabledFeatures, features);
        return this;
    }

    @Override
    public boolean isEnabled(CocoFeature feature) {
        return this.enabledFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }

    @Override
    public boolean isDisabled(CocoFeature feature) {
        return this.disabledFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }

    @Override
    public Set<CocoFeature> enabledFeatures() {
        if (this.enabledFeatures.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(this.enabledFeatures));
    }

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
