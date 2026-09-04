package io.github.coco.storage;

import java.io.InputStream;
import java.util.Objects;

/**
 * Coco 对象写入请求。
 * <p>
 * {@code contentLength} 只用于提前拒绝明显超限的请求，存储实现仍必须按实际读取字节数校验。
 * </p>
 * @param key 业务方决定的对象键，不接受原始文件名作为存储定位依据
 * @param content 待写入的流；调用方负责其生命周期
 * @param contentLength 客户端声明长度；未知时为 {@code null}
 * @param contentType 内容类型；为空时由实现使用安全默认值
 * @param overwritePolicy 覆盖策略；为空时使用存储配置的默认值
 */
public record CocoObjectPutRequest(String key, InputStream content, Long contentLength, String contentType,
        CocoStorageOverwritePolicy overwritePolicy) {

    /**
     * 创建对象写入请求。
     */
    public CocoObjectPutRequest {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        content = Objects.requireNonNull(content, "content must not be null");
        if (contentLength != null && contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
    }

    /**
     * 创建使用存储默认覆盖策略的写入请求。
     * @param key 对象键
     * @param content 内容流
     * @param contentLength 声明长度；未知时为空
     * @param contentType 内容类型；为空时使用安全默认值
     * @return 写入请求
     */
    public static CocoObjectPutRequest of(String key, InputStream content, Long contentLength, String contentType) {
        return new CocoObjectPutRequest(key, content, contentLength, contentType, null);
    }
}
