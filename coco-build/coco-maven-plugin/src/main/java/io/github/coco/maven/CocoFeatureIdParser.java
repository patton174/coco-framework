package io.github.coco.maven;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.coco.api.feature.CocoFeature;

/**
 * Coco 构建期功能标识解析器。
 * <p>
 * 统一解析 Maven 参数、properties 和 YAML 中的功能标识，并在遇到未知标识时立即失败，避免拼写错误导致构建期功能裁剪静默失效。
 * 已在主版本中移除的功能标识同样失败，但错误信息会给出替代方案，帮助业务项目完成升级。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-maven-plugin}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
final class CocoFeatureIdParser {

    private static final String VALID_FEATURE_IDS = Arrays.stream(CocoFeature.values())
            .map(CocoFeature::id)
            .collect(Collectors.joining(", "));

    /** 已从框架移除的功能标识及其升级指引。 */
    static final Map<String, String> RETIRED_FEATURE_IDS = Map.of(
            "codegen", "The codegen feature was removed in Coco Framework 3.0.0. CRUD source generation now lives in "
                    + "coco-generate (https://github.com/patton174/coco-generate); remove this id from the "
                    + "configuration.");

    private CocoFeatureIdParser() {
    }

    /**
     * <p>
     * 解析任意配置值中的功能标识。
     * </p>
     * @param value 配置值
     * @param source 配置来源描述
     * @return 功能集合
     */
    static Set<CocoFeature> parse(Object value, String source) {
        LinkedHashSet<CocoFeature> features = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addFeatures(features, item, source);
            }
            return Set.copyOf(features);
        }
        addFeatures(features, value, source);
        return Set.copyOf(features);
    }

    private static void addFeatures(Set<CocoFeature> target, Object value, String source) {
        if (value == null) {
            return;
        }
        for (String token : value.toString().split(",")) {
            String featureId = token.trim();
            if (featureId.isEmpty()) {
                continue;
            }
            target.add(resolve(featureId, source));
        }
    }

    private static CocoFeature resolve(String featureId, String source) {
        return CocoFeature.fromId(featureId)
                .orElseThrow(() -> unresolvableFeature(featureId, source));
    }

    private static IllegalArgumentException unresolvableFeature(String featureId, String source) {
        String retirementNotice = RETIRED_FEATURE_IDS.get(featureId);
        if (retirementNotice != null) {
            return new IllegalArgumentException("Retired Coco feature id '" + featureId + "' in " + source + ". "
                    + retirementNotice);
        }
        return new IllegalArgumentException("Unknown Coco feature id '" + featureId + "' in " + source
                + ". Valid feature ids: " + VALID_FEATURE_IDS + ".");
    }
}
