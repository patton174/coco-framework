package io.github.coco.logging.lifecycle;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;

import io.github.coco.logging.core.CocoLogHandles;
import io.github.coco.logging.core.CocoLogManager;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Coco 应用生命周期日志记录器。
 * <p>
 * 使用 Coco 日志管理器记录应用启动完成事件，替代 Spring Boot 原始启动摘要日志。
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
public final class CocoLifecycleLogger {

    private static final String STARTED_HANDLE = CocoLogHandles.LIFECYCLE;

    private final CocoLogManager logManager;

    /**
     * <p>
     * 创建默认生命周期日志记录器。
     * </p>
     */
    public CocoLifecycleLogger() {
        this(CocoLogManager.defaults());
    }

    /**
     * <p>
     * 创建生命周期日志记录器。
     * </p>
     * @param logManager Coco 日志管理器
     */
    public CocoLifecycleLogger(CocoLogManager logManager) {
        this.logManager = logManager == null ? CocoLogManager.defaults() : logManager;
    }

    /**
     * <p>
     * 记录应用上下文启动完成事件。
     * </p>
     * @param context 应用上下文
     * @param timeTaken 启动耗时
     */
    public void started(ConfigurableApplicationContext context, Duration timeTaken) {
        startedRecords(context, timeTaken)
                .forEach(message -> this.logManager.info(STARTED_HANDLE, message));
    }

    /**
     * <p>
     * 记录应用启动完成事件。
     * </p>
     * @param context 应用上下文
     * @param timeTaken 启动耗时
     */
    public void ready(ConfigurableApplicationContext context, Duration timeTaken) {
        this.logManager.info(STARTED_HANDLE, readyMessage(context, timeTaken));
    }

    /**
     * <p>
     * 记录应用启动失败事件。
     * </p>
     * @param context 应用上下文；启动早期失败时可能为空
     * @param exception 启动异常
     */
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
        this.logManager.error(STARTED_HANDLE, failedMessage(context, exception), exception);
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
        return String.join(System.lineSeparator(), startedRecords(context, timeTaken));
    }

    /**
     * <p>
     * 创建应用上下文启动完成事件日志文本列表。
     * </p>
     * @param context 应用上下文
     * @param timeTaken 启动耗时
     * @return 应用上下文启动完成事件日志文本列表
     */
    public List<String> startedMessages(ConfigurableApplicationContext context, Duration timeTaken) {
        return startedRecords(context, timeTaken);
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
        StringBuilder message = new StringBuilder("◂ ready")
                .append(" app=").append(context.getId())
                .append(" profiles=").append(activeProfiles(context))
                .append(" time=").append(durationMillis(timeTaken)).append("ms");
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
        String message = exception == null || exception.getMessage() == null || exception.getMessage().isBlank()
                ? "unknown"
                : exception.getMessage();
        return String.join(System.lineSeparator(),
                "◂ failed",
                "  app       " + application,
                "  exception " + exceptionName,
                "  message   " + message);
    }

    private static List<String> startedRecords(ConfigurableApplicationContext context, Duration timeTaken) {
        return List.of(
                "app " + context.getId(),
                "profiles " + activeProfiles(context),
                "time " + durationMillis(timeTaken) + "ms",
                "java " + System.getProperty("java.version"),
                "pid " + ProcessHandle.current().pid());
    }

    private static long durationMillis(Duration duration) {
        return duration == null ? 0L : duration.toMillis();
    }

    private static OptionalInt resolvePort(ConfigurableApplicationContext context) {
        try {
            Method getWebServer = context.getClass().getMethod("getWebServer");
            Object webServer = getWebServer.invoke(context);
            if (webServer == null) {
                return OptionalInt.empty();
            }
            Method getPort = webServer.getClass().getMethod("getPort");
            Object port = getPort.invoke(webServer);
            return port instanceof Number number ? OptionalInt.of(number.intValue()) : OptionalInt.empty();
        }
        catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
            return OptionalInt.empty();
        }
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
}
