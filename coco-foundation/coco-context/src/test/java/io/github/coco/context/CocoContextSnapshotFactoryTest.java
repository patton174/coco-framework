package io.github.coco.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CocoContextSnapshotFactoryTest {

    @Test
    void capturesInStableOrderAndComposesSnapshots() {
        List<String> calls = new ArrayList<>();
        CocoContextSnapshotFactory factory = new CocoContextSnapshotFactory(List.of(
                contributor("late", 20, calls), contributor("early", 10, calls)));

        try (CocoContextScope ignored = factory.capture().restore()) {
            assertEquals(List.of("capture:early", "capture:late", "restore:early", "restore:late"), calls);
        }
        assertEquals(List.of("capture:early", "capture:late", "restore:early", "restore:late", "close:late", "close:early"), calls);
    }

    @Test
    void rejectsDuplicateIdsDuringConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new CocoContextSnapshotFactory(List.of(
                contributor("same", 0, new ArrayList<>()), contributor("same", 1, new ArrayList<>()))));
    }

    @Test
    void captureFailurePreventsLaterContributorCapture() {
        List<String> calls = new ArrayList<>();
        CocoContextSnapshotContributor failing = new CocoContextSnapshotContributor() {
            @Override public String id() { return "failing"; }
            @Override public CocoContextSnapshot capture() { throw new IllegalStateException("failure"); }
        };
        assertThrows(IllegalStateException.class,
                () -> new CocoContextSnapshotFactory(List.of(failing, contributor("later", 1, calls))).capture());
        assertEquals(List.of(), calls);
    }

    private static CocoContextSnapshotContributor contributor(String id, int order, List<String> calls) {
        return new CocoContextSnapshotContributor() {
            @Override public String id() { return id; }
            @Override public int order() { return order; }
            @Override public CocoContextSnapshot capture() {
                calls.add("capture:" + id);
                return () -> {
                    calls.add("restore:" + id);
                    return () -> calls.add("close:" + id);
                };
            }
        };
    }
}
