package io.github.coco.spring.boot;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Coco Starter 配置元数据测试。
 * <p>
 * 验证 Starter 模块提供 Spring Boot IDE 可识别的启动 banner 配置提示。
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
class CocoStarterConfigurationMetadataTest {

    @Test
    void exposesBannerPropertyMetadata() throws IOException {
        InputStream metadata = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json");

        assertNotNull(metadata);
        String content = new String(metadata.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"name\": \"coco.banner.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.banner.title\""));
        assertTrue(content.contains("\"name\": \"coco.banner.author\""));
        assertTrue(content.contains("\"name\": \"coco.banner.repository\""));
        assertTrue(content.contains("\"name\": \"coco.banner.version\""));
        assertTrue(content.contains("\"name\": \"coco.logging.enabled\""));
        assertTrue(content.contains("\"name\": \"coco.logging.quiet-spring\""));
        assertTrue(content.contains("\"name\": \"coco.logging.console-pattern\""));
    }
}
