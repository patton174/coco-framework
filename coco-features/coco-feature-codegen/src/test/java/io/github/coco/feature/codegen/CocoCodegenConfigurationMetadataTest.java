package io.github.coco.feature.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

/**
 * Coco 代码生成配置元数据测试。
 * <p>
 * 验证代码生成功能模块提供 Spring Boot IDE 可识别的配置提示元数据。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-codegen}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoCodegenConfigurationMetadataTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void exposesCodegenPropertyMetadata() throws IOException {
        JsonNode metadata = configurationMetadata();

        assertProperty(metadata, "coco.codegen.enabled", "java.lang.Boolean", "true");
        assertProperty(metadata, "coco.codegen.templates.location", "java.lang.String",
                "classpath:/coco/codegen/templates");
        assertProperty(metadata, "coco.codegen.templates.encoding", "java.lang.String", "UTF-8");
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
        assertTrue(property.path("description").asText().contains("Coco"));
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
