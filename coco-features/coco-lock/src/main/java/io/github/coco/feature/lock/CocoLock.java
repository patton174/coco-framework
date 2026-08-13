package io.github.coco.feature.lock;

import java.time.Instant;

/**
 * Coco 锁句柄。
 * <p>
 * 锁句柄绑定创建它的线程，只能由该线程关闭。{@link #close()} 可重复调用，首次成功关闭后不再释放任何锁。
 * </p>
 */
public interface CocoLock extends AutoCloseable {

    /**
     * 返回业务锁键。
     * @return 业务锁键
     */
    String key();

    /**
     * 返回成功获取锁的时间。
     * @return 获取时间
     */
    Instant acquiredAt();

    /**
     * 返回锁的预期到期时间。
     * @return 预期到期时间
     */
    Instant expiresAt();

    /**
     * 释放当前句柄拥有的锁。
     */
    @Override
    void close();
}
