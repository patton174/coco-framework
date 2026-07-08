package io.github.coco.feature.openapi.core;

import java.util.Optional;

/**
 * Coco OpenAPI 元数据。
 * <p>
 * 承载框架级 API 文档基础信息，不绑定具体 OpenAPI 渲染库。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-openapi}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public record CocoOpenApiMetadata(String title, String version, String description) {

    /**
     * <p>
     * 创建 OpenAPI 元数据。
     * </p>
     * @param title 文档标题
     * @param version 文档版本
     * @param description 文档描述
     */
    public CocoOpenApiMetadata {
        title = normalize(title, "Coco API");
        version = normalize(version, "1.0.0");
        description = normalize(description, null);
    }

    /**
     * <p>
     * 返回可选文档描述。
     * </p>
     * @return 文档描述；不存在时为空
     */
    public Optional<String> descriptionOptional() {
        return Optional.ofNullable(this.description);
    }

    private static String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
