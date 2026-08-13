package io.github.coco.feature.ratelimit.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CocoRateLimitRedisConfigurationMetadataTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void exposesRedisKeyPrefixMetadata() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/META-INF/spring-configuration-metadata.json")) {
            assertNotNull(input);
            JsonNode metadata = OBJECT_MAPPER.readTree(input);
            JsonNode property = findProperty(metadata, "coco.rate-limit.redis.key-prefix");

            assertNotNull(property);
            assertEquals("java.lang.String", property.path("type").asText());
            assertEquals(CocoRateLimitRedisProperties.DEFAULT_KEY_PREFIX,
                    property.path("defaultValue").asText());
            assertTrue(property.path("description").asText().length() > 0);
        }
    }

    private static JsonNode findProperty(JsonNode metadata, String name) {
        for (JsonNode property : metadata.path("properties")) {
            if (name.equals(property.path("name").asText())) {
                return property;
            }
        }
        return null;
    }
}
