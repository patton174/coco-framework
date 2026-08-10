package io.github.coco.feature.tenant.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Coco 租户上下文执行器适配器测试。
 * <p>
 * 验证任务提交时捕获、空上下文传播、worker 上下文恢复以及委托异常语义。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoTenantContextExecutorTest {

    @AfterEach
    void clearContext() {
        CocoTenantContextHolder.clear();
    }

    @Test
    void capturesContextAtSubmissionAndRestoresWorkerContextAfterSuccess() {
        ManualExecutor delegate = new ManualExecutor();
        Executor executor = new CocoTenantContextExecutor(delegate);
        AtomicReference<String> observedTenantId = new AtomicReference<>();

        CocoTenantContextHolder.set(context("submission"));
        executor.execute(() -> observedTenantId.set(currentTenantId()));

        CocoTenantContextHolder.set(context("worker"));
        delegate.runNext();

        assertEquals("submission", observedTenantId.get());
        assertEquals("worker", currentTenantId());
    }

    @Test
    void propagatesEmptyContextAndRestoresWorkerContext() {
        ManualExecutor delegate = new ManualExecutor();
        Executor executor = new CocoTenantContextExecutor(delegate);
        AtomicReference<Boolean> observedEmptyContext = new AtomicReference<>();

        CocoTenantContextHolder.clear();
        executor.execute(() -> observedEmptyContext.set(CocoTenantContextHolder.current().isEmpty()));

        CocoTenantContextHolder.set(context("worker"));
        delegate.runNext();

        assertTrue(observedEmptyContext.get());
        assertEquals("worker", currentTenantId());
    }

    @Test
    void restoresWorkerContextAfterTaskException() {
        ManualExecutor delegate = new ManualExecutor();
        Executor executor = new CocoTenantContextExecutor(delegate);
        IllegalStateException taskFailure = new IllegalStateException("task failed");

        CocoTenantContextHolder.set(context("submission"));
        executor.execute(() -> {
            assertEquals("submission", currentTenantId());
            throw taskFailure;
        });

        CocoTenantContextHolder.set(context("worker"));
        assertSame(taskFailure, assertThrows(IllegalStateException.class, delegate::runNext));
        assertEquals("worker", currentTenantId());
    }

    @Test
    void preservesDelegateRejection() {
        RejectedExecutionException rejection = new RejectedExecutionException("rejected");
        Executor executor = new CocoTenantContextExecutor(command -> {
            throw rejection;
        });

        assertSame(rejection, assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
        })));
    }

    @Test
    void rejectsNullDelegateAndCommandBeforeDelegation() {
        assertThrows(NullPointerException.class, () -> new CocoTenantContextExecutor(null));

        ManualExecutor delegate = new ManualExecutor();
        Executor executor = new CocoTenantContextExecutor(delegate);

        assertThrows(NullPointerException.class, () -> executor.execute(null));
        assertTrue(delegate.isEmpty());
    }

    private static CocoTenantContext context(String tenantId) {
        return CocoTenantContext.of(tenantId, "Tenant " + tenantId);
    }

    private static String currentTenantId() {
        return CocoTenantContextHolder.requireCurrent().tenantId();
    }

    private static final class ManualExecutor implements Executor {

        private final Deque<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            this.tasks.addLast(command);
        }

        void runNext() {
            this.tasks.removeFirst().run();
        }

        boolean isEmpty() {
            return this.tasks.isEmpty();
        }
    }
}
