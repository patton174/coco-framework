package io.github.coco.logging;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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

    private static final long CLOSE_TIMEOUT_MILLIS = 1000L;

    private final CocoLogSink delegate;

    private final ArrayBlockingQueue<CocoLogRecord> queue;

    private final CocoAsyncLogDropListener dropListener;

    private final AtomicLong droppedRecordCount = new AtomicLong();

    private final ThreadLocal<Boolean> notifyingDropListener = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final Object lifecycleMonitor = new Object();

    private final Thread worker;

    private volatile boolean running = true;

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
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.queue = new ArrayBlockingQueue<>(normalizeCapacity(queueCapacity));
        this.dropListener = Objects.requireNonNull(dropListener, "dropListener must not be null");
        this.worker = new Thread(this::drain, "coco-log-writer");
        this.worker.setDaemon(true);
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
        if (requiresSynchronousWrite(checkedRecord)) {
            this.delegate.log(checkedRecord);
            return;
        }
        boolean writeSynchronously;
        synchronized (this.lifecycleMonitor) {
            writeSynchronously = !this.running;
            if (!writeSynchronously && this.queue.offer(checkedRecord)) {
                return;
            }
        }
        if (writeSynchronously) {
            this.delegate.log(checkedRecord);
            return;
        }
        if (isImportant(checkedRecord.level())) {
            this.delegate.log(checkedRecord);
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
     * 关闭异步输出器，并尽量刷完队列中的日志记录。
     * </p>
     */
    @Override
    public void close() {
        synchronized (this.lifecycleMonitor) {
            this.running = false;
        }
        this.worker.interrupt();
        try {
            this.worker.join(CLOSE_TIMEOUT_MILLIS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void drain() {
        while (this.running || !this.queue.isEmpty()) {
            try {
                CocoLogRecord record = this.queue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                if (record != null) {
                    this.delegate.log(record);
                }
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        CocoLogRecord record;
        while ((record = this.queue.poll()) != null) {
            this.delegate.log(record);
        }
    }

    private static int normalizeCapacity(int queueCapacity) {
        return queueCapacity <= 0 ? DEFAULT_QUEUE_CAPACITY : queueCapacity;
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
}
