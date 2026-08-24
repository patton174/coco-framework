package io.github.coco.scheduling;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于原子状态的进程内任务执行互斥实现。
 *
 * @since 1.0.0
 */
public final class InMemoryCocoTaskExecutionGuard implements CocoTaskExecutionGuard {

    private final ConcurrentHashMap<String, AtomicBoolean> executions = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String taskName) {
        return this.executions.computeIfAbsent(taskName, ignored -> new AtomicBoolean()).compareAndSet(false, true);
    }

    @Override
    public void release(String taskName) {
        AtomicBoolean execution = this.executions.get(taskName);
        if (execution != null) {
            execution.set(false);
        }
    }
}
