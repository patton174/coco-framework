package io.github.coco.feature.concurrencylimit;

import io.github.coco.feature.web.context.CocoWebRequestSnapshot;

/**
 * Coco 在途请求并发限制键解析器。
 * <p>
 * 业务应用可以提供同类型 Bean，把应用自有的稳定标识转换为并发分组键。
 * </p>
 */
@FunctionalInterface
public interface CocoConcurrencyLimitKeyResolver {

    /**
     * 解析当前请求在所选路由内的分组键。
     * @param snapshot Coco Web 请求快照
     * @param route 所选显式路由
     * @return 非空白分组键
     */
    String resolve(CocoWebRequestSnapshot snapshot, CocoConcurrencyLimitRoute route);
}
