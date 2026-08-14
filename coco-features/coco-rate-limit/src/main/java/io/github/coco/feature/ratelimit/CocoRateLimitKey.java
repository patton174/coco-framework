package io.github.coco.feature.ratelimit;

import java.util.Objects;

/**
 * 限流存储使用的逻辑键。
 * <p>
 * 路由标识和主体标识分开保存，避免不同路由共享一个容量桶。
 * </p>
 * @param routeId 路由标识
 * @param subject 主体标识
 */
public record CocoRateLimitKey(String routeId, String subject) {

    /**
     * 创建限流逻辑键。
     * @param routeId 路由标识
     * @param subject 主体标识
     */
    public CocoRateLimitKey {
        routeId = requireValue(routeId, "routeId");
        subject = requireValue(subject, "subject");
    }

    private static String requireValue(String value, String name) {
        String normalized = Objects.requireNonNull(value, name + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
