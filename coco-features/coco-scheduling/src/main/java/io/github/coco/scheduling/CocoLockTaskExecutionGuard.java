package io.github.coco.scheduling;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.github.coco.feature.lock.CocoLockHandle;
import io.github.coco.feature.lock.CocoLockManager;
import io.github.coco.feature.lock.CocoLockRequest;
import io.github.coco.feature.lock.CocoLockResult;
import io.github.coco.feature.lock.CocoLockStore;

/** 基于 {@link CocoLockManager} 的跨实例任务执行 guard。 */
public final class CocoLockTaskExecutionGuard implements CocoTaskExecutionGuard {

    private static final String TASK_LOCK_NAMESPACE = "coco:scheduling:task:";

    private final CocoLockManager lockManager;
    private final CocoSchedulingProperties.GuardProperties properties;
    private final ThreadLocal<Map<String, ArrayDeque<CocoLockHandle>>> heldLocks = ThreadLocal.withInitial(HashMap::new);

    /** 创建 CocoLock 任务执行 guard。 */
    public CocoLockTaskExecutionGuard(CocoLockManager lockManager, CocoSchedulingProperties.GuardProperties properties) {
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        validate(properties);
    }

    @Override
    public boolean tryAcquire(String taskName) {
        String checkedTaskName = taskName(taskName);
        CocoLockResult result = this.lockManager.tryAcquire(new CocoLockRequest(TASK_LOCK_NAMESPACE + checkedTaskName,
                this.properties.getLease(), this.properties.getWait(), this.properties.getPollInterval()));
        if (result.status() == CocoLockStore.AcquireResult.CONTENDED) {
            return false;
        }
        if (!result.acquired()) {
            throw new IllegalStateException("Coco lock store is unavailable for scheduled task guard");
        }
        this.heldLocks.get().computeIfAbsent(checkedTaskName, ignored -> new ArrayDeque<>()).push(result.handle());
        return true;
    }

    @Override
    public void release(String taskName) {
        String checkedTaskName = taskName(taskName);
        Map<String, ArrayDeque<CocoLockHandle>> current = this.heldLocks.get();
        ArrayDeque<CocoLockHandle> handles = current.get(checkedTaskName);
        if (handles == null || handles.isEmpty()) {
            return;
        }
        CocoLockHandle handle = handles.pop();
        if (handles.isEmpty()) {
            current.remove(checkedTaskName);
        }
        if (current.isEmpty()) {
            this.heldLocks.remove();
        }
        handle.close();
    }

    private static void validate(CocoSchedulingProperties.GuardProperties properties) {
        new CocoLockRequest(TASK_LOCK_NAMESPACE + "validation", properties.getLease(), properties.getWait(),
                properties.getPollInterval());
    }

    private static String taskName(String taskName) {
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("taskName must not be blank");
        }
        return taskName.trim();
    }
}
