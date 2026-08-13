package io.github.coco.feature.idempotency.store;

import java.util.Objects;

/**
 * 已脱敏的幂等请求身份。
 *
 * @param keyHash 幂等键 SHA-256 摘要
 * @param requestHash 请求内容 SHA-256 摘要
 * @author patton174
 * @since 1.0.0
 */
public record CocoIdempotencyRequest(String keyHash, String requestHash) {

    /**
     * 创建已脱敏的幂等请求身份。
     */
    public CocoIdempotencyRequest {
        keyHash = requireSha256(keyHash, "keyHash");
        requestHash = requireSha256(requestHash, "requestHash");
    }

    private static String requireSha256(String value, String name) {
        String checked = Objects.requireNonNull(value, name + " must not be null");
        if (!checked.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(name + " must be a lowercase SHA-256 digest");
        }
        return checked;
    }
}
