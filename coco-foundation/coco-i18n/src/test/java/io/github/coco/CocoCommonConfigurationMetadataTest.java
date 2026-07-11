package io.github.coco;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Coco 通用配置元数据测试。
 * <p>
 * 验证框架产物中包含 Spring Boot IDE 可识别的通用基础设施配置提示。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-i18n}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoCommonConfigurationMetadataTest {

    @Test
    void exposesI18nPropertyMetadata() throws IOException {
        InputStream metadata = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json");

        assertNotNull(metadata);
        String content = new String(metadata.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"name\": \"coco.common.i18n.basename\""));
        assertTrue(content.contains("\"name\": \"coco.common.i18n.default-locale\""));
        assertTrue(content.contains("\"name\": \"coco.common.i18n.fallback-to-system-locale\""));
        assertTrue(content.contains("\"name\": \"coco.common.i18n.use-code-as-default-message\""));
    }
}
