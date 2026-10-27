package com.ensox.ui.event;

/**
 * Key press or release, delivered to the focused widget and bubbled to its
 * ancestors. The vanilla event is kept for its shortcut helpers
 * ({@code isCopy()}, {@code isPaste()}, {@code isSelectAll()}, arrows, ...),
 * which already handle platform quirks such as Cmd on macOS.
 */
public final class KeyEvent extends UiEvent {
    public enum Type {
        PRESS,
        RELEASE
    }

    private final Type type;
    private final net.minecraft.client.input.KeyEvent vanilla;

    public KeyEvent(Type type, net.minecraft.client.input.KeyEvent vanilla) {
        this.type = type;
        this.vanilla = vanilla;
    }

    public Type type() {
        return type;
    }

    public int key() {
        return vanilla.key();
    }

    public int scancode() {
        return vanilla.scancode();
    }

    public Modifiers modifiers() {
        return new Modifiers(vanilla.modifiers());
    }

    public net.minecraft.client.input.KeyEvent vanilla() {
        return vanilla;
    }
}
