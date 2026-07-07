package io.github.coco.common.logging.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    void dispatchesRecordsOnBackgroundThread() throws Exception {
        CapturingSink delegate = new CapturingSink();
        try (AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, 8)) {
            sink.log(record(CocoLogLevel.INFO, "hello"));

            assertTrue(delegate.awaitMessages(1));
            assertEquals(List.of("hello"), delegate.messages());
        }
    }

    @Test
    void dropsLowValueRecordWhenQueueIsFull() throws Exception {
        BlockingSink delegate = new BlockingSink("block");
        try (AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, 1)) {
            sink.log(record(CocoLogLevel.INFO, "block"));
            assertTrue(delegate.awaitBlockingRecord());

            sink.log(record(CocoLogLevel.DEBUG, "queued"));
            sink.log(record(CocoLogLevel.DEBUG, "dropped"));

            delegate.release();
            assertTrue(delegate.awaitMessages(2));
            assertEquals(List.of("block", "queued"), delegate.messages());
        }
    }

    @Test
    void writesImportantRecordSynchronouslyWhenQueueIsFull() throws Exception {
        BlockingSink delegate = new BlockingSink("block");
        try (AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, 1)) {
            sink.log(record(CocoLogLevel.INFO, "block"));
            assertTrue(delegate.awaitBlockingRecord());
            sink.log(record(CocoLogLevel.DEBUG, "queued"));

            sink.log(record(CocoLogLevel.ERROR, "error"));

            assertTrue(delegate.messages().contains("error"));
            delegate.release();
            assertTrue(delegate.awaitMessages(3));
        }
    }

    @Test
    void writesErrorWithFailureSynchronouslyEvenWhenQueueHasCapacity() {
        CapturingSink delegate = new CapturingSink();
        String callerThreadName = Thread.currentThread().getName();

        try (AsyncCocoLogSink sink = new AsyncCocoLogSink(delegate, 8)) {
            sink.log(record(CocoLogLevel.ERROR, "boom", new IllegalStateException("boom")));

            assertEquals(List.of("boom"), delegate.messages());
            assertEquals(List.of(callerThreadName), delegate.threadNames());
        }
    }

    private static CocoLogRecord record(CocoLogLevel level, String message) {
        return record(level, message, null);
    }

    private static CocoLogRecord record(CocoLogLevel level, String message, Throwable failure) {
        return new CocoLogRecord(CocoLogHandle.of("test", "io.github.coco.test", CocoLogLevel.TRACE),
                level, message, failure);
    }

    private static class CapturingSink implements CocoLogSink {

        private final List<String> messages = new CopyOnWriteArrayList<>();

        private final List<String> threadNames = new CopyOnWriteArrayList<>();

        @Override
        public void log(CocoLogRecord record) {
            this.messages.add(record.message());
            this.threadNames.add(Thread.currentThread().getName());
        }

        protected List<String> messages() {
            return List.copyOf(this.messages);
        }

        protected List<String> threadNames() {
            return List.copyOf(this.threadNames);
        }

        protected boolean awaitMessages(int count) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < deadline) {
                if (this.messages.size() >= count) {
                    return true;
                }
                Thread.sleep(10L);
            }
            return this.messages.size() >= count;
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
