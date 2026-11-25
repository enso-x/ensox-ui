package com.ensox.ui.layout;

/**
 * How a widget is placed inside its parent.
 *
 * <ul>
 *     <li>{@link #flow()}: placed by the parent flow (row/column), the default;</li>
 *     <li>{@link #offset(int, int)}: placed by the flow, then shifted by pixels;</li>
 *     <li>{@link #absolute(int, int)}: pixel offset from the parent content box,
 *     outside the flow;</li>
 *     <li>{@link #anchor(int, int)}: percentage of the free space in the content
 *     box, outside the flow. {@code anchor(100, 50)} sticks the widget to the
 *     right edge, vertically centered;</li>
 *     <li>{@link #fraction(int, int)}: percentage of the content box size; the
 *     widget's top-left corner lands on that point, outside the flow.</li>
 * </ul>
 */
public record Position(Kind kind, int x, int y) {
    public enum Kind {
        FLOW,
        OFFSET,
        ABSOLUTE,
        ANCHOR,
        FRACTION
    }

    private static final Position FLOW = new Position(Kind.FLOW, 0, 0);

    public static Position flow() {
        return FLOW;
    }

    public static Position offset(int dx, int dy) {
        return new Position(Kind.OFFSET, dx, dy);
    }

    public static Position absolute(int x, int y) {
        return new Position(Kind.ABSOLUTE, x, y);
    }

    public static Position anchor(int percentX, int percentY) {
        return new Position(Kind.ANCHOR, percentX, percentY);
    }

    public static Position fraction(int percentX, int percentY) {
        return new Position(Kind.FRACTION, percentX, percentY);
    }

    /** Whether the widget takes space in the parent flow. */
    public boolean inFlow() {
        return kind == Kind.FLOW || kind == Kind.OFFSET;
    }
}
