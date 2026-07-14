package io.github.coco.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import io.github.coco.context.trace.CocoTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CocoContextSnapshotTest {

    @AfterEach
    void clearContext() {
        CocoRequestContextHolder.clear();
        CocoTraceContext.clear();
    }

    @Test
    void closeContinuesInReverseOrderWhenMultipleScopesThrow() {
        List<String> events = new ArrayList<>();
        IllegalStateException thirdFailure = new IllegalStateException("third close failed");
        IllegalArgumentException secondFailure = new IllegalArgumentException("second close failed");

        CocoContextScope scope = CocoContextSnapshot.compose(
                snapshot(events, "first", null),
                snapshot(events, "second", secondFailure),
                snapshot(events, "third", thirdFailure)).restore();

        IllegalStateException exception = assertThrows(IllegalStateException.class, scope::close);

        assertSame(thirdFailure, exception);
        assertEquals(List.of(secondFailure), List.of(exception.getSuppressed()));
        assertEquals(List.of("first-open", "second-open", "third-open", "third-close", "second-close", "first-close"),
                events);
    }

    @Test
    void nestedComposedSnapshotsPreserveEveryCloseFailure() {
        List<String> events = new ArrayList<>();
        IllegalStateException outerFailure = new IllegalStateException("outer close failed");
        IllegalArgumentException innerSecondFailure = new IllegalArgumentException("inner second close failed");
        UnsupportedOperationException innerFirstFailure = new UnsupportedOperationException("inner first close failed");

        CocoContextSnapshot inner = CocoContextSnapshot.compose(
                snapshot(events, "inner-first", innerFirstFailure),
                snapshot(events, "inner-second", innerSecondFailure));
        CocoContextScope scope = CocoContextSnapshot.compose(inner, snapshot(events, "outer", outerFailure)).restore();

        IllegalStateException exception = assertThrows(IllegalStateException.class, scope::close);

        assertSame(outerFailure, exception);
        assertEquals(List.of(innerSecondFailure), List.of(exception.getSuppressed()));
        assertEquals(List.of(innerFirstFailure), List.of(innerSecondFailure.getSuppressed()));
        assertEquals(List.of("inner-first-open", "inner-second-open", "outer-open", "outer-close", "inner-second-close",
                "inner-first-close"), events);
    }

    @Test
    void restoreFailureClosesOpenedScopesAndRestoresRequestAndTraceContexts() {
        CocoRequestContext captured = CocoRequestContext.of("captured-request", "GET", "/captured");
        CocoRequestContextHolder.set(captured);
        CocoContextSnapshot requestSnapshot = CocoRequestContextHolder.capture();
        CocoTraceContext.setTraceId("captured-trace");
        CocoContextSnapshot traceSnapshot = CocoTraceContext.capture();

        CocoRequestContext outer = CocoRequestContext.of("outer-request", "GET", "/outer");
        CocoRequestContextHolder.set(outer);
        IllegalStateException restoreFailure = new IllegalStateException("restore failed");
        IllegalArgumentException closeFailure = new IllegalArgumentException("close failed");
        CocoContextSnapshot failingSnapshot = () -> {
            throw restoreFailure;
        };
        CocoContextSnapshot throwingScopeSnapshot = () -> () -> {
            throw closeFailure;
        };

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> CocoContextSnapshot.compose(requestSnapshot, traceSnapshot, throwingScopeSnapshot, failingSnapshot)
                        .restore());

        assertSame(restoreFailure, exception);
        assertEquals(List.of(closeFailure), List.of(exception.getSuppressed()));
        assertEquals(outer, CocoRequestContextHolder.current().orElseThrow());
        assertEquals("outer-request", CocoTraceContext.currentTraceId().orElseThrow());
    }

    @Test
    void decodeListRejectsMalformedLengthsAndCounts() {
        List<String> malformedValues = List.of(
                "coco:list:-1|",
                "coco:list:1|-1:a",
                "coco:list:1|2147483648:a",
                "coco:list:1|2147483647:a",
                "coco:list:1|3:ab",
                "coco:list:10001|",
                "coco:list:2|0:",
                "coco:list:0|trailing");

        for (String malformedValue : malformedValues) {
            assertThrows(IllegalArgumentException.class, () -> CocoRequestContextValueCodec.decodeList(malformedValue));
        }
    }

    @Test
    void decodeListFuzzesInvalidNumericBoundariesWithoutLeakingImplementationExceptions() {
        for (int value = 0; value < 1_000; value++) {
            String oversizedCount = "coco:list:" + (10_001 + value) + "|";
            String negativeLength = "coco:list:1|-" + value + ":a";
            String truncatedLength = "coco:list:1|" + (Integer.MAX_VALUE - value) + ":a";

            assertThrows(IllegalArgumentException.class,
                    () -> CocoRequestContextValueCodec.decodeList(oversizedCount));
            assertThrows(IllegalArgumentException.class,
                    () -> CocoRequestContextValueCodec.decodeList(negativeLength));
            assertThrows(IllegalArgumentException.class,
                    () -> CocoRequestContextValueCodec.decodeList(truncatedLength));
        }
        assertTrue(CocoRequestContextValueCodec.decodeList("coco:list:2|0:|1:a").contains("a"));
    }

    private static CocoContextSnapshot snapshot(List<String> events, String name, RuntimeException closeFailure) {
        return () -> {
            events.add(name + "-open");
            return () -> {
                events.add(name + "-close");
                if (closeFailure != null) {
                    throw closeFailure;
                }
            };
        };
    }
}
