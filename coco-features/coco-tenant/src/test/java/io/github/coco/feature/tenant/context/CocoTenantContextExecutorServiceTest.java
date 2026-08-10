package io.github.coco.feature.tenant.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Coco 租户上下文执行器服务适配器测试。
 * <p>
 * 验证所有任务提交入口的上下文捕获、批量执行、worker 上下文恢复及生命周期委托。
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
class CocoTenantContextExecutorServiceTest {

    @AfterEach
    void clearContext() {
        CocoTenantContextHolder.clear();
    }

    @Test
    void capturesEveryExecuteAndSubmitTaskAtSubmissionTime() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        CountDownLatch blockerEntered = new CountDownLatch(1);
        CountDownLatch releaseBlocker = new CountDownLatch(1);
        try {
            Future<?> blocker = delegate.submit(() -> {
                blockerEntered.countDown();
                await(releaseBlocker);
            });
            assertTrue(blockerEntered.await(5, TimeUnit.SECONDS));

            CocoTenantContextExecutorService executor = new CocoTenantContextExecutorService(delegate);
            AtomicReference<String> executeTenant = new AtomicReference<>();
            AtomicReference<String> runnableTenant = new AtomicReference<>();
            AtomicReference<String> resultRunnableTenant = new AtomicReference<>();
            CountDownLatch executeDone = new CountDownLatch(1);

            CocoTenantContextHolder.set(context("execute"));
            executor.execute(() -> {
                executeTenant.set(currentTenantId());
                executeDone.countDown();
            });
            CocoTenantContextHolder.set(context("runnable"));
            Future<?> runnable = executor.submit(() -> runnableTenant.set(currentTenantId()));
            CocoTenantContextHolder.set(context("callable"));
            Future<String> callable = executor.submit(CocoTenantContextExecutorServiceTest::currentTenantId);
            CocoTenantContextHolder.set(context("result-runnable"));
            Future<String> resultRunnable = executor.submit(
                    () -> resultRunnableTenant.set(currentTenantId()), "result");
            CocoTenantContextHolder.set(context("caller"));

            releaseBlocker.countDown();
            blocker.get(5, TimeUnit.SECONDS);
            assertTrue(executeDone.await(5, TimeUnit.SECONDS));
            assertNull(runnable.get(5, TimeUnit.SECONDS));
            assertEquals("callable", callable.get(5, TimeUnit.SECONDS));
            assertEquals("result", resultRunnable.get(5, TimeUnit.SECONDS));

            assertEquals("execute", executeTenant.get());
            assertEquals("runnable", runnableTenant.get());
            assertEquals("result-runnable", resultRunnableTenant.get());
            assertEquals("caller", currentTenantId());
        }
        finally {
            releaseBlocker.countDown();
            stop(delegate);
        }
    }

    @Test
    void restoresWorkerContextAfterSubmitSuccessAndException() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        try {
            delegate.submit(() -> CocoTenantContextHolder.set(context("worker"))).get(5, TimeUnit.SECONDS);
            CocoTenantContextExecutorService executor = new CocoTenantContextExecutorService(delegate);

            CocoTenantContextHolder.set(context("success"));
            assertEquals("success", executor.submit(CocoTenantContextExecutorServiceTest::currentTenantId)
                    .get(5, TimeUnit.SECONDS));
            assertEquals("worker", delegate.submit(CocoTenantContextExecutorServiceTest::currentTenantId)
                    .get(5, TimeUnit.SECONDS));

            IllegalStateException taskFailure = new IllegalStateException("task failed");
            CocoTenantContextHolder.set(context("failure"));
            Future<Void> failed = executor.submit((Callable<Void>) () -> {
                assertEquals("failure", currentTenantId());
                throw taskFailure;
            });
            ExecutionException executionException = assertThrows(ExecutionException.class,
                    () -> failed.get(5, TimeUnit.SECONDS));

            assertSame(taskFailure, executionException.getCause());
            assertEquals("worker", delegate.submit(CocoTenantContextExecutorServiceTest::currentTenantId)
                    .get(5, TimeUnit.SECONDS));
        }
        finally {
            stop(delegate);
        }
    }

    @Test
    void propagatesEmptyContextAndRestoresWorkerContext() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        try {
            delegate.submit(() -> CocoTenantContextHolder.set(context("worker"))).get(5, TimeUnit.SECONDS);
            CocoTenantContextExecutorService executor = new CocoTenantContextExecutorService(delegate);
            CocoTenantContextHolder.clear();

            assertTrue(executor.submit(() -> CocoTenantContextHolder.current().isEmpty()).get(5, TimeUnit.SECONDS));
            assertEquals("worker", delegate.submit(CocoTenantContextExecutorServiceTest::currentTenantId)
                    .get(5, TimeUnit.SECONDS));
        }
        finally {
            stop(delegate);
        }
    }

    @Test
    void invokeAllAndInvokeAnyCaptureEachTaskAndRestoreWorkerContext() throws Exception {
        ExecutorService delegate = Executors.newSingleThreadExecutor();
        try {
            delegate.submit(() -> CocoTenantContextHolder.set(context("worker"))).get(5, TimeUnit.SECONDS);
            CocoTenantContextExecutorService executor = new CocoTenantContextExecutorService(delegate);
            CocoTenantContextHolder.set(context("batch"));

            List<Callable<String>> tasks = List.of(
                    () -> {
                        String tenantId = currentTenantId();
                        CocoTenantContextHolder.set(context("mutated"));
                        return tenantId;
                    },
                    CocoTenantContextExecutorServiceTest::currentTenantId);

            assertFutureValues(List.of("batch", "batch"), executor.invokeAll(tasks));
            assertFutureValues(List.of("batch", "batch"), executor.invokeAll(tasks, 5, TimeUnit.SECONDS));
            assertEquals("batch", executor.invokeAny(tasks));
            assertEquals("batch", executor.invokeAny(tasks, 5, TimeUnit.SECONDS));
            assertEquals("worker", delegate.submit(CocoTenantContextExecutorServiceTest::currentTenantId)
                    .get(5, TimeUnit.SECONDS));
        }
        finally {
            stop(delegate);
        }
    }

    @Test
    void delegatesLifecycleWithoutMaintainingIndependentState() throws Exception {
        LifecycleExecutorService delegate = new LifecycleExecutorService();
        CocoTenantContextExecutorService executor = new CocoTenantContextExecutorService(delegate);

        assertFalse(executor.isShutdown());
        assertFalse(executor.isTerminated());

        executor.shutdown();
        assertTrue(delegate.shutdownCalled);
        assertTrue(executor.isShutdown());

        assertSame(delegate.shutdownNowTasks, executor.shutdownNow());
        assertTrue(delegate.shutdownNowCalled);
        assertTrue(executor.isTerminated());

        assertTrue(executor.awaitTermination(37, TimeUnit.MILLISECONDS));
        assertEquals(37, delegate.awaitedTimeout);
        assertSame(TimeUnit.MILLISECONDS, delegate.awaitedUnit);
    }

    @Test
    void preservesDelegateRejectionAcrossSubmissionMethods() throws Exception {
        RejectedExecutionException rejection = new RejectedExecutionException("rejected");
        CocoTenantContextExecutorService executor = new CocoTenantContextExecutorService(
                new RejectingExecutorService(rejection));

        assertSame(rejection, assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {
        })));
        assertSame(rejection, assertThrows(RejectedExecutionException.class, () -> executor.submit(() -> {
        })));
        assertSame(rejection, assertThrows(RejectedExecutionException.class,
                () -> executor.invokeAll(List.of(() -> "value"))));
        assertSame(rejection, assertThrows(RejectedExecutionException.class,
                () -> executor.invokeAny(List.of(() -> "value"))));
    }

    @Test
    void rejectsNullTaskParametersWithStandardNullPointerSemantics() throws Exception {
        assertThrows(NullPointerException.class, () -> new CocoTenantContextExecutorService(null));

        ExecutorService delegate = Executors.newSingleThreadExecutor();
        try {
            CocoTenantContextExecutorService executor = new CocoTenantContextExecutorService(delegate);
            Collection<Callable<Object>> nullTasks = null;
            Collection<Callable<Object>> tasksWithNull = Collections.singletonList(null);
            List<Callable<Object>> validTasks = List.of(() -> "value");

            assertThrows(NullPointerException.class, () -> executor.execute(null));
            assertThrows(NullPointerException.class, () -> executor.submit((Runnable) null));
            assertThrows(NullPointerException.class, () -> executor.submit((Callable<Object>) null));
            assertThrows(NullPointerException.class, () -> executor.submit((Runnable) null, "result"));
            assertThrows(NullPointerException.class, () -> executor.invokeAll(nullTasks));
            assertThrows(NullPointerException.class,
                    () -> executor.invokeAll(nullTasks, 1, TimeUnit.SECONDS));
            assertThrows(NullPointerException.class, () -> executor.invokeAny(nullTasks));
            assertThrows(NullPointerException.class,
                    () -> executor.invokeAny(nullTasks, 1, TimeUnit.SECONDS));
            assertThrows(NullPointerException.class, () -> executor.invokeAll(tasksWithNull));
            assertThrows(NullPointerException.class, () -> executor.invokeAny(tasksWithNull));
            assertThrows(NullPointerException.class, () -> executor.invokeAll(validTasks, 1, null));
            assertThrows(NullPointerException.class, () -> executor.invokeAny(validTasks, 1, null));
            assertThrows(NullPointerException.class, () -> executor.awaitTermination(1, null));
        }
        finally {
            stop(delegate);
        }
    }

    private static CocoTenantContext context(String tenantId) {
        return CocoTenantContext.of(tenantId, "Tenant " + tenantId);
    }

    private static String currentTenantId() {
        return CocoTenantContextHolder.requireCurrent().tenantId();
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private static void assertFutureValues(List<String> expected, List<Future<String>> futures) throws Exception {
        assertEquals(expected.size(), futures.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), futures.get(i).get(5, TimeUnit.SECONDS));
        }
    }

    private static void stop(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static final class LifecycleExecutorService extends AbstractExecutorService {

        private final List<Runnable> shutdownNowTasks = List.of(() -> {
        });

        private boolean shutdownCalled;

        private boolean shutdownNowCalled;

        private long awaitedTimeout;

        private TimeUnit awaitedUnit;

        @Override
        public void shutdown() {
            this.shutdownCalled = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            this.shutdownCalled = true;
            this.shutdownNowCalled = true;
            return this.shutdownNowTasks;
        }

        @Override
        public boolean isShutdown() {
            return this.shutdownCalled;
        }

        @Override
        public boolean isTerminated() {
            return this.shutdownNowCalled;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            this.awaitedTimeout = timeout;
            this.awaitedUnit = unit;
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    private static final class RejectingExecutorService extends AbstractExecutorService {

        private final RejectedExecutionException rejection;

        private RejectingExecutorService(RejectedExecutionException rejection) {
            this.rejection = rejection;
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public void execute(Runnable command) {
            throw this.rejection;
        }
    }
}
