package io.github.coco.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CocoRequestContextTest {

    @Test
    void exposesImmutableAttributeSnapshots() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("custom", "initial");
        CocoRequestContext context = CocoRequestContext.of("trace-1", "GET", "/orders", attributes);
        attributes.put("custom", "mutated");

        Map<String, String> snapshot = context.attributes();

        assertEquals(Map.of("custom", "initial"), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("other", "value"));
        assertEquals(Map.of("custom", "initial"), context.attributes());
    }

    @Test
    void preservesPublishedRequestContextApi() throws ReflectiveOperationException {
        assertEquals(CocoRequestContext.class, Class.forName("io.github.coco.context.CocoRequestContext"));
        assertNotNull(CocoRequestContext.class.getMethod("of", String.class, String.class, String.class, Map.class));
        assertEquals(Map.class, CocoRequestContext.class.getMethod("attributes").getReturnType());
    }
}
