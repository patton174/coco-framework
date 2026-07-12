package io.github.coco.feature.mybatisplus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

/**
 * Coco MyBatis-Plus 配置元数据测试。
 * <p>
 * 验证 MyBatis-Plus 功能模块提供 Spring Boot IDE 可识别的配置提示元数据。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-mybatis-plus}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoMybatisPlusConfigurationMetadataTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void exposesPaginationPropertyMetadata() throws IOException {
        JsonNode metadata = configurationMetadata();

        assertProperty(metadata, "coco.mybatis-plus.pagination.enabled", "java.lang.Boolean", "true");
        assertProperty(metadata, "coco.mybatis-plus.pagination.db-type", "java.lang.String", null);
        assertProperty(metadata, "coco.mybatis-plus.pagination.max-limit", "java.lang.Long", null);
        assertProperty(metadata, "coco.mybatis-plus.pagination.optimize-join", "java.lang.Boolean", "true");
        assertHint(metadata, "coco.mybatis-plus.pagination.db-type", "mysql");
        assertHint(metadata, "coco.mybatis-plus.pagination.db-type", "postgre-sql");
    }

    @Test
    void exposesSqlGuardPropertyMetadata() throws IOException {
        JsonNode metadata = configurationMetadata();

        assertProperty(metadata, "coco.mybatis-plus.sql-guard.block-attack-enabled", "java.lang.Boolean", "false");
        assertProperty(metadata, "coco.mybatis-plus.sql-guard.illegal-sql-enabled", "java.lang.Boolean", "false");
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
        if (defaultValue != null) {
            assertEquals(defaultValue, property.path("defaultValue").asText());
        }
        assertTrue(property.path("description").asText().length() > 0);
    }

    private static void assertHint(JsonNode metadata, String name, String expectedValue) {
        JsonNode hint = findNamedNode(metadata.path("hints"), name);
        assertNotNull(hint, "missing hint: " + name);
        for (JsonNode value : hint.path("values")) {
            if (expectedValue.equals(value.path("value").asText())) {
                return;
            }
        }
        throw new AssertionError("missing hint value: " + expectedValue);
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
