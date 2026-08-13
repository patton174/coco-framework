package io.github.coco.feature.lock;

/**
 * Coco 锁基础设施异常。
 */
public class CocoLockException extends RuntimeException {

    /**
     * 创建锁异常。
     * @param message 异常说明
     */
    public CocoLockException(String message) {
        super(message);
    }

    /**
     * 创建锁异常。
     * @param message 异常说明
     * @param cause 原始异常
     */
    public CocoLockException(String message, Throwable cause) {
        super(message, cause);
    }
}
