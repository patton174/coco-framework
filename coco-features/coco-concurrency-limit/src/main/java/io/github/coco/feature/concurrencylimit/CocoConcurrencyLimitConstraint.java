package io.github.coco.feature.concurrencylimit;

import java.util.Objects;

/**
 * 单个在途请求并发约束。
 *
 * @param dimension 约束维度
 * @param key 存储键
 * @param limit 最大同时在途请求数
 */
public record CocoConcurrencyLimitConstraint(CocoConcurrencyLimitDimension dimension, String key, int limit) {

    /**
     * 校验并归一化并发约束。
     */
    public CocoConcurrencyLimitConstraint {
        dimension = Objects.requireNonNull(dimension, "dimension must not be null");
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        key = key.trim();
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    String storeKey() {
        return this.dimension.name() + '\0' + this.key;
    }
}
