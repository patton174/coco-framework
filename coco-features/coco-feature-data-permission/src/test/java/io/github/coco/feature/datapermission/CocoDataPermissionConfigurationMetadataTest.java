package io.github.coco.feature.datapermission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Coco 数据权限配置元数据测试。
 * <p>
 * 验证数据权限功能模块提供 Spring Boot IDE 可识别的配置提示元数据。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-data-permission}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoDataPermissionConfigurationMetadataTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void exposesSqlPropertyMetadata() throws IOException {
        JsonNode metadata = configurationMetadata();

        assertProperty(metadata, "coco.data-permission.sql.enabled", "java.lang.Boolean");
        assertProperty(metadata, "coco.data-permission.sql.missing-context-policy",
                "io.github.coco.feature.datapermission.sql.CocoDataPermissionMissingContextPolicy");
        assertProperty(metadata, "coco.data-permission.sql.missing-rule-policy",
                "io.github.coco.feature.datapermission.sql.CocoDataPermissionMissingRulePolicy");
        assertProperty(metadata, "coco.data-permission.sql.resources",
                "java.util.Map<java.lang.String,io.github.coco.feature.datapermission.sql.CocoDataPermissionSqlResourceProperties>");
        assertHintValues(metadata, "coco.data-permission.sql.missing-context-policy", "throw", "deny", "ignore");
        assertHintValues(metadata, "coco.data-permission.sql.missing-rule-policy", "deny", "ignore");
    }

    private JsonNode configurationMetadata() throws IOException {
        InputStream metadata = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json");
        assertNotNull(metadata);
        return OBJECT_MAPPER.readTree(metadata);
    }

    private static void assertProperty(JsonNode metadata, String name, String type) {
        JsonNode property = findNamedNode(metadata.path("properties"), name);
        assertNotNull(property, "missing property: " + name);
        assertEquals(type, property.path("type").asText());
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
