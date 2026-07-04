package io.github.coco.common.trace;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Coco Trace 上下文。
 * <p>
 * 基于 {@link ThreadLocal} 保存当前线程的 TraceId，供后续日志、审计、链路追踪和业务扩展能力共享同一个请求标识。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-core}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public final class CocoTraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private CocoTraceContext() {
    }

    /**
     * <p>
     * 返回当前线程中的 TraceId。
     * </p>
     * @return 当前 TraceId；未设置时返回空结果
     */
    public static Optional<String> currentTraceId() {
        return Optional.ofNullable(TRACE_ID.get());
    }

    /**
     * <p>
     * 获取当前线程 TraceId；如果不存在则自动生成并保存。
     * </p>
     * @return 当前或新生成的 TraceId
     */
    public static String getOrCreateTraceId() {
        return currentTraceId().orElseGet(() -> setTraceId(CocoTraceIdGenerator.generate()));
    }

    /**
     * <p>
     * 设置当前线程 TraceId。
     * </p>
     * @param traceId TraceId
     * @return 去除首尾空白后的 TraceId
     */
    public static String setTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        String checkedTraceId = traceId.trim();
        TRACE_ID.set(checkedTraceId);
        return checkedTraceId;
    }

    /**
     * <p>
     * 清除当前线程 TraceId。
     * </p>
     */
    public static void clear() {
        TRACE_ID.remove();
    }

    /**
     * <p>
     * 在指定 TraceId 上下文中执行逻辑，并在结束后恢复之前的上下文。
     * </p>
     * @param traceId 临时 TraceId
     * @param runnable 待执行逻辑
     */
    public static void runWithTraceId(String traceId, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable must not be null");
        Optional<String> previous = currentTraceId();
        setTraceId(traceId);
        try {
            runnable.run();
        }
        finally {
            restore(previous);
        }
    }

    /**
     * <p>
     * 在指定 TraceId 上下文中执行逻辑，返回执行结果，并在结束后恢复之前的上下文。
     * </p>
     * @param traceId 临时 TraceId
     * @param supplier 待执行逻辑
     * @param <T> 返回值类型
     * @return 逻辑执行结果
     */
    public static <T> T callWithTraceId(String traceId, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        Optional<String> previous = currentTraceId();
        setTraceId(traceId);
        try {
            return supplier.get();
        }
        finally {
            restore(previous);
        }
    }

    private static void restore(Optional<String> previous) {
        if (previous.isPresent()) {
            TRACE_ID.set(previous.get());
        }
        else {
            clear();
        }
    }
}
