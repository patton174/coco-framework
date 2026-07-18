package io.github.coco.feature.web.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.coco.feature.web.context.payload.CocoWebPayloadParseResult;
import io.github.coco.feature.web.context.payload.CocoWebPayloadParseStatus;
import org.junit.jupiter.api.Test;

/**
 * Coco Web 参数快照不可变性测试。
 *
 * @author patton174
 * @since 1.0.0
 */
class CocoWebParameterSnapshotImmutabilityTest {

    @Test
    void requestParametersAccessorReturnsDeepImmutableSnapshot() {
        Map<String, List<String>> source = mutableParameters();

        CocoWebRequestParameters snapshot = new CocoWebRequestParameters("first=one", source, source, source);
        mutate(source);

        assertSnapshot(snapshot.parameters());
        assertSnapshot(snapshot.queryParameters());
        assertSnapshot(snapshot.payloadParameters());
    }

    @Test
    void payloadParseResultAccessorReturnsDeepImmutableSnapshot() {
        Map<String, List<String>> source = mutableParameters();

        CocoWebPayloadParseResult snapshot = new CocoWebPayloadParseResult(source, CocoWebPayloadParseStatus.PARSED,
                CocoWebParameterSource.JSON);
        mutate(source);

        assertSnapshot(snapshot.parameters());
    }

    private static Map<String, List<String>> mutableParameters() {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        List<String> firstValues = new ArrayList<>();
        firstValues.add("one");
        firstValues.add(null);
        firstValues.add("one");
        parameters.put(" first ", firstValues);
        parameters.put("second", new ArrayList<>(List.of("two")));
        return parameters;
    }

    private static void mutate(Map<String, List<String>> source) {
        source.get(" first ").add("changed");
        source.put("third", new ArrayList<>(List.of("three")));
    }

    private static void assertSnapshot(Map<String, List<String>> snapshot) {
        assertEquals(List.of("first", "second"), new ArrayList<>(snapshot.keySet()));
        assertEquals(List.of("one", "", "one"), snapshot.get("first"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("third", List.of("three")));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.get("first").add("changed"));
    }
}
