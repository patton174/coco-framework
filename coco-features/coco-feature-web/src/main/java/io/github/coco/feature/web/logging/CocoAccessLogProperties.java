package io.github.coco.feature.web.logging;

/**
 * Coco 接口访问日志打印配置属性。
 * <p>
 * 绑定 {@code coco.web.access-log} 命名空间，控制接口访问日志是否打印、打印级别、输出样式和隔离 logger 名称。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
public class CocoAccessLogProperties {

    /**
     * 默认接口访问日志 logger 名称。
     */
    public static final String DEFAULT_LOGGER_NAME = "io.github.coco.access";

    private boolean enabled = true;

    private CocoAccessLogLevel level = CocoAccessLogLevel.INFO;

    private CocoAccessLogStyle style = CocoAccessLogStyle.TEXT;

    private String loggerName = DEFAULT_LOGGER_NAME;

    /**
     * <p>
     * 返回是否启用接口访问日志打印。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用接口访问日志打印。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回接口访问日志打印级别。
     * </p>
     * @return 打印级别
     */
    public CocoAccessLogLevel getLevel() {
        return this.level;
    }

    /**
     * <p>
     * 设置接口访问日志打印级别。
     * </p>
     * @param level 打印级别
     */
    public void setLevel(CocoAccessLogLevel level) {
        this.level = level == null ? CocoAccessLogLevel.INFO : level;
    }

    /**
     * <p>
     * 返回接口访问日志输出样式。
     * </p>
     * @return 输出样式
     */
    public CocoAccessLogStyle getStyle() {
        return this.style;
    }

    /**
     * <p>
     * 设置接口访问日志输出样式。
     * </p>
     * @param style 输出样式
     */
    public void setStyle(CocoAccessLogStyle style) {
        this.style = style == null ? CocoAccessLogStyle.TEXT : style;
    }

    /**
     * <p>
     * 返回接口访问日志隔离 logger 名称。
     * </p>
     * @return logger 名称
     */
    public String getLoggerName() {
        return this.loggerName;
    }

    /**
     * <p>
     * 设置接口访问日志隔离 logger 名称。
     * </p>
     * @param loggerName logger 名称
     */
    public void setLoggerName(String loggerName) {
        this.loggerName = loggerName == null || loggerName.isBlank()
                ? DEFAULT_LOGGER_NAME
                : loggerName.trim();
    }
}
