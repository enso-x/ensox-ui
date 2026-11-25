package com.ensox.ui.event;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Mouse pointer event in GUI coordinates.
 *
 * <ul>
 *     <li>{@link Type#PRESS} goes to the widget under the pointer. The widget
 *     that consumes it captures the pointer: it receives the following
 *     {@link Type#DRAG} and {@link Type#RELEASE} events even outside its bounds.</li>
 *     <li>{@link Type#MOVE} is delivered to the widget under the pointer while
 *     no button is held.</li>
 *     <li>{@link Type#CANCEL} tells the capturing widget that an ancestor took
 *     over the gesture (for example a scroll view started drag-scrolling): reset
 *     pressed state, do not fire a click. It is not bubbled.</li>
 * </ul>
 */
public final class PointerEvent extends UiEvent {
    public enum Type {
        PRESS,
        RELEASE,
        DRAG,
        MOVE,
        CANCEL
    }

    private final Type type;
    private final double x;
    private final double y;
    private final int button;
    private final Modifiers modifiers;
    private final boolean doubleClick;
    private final double deltaX;
    private final double deltaY;

    public PointerEvent(Type type, double x, double y, int button, Modifiers modifiers, boolean doubleClick,
                        double deltaX, double deltaY) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.button = button;
        this.modifiers = modifiers;
        this.doubleClick = doubleClick;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
    }

    public Type type() {
        return type;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    /** InputConstants mouse button; {@code -1} for {@link Type#MOVE}. */
    public int button() {
        return button;
    }

    public boolean isPrimary() {
        return button == InputConstants.MOUSE_BUTTON_LEFT;
    }

    public boolean isSecondary() {
        return button == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    public boolean isMiddle() {
        return button == InputConstants.MOUSE_BUTTON_MIDDLE;
    }

    public Modifiers modifiers() {
        return modifiers;
    }

    public boolean isDoubleClick() {
        return doubleClick;
    }

    /** Pointer movement since the previous drag event. */
    public double deltaX() {
        return deltaX;
    }

    public double deltaY() {
        return deltaY;
    }
}
