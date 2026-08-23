package io.github.coco.feature.idempotency;

import java.util.Objects;

/**
 * 幂等存储逻辑键。
 * <p>keyDigest 只能是原始 {@code Idempotency-Key} 的 SHA-256 十六进制摘要，绝不保存明文。</p>
 * @param namespace 注解命名空间
 * @param method 规范化 HTTP 方法
 * @param route 规范化路由模式
 * @param keyDigest 原始键摘要
 */
public record CocoIdempotencyKey(String namespace, String method, String route, String keyDigest) {

    /** 创建仅包含非敏感字段的逻辑键。 */
    public CocoIdempotencyKey {
        namespace = normalized(namespace, "namespace");
        method = normalized(method, "method");
        route = normalized(route, "route");
        keyDigest = normalized(keyDigest, "keyDigest");
    }

    private static String normalized(String value, String name) {
        String result = Objects.requireNonNull(value, name + " must not be null").trim();
        if (result.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return result;
    }
}
