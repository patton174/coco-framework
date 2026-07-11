package io.github.coco.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import org.junit.jupiter.api.Test;

/**
 * Coco 配置模块内部功能注册器测试。
 * <p>
 * 验证已废弃 {@code CocoConfigurer} 兼容路径使用的内部注册器行为。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-config}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class MutableCocoFeatureRegistryTest {

    @Test
    void recordsEnabledAndDisabledFeatures() {
        MutableCocoFeatureRegistry registry = new MutableCocoFeatureRegistry();

        registry.enable(CocoFeature.WEB, CocoFeature.AUDIT)
                .disable(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION);

        assertTrue(registry.isEnabled(CocoFeature.WEB));
        assertTrue(registry.isDisabled(CocoFeature.TENANT));
        assertEquals(Set.of(CocoFeature.WEB, CocoFeature.AUDIT), registry.enabledFeatures());
        assertEquals(Set.of(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION), registry.disabledFeatures());
    }

    @Test
    void ignoresNullInputsWhenRecordingFeatures() {
        MutableCocoFeatureRegistry registry = new MutableCocoFeatureRegistry();

        registry.enable((CocoFeature[]) null)
                .enable(CocoFeature.WEB, null)
                .disable((CocoFeature[]) null)
                .disable(CocoFeature.TENANT, null);

        assertEquals(Set.of(CocoFeature.WEB), registry.enabledFeatures());
        assertEquals(Set.of(CocoFeature.TENANT), registry.disabledFeatures());
    }

    @Test
    void exposesImmutableSnapshots() {
        MutableCocoFeatureRegistry registry = new MutableCocoFeatureRegistry();
        registry.enable(CocoFeature.OPENAPI);

        Set<CocoFeature> enabledFeatures = registry.enabledFeatures();

        assertThrows(UnsupportedOperationException.class, () -> enabledFeatures.add(CocoFeature.CODEGEN));
        assertEquals(Set.of(CocoFeature.OPENAPI), registry.enabledFeatures());
    }

    @Test
    void rejectsNullFeatureChecks() {
        MutableCocoFeatureRegistry registry = new MutableCocoFeatureRegistry();

        assertThrows(NullPointerException.class, () -> registry.isEnabled(null));
        assertThrows(NullPointerException.class, () -> registry.isDisabled(null));
    }
}
