package io.github.coco.logging.context;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import io.github.coco.context.CocoContextScope;
import io.github.coco.context.CocoContextSnapshot;
import org.slf4j.MDC;

/**
 * Coco MDC 上下文工具。
 * <p>
 * 捕获当前线程的完整 MDC，并通过 Coco 上下文快照在异步线程中临时恢复；作用域关闭后恢复工作线程原有 MDC，避免线程池复用导致日志上下文泄漏。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoMdcContext {

    private CocoMdcContext() {
    }

    /**
     * <p>
     * 捕获当前线程的完整 MDC。
     * </p>
     * @return 可跨线程恢复的 MDC 快照
     */
    public static CocoContextSnapshot capture() {
        Map<String, String> captured = currentContextMap();
        return () -> {
            Map<String, String> previous = currentContextMap();
            restore(captured);
            return () -> restore(previous);
        };
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
     * 捕获当前 MDC 上下文并包装 {@link Runnable}。
     * </p>
     * @param runnable 待执行逻辑
     * @return 包装后的逻辑
     */
    public static Runnable wrap(Runnable runnable) {
        return capture().wrap(runnable);
    }

    /**
     * <p>
     * 捕获当前 MDC 上下文并包装 {@link Callable}。
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
     * 捕获当前 MDC 上下文并包装 {@link Supplier}。
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
     * 在指定 MDC 上下文中执行逻辑，并在结束后恢复之前的上下文。
     * </p>
     * @param contextMap 临时 MDC 上下文
     * @param runnable 待执行逻辑
     */
    public static void runWithContext(Map<String, String> contextMap, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        callWithContext(contextMap, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * <p>
     * 在指定 MDC 上下文中执行逻辑，返回执行结果，并在结束后恢复之前的上下文。
     * </p>
     * @param contextMap 临时 MDC 上下文
     * @param supplier 待执行逻辑
     * @param <T> 返回值类型
     * @return 逻辑执行结果
     */
    public static <T> T callWithContext(Map<String, String> contextMap, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        Map<String, String> checkedContextMap = copyContextMap(contextMap);
        Map<String, String> previous = currentContextMap();
        restore(checkedContextMap);
        try {
            return supplier.get();
        }
        finally {
            restore(previous);
        }
    }

    private static Map<String, String> currentContextMap() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return contextMap == null ? Map.of() : copyContextMap(contextMap);
    }

    private static Map<String, String> copyContextMap(Map<String, String> contextMap) {
        Objects.requireNonNull(contextMap, "contextMap must not be null");
        return contextMap.isEmpty() ? Map.of() : Map.copyOf(contextMap);
    }

    private static void restore(Map<String, String> contextMap) {
        MDC.clear();
        if (!contextMap.isEmpty()) {
            MDC.setContextMap(contextMap);
        }
    }
}
