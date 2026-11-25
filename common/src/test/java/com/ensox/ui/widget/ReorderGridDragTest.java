package com.ensox.ui.widget;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReorderGridDragTest {
    private static final List<String> IDS = List.of("a", "b", "c", "d", "e");

    @Test void shortMovementRemainsASelectionClick() {
        var drag = new ReorderGridDrag(IDS, "b", 10, 20);
        drag.update(13, 22, 4);
        assertFalse(drag.active());
        assertEquals(1, drag.destination());
        assertTrue(drag.drop().isEmpty());
        drag.update(14, 20, 4);
        assertTrue(drag.active());
    }

    @Test void movingLaterReservesSlotAndUsesVisibleAnchorAfterIt() {
        var drag = new ReorderGridDrag(IDS, "b", 0, 0);
        drag.update(30, 0, 3);
        assertEquals(List.of(0, 3, 1, 2, 4), java.util.stream.IntStream.range(0, 5).map(drag::visualIndex).boxed().toList());
        assertEquals(new ReorderGridDrag.Move("b", "d", true), drag.drop().orElseThrow());
        for (int i = 0; i < 5; i++) assertEquals(i, drag.sourceIndex(drag.visualIndex(i)));
        assertEquals(List.of("a", "b", "c", "d", "e"), IDS, "Dragging does not mutate source order");
    }

    @Test void movingEarlierUsesBeforeAnchorAndReturningIsNoOp() {
        var drag = new ReorderGridDrag(IDS, "e", 0, 0);
        drag.update(30, 0, 1);
        assertEquals(new ReorderGridDrag.Move("e", "b", false), drag.drop().orElseThrow());
        for (int i = 0; i < 5; i++) assertEquals(i, drag.sourceIndex(drag.visualIndex(i)));
        drag.update(40, 0, 4);
        assertTrue(drag.drop().isEmpty());
    }
}
