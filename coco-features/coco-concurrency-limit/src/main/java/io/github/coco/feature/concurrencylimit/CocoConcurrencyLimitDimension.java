package io.github.coco.feature.concurrencylimit;

/**
 * Coco 在途请求并发约束维度。
 */
public enum CocoConcurrencyLimitDimension {

    /** 当前应用进程内的全局受保护请求。 */
    GLOBAL,

    /** 当前显式路由。 */
    ROUTE,

    /** 当前路由内由键解析器得到的分组。 */
    KEY
}
