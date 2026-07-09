package io.github.coco.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * Coco 测试支持工具测试。
 * <p>
 * 固定共享测试工具的资源读取和命名节点查找语义，避免使用方模块复制同类逻辑。
 * </p>
 * <p>
 * 项目信息：
 * </p>
 * <ul>
 *   <li>作者：<a href="https://github.com/patton174">patton174</a></li>
 *   <li>仓库：<a href="https://github.com/patton174/coco-framework">https://github.com/patton174/coco-framework</a></li>
 *   <li>模块：{@code coco-test}</li>
 * </ul>
 * @author patton174
 * @since 1.0.0
 */
class CocoTestSupportTest {

    @Test
    void readsJsonResourceAndFindsNamedNode() throws IOException {
        JsonNode metadata = CocoTestSupport.jsonResource(getClass(), "/test-support-metadata.json");

        JsonNode property = CocoTestSupport.requiredNamedNode(metadata.path("properties"), "coco.test.enabled");

        assertEquals("java.lang.Boolean", property.path("type").asText());
        assertEquals("true", property.path("defaultValue").asText());
        assertNotNull(CocoTestSupport.findNamedNode(metadata.path("properties"), "coco.test.enabled"));
        assertNull(CocoTestSupport.findNamedNode(metadata.path("properties"), "coco.test.missing"));
        assertNull(CocoTestSupport.findNamedNode(metadata.path("missing"), "coco.test.enabled"));
    }

    @Test
    void rejectsMissingResourceAndMissingNamedNode() {
        assertThrows(IllegalArgumentException.class,
                () -> CocoTestSupport.jsonResource(getClass(), "/missing-resource.json"));
        assertThrows(IllegalArgumentException.class,
                () -> CocoTestSupport.requiredNamedNode(null, "coco.test.enabled"));
        assertThrows(IllegalArgumentException.class,
                () -> CocoTestSupport.jsonResource(getClass(), " "));
    }
}
