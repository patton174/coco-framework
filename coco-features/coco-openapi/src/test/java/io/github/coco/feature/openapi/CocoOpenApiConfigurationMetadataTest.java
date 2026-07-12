package io.github.coco.feature.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

/**
 * Coco OpenAPI 配置元数据测试。
 * <p>
 * 验证 OpenAPI 功能模块提供 Spring Boot IDE 可识别的配置提示元数据。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-openapi}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoOpenApiConfigurationMetadataTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void exposesOpenApiPropertyMetadata() throws IOException {
        JsonNode metadata = configurationMetadata();

        assertProperty(metadata, "coco.openapi.enabled", "java.lang.Boolean", "true");
        assertProperty(metadata, "coco.openapi.info.title", "java.lang.String", "Coco API");
        assertProperty(metadata, "coco.openapi.info.version", "java.lang.String", "1.0.0");
        assertProperty(metadata, "coco.openapi.info.description", "java.lang.String", "Coco Framework API");
        assertProperty(metadata, "coco.openapi.springdoc.enabled", "java.lang.Boolean", "true");
    }

    private JsonNode configurationMetadata() throws IOException {
        InputStream metadata = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json");
        assertNotNull(metadata);
        return OBJECT_MAPPER.readTree(metadata);
    }

    private static void assertProperty(JsonNode metadata, String name, String type, String defaultValue) {
        JsonNode property = findNamedNode(metadata.path("properties"), name);
        assertNotNull(property, "missing property: " + name);
        assertEquals(type, property.path("type").asText());
        assertEquals(defaultValue, property.path("defaultValue").asText());
        assertTrue(property.path("description").asText().contains("OpenAPI"));
    }

    private static JsonNode findNamedNode(JsonNode nodes, String name) {
        for (JsonNode node : nodes) {
            if (name.equals(node.path("name").asText())) {
                return node;
            }
        }
        return null;
    }
}
