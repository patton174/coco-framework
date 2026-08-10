package io.github.coco.feature.audit;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.github.coco.feature.audit.core.CocoAuditEvent;
import io.github.coco.feature.audit.core.CocoAuditFailurePolicy;
import io.github.coco.feature.audit.core.CocoAuditPublisher;

/**
 * Coco 进程内异步审计发布器。
 * <p>
 * 使用单消费者有界队列保持事件提交顺序，并在关闭时等待已接收事件排空。队列拒绝和后台记录失败统一遵循
 * {@link CocoAuditFailurePolicy}，且失败诊断不会再次进入审计发布链。
 * </p>
 * <p>
 * 关闭等待超时或被中断时始终以异常报告未排空事件数量，不受记录失败策略影响；工作线程为守护线程，停止请求不会无限阻塞 JVM。
 * </p>
 *
 * @author patton174
 * @since 1.0.0
 */
final class AsyncCocoAuditPublisher implements CocoAuditPublisher, AutoCloseable {

    private static final long POLL_TIMEOUT_MILLIS = 100L;

    private static final String WORKER_THREAD_NAME = "coco-audit-writer";

    private static final String CLOSED_MESSAGE = "Coco audit publisher is closed";

    private static final String QUEUE_FULL_MESSAGE = "Coco audit queue is full";

    private static final String INTERRUPTED_DRAIN_MESSAGE = "Interrupted while draining Coco audit queue";

    private static final String TIMED_OUT_DRAIN_MESSAGE = "Timed out while draining Coco audit queue";

    private final CocoAuditPublisher delegate;

    private final ArrayBlockingQueue<CocoAuditEvent> queue;

    private final CocoAuditFailurePolicy failurePolicy;

    private final long shutdownTimeoutMillis;

    private final AtomicReference<RuntimeException> terminalFailure = new AtomicReference<>();

    private final AtomicReference<IllegalStateException> shutdownFailure = new AtomicReference<>();

    private final AtomicLong acceptedEventCount = new AtomicLong();

    private final AtomicLong completedEventCount = new AtomicLong();

    private final Object lifecycleMonitor = new Object();

    private final Thread worker;

    private volatile boolean accepting = true;

    private volatile boolean stopping;

    AsyncCocoAuditPublisher(CocoAuditPublisher delegate, int queueCapacity, Duration shutdownTimeout,
            CocoAuditFailurePolicy failurePolicy) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.queue = new ArrayBlockingQueue<>(normalizeQueueCapacity(queueCapacity));
        this.failurePolicy = failurePolicy == null ? CocoAuditFailurePolicy.IGNORE : failurePolicy;
        this.shutdownTimeoutMillis = Math.max(1L, normalizeShutdownTimeout(shutdownTimeout).toMillis());
        this.worker = new Thread(this::drain, WORKER_THREAD_NAME);
        this.worker.setDaemon(true);
        this.worker.start();
    }

    @Override
    public void publish(CocoAuditEvent event) {
        CocoAuditEvent checkedEvent = Objects.requireNonNull(event, "event must not be null");
        if (Thread.currentThread() == this.worker) {
            this.delegate.publish(checkedEvent);
            return;
        }
        RuntimeException rejection = null;
        synchronized (this.lifecycleMonitor) {
            RuntimeException failure = currentFailure();
            if (failure != null) {
                throw failure;
            }
            else if (!this.accepting) {
                rejection = new IllegalStateException(CLOSED_MESSAGE);
            }
            else if (this.queue.offer(checkedEvent)) {
                this.acceptedEventCount.incrementAndGet();
                return;
            }
            else {
                rejection = new IllegalStateException(QUEUE_FULL_MESSAGE);
            }
        }
        if (this.failurePolicy == CocoAuditFailurePolicy.THROW) {
            throw rejection;
        }
    }

    @Override
    public void close() {
        synchronized (this.lifecycleMonitor) {
            this.accepting = false;
        }
        RuntimeException failure = currentFailure();
        if (failure != null) {
            throw failure;
        }
        if (Thread.currentThread() == this.worker) {
            return;
        }
        try {
            this.worker.join(this.shutdownTimeoutMillis);
        }
        catch (InterruptedException ex) {
            IllegalStateException shutdownFailure = registerShutdownFailure(INTERRUPTED_DRAIN_MESSAGE, ex);
            stopWorker();
            Thread.currentThread().interrupt();
            throw shutdownFailure;
        }
        if (this.worker.isAlive()) {
            IllegalStateException shutdownFailure = registerShutdownFailure(TIMED_OUT_DRAIN_MESSAGE, null);
            stopWorker();
            throw shutdownFailure;
        }
        failure = currentFailure();
        if (failure != null) {
            throw failure;
        }
    }

    private void drain() {
        while (!this.stopping && (this.accepting || !this.queue.isEmpty())) {
            try {
                CocoAuditEvent event = this.queue.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                if (event != null && !record(event)) {
                    return;
                }
            }
            catch (InterruptedException ex) {
                if (this.stopping) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private boolean record(CocoAuditEvent event) {
        try {
            this.delegate.publish(event);
            this.completedEventCount.incrementAndGet();
            return true;
        }
        catch (RuntimeException ex) {
            handleTerminalFailure(ex);
            synchronized (this.lifecycleMonitor) {
                this.accepting = false;
                this.stopping = true;
            }
            return false;
        }
    }

    private void handleTerminalFailure(RuntimeException failure) {
        this.terminalFailure.compareAndSet(null, failure);
    }

    private RuntimeException currentFailure() {
        RuntimeException failure = this.shutdownFailure.get();
        return failure != null ? failure : this.terminalFailure.get();
    }

    private IllegalStateException registerShutdownFailure(String reason, InterruptedException cause) {
        long undrainedEventCount = Math.max(0L,
                this.acceptedEventCount.get() - this.completedEventCount.get());
        IllegalStateException failure = new IllegalStateException(reason + "; " + undrainedEventCount
                + " accepted event(s) remain undrained", cause);
        this.shutdownFailure.compareAndSet(null, failure);
        return this.shutdownFailure.get();
    }

    private void stopWorker() {
        synchronized (this.lifecycleMonitor) {
            this.stopping = true;
        }
        this.worker.interrupt();
    }

    private static int normalizeQueueCapacity(int queueCapacity) {
        return queueCapacity > 0 ? queueCapacity : CocoAuditProperties.AsyncProperties.DEFAULT_QUEUE_CAPACITY;
    }

    private static Duration normalizeShutdownTimeout(Duration shutdownTimeout) {
        return shutdownTimeout == null || shutdownTimeout.isNegative() || shutdownTimeout.isZero()
                ? CocoAuditProperties.AsyncProperties.DEFAULT_SHUTDOWN_TIMEOUT : shutdownTimeout;
    }

}
