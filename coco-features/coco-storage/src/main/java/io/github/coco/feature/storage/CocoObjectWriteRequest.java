package io.github.coco.feature.storage;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 流式对象写入请求。输入流由调用方拥有，存储实现不得关闭它。 */
public record CocoObjectWriteRequest(String key, InputStream inputStream, Long contentLength, String contentType,
        Map<String, String> metadata) {
    public CocoObjectWriteRequest {
        CocoObjectKey.validate(key);
        inputStream = Objects.requireNonNull(inputStream, "inputStream must not be null");
        if (contentLength != null && contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
        metadata = CocoObjectMetadata.validateAndCopy(metadata);
    }
}
