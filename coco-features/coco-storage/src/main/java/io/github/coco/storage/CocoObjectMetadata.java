package io.github.coco.storage;

import java.time.Instant;
import java.util.Objects;

/**
 * Coco 对象元数据。
 * <p>
 * 元数据由存储实现生成并持久化，业务方不应将客户端提交的文件名或长度直接作为可信元数据。
 * </p>
 * @param key 规范化后的对象键
 * @param size 对象字节长度
 * @param contentType 对象内容类型
 * @param sha256 小写十六进制 SHA-256 摘要
 * @param lastModified 最后成功写入时间
 */
public record CocoObjectMetadata(String key, long size, String contentType, String sha256, Instant lastModified) {

    /**
     * 创建稳定对象元数据。
     */
    public CocoObjectMetadata {
        key = requireText(key, "key");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
        contentType = requireText(contentType, "contentType");
        sha256 = requireText(sha256, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must be a lowercase SHA-256 digest");
        }
        lastModified = Objects.requireNonNull(lastModified, "lastModified must not be null");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
