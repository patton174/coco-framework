package io.github.coco.feature.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * 幂等存储逻辑键。
 * <p>
 * 公共入口只接收原始请求键，并在创建时立即计算 SHA-256 摘要。实例不保留原始键，
 * {@link #toString()} 也不会输出摘要。
 * </p>
 */
public final class CocoIdempotencyKey {
    private final String namespace;
    private final String method;
    private final String operationId;
    private final String keyDigest;

    private CocoIdempotencyKey(String namespace, String method, String operationId, String keyDigest) {
        this.namespace = normalized(namespace, "namespace");
        this.method = normalized(method, "method");
        this.operationId = normalized(operationId, "operationId");
        this.keyDigest = normalized(keyDigest, "keyDigest");
    }

    /**
     * <p>从原始请求键创建逻辑键。</p>
     * @param namespace 幂等命名空间
     * @param method 规范化 HTTP 方法
     * @param operationId 稳定的处理操作标识
     * @param rawKey 原始 {@code Idempotency-Key}
     * @return 仅包含摘要的逻辑键
     */
    public static CocoIdempotencyKey fromRawKey(String namespace, String method, String operationId, String rawKey) {
        return new CocoIdempotencyKey(namespace, method, operationId, sha256(rawKey));
    }

    /** @return 注解命名空间 */
    public String namespace() { return this.namespace; }
    /** @return 规范化 HTTP 方法 */
    public String method() { return this.method; }
    /** @return 稳定的处理操作标识 */
    public String operationId() { return this.operationId; }
    /** @return 原始请求键的 SHA-256 十六进制摘要 */
    public String keyDigest() { return this.keyDigest; }

    @Override
    public boolean equals(Object other) {
        if (this == other) { return true; }
        if (!(other instanceof CocoIdempotencyKey key)) { return false; }
        return this.namespace.equals(key.namespace) && this.method.equals(key.method)
                && this.operationId.equals(key.operationId) && this.keyDigest.equals(key.keyDigest);
    }

    @Override
    public int hashCode() { return Objects.hash(this.namespace, this.method, this.operationId, this.keyDigest); }

    @Override
    public String toString() {
        return "CocoIdempotencyKey[namespace=" + this.namespace + ", method=" + this.method
                + ", operationId=" + this.operationId + "]";
    }

    private static String sha256(String rawKey) {
        String checked = normalized(rawKey, "rawKey");
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(checked.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) { result.append(String.format(Locale.ROOT, "%02x", value)); }
            return result.toString();
        }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private static String normalized(String value, String name) {
        String result = Objects.requireNonNull(value, name + " must not be null").trim();
        if (result.isEmpty()) { throw new IllegalArgumentException(name + " must not be blank"); }
        return result;
    }
}
