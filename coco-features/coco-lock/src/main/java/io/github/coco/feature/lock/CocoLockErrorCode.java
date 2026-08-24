package io.github.coco.feature.lock;

import io.github.coco.exception.CocoBusinessCode;

/** Coco 锁模块对外错误码。 */
public enum CocoLockErrorCode implements CocoBusinessCode {
    /** 锁键缺失、无效或表达式无法求值。 */
    INVALID_KEY(40060, "coco.lock.invalid-key"),
    /** 在有限等待时间内未获得锁。 */
    TIMED_OUT(40960, "coco.lock.timed-out"),
    /** 锁存储不可用。 */
    UNAVAILABLE(50360, "coco.lock.unavailable"),
    /** 注解锁拒绝异步或响应式返回类型。 */
    ASYNCHRONOUS_RETURN(50060, "coco.lock.asynchronous-return"),
    /** 等待锁时线程被中断。 */
    INTERRUPTED(50361, "coco.lock.interrupted");

    private final int code;
    private final String messageCode;

    CocoLockErrorCode(int code, String messageCode) {
        this.code = code;
        this.messageCode = messageCode;
    }

    @Override public int code() { return this.code; }
    @Override public String messageCode() { return this.messageCode; }
}
