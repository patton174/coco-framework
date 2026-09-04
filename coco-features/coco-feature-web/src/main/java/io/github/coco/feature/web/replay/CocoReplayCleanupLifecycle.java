package io.github.coco.feature.web.replay;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

/**
 * Coco Web 防重放存储共享的后台清理生命周期。
 * <p>
 * 封装定时清理调度器的创建、启动、安全执行和关闭逻辑，由具体存储实现组合使用。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
final class CocoReplayCleanupLifecycle implements AutoCloseable {

    private final Logger logger;

    private final Runnable cleanupTask;

    private final long cleanupIntervalSeconds;

    private final ScheduledExecutorService cleanupExecutor;

    private final AtomicBoolean cleanupStarted = new AtomicBoolean();

    private final AtomicBoolean closed = new AtomicBoolean();

    CocoReplayCleanupLifecycle(Runnable cleanupTask, long cleanupIntervalSeconds,
            String threadName, Logger logger, boolean backgroundCleanupEnabled) {
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.cleanupTask = Objects.requireNonNull(cleanupTask, "cleanupTask must not be null");
        this.cleanupIntervalSeconds = cleanupIntervalSeconds;
        this.cleanupExecutor = backgroundCleanupEnabled
                ? Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, threadName);
                    thread.setDaemon(true);
                    return thread;
                })
                : null;
    }

    void startCleanupTaskIfNecessary() {
        if (this.cleanupExecutor == null || this.closed.get()
                || !this.cleanupStarted.compareAndSet(false, true)) {
            return;
        }
        this.cleanupExecutor.scheduleWithFixedDelay(this::cleanupSafely,
                this.cleanupIntervalSeconds, this.cleanupIntervalSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        if (this.cleanupExecutor != null && this.closed.compareAndSet(false, true)) {
            this.cleanupExecutor.shutdownNow();
        }
    }

    boolean cleanupStarted() {
        return this.cleanupStarted.get();
    }

    boolean closed() {
        return this.closed.get();
    }

    private void cleanupSafely() {
        try {
            this.cleanupTask.run();
        }
        catch (RuntimeException ex) {
            this.logger.warn("Coco replay cleanup failed; expired replay keys will be retried later.", ex);
        }
    }
}
