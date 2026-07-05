package io.github.coco.spring.boot.logging;

import java.time.Duration;
import java.util.Arrays;
import java.util.OptionalInt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Coco 应用生命周期日志记录器。
 * <p>
 * 使用 Coco 自己的结构化日志文本记录应用启动完成事件，替代 Spring Boot 原始启动摘要日志。
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
public final class CocoLifecycleLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger("io.github.coco.lifecycle");

    /**
     * <p>
     * 记录应用上下文启动完成事件。
     * </p>
     * @param context 应用上下文
     * @param timeTaken 启动耗时
     */
    public void started(ConfigurableApplicationContext context, Duration timeTaken) {
        LOGGER.info(startedMessage(context, timeTaken));
    }

    /**
     * <p>
     * 记录应用启动完成事件。
     * </p>
     * @param context 应用上下文
     * @param timeTaken 启动耗时
     */
    public void ready(ConfigurableApplicationContext context, Duration timeTaken) {
        LOGGER.info(readyMessage(context, timeTaken));
    }

    /**
     * <p>
     * 记录应用启动失败事件。
     * </p>
     * @param context 应用上下文；启动早期失败时可能为空
     * @param exception 启动异常
     */
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
        LOGGER.error(failedMessage(context, exception), exception);
    }

    /**
     * <p>
     * 创建应用上下文启动完成事件日志文本。
     * </p>
     * @param context 应用上下文
     * @param timeTaken 启动耗时
     * @return 应用上下文启动完成事件日志文本
     */
    public String startedMessage(ConfigurableApplicationContext context, Duration timeTaken) {
        return new StringBuilder()
                .append("event=coco.started")
                .append(" application=").append(context.getId())
                .append(" durationMs=").append(timeTaken.toMillis())
                .append(" profiles=").append(activeProfiles(context))
                .append(" java=").append(System.getProperty("java.version"))
                .append(" pid=").append(ProcessHandle.current().pid())
                .append(" cwd=\"").append(escape(System.getProperty("user.dir"))).append('"')
                .toString();
    }

    /**
     * <p>
     * 创建应用启动完成事件日志文本。
     * </p>
     * @param context 应用上下文
     * @param timeTaken 启动耗时
     * @return 启动完成事件日志文本
     */
    public String readyMessage(ConfigurableApplicationContext context, Duration timeTaken) {
        StringBuilder message = new StringBuilder()
                .append("event=coco.ready")
                .append(" application=").append(context.getId())
                .append(" durationMs=").append(timeTaken.toMillis())
                .append(" profiles=").append(activeProfiles(context));
        resolvePort(context).ifPresent(port -> message.append(" port=").append(port));
        return message.toString();
    }

    /**
     * <p>
     * 创建应用启动失败事件日志文本。
     * </p>
     * @param context 应用上下文；启动早期失败时可能为空
     * @param exception 启动异常
     * @return 应用启动失败事件日志文本
     */
    public String failedMessage(ConfigurableApplicationContext context, Throwable exception) {
        String application = context == null ? "unknown" : context.getId();
        String exceptionName = exception == null ? "unknown" : exception.getClass().getName();
        return "event=coco.failed application=" + application + " exception=" + exceptionName;
    }

    private static OptionalInt resolvePort(ConfigurableApplicationContext context) {
        if (context instanceof WebServerApplicationContext webServerApplicationContext
                && webServerApplicationContext.getWebServer() != null) {
            return OptionalInt.of(webServerApplicationContext.getWebServer().getPort());
        }
        return OptionalInt.empty();
    }

    private static String activeProfiles(ConfigurableApplicationContext context) {
        String[] activeProfiles = context.getEnvironment().getActiveProfiles();
        if (activeProfiles.length == 0) {
            return "default";
        }
        return String.join(",", Arrays.stream(activeProfiles)
                .filter(profile -> profile != null && !profile.isBlank())
                .map(String::trim)
                .toList());
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
