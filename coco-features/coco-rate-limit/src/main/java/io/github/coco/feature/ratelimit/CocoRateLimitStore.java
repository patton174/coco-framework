package io.github.coco.feature.ratelimit;

/**
 * Coco 限流原子存储 SPI。
 * <p>
 * 实现必须在单个原子操作中完成计数、上限判断和 TTL 写入。多实例生产环境应提供 Redis、数据库或其他
 * 共享一致性存储实现，不能依赖进程内参考实现获得跨节点配额。
 * </p>
 */
@FunctionalInterface
public interface CocoRateLimitStore {

    /**
     * 原子占用当前窗口的一个配额。
     * @param permit 占用请求
     * @return 占用结果
     * @throws RuntimeException 存储故障；调用方不得将存储故障伪装为限流拒绝
     */
    CocoRateLimitDecision acquire(CocoRateLimitPermit permit);
}
