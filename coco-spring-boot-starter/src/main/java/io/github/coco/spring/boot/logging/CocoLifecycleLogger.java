package io.github.coco.spring.boot.logging;

import java.time.Duration;
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
                .append(" durationMs=").append(timeTaken.toMillis());
        resolvePort(context).ifPresent(port -> message.append(" port=").append(port));
        return message.toString();
    }

    private static OptionalInt resolvePort(ConfigurableApplicationContext context) {
        if (context instanceof WebServerApplicationContext webServerApplicationContext
                && webServerApplicationContext.getWebServer() != null) {
            return OptionalInt.of(webServerApplicationContext.getWebServer().getPort());
        }
        return OptionalInt.empty();
    }
}
