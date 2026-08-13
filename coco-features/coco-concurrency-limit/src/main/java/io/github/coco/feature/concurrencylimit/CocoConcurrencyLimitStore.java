package io.github.coco.feature.concurrencylimit;

/**
 * Coco 在途请求并发许可原子存储。
 * <p>
 * 一次申请必须对全部维度执行全有或全无的原子更新。释放操作必须支持调用方的重复释放保护。
 * </p>
 */
public interface CocoConcurrencyLimitStore extends AutoCloseable {

    /**
     * 原子申请当前请求的全部并发约束。
     * @param request 并发约束请求
     * @return 原子申请结果
     */
    CocoConcurrencyLimitAcquisition acquire(CocoConcurrencyLimitRequest request);

    /**
     * 释放先前成功申请返回的许可。
     * @param permit 待释放许可
     */
    void release(CocoConcurrencyLimitPermit permit);

    /**
     * 关闭存储。默认实现不执行操作。
     */
    @Override
    default void close() {
    }
}
