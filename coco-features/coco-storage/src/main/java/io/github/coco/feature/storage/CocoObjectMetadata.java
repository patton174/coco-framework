package io.github.coco.feature.storage;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 对象的持久化元数据。 */
public record CocoObjectMetadata(String key, long contentLength, String contentType, Map<String, String> metadata,
        Instant lastModified) {
    static final int MAX_METADATA_ENTRIES = 32;
    static final int MAX_METADATA_KEY_LENGTH = 128;
    static final int MAX_METADATA_VALUE_LENGTH = 1024;
    static final int MAX_METADATA_TOTAL_LENGTH = 8192;

    public CocoObjectMetadata {
        CocoObjectKey.validate(key);
        if (contentLength < 0) throw new IllegalArgumentException("contentLength must not be negative");
        metadata = validateAndCopy(metadata);
        lastModified = lastModified == null ? Instant.now() : lastModified;
    }

    static Map<String, String> validateAndCopy(Map<String, String> source) {
        if (source == null || source.isEmpty()) return Map.of();
        if (source.size() > MAX_METADATA_ENTRIES) throw new IllegalArgumentException("too many metadata entries");
        int total = 0;
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = entry.getKey(); String value = entry.getValue();
            if (key == null || value == null || key.isBlank() || key.length() > MAX_METADATA_KEY_LENGTH
                    || value.length() > MAX_METADATA_VALUE_LENGTH) throw new IllegalArgumentException("invalid metadata");
            total += key.length() + value.length();
            if (total > MAX_METADATA_TOTAL_LENGTH) throw new IllegalArgumentException("metadata is too large");
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }
}
