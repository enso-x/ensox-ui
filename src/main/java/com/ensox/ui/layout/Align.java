package com.ensox.ui.layout;

/** Alignment of children inside a container's content box, per screen axis. */
public enum Align {
    START,
    CENTER,
    END;

    /**
     * Offset of an item of {@code size} inside {@code space}. Centering uses
     * floor division, so the odd pixel always goes to the end side and items
     * larger than the space overflow symmetrically.
     */
    public int offset(int size, int space) {
        return switch (this) {
            case START -> 0;
            case CENTER -> Math.floorDiv(space - size, 2);
            case END -> space - size;
        };
    }
}
