package io.github.coco.feature.datapermission.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import io.github.coco.context.CocoContextScope;
import io.github.coco.context.CocoContextSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Coco 数据权限上下文持有器测试。
 * <p>
 * 验证数据权限上下文可以被捕获、恢复，并包装跨线程任务。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoDataPermissionContextHolderTest {

    @AfterEach
    void clearContext() {
        CocoDataPermissionContextHolder.clear();
    }

    @Test
    void capturedContextWrapsCallableAndRestoresCallerContext() throws Exception {
        CocoDataPermissionContextHolder.set(context("orders"));
        Callable<String> callable = CocoDataPermissionContextHolder.wrap(
                () -> CocoDataPermissionContextHolder.requireCurrent().rule("orders").orElseThrow().resource());
        CocoDataPermissionContextHolder.set(context("products"));

        assertEquals("orders", callable.call());
        assertEquals("products", currentResource());
    }

    @Test
    void restoringEmptySnapshotClearsContextTemporarily() {
        CocoContextSnapshot snapshot = CocoDataPermissionContextHolder.capture();
        CocoDataPermissionContextHolder.set(context("products"));

        try (CocoContextScope ignored = CocoDataPermissionContextHolder.restore(snapshot)) {
            assertTrue(CocoDataPermissionContextHolder.current().isEmpty());
        }

        assertEquals("products", currentResource());
    }

    @Test
    void nestedScopesRestoreOuterAndRootContexts() {
        CocoDataPermissionContextHolder.set(context("root"));

        try (CocoContextScope outer = CocoDataPermissionContextHolder.push(context("outer"))) {
            assertEquals("outer", currentResource());
            try (CocoContextScope inner = CocoDataPermissionContextHolder.push(context("inner"))) {
                assertEquals("inner", currentResource());
                CocoDataPermissionContextHolder.clear();
                assertTrue(CocoDataPermissionContextHolder.current().isEmpty());
            }
            assertEquals("outer", currentResource());
        }

        assertEquals("root", currentResource());
    }

    @Test
    void outOfOrderCloseCannotOverwriteLaterExplicitContext() {
        CocoDataPermissionContextHolder.set(context("root"));
        CocoContextScope outer = CocoDataPermissionContextHolder.push(context("outer"));
        CocoContextScope inner = CocoDataPermissionContextHolder.push(context("inner"));

        outer.close();
        assertEquals("root", currentResource());
        CocoDataPermissionContextHolder.set(context("later"));
        inner.close();

        assertEquals("later", currentResource());
    }

    @Test
    void duplicateCloseCannotOverwriteLaterExplicitContext() {
        CocoDataPermissionContextHolder.set(context("root"));
        CocoContextScope scope = CocoDataPermissionContextHolder.push(context("scoped"));

        scope.close();
        CocoDataPermissionContextHolder.set(context("later"));
        scope.close();

        assertEquals("later", currentResource());
    }

    @Test
    void foreignCloseDoesNotConsumeOwnerClose() throws Exception {
        CocoDataPermissionContextHolder.set(context("root"));
        CocoContextScope scope = CocoDataPermissionContextHolder.push(context("scoped"));
        Thread[] foreignThreads = new Thread[8];
        for (int i = 0; i < foreignThreads.length; i++) {
            foreignThreads[i] = new Thread(scope::close);
            foreignThreads[i].start();
        }
        for (Thread foreignThread : foreignThreads) {
            foreignThread.join();
        }

        assertEquals("scoped", currentResource());
        scope.close();

        assertEquals("root", currentResource());
    }

    @Test
    void emptyRootIsRemovedAfterOwnerClosesScope() throws Exception {
        CocoContextScope scope = CocoDataPermissionContextHolder.push(context("scoped"));
        Thread foreign = new Thread(scope::close);
        foreign.start();
        foreign.join();

        scope.close();

        assertTrue(CocoDataPermissionContextHolder.current().isEmpty());
        Field contextField = CocoDataPermissionContextHolder.class.getDeclaredField("DATA_PERMISSION_CONTEXT");
        contextField.setAccessible(true);
        ThreadLocal<?> threadLocal = (ThreadLocal<?>) contextField.get(null);
        assertNull(threadLocal.get());
    }

    @Test
    void snapshotRestoreSurvivesCallbackSetAndClear() {
        CocoDataPermissionContextHolder.set(context("request"));
        CocoContextSnapshot snapshot = CocoDataPermissionContextHolder.capture();
        CocoDataPermissionContextHolder.set(context("worker"));

        try (CocoContextScope ignored = snapshot.restore()) {
            assertEquals("request", currentResource());
            CocoDataPermissionContextHolder.set(context("mutated"));
        }
        assertEquals("worker", currentResource());

        try (CocoContextScope ignored = snapshot.restore()) {
            assertEquals("request", currentResource());
            CocoDataPermissionContextHolder.clear();
            assertTrue(CocoDataPermissionContextHolder.current().isEmpty());
        }
        assertEquals("worker", currentResource());
    }

    @Test
    void wrappedCallbacksRestoreWorkerContextAfterSetAndClear() throws Exception {
        CocoDataPermissionContextHolder.set(context("request"));
        Runnable setCallback = CocoDataPermissionContextHolder.wrap(() -> {
            CocoDataPermissionContextHolder.set(context("mutated"));
        });
        Callable<Boolean> clearCallback = CocoDataPermissionContextHolder.wrap(() -> {
            CocoDataPermissionContextHolder.clear();
            return CocoDataPermissionContextHolder.current().isEmpty();
        });
        CocoDataPermissionContextHolder.set(context("worker"));

        setCallback.run();
        assertEquals("worker", currentResource());
        assertTrue(clearCallback.call());
        assertEquals("worker", currentResource());
    }

    @Test
    void callWithContextRestoresRootAfterCallbackSetClearAndFailure() {
        CocoDataPermissionContextHolder.set(context("root"));

        assertEquals("mutated", CocoDataPermissionContextHolder.callWithContext(context("temporary"), () -> {
            CocoDataPermissionContextHolder.set(context("mutated"));
            return currentResource();
        }));
        assertEquals("root", currentResource());

        assertTrue(CocoDataPermissionContextHolder.callWithContext(context("temporary"), () -> {
            CocoDataPermissionContextHolder.clear();
            return CocoDataPermissionContextHolder.current().isEmpty();
        }));
        assertEquals("root", currentResource());

        assertThrows(IllegalStateException.class,
                () -> CocoDataPermissionContextHolder.callWithContext(context("temporary"), () -> {
                    CocoDataPermissionContextHolder.set(context("mutated"));
                    throw new IllegalStateException("failure");
                }));
        assertEquals("root", currentResource());
    }

    @Test
    void executorNeverLeaksCallbackSetOrClearIntoReusedWorker() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> CocoDataPermissionContextHolder.set(context("worker"))).get(5, TimeUnit.SECONDS);
            Executor executor = CocoDataPermissionContextHolder.executor(worker);

            CocoDataPermissionContextHolder.set(context("request-set"));
            CompletableFuture<String> setResult = new CompletableFuture<>();
            executor.execute(() -> complete(setResult, () -> {
                String captured = currentResource();
                CocoDataPermissionContextHolder.set(context("mutated"));
                return captured;
            }));
            assertEquals("request-set", setResult.get(5, TimeUnit.SECONDS));
            assertEquals("worker", worker.submit(CocoDataPermissionContextHolderTest::currentResource)
                    .get(5, TimeUnit.SECONDS));

            CocoDataPermissionContextHolder.set(context("request-clear"));
            CompletableFuture<Boolean> clearResult = new CompletableFuture<>();
            executor.execute(() -> complete(clearResult, () -> {
                CocoDataPermissionContextHolder.clear();
                return CocoDataPermissionContextHolder.current().isEmpty();
            }));
            assertTrue(clearResult.get(5, TimeUnit.SECONDS));
            assertEquals("worker", worker.submit(CocoDataPermissionContextHolderTest::currentResource)
                    .get(5, TimeUnit.SECONDS));
        }
        finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void executorRestoresReusedWorkerAfterCallbackFailure() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        LinkedBlockingQueue<Future<?>> submitted = new LinkedBlockingQueue<>();
        try {
            worker.submit(() -> CocoDataPermissionContextHolder.set(context("worker"))).get(5, TimeUnit.SECONDS);
            Executor executor = CocoDataPermissionContextHolder.executor(command -> submitted.add(worker.submit(command)));
            CocoDataPermissionContextHolder.set(context("request"));

            executor.execute(() -> {
                CocoDataPermissionContextHolder.set(context("mutated"));
                throw new IllegalStateException("failure");
            });

            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> submitted.take().get(5, TimeUnit.SECONDS));
            assertEquals(IllegalStateException.class, failure.getCause().getClass());
            assertEquals("worker", worker.submit(CocoDataPermissionContextHolderTest::currentResource)
                    .get(5, TimeUnit.SECONDS));
        }
        finally {
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void explicitClearWithoutScopeRemovesCurrentContext() {
        CocoDataPermissionContextHolder.set(context("root"));

        CocoDataPermissionContextHolder.clear();

        assertFalse(CocoDataPermissionContextHolder.current().isPresent());
    }

    private static <T> void complete(CompletableFuture<T> future, Callable<T> callable) {
        try {
            future.complete(callable.call());
        }
        catch (Throwable ex) {
            future.completeExceptionally(ex);
        }
    }

    private static String currentResource() {
        return CocoDataPermissionContextHolder.requireCurrent().rules().iterator().next().resource();
    }

    private static CocoDataPermissionContext context(String resource) {
        return CocoDataPermissionContext.of(Set.of(CocoDataPermissionRule.all(resource)));
    }
}
