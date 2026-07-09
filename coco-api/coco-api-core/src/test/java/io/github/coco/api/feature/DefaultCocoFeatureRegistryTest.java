package io.github.coco.api.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 默认功能注册器测试。
 * <p>
 * 验证功能启用和禁用配置的基础行为，保证后续构建期裁剪有稳定输入。
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
@SuppressWarnings("deprecation")
class DefaultCocoFeatureRegistryTest {

    @Test
    void startsWithNoFeatureSelections() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        assertTrue(registry.enabledFeatures().isEmpty());
        assertTrue(registry.disabledFeatures().isEmpty());
        assertFalse(registry.isEnabled(CocoFeature.WEB));
        assertFalse(registry.isDisabled(CocoFeature.TENANT));
    }

    @Test
    void recordsEnabledFeatures() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        registry.enable(CocoFeature.WEB, CocoFeature.AUDIT);

        assertTrue(registry.isEnabled(CocoFeature.WEB));
        assertTrue(registry.isEnabled(CocoFeature.AUDIT));
        assertEquals(Set.of(CocoFeature.WEB, CocoFeature.AUDIT), registry.enabledFeatures());
    }

    @Test
    void recordsDisabledFeatures() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        registry.disable(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION);

        assertTrue(registry.isDisabled(CocoFeature.TENANT));
        assertTrue(registry.isDisabled(CocoFeature.DATA_PERMISSION));
        assertEquals(Set.of(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION), registry.disabledFeatures());
    }

    @Test
    void ignoresDuplicateDisabledFeatures() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        registry.disable(CocoFeature.TENANT, CocoFeature.TENANT);

        assertEquals(Set.of(CocoFeature.TENANT), registry.disabledFeatures());
    }

    @Test
    void supportsEnableAndDisableChaining() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        registry.enable(CocoFeature.OPENAPI).disable(CocoFeature.DATA_PERMISSION);

        assertEquals(Set.of(CocoFeature.OPENAPI), registry.enabledFeatures());
        assertEquals(Set.of(CocoFeature.DATA_PERMISSION), registry.disabledFeatures());
    }

    @Test
    void ignoresNullFeatureArraysAndValues() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        registry.enable((CocoFeature[]) null)
                .disable((CocoFeature[]) null)
                .enable(CocoFeature.WEB, null)
                .disable(null, CocoFeature.TENANT);

        assertEquals(Set.of(CocoFeature.WEB), registry.enabledFeatures());
        assertEquals(Set.of(CocoFeature.TENANT), registry.disabledFeatures());
    }
}
