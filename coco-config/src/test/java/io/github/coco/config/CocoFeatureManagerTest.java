package io.github.coco.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import io.github.coco.api.feature.CocoFeature;
import org.junit.jupiter.api.Test;

/**
 * Coco 功能管理器测试。
 * <p>
 * 验证运行时功能启用状态和依赖排除传播规则。
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
class CocoFeatureManagerTest {

    @Test
    void enablesAllStandardFeaturesByDefault() {
        CocoFeatureManager manager = new DefaultCocoFeatureManager(Set.of());

        assertEquals(EnumSet.allOf(CocoFeature.class), manager.enabledFeatures());
        assertTrue(manager.excludedFeatures().isEmpty());
    }

    @Test
    void excludesDependentFeaturesWhenBaseFeatureIsExcluded() {
        CocoFeatureManager manager = new DefaultCocoFeatureManager(Set.of(CocoFeature.MYBATIS_PLUS));

        assertFalse(manager.isEnabled(CocoFeature.MYBATIS_PLUS));
        assertFalse(manager.isEnabled(CocoFeature.AUDIT));
        assertFalse(manager.isEnabled(CocoFeature.TENANT));
        assertFalse(manager.isEnabled(CocoFeature.DATA_PERMISSION));
        assertFalse(manager.isEnabled(CocoFeature.CODEGEN));
        assertTrue(manager.isEnabled(CocoFeature.WEB));
        assertTrue(manager.isEnabled(CocoFeature.SECURITY));
        assertTrue(manager.isEnabled(CocoFeature.OPENAPI));
    }
}
