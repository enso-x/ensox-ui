package com.ensox.ui.widget;

import com.ensox.ui.geom.Rect;

/** Destination geometry, independent of animated widget positions and persistence. */
record ReorderGridLayout(int columns, int cellWidth, int cellHeight, int gap, int count) {
    static ReorderGridLayout create(int width, int minimumWidth, int cellHeight, int gap, int count) {
        if (minimumWidth < 1 || cellHeight < 1 || gap < 0 || count < 0) throw new IllegalArgumentException("Invalid grid dimensions");
        int columns = (int) Math.max(1, ((long) Math.max(0, width) + gap) / ((long) minimumWidth + gap));
        int cellWidth = Math.max(1, (width - (columns - 1) * gap) / columns);
        return new ReorderGridLayout(columns, cellWidth, cellHeight, gap, count);
    }

    Rect slot(int index) {
        return Rect.of(index % columns * (cellWidth + gap), index / columns * (cellHeight + gap), cellWidth, cellHeight);
    }

    int contentHeight() {
        long rows = ((long) count + columns - 1) / columns;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, rows * (cellHeight + gap) - gap));
    }

    int nearestIndex(double x, double y) {
        if (count == 0) return -1;
        int column = Math.clamp((int) Math.floor((x + gap / 2.0) / (cellWidth + gap)), 0, columns - 1);
        long row = Math.max(0L, (long) Math.floor((y + gap / 2.0) / (cellHeight + gap)));
        return (int) Math.min(count - 1L, row * columns + column);
    }

    Range visible(double scrollY, int viewportHeight) {
        long firstRow = Math.max(0L, (long) Math.floor(scrollY / (cellHeight + gap)) - 1);
        long lastRow = Math.max(firstRow, (long) Math.floor((scrollY + Math.max(0, viewportHeight)) / (cellHeight + gap)) + 1);
        return new Range((int) Math.min(count, firstRow * columns), (int) Math.min(count, (lastRow + 1) * columns));
    }

    record Range(int start, int end) { }
}
