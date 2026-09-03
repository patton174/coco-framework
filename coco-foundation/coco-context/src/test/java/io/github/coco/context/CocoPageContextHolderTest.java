package io.github.coco.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CocoPageContextHolderTest {

    @AfterEach
    void tearDown() {
        CocoPageContextHolder.clear();
    }

    @Test
    void setAndCurrent() {
        CocoPageContext context = new CocoPageContext(2, 15);
        CocoPageContextHolder.set(context);
        assertTrue(CocoPageContextHolder.current().isPresent());
        assertEquals(2, CocoPageContextHolder.current().get().page());
        assertEquals(15, CocoPageContextHolder.current().get().size());
    }

    @Test
    void clearRemovesContext() {
        CocoPageContextHolder.set(new CocoPageContext(1, 10));
        CocoPageContextHolder.clear();
        assertFalse(CocoPageContextHolder.current().isPresent());
    }

    @Test
    void setNullClears() {
        CocoPageContextHolder.set(new CocoPageContext(1, 10));
        CocoPageContextHolder.set(null);
        assertFalse(CocoPageContextHolder.current().isPresent());
    }

    @Test
    void captureRestoresPreviousContext() {
        CocoPageContextHolder.set(new CocoPageContext(1, 10));
        CocoContextSnapshot snapshot = CocoPageContextHolder.capture();
        CocoPageContextHolder.set(new CocoPageContext(5, 50));

        try (CocoContextScope scope = snapshot.restore()) {
            assertEquals(1, CocoPageContextHolder.current().get().page());
        }
        assertEquals(5, CocoPageContextHolder.current().get().page());
    }
}
