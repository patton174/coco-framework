package io.github.coco.logging.core;

import java.util.Objects;
import java.util.Optional;

/**
 * Coco 日志记录。
 * <p>
 * 表示日志模块准备输出的一条标准日志，包含句柄、级别、正文和可选异常。
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
public final class CocoLogRecord {

    private final CocoLogHandle handle;

    private final CocoLogLevel level;

    private final String message;

    private final Throwable failure;

    /**
     * <p>
     * 创建日志记录。
     * </p>
     * @param handle 日志句柄
     * @param level 输出级别
     * @param message 日志正文
     * @param failure 异常；没有异常时为空
     */
    public CocoLogRecord(CocoLogHandle handle, CocoLogLevel level, String message, Throwable failure) {
        this.handle = Objects.requireNonNull(handle, "handle must not be null");
        this.level = level == null ? CocoLogLevel.INFO : level;
        this.message = message == null ? "" : message;
        this.failure = failure;
    }

    /**
     * <p>
     * 返回日志句柄。
     * </p>
     * @return 日志句柄
     */
    public CocoLogHandle handle() {
        return this.handle;
    }

    /**
     * <p>
     * 返回输出级别。
     * </p>
     * @return 输出级别
     */
    public CocoLogLevel level() {
        return this.level;
    }

    /**
     * <p>
     * 返回日志正文。
     * </p>
     * @return 日志正文
     */
    public String message() {
        return this.message;
    }

    /**
     * <p>
     * 返回日志异常。
     * </p>
     * @return 日志异常；没有异常时为空
     */
    public Optional<Throwable> failure() {
        return Optional.ofNullable(this.failure);
    }
}
