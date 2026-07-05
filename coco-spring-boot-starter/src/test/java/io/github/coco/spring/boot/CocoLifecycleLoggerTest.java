package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import io.github.coco.common.logging.lifecycle.CocoLifecycleLogger;
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
    void formatsReadyMessageAsMultilinePanel() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setId("sample");
        CocoLifecycleLogger logger = new CocoLifecycleLogger();

        String message = logger.readyMessage(context, Duration.ofMillis(1234));

        assertEquals(String.join(System.lineSeparator(),
                "◂ ready",
                "  app      sample",
                "  profiles default",
                "  time     1234ms"), message);
        assertFalse(message.contains("Started "));
    }

    @Test
    void formatsStartedMessageAsMultilinePanelWithRuntimeFields() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.setId("sample");
        CocoLifecycleLogger logger = new CocoLifecycleLogger();

        String message = logger.startedMessage(context, Duration.ofMillis(1000));

        assertTrue(message.contains("▸ startup"));
        assertTrue(message.contains(System.lineSeparator() + "  app      sample"));
        assertTrue(message.contains(System.lineSeparator() + "  profiles default"));
        assertTrue(message.contains(System.lineSeparator() + "  time     1000ms"));
        assertTrue(message.contains(System.lineSeparator() + "  java     "));
        assertTrue(message.contains(System.lineSeparator() + "  pid      "));
        assertTrue(message.contains(System.lineSeparator() + "  workdir  \""));
    }
}
