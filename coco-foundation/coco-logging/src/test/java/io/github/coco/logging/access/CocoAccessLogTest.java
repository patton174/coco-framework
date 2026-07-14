package io.github.coco.logging.access;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class CocoAccessLogTest {

    @Test
    void exposesImmutableHeaderAndParameterSnapshots() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Request-Id", "request-1");
        List<String> values = new ArrayList<>(List.of("first"));
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        parameters.put("page", values);
        CocoAccessLog accessLog = CocoAccessLog.of("trace-1", "GET", "/orders", 200, 1, true, null,
                null, null, null, null, null, headers, null, null, null, null, null, null, parameters);
        headers.put("X-Request-Id", "mutated");
        values.add("mutated");

        Map<String, String> headerSnapshot = accessLog.headers();
        Map<String, List<String>> parameterSnapshot = accessLog.requestParameters();

        assertEquals(Map.of("x-request-id", "request-1"), headerSnapshot);
        assertEquals(Map.of("page", List.of("first")), parameterSnapshot);
        assertThrows(UnsupportedOperationException.class, () -> headerSnapshot.put("other", "value"));
        assertThrows(UnsupportedOperationException.class, () -> parameterSnapshot.put("other", List.of("value")));
        assertThrows(UnsupportedOperationException.class, () -> parameterSnapshot.get("page").add("other"));
    }
}
