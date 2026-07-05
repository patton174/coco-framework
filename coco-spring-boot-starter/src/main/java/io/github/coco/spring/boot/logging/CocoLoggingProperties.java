package io.github.coco.spring.boot.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Coco 日志机制配置属性。
 * <p>
 * 绑定 {@code coco.logging} 命名空间，控制 Starter 是否注入 Coco 默认日志样式和 Spring 原始启动日志降噪策略。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-spring-boot-starter}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "coco.logging")
public class CocoLoggingProperties {

    /**
     * Coco 默认控制台日志格式。
     */
    public static final String DEFAULT_CONSOLE_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level COCO [%15.15thread] "
            + "%-40.40logger{39} : %msg%n%ex";

    private boolean enabled = true;

    private boolean quietSpring = true;

    private String consolePattern = DEFAULT_CONSOLE_PATTERN;

    /**
     * <p>
     * 返回是否启用 Coco 默认日志机制。
     * </p>
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    /**
     * <p>
     * 设置是否启用 Coco 默认日志机制。
     * </p>
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * <p>
     * 返回是否降低 Spring 与容器原始启动日志噪音。
     * </p>
     * @return 降噪时返回 {@code true}
     */
    public boolean isQuietSpring() {
        return this.quietSpring;
    }

    /**
     * <p>
     * 设置是否降低 Spring 与容器原始启动日志噪音。
     * </p>
     * @param quietSpring 是否降噪
     */
    public void setQuietSpring(boolean quietSpring) {
        this.quietSpring = quietSpring;
    }

    /**
     * <p>
     * 返回 Coco 默认控制台日志格式。
     * </p>
     * @return 控制台日志格式
     */
    public String getConsolePattern() {
        return this.consolePattern;
    }

    /**
     * <p>
     * 设置 Coco 默认控制台日志格式。
     * </p>
     * @param consolePattern 控制台日志格式
     */
    public void setConsolePattern(String consolePattern) {
        this.consolePattern = consolePattern == null || consolePattern.isBlank()
                ? DEFAULT_CONSOLE_PATTERN
                : consolePattern;
    }
}
