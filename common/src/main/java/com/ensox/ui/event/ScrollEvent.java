package com.ensox.ui.event;

/** Mouse wheel / touchpad scroll over the widget under the pointer. Positive {@code amountY} scrolls up. */
public final class ScrollEvent extends UiEvent {
    private final double x;
    private final double y;
    private final double amountX;
    private final double amountY;
    private final Modifiers modifiers;

    public ScrollEvent(double x, double y, double amountX, double amountY, Modifiers modifiers) {
        this.x = x;
        this.y = y;
        this.amountX = amountX;
        this.amountY = amountY;
        this.modifiers = modifiers;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double amountX() {
        return amountX;
    }

    public double amountY() {
        return amountY;
    }

    public Modifiers modifiers() {
        return modifiers;
    }
}
