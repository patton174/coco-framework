package io.github.coco.feature.audit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

/**
 * Coco 审计配置元数据测试。
 * <p>
 * 验证审计功能模块提供 Spring Boot IDE 可识别的配置提示元数据。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-audit}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoAuditConfigurationMetadataTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void exposesAuditPropertyMetadata() throws IOException {
        JsonNode metadata = configurationMetadata();

        assertProperty(metadata, "coco.audit.enabled", "java.lang.Boolean", true);
        assertProperty(metadata, "coco.audit.failure-policy",
                "io.github.coco.feature.audit.core.CocoAuditFailurePolicy", "ignore");
        assertProperty(metadata, "coco.audit.logging.enabled", "java.lang.Boolean", true);
        assertProperty(metadata, "coco.audit.logging.logger-name", "java.lang.String", "io.github.coco.audit");
        assertProperty(metadata, "coco.audit.logging.level",
                "io.github.coco.logging.core.CocoLogLevel", "info");
        assertProperty(metadata, "coco.audit.access-log.enabled", "java.lang.Boolean", true);
        assertHintValues(metadata, "coco.audit.failure-policy", "ignore", "throw");
        assertHintValues(metadata, "coco.audit.logging.level", "off", "error", "warn", "info", "debug", "trace");
    }

    private JsonNode configurationMetadata() throws IOException {
        InputStream metadata = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json");
        assertNotNull(metadata);
        return OBJECT_MAPPER.readTree(metadata);
    }

    private static void assertProperty(JsonNode metadata, String name, String type, Object defaultValue) {
        JsonNode property = findNamedNode(metadata.path("properties"), name);
        assertNotNull(property, "missing property: " + name);
        assertEquals(type, property.path("type").asText());
        assertEquals(String.valueOf(defaultValue), property.path("defaultValue").asText());
        assertTrue(property.path("description").asText().contains("Coco"));
    }

    private static void assertHintValues(JsonNode metadata, String name, String... expectedValues) {
        JsonNode hint = findNamedNode(metadata.path("hints"), name);
        assertNotNull(hint, "missing hint: " + name);
        List<String> values = StreamSupport.stream(hint.path("values").spliterator(), false)
                .map(value -> value.path("value").asText())
                .toList();
        assertEquals(List.of(expectedValues), values);
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
