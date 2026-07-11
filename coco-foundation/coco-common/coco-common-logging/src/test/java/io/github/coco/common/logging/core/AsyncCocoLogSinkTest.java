package io.github.coco.common.logging.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;

/**
 * Coco 异步日志输出器测试。
 * <p>
 * 验证日志输出器默认以非阻塞方式投递日志，并在队列拥挤时优先保证高等级日志可见。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class AsyncCocoLogSinkTest {

    @Test
    void keepsTwoArgumentConstructorAndStartsDropCountAtZero() throws Exception {
        CapturingSink delegate = new CapturingSink();
        try (AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, 8)) {
            assertEquals(0L, sink.droppedRecordCount());

            sink.log(record(CocoLogLevel.INFO, "hello"));

            assertTrue(delegate.awaitMessages(1));
            assertEquals(List.of("hello"), delegate.messages());
            assertEquals(0L, sink.droppedRecordCount());
        }
    }

    @Test
    void countsAndNotifiesEveryDroppedLowLevelRecord() throws Exception {
        BlockingSink delegate = new BlockingSink("block");
        List<DropEvent> drops = new CopyOnWriteArrayList<>();
        CocoAsyncLogDropListener listener = (level, handleName, totalDropped) ->
                drops.add(new DropEvent(level, handleName, totalDropped));
        try (AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, 1, listener)) {
            fillQueue(delegate, sink);

            sink.log(record("trace-handle", CocoLogLevel.TRACE, "dropped-trace"));
            sink.log(record("debug-handle", CocoLogLevel.DEBUG, "dropped-debug"));
            sink.log(record("info-handle", CocoLogLevel.INFO, "dropped-info"));

            assertEquals(3L, sink.droppedRecordCount());
            assertEquals(List.of(
                    new DropEvent(CocoLogLevel.TRACE, "trace-handle", 1L),
                    new DropEvent(CocoLogLevel.DEBUG, "debug-handle", 2L),
                    new DropEvent(CocoLogLevel.INFO, "info-handle", 3L)), drops);

            delegate.release();
            assertTrue(delegate.awaitMessages(2));
            assertEquals(List.of("block", "queued"), delegate.messages());
        }
    }

    @Test
    void writesWarnSynchronouslyWhenQueueIsFullWithoutCountingDrop() throws Exception {
        BlockingSink delegate = new BlockingSink("block");
        List<DropEvent> drops = new CopyOnWriteArrayList<>();
        String callerThreadName = Thread.currentThread().getName();
        try (AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, 1,
                (level, handleName, totalDropped) -> drops.add(new DropEvent(level, handleName, totalDropped)))) {
            fillQueue(delegate, sink);

            sink.log(record(CocoLogLevel.WARN, "warn"));

            assertTrue(delegate.loggedOnThread("warn", callerThreadName));
            assertEquals(0L, sink.droppedRecordCount());
            assertTrue(drops.isEmpty());

            delegate.release();
            assertTrue(delegate.awaitMessages(3));
        }
    }

    @Test
    void writesErrorAndFailureSynchronouslyWithoutNotifyingDrop() {
        CapturingSink delegate = new CapturingSink();
        List<DropEvent> drops = new CopyOnWriteArrayList<>();
        String callerThreadName = Thread.currentThread().getName();
        try (AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, 1,
                (level, handleName, totalDropped) -> drops.add(new DropEvent(level, handleName, totalDropped)))) {
            sink.log(record(CocoLogLevel.ERROR, "error"));
            sink.log(record(CocoLogLevel.INFO, "failure", new IllegalStateException("boom")));

            assertEquals(List.of("error", "failure"), delegate.messages());
            assertTrue(delegate.loggedOnThread("error", callerThreadName));
            assertTrue(delegate.loggedOnThread("failure", callerThreadName));
            assertEquals(0L, sink.droppedRecordCount());
            assertTrue(drops.isEmpty());
        }
    }

    @Test
    void countsConcurrentDropsAccuratelyWithUniqueTotals() throws Exception {
        BlockingSink delegate = new BlockingSink("block");
        List<Long> totals = new CopyOnWriteArrayList<>();
        int droppedRecords = 200;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try (AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, 1,
                (level, handleName, totalDropped) -> totals.add(totalDropped))) {
            fillQueue(delegate, sink);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>(droppedRecords);
            for (int index = 0; index < droppedRecords; index++) {
                int messageIndex = index;
                futures.add(executor.submit(() -> {
                    await(start);
                    sink.log(record(CocoLogLevel.INFO, "dropped-" + messageIndex));
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get(5L, TimeUnit.SECONDS);
            }

            Set<Long> expectedTotals = LongStream.rangeClosed(1L, droppedRecords)
                    .boxed()
                    .collect(Collectors.toSet());
            assertEquals(droppedRecords, sink.droppedRecordCount());
            assertEquals(droppedRecords, totals.size());
            assertEquals(expectedTotals, new HashSet<>(totals));

            delegate.release();
            assertTrue(delegate.awaitMessages(2));
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void isolatesListenerRuntimeExceptionAndContinuesDraining() throws Exception {
        BlockingSink delegate = new BlockingSink("block");
        try (AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, 1,
                (level, handleName, totalDropped) -> {
                    throw new IllegalStateException("listener failed");
                })) {
            fillQueue(delegate, sink);

            assertDoesNotThrow(() -> sink.log(record(CocoLogLevel.INFO, "dropped")));
            assertEquals(1L, sink.droppedRecordCount());

            delegate.release();
            assertTrue(delegate.awaitMessages(2));
            sink.log(record(CocoLogLevel.INFO, "after-listener-failure"));
            assertTrue(delegate.awaitMessages(3));
            assertTrue(delegate.messages().contains("after-listener-failure"));
        }
    }

    @Test
    void suppressesReentrantListenerNotificationButStillCountsNestedDrop() throws Exception {
        BlockingSink delegate = new BlockingSink("block");
        List<DropEvent> drops = new CopyOnWriteArrayList<>();
        AtomicReference<AsyncCocoLogSink> sinkReference = new AtomicReference<>();
        CocoAsyncLogDropListener listener = (level, handleName, totalDropped) -> {
            drops.add(new DropEvent(level, handleName, totalDropped));
            sinkReference.get().log(record("nested-handle", CocoLogLevel.DEBUG, "nested-drop"));
        };
        try (AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, 1, listener)) {
            sinkReference.set(sink);
            fillQueue(delegate, sink);

            assertDoesNotThrow(() -> sink.log(record("outer-handle", CocoLogLevel.INFO, "outer-drop")));

            assertEquals(2L, sink.droppedRecordCount());
            assertEquals(List.of(new DropEvent(CocoLogLevel.INFO, "outer-handle", 1L)), drops);

            delegate.release();
            assertTrue(delegate.awaitMessages(2));
        }
    }

    @Test
    void writesSynchronouslyAfterCloseWithoutNotifyingDrop() {
        CapturingSink delegate = new CapturingSink();
        List<DropEvent> drops = new CopyOnWriteArrayList<>();
        AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, 1,
                (level, handleName, totalDropped) -> drops.add(new DropEvent(level, handleName, totalDropped)));
        sink.close();
        String callerThreadName = Thread.currentThread().getName();

        sink.log(record(CocoLogLevel.INFO, "after-close"));

        assertEquals(List.of("after-close"), delegate.messages());
        assertTrue(delegate.loggedOnThread("after-close", callerThreadName));
        assertEquals(0L, sink.droppedRecordCount());
        assertTrue(drops.isEmpty());
    }

    @Test
    void deliversRecordsSubmittedConcurrentlyWithClose() throws Exception {
        CapturingSink delegate = new CapturingSink();
        int recordCount = 200;
        AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, recordCount,
                (level, handleName, totalDropped) -> {
                });
        ExecutorService executor = Executors.newFixedThreadPool(9);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>(recordCount + 1);
            futures.add(executor.submit(() -> {
                await(start);
                sink.close();
            }));
            for (int index = 0; index < recordCount; index++) {
                int messageIndex = index;
                futures.add(executor.submit(() -> {
                    await(start);
                    sink.log(record(CocoLogLevel.INFO, "record-" + messageIndex));
                }));
            }

            start.countDown();
            for (Future<?> future : futures) {
                future.get(5L, TimeUnit.SECONDS);
            }

            assertTrue(delegate.awaitMessages(recordCount));
            assertEquals(recordCount, new HashSet<>(delegate.messages()).size());
            assertEquals(0L, sink.droppedRecordCount());
        }
        finally {
            sink.close();
            executor.shutdownNow();
        }
    }

    private static void fillQueue(BlockingSink delegate, AsyncCocoLogSink sink) throws InterruptedException {
        sink.log(record(CocoLogLevel.INFO, "block"));
        assertTrue(delegate.awaitBlockingRecord());
        sink.log(record(CocoLogLevel.DEBUG, "queued"));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static CocoLogRecord record(CocoLogLevel level, String message) {
        return record("test", level, message, null);
    }

    private static CocoLogRecord record(CocoLogLevel level, String message, Throwable failure) {
        return record("test", level, message, failure);
    }

    private static CocoLogRecord record(String handleName, CocoLogLevel level, String message) {
        return record(handleName, level, message, null);
    }

    private static CocoLogRecord record(String handleName, CocoLogLevel level, String message, Throwable failure) {
        return new CocoLogRecord(CocoLogHandle.of(handleName, "io.github.coco.test", CocoLogLevel.TRACE),
                level, message, failure);
    }

    private record DropEvent(CocoLogLevel level, String handleName, long totalDropped) {
    }

    private record CapturedLog(String message, String threadName) {
    }

    private static class CapturingSink implements CocoLogSink {

        private final List<CapturedLog> records = new CopyOnWriteArrayList<>();

        @Override
        public void log(CocoLogRecord record) {
            this.records.add(new CapturedLog(record.message(), Thread.currentThread().getName()));
        }

        protected List<String> messages() {
            return this.records.stream().map(CapturedLog::message).toList();
        }

        protected boolean loggedOnThread(String message, String threadName) {
            return this.records.contains(new CapturedLog(message, threadName));
        }

        protected boolean awaitMessages(int count) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < deadline) {
                if (this.records.size() >= count) {
                    return true;
                }
                Thread.sleep(10L);
            }
            return this.records.size() >= count;
        }
    }

    private static final class BlockingSink extends CapturingSink {

        private final String blockingMessage;

        private final CountDownLatch blockingRecord = new CountDownLatch(1);

        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingSink(String blockingMessage) {
            this.blockingMessage = blockingMessage;
        }

        @Override
        public void log(CocoLogRecord record) {
            super.log(record);
            if (this.blockingMessage.equals(record.message())) {
                this.blockingRecord.countDown();
                try {
                    this.release.await(2L, TimeUnit.SECONDS);
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private boolean awaitBlockingRecord() throws InterruptedException {
            return this.blockingRecord.await(2L, TimeUnit.SECONDS);
        }

        private void release() {
            this.release.countDown();
        }
    }
}
