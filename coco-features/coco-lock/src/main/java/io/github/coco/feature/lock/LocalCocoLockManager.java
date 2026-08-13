package io.github.coco.feature.lock;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于公平 {@link ReentrantLock} 的进程内 Coco 锁管理器。
 * <p>
 * 同一线程对同一键不支持重入，第二次获取会抛出 {@link CocoLockException}。本地锁不因为租期自动释放，租期仅向
 * {@link CocoLock} 提供一致的到期元数据，避免在拥有者线程仍在执行时错误解锁。
 * </p>
 */
public final class LocalCocoLockManager implements CocoLockManager {

    private final ConcurrentHashMap<String, LockState> states = new ConcurrentHashMap<>();

    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<CocoLock> tryLock(String key, Duration waitTime, Duration leaseTime) {
        validateRequest(key, waitTime, leaseTime);
        if (this.closed.get()) {
            throw new IllegalStateException("Coco lock manager is closed");
        }

        LockState state = this.states.compute(key, (ignored, existing) -> {
            LockState current = existing == null ? new LockState() : existing;
            current.references++;
            return current;
        });
        boolean acquired = false;
        try {
            if (state.lock.isHeldByCurrentThread()) {
                throw new CocoLockException("Local Coco lock is not reentrant: " + key);
            }
            acquired = state.lock.tryLock(waitTime.toNanos(), TimeUnit.NANOSECONDS);
            if (!acquired) {
                releaseReference(key, state);
                return Optional.empty();
            }
            if (this.closed.get()) {
                state.lock.unlock();
                releaseReference(key, state);
                throw new IllegalStateException("Coco lock manager is closed");
            }
            Instant acquiredAt = Instant.now();
            return Optional.of(new LocalCocoLock(key, acquiredAt, acquiredAt.plus(leaseTime), state));
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            releaseReference(key, state);
            throw new CocoLockException("Interrupted while acquiring Coco lock: " + key, ex);
        }
        catch (RuntimeException ex) {
            if (!acquired) {
                releaseReference(key, state);
            }
            throw ex;
        }
    }

    /**
     * 返回当前保留的键状态数量，供模块测试验证回收行为。
     * @return 键状态数量
     */
    int stateCount() {
        return this.states.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        this.closed.set(true);
    }

    private void releaseReference(String key, LockState state) {
        this.states.computeIfPresent(key, (ignored, current) -> {
            if (current != state) {
                return current;
            }
            current.references--;
            return current.references == 0 && !current.lock.isLocked() && !current.lock.hasQueuedThreads() ? null : current;
        });
    }

    private static void validateRequest(String key, Duration waitTime, Duration leaseTime) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Coco lock key must not be blank");
        }
        if (waitTime == null || waitTime.isNegative()) {
            throw new IllegalArgumentException("Coco lock waitTime must not be negative");
        }
        if (leaseTime == null || leaseTime.isZero() || leaseTime.isNegative()) {
            throw new IllegalArgumentException("Coco lock leaseTime must be positive");
        }
    }

    private final class LocalCocoLock implements CocoLock {

        private final String key;

        private final Instant acquiredAt;

        private final Instant expiresAt;

        private final LockState state;

        private final Thread owner = Thread.currentThread();

        private final AtomicBoolean released = new AtomicBoolean();

        private LocalCocoLock(String key, Instant acquiredAt, Instant expiresAt, LockState state) {
            this.key = key;
            this.acquiredAt = acquiredAt;
            this.expiresAt = expiresAt;
            this.state = state;
        }

        @Override
        public String key() {
            return this.key;
        }

        @Override
        public Instant acquiredAt() {
            return this.acquiredAt;
        }

        @Override
        public Instant expiresAt() {
            return this.expiresAt;
        }

        @Override
        public void close() {
            if (this.released.get()) {
                return;
            }
            if (Thread.currentThread() != this.owner) {
                throw new IllegalStateException("Coco lock must be released by its acquiring thread");
            }
            if (this.released.compareAndSet(false, true)) {
                try {
                    this.state.lock.unlock();
                }
                finally {
                    releaseReference(this.key, this.state);
                }
            }
        }
    }

    private static final class LockState {

        private final ReentrantLock lock = new ReentrantLock(true);

        private int references;
    }
}
