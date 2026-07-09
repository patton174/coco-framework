package io.github.coco.feature.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Coco 租户配置元数据测试。
 * <p>
 * 验证租户功能模块提供 Spring Boot IDE 可识别的配置提示元数据。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-feature-tenant}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoTenantConfigurationMetadataTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void exposesTenantSqlPropertyMetadata() throws IOException {
        JsonNode metadata = configurationMetadata();

        assertProperty(metadata, "coco.tenant.sql.enabled", "java.lang.Boolean", "true");
        assertProperty(metadata, "coco.tenant.sql.tenant-id-column", "java.lang.String", "tenant_id");
        assertProperty(metadata, "coco.tenant.sql.ignore-tables", "java.util.Set<java.lang.String>", null);
        assertProperty(metadata, "coco.tenant.sql.fail-on-missing-context", "java.lang.Boolean", "true");
        assertProperty(metadata, "coco.tenant.sql.interceptor-ignore.block-unlisted",
                "java.lang.Boolean", "true");
        assertProperty(metadata, "coco.tenant.sql.interceptor-ignore.allowed-mapped-statements",
                "java.util.Set<java.lang.String>", null);
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

    private static JsonNode findNamedNode(JsonNode nodes, String name) {
        for (JsonNode node : nodes) {
            if (name.equals(node.path("name").asText())) {
                return node;
            }
        }
        return null;
    }
}
