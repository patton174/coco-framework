package io.github.coco.context;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Coco 上下文执行器。
 * <p>
 * 通过在任务提交时捕获 {@link CocoContextSnapshot}，将提交线程中的上下文传播到异步执行线程，
 * 并在任务结束后恢复 worker 线程原有上下文。
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
public final class CocoContextExecutor {

    private CocoContextExecutor() {
    }

    /**
     * <p>
     * 包装目标执行器，使其在任务提交时捕获上下文快照，并在任务执行前恢复该快照。
     * </p>
     * @param delegate 目标执行器
     * @param snapshotSupplier 快照提供器
     * @return 包装后的执行器
     */
    public static Executor wrap(Executor delegate, Supplier<? extends CocoContextSnapshot> snapshotSupplier) {
        Executor checkedDelegate = Objects.requireNonNull(delegate, "delegate must not be null");
        Supplier<? extends CocoContextSnapshot> checkedSnapshotSupplier = Objects.requireNonNull(snapshotSupplier,
                "snapshotSupplier must not be null");
        return command -> {
            Runnable checkedCommand = Objects.requireNonNull(command, "command must not be null");
            CocoContextSnapshot snapshot = Objects.requireNonNull(checkedSnapshotSupplier.get(),
                    "snapshotSupplier.get() must not return null");
            checkedDelegate.execute(snapshot.wrap(checkedCommand));
        };
    }
}
