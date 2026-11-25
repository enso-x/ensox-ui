package com.ensox.ui.geom;

import com.ensox.base.Insets;

/**
 * Immutable integer rectangle in GUI coordinates. {@link #right()} and
 * {@link #bottom()} are exclusive, matching {@code GuiGraphicsExtractor.fill}.
 */
public record Rect(int x, int y, int width, int height) {
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    public static Rect of(int x, int y, int width, int height) {
        return new Rect(x, y, width, height);
    }

    public static Rect fromEdges(int left, int top, int right, int bottom) {
        return new Rect(left, top, right - left, bottom - top);
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public int centerX() {
        return x + width / 2;
    }

    public int centerY() {
        return y + height / 2;
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }

    public boolean contains(double px, double py) {
        return px >= x && py >= y && px < right() && py < bottom();
    }

    /** Shrinks by {@code insets}; negative insets grow the rectangle. */
    public Rect inset(Insets insets) {
        return new Rect(x + insets.left(), y + insets.top(),
            width - insets.horizontal(), height - insets.vertical());
    }

    public Rect outset(Insets insets) {
        return inset(insets.inverted());
    }

    public Rect offset(int dx, int dy) {
        return new Rect(x + dx, y + dy, width, height);
    }

    public Rect intersect(Rect other) {
        int left = Math.max(x, other.x);
        int top = Math.max(y, other.y);
        int right = Math.min(right(), other.right());
        int bottom = Math.min(bottom(), other.bottom());
        return right <= left || bottom <= top ? new Rect(left, top, 0, 0) : fromEdges(left, top, right, bottom);
    }

    public boolean intersects(Rect other) {
        return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
    }
}
