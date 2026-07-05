package io.github.coco.common.logging.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Coco 日志配置元数据测试。
 * <p>
 * 验证日志模块提供 Spring Boot IDE 可识别的配置提示。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-common-logging}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoLoggingConfigurationMetadataTest {

    @Test
    void exposesLoggingPropertyMetadata() throws IOException {
        InputStream metadata = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json");

        assertNotNull(metadata);
        String content = new String(metadata.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"name\": \"coco.logging.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.logging.quiet-spring\""));
        assertTrue(content.contains("\"name\": \"coco.logging.console-pattern\""));
        assertTrue(content.contains("\"name\": \"coco.logging.async.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.logging.async.queue-capacity\""));
        assertTrue(content.contains("\"name\": \"coco.logging.access-log.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.logging.access-log.level\""));
        assertTrue(content.contains("\"name\": \"coco.logging.access-log.style\""));
        assertTrue(content.contains("\"name\": \"coco.logging.access-log.logger-name\""));
    }
}
