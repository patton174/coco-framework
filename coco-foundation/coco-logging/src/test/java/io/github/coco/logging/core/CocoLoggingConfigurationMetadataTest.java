package io.github.coco.logging.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import io.github.coco.logging.access.CocoAccessLogProperties;
import io.github.coco.logging.access.CocoAccessLogStyle;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

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
 *   <li>模块：{@code coco-logging}</li>
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
        assertTrue(content.contains("\"name\": \"coco.logging.node-renderer.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.logging.node-renderer.jar-only\""));
        assertTrue(content.contains("\"name\": \"coco.logging.node-renderer.command\""));
        assertTrue(content.contains("\"name\": \"coco.logging.node-renderer.color\""));
        assertTrue(content.contains("\"name\": \"coco.logging.access-log.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.logging.access-log.level\""));
        assertTrue(content.contains("\"name\": \"coco.logging.access-log.style\""));
        assertTrue(content.contains("\"name\": \"coco.logging.access-log.logger-name\""));
        assertFalse(content.contains("\"name\": \"coco.logging.access-log.include-parameters\""));
        assertFalse(content.contains("\"name\": \"coco.logging.access-log.max-parameter-value-length\""));
        assertFalse(content.contains("\"name\": \"coco.logging.access-log.masked-parameter-names\""));
    }

    @Test
    void exposesImmutableNestedConfigurationSnapshots() {
        CocoLoggingProperties.AsyncProperties async = new CocoLoggingProperties.AsyncProperties();
        async.setQueueCapacity(32);
        CocoLoggingProperties.NodeRendererProperties nodeRenderer = new CocoLoggingProperties.NodeRendererProperties();
        nodeRenderer.setColor("never");
        CocoAccessLogProperties accessLog = new CocoAccessLogProperties();
        accessLog.setLoggerName("example.access");
        CocoLoggingProperties properties = new CocoLoggingProperties();
        properties.setAsync(async);
        properties.setNodeRenderer(nodeRenderer);
        properties.setAccessLog(accessLog);
        async.setQueueCapacity(64);
        nodeRenderer.setColor("auto");
        accessLog.setLoggerName("changed.access");

        assertEquals(32, properties.getAsync().getQueueCapacity());
        assertEquals("never", properties.getNodeRenderer().getColor());
        assertEquals("example.access", properties.getAccessLog().getLoggerName());
        properties.getAsync().setQueueCapacity(128);
        properties.getNodeRenderer().setColor("auto");
        properties.getAccessLog().setLoggerName("snapshot.access");
        assertEquals(32, properties.getAsync().getQueueCapacity());
        assertEquals("never", properties.getNodeRenderer().getColor());
        assertEquals("example.access", properties.getAccessLog().getLoggerName());
    }

    @Test
    void bindsNestedLoggingPropertiesThroughJavaBeanAccessors() {
        CocoLoggingProperties properties = new CocoLoggingProperties();
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "coco.logging.async.enabled", "false",
                "coco.logging.async.queue-capacity", "64",
                "coco.logging.node-renderer.enabled", "false",
                "coco.logging.node-renderer.jar-only", "false",
                "coco.logging.node-renderer.command", "custom-node",
                "coco.logging.node-renderer.color", "NEVER",
                "coco.logging.access-log.enabled", "false",
                "coco.logging.access-log.level", "DEBUG",
                "coco.logging.access-log.style", "JSON",
                "coco.logging.access-log.logger-name", "example.access")));

        assertTrue(binder.bind("coco.logging", Bindable.ofInstance(properties)).isBound());
        assertFalse(properties.getAsync().isEnabled());
        assertEquals(64, properties.getAsync().getQueueCapacity());
        assertFalse(properties.getNodeRenderer().isEnabled());
        assertFalse(properties.getNodeRenderer().isJarOnly());
        assertEquals("custom-node", properties.getNodeRenderer().getCommand());
        assertEquals("never", properties.getNodeRenderer().getColor());
        assertFalse(properties.getAccessLog().isEnabled());
        assertEquals(CocoLogLevel.DEBUG, properties.getAccessLog().getLevel());
        assertEquals(CocoAccessLogStyle.JSON, properties.getAccessLog().getStyle());
        assertEquals("example.access", properties.getAccessLog().getLoggerName());
    }

    @Test
    void preservesPublishedLoggingConfigurationApi() throws ReflectiveOperationException {
        assertEquals(CocoLoggingProperties.class, Class.forName("io.github.coco.logging.core.CocoLoggingProperties"));
        assertNotNull(CocoLoggingProperties.class.getConstructor());
        assertEquals(CocoLoggingProperties.AsyncProperties.class,
                CocoLoggingProperties.class.getMethod("getAsync").getReturnType());
        assertEquals(CocoLoggingProperties.NodeRendererProperties.class,
                CocoLoggingProperties.class.getMethod("getNodeRenderer").getReturnType());
        assertEquals(CocoAccessLogProperties.class,
                CocoLoggingProperties.class.getMethod("getAccessLog").getReturnType());
        assertNotNull(CocoLoggingProperties.class.getMethod("setAsync", CocoLoggingProperties.AsyncProperties.class));
        assertNotNull(CocoLoggingProperties.class.getMethod("setNodeRenderer",
                CocoLoggingProperties.NodeRendererProperties.class));
        assertNotNull(CocoLoggingProperties.class.getMethod("setAccessLog", CocoAccessLogProperties.class));
    }

    @Test
    void preservesLogRecordFailureIdentity() {
        IllegalStateException failure = new IllegalStateException("failed");
        CocoLogRecord record = new CocoLogRecord(
                CocoLogHandle.of("test", "io.github.coco.test", CocoLogLevel.ERROR),
                CocoLogLevel.ERROR, "message", failure);

        assertSame(failure, record.failure().orElseThrow());
    }
}
