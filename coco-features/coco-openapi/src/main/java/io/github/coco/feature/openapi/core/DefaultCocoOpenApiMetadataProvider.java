package io.github.coco.feature.openapi.core;

import java.util.Objects;

import io.github.coco.feature.openapi.CocoOpenApiProperties;

/**
 * 默认 Coco OpenAPI 元数据提供器。
 * <p>
 * 从 {@link CocoOpenApiProperties} 读取文档基础信息并转换为稳定的元数据对象。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-openapi}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class DefaultCocoOpenApiMetadataProvider implements CocoOpenApiMetadataProvider {

    private final CocoOpenApiProperties properties;

    /**
     * <p>
     * 创建默认 OpenAPI 元数据提供器。
     * </p>
     * @param properties OpenAPI 配置属性
     */
    public DefaultCocoOpenApiMetadataProvider(CocoOpenApiProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CocoOpenApiMetadata metadata() {
        CocoOpenApiProperties.InfoProperties info = this.properties.getInfo();
        return new CocoOpenApiMetadata(info.getTitle(), info.getVersion(), info.getDescription());
    }
}
