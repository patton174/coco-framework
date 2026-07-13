package io.github.coco.feature.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

import io.github.coco.test.CocoTestSupport;
import org.junit.jupiter.api.Test;

/**
 * Coco 安全配置元数据测试。
 * <p>
 * 验证安全功能模块提供 Spring Boot IDE 可识别的配置提示元数据。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-security}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoSecurityConfigurationMetadataTest {

    @Test
    void exposesSecurityWebPropertyMetadata() throws IOException {
        JsonNode metadata = CocoTestSupport.configurationMetadata(getClass());

        assertProperty(metadata, "coco.security.web.enabled", "java.lang.Boolean", "true");
        assertProperty(metadata, "coco.security.web.header.enabled", "java.lang.Boolean", "false");
        assertProperty(metadata, "coco.security.web.header.principal-id-header-name",
                "java.lang.String", "X-Coco-Principal-Id");
        assertProperty(metadata, "coco.security.web.header.principal-name-header-name",
                "java.lang.String", "X-Coco-Principal-Name");
        assertProperty(metadata, "coco.security.web.header.roles-header-name",
                "java.lang.String", "X-Coco-Roles");
        assertProperty(metadata, "coco.security.web.header.permissions-header-name",
                "java.lang.String", "X-Coco-Permissions");
        assertProperty(metadata, "coco.security.web.header.authority-delimiter",
                "java.lang.String", ",");
    }

    private static void assertProperty(JsonNode metadata, String name, String type, String defaultValue) {
        JsonNode property = CocoTestSupport.requiredNamedNode(metadata.path("properties"), name);
        assertEquals(type, property.path("type").asText());
        assertEquals(defaultValue, property.path("defaultValue").asText());
        assertFalse(property.path("description").asText().isBlank());
    }
}
