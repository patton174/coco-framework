package io.github.coco.api.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * 默认功能注册器测试。
 * <p>
 * 验证功能排除配置的基础行为，保证后续构建期裁剪有稳定输入。
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
class DefaultCocoFeatureRegistryTest {

    @Test
    void startsWithNoExcludedFeatures() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        assertTrue(registry.includedFeatures().isEmpty());
        assertTrue(registry.excludedFeatures().isEmpty());
        assertFalse(registry.isIncluded(CocoFeature.WEB));
        assertFalse(registry.isExcluded(CocoFeature.TENANT));
    }

    @Test
    void recordsIncludedFeatures() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        registry.include(CocoFeature.WEB, CocoFeature.AUDIT);

        assertTrue(registry.isIncluded(CocoFeature.WEB));
        assertTrue(registry.isIncluded(CocoFeature.AUDIT));
        assertEquals(Set.of(CocoFeature.WEB, CocoFeature.AUDIT), registry.includedFeatures());
    }

    @Test
    void recordsExcludedFeatures() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        registry.exclude(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION);

        assertTrue(registry.isExcluded(CocoFeature.TENANT));
        assertTrue(registry.isExcluded(CocoFeature.DATA_PERMISSION));
        assertEquals(Set.of(CocoFeature.TENANT, CocoFeature.DATA_PERMISSION), registry.excludedFeatures());
    }

    @Test
    void ignoresDuplicateExclusions() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        registry.exclude(CocoFeature.TENANT, CocoFeature.TENANT);

        assertEquals(Set.of(CocoFeature.TENANT), registry.excludedFeatures());
    }

    @Test
    void supportsEnableAndDisableAliases() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        registry.enable(CocoFeature.OPENAPI).disable(CocoFeature.DATA_PERMISSION);

        assertEquals(Set.of(CocoFeature.OPENAPI), registry.includedFeatures());
        assertEquals(Set.of(CocoFeature.DATA_PERMISSION), registry.excludedFeatures());
    }

    @Test
    void ignoresNullFeatureArraysAndValues() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        registry.include((CocoFeature[]) null)
                .exclude((CocoFeature[]) null)
                .include(CocoFeature.WEB, null)
                .exclude(null, CocoFeature.TENANT);

        assertEquals(Set.of(CocoFeature.WEB), registry.includedFeatures());
        assertEquals(Set.of(CocoFeature.TENANT), registry.excludedFeatures());
    }
}
