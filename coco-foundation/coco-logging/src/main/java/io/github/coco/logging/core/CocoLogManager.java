package io.github.coco.logging.core;

import java.util.Objects;

/**
 * Coco 日志管理器。
 * <p>
 * 框架内部模块只向管理器提交句柄名称、级别和日志正文，具体 logger、样式和输出目标由日志模块集中处理。
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
public final class CocoLogManager {

    private final CocoLogHandleRegistry registry;

    private final CocoLogSink sink;

    /**
     * <p>
     * 创建日志管理器。
     * </p>
     * @param registry 日志句柄注册表
     * @param sink 日志输出器
     */
    public CocoLogManager(CocoLogHandleRegistry registry, CocoLogSink sink) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
    }

    /**
     * <p>
     * 创建带内置句柄和 SLF4J 输出器的默认日志管理器。
     * </p>
     * @return 默认日志管理器
     */
    public static CocoLogManager defaults() {
        CocoLogHandleRegistry registry = new CocoLogHandleRegistry();
        CocoLogHandles.registerDefaults(registry);
        return new CocoLogManager(registry, new Slf4jCocoLogSink());
    }

    /**
     * <p>
     * 输出日志。
     * </p>
     * @param handleName 日志句柄名称
     * @param level 输出级别
     * @param message 日志正文
     * @param failure 异常；没有异常时为空
     */
    public void log(String handleName, CocoLogLevel level, String message, Throwable failure) {
        CocoLogLevel checkedLevel = level == null ? CocoLogLevel.INFO : level;
        CocoLogHandle handle = this.registry.find(handleName)
                .orElseGet(() -> CocoLogHandle.of(handleName, "io.github.coco." + handleName, checkedLevel));
        if (!handle.defaultLevel().allows(checkedLevel)) {
            return;
        }
        this.sink.log(new CocoLogRecord(handle, checkedLevel, message, failure));
    }

    /**
     * <p>
     * 使用 INFO 级别输出日志。
     * </p>
     * @param handleName 日志句柄名称
     * @param message 日志正文
     */
    public void info(String handleName, String message) {
        log(handleName, CocoLogLevel.INFO, message, null);
    }

    /**
     * <p>
     * 使用 ERROR 级别输出日志。
     * </p>
     * @param handleName 日志句柄名称
     * @param message 日志正文
     * @param failure 异常
     */
    public void error(String handleName, String message, Throwable failure) {
        log(handleName, CocoLogLevel.ERROR, message, failure);
    }
}
