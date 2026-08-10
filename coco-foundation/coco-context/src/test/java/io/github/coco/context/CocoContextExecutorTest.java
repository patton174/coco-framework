package io.github.coco.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.context.trace.CocoTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Coco 上下文执行器测试。
 * <p>
 * 验证提交时捕获、worker 线程上下文恢复以及参数校验语义。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-context}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoContextExecutorTest {

    @AfterEach
    void clearContext() {
        CocoRequestContextHolder.clear();
    }

    @Test
    void wrapCapturesContextAtSubmissionTime() throws Exception {
        try (SingleThreadExecutor worker = new SingleThreadExecutor()) {
            CocoRequestContextHolder.set(CocoRequestContext.of("captured", "GET", "/captured"));
            Executor executor = CocoContextExecutor.wrap(worker, CocoRequestContextHolder::capture);

            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> observedTraceId = new AtomicReference<>();
            AtomicReference<String> observedRequestId = new AtomicReference<>();

            executor.execute(() -> {
                entered.countDown();
                try {
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(ex);
                }
                observedTraceId.set(CocoTraceContext.currentTraceId().orElseThrow());
                observedRequestId.set(CocoRequestContextHolder.current().orElseThrow().traceId());
                done.countDown();
            });

            assertTrue(entered.await(5, TimeUnit.SECONDS));
            CocoRequestContextHolder.set(CocoRequestContext.of("current", "POST", "/current"));
            release.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals("captured", observedTraceId.get());
            assertEquals("captured", observedRequestId.get());
            assertEquals("current", CocoRequestContextHolder.current().orElseThrow().traceId());
            assertEquals("current", CocoTraceContext.currentTraceId().orElseThrow());
        }
    }

    @Test
    void wrapRestoresWorkerContextAfterSuccess() throws Exception {
        try (SingleThreadExecutor worker = new SingleThreadExecutor()) {
            CountDownLatch prepared = new CountDownLatch(1);
            worker.execute(() -> {
                CocoRequestContextHolder.set(CocoRequestContext.of("worker", "GET", "/worker"));
                prepared.countDown();
            });
            assertTrue(prepared.await(5, TimeUnit.SECONDS));

            CocoRequestContextHolder.set(CocoRequestContext.of("submission", "POST", "/submission"));
            Executor executor = CocoContextExecutor.wrap(worker, CocoRequestContextHolder::capture);
            CountDownLatch executed = new CountDownLatch(1);
            executor.execute(() -> {
                assertEquals("submission", CocoRequestContextHolder.current().orElseThrow().traceId());
                assertEquals("submission", CocoTraceContext.currentTraceId().orElseThrow());
                executed.countDown();
            });
            assertTrue(executed.await(5, TimeUnit.SECONDS));

            CountDownLatch verified = new CountDownLatch(1);
            AtomicReference<String> afterTraceId = new AtomicReference<>();
            AtomicReference<String> afterRequestId = new AtomicReference<>();
            worker.execute(() -> {
                afterTraceId.set(CocoTraceContext.currentTraceId().orElseThrow());
                afterRequestId.set(CocoRequestContextHolder.current().orElseThrow().traceId());
                verified.countDown();
            });

            assertTrue(verified.await(5, TimeUnit.SECONDS));
            assertEquals("worker", afterTraceId.get());
            assertEquals("worker", afterRequestId.get());
        }
    }

    @Test
    void wrapRestoresWorkerContextAfterTaskException() throws Exception {
        try (SingleThreadExecutor worker = new SingleThreadExecutor()) {
            CountDownLatch prepared = new CountDownLatch(1);
            worker.execute(() -> {
                CocoRequestContextHolder.set(CocoRequestContext.of("worker", "GET", "/worker"));
                prepared.countDown();
            });
            assertTrue(prepared.await(5, TimeUnit.SECONDS));

            CocoRequestContextHolder.set(CocoRequestContext.of("submission", "POST", "/submission"));
            Executor executor = CocoContextExecutor.wrap(worker, CocoRequestContextHolder::capture);
            CountDownLatch failed = new CountDownLatch(1);
            executor.execute(() -> {
                assertEquals("submission", CocoRequestContextHolder.current().orElseThrow().traceId());
                failed.countDown();
                throw new IllegalStateException("boom");
            });

            assertTrue(failed.await(5, TimeUnit.SECONDS));
            CountDownLatch failureObserved = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            worker.execute(() -> {
                failure.set(worker.lastFailure());
                failureObserved.countDown();
            });
            assertTrue(failureObserved.await(5, TimeUnit.SECONDS));
            assertEquals("boom", failure.get().getMessage());

            CountDownLatch verified = new CountDownLatch(1);
            AtomicReference<String> afterTraceId = new AtomicReference<>();
            AtomicReference<String> afterRequestId = new AtomicReference<>();
            worker.execute(() -> {
                afterTraceId.set(CocoTraceContext.currentTraceId().orElseThrow());
                afterRequestId.set(CocoRequestContextHolder.current().orElseThrow().traceId());
                verified.countDown();
            });

            assertTrue(verified.await(5, TimeUnit.SECONDS));
            assertEquals("worker", afterTraceId.get());
            assertEquals("worker", afterRequestId.get());
        }
    }

    @Test
    void wrapRejectsNullArgumentsAndNullSnapshot() {
        assertThrows(NullPointerException.class,
                () -> CocoContextExecutor.wrap(null, CocoRequestContextHolder::capture));
        assertThrows(NullPointerException.class, () -> CocoContextExecutor.wrap(Runnable::run, null));

        Executor executor = CocoContextExecutor.wrap(Runnable::run, () -> null);
        assertThrows(NullPointerException.class, () -> executor.execute(() -> {
        }));
        assertThrows(NullPointerException.class, () -> executor.execute(null));
    }

    private static final class SingleThreadExecutor implements Executor, AutoCloseable {

        private final Thread workerThread;

        private final Deque<Runnable> tasks = new ArrayDeque<>();

        private final Object monitor = new Object();

        private final AtomicReference<Throwable> lastFailure = new AtomicReference<>();

        private volatile boolean closed;

        private SingleThreadExecutor() {
            this.workerThread = new Thread(this::runLoop, "coco-context-executor-test");
            this.workerThread.start();
        }

        @Override
        public void execute(Runnable command) {
            Objects.requireNonNull(command, "command must not be null");
            synchronized (this.monitor) {
                if (this.closed) {
                    throw new IllegalStateException("executor is closed");
                }
                this.tasks.addLast(command);
                this.monitor.notifyAll();
            }
        }

        Throwable lastFailure() {
            return this.lastFailure.get();
        }

        private void runLoop() {
            while (true) {
                Runnable task;
                synchronized (this.monitor) {
                    while (this.tasks.isEmpty() && !this.closed) {
                        try {
                            this.monitor.wait();
                        }
                        catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    if (this.tasks.isEmpty() && this.closed) {
                        return;
                    }
                    task = this.tasks.removeFirst();
                }
                try {
                    task.run();
                }
                catch (Throwable ex) {
                    this.lastFailure.set(ex);
                }
            }
        }
        @Override
        public void close() throws InterruptedException {
            synchronized (this.monitor) {
                this.closed = true;
                this.monitor.notifyAll();
            }
            this.workerThread.join(TimeUnit.SECONDS.toMillis(5));
        }
    }
}
