package io.github.coco.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Coco 上下文快照。
 * <p>
 * 快照捕获当前线程中的上下文状态，并可以在后续线程或回调中恢复该状态。恢复后返回的
 * {@link CocoContextScope} 负责在关闭时恢复进入作用域前的上下文。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-context}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@FunctionalInterface
public interface CocoContextSnapshot {

    /**
     * <p>
     * 恢复快照中的上下文，并返回用于恢复之前上下文的作用域。
     * </p>
     * @return 上下文作用域
     */
    CocoContextScope restore();

    /**
     * <p>
     * 捕获当前快照并包装 {@link Runnable}，执行结束后自动恢复工作线程原有上下文。
     * </p>
     * @param runnable 待执行逻辑
     * @return 包装后的逻辑
     */
    default Runnable wrap(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        return () -> {
            try (CocoContextScope ignored = restore()) {
                runnable.run();
            }
        };
    }

    /**
     * <p>
     * 捕获当前快照并包装 {@link Callable}，执行结束后自动恢复工作线程原有上下文。
     * </p>
     * @param callable 待执行逻辑
     * @param <T> 返回值类型
     * @return 包装后的逻辑
     */
    default <T> Callable<T> wrap(Callable<T> callable) {
        Objects.requireNonNull(callable, "callable must not be null");
        return () -> {
            try (CocoContextScope ignored = restore()) {
                return callable.call();
            }
        };
    }

    /**
     * <p>
     * 捕获当前快照并包装 {@link Supplier}，执行结束后自动恢复工作线程原有上下文。
     * </p>
     * @param supplier 待执行逻辑
     * @param <T> 返回值类型
     * @return 包装后的逻辑
     */
    default <T> Supplier<T> wrapSupplier(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        return () -> {
            try (CocoContextScope ignored = restore()) {
                return supplier.get();
            }
        };
    }

    /**
     * <p>
     * 返回空快照。
     * </p>
     * @return 空快照
     */
    static CocoContextSnapshot noop() {
        return CocoContextScope::noop;
    }

    /**
     * <p>
     * 合并多个上下文快照。
     * </p>
     * @param snapshots 上下文快照
     * @return 合并后的上下文快照
     */
    static CocoContextSnapshot compose(CocoContextSnapshot... snapshots) {
        Objects.requireNonNull(snapshots, "snapshots must not be null");
        return compose(Arrays.asList(snapshots));
    }

    /**
     * <p>
     * 合并多个上下文快照。
     * </p>
     * @param snapshots 上下文快照
     * @return 合并后的上下文快照
     */
    static CocoContextSnapshot compose(Collection<? extends CocoContextSnapshot> snapshots) {
        Objects.requireNonNull(snapshots, "snapshots must not be null");
        List<CocoContextSnapshot> snapshotList = List.copyOf(snapshots);
        if (snapshotList.isEmpty()) {
            return noop();
        }
        return () -> {
            List<CocoContextScope> scopes = new ArrayList<>(snapshotList.size());
            try {
                for (CocoContextSnapshot snapshot : snapshotList) {
                    scopes.add(snapshot.restore());
                }
            }
            catch (RuntimeException | Error ex) {
                closeReverse(scopes);
                throw ex;
            }
            return () -> closeReverse(scopes);
        };
    }

    private static void closeReverse(List<CocoContextScope> scopes) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            scopes.get(i).close();
        }
    }
}
