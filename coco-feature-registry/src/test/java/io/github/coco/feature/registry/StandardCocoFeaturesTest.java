package io.github.coco.feature.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.coco.api.feature.CocoFeature;
import org.junit.jupiter.api.Test;

/**
 * 标准功能元数据测试。
 * <p>
 * 验证标准功能清单、依赖声明和依赖排除传播规则。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-registry}</li>
 * </ul>
 * <p>
 * 代码注释采用标准 JavaDoc HTML 标签，不使用 Markdown 语法。
 * </p>
 * @author patton174
 * @since 1.0.0
 */
class StandardCocoFeaturesTest {

    @Test
    void registersAllStandardFeatures() {
        Set<CocoFeature> registered = StandardCocoFeatures.all().stream()
                .map(CocoFeatureDefinition::feature)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CocoFeature.class)));

        assertEquals(EnumSet.allOf(CocoFeature.class), registered);
    }

    @Test
    void declaresPlannedFeatureDependencies() {
        Map<CocoFeature, CocoFeatureDefinition> definitions = StandardCocoFeatures.allByFeature();

        assertEquals(Set.of(CocoFeature.WEB, CocoFeature.MYBATIS_PLUS),
                definitions.get(CocoFeature.AUDIT).dependencies());
        assertEquals(Set.of(CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
                definitions.get(CocoFeature.TENANT).dependencies());
        assertEquals(Set.of(CocoFeature.MYBATIS_PLUS, CocoFeature.SECURITY),
                definitions.get(CocoFeature.DATA_PERMISSION).dependencies());
        assertEquals(Set.of(CocoFeature.WEB, CocoFeature.SECURITY),
                definitions.get(CocoFeature.OPENAPI).dependencies());
        assertEquals(Set.of(CocoFeature.MYBATIS_PLUS),
                definitions.get(CocoFeature.CODEGEN).dependencies());
    }

    @Test
    void excludesFeaturesThatDependOnExcludedBaseFeature() {
        Set<CocoFeature> enabled = StandardCocoFeatures.resolveEnabled(Set.of(CocoFeature.MYBATIS_PLUS));

        assertFalse(enabled.contains(CocoFeature.MYBATIS_PLUS));
        assertFalse(enabled.contains(CocoFeature.AUDIT));
        assertFalse(enabled.contains(CocoFeature.TENANT));
        assertFalse(enabled.contains(CocoFeature.DATA_PERMISSION));
        assertFalse(enabled.contains(CocoFeature.CODEGEN));
        assertTrue(enabled.contains(CocoFeature.WEB));
        assertTrue(enabled.contains(CocoFeature.SECURITY));
        assertTrue(enabled.contains(CocoFeature.OPENAPI));
    }
}
