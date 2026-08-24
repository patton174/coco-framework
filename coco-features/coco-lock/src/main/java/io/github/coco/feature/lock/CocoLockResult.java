package io.github.coco.feature.lock;

import java.util.Objects;

/**
 * 编程式获取锁的结果契约。
 * @param status Store 获取状态
 * @param handle 成功时的锁句柄，失败时为 {@code null}
 */
public record CocoLockResult(CocoLockStore.AcquireResult status, CocoLockHandle handle) {
    /** 校验状态与句柄的一致性。 */
    public CocoLockResult {
        status = Objects.requireNonNull(status, "status must not be null");
        if ((status == CocoLockStore.AcquireResult.ACQUIRED) != (handle != null)) {
            throw new IllegalArgumentException("acquired status and handle must agree");
        }
    }

    /** 是否成功获得锁。 */
    public boolean acquired() { return this.status == CocoLockStore.AcquireResult.ACQUIRED; }
}
