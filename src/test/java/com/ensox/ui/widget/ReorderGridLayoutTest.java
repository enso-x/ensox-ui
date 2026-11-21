package com.ensox.ui.widget;

import com.ensox.ui.geom.Rect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReorderGridLayoutTest {
    @Test void columnsAdaptWithoutMakingCardsNarrowerThanTheMinimum() {
        var narrow = ReorderGridLayout.create(260, 300, 176, 8, 20);
        assertEquals(1, narrow.columns());
        assertEquals(260, narrow.cellWidth());
        var wide = ReorderGridLayout.create(940, 300, 176, 8, 20);
        assertEquals(3, wide.columns());
        assertEquals(Rect.of(0, 184, 308, 176), wide.slot(3));
        assertEquals(1280, wide.contentHeight());
    }

    @Test void destinationSlotsStayStableInGapsAndIncompleteRows() {
        var layout = ReorderGridLayout.create(940, 300, 176, 8, 5);
        assertEquals(0, layout.nearestIndex(0, 0));
        assertEquals(1, layout.nearestIndex(313, 20));
        assertEquals(3, layout.nearestIndex(10, 200));
        assertEquals(4, layout.nearestIndex(900, 200));
        assertEquals(4, layout.nearestIndex(900, 100_000));
        assertEquals(0, layout.nearestIndex(-50, -100));
    }

    @Test void visibleRangeIsBoundedByViewportAndOneOverscanRow() {
        var layout = ReorderGridLayout.create(940, 300, 176, 8, 10_000);
        assertEquals(new ReorderGridLayout.Range(0, 9), layout.visible(0, 200));
        assertEquals(new ReorderGridLayout.Range(27, 39), layout.visible(1840, 200));
        assertEquals(new ReorderGridLayout.Range(10_000, 10_000), layout.visible(1e9, 200));
    }

    @Test void emptyGridHasNoDropPositionOrContent() {
        var layout = ReorderGridLayout.create(940, 300, 176, 8, 0);
        assertEquals(0, layout.contentHeight());
        assertEquals(-1, layout.nearestIndex(20, 40));
        assertThrows(IllegalArgumentException.class, () -> ReorderGridLayout.create(200, 0, 100, 8, 5));
    }
}
