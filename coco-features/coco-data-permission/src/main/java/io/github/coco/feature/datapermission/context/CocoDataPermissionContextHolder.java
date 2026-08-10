package io.github.coco.feature.datapermission.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import io.github.coco.context.CocoContextExecutor;
import io.github.coco.context.CocoContextScope;
import io.github.coco.context.CocoContextSnapshot;
import io.github.coco.feature.datapermission.CocoDataPermissionErrorCode;

/**
 * Coco 数据权限上下文持有器。
 * <p>
 * 使用线程私有上下文栈保存当前数据权限上下文，入口适配器负责设置和清理，SQL 或查询层只读取当前上下文。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoDataPermissionContextHolder {

    private static final ThreadLocal<ContextStack> DATA_PERMISSION_CONTEXT = new ThreadLocal<>();

    private CocoDataPermissionContextHolder() {
    }

    /**
     * <p>
     * 返回当前数据权限上下文。
     * </p>
     * @return 当前数据权限上下文；未设置时为空
     */
    public static Optional<CocoDataPermissionContext> current() {
        ContextStack stack = DATA_PERMISSION_CONTEXT.get();
        return stack == null ? Optional.empty() : Optional.ofNullable(stack.current());
    }

    /**
     * <p>
     * 返回当前数据权限上下文，不存在时抛出无权限异常。
     * </p>
     * @return 当前数据权限上下文
     */
    public static CocoDataPermissionContext requireCurrent() {
        return current().orElseThrow(() -> CocoDataPermissionErrorCode.CONTEXT_MISSING.forbidden());
    }

    /**
     * <p>
     * 设置当前数据权限上下文。作用域存在时，只修改当前作用域的值，作用域关闭后仍会恢复进入作用域前的值。
     * </p>
     * @param dataPermissionContext 数据权限上下文
     * @return 已设置的数据权限上下文
     */
    public static CocoDataPermissionContext set(CocoDataPermissionContext dataPermissionContext) {
        CocoDataPermissionContext checkedContext = Objects.requireNonNull(dataPermissionContext,
                "dataPermissionContext must not be null");
        state().set(checkedContext);
        return checkedContext;
    }

    /**
     * <p>
     * 清除当前数据权限上下文。作用域存在时，只清除当前作用域的值，作用域关闭后仍会恢复进入作用域前的值。
     * </p>
     */
    public static void clear() {
        ContextStack stack = DATA_PERMISSION_CONTEXT.get();
        if (stack == null) {
            return;
        }
        stack.clear();
        if (stack.isEmpty()) {
            DATA_PERMISSION_CONTEXT.remove();
        }
    }

    /**
     * <p>
     * 捕获当前线程数据权限上下文。
     * </p>
     * @return 数据权限上下文快照
     */
    public static CocoContextSnapshot capture() {
        Optional<CocoDataPermissionContext> captured = current();
        return () -> pushCaptured(captured);
    }

    /**
     * <p>
     * 恢复指定上下文快照。
     * </p>
     * @param snapshot 上下文快照
     * @return 上下文作用域
     */
    public static CocoContextScope restore(CocoContextSnapshot snapshot) {
        return Objects.requireNonNull(snapshot, "snapshot must not be null").restore();
    }

    /**
     * <p>
     * 在当前线程建立一个可关闭的数据权限作用域。
     * </p>
     * @param dataPermissionContext 临时数据权限上下文
     * @return 数据权限作用域
     */
    public static CocoContextScope push(CocoDataPermissionContext dataPermissionContext) {
        CocoDataPermissionContext checkedContext = Objects.requireNonNull(dataPermissionContext,
                "dataPermissionContext must not be null");
        return state().push(checkedContext);
    }

    /**
     * <p>
     * 捕获当前数据权限上下文并包装 {@link Runnable}。
     * </p>
     * @param runnable 待执行逻辑
     * @return 包装后的逻辑
     */
    public static Runnable wrap(Runnable runnable) {
        return capture().wrap(runnable);
    }

    /**
     * <p>
     * 捕获当前数据权限上下文并包装 {@link Callable}。
     * </p>
     * @param callable 待执行逻辑
     * @param <T> 返回值类型
     * @return 包装后的逻辑
     */
    public static <T> Callable<T> wrap(Callable<T> callable) {
        return capture().wrap(callable);
    }

    /**
     * <p>
     * 捕获当前数据权限上下文并包装 {@link Supplier}。
     * </p>
     * @param supplier 待执行逻辑
     * @param <T> 返回值类型
     * @return 包装后的逻辑
     */
    public static <T> Supplier<T> wrapSupplier(Supplier<T> supplier) {
        return capture().wrapSupplier(supplier);
    }

    /**
     * <p>
     * 创建传播数据权限上下文的执行器。上下文在提交时捕获，在任务结束时恢复 worker 原上下文。
     * </p>
     * @param delegate 目标执行器
     * @return 上下文传播执行器
     */
    public static Executor executor(Executor delegate) {
        return CocoContextExecutor.wrap(delegate, CocoDataPermissionContextHolder::capture);
    }

    /**
     * <p>
     * 在指定数据权限上下文中执行逻辑，并在结束后恢复之前的上下文。
     * </p>
     * @param dataPermissionContext 临时数据权限上下文
     * @param runnable 待执行逻辑
     */
    public static void runWithContext(CocoDataPermissionContext dataPermissionContext, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        try (CocoContextScope ignored = push(dataPermissionContext)) {
            runnable.run();
        }
    }

    /**
     * <p>
     * 在指定数据权限上下文中执行逻辑，返回执行结果，并在结束后恢复之前的上下文。
     * </p>
     * @param dataPermissionContext 临时数据权限上下文
     * @param supplier 待执行逻辑
     * @param <T> 返回值类型
     * @return 逻辑执行结果
     */
    public static <T> T callWithContext(CocoDataPermissionContext dataPermissionContext, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        try (CocoContextScope ignored = push(dataPermissionContext)) {
            return supplier.get();
        }
    }

    private static ContextStack state() {
        ContextStack stack = DATA_PERMISSION_CONTEXT.get();
        if (stack == null) {
            stack = new ContextStack();
            DATA_PERMISSION_CONTEXT.set(stack);
        }
        return stack;
    }

    private static CocoContextScope pushCaptured(Optional<CocoDataPermissionContext> captured) {
        return state().push(captured.orElse(null));
    }

    private static final class ContextStack {

        private CocoDataPermissionContext root;

        private final List<ScopeFrame> frames = new ArrayList<>();

        private CocoDataPermissionContext current() {
            if (frames.isEmpty()) {
                return root;
            }
            return frames.get(frames.size() - 1).context;
        }

        private void set(CocoDataPermissionContext context) {
            if (frames.isEmpty()) {
                root = context;
            }
            else {
                frames.get(frames.size() - 1).context = context;
            }
        }

        private void clear() {
            if (frames.isEmpty()) {
                root = null;
            }
            else {
                frames.get(frames.size() - 1).context = null;
            }
        }

        private CocoContextScope push(CocoDataPermissionContext context) {
            ScopeFrame frame = new ScopeFrame(context);
            frames.add(frame);
            frame.scope = new OwnedScope(this, frame, Thread.currentThread());
            return frame.scope;
        }

        private boolean close(OwnedScope scope) {
            if (scope.owner != Thread.currentThread()) {
                return false;
            }
            int index = frames.indexOf(scope.frame);
            if (index < 0) {
                return false;
            }
            for (int i = frames.size() - 1; i >= index; i--) {
                frames.remove(i).scope.markClosed();
            }
            return true;
        }

        private boolean isEmpty() {
            return root == null && frames.isEmpty();
        }
    }

    private static final class ScopeFrame {

        private CocoDataPermissionContext context;

        private OwnedScope scope;

        private ScopeFrame(CocoDataPermissionContext context) {
            this.context = context;
        }
    }

    private static final class OwnedScope implements CocoContextScope {

        private final ContextStack stack;

        private final ScopeFrame frame;

        private final Thread owner;

        private boolean closed;

        private OwnedScope(ContextStack stack, ScopeFrame frame, Thread owner) {
            this.stack = stack;
            this.frame = frame;
            this.owner = owner;
        }

        @Override
        public void close() {
            if (Thread.currentThread() != owner || closed) {
                return;
            }
            if (stack.close(this)) {
                closed = true;
                if (stack.isEmpty()) {
                    DATA_PERMISSION_CONTEXT.remove();
                }
            }
        }

        private void markClosed() {
            this.closed = true;
        }
    }
}
