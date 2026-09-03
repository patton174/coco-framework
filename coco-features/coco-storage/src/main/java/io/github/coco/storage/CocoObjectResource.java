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
public record CocoObjectResource(CocoObjectMetadata metadata, Resource resource) implements AutoCloseable {

    /**
     * 创建对象读取结果。
     */
    public CocoObjectResource {
        metadata = Objects.requireNonNull(metadata, "metadata must not be null");
        resource = Objects.requireNonNull(resource, "resource must not be null");
    }

    /**
     * 释放尚未打开的读取快照。
     * <p>
     * 本地实现会用该边界延迟回收已被覆盖或删除的 blob。已打开的输入流仍由输入流自身关闭时释放。
     * </p>
     */
    @Override
    public void close() {
        if (this.resource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            }
            catch (Exception exception) {
                throw new IllegalStateException("Unable to close Coco object resource", exception);
            }
        }
    }
}
