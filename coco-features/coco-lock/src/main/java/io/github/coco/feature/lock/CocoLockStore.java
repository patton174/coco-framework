package io.github.coco.feature.lock;

/**
 * Coco 分布式锁原子存储 SPI。
 * <p>实现必须按 key 原子获取，且只能让当前 owner token 续期或释放租约。多实例部署必须提供共享实现。</p>
 */
public interface CocoLockStore {

    /** 原子申请租约。 */
    AcquireResult acquire(CocoLockLease lease);

    /** 仅在 owner token 仍匹配且租约未过期时续期。 */
    RenewResult renew(CocoLockLease lease);

    /** 仅在 owner token 仍匹配时释放租约。 */
    boolean release(CocoLockLease lease);

    /** 获取结果。 */
    enum AcquireResult { ACQUIRED, CONTENDED, UNAVAILABLE }

    /** 续期结果。 */
    enum RenewResult { RENEWED, NOT_OWNER, UNAVAILABLE }
}
