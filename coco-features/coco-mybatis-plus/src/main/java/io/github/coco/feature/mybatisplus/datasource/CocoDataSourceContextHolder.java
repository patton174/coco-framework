package io.github.coco.feature.mybatisplus.datasource;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import io.github.coco.feature.mybatisplus.CocoMybatisPlusErrorCode;

/**
 * Coco 动态数据源线程上下文持有器。
 * <p>
 * 使用栈式 {@link ThreadLocal} 管理当前线程的数据源路由键，支持多层嵌套 {@link CocoDataSource} 调用时的压栈与逐层恢复。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoDataSourceContextHolder {

    private static final ThreadLocal<Deque<String>> CONTEXT_STACK = new ThreadLocal<>();

    private CocoDataSourceContextHolder() {
    }

    /**
     * <p>
     * 返回当前线程栈顶的数据源路由键。
     * </p>
     * @return 当前数据源路由键；未设置时返回空
     */
    public static Optional<String> current() {
        Deque<String> stack = CONTEXT_STACK.get();
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.peekFirst());
    }

    /**
     * <p>
     * 将目标数据源路由键压入当前线程上下文栈，并返回可自动恢复的作用域。
     * </p>
     * @param dataSourceKey 目标数据源路由键
     * @return 数据源上下文作用域
     */
    public static CocoDataSourceScope push(String dataSourceKey) {
        String normalizedKey = requireValidKey(dataSourceKey);
        Deque<String> stack = CONTEXT_STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            CONTEXT_STACK.set(stack);
        }
        stack.addFirst(normalizedKey);
        AtomicBoolean closed = new AtomicBoolean(false);
        return () -> {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            Deque<String> currentStack = CONTEXT_STACK.get();
            if (currentStack == null || currentStack.isEmpty()) {
                CONTEXT_STACK.remove();
                return;
            }
            currentStack.pollFirst();
            if (currentStack.isEmpty()) {
                CONTEXT_STACK.remove();
            }
        };
    }

    /**
     * <p>
     * 返回当前线程完整的数据源上下文栈快照（从栈顶到栈底）。
     * </p>
     * @return 不可变的数据源键列表
     */
    public static List<String> snapshotStack() {
        Deque<String> stack = CONTEXT_STACK.get();
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        return List.copyOf(stack);
    }

    /**
     * <p>
     * 清空当前线程的数据源路由上下文。
     * </p>
     */
    public static void clear() {
        CONTEXT_STACK.remove();
    }

    /**
     * <p>
     * 在指定数据源上下文中执行无返回值逻辑，并在结束后恢复之前的数据源栈状态。
     * </p>
     * @param dataSourceKey 目标数据源路由键
     * @param runnable 待执行逻辑
     */
    public static void runWith(String dataSourceKey, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        try (CocoDataSourceScope ignored = push(dataSourceKey)) {
            runnable.run();
        }
    }

    /**
     * <p>
     * 在指定数据源上下文中执行有返回值逻辑，并在结束后恢复之前的数据源栈状态。
     * </p>
     * @param dataSourceKey 目标数据源路由键
     * @param supplier 待执行逻辑
     * @param <T> 返回值类型
     * @return 执行结果
     */
    public static <T> T callWith(String dataSourceKey, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        try (CocoDataSourceScope ignored = push(dataSourceKey)) {
            return supplier.get();
        }
    }

    /**
     * <p>
     * 捕获当前线程数据源栈并包装 {@link Runnable}，用于跨线程传递数据源路由上下文。
     * </p>
     * @param runnable 待执行逻辑
     * @return 包装后的逻辑
     */
    public static Runnable wrap(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        List<String> captured = snapshotStack();
        return () -> {
            List<String> previous = snapshotStack();
            restoreStack(captured);
            try {
                runnable.run();
            }
            finally {
                restoreStack(previous);
            }
        };
    }

    /**
     * <p>
     * 捕获当前线程数据源栈并包装 {@link Callable}，用于跨线程传递数据源路由上下文。
     * </p>
     * @param callable 待执行逻辑
     * @param <T> 返回值类型
     * @return 包装后的逻辑
     */
    public static <T> Callable<T> wrap(Callable<T> callable) {
        Objects.requireNonNull(callable, "callable must not be null");
        List<String> captured = snapshotStack();
        return () -> {
            List<String> previous = snapshotStack();
            restoreStack(captured);
            try {
                return callable.call();
            }
            finally {
                restoreStack(previous);
            }
        };
    }

    /**
     * <p>
     * 捕获当前线程数据源栈并包装 {@link Supplier}，用于跨线程传递数据源路由上下文。
     * </p>
     * @param supplier 待执行逻辑
     * @param <T> 返回值类型
     * @return 包装后的逻辑
     */
    public static <T> Supplier<T> wrapSupplier(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        List<String> captured = snapshotStack();
        return () -> {
            List<String> previous = snapshotStack();
            restoreStack(captured);
            try {
                return supplier.get();
            }
            finally {
                restoreStack(previous);
            }
        };
    }

    private static String requireValidKey(String dataSourceKey) {
        if (dataSourceKey == null || dataSourceKey.isBlank()) {
            throw CocoMybatisPlusErrorCode.INVALID_DATASOURCE_KEY.request();
        }
        return dataSourceKey.trim();
    }

    private static void restoreStack(List<String> stackSnapshot) {
        if (stackSnapshot == null || stackSnapshot.isEmpty()) {
            CONTEXT_STACK.remove();
            return;
        }
        CONTEXT_STACK.set(new ArrayDeque<>(stackSnapshot));
    }
}
