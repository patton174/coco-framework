package io.github.coco.logging.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.MDC;

import io.github.coco.context.CocoContextScope;
import io.github.coco.context.CocoContextSnapshot;
import io.github.coco.context.trace.CocoTraceContext;

/**
 * Coco 异步日志输出器。
 * <p>
 * 框架日志先进入进程内有界队列，再由后台线程写入真实输出器，降低业务请求线程和启动主线程的日志等待时间。
 * 队列满时，低价值日志会被丢弃、计数并通知监听器，高价值日志会同步写入，避免关键错误信息丢失。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class AsyncCocoLogSink implements CocoLogSink, AutoCloseable {

    private static final int DEFAULT_QUEUE_CAPACITY = 1024;

    private static final long POLL_TIMEOUT_MILLIS = 100L;

    private final CocoLogSink delegate;

    private final ArrayBlockingQueue<AsyncLogEnvelope> queue;

    private final CocoAsyncLogDropListener dropListener;

    private final AtomicLong droppedRecordCount = new AtomicLong();

    private final ThreadLocal<Boolean> notifyingDropListener = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final ThreadLocal<Integer> directWriteDepth = new ThreadLocal<>();

    private final Object lifecycleMonitor = new Object();

    private final Thread worker;

    private boolean accepting = true;

    private int inFlightDirectWrites;

    /**
     * <p>
     * 创建异步日志输出器。
     * </p>
     * @param delegate 真实日志输出器
     * @param queueCapacity 队列容量；小于等于零时使用默认容量
     */
    public AsyncCocoLogSink(CocoLogSink delegate, int queueCapacity) {
        this(delegate, queueCapacity, new Slf4jCocoAsyncLogDropListener());
    }

    /**
     * <p>
     * 创建带丢弃监听器的异步日志输出器。
     * </p>
     * @param delegate 真实日志输出器
     * @param queueCapacity 队列容量；小于等于零时使用默认容量
     * @param dropListener 异步日志丢弃监听器
     */
    public AsyncCocoLogSink(CocoLogSink delegate, int queueCapacity, CocoAsyncLogDropListener dropListener) {
        this(delegate, queueCapacity, dropListener, AsyncCocoLogSink::newWorkerThread);
    }

    AsyncCocoLogSink(CocoLogSink delegate, int queueCapacity, CocoAsyncLogDropListener dropListener,
            ThreadFactory workerThreadFactory) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.queue = new ArrayBlockingQueue<>(normalizeCapacity(queueCapacity));
        this.dropListener = Objects.requireNonNull(dropListener, "dropListener must not be null");
        ThreadFactory checkedWorkerThreadFactory = Objects.requireNonNull(workerThreadFactory,
                "workerThreadFactory must not be null");
        this.worker = Objects.requireNonNull(checkedWorkerThreadFactory.newThread(this::drain),
                "workerThreadFactory must create a thread");
        this.worker.start();
    }

    /**
     * <p>
     * 提交日志记录。
     * </p>
     * @param record 日志记录
     */
    @Override
    public void log(CocoLogRecord record) {
        CocoLogRecord checkedRecord = Objects.requireNonNull(record, "record must not be null");
        boolean writeSynchronously;
        synchronized (this.lifecycleMonitor) {
            if (!this.accepting) {
                throw new IllegalStateException("AsyncCocoLogSink is closed");
            }
            writeSynchronously = requiresSynchronousWrite(checkedRecord);
            if (!writeSynchronously && this.queue.offer(capture(checkedRecord))) {
                return;
            }
            if (writeSynchronously || isImportant(checkedRecord.level())) {
                beginDirectWrite();
            }
        }
        if (writeSynchronously || isImportant(checkedRecord.level())) {
            try {
                this.delegate.log(checkedRecord);
            }
            finally {
                completeDirectWrite();
            }
            return;
        }
        if (isDroppable(checkedRecord.level())) {
            notifyDropped(checkedRecord);
        }
    }

    /**
     * <p>
     * 返回当前异步输出器累计实际丢弃的日志记录数。
     * </p>
     * @return 累计丢弃记录数
     */
    public long droppedRecordCount() {
        return this.droppedRecordCount.get();
    }

    /**
     * <p>
     * 停止接收新的日志记录，并刷完关闭前已接受的日志记录。
     * </p>
     * @throws IllegalStateException 当前线程在输出器内部重入关闭，或等待关闭完成时被中断
     */
    @Override
    public void close() {
        synchronized (this.lifecycleMonitor) {
            this.accepting = false;
        }
        this.worker.interrupt();
        if (Thread.currentThread() == this.worker || this.directWriteDepth.get() != null) {
            throw new IllegalStateException("AsyncCocoLogSink cannot complete close from its delegate");
        }
        try {
            this.worker.join();
            awaitDirectWrites();
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while closing AsyncCocoLogSink", ex);
        }
    }

    private void drain() {
        while (isAccepting() || !this.queue.isEmpty()) {
            try {
                AsyncLogEnvelope envelope = this.queue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                if (envelope != null) {
                    writeAsync(envelope);
                }
            }
            catch (InterruptedException ex) {
                // close() wakes the poller so it can observe the closed state and drain promptly.
            }
        }
    }

    private boolean isAccepting() {
        synchronized (this.lifecycleMonitor) {
            return this.accepting;
        }
    }

    private static AsyncLogEnvelope capture(CocoLogRecord record) {
        return new AsyncLogEnvelope(record, captureMdc(), CocoTraceContext.capture());
    }

    private void writeAsync(AsyncLogEnvelope envelope) {
        Map<String, String> previousMdcContext = captureMdc();
        restoreMdc(envelope.mdcContext());
        try (CocoContextScope ignored = CocoTraceContext.restore(envelope.traceContext())) {
            this.delegate.log(envelope.record());
        }
        catch (RuntimeException ignored) {
            // A failing delegate must not strand later accepted records in the worker queue.
        }
        finally {
            restoreMdc(previousMdcContext);
        }
    }

    private void beginDirectWrite() {
        this.inFlightDirectWrites++;
        Integer currentDepth = this.directWriteDepth.get();
        this.directWriteDepth.set(currentDepth == null ? 1 : currentDepth + 1);
    }

    private void completeDirectWrite() {
        int remainingDepth = this.directWriteDepth.get() - 1;
        if (remainingDepth == 0) {
            this.directWriteDepth.remove();
        }
        else {
            this.directWriteDepth.set(remainingDepth);
        }
        synchronized (this.lifecycleMonitor) {
            this.inFlightDirectWrites--;
            this.lifecycleMonitor.notifyAll();
        }
    }

    private void awaitDirectWrites() throws InterruptedException {
        synchronized (this.lifecycleMonitor) {
            while (this.inFlightDirectWrites > 0) {
                this.lifecycleMonitor.wait();
            }
        }
    }

    private static Map<String, String> captureMdc() {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();
        return mdcContext == null || mdcContext.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(mdcContext));
    }

    private static void restoreMdc(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        }
        else {
            MDC.setContextMap(context);
        }
    }

    private static int normalizeCapacity(int queueCapacity) {
        return queueCapacity <= 0 ? DEFAULT_QUEUE_CAPACITY : queueCapacity;
    }

    private static Thread newWorkerThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "coco-log-writer");
        thread.setDaemon(true);
        return thread;
    }

    private static boolean isImportant(CocoLogLevel level) {
        return level == CocoLogLevel.WARN || level == CocoLogLevel.ERROR;
    }

    private static boolean isDroppable(CocoLogLevel level) {
        return level == CocoLogLevel.TRACE || level == CocoLogLevel.DEBUG || level == CocoLogLevel.INFO;
    }

    private static boolean requiresSynchronousWrite(CocoLogRecord record) {
        return record.level() == CocoLogLevel.ERROR || record.failure().isPresent();
    }

    private void notifyDropped(CocoLogRecord record) {
        long totalDropped = this.droppedRecordCount.incrementAndGet();
        if (this.notifyingDropListener.get()) {
            return;
        }
        this.notifyingDropListener.set(Boolean.TRUE);
        try {
            this.dropListener.onDropped(record.level(), record.handle().name(), totalDropped);
        }
        catch (RuntimeException ignored) {
            // Overflow diagnostics must not fail the logging caller.
        }
        finally {
            this.notifyingDropListener.remove();
        }
    }

    private record AsyncLogEnvelope(CocoLogRecord record, Map<String, String> mdcContext,
            CocoContextSnapshot traceContext) {
    }
}
