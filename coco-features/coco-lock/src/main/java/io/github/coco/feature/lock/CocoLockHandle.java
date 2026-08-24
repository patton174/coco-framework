package io.github.coco.feature.lock;

/** 已成功获得的锁句柄；必须由获得它的线程关闭。 */
public interface CocoLockHandle extends AutoCloseable {
    /** 返回当前租约快照。 */
    CocoLockLease lease();

    /** 是否为当前线程的可重入申请。 */
    boolean reentrant();

    /** 租约是否已丢失或所属管理器已关闭。 */
    boolean lost();

    /** 释放一次锁重入计数，最外层关闭时实际释放 Store 租约。 */
    @Override
    void close();
}
