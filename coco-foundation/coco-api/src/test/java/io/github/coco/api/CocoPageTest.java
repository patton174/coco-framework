package io.github.coco.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CocoPageTest {

    @Test
    void totalPagesCalculation() {
        assertEquals(5, new CocoPage<>(List.of(), 50, 1, 10).totalPages());
        assertEquals(6, new CocoPage<>(List.of(), 51, 1, 10).totalPages());
        assertEquals(1, new CocoPage<>(List.of(), 1, 1, 10).totalPages());
        assertEquals(0, new CocoPage<>(List.of(), 0, 1, 10).totalPages());
        assertEquals(0, new CocoPage<>(List.of(), 10, 1, 0).totalPages());
    }

    @Test
    void hasNextDetectsRemainingPages() {
        assertTrue(new CocoPage<>(List.of(), 50, 1, 10).hasNext());
        assertTrue(new CocoPage<>(List.of(), 50, 4, 10).hasNext());
        assertFalse(new CocoPage<>(List.of(), 50, 5, 10).hasNext());
        assertFalse(new CocoPage<>(List.of(), 50, 6, 10).hasNext());
    }

    @Test
    void nullItemsDefaultsToEmptyList() {
        CocoPage<String> page = new CocoPage<>(null, 0, 1, 10);
        assertEquals(List.of(), page.items());
    }

    @Test
    void itemsAreDefensivelyCopied() {
        List<String> original = new java.util.ArrayList<>(List.of("a", "b"));
        CocoPage<String> page = new CocoPage<>(original, 2, 1, 10);
        original.add("c");
        assertEquals(2, page.items().size());
    }
}
