package io.github.coco.feature.openapi.core;

/**
 * Coco OpenAPI 元数据提供器。
 * <p>
 * 文档渲染实现通过该 SPI 获取框架统一的 OpenAPI 基础信息。
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
@FunctionalInterface
public interface CocoOpenApiMetadataProvider {

    /**
     * <p>
     * 返回 OpenAPI 元数据。
     * </p>
     * @return OpenAPI 元数据
     */
    CocoOpenApiMetadata metadata();
}
