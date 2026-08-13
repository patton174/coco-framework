package io.github.coco.feature.idempotency.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 可重放的 Servlet 响应快照。
 *
 * @author patton174
 * @since 1.0.0
 */
public final class CocoIdempotencyStoredResponse {

    private final int status;

    private final Map<String, List<String>> headers;

    private final byte[] body;

    /**
     * 创建响应快照。
     * @param status HTTP 状态码
     * @param headers 响应头多值快照
     * @param body 响应体字节
     */
    public CocoIdempotencyStoredResponse(int status, Map<String, List<String>> headers, byte[] body) {
        if (status < 100 || status > 999) {
            throw new IllegalArgumentException("status must be between 100 and 999");
        }
        this.status = status;
        this.headers = immutableHeaders(headers);
        this.body = Objects.requireNonNull(body, "body must not be null").clone();
    }

    /**
     * 返回 HTTP 状态码。
     * @return HTTP 状态码
     */
    public int status() {
        return this.status;
    }

    /**
     * 返回不可变响应头快照。
     * @return 响应头快照
     */
    public Map<String, List<String>> headers() {
        return immutableHeaders(this.headers);
    }

    /**
     * 返回响应体副本。
     * @return 响应体副本
     */
    public byte[] body() {
        return this.body.clone();
    }

    private static Map<String, List<String>> immutableHeaders(Map<String, List<String>> source) {
        Objects.requireNonNull(source, "headers must not be null");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        source.forEach((name, values) -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("header name must not be blank");
            }
            List<String> checkedValues = values == null ? List.of() : new ArrayList<>(values);
            if (checkedValues.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("header values must not contain null");
            }
            copy.put(name, Collections.unmodifiableList(checkedValues));
        });
        return Collections.unmodifiableMap(copy);
    }
}
