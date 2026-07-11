package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.github.coco.logging.CocoLogHandleRegistry;
import io.github.coco.logging.CocoLogHandles;
import io.github.coco.logging.CocoLogManager;
import io.github.coco.logging.lifecycle.CocoLifecycleLogger;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

/**
 * Coco 生命周期日志测试。
 * <p>
 * 验证应用启动完成日志使用 Coco 自己的多行结构化文本，而不是 Spring Boot 原始启动摘要。
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
    void formatsReadyMessageAsSingleLine() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setId("sample");
        CocoLifecycleLogger logger = new CocoLifecycleLogger();

        String message = logger.readyMessage(context, Duration.ofMillis(1234));

        assertEquals("◂ ready app=sample profiles=default time=1234ms", message);
        assertFalse(message.contains(System.lineSeparator()));
        assertFalse(message.contains("Started "));
    }

    @Test
    void writesStartedRuntimeFieldsAsIndependentInfoRecords() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setId("sample");
        List<String> messages = new ArrayList<>();
        CocoLogHandleRegistry registry = new CocoLogHandleRegistry();
        CocoLogHandles.registerDefaults(registry);
        CocoLogManager logManager = new CocoLogManager(registry, record -> messages.add(record.message()));
        CocoLifecycleLogger logger = new CocoLifecycleLogger(logManager);

        logger.started(context, Duration.ofMillis(1000));

        assertEquals(5, messages.size());
        assertEquals("app sample", messages.get(0));
        assertEquals("profiles default", messages.get(1));
        assertEquals("time 1000ms", messages.get(2));
        assertTrue(messages.get(3).startsWith("java "));
        assertTrue(messages.get(4).startsWith("pid "));
        assertFalse(messages.stream().anyMatch(message -> message.contains("startup")));
        assertFalse(messages.stream().anyMatch(message -> message.contains("workdir")));
    }

    @Test
    void formatsStartedMessageWithoutStartupHeader() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setId("sample");
        CocoLifecycleLogger logger = new CocoLifecycleLogger();

        String message = logger.startedMessage(context, Duration.ofMillis(1000));

        assertTrue(message.contains("app sample"));
        assertTrue(message.contains(System.lineSeparator() + "profiles default"));
        assertTrue(message.contains(System.lineSeparator() + "time 1000ms"));
        assertFalse(message.contains("▸ startup"));
        assertFalse(message.contains("workdir"));
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
