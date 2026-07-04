package io.github.coco.api.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * # 默认功能注册器测试
 *
 * - **作者**: [patton174](https://github.com/patton174)
 * - **仓库**: [coco-framework](https://github.com/patton174/coco-framework)
 * - **模块**: `coco-api`
 *
 * 验证功能排除配置的基础行为，保证后续构建期裁剪有稳定输入。
 *
 * @author patton174
 * @since 1.0.0
 */
class DefaultCocoFeatureRegistryTest {

    @Test
    void startsWithNoExcludedFeatures() {
        DefaultCocoFeatureRegistry registry = new DefaultCocoFeatureRegistry();

        assertTrue(registry.excludedFeatures().isEmpty());
        assertFalse(registry.isExcluded(CocoFeature.TENANT));
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
}
