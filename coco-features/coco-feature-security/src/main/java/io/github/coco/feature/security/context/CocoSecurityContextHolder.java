package io.github.coco.feature.security.context;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.github.coco.feature.security.CocoSecurityErrorCode;

/**
 * Coco 安全上下文持有器。
 * <p>
 * 使用 {@link ThreadLocal} 保存当前线程安全上下文，入口适配器负责设置和清理，业务逻辑只读取当前上下文。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-security}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoSecurityContextHolder {

    private static final ThreadLocal<CocoSecurityContext> SECURITY_CONTEXT = new ThreadLocal<>();

    private CocoSecurityContextHolder() {
    }

    /**
     * <p>
     * 返回当前安全上下文。
     * </p>
     * @return 当前安全上下文；未设置时为空
     */
    public static Optional<CocoSecurityContext> current() {
        return Optional.ofNullable(SECURITY_CONTEXT.get());
    }

    /**
     * <p>
     * 返回当前安全上下文，不存在时抛出未认证异常。
     * </p>
     * @return 当前安全上下文
     */
    public static CocoSecurityContext requireCurrent() {
        return current().orElseThrow(() -> CocoSecurityErrorCode.CONTEXT_MISSING.unauthorized());
    }

    /**
     * <p>
     * 设置当前安全上下文。
     * </p>
     * @param securityContext 安全上下文
     * @return 已设置的安全上下文
     */
    public static CocoSecurityContext set(CocoSecurityContext securityContext) {
        CocoSecurityContext checkedContext = Objects.requireNonNull(securityContext,
                "securityContext must not be null");
        SECURITY_CONTEXT.set(checkedContext);
        return checkedContext;
    }

    /**
     * <p>
     * 清除当前安全上下文。
     * </p>
     */
    public static void clear() {
        SECURITY_CONTEXT.remove();
    }

    /**
     * <p>
     * 在指定安全上下文中执行逻辑，并在结束后恢复之前的上下文。
     * </p>
     * @param securityContext 临时安全上下文
     * @param runnable 待执行逻辑
     */
    public static void runWithContext(CocoSecurityContext securityContext, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        callWithContext(securityContext, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * <p>
     * 在指定安全上下文中执行逻辑，返回执行结果，并在结束后恢复之前的上下文。
     * </p>
     * @param securityContext 临时安全上下文
     * @param supplier 待执行逻辑
     * @param <T> 返回值类型
     * @return 逻辑执行结果
     */
    public static <T> T callWithContext(CocoSecurityContext securityContext, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        Optional<CocoSecurityContext> previous = current();
        set(securityContext);
        try {
            return supplier.get();
        }
        finally {
            restore(previous);
        }
    }

    private static void restore(Optional<CocoSecurityContext> previous) {
        if (previous.isPresent()) {
            SECURITY_CONTEXT.set(previous.get());
        }
        else {
            clear();
        }
    }
}
