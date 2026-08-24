package io.github.coco.storage;

import java.util.Objects;

import org.springframework.core.io.Resource;

/**
 * Coco 对象读取结果。
 * <p>
 * {@link #resource()} 是可按需打开的 Spring {@link Resource}，不会在 {@code open} 时将对象整体读入内存。
 * </p>
 * @param metadata 对象稳定元数据快照
 * @param resource 对象内容资源
 */
public record CocoObjectResource(CocoObjectMetadata metadata, Resource resource) {

    /**
     * 创建对象读取结果。
     */
    public CocoObjectResource {
        metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        resource = Objects.requireNonNull(resource, "resource must not be null");
    }
}
