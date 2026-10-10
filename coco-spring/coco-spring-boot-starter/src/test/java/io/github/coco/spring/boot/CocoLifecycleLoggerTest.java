package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.github.coco.logging.core.CocoLogHandleRegistry;
import io.github.coco.logging.core.CocoLogHandles;
import io.github.coco.logging.core.CocoLogManager;
import io.github.coco.logging.lifecycle.CocoLifecycleLogger;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Coco 生命周期日志测试。
 * <p>
 * 验证应用启动完成日志只输出一条 Coco 单行文本，而不是 Spring Boot 原始启动摘要或多条键值记录。
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
class CocoLifecycleLoggerTest {

    @Test
    void formatsReadyMessageAsSingleLineWithRuntimeFields() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setId("sample");
        CocoLifecycleLogger logger = new CocoLifecycleLogger();

        String message = logger.readyMessage(context, Duration.ofMillis(1234));

        assertTrue(message.startsWith("◂ ready app=sample profiles=default time=1234ms"));
        assertTrue(message.contains(" java=" + System.getProperty("java.version")));
        assertTrue(message.endsWith(" pid=" + ProcessHandle.current().pid()));
        assertFalse(message.contains(System.lineSeparator()));
        assertFalse(message.contains("Started "));
    }

    @Test
    void writesReadyAsSingleInfoRecord() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setId("sample");
        List<String> messages = new ArrayList<>();
        CocoLogHandleRegistry registry = new CocoLogHandleRegistry();
        CocoLogHandles.registerDefaults(registry);
        CocoLogManager logManager = new CocoLogManager(registry, record -> messages.add(record.message()));
        CocoLifecycleLogger logger = new CocoLifecycleLogger(logManager);

        logger.ready(context, Duration.ofMillis(1000));

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).startsWith("◂ ready app=sample"));
        assertFalse(messages.stream().anyMatch(message -> message.contains("startup")));
        assertFalse(messages.stream().anyMatch(message -> message.contains("workdir")));
    }

    @Test
    void formatsFailedMessageWithExceptionMessage() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setId("sample");
        CocoLifecycleLogger logger = new CocoLifecycleLogger();

        String message = logger.failedMessage(context, new IllegalStateException("port already in use"));

        assertEquals(String.join(System.lineSeparator(),
                "◂ failed",
                "  app       sample",
                "  exception java.lang.IllegalStateException",
                "  message   port already in use"), message);
    }
}
