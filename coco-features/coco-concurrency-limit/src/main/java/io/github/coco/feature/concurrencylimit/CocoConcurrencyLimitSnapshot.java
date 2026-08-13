package io.github.coco.feature.concurrencylimit;

import java.util.Objects;

/**
 * 一次申请完成后的并发维度快照。
 *
 * @param dimension 约束维度
 * @param limit 配置上限
 * @param remaining 当前申请后剩余容量
 */
public record CocoConcurrencyLimitSnapshot(CocoConcurrencyLimitDimension dimension, int limit, int remaining) {

    /**
     * 校验并发快照。
     */
    public CocoConcurrencyLimitSnapshot {
        dimension = Objects.requireNonNull(dimension, "dimension must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (remaining < 0 || remaining > limit) {
            throw new IllegalArgumentException("remaining must be between zero and limit");
        }
    }
}
