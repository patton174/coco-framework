package io.github.coco.feature.lock;

/** Coco 锁存储类型。 */
public enum CocoLockStoreType {

    /** 进程内参考存储。 */
    IN_MEMORY,

    /** Redis 共享存储。 */
    REDIS
}
