package io.github.coco.feature.concurrencylimit;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 一次请求需要原子申请的全部并发约束。
 *
 * @param constraints 按全局、路由、解析键顺序排列的约束
 */
public record CocoConcurrencyLimitRequest(List<CocoConcurrencyLimitConstraint> constraints) {

    /**
     * 校验并复制约束列表。
     */
    public CocoConcurrencyLimitRequest {
        if (constraints == null || constraints.isEmpty()) {
            throw new IllegalArgumentException("constraints must not be empty");
        }
        constraints = List.copyOf(constraints);
        Set<CocoConcurrencyLimitDimension> dimensions = EnumSet.noneOf(CocoConcurrencyLimitDimension.class);
        Set<String> keys = new HashSet<>();
        for (CocoConcurrencyLimitConstraint constraint : constraints) {
            CocoConcurrencyLimitConstraint checked = Objects.requireNonNull(constraint,
                    "constraint must not be null");
            if (!dimensions.add(checked.dimension())) {
                throw new IllegalArgumentException("duplicate concurrency dimension: " + checked.dimension());
            }
            if (!keys.add(checked.storeKey())) {
                throw new IllegalArgumentException("duplicate concurrency key: " + checked.key());
            }
        }
    }
}
