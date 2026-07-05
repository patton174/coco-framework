package io.github.coco.feature.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Coco Web 配置元数据测试。
 * <p>
 * 验证 Web 功能模块提供 Spring Boot IDE 可识别的配置提示。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-web}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoWebConfigurationMetadataTest {

    @Test
    void exposesTracePropertyMetadata() throws IOException {
        InputStream metadata = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json");

        assertNotNull(metadata);
        String content = new String(metadata.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"name\": \"coco.web.trace.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.header-name\""));
        assertTrue(content.contains("\"name\": \"coco.web.trace.mdc-key\""));
        assertTrue(content.contains("\"name\": \"coco.web.response-wrap.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.web.response-wrap.success-message-code\""));
        assertFalse(content.contains("\"name\": \"coco.web.response-wrap.success-code\""));
    }
}
