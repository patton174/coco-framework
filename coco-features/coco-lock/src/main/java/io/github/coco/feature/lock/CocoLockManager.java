package io.github.coco.feature.lock;

import java.time.Duration;
import java.util.Optional;

/**
 * Coco 锁管理器 SPI。
 * <p>
 * 返回空值表示在等待期限内没有取得锁。连接、Redis 命令或线程中断等基础设施错误必须以异常向调用方暴露，不能伪装成空值。
 * </p>
 */
public interface CocoLockManager extends AutoCloseable {

    /**
     * 尝试获取指定业务键的锁。
     * @param key 非空白业务锁键
     * @param waitTime 最长等待时间，允许为零
     * @param leaseTime 锁租期，必须为正数
     * @return 成功时的锁句柄；等待超时时为空
     */
    Optional<CocoLock> tryLock(String key, Duration waitTime, Duration leaseTime);

    /**
     * 关闭管理器并拒绝后续获取请求。
     * <p>
     * 已成功取得的锁仍只能由原句柄和原线程释放。
     * </p>
     */
    @Override
    void close();
}
