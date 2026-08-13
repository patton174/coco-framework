package io.github.coco.context.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.coco.context.CocoContextSnapshotFactory;
import io.github.coco.context.trace.CocoTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class CocoContextTaskDecoratorTest {

    @AfterEach
    void clearContext() { CocoTraceContext.clear(); }

    @Test
    void capturesAtDecorationAndRestoresWorkerAfterFailure() throws Exception {
        ThreadPoolTaskExecutor executor = executor();
        try {
            CocoTraceContext.setTraceId("submitted");
            Future<String> result = executor.submit(() -> CocoTraceContext.currentTraceId().orElseThrow());
            CocoTraceContext.setTraceId("changed-after-submit");
            assertEquals("submitted", result.get());
            CocoTraceContext.clear();

            CocoTraceContext.setTraceId("submission-a");
            assertEquals("submission-a", executor.submit(() -> CocoTraceContext.currentTraceId().orElseThrow()).get());
            CocoTraceContext.setTraceId("submission-b");
            assertEquals("submission-b", executor.submit(() -> CocoTraceContext.currentTraceId().orElseThrow()).get());
            CocoTraceContext.clear();

            Future<?> failed = executor.submit(() -> { CocoTraceContext.setTraceId("mutated"); throw new IllegalStateException("failure"); });
            assertThrows(ExecutionException.class, failed::get);
            assertEquals(false, executor.getThreadPoolExecutor().submit(
                    () -> CocoTraceContext.currentTraceId().isPresent()).get());
        } finally { executor.shutdown(); }
    }

    @Test
    void nestedTaskCapturesTheRestoredContext() throws Exception {
        ThreadPoolTaskExecutor outer = executor();
        ThreadPoolTaskExecutor inner = executor();
        try {
            CocoTraceContext.setTraceId("outer");
            assertEquals("outer", outer.submit(() -> inner.submit(
                    () -> CocoTraceContext.currentTraceId().orElseThrow()).get()).get());
        } finally {
            outer.shutdown();
            inner.shutdown();
        }
    }

    @Test
    void restoresExistingWorkerContextWithControlledSingleThreadExecutor() throws Exception {
        CocoContextTaskDecorator decorator = new CocoContextTaskDecorator(new CocoContextSnapshotFactory(List.of(
                new TraceContributor())));
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> CocoTraceContext.setTraceId("worker-old")).get();
            CocoTraceContext.setTraceId("submitted");
            Runnable decorated = decorator.decorate(() ->
                    assertEquals("submitted", CocoTraceContext.currentTraceId().orElseThrow()));
            CocoTraceContext.clear();

            worker.submit(decorated).get();

            assertEquals("worker-old", worker.submit(() -> CocoTraceContext.currentTraceId().orElseThrow()).get());
        }
        finally {
            worker.submit(CocoTraceContext::clear).get();
            worker.shutdownNow();
        }
    }

    private static ThreadPoolTaskExecutor executor() {
        ThreadPoolTaskExecutor executor = plainExecutor();
        configureDecorator(executor);
        return executor;
    }

    private static ThreadPoolTaskExecutor plainExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.initialize();
        return executor;
    }

    private static void configureDecorator(ThreadPoolTaskExecutor executor) {
        executor.setTaskDecorator(new CocoContextTaskDecorator(new CocoContextSnapshotFactory(List.of(
                new TraceContributor()))));
    }

    private static final class TraceContributor implements io.github.coco.context.CocoContextSnapshotContributor {
        @Override public String id() { return "trace"; }
        @Override public int order() { return 0; }
        @Override public io.github.coco.context.CocoContextSnapshot capture() { return CocoTraceContext.capture(); }
    }

}
