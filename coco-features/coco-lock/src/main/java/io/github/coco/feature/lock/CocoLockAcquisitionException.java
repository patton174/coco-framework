package io.github.coco.feature.lock;

/**
 * 无法在指定等待期限内获取 Coco 锁时抛出的异常。
 */
public final class CocoLockAcquisitionException extends CocoLockException {

    /**
     * 创建获取锁失败异常。
     * @param key 未取得的业务锁键
     */
    public CocoLockAcquisitionException(String key) {
        super("Failed to acquire Coco lock: " + key);
    }
}
