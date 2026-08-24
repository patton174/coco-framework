package io.github.coco.feature.lock;

/** Coco 分布式锁编程式入口。 */
public interface CocoLockManager {
    /** 按请求尝试获取锁，等待超时和 Store 不可用会作为结果返回。 */
    CocoLockResult tryAcquire(CocoLockRequest request);
}
