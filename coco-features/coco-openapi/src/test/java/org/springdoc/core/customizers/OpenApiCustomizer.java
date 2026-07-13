package org.springdoc.core.customizers;

import io.swagger.v3.oas.models.OpenAPI;

/**
 * SpringDoc OpenAPI 定制器测试桩。
 * <p>
 * 仅用于验证 Coco 在 SpringDoc 类型存在时的条件装配和元数据适配行为。
 * </p>
 */
@FunctionalInterface
public interface OpenApiCustomizer {

    /**
     * <p>
     * 定制 OpenAPI 模型。
     * </p>
     * @param openApi OpenAPI 模型
     */
    void customise(OpenAPI openApi);
}
