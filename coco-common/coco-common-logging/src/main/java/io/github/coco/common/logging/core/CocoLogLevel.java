package io.github.coco.common.logging.core;

/**
 * Coco 日志输出级别。
 * <p>
 * 用于框架内部隔离控制不同日志句柄的输出级别，避免日志模块调用方直接依赖具体日志框架。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public enum CocoLogLevel {

    /**
     * <p>
     * 关闭日志输出。
     * </p>
     */
    OFF,

    /**
     * <p>
     * 使用 ERROR 级别输出。
     * </p>
     */
    ERROR,

    /**
     * <p>
     * 使用 WARN 级别输出。
     * </p>
     */
    WARN,

    /**
     * <p>
     * 使用 INFO 级别输出。
     * </p>
     */
    INFO,

    /**
     * <p>
     * 使用 DEBUG 级别输出。
     * </p>
     */
    DEBUG,

    /**
     * <p>
     * 使用 TRACE 级别输出。
     * </p>
     */
    TRACE;

    /**
     * <p>
     * 判断当前级别是否允许输出。
     * </p>
     * @return 非 {@link #OFF} 时返回 {@code true}
     */
    public boolean enabled() {
        return this != OFF;
    }
}
