package io.github.coco.feature.lock;

import io.github.coco.exception.type.CocoSystemException;

/** Coco 锁租约在关闭时已失效或无法确认释放的异常。 */
public final class CocoLockException extends CocoSystemException {

    /** 使用锁错误码创建异常。 */
    public CocoLockException(CocoLockErrorCode errorCode) {
        super(errorCode);
    }

    /** 使用锁错误码和原始异常创建异常。 */
    public CocoLockException(CocoLockErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }
}
