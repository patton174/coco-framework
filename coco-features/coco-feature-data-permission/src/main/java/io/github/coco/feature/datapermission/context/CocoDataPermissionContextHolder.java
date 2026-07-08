package io.github.coco.feature.datapermission.context;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.github.coco.feature.datapermission.CocoDataPermissionErrorCode;

/**
 * Coco 数据权限上下文持有器。
 * <p>
 * 使用 {@link ThreadLocal} 保存当前线程数据权限上下文，入口适配器负责设置和清理，SQL 或查询层只读取当前上下文。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoDataPermissionContextHolder {

    private static final ThreadLocal<CocoDataPermissionContext> DATA_PERMISSION_CONTEXT = new ThreadLocal<>();

    private CocoDataPermissionContextHolder() {
    }

    /**
     * <p>
     * 返回当前数据权限上下文。
     * </p>
     * @return 当前数据权限上下文；未设置时为空
     */
    public static Optional<CocoDataPermissionContext> current() {
        return Optional.ofNullable(DATA_PERMISSION_CONTEXT.get());
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
     * 设置当前数据权限上下文。
     * </p>
     * @param dataPermissionContext 数据权限上下文
     * @return 已设置的数据权限上下文
     */
    public static CocoDataPermissionContext set(CocoDataPermissionContext dataPermissionContext) {
        CocoDataPermissionContext checkedContext = Objects.requireNonNull(dataPermissionContext,
                "dataPermissionContext must not be null");
        DATA_PERMISSION_CONTEXT.set(checkedContext);
        return checkedContext;
    }

    /**
     * <p>
     * 清除当前数据权限上下文。
     * </p>
     */
    public static void clear() {
        DATA_PERMISSION_CONTEXT.remove();
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
        callWithContext(dataPermissionContext, () -> {
            runnable.run();
            return null;
        });
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
        Optional<CocoDataPermissionContext> previous = current();
        set(dataPermissionContext);
        try {
            return supplier.get();
        }
        finally {
            restore(previous);
        }
    }

    private static void restore(Optional<CocoDataPermissionContext> previous) {
        if (previous.isPresent()) {
            DATA_PERMISSION_CONTEXT.set(previous.get());
        }
        else {
            clear();
        }
    }
}
