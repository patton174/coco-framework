package io.github.coco.feature.tenant.context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Coco 租户上下文执行器服务适配器。
 * <p>
 * 在每次任务提交时捕获当前线程的租户上下文，并将执行与生命周期操作委托给目标
 * {@link ExecutorService}。适配器不创建线程，也不维护独立的生命周期状态。
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
public final class CocoTenantContextExecutorService implements ExecutorService {

    private final ExecutorService delegate;

    /**
     * <p>
     * 创建租户上下文执行器服务适配器。
     * </p>
     * @param delegate 目标执行器服务
     */
    public CocoTenantContextExecutorService(ExecutorService delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        this.delegate.shutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Runnable> shutdownNow() {
        return this.delegate.shutdownNow();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isShutdown() {
        return this.delegate.isShutdown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTerminated() {
        return this.delegate.isTerminated();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return this.delegate.awaitTermination(timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Runnable command) {
        this.delegate.execute(capture(command));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return this.delegate.submit(capture(task));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return this.delegate.submit(capture(task), result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<?> submit(Runnable task) {
        return this.delegate.submit(capture(task));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return this.delegate.invokeAll(capture(tasks));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException {
        return this.delegate.invokeAll(capture(tasks), timeout, unit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
            throws InterruptedException, ExecutionException {
        return this.delegate.invokeAny(capture(tasks));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return this.delegate.invokeAny(capture(tasks), timeout, unit);
    }

    private static Runnable capture(Runnable task) {
        Runnable checkedTask = Objects.requireNonNull(task, "task must not be null");
        return CocoTenantContextHolder.capture().wrap(checkedTask);
    }

    private static <T> Callable<T> capture(Callable<T> task) {
        Callable<T> checkedTask = Objects.requireNonNull(task, "task must not be null");
        return CocoTenantContextHolder.capture().wrap(checkedTask);
    }

    private static <T> List<Callable<T>> capture(Collection<? extends Callable<T>> tasks) {
        Collection<? extends Callable<T>> checkedTasks = Objects.requireNonNull(tasks, "tasks must not be null");
        List<Callable<T>> capturedTasks = new ArrayList<>(checkedTasks.size());
        for (Callable<T> task : checkedTasks) {
            capturedTasks.add(capture(task));
        }
        return capturedTasks;
    }
}
