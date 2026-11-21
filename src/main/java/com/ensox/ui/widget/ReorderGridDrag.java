package com.ensox.ui.widget;

import java.util.List;
import java.util.Optional;

/** One gesture's provisional permutation. No metadata writes occur until {@link #drop()}. */
final class ReorderGridDrag {
    private final List<String> ids;
    private final int source;
    private final double pressX, pressY;
    private int destination;
    private boolean active;

    ReorderGridDrag(List<String> ids, String moved, double pressX, double pressY) {
        this.ids = List.copyOf(ids);
        this.source = ids.indexOf(moved);
        if (source < 0) throw new IllegalArgumentException("Unknown dragged item");
        this.destination = source;
        this.pressX = pressX;
        this.pressY = pressY;
    }

    String moved() { return ids.get(source); }
    int destination() { return destination; }
    boolean active() { return active; }

    void update(double x, double y, int slot) {
        if (!active && (x - pressX) * (x - pressX) + (y - pressY) * (y - pressY) < 16) return;
        active = true;
        if (slot >= 0) destination = Math.clamp(slot, 0, ids.size() - 1);
    }

    int visualIndex(int index) {
        if (!active) return index;
        if (index == source) return destination;
        if (source < destination && index > source && index <= destination) return index - 1;
        if (source > destination && index >= destination && index < source) return index + 1;
        return index;
    }

    int sourceIndex(int slot) {
        if (!active) return slot;
        if (slot == destination) return source;
        if (source < destination && slot >= source && slot < destination) return slot + 1;
        if (source > destination && slot > destination && slot <= source) return slot - 1;
        return slot;
    }

    Optional<Move> drop() {
        return !active || source == destination ? Optional.empty()
            : Optional.of(new Move(moved(), ids.get(destination), destination > source));
    }

    record Move(String moved, String anchor, boolean after) { }
}
