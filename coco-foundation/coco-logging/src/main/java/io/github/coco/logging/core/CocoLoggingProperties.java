package io.github.coco.logging.core;

import java.util.Locale;

import io.github.coco.logging.access.CocoAccessLogProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Coco 日志机制配置属性。
 * <p>
 * 绑定 {@code coco.logging} 命名空间，控制 Coco 默认控制台日志格式、Spring 原始启动日志降噪、
 * 异步日志输出、Node 终端渲染器以及访问日志配置。
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
@ConfigurationProperties(prefix = "coco.logging")
public class CocoLoggingProperties {

    /**
     * Coco 默认控制台日志格式。
     */
    public static final String DEFAULT_CONSOLE_PATTERN = "%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} "
            + "%highlight(%-5level) %clr(COCO){cyan} %clr(%logger{32}){magenta} "
            + "%clr([%thread]){faint} : %msg%n%wEx";

    private boolean enabled = true;

    private boolean quietSpring = true;

    private String consolePattern = DEFAULT_CONSOLE_PATTERN;

    @NestedConfigurationProperty
    private AsyncProperties async = new AsyncProperties();

    @NestedConfigurationProperty
    private NodeRendererProperties nodeRenderer = new NodeRendererProperties();

    @NestedConfigurationProperty
    private CocoAccessLogProperties accessLog = new CocoAccessLogProperties();

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

    /**
     * <p>
     * 返回异步日志配置。
     * </p>
     * @return 异步日志配置
     */
    public AsyncProperties getAsync() {
        return copy(this.async);
    }

    /**
     * <p>
     * 设置异步日志配置。
     * </p>
     * @param async 异步日志配置
     */
    public void setAsync(AsyncProperties async) {
        this.async = copy(async);
    }

    /**
     * <p>
     * 返回 Node 终端日志渲染器配置。
     * </p>
     * @return Node 终端日志渲染器配置
     */
    public NodeRendererProperties getNodeRenderer() {
        return copy(this.nodeRenderer);
    }

    /**
     * <p>
     * 设置 Node 终端日志渲染器配置。
     * </p>
     * @param nodeRenderer Node 终端日志渲染器配置
     */
    public void setNodeRenderer(NodeRendererProperties nodeRenderer) {
        this.nodeRenderer = copy(nodeRenderer);
    }

    /**
     * <p>
     * 返回访问日志配置。
     * </p>
     * @return 访问日志配置
     */
    public CocoAccessLogProperties getAccessLog() {
        return copy(this.accessLog);
    }

    /**
     * <p>
     * 设置访问日志配置。
     * </p>
     * @param accessLog 访问日志配置
     */
    public void setAccessLog(CocoAccessLogProperties accessLog) {
        this.accessLog = copy(accessLog);
    }

    private static AsyncProperties copy(AsyncProperties source) {
        AsyncProperties copy = new AsyncProperties();
        if (source != null) {
            copy.setEnabled(source.isEnabled());
            copy.setQueueCapacity(source.getQueueCapacity());
        }
        return copy;
    }

    private static NodeRendererProperties copy(NodeRendererProperties source) {
        NodeRendererProperties copy = new NodeRendererProperties();
        if (source != null) {
            copy.setEnabled(source.isEnabled());
            copy.setJarOnly(source.isJarOnly());
            copy.setCommand(source.getCommand());
            copy.setColor(source.getColor());
        }
        return copy;
    }

    private static CocoAccessLogProperties copy(CocoAccessLogProperties source) {
        CocoAccessLogProperties copy = new CocoAccessLogProperties();
        if (source != null) {
            copy.setEnabled(source.isEnabled());
            copy.setLevel(source.getLevel());
            copy.setStyle(source.getStyle());
            copy.setLoggerName(source.getLoggerName());
        }
        return copy;
    }

    /**
     * Coco 异步日志配置。
     * <p>
     * 控制框架日志是否通过进程内有界队列异步写出。
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
    public static class AsyncProperties {

        private static final int DEFAULT_QUEUE_CAPACITY = 1024;

        private boolean enabled = true;

        private int queueCapacity = DEFAULT_QUEUE_CAPACITY;

        /**
         * <p>
         * 返回是否启用异步日志输出。
         * </p>
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return this.enabled;
        }

        /**
         * <p>
         * 设置是否启用异步日志输出。
         * </p>
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * <p>
         * 返回异步日志队列容量。
         * </p>
         * @return 异步日志队列容量
         */
        public int getQueueCapacity() {
            return this.queueCapacity;
        }

        /**
         * <p>
         * 设置异步日志队列容量。
         * </p>
         * @param queueCapacity 队列容量；小于等于零时使用默认容量
         */
        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity <= 0 ? DEFAULT_QUEUE_CAPACITY : queueCapacity;
        }
    }

    /**
     * Coco Node 终端日志渲染器配置。
     * <p>
     * 控制 jar 启动时是否自动启动 Node.js 日志渲染子进程来接管控制台输出。
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
    public static class NodeRendererProperties {

        private boolean enabled = true;

        private boolean jarOnly = true;

        private String command = "node";

        private String color = "always";

        /**
         * <p>
         * 返回是否启用 Node 终端日志渲染器。
         * </p>
         * @return 启用时返回 {@code true}
         */
        public boolean isEnabled() {
            return this.enabled;
        }

        /**
         * <p>
         * 设置是否启用 Node 终端日志渲染器。
         * </p>
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * <p>
         * 返回是否只在 {@code java -jar} 启动场景自动接管日志输出。
         * </p>
         * @return 仅 jar 启动场景启用时返回 {@code true}
         */
        public boolean isJarOnly() {
            return this.jarOnly;
        }

        /**
         * <p>
         * 设置是否只在 {@code java -jar} 启动场景自动接管日志输出。
         * </p>
         * @param jarOnly 是否仅 jar 启动场景启用
         */
        public void setJarOnly(boolean jarOnly) {
            this.jarOnly = jarOnly;
        }

        /**
         * <p>
         * 返回 Node.js 命令。
         * </p>
         * @return Node.js 命令
         */
        public String getCommand() {
            return this.command;
        }

        /**
         * <p>
         * 设置 Node.js 命令。
         * </p>
         * @param command Node.js 命令；为空时使用 {@code node}
         */
        public void setCommand(String command) {
            this.command = command == null || command.isBlank() ? "node" : command.trim();
        }

        /**
         * <p>
         * 返回颜色模式。
         * </p>
         * @return 颜色模式，支持 {@code always}、{@code auto}、{@code never}
         */
        public String getColor() {
            return this.color;
        }

        /**
         * <p>
         * 设置颜色模式。
         * </p>
         * @param color 颜色模式，支持 {@code always}、{@code auto}、{@code never}
         */
        public void setColor(String color) {
            String checkedColor = color == null || color.isBlank()
                    ? "always"
                    : color.trim().toLowerCase(Locale.ROOT);
            this.color = switch (checkedColor) {
                case "auto", "never" -> checkedColor;
                default -> "always";
            };
        }
    }
}
