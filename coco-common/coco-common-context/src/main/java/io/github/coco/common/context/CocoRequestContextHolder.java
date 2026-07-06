package io.github.coco.common.context;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import io.github.coco.common.trace.CocoTraceContext;

/**
 * Coco 请求上下文持有器。
 * <p>
 * 基于 {@link ThreadLocal} 保存当前线程的请求上下文，并在设置和恢复请求上下文时同步 {@link CocoTraceContext}。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-context}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoRequestContextHolder {

    private static final ThreadLocal<CocoRequestContext> REQUEST_CONTEXT = new ThreadLocal<>();

    private CocoRequestContextHolder() {
    }

    /**
     * <p>
     * 返回当前线程中的请求上下文。
     * </p>
     * @return 当前请求上下文；未设置时为空
     */
    public static Optional<CocoRequestContext> current() {
        return Optional.ofNullable(REQUEST_CONTEXT.get());
    }

    /**
     * <p>
     * 设置当前线程请求上下文，并同步 Trace 上下文。
     * </p>
     * @param requestContext 请求上下文
     * @return 已设置的请求上下文
     */
    public static CocoRequestContext set(CocoRequestContext requestContext) {
        CocoRequestContext checkedContext = Objects.requireNonNull(requestContext,
                "requestContext must not be null");
        REQUEST_CONTEXT.set(checkedContext);
        CocoTraceContext.setTraceId(checkedContext.traceId());
        return checkedContext;
    }

    /**
     * <p>
     * 使用 TraceId、HTTP 方法和请求路径设置当前线程请求上下文。
     * </p>
     * @param traceId TraceId
     * @param method HTTP 方法
     * @param path 请求路径
     * @return 已设置的请求上下文
     */
    public static CocoRequestContext set(String traceId, String method, String path) {
        return set(CocoRequestContext.of(traceId, method, path));
    }

    /**
     * <p>
     * 清除当前线程请求上下文和 Trace 上下文。
     * </p>
     */
    public static void clear() {
        REQUEST_CONTEXT.remove();
        CocoTraceContext.clear();
    }

    /**
     * <p>
     * 在指定请求上下文中执行逻辑，并在结束后恢复之前的上下文。
     * </p>
     * @param requestContext 临时请求上下文
     * @param runnable 待执行逻辑
     */
    public static void runWithContext(CocoRequestContext requestContext, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        runWithContext(requestContext, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * <p>
     * 在指定请求上下文中执行逻辑，返回执行结果，并在结束后恢复之前的上下文。
     * </p>
     * @param requestContext 临时请求上下文
     * @param supplier 待执行逻辑
     * @param <T> 返回值类型
     * @return 逻辑执行结果
     */
    public static <T> T runWithContext(CocoRequestContext requestContext, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        Optional<CocoRequestContext> previousContext = current();
        Optional<String> previousTraceId = CocoTraceContext.currentTraceId();
        set(requestContext);
        try {
            return supplier.get();
        }
        finally {
            restore(previousContext, previousTraceId);
        }
    }

    private static void restore(Optional<CocoRequestContext> previousContext, Optional<String> previousTraceId) {
        if (previousContext.isPresent()) {
            set(previousContext.get());
            return;
        }
        REQUEST_CONTEXT.remove();
        if (previousTraceId.isPresent()) {
            CocoTraceContext.setTraceId(previousTraceId.get());
        }
        else {
            CocoTraceContext.clear();
        }
    }
}
