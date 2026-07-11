package io.github.coco.logging;

import java.util.Objects;

/**
 * Coco 日志句柄。
 * <p>
 * 框架内部模块通过句柄声明自己的日志隔离边界，由日志模块统一决定 logger 名称、输出级别和最终打印方式。
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
public final class CocoLogHandle {

    private final String name;

    private final String loggerName;

    private final CocoLogLevel defaultLevel;

    private CocoLogHandle(String name, String loggerName, CocoLogLevel defaultLevel) {
        this.name = requireText(name, "name");
        this.loggerName = requireText(loggerName, "loggerName");
        this.defaultLevel = defaultLevel == null ? CocoLogLevel.INFO : defaultLevel;
    }

    /**
     * <p>
     * 创建日志句柄。
     * </p>
     * @param name 句柄名称
     * @param loggerName SLF4J logger 名称
     * @param defaultLevel 默认输出级别
     * @return 日志句柄
     */
    public static CocoLogHandle of(String name, String loggerName, CocoLogLevel defaultLevel) {
        return new CocoLogHandle(name, loggerName, defaultLevel);
    }

    /**
     * <p>
     * 返回句柄名称。
     * </p>
     * @return 句柄名称
     */
    public String name() {
        return this.name;
    }

    /**
     * <p>
     * 返回隔离 logger 名称。
     * </p>
     * @return logger 名称
     */
    public String loggerName() {
        return this.loggerName;
    }

    /**
     * <p>
     * 返回默认输出级别。
     * </p>
     * @return 默认输出级别
     */
    public CocoLogLevel defaultLevel() {
        return this.defaultLevel;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
